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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class BrokerRestTest extends QpidRestTestCase
{

    private static final String BROKER_AUTHENTICATIONPROVIDERS_ATTRIBUTE = "authenticationproviders";
    private static final String BROKER_PORTS_ATTRIBUTE = "ports";
    private static final String BROKER_VIRTUALHOSTS_ATTRIBUTE = "virtualhosts";
    private static final String BROKER_STATISTICS_ATTRIBUTE = "statistics";

    public void testGet() throws Exception
    {
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/broker");

        assertBrokerAttributes(brokerDetails);

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) brokerDetails.get(BROKER_STATISTICS_ATTRIBUTE);
        Asserts.assertAttributesPresent(statistics, new String[]{ "bytesIn", "messagesOut", "bytesOut", "messagesIn" });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> virtualhosts = (List<Map<String, Object>>) brokerDetails.get(BROKER_VIRTUALHOSTS_ATTRIBUTE);
        assertEquals("Unexpected number of virtual hosts", 3, virtualhosts.size());

        Asserts.assertVirtualHost(TEST3_VIRTUALHOST, getRestTestHelper().find(VirtualHost.NAME, TEST3_VIRTUALHOST, virtualhosts));
        Asserts.assertVirtualHost(TEST2_VIRTUALHOST, getRestTestHelper().find(VirtualHost.NAME, TEST2_VIRTUALHOST, virtualhosts));
        Asserts.assertVirtualHost(TEST1_VIRTUALHOST, getRestTestHelper().find(VirtualHost.NAME, TEST1_VIRTUALHOST, virtualhosts));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports = (List<Map<String, Object>>) brokerDetails.get(BROKER_PORTS_ATTRIBUTE);
        assertEquals("Unexpected number of ports", 4, ports.size());

        for (Map<String, Object> port : ports)
        {
            Asserts.assertPortAttributes(port);
        }

        Map<String, Object> amqpPort = getRestTestHelper().find(Port.NAME, TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT, ports);
        Map<String, Object> httpPort = getRestTestHelper().find(Port.NAME, TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, ports);

        assertEquals("Unexpected binding address", "*", amqpPort.get(Port.BINDING_ADDRESS));
        assertNotNull("Cannot find AMQP port", amqpPort);
        assertNotNull("Cannot find HTTP port", httpPort);

        @SuppressWarnings("unchecked")
        Collection<String> port1Protocols = (Collection<String>) amqpPort.get(Port.PROTOCOLS);
        assertFalse("AMQP protocol list cannot contain HTTP", port1Protocols.contains("HTTP"));

        @SuppressWarnings("unchecked")
        Collection<String> port2Protocols = (Collection<String>) httpPort.get(Port.PROTOCOLS);
        assertEquals("Unexpected value of attribute " + Port.PROTOCOLS, new HashSet<String>(Arrays.asList("HTTP")),
                new HashSet<String>(port2Protocols));
    }

    protected void assertBrokerAttributes(Map<String, Object> brokerDetails)
    {
        Asserts.assertAttributesPresent(brokerDetails, Broker.AVAILABLE_ATTRIBUTES,
                Broker.BYTES_RETAINED, Broker.PROCESS_PID, Broker.SUPPORTED_STORE_TYPES,
                Broker.CREATED, Broker.TIME_TO_LIVE, Broker.UPDATED, Broker.ACL_FILE,
                Broker.KEY_STORE_PATH, Broker.KEY_STORE_PASSWORD, Broker.KEY_STORE_CERT_ALIAS,
                Broker.TRUST_STORE_PATH, Broker.TRUST_STORE_PASSWORD, Broker.GROUP_FILE);

        assertEquals("Unexpected value of attribute " + Broker.BUILD_VERSION, QpidProperties.getBuildVersion(),
                brokerDetails.get(Broker.BUILD_VERSION));
        assertEquals("Unexpected value of attribute " + Broker.OPERATING_SYSTEM, System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " " + System.getProperty("os.arch"),
                brokerDetails.get(Broker.OPERATING_SYSTEM));
        assertEquals(
                "Unexpected value of attribute " + Broker.PLATFORM,
                System.getProperty("java.vendor") + " "
                        + System.getProperty("java.runtime.version", System.getProperty("java.version")),
                brokerDetails.get(Broker.PLATFORM));
        assertEquals("Unexpected value of attribute " + Broker.DURABLE, Boolean.TRUE, brokerDetails.get(Broker.DURABLE));
        assertEquals("Unexpected value of attribute " + Broker.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                brokerDetails.get(Broker.LIFETIME_POLICY));
        assertEquals("Unexpected value of attribute " + Broker.NAME, "QpidBroker", brokerDetails.get(Broker.NAME));
        assertEquals("Unexpected value of attribute " + Broker.STATE, State.ACTIVE.name(), brokerDetails.get(Broker.STATE));

        assertNotNull("Unexpected value of attribute " + Broker.ID, brokerDetails.get(Broker.ID));
        assertNotNull("Unexpected value of attribute statistics", brokerDetails.get(BROKER_STATISTICS_ATTRIBUTE));
        assertNotNull("Unexpected value of attribute virtualhosts", brokerDetails.get(BROKER_VIRTUALHOSTS_ATTRIBUTE));
        assertNotNull("Unexpected value of attribute ports", brokerDetails.get(BROKER_PORTS_ATTRIBUTE));
        assertNotNull("Unexpected value of attribute authenticationproviders", brokerDetails.get(BROKER_AUTHENTICATIONPROVIDERS_ATTRIBUTE));
    }

}
