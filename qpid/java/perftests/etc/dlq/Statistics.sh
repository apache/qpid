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

# Set Qpid Version
VERSION=0.5

# Setup Java CLASSPATH
CLASSPATH=${QPID_HOME}/lib/qpid-all.jar
CLASSPATH=${CLASSPATH}:${QPID_HOME}/lib/qpid-perftests-${VERSION}.jar
CLASSPATH=${CLASSPATH}:${QPID_HOME}/lib/slf4j-api-1.4.0.jar
CLASSPATH=${CLASSPATH}:${QPID_HOME}/lib/slf4j-log4j12-1.4.0.jar
CLASSPATH=${CLASSPATH}:${QPID_HOME}/lib/log4j-1.2.12.jar
CLASSPATH=${CLASSPATH}:${QPID_HOME}/lib/geronimo-jms_1.1_spec-1.0.jar

# Run Performance Test Framework
echo "Running DLQ Performance Tests"
java -cp ${CLASSPATH} org.apache.qpid.perftests.dlq.test.PerformanceStatistics $*
