#!/bin/bash
debug_string=""
if $DEBUG_MODE ; then
    debug_string="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=6006"
fi
java -jar /app/proxyLive.jar $debug_string $@

