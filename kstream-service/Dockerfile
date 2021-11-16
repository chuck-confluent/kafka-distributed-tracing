FROM maven:3.6.3-jdk-11
WORKDIR /tmp
COPY . .
ENV JAVA_OPTS ""
RUN mvn compile assembly:single
CMD ["sh", "-c", "java ${JAVA_OPTS} -jar /tmp/target/streams.examples-0.1-jar-with-dependencies.jar"]
