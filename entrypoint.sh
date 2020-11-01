#!/bin/bash
DEBUG_STRING=""
if $DEBUG_MODE ; then
    DEBUG_STRING="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=6006"
fi
java $DEBUG_STRING $JAVA_OPTS -jar /app/proxyLive.jar $@

