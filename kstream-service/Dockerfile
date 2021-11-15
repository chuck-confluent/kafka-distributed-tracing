FROM maven:3.6.3-jdk-11
WORKDIR /tmp
COPY . .
RUN mvn compile assembly:single
ENTRYPOINT ["java","-Dcom.sun.management.jmxremote.port=9999", "-Dcom.sun.management.jmxremote.authenticate=false", "-Dcom.sun.management.jmxremote.ssl=false", "-jar", "/tmp/target/streams.examples-0.1-jar-with-dependencies.jar"]
