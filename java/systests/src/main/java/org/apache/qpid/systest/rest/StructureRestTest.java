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
package org.apache.qpid.systest.rest;

import java.util.List;
import java.util.Map;

import org.apache.qpid.server.model.Port;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class StructureRestTest extends QpidRestTestCase
{

    public void testGet() throws Exception
    {
        Map<String, Object> structure = getRestTestHelper().getJsonAsMap("/rest/structure");
        assertNotNull("Structure data cannot be null", structure);
        assertNode(structure, "QpidBroker");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> virtualhosts = (List<Map<String, Object>>) structure.get("virtualhosts");
        assertEquals("Unexpected number of virtual hosts", 3, virtualhosts.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports = (List<Map<String, Object>>) structure.get("ports");
        assertEquals("Unexpected number of ports", 4, ports.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> providers = (List<Map<String, Object>>) structure.get("authenticationproviders");
        assertEquals("Unexpected number of authentication providers", 1, providers.size());

        for (String hostName : EXPECTED_VIRTUALHOSTS)
        {
            Map<String, Object> host = getRestTestHelper().find("name", hostName, virtualhosts);
            assertNotNull("Host " + hostName + " is not found ", host);
            assertNode(host, hostName);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> queues = (List<Map<String, Object>>) host.get("queues");
            assertNotNull("Host " + hostName + " queues are not found ", queues);
            for (String queueName : EXPECTED_QUEUES)
            {
                Map<String, Object> queue = getRestTestHelper().find("name", queueName, queues);
                assertNotNull(hostName + " queue " + queueName + " is not found ", queue);
                assertNode(queue, queueName);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> bindings = (List<Map<String, Object>>) queue.get("bindings");
                assertNotNull(hostName + " queue " + queueName + " bindings are not found ", queues);
                for (Map<String, Object> binding : bindings)
                {
                    assertNode(binding, queueName);
                }
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exchanges = (List<Map<String, Object>>) host.get("exchanges");
            assertNotNull("Host " + hostName + " exchanges are not found ", exchanges);
            for (String exchangeName : EXPECTED_EXCHANGES)
            {
                Map<String, Object> exchange = getRestTestHelper().find("name", exchangeName, exchanges);
                assertNotNull("Exchange " + exchangeName + " is not found ", exchange);
                assertNode(exchange, exchangeName);
                if ("amq.direct".equalsIgnoreCase(exchangeName) || "<<default>>".equalsIgnoreCase(exchangeName))
                {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> bindings = (List<Map<String, Object>>) exchange.get("bindings");
                    assertNotNull(hostName + " exchange " + exchangeName + " bindings are not found ", bindings);
                    for (String queueName : EXPECTED_QUEUES)
                    {
                        Map<String, Object> binding = getRestTestHelper().find("name", queueName, bindings);
                        assertNotNull(hostName + " exchange " + exchangeName + " binding  " + queueName + " is not found", binding);
                        assertNode(binding, queueName);
                    }
                }
            }
        }


        String httpPortName = TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT;
        Map<String, Object> portData = getRestTestHelper().find(Port.NAME, httpPortName, ports);
        assertNotNull("Http Port " + httpPortName + " is not found", portData);
        assertNode(portData, httpPortName);

        String amqpPortName = TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT;
        Map<String, Object> amqpPortData = getRestTestHelper().find(Port.NAME, amqpPortName, ports);
        assertNotNull("Amqp port " + amqpPortName + " is not found", amqpPortData);
        assertNode(amqpPortData, amqpPortName);
    }

    private void assertNode(Map<String, Object> node, String name)
    {
        assertEquals("Unexpected name", name, node.get("name"));
        assertNotNull("Unexpected id", node.get("id"));
    }
}
