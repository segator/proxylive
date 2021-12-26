#!/bin/bash
DEBUG_STRING=""
PROFILE_STRING=""
if $DEBUG_MODE ; then
    DEBUG_STRING="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=6006"
fi

if [ ! -z  "$PROFILE" ]
then
    PROFILE_STRING="-Dspring.profiles.active=$PROFILE"
fi
java $DEBUG_STRING $JAVA_OPTS $PROFILE_STRING -jar /app/proxyLive.jar $@

