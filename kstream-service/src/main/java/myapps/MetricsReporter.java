package myapps;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.Metric;
import org.apache.kafka.streams.KafkaStreams;

public class MetricsReporter {
    public void reportMetrics(KafkaStreams streams) throws InterruptedException {
        while(true){
            System.out.println("--- Application Metrics ---");
            Map<MetricName, ? extends Metric> metrics = streams.metrics();
            for (Map.Entry<MetricName, ? extends Metric> metric: metrics.entrySet()){
                System.out.println(metric.getKey().toString() + ", " + metric.getValue().metricValue());
                TimeUnit.MILLISECONDS.sleep(500);
            }
            TimeUnit.SECONDS.sleep(20);
        }
    }
}