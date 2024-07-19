FROM maven:3.9.8-ibm-semeru-21-jammy as builder
WORKDIR /app/code
COPY / /app/code
RUN mvn clean install && \
    apt-get update && \
    apt-get install tar wget -y && \
    wget https://download.docker.com/linux/static/stable/x86_64/docker-27.0.3.tgz && \
    tar zxvf docker-27.0.3.tgz

FROM linuxserver/ffmpeg:7.0.1
MAINTAINER Isaac Aymerich <isaac.aymerich@gmail.com>

RUN apt-get update -y && \
    apt-get install -y wget && \
    apt-get clean all && \
    wget  https://github.com/AdoptOpenJDK/semeru22-binaries/releases/download/jdk-22.0.1%2B8_openj9-0.45.0/ibm-semeru-open-jdk_x64_linux_22.0.1_8_openj9-0.45.0.tar.gz && \
    tar xvzf ibm-semeru-open-jdk_x64_linux_22.0.1_8_openj9-0.45.0.tar.gz && \
    rm -rf ibm-semeru-open-jdk_x64_linux_22.0.1_8_openj9-0.45.0.tar.gz && \
    mv jdk-22.0.1+8 /usr/java && \
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
