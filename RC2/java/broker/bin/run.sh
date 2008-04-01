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


COMMONDIR=../../common

COMMONLIB=$COMMONLIB:$COMMONDIR/lib/slf4j/slf4j-simple.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/logging-log4j/log4j-1.2.13.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/mina/mina-core-0.9.5-SNAPSHOT.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/mina/mina-filter-ssl-0.9.5-SNAPSHOT.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/commons-collections/commons-collections-3.1.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/commons-cli/commons-cli-1.0.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/commons-configuration/commons-configuration-1.2.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/commons-logging/commons-logging-api.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/commons-logging/commons-logging.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/commons-lang/commons-lang-2.1.jar
COMMONLIB=$COMMONLIB:$COMMONDIR/lib/junit/junit-4.0.jar

DIST=../lib/bdb/je-3.0.12.jar:../dist/amqpd-tests.jar:../dist/amqpd.jar:../../client/dist/amqp-common.jar

CP=../intellijclasses:$DIST:$COMMONLIB

if [ "$(uname -a | fgrep Cygwin)" != "" ]; then
    CP=$(cygpath --mixed --path $CP)
fi

"$JAVA_HOME/bin/java" -Xmx512m -Dprepopulate=10 -Damqj.logging.level=INFO -cp $CP $*
