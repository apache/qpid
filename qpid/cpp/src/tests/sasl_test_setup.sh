#!/usr/bin/env bash

#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

source env.sh

test -x $SASLPASSWD2 || { echo Skipping SASL test, saslpasswd2 not found; exit 0; }

mkdir -p sasl_config

# Create configuration file.
cat > sasl_config/qpidd.conf <<EOF
pwcheck_method: auxprop
auxprop_plugin: sasldb
sasldb_path: $PWD/sasl_config/qpidd.sasldb
sql_select: dummy select
mech_list: ANONYMOUS PLAIN DIGEST-MD5 EXTERNAL CRAM-MD5
EOF

# Populate temporary sasl db.
SASLTEST_DB=./sasl_config/qpidd.sasldb
rm -f $SASLTEST_DB
echo guest | $SASLPASSWD2 -c -p -f $SASLTEST_DB -u QPID guest
echo zig | $SASLPASSWD2 -c -p -f $SASLTEST_DB -u QPID zig
echo zag | $SASLPASSWD2 -c -p -f $SASLTEST_DB -u QPID zag

