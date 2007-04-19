#!/bin/bash

# Parse arguements taking all - prefixed args as JAVA_OPTS
for arg in "$@"; do
    if [[ $arg == -java:* ]]; then
        JAVA_OPTS="${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  "
    else
        ARGS="${ARGS}$arg "
    fi
done

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx256m -Dbadger.level=warn -Damqj.test.logging.level=warn -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar org.apache.qpid.ping.PingDurableClient -o $QPID_WORK/results ${ARGS} 
