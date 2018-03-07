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
    yum install -y wget && \
    cd /opt && \
    wget --no-cookies --no-check-certificate --header "Cookie: gpw_e24=http%3A%2F%2Fwww.oracle.com%2F; oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/8u161-b12/2f38c3b165be4555a1fa6e98c45e0808/jdk-8u161-linux-x64.tar.gz" && \
    tar xzf jdk-8u161-linux-x64.tar.gz && \
    rm jdk-8u161-linux-x64.tar.gz && \
    cd /opt/jdk1.8.0_161/ && \
    alternatives --install /usr/bin/java java /opt/jdk1.8.0_161/bin/java 2 && \
    alternatives --install /usr/bin/jar jar /opt/jdk1.8.0_161/bin/jar 2 && \
    alternatives --install /usr/bin/javac javac /opt/jdk1.8.0_161/bin/javac 2 && \
    alternatives --set jar /opt/jdk1.8.0_161/bin/jar && \
    alternatives --set javac /opt/jdk1.8.0_161/bin/javac && \
    yum clean all && \
    rm -rf /var/cache/yum

ENV JAVA_HOME /usr/java/default
EXPOSE 8080

WORKDIR /app

ENTRYPOINT ["java", "-jar", "/app/proxyLive.jar"]