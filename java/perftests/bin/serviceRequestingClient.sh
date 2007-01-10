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
# args supplied: <host:port> <num messages>
#
if [[ $# < 2 ]] ; then
 echo "usage: ./serviceRequestingClient.sh <brokerdetails> <number of messages> [<message size 4096b default>]"
 exit 1
fi

thehosts=$1
shift
echo $thehosts
# XXX -Xms1024m -XX:NewSize=300m
. ./setupclasspath.sh
echo $CP
$JAVA_HOME/bin/java -cp $CP -Damqj.logging.level="warn" -Damqj.test.logging.level="info" -Dlog4j.configuration=perftests.log4j org.apache.qpid.requestreply.ServiceRequestingClient $thehosts guest guest /test serviceQ "$@"
