#!/bin/bash

# Parse arguments taking all - prefixed args as JAVA_OPTS
for arg in "$@"; do
    if [[ $arg == -java:* ]]; then
        JAVA_OPTS="${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  "
    else
        ARGS="${ARGS}$arg "
    fi
done

echo "Starting Persistend Messaging Test Script"
java -Dlog4j.configuration=backup-log4j.xml ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar org.apache.qpid.ping.PingDurableClient ${ARGS}
