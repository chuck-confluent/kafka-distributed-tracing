# Kafka Distributed Tracing

This repository is derived from the work of Nacho Munoz and Samir Hafez as described in the blog post [Integrating Apache Kafka Clients with CNCF Jaeger at Funding Circle Using OpenTelemetry](https://www.confluent.io/blog/integrate-kafka-and-jaeger-for-distributed-tracing-and-monitoring/).

## Run in Gitpod

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/chuck-confluent/kafka-distributed-tracing)

## Prerequisites

1. Download OpenTelemetry Java Agent (skip if running in Gitpod).

    ```bash
    wget -P agents https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.7.1/opentelemetry-javaagent-all.jar
    ```

1. Download OpenTelemetry JMX Gatherer (skip if running in Gitpod).

    ```bash
    wget -P agents https://github.com/open-telemetry/opentelemetry-java-contrib/releases/download/v1.7.0/opentelemetry-jmx-metrics.jar
    ```

1. Download Prometheus JMX Exporter (skip if running in Gitpod).

    ```bash
    wget -P agents https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.16.1/jmx_prometheus_javaagent-0.16.1.jar
    ```

1. Spin up docker compose stack  (skip if running in Gitpod)

    ```bash
    docker-compose up -d
    ```


1. Deploy the datagen connectors, which produce Avro records to Kafka.

    ```bash
    curl -X PUT -H "Content-type: application/json" -d @connectors/datagen-connector-trades.json http://localhost:8083/connectors/datagen-connector-trades/config

    curl -X PUT -H "Content-type: application/json" -d @connectors/datagen-connector-users.json http://localhost:8083/connectors/datagen-connector-users/config
    ```


1. Open ksqlDB CLI prompt.

    ```bash
    docker run --network kafka-distributed-tracing_default --rm --interactive --tty \
        -v ${PWD}/ksqldb_script.sql:/app/ksqldb_script.sql \
        confluentinc/ksqldb-cli:0.21.0 ksql \
        http://ksqldb-server:8088
    ```


1. Create streaming application in ksqlDB

    ```SQL
    ksql> run script /app/ksqldb_script.sql
    ```

1. Try a push query

    ```SQL
    SELECT * FROM stockapp_dollars_by_zip_5_min EMIT CHANGES;
    ```

1. Press `Ctrl+D` to exit the ksql shell.

## View Metrics and Traces in Jaeger UI

1. Open http://localhost:16686 to see the Jaeger UI.
    - In Gitpod, you can `Ctrl+Click` the URL output from the following command:
    ```bash
    echo https://16686-${GITPOD_WORKSPACE_URL#https://}
    ```