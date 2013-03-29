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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.test.utils.TestSSLConstants;

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
        assertFalse("AMQP protocol list cannot contain HTTP", port1Protocols.contains("HTTP"));

        @SuppressWarnings("unchecked")
        Collection<String> port2Protocols = (Collection<String>) httpPort.get(Port.PROTOCOLS);
        assertEquals("Unexpected value of attribute " + Port.PROTOCOLS, new HashSet<String>(Arrays.asList("HTTP")),
                new HashSet<String>(port2Protocols));
    }

    public void testPutToUpdateWithValidAttributeValues() throws Exception
    {
        Map<String, Object> brokerAttributes = getValidBrokerAttributes();

        int response = getRestTestHelper().submitRequest("/rest/broker", "PUT", brokerAttributes);
        assertEquals("Unexpected update response", 200, response);

        restartBroker();
        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/broker");
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

        int response = getRestTestHelper().submitRequest("/rest/broker", "PUT", attributes);
        assertEquals("Unexpected update response", 200, response);

        Map<String, Object> brokerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/broker");
        assertBrokerAttributes(validAttributes, brokerDetails);
    }

    public void testPutToUpdateWithInvalidAttributeValues() throws Exception
    {
        Map<String, Object> invalidAttributes = new HashMap<String, Object>();
        invalidAttributes.put(Broker.DEFAULT_AUTHENTICATION_PROVIDER, "non-existing-provider");
        invalidAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, "non-existing-host");
        invalidAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_AGE, -1000);
        invalidAttributes.put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, -2000);
        invalidAttributes.put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, -3000);
        invalidAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_SIZE, -4000);
        invalidAttributes.put(Broker.ALERT_REPEAT_GAP, -5000);
        invalidAttributes.put(Broker.FLOW_CONTROL_SIZE_BYTES, -7000);
        invalidAttributes.put(Broker.FLOW_CONTROL_RESUME_SIZE_BYTES, -16000);
        invalidAttributes.put(Broker.MAXIMUM_DELIVERY_ATTEMPTS, -8);
        invalidAttributes.put(Broker.HOUSEKEEPING_CHECK_PERIOD, -90000);
        invalidAttributes.put(Broker.SESSION_COUNT_LIMIT, -10);
        invalidAttributes.put(Broker.HEART_BEAT_DELAY, -11000);
        invalidAttributes.put(Broker.STATISTICS_REPORTING_PERIOD, -12000);
        invalidAttributes.put(Broker.ACL_FILE, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "non-existing-acl.acl");
        invalidAttributes.put(Broker.KEY_STORE_PATH, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "non-existing-keystore.jks");
        invalidAttributes.put(Broker.KEY_STORE_PASSWORD, "password1");
        invalidAttributes.put(Broker.KEY_STORE_CERT_ALIAS, "java-broker1");
        invalidAttributes.put(Broker.TRUST_STORE_PATH, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "non-existing-truststore.jks");
        invalidAttributes.put(Broker.TRUST_STORE_PASSWORD, "password2");
        invalidAttributes.put(Broker.PEER_STORE_PATH, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "non-existing-peerstore.jks");
        invalidAttributes.put(Broker.PEER_STORE_PASSWORD, "password3");
        invalidAttributes.put(Broker.GROUP_FILE, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "groups-non-existing");
        invalidAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, -13000);
        invalidAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN, -14000);
        invalidAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, -15000);
        invalidAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN, -16000);

        for (Map.Entry<String, Object> entry : invalidAttributes.entrySet())
        {
            Map<String, Object> brokerAttributes = getValidBrokerAttributes();
            brokerAttributes.put(entry.getKey(), entry.getValue());
            int response = getRestTestHelper().submitRequest("/rest/broker", "PUT", brokerAttributes);
            assertEquals("Unexpected update response for invalid attribute " + entry.getKey() + "=" + entry.getValue(), 409, response);
        }

        // a special case when FLOW_CONTROL_RESUME_SIZE_BYTES > FLOW_CONTROL_SIZE_BYTES
        Map<String, Object> brokerAttributes = getValidBrokerAttributes();
        brokerAttributes.put(Broker.FLOW_CONTROL_SIZE_BYTES, 1000);
        brokerAttributes.put(Broker.FLOW_CONTROL_RESUME_SIZE_BYTES, 2000);
        int response = getRestTestHelper().submitRequest("/rest/broker", "PUT", brokerAttributes);
        assertEquals("Unexpected update response for flow resume size > flow size", 409, response);
    }

    private Map<String, Object> getValidBrokerAttributes()
    {
        Map<String, Object> brokerAttributes = new HashMap<String, Object>();
        brokerAttributes.put(Broker.DEFAULT_AUTHENTICATION_PROVIDER, ANONYMOUS_AUTHENTICATION_PROVIDER);
        brokerAttributes.put(Broker.DEFAULT_VIRTUAL_HOST, TEST3_VIRTUALHOST);
        brokerAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_AGE, 1000);
        brokerAttributes.put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, 2000);
        brokerAttributes.put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, 3000);
        brokerAttributes.put(Broker.ALERT_THRESHOLD_MESSAGE_SIZE, 4000);
        brokerAttributes.put(Broker.ALERT_REPEAT_GAP, 5000);
        brokerAttributes.put(Broker.FLOW_CONTROL_SIZE_BYTES, 7000);
        brokerAttributes.put(Broker.FLOW_CONTROL_RESUME_SIZE_BYTES, 6000);
        brokerAttributes.put(Broker.MAXIMUM_DELIVERY_ATTEMPTS, 8);
        brokerAttributes.put(Broker.DEAD_LETTER_QUEUE_ENABLED, true);
        brokerAttributes.put(Broker.HOUSEKEEPING_CHECK_PERIOD, 90000);
        brokerAttributes.put(Broker.SESSION_COUNT_LIMIT, 10);
        brokerAttributes.put(Broker.HEART_BEAT_DELAY, 11000);
        brokerAttributes.put(Broker.STATISTICS_REPORTING_PERIOD, 12000);
        brokerAttributes.put(Broker.STATISTICS_REPORTING_RESET_ENABLED, true);
        brokerAttributes.put(Broker.ACL_FILE, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "broker_example.acl");
        brokerAttributes.put(Broker.KEY_STORE_PATH, TestSSLConstants.BROKER_KEYSTORE);
        brokerAttributes.put(Broker.KEY_STORE_PASSWORD, TestSSLConstants.BROKER_KEYSTORE_PASSWORD);
        brokerAttributes.put(Broker.KEY_STORE_CERT_ALIAS, "java-broker");
        brokerAttributes.put(Broker.TRUST_STORE_PATH, TestSSLConstants.TRUSTSTORE);
        brokerAttributes.put(Broker.TRUST_STORE_PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);
        brokerAttributes.put(Broker.PEER_STORE_PATH, TestSSLConstants.TRUSTSTORE);
        brokerAttributes.put(Broker.PEER_STORE_PASSWORD, TestSSLConstants.TRUSTSTORE_PASSWORD);
        brokerAttributes.put(Broker.GROUP_FILE, QpidTestCase.QPID_HOME + File.separator + "etc" + File.separator + "groups");
        brokerAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, 13000);
        brokerAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN, 14000);
        brokerAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, 15000);
        brokerAttributes.put(Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN, 16000);
        return brokerAttributes;
    }

    private void assertBrokerAttributes(Map<String, Object> expectedAttributes, Map<String, Object> actualAttributes)
    {
        for (Map.Entry<String, Object> entry : expectedAttributes.entrySet())
        {
            String attributeName = entry.getKey();
            Object attributeValue = entry.getValue();
            if (attributeName.equals(Broker.KEY_STORE_PASSWORD) || attributeName.equals(Broker.TRUST_STORE_PASSWORD) || attributeName.equals(Broker.PEER_STORE_PASSWORD))
            {
                attributeValue = "********";
            }
            Object currentValue = actualAttributes.get(attributeName);
            assertEquals("Unexpected attribute " + attributeName + " value:", attributeValue, currentValue);
        }
    }

    protected void assertBrokerAttributes(Map<String, Object> brokerDetails)
    {
        Asserts.assertAttributesPresent(brokerDetails, Broker.AVAILABLE_ATTRIBUTES,
                Broker.BYTES_RETAINED, Broker.PROCESS_PID, Broker.SUPPORTED_STORE_TYPES,
                Broker.CREATED, Broker.TIME_TO_LIVE, Broker.UPDATED, Broker.ACL_FILE,
                Broker.KEY_STORE_PATH, Broker.KEY_STORE_PASSWORD, Broker.KEY_STORE_CERT_ALIAS,
                Broker.TRUST_STORE_PATH, Broker.TRUST_STORE_PASSWORD, Broker.GROUP_FILE,
                Broker.PEER_STORE_PATH, Broker.PEER_STORE_PASSWORD);

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
