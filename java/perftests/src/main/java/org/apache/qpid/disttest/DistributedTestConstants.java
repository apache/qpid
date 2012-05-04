/*
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
package org.apache.qpid.disttest;

public abstract class DistributedTestConstants
{
    public static final String PERF_TEST_PROPERTIES_FILE = "perftests.properties";

    public static final String MSG_COMMAND_PROPERTY = "COMMAND";
    public static final String MSG_JSON_PROPERTY = "JSON";

    public static final long REGISTRATION_TIMEOUT = 60000;
    public static final long COMMAND_RESPONSE_TIMEOUT = 10000;

    public static final String CONTROLLER_QUEUE_JNDI_NAME = "controllerqueue";
}
