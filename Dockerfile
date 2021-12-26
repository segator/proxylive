FROM maven:3.6.3-adoptopenjdk-14 as builder
WORKDIR /app/code
COPY / /app/code
RUN mvn clean install && \
    apt-get update && \
    apt-get install tar wget -y && \
    wget https://get.docker.com/builds/Linux/x86_64/docker-1.12.0.tgz && \
    tar zxvf docker-1.12.0.tgz

FROM jrottenberg/ffmpeg:4.3-vaapi
MAINTAINER Isaac Aymerich <isaac.aymerich@gmail.com>

RUN apt-get update -y && \
    apt-get install -y wget && \
    apt-get clean all && \
    wget https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.2%2B12/OpenJDK14U-jdk_x64_linux_hotspot_14.0.2_12.tar.gz && \
    tar xvzf OpenJDK14U-jdk_x64_linux_hotspot_14.0.2_12.tar.gz && \
    rm -rf OpenJDK14U-jdk_x64_linux_hotspot_14.0.2_12.tar.gz && \
    mv jdk-14.0.2+12 /usr/java && \
    ln -s /usr/java/bin/java /usr/bin/java

COPY --from=builder /app/code/target/ProxyLive.jar /app/proxyLive.jar
COPY --from=builder /app/code/docker/docker /usr/bin/docker
COPY --from=builder /app/code/target/application.yml /app/application.yml

ENV     LANG en_US.UTF-8
ENV     LC_ALL en_US.UTF-8


ENV DEBUG_MODE false
ENV PROFILE = ""
ENV JAVA_OPTS="-Xms256m -Xmx1024m"
ENV JAVA_HOME /usr/java

COPY entrypoint.sh /entrypoint.sh
EXPOSE 8080

WORKDIR /app

ENTRYPOINT ["/entrypoint.sh"]