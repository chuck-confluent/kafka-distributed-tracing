package myapps;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.Metric;
import org.apache.kafka.streams.KafkaStreams;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

public class MetricsReporter {

    private static final Meter meter = GlobalMeterProvider.get().get("io.opentelemetry.metrics.hello");

    public void reportMetrics(KafkaStreams streams) throws InterruptedException {

        while(true){

            // Access Kafka Streams metrics
            Map<MetricName, ? extends Metric> metrics = streams.metrics();

            for (Map.Entry<MetricName, ? extends Metric> metric: metrics.entrySet()){

                System.out.println("Metric: " + metric.getKey().toString() + ", " + metric.getValue().metricValue());
                meter
                    .counterBuilder(metric.getKey().name())
                    .setUnit(metric.getValue().toString())
                    .buildWithCallback(
                        r -> {
                            r.observe(metric.getValue().metricValue());
                    });
                TimeUnit.MILLISECONDS.sleep(500);
            }

            TimeUnit.SECONDS.sleep(30);

        }
    }

    
}