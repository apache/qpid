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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.test.client.UnroutableMessageTestExceptionListener;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.util.SystemUtils;

public class BrokerRestTest extends QpidRestTestCase
{
    private static final String BROKER_AUTHENTICATIONPROVIDERS_ATTRIBUTE = "authenticationproviders";
    private static final String BROKER_PORTS_ATTRIBUTE = "ports";
    private static final String BROKER_VIRTUALHOST_NODES_ATTRIBUTE = "virtualhostnodes";
    private static final String BROKER_STATISTICS_ATTRIBUTE = "statistics";

    public void testGet() throws Exception
    {
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("broker");

        assertBrokerAttributes(brokerDetails);

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) brokerDetails.get(BROKER_STATISTICS_ATTRIBUTE);
        Asserts.assertAttributesPresent(statistics, new String[]{ "bytesIn", "messagesOut", "bytesOut", "messagesIn" });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) brokerDetails.get(BROKER_VIRTUALHOST_NODES_ATTRIBUTE);
        assertEquals("Unexpected number of virtual hosts", 3, nodes.size());

        for (String nodeName: EXPECTED_VIRTUALHOSTS)
        {
            Map<String, Object> nodeAttributes = getRestTestHelper().find(VirtualHostNode.NAME, nodeName, nodes);
            assertNotNull("Node attributes are not found for node with name " + nodeName, nodeAttributes);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports = (List<Map<String, Object>>) brokerDetails.get(BROKER_PORTS_ATTRIBUTE);
        assertEquals("Unexpected number of ports", 2, ports.size());

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
        assertFalse("AMQP protocol list cannot contain HTTP", port1Protocols != null && port1Protocols.contains("HTTP"));

        @SuppressWarnings("unchecked")
        Collection<String> port2Protocols = (Collection<String>) httpPort.get(Port.PROTOCOLS);
        assertEquals("Unexpected value of attribute " + Port.PROTOCOLS, new HashSet<String>(Arrays.asList("HTTP")),
                new HashSet<String>(port2Protocols));
    }

    public void testPutToUpdateWithValidAttributeValues() throws Exception
    {
        Map<String, Object> brokerAttributes = getValidBrokerAttributes();

        int response = getRestTestHelper().submitRequest("broker", "PUT", brokerAttributes);
        assertEquals("Unexpected update response", 200, response);

        restartBroker();
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("broker");
        assertBrokerAttributes(brokerAttributes, brokerDetails);
    }

    public void testPutUpdateWhereNumericAttributesAreSetAsStringValues() throws Exception
    {
        Map<String, Object> validAttributes = getValidBrokerAttributes();
        Map<String, Object> attributes = new HashMap<String, Object>();

        for (Map.Entry<String, Object> entry : validAttributes.entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Number)
            {
                value = String.valueOf(value);
            }
            attributes.put(entry.getKey(), value);
        }

        int response = getRestTestHelper().submitRequest("broker", "PUT", attributes);
        assertEquals("Unexpected update response", 200, response);

        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("broker");
        assertBrokerAttributes(validAttributes, brokerDetails);
    }

    public void testPutToUpdateWithInvalidAttributeValues() throws Exception
    {
        Map<String, Object> invalidAttributes = new HashMap<String, Object>();
        invalidAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, "non-existing-host");
        invalidAttributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, -10);
        invalidAttributes.put(Broker.CONNECTION_HEART_BEAT_DELAY, -11000);
        invalidAttributes.put(Broker.STATISTICS_REPORTING_PERIOD, -12000);

        for (Map.Entry<String, Object> entry : invalidAttributes.entrySet())
        {
            Map<String, Object> brokerAttributes = getValidBrokerAttributes();
            brokerAttributes.put(entry.getKey(), entry.getValue());
            int response = getRestTestHelper().submitRequest("broker", "PUT", brokerAttributes);
            assertEquals("Unexpected update response for invalid attribute " + entry.getKey() + "=" + entry.getValue(), 409, response);
        }

    }

    public void testSetCloseOnNoRoute() throws Exception
    {
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("broker");
        assertTrue("closeOnNoRoute should be true", (Boolean)brokerDetails.get(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE));

        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE, false);

        int response = getRestTestHelper().submitRequest("broker", "PUT", brokerAttributes);
        assertEquals("Unexpected update response", 200, response);

        brokerDetails = getRestTestHelper().getJsonAsSingletonList("broker");
        assertFalse("closeOnNoRoute should be false", (Boolean)brokerDetails.get(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE));

        Connection connection = getConnection();
        UnroutableMessageTestExceptionListener exceptionListener = new UnroutableMessageTestExceptionListener();
        connection.setExceptionListener(exceptionListener);
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        MessageProducer producer = session.createProducer(getTestQueue());
        TextMessage message = session.createTextMessage("Test");
        producer.send(message);

        session.commit();

        exceptionListener.assertReceivedNoRouteWithReturnedMessage(message, getTestQueueName());
    }

    private Map<String, Object> getValidBrokerAttributes()
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, TEST3_VIRTUALHOST);
        brokerAttributes.put(Broker.CONNECTION_SESSION_COUNT_LIMIT, 10);
        brokerAttributes.put(Broker.CONNECTION_HEART_BEAT_DELAY, 11000);
        brokerAttributes.put(Broker.STATISTICS_REPORTING_PERIOD, 12000);
        brokerAttributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, true);
        return brokerAttributes;
    }

    private void assertBrokerAttributes(Map<String, Object> expectedAttributes, Map<String, Object> actualAttributes)
    {
        for (Map.Entry<String, Object> entry : expectedAttributes.entrySet())
        {
            String attributeName = entry.getKey();
            Object attributeValue = entry.getValue();

            Object currentValue = actualAttributes.get(attributeName);
            assertEquals("Unexpected attribute " + attributeName + " value:", attributeValue, currentValue);
        }
    }

    protected void assertBrokerAttributes(Map<String, Object> brokerDetails)
    {
        Asserts.assertAttributesPresent(brokerDetails, BrokerModel.getInstance().getTypeRegistry().getAttributeNames(
                Broker.class),
                Broker.PROCESS_PID,
                Broker.CONFIDENTIAL_CONFIGURATION_ENCRYPTION_PROVIDER,
                ConfiguredObject.TYPE,
                ConfiguredObject.CREATED_BY,
                ConfiguredObject.CREATED_TIME,
                ConfiguredObject.LAST_UPDATED_BY,
                ConfiguredObject.LAST_UPDATED_TIME,
                ConfiguredObject.DESCRIPTION,
                ConfiguredObject.CONTEXT,
                ConfiguredObject.DESIRED_STATE);

        assertEquals("Unexpected value of attribute " + Broker.BUILD_VERSION, QpidProperties.getBuildVersion(),
                brokerDetails.get(Broker.BUILD_VERSION));
        assertEquals("Unexpected value of attribute " + Broker.OPERATING_SYSTEM, SystemUtils.getOSString(),
                brokerDetails.get(Broker.OPERATING_SYSTEM));
        assertEquals(
                "Unexpected value of attribute " + Broker.PLATFORM,
                System.getProperty("java.vendor") + " "
                        + System.getProperty("java.runtime.version", System.getProperty("java.version")),
                brokerDetails.get(Broker.PLATFORM));
        assertEquals("Unexpected value of attribute " + Broker.DURABLE, Boolean.TRUE, brokerDetails.get(Broker.DURABLE));
        assertEquals("Unexpected value of attribute " + Broker.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                brokerDetails.get(Broker.LIFETIME_POLICY));
        assertEquals("Unexpected value of attribute " + Broker.NAME, "Broker", brokerDetails.get(Broker.NAME));
        assertEquals("Unexpected value of attribute " + Broker.STATE, State.ACTIVE.name(), brokerDetails.get(Broker.STATE));

        assertNotNull("Unexpected value of attribute " + Broker.ID, brokerDetails.get(Broker.ID));
        assertNotNull("Unexpected value of attribute statistics", brokerDetails.get(BROKER_STATISTICS_ATTRIBUTE));
        assertNotNull("Unexpected value of attribute virtual host nodes", brokerDetails.get(BROKER_VIRTUALHOST_NODES_ATTRIBUTE));
        assertNotNull("Unexpected value of attribute ports", brokerDetails.get(BROKER_PORTS_ATTRIBUTE));
        assertNotNull("Unexpected value of attribute authenticationproviders", brokerDetails.get(BROKER_AUTHENTICATIONPROVIDERS_ATTRIBUTE));

    }

}
