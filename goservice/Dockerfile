FROM golang:buster as builder

WORKDIR /go/src
COPY . .
RUN wget -qO - https://packages.confluent.io/deb/7.0/archive.key | apt-key add - \
  && echo "deb [arch=amd64] https://packages.confluent.io/deb/7.0 stable main" >> /etc/apt/sources.list \
  && echo "deb https://packages.confluent.io/clients/deb buster main" >> /etc/apt/sources.list \
  && apt-get update &&  apt-get install -y librdkafka-dev
RUN go mod download && go build -o goapi *.go
ENV BOOTSTRAP_SERVERS="broker:29092"
ENTRYPOINT [ "/go/src/goapi" ]
