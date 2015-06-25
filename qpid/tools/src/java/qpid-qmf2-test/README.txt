/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

This module contains various manual test cases.The moudle pom has a helper profile
with helper exec plugin config to allow executing a particular test class from
the org.apache.qpid.qmf2.test package.

The test classes can be found at:
src/main/java/org/apache/qpid/qmf2/test/

To execute a particular test class, use:

mvn test -DtestCase=<Simple Class Name> [-Dexec.args=<arguments>]


The above command can be run either from the parent directory, or from within
the modules own directory, though in the latter case the other modules should
be installed into your local repo (mvn clean install) first to ensure the latest
code is being tested rather than a remote dependency.

Currently available classes are:

AgentExternalTest
AgentSubscriptionTestConsole
AgentTestConsole
AgentTest
BigPayloadAgentTestConsole
BigPayloadAgentTest
BrokerSubscriptionTestConsole
InvokeMethodTest
PartialGetObjectsTest
SchemaTest
Test1
Test2
Test3
Test4
URLTest
