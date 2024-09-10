FROM debian:12 as builder

RUN apt-get update && \
  apt-get install -y openjdk-17-jdk maven

COPY . /app
WORKDIR /app

RUN mvn package

FROM debian:12
RUN apt-get update && \
  apt-get install -y openjdk-17-jre

run mkdir /app
COPY --from=builder /app/app/target/bowlby /app/bowlby

VOLUME /data
CMD /app/bowlby -d /data
