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
# args supplied: <host> <port> <messages> <clients> <batches>

if [[ $# < 5 ]] ; then
 echo "usage: ./topic-QuickTest.sh <host> <port> <messages> <clients> <batches> [other params for both listener and publisher]"
 exit 1
fi

host=$1
shift

port=$1
shift

nomessages=$1
shift

noclients=$1
shift

batches=$1
shift

sleeptime=$(( 2 * $noclients ))

. ./setupclasspath.sh
echo $CP

./run_many.sh $noclients topic "$JAVA_HOME/bin/java -cp $CP -Damqj.logging.level='warn' -Damqj.test.logging.level='info' -Dlog4j.configuration=src/perftests.log4j org.apache.qpid.topic.Listener -host $host -port $port $@" &

echo
echo "Pausing for $sleeptime seconds to allow clients to connect"
sleep $sleeptime

$JAVA_HOME/bin/java -cp $CP -Damqj.logging.level="warn" -Damqj.test.logging.level="info" -Dlog4j.configuration=src/perftests.log4j org.apache.qpid.topic.Publisher -host $host -port $port -messages $nomessages -clients $noclients -batch $batches $@ 


