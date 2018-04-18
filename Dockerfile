FROM maven:3.5-jdk-8-alpine as builder
WORKDIR /app/code
COPY / /app/code
RUN mvn clean install

FROM jrottenberg/ffmpeg:3.2-centos
MAINTAINER Isaac Aymerich <isaac.aymerich@gmail.com>

COPY --from=builder /app/code/target/ProxyLive.jar /app/proxyLive.jar
COPY --from=builder /app/code/target/application.yml /app/application.yml

ENV     LANG en_US.UTF-8
ENV     LC_ALL en_US.UTF-8


RUN yum update -y && \
    yum -y install java-1.8.0-openjdk java-1.8.0-openjdk-devel && \
    yum clean all && \
    rm -rf /var/cache/yum

ENV JAVA_HOME /usr/java/default
COPY bootstrap.yml /app/bootstrap.yml
COPY entrypoint.sh /entrypoint.sh
ENV CONSUL_SERVER=""
ENV CONSUL_PORT=""
EXPOSE 8080

WORKDIR /app

ENTRYPOINT ["/entrypoint.sh"]
