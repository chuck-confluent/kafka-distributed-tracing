/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package myapps;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class AvroToJSONStream {
    private static JsonNode avroToJSON(String jsonStr)  {
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode actualObj = null;
        try {
            actualObj = mapper.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return actualObj;
    }

    public static void main(String[] args) throws Exception {

        // Get required endpoints from environments
        String brokerEndpoint = System.getenv("BOOTSTRAP_SERVER");
        String schemaRegistryEndpoint = System.getenv("SCHEMA_REGISTRY");
        if (brokerEndpoint == null) {brokerEndpoint = "localhost:9092";}
        if (schemaRegistryEndpoint == null) {schemaRegistryEndpoint = "http://localhost:8081";}

        // Create configuration properties
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-avro-to-json"+ UUID.randomUUID().toString());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, brokerEndpoint);
        props.put("schema.registry.url", schemaRegistryEndpoint);
        SchemaRegistryClient client = new CachedSchemaRegistryClient(schemaRegistryEndpoint, 100);

        // Create serializers and serializers

        // Avro deserializer needed to deserialize values from stockapp.trades events
        final Serde avroSerde=Serdes.serdeFrom(new KafkaAvroSerializer(client), new KafkaAvroDeserializer(client));
        // Json serializer needed to produce values to stockapp.trades-json
        final Serde jsonSerde=Serdes.serdeFrom(new JsonSerializer(),new JsonDeserializer());
        // String serde needed to read the key
        final Serde stringSerde=Serdes.String();
        
        // Design stream topology from the StreamsBuilder
        final StreamsBuilder builder = new StreamsBuilder();
        builder.<String, String>stream("stockapp.trades", Consumed.with(stringSerde,avroSerde))
            .mapValues(value -> {
                System.out.println(value.toString());

                return avroToJSON(value.toString());

            } )
            .to("stockapp.trades-json", Produced.with(stringSerde,jsonSerde));

        // Build stream topology
        final Topology topology = builder.build();
        // Create kstreams app from topology and configuration properties
        final KafkaStreams streams = new KafkaStreams(topology, props);
        // Create countdown latch for graceful shutdown
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {

            // Start kstreams application
            streams.start();

            // Report metrics. See MetricsReporter.java to see how to export kstreams metrics via otlp
            new MetricsReporter().reportMetrics(streams);

            // Await until shutdown hook runs
            latch.await();
            
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
