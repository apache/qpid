#!/bin/bash

# Parse arguements taking all - prefixed args as JAVA_OPTS
for arg in "$@"; do
    if [[ $arg == -java:* ]]; then
        JAVA_OPTS="${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  "
    else
        ARGS="${ARGS}$arg "
    fi
done
echo "Starting 6 parallel tests"

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx3072m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar uk.co.thebadgerset.junit.extensions.TKTestRunner -n PT-Qpid-14 -s [250] -c[200] -t testAsyncPingOk org.apache.qpid.ping.PingAsyncTestPerf pubsub=true messageSize=256 destinationName=ping1 batchSize=250 -o $QPID_WORK/results ${ARGS} &

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx3072m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar uk.co.thebadgerset.junit.extensions.TKTestRunner -n PT-Qpid-14 -s [250] -c[200] -t testAsyncPingOk org.apache.qpid.ping.PingAsyncTestPerf pubsub=true messageSize=256 destinationName=ping2 batchSize=250 -o $QPID_WORK/results ${ARGS} &

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx3072m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar uk.co.thebadgerset.junit.extensions.TKTestRunner -n PT-Qpid-14 -s [250] -c[200] -t testAsyncPingOk org.apache.qpid.ping.PingAsyncTestPerf pubsub=true messageSize=256 destinationName=ping3 batchSize=250 -o $QPID_WORK/results ${ARGS} &

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx3072m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar uk.co.thebadgerset.junit.extensions.TKTestRunner -n PT-Qpid-14 -s [250] -c[200] -t testAsyncPingOk org.apache.qpid.ping.PingAsyncTestPerf pubsub=true messageSize=256destinationname=ping4  batchSize=250 -o $QPID_WORK/results ${ARGS} &

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx3072m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar uk.co.thebadgerset.junit.extensions.TKTestRunner -n PT-Qpid-14 -s [250] -c[100] -t testAsyncPingOk org.apache.qpid.ping.PingAsyncTestPerf pubsub=true messageSize=256 destinationName=ping5 batchSize=250 -o $QPID_WORK/results ${ARGS} &

java -Xms256m -Dlog4j.configuration=perftests.log4j -Xmx3072m -Dbadger.level=warn -Damqj.test.logging.level=info -Damqj.logging.level=warn ${JAVA_OPTS} -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar uk.co.thebadgerset.junit.extensions.TKTestRunner -n PT-Qpid-14 -s [250] -c[100] -t testAsyncPingOk org.apache.qpid.ping.PingAsyncTestPerf pubsub=true messageSize=256 destinationName=ping6 batchSize=250 -o $QPID_WORK/results ${ARGS}
