package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.7.0"
	"go.opentelemetry.io/otel/trace"
	"gopkg.in/confluentinc/confluent-kafka-go.v1/kafka"
)

func initProvider() func() {
	ctx := context.Background()
	otlpEndpointAddr, ok := os.LookupEnv("OTEL_ENDPOINT")

	if !ok {
		otlpEndpointAddr = "localhost:8200"
	}

	exp, err := otlptracegrpc.New(ctx, otlptracegrpc.WithEndpoint(otlpEndpointAddr), otlptracegrpc.WithInsecure())
	handleErr(err, "failed to create exporter")

	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceNameKey.String("api-goservice"),
		),
	)
	handleErr(err, "failed to create resource")

	bsp := sdktrace.NewBatchSpanProcessor(exp)
	tracerProvider := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.AlwaysSample()),
		sdktrace.WithResource(res),
		sdktrace.WithSpanProcessor(bsp),
	)

	// set global propagator to tracecontext (the default is no-op).
	otel.SetTextMapPropagator(propagation.TraceContext{})
	otel.SetTracerProvider(tracerProvider)

	return func() {
		// Shutdown will flush any remaining spans.
		handleErr(tracerProvider.Shutdown(ctx), "failed to shutdown TracerProvider")

	}
}

func handleErr(err error, message string) {
	if err != nil {
		log.Fatalf("%s: %v", message, err)
	}
}

func main() {
	closeOtel := initProvider()
	defer closeOtel()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	msgs := consumerLoop(&ctx)
	data := make([]dollarsByZip, 0)

	go func() {
		for msg := range msgs {
			data = append(data, msg)
		}
	}()

	app := fiber.New()
	app.Get("/", func(c *fiber.Ctx) error {
		return c.Status(200).JSON(data)
	})
	app.Listen(":3100")
}

func spanFromHeaders(topic string, headers []kafka.Header) {
	var propagatedId string
	for _, h := range headers {
		if h.Key == "traceparent" {
			propagatedId = string(h.Value)
			break
		}
	}

	if propagatedId == "" {
		return
	}

	tracer := otel.Tracer("api-goservice")
	attWithTopic := make([]attribute.KeyValue, 10)
	attWithTopic = append(
		attWithTopic,
		attribute.String("messaging.destination_kind", "topic"),
		attribute.String("span.otel.kind", "CONSUMER"),
		attribute.String("messaging.system", "kafka"),
		attribute.String("net.transport", "IP.TCP"),
		attribute.String("messaging.url", "broker:29092"),
		attribute.String("messaging.operation", "receive"),
		attribute.String("messaging.destination", "stockapp.dollarsbyzip"))

	traceID, spanID, err := parseParentTrace(propagatedId)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error parsing parent trace %v \n", err)
		return
	}
	ctx := trace.ContextWithRemoteSpanContext(context.Background(),
		trace.NewSpanContext(trace.SpanContextConfig{TraceID: *traceID, SpanID: *spanID}))
	ctx, span := tracer.Start(ctx, "consumer-go-service", trace.WithAttributes(attWithTopic...))
	defer span.End()
}
func parseParentTrace(ptrace string) (*trace.TraceID, *trace.SpanID, error) {
	tokens := strings.Split(ptrace, "-")
	traceID, err := trace.TraceIDFromHex(tokens[1])
	if err != nil {
		return nil, nil, err
	}
	spanID, err := trace.SpanIDFromHex(tokens[2])
	if err != nil {
		return nil, nil, err
	}
	return &traceID, &spanID, nil
}

func consumerLoop(ctx *context.Context) <-chan dollarsByZip {
	records := make(chan dollarsByZip, 1)

	go func() {
		sigchan := make(chan os.Signal, 1)
		signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)
		broker, ok := os.LookupEnv("BOOTSTRAP_SERVERS")
		if !ok {
			broker = "localhost:9092"
		}
		c, err := kafka.NewConsumer(&kafka.ConfigMap{
			"bootstrap.servers":        broker,
			"group.id":                 uuid.New().String(),
			"enable.auto.commit":       "false",
			"debug":                    "consumer",
			"auto.offset.reset":        "earliest",
			"go.events.channel.enable": true,
		})

		if err != nil {
			fmt.Fprintf(os.Stderr, "error creating consumer %v \n", err)
		}
		handleErr(c.Subscribe("stockapp.dollarsbyzip", nil), "error subscribing to topic")
		defer c.Close()
		for {
			select {
			case sig := <-sigchan:
				fmt.Printf("Caught signal %v: terminating\n", sig)
				os.Exit(0)
			case <-(*ctx).Done():
				fmt.Printf("exiting consumer loop due to context cancellation")
				return
			case ev := <-c.Events():
				switch e := ev.(type) {

				case *kafka.Message:
					fmt.Printf("%% Message on %s:\n%s\n",
						e.TopicPartition, string(e.Value))
					var evt dollarsByZip
					if err := json.Unmarshal(e.Value, &evt); err != nil {
						fmt.Printf(" Error: %v\n", err)
						c.Close()
					}
					spanFromHeaders("stockapp.dollarsbyzip", e.Headers)
					records <- evt

				case kafka.Error:
					fmt.Fprintf(os.Stderr, "%% Error: %v\n", e)
					c.Close()
				}
			}
		}
	}()
	return records
}

type dollarsByZip struct {
	Zipcode      string  `json:"ZIPCODE"`
	TotalDollars float64 `json:"TOTAL_DOLLARS"`
}
