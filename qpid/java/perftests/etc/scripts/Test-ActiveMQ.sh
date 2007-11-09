#!/bin/bash

# Parse arguements taking all - prefixed args as JAVA_OPTS
for arg in "$@"; do
    if [[ $arg == -java:* ]]; then
        JAVA_OPTS="${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  "
    else
        ARGS="${ARGS}$arg "
    fi
done

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx1024m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp "qpid-perftests-1.0-incubating-M2.1-SNAPSHOT.jar;activemq-jars/apache-activemq-4.1.1.jar" uk.co.thebadgerset.junit.extensions.TKTestRunner -n Test-ActiveMQ -s[1] -r 1 -t testAsyncPingOk -o . org.apache.qpid.ping.PingAsyncTestPerf properties=activemq.properties factoryName=ConnectionFactory ${ARGS}

#  queueNamePostfix=@router1 overrideClientId=true 