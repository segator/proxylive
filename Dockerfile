FROM jrottenberg/ffmpeg:3.2
MAINTAINER Isaac Aymerich <isaac.aymerich@gmail.com>

ADD proxyLive.jar /app/proxyLive.jar
ADD application.yml /app/application.yml

ENV     LANG en_US.UTF-8
ENV     LC_ALL en_US.UTF-8

RUN yum install -y curl

ENV JDK_VERSION 8u31
ENV JDK_BUILD_VERSION b13

RUN curl -LO "http://download.oracle.com/otn-pub/java/jdk/$JDK_VERSION-$JDK_BUILD_VERSION/jdk-$JDK_VERSION-linux-x64.rpm" -H 'Cookie: oraclelicense=accept-securebackup-cookie' && rpm -i jdk-$JDK_VERSION-linux-x64.rpm; rm -f jdk-$JDK_VERSION-linux-x64.rpm; yum clean all

ENV JAVA_HOME /usr/java/default
EXPOSE 8080

WORKDIR /app

ENTRYPOINT ["java", "-jar", "/app/proxyLive.jar"]