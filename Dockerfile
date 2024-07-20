FROM maven:3.9.8-ibm-semeru-21-jammy as builder
WORKDIR /opt
#RUN apt update && apt-get install xz-utils -y
#ADD https://download.docker.com/linux/static/stable/x86_64/docker-27.0.3.tgz /opt/docker.tgz
#RUN tar zxvf /opt/docker.tgz
#ADD https://johnvansickle.com/ffmpeg/builds/ffmpeg-git-amd64-static.tar.xz /opt/ffmpeg.tar.xz
#RUN tar -xJf /opt/ffmpeg.tar.xz && \
#    mv ffmpeg-* ffmpeg

ADD https://github.com/AdoptOpenJDK/semeru22-binaries/releases/download/jdk-22.0.1%2B8_openj9-0.45.0/ibm-semeru-open-jdk_x64_linux_22.0.1_8_openj9-0.45.0.tar.gz java.tar.gz
RUN  tar xvzf java.tar.gz && mv jdk* jdk
COPY pom.xml /app/pom.xml
COPY src /app/src

WORKDIR /app
RUN mvn clean install

FROM linuxserver/ffmpeg:latest
MAINTAINER Isaac Aymerich <isaac.aymerich@gmail.com>




COPY --from=builder /opt/jdk /usr/java
RUN ln -s /usr/java/bin/java /usr/bin/java
#COPY --from=builder /opt/docker/docker /usr/bin/docker
#COPY --from=builder /opt/ffmpeg/ffmpeg /usr/bin/ffmpeg
COPY --from=builder /app/target/ProxyLive.jar /app/proxyLive.jar
COPY --from=builder /app/target/application.yml /app/application.yml


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
