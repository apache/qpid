java -cp ".:${QPID_HOME}/lib/*" -Dlog4j.configuration=log4j-client.properties -Dqpid.dest_syntax=BURL  org.apache.qpid.disttest.ClientRunner jndi-config=perftests-jndi.properties
