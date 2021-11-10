FROM maven:3.6.3-jdk-11
WORKDIR /tmp
COPY . .
RUN mvn compile assembly:single
ENTRYPOINT ["java","-jar", "/tmp/target/streams.examples-0.1-jar-with-dependencies.jar"]
