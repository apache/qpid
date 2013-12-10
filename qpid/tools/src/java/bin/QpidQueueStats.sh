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

WHEREAMI=`dirname $0`
if [ -z "$QMF2_HOME" ]; then
    export QMF2_HOME=`cd $WHEREAMI/../ && pwd`
fi

QMF2_LIBS=$QMF2_HOME/build/lib

CLASSPATH=$QMF2_LIBS/qpid-client-patch.jar:$CLASSPATH:$QMF2_LIBS/qmf2.jar

# Get the log level from the AMQJ_LOGGING_LEVEL environment variable.
if [ -n "$AMQJ_LOGGING_LEVEL" ]; then
    PROPERTIES=-Damqj.logging.level=$AMQJ_LOGGING_LEVEL
fi

java -cp $CLASSPATH $PROPERTIES org.apache.qpid.qmf2.tools.QpidQueueStats "$@"
