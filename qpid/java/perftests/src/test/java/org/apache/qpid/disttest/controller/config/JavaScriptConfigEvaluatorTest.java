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
package org.apache.qpid.disttest.controller.config;

import junit.framework.TestCase;

public class JavaScriptConfigEvaluatorTest extends TestCase
{
    public void testEvaluateJavaScript() throws Exception
    {
        String path = getClass().getClassLoader().getResource("org/apache/qpid/disttest/controller/config/test-config.js")
                .toURI().getPath();

        String config = new JavaScriptConfigEvaluator().evaluateJavaScript(path);

        String expected = "{\"_tests\":[{\"_queues\":[{\"_name\":\"Json-Queue-Name\"}],\"_clients\":"
                + "[{\"_connections\":[{\"_name\":\"connection1\",\"_sessions\":[{\"_acknowledgeMode\":\"0\","
                + "\"_sessionName\":\"session1\",\"_consumers\":[]}]}],\"_name\":\"repeatingClient0\"},"
                + "{\"_connections\":[{\"_name\":\"connection1\",\"_sessions\":[{\"_acknowledgeMode\":\"0\","
                + "\"_sessionName\":\"session1\",\"_consumers\":[]}]}],\"_name\":\"repeatingClient1\"}]"
                + ",\"_iterationNumber\":0,\"_name\":\"Test 1\"},{\"_queues\":[{\"_name\":\"Json-Queue-Name\"}],"
                + "\"_clients\":[{\"_connections\":[{\"_name\":\"connection1\",\"_sessions\":"
                + "[{\"_acknowledgeMode\":\"1\",\"_sessionName\":\"session1\",\"_consumers\":[]}]}],"
                + "\"_name\":\"repeatingClient0\"},{\"_connections\":[{\"_name\":\"connection1\",\"_sessions\":"
                + "[{\"_acknowledgeMode\":\"1\",\"_sessionName\":\"session1\",\"_consumers\":[]}]}],"
                + "\"_name\":\"repeatingClient1\"}],\"_iterationNumber\":1,\"_name\":\"Test 1\"}]}";

        assertEquals("Unexpected configuration", expected, config);
    }
}
