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

# Parse arguments taking all - prefixed args as JAVA_OPTS

declare -a ARGS
for arg in "$@"; do
    if [[ $arg == -java:* ]]; then
        JAVA_OPTS="${JAVA_OPTS}-`echo $arg|cut -d ':' -f 2`  "
    else
        ARGS[${#ARGS[@]}]="$arg"
    fi
done

if [ -z "${QPID_HOME}" ]; then
    WHEREAMI=`dirname "$0"`
    export QPID_HOME=`cd ${WHEREAMI}/../ && pwd`
fi

# BDB's je JAR expected to be found in lib/opt
LIBS="${QPID_HOME}/lib/opt/*:${QPID_HOME}/lib/qpid-all.jar"

echo "Starting Hot Backup Script"
java -Dlog4j.configuration=backup-log4j.xml ${JAVA_OPTS} -cp "${LIBS}" org.apache.qpid.server.store.berkeleydb.BDBBackup "${ARGS[@]}"
