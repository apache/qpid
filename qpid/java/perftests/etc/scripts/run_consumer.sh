java -Xms256m -Xmx1024m -Ddestinations="test" -Dduration=15M -DlogFrequency=1000 -DlogFilePath="/home/rajith/qpid" -Dmax_prefetch=1000 -Djava.naming.provider.url="/home/rajith/tests.properties" -Djava.naming.factory.initial=org.apache.qpid.jndi.PropertiesFileInitialContextFactory -Djms_timeout=2000 -cp qpid-perftests-1.0-incubating-M2-SNAPSHOT.jar org.apache.qpid.client.perf.MessageConsumerTest

