package myapps;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.JMException;

import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.Metric;
import org.apache.kafka.streams.KafkaStreams;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

/**
 * DRAFT
 * Report Kafka Streams metrics to observability backend via OpenTelemetry protocol (OTLP)
 * 
 * See https://github.com/confluentinc/jmx-monitoring-stacks/blob/6.1.0-post/shared-assets/jmx-exporter/kafka_streams.yml
 * for an opinionated set of good Kafka Streams JMX metrics to monitor.
 * 
 * See see https://docs.confluent.io/platform/current/streams/monitoring.html
 * for all Kafka Streams metrics
 */

public class MetricsReporter {

    public static void reportMetrics(KafkaStreams streams) throws JMException {

        // Create OpenTelemetry meter
        Meter meter = GlobalMeterProvider.get().get(MetricsReporter.class.getName());

        // Create collection of Kafka Streams metrics
        Collection<? extends Metric> metrics = streams.metrics().values();

        // TODO Filter metrics collection down to a set that really matter to monitor

        // TODO Create collection of metrics of type long
        Predicate<Metric> isLongMetric = metric -> metric.metricValue().getClass().isInstance(long.class);
        Collection<? extends Metric> longMetrics = metrics.stream().filter(isLongMetric).collect(Collectors.toList());

        // TODO Create collections of metrics of other types

        // Observe metrics of type long with the OpenTelemetry API
        for (Metric longMetric : longMetrics) {
            MetricName metricName = longMetric.metricName();
            AttributesBuilder attributesBuilder = Attributes.builder();
            metricName.tags().forEach((key, value) -> attributesBuilder.put(key, value));
            Attributes attributes = attributesBuilder.build();
            meter
                .counterBuilder(metricName.group() + "_" + metricName.name()) // TODO choose the type of the metric: counter, upDownCounter, gauge, or histogram 
                .setDescription(metricName.description())
                .buildWithCallback(observableLongMeasurement -> {
                    Long metricValue = (Long) longMetric.metricValue(); // TODO verify it's the right way to convert metricValue() into a Long or Double
                    observableLongMeasurement.observe(metricValue, attributes);
                });
        }

        // TODO Observe other metrics with the OpenTelemetry API

    }

    
}