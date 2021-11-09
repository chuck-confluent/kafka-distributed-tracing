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
    private static JsonNode avroToJSON(String jsonStr, boolean key)  {
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
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-avro-to-json"+ UUID.randomUUID().toString());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "broker:29092");
        props.put("schema.registry.url", "http://schema-registry:8081");
        SchemaRegistryClient client = new CachedSchemaRegistryClient("http://schema-registry:8081", 100);
        final Serde avroSerde=Serdes.serdeFrom(new KafkaAvroSerializer(client), new KafkaAvroDeserializer(client));

        final StreamsBuilder builder = new StreamsBuilder();
        final Serde jsonSerde=Serdes.serdeFrom(new JsonSerializer(),new JsonDeserializer());
        final Serde stringSerde=Serdes.String();
        builder.<String, String>stream("stockapp.trades", Consumed.with(stringSerde,avroSerde))
            .map((key, value) -> {
                System.out.println(value.toString());

                return KeyValue.pair(key, avroToJSON(value.toString(),false));

            } )
            .to("stockapp.trades-json", Produced.with(stringSerde,jsonSerde));

        final Topology topology = builder.build();
        final KafkaStreams streams = new KafkaStreams(topology, props);
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
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}
