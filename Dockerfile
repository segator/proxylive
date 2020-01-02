FROM maven:3.6.3-jdk-11-slim as builder
WORKDIR /app/code
COPY / /app/code
RUN mvn clean install && \
    apt-get update && \
    apt-get install tar wget -y && \
    wget https://get.docker.com/builds/Linux/x86_64/docker-1.12.0.tgz && \
    tar zxvf docker-1.12.0.tgz

FROM jrottenberg/ffmpeg:3.4-vaapi
MAINTAINER Isaac Aymerich <isaac.aymerich@gmail.com>

COPY --from=builder /app/code/target/ProxyLive.jar /app/proxyLive.jar
COPY --from=builder /app/code/docker/docker /usr/bin/docker
COPY --from=builder /app/code/target/application.yml /app/application.yml

ENV     LANG en_US.UTF-8
ENV     LC_ALL en_US.UTF-8


RUN apt-get update -y && \
    apt-get install -y openjdk-11-jdk && \
    apt-get clean all


ENV DEBUG_MODE false
ENV JAVA_HOME /usr/java/default
COPY entrypoint.sh /entrypoint.sh
EXPOSE 8080

WORKDIR /app

ENTRYPOINT ["/entrypoint.sh"]