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

error() { echo $*; exit 1; }

# Make sure $QPID_DIR contains what we need.
if ! test -d "$QPID_DIR" ; then
    echo "WARNING: QPID_DIR is not set skipping system tests."
    exit
fi
STORE_LIB=../lib/.libs/msgstore.so

xml_spec=$QPID_DIR/specs/amqp.0-10-qpid-errata.stripped.xml
test -f $xml_spec || error "$xml_spec not found: invalid \$QPID_DIR ?"
export PYTHONPATH=$QPID_DIR/python:$QPID_DIR/extras/qmf/src/py:$QPID_DIR/tools/src/py

echo "Using directory $TMP_DATA_DIR"

fail=0

# Run the tests with a given set of flags
BROKER_OPTS="--no-module-dir --load-module=$STORE_LIB --data-dir=$TMP_DATA_DIR --auth=no --wcache-page-size 16"
run_tests() {
    for p in `seq 1 8`; do
	$abs_srcdir/start_broker "$@" ${BROKER_OPTS} || { echo "FAIL broker start";  return 1; }
	python "$abs_srcdir/persistence.py" -s "$xml_spec" -b localhost:`cat qpidd.port` -p $p -r 3 || fail=1;
	$abs_srcdir/stop_broker
    done
}

run_tests || fail=1

exit $fail
