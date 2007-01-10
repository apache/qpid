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

# XXX -Xms1024m -XX:NewSize=300m

if [[ $# != 1 ]] ; then
 echo "usage: ./serviceProvidingClient.sh <brokerdetails>"
 exit 1
fi

. ./setupclasspath.sh
echo $CP
# usage: just pass in the host(s)
$JAVA_HOME/bin/java -cp $CP -Damqj.logging.level="warn" -Damqj.test.logging.level="info" -Dlog4j.configuration=perftests.log4j org.apache.qpid.requestreply.ServiceProvidingClient $1 guest guest /test serviceQ P T
