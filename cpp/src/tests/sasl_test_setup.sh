#! /bin/bash

SASL_PW=/usr/sbin/saslpasswd2
test -x $SASL_PW || { echo Skipping SASL test, saslpasswd2 not found; exit 0; }

mkdir -p sasl_config

# Create configuration file.
cat > sasl_config/qpidd.conf <<EOF
pwcheck_method: auxprop
auxprop_plugin: sasldb
sasldb_path: $PWD/sasl_config/qpidd.sasldb
sql_select: dummy select
EOF

# Populate temporary sasl db.
SASLTEST_DB=./sasl_config/qpidd.sasldb
rm -f $SASLTEST_DB
echo guest | $SASL_PW -c -p -f $SASLTEST_DB -u QPID guest
echo zig | $SASL_PW -c -p -f $SASLTEST_DB -u QPID zig
echo zag | $SASL_PW -c -p -f $SASLTEST_DB -u QPID zag

