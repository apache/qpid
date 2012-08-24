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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Port;

public class PortRestTest extends QpidRestTestCase
{
    public void testGet() throws Exception
    {
        List<Map<String, Object>> ports = getRestTestHelper().getJsonAsList("/rest/port/");
        assertNotNull("Port data cannot be null", ports);
        assertEquals("Unexpected number of ports", 2, ports.size());
        int[] expectedPorts = { getPort(), getRestTestHelper().getHttpPort() };
        for (int port : expectedPorts)
        {
            String portName = "0.0.0.0:" + port;
            Map<String, Object> portData = getRestTestHelper().find(Port.NAME, portName, ports);
            assertNotNull("Port " + portName + " is not found", portData);
            Asserts.assertPortAttributes(portData);
        }
    }

    public void testGetPort() throws Exception
    {
        List<Map<String, Object>> ports = getRestTestHelper().getJsonAsList("/rest/port/");
        assertNotNull("Ports data cannot be null", ports);
        assertEquals("Unexpected number of ports", 2, ports.size());
        for (Map<String, Object> portMap : ports)
        {
            String portName = (String) portMap.get(Port.NAME);
            assertNotNull("Port name attribute is not found", portName);
            Map<String, Object> portData = getRestTestHelper().getJsonAsSingletonList("/rest/port/" + URLDecoder.decode(portName, "UTF-8"));
            assertNotNull("Port " + portName + " is not found", portData);
            Asserts.assertPortAttributes(portData);
        }
    }

}
