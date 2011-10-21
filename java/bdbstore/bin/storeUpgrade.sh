#!/bin/bash
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

# Parse arguements taking all - prefixed args as JAVA_OPTS
for arg in "$@"; do
    if [[ $arg == -java:* ]]; then
        JAVA_OPTS="${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  "
    else
        ARGS="${ARGS}$arg "
    fi
done

if [ -z "$QPID_HOME" ]; then
    export QPID_HOME=$(dirname $(dirname $(readlink -f $0)))
    export PATH=${PATH}:${QPID_HOME}/bin
fi

if [ -z "$BDB_HOME" ]; then
    export BDB_HOME=$(dirname $(dirname $(readlink -f $0)))
fi

VERSION=0.13

LIBS=$BDB_HOME/lib/je-4.0.103.jar:$BDB_HOME/lib/qpid-bdbstore-$VERSION.jar:$QPID_HOME/lib/qpid-all.jar

java -Xms256m -Dlog4j.configuration=BDBStoreUpgrade.log4j.xml -Xmx256m -Damqj.logging.level=warn ${JAVA_OPTS} -cp $LIBS org.apache.qpid.server.store.berkeleydb.BDBStoreUpgrade  ${ARGS} 
