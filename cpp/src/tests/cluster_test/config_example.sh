# Cluster configuration.

# All output stored under $HOME/$CLUSTER_HOME.
CLUSTER_HOME=$HOME/cluster_test

# Hosts where brokers will be run. Repeat hostname to run multiple brokers on 1 host.
BROKER_HOSTS="mrg22 mrg23 mrg24 mrg25 mrg26"

# Hosts where clients will be run.
CLIENT_HOSTS="$BROKER_HOSTS"

# Paths to executables
QPIDD=qpidd
PERFTEST=perftest

# Directory containing tests
TESTDIR=/usr/bin

# Options for qpidd, must be sufficient to load the cluster plugin.
# Scripts will add --cluster-name, --daemon, --port and --log-to-file options here.
QPIDD_OPTS=" \
--auth=no \
--log-enable=notice+ \
--log-enable=debug+:cluster \
"
