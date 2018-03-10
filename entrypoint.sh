#!/bin/bash
if [ ! -z "${CONSUL_SERVER}" ]; then
        sed -i "s/\#enabled/true/g" /app/bootstrap.yml
	sed -i "s/\#server/${CONSUL_SERVER}/g" /app/bootstrap.yml
        sed -i "s/\#port/${CONSUL_PORT}/g" /app/bootstrap.yml

else
	sed -i "s/\#enabled/false/g" /app/bootstrap.yml
fi
java -jar /app/proxyLive.jar $@

