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
for x in `seq 1 $1`;
do
    java -cp qpid-integrationtests-1.0-incubating-M2-SNAPSHOT-all-test-deps.jar -Dlog4j.configuration=file:/home/rupert/qpid/trunk/qpid/java/etc/mylog4j.xml org.apache.qpid.test.framework.distributedtesting.TestClient -n java$x &
done
