java -cp ".:${QPID_HOME}/lib/*" -Dqpid.dest_syntax=BURL  org.apache.qpid.disttest.ControllerRunner jndi-config=perftests-jndi.properties test-config=$1 distributed=true

