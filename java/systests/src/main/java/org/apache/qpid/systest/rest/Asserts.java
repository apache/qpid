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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

public class Asserts
{
    public static final String STATISTICS_ATTRIBUTE = "statistics";

    public static void assertVirtualHost(String virtualHostName, Map<String, Object> virtualHost)
    {
        assertNotNull("Virtualhost " + virtualHostName + " data are not found", virtualHost);
        assertAttributesPresent(virtualHost, VirtualHost.AVAILABLE_ATTRIBUTES, VirtualHost.TIME_TO_LIVE,
                VirtualHost.CREATED, VirtualHost.UPDATED, VirtualHost.SUPPORTED_QUEUE_TYPES, VirtualHost.STORE_PATH, VirtualHost.CONFIG_PATH);

        assertEquals("Unexpected value of attribute " + VirtualHost.NAME, virtualHostName, virtualHost.get(VirtualHost.NAME));
        assertNotNull("Unexpected value of attribute " + VirtualHost.ID, virtualHost.get(VirtualHost.ID));
        assertEquals("Unexpected value of attribute " + VirtualHost.STATE, State.ACTIVE.name(),
                virtualHost.get(VirtualHost.STATE));
        assertEquals("Unexpected value of attribute " + VirtualHost.DURABLE, Boolean.TRUE,
                virtualHost.get(VirtualHost.DURABLE));
        assertEquals("Unexpected value of attribute " + VirtualHost.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                virtualHost.get(VirtualHost.LIFETIME_POLICY));
        assertEquals("Unexpected value of attribute " + VirtualHost.DEAD_LETTER_QUEUE_ENABLED, Boolean.FALSE,
                virtualHost.get(VirtualHost.DEAD_LETTER_QUEUE_ENABLED));

        @SuppressWarnings("unchecked")
        Collection<String> exchangeTypes = (Collection<String>) virtualHost.get(VirtualHost.SUPPORTED_EXCHANGE_TYPES);
        assertEquals("Unexpected value of attribute " + VirtualHost.SUPPORTED_EXCHANGE_TYPES,
                new HashSet<String>(Arrays.asList("headers", "topic", "direct", "fanout")),
                new HashSet<String>(exchangeTypes));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) virtualHost.get(STATISTICS_ATTRIBUTE);
        Asserts.assertAttributesPresent(statistics, VirtualHost.AVAILABLE_STATISTICS, VirtualHost.BYTES_RETAINED,
                VirtualHost.LOCAL_TRANSACTION_BEGINS, VirtualHost.LOCAL_TRANSACTION_ROLLBACKS,
                VirtualHost.MESSAGES_RETAINED, VirtualHost.STATE_CHANGED, VirtualHost.XA_TRANSACTION_BRANCH_ENDS,
                VirtualHost.XA_TRANSACTION_BRANCH_STARTS, VirtualHost.XA_TRANSACTION_BRANCH_SUSPENDS);

    }

    public static void assertQueue(String queueName, String queueType, Map<String, Object> queueData)
    {
        assertQueue(queueName, queueType, queueData, null);
    }

    public static void assertQueue(String queueName, String queueType, Map<String, Object> queueData, Map<String, Object> expectedAttributes)
    {
        assertNotNull("Queue " + queueName + " is not found!", queueData);
        Asserts.assertAttributesPresent(queueData, Queue.AVAILABLE_ATTRIBUTES, Queue.CREATED, Queue.UPDATED,
                Queue.DESCRIPTION, Queue.TIME_TO_LIVE, Queue.ALTERNATE_EXCHANGE, Queue.OWNER, Queue.NO_LOCAL, Queue.LVQ_KEY,
                Queue.SORT_KEY, Queue.MESSAGE_GROUP_KEY, Queue.MESSAGE_GROUP_DEFAULT_GROUP,
                Queue.MESSAGE_GROUP_SHARED_GROUPS, Queue.PRIORITIES);

        assertEquals("Unexpected value of queue attribute " + Queue.NAME, queueName, queueData.get(Queue.NAME));
        assertNotNull("Unexpected value of queue attribute " + Queue.ID, queueData.get(Queue.ID));
        assertEquals("Unexpected value of queue attribute " + Queue.STATE, State.ACTIVE.name(), queueData.get(Queue.STATE));
        assertEquals("Unexpected value of queue attribute " + Queue.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                queueData.get(Queue.LIFETIME_POLICY));
        assertEquals("Unexpected value of queue attribute " + Queue.TYPE, queueType, queueData.get(Queue.TYPE));
        if (expectedAttributes == null)
        {
            assertEquals("Unexpected value of queue attribute " + Queue.EXCLUSIVE, Boolean.FALSE, queueData.get(Queue.EXCLUSIVE));
            assertEquals("Unexpected value of queue attribute " + Queue.MAXIMUM_DELIVERY_ATTEMPTS, 0,
                    queueData.get(Queue.MAXIMUM_DELIVERY_ATTEMPTS));
            assertEquals("Unexpected value of queue attribute " + Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 0,
                    queueData.get(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES));
            assertEquals("Unexpected value of queue attribute " + Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 0,
                    queueData.get(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES));
            assertEquals("Unexpected value of queue attribute " + Queue.QUEUE_FLOW_STOPPED, Boolean.FALSE,
                    queueData.get(Queue.QUEUE_FLOW_STOPPED));
        }
        else
        {
            for (Map.Entry<String, Object> attribute : expectedAttributes.entrySet())
            {
                assertEquals("Unexpected value of " + queueName + " queue attribute " + attribute.getKey(),
                        attribute.getValue(), queueData.get(attribute.getKey()));
            }
        }

        assertNotNull("Unexpected value of queue attribute statistics", queueData.get(Asserts.STATISTICS_ATTRIBUTE));
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) queueData.get(Asserts.STATISTICS_ATTRIBUTE);
        Asserts.assertAttributesPresent(statistics, Queue.AVAILABLE_STATISTICS, Queue.DISCARDS_TTL_BYTES,
                Queue.DISCARDS_TTL_MESSAGES, Queue.STATE_CHANGED);
    }

    public static void assertAttributesPresent(Map<String, Object> data, String[] attributes)
    {
        for (String name : attributes)
        {
            assertNotNull("Attribute " + name + " is not present", data.get(name));
        }
    }

    public static void assertAttributesPresent(Map<String, Object> data, Collection<String> attributes,
            String... unsupportedAttributes)
    {
        for (String name : attributes)
        {
            boolean unsupported = false;
            for (String unsupportedAttribute : unsupportedAttributes)
            {
                if (unsupportedAttribute.equals(name))
                {
                    unsupported = true;
                    break;
                }
            }
            if (unsupported)
            {
                continue;
            }
            assertNotNull("Attribute " + name + " is not present", data.get(name));
        }
    }

    public static void assertConnection(Map<String, Object> connectionData, AMQConnection connection) throws JMSException
    {
        assertNotNull("Unexpected connection data", connectionData);
        assertAttributesPresent(connectionData, Connection.AVAILABLE_ATTRIBUTES, Connection.STATE, Connection.DURABLE,
                Connection.LIFETIME_POLICY, Connection.TIME_TO_LIVE, Connection.CREATED, Connection.UPDATED,
                Connection.INCOMING, Connection.REMOTE_PROCESS_NAME, Connection.REMOTE_PROCESS_PID,
                Connection.LOCAL_ADDRESS, Connection.PROPERTIES);

        assertEquals("Unexpected value of connection attribute " + Connection.SESSION_COUNT_LIMIT,
                (int) connection.getMaximumChannelCount(), connectionData.get(Connection.SESSION_COUNT_LIMIT));
        assertEquals("Unexpected value of connection attribute " + Connection.CLIENT_ID, "clientid",
                connectionData.get(Connection.CLIENT_ID));
        assertEquals("Unexpected value of connection attribute " + Connection.PRINCIPAL, "guest",
                connectionData.get(Connection.PRINCIPAL));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) connectionData.get(STATISTICS_ATTRIBUTE);
        assertAttributesPresent(statistics, Connection.AVAILABLE_STATISTICS, Connection.LOCAL_TRANSACTION_BEGINS,
                Connection.LOCAL_TRANSACTION_ROLLBACKS, Connection.STATE_CHANGED, Connection.XA_TRANSACTION_BRANCH_ENDS,
                Connection.XA_TRANSACTION_BRANCH_STARTS, Connection.XA_TRANSACTION_BRANCH_SUSPENDS);
        assertEquals("Unexpected value of connection statistics attribute " + Connection.SESSION_COUNT, 1,
                statistics.get(Connection.SESSION_COUNT));
    }

    public static void assertPortAttributes(Map<String, Object> port)
    {

        assertNotNull("Unexpected value of attribute " + Port.ID, port.get(Port.ID));
        assertEquals("Unexpected value of attribute " + Port.DURABLE, Boolean.FALSE, port.get(Port.DURABLE));
        assertEquals("Unexpected value of attribute " + Port.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                port.get(Broker.LIFETIME_POLICY));
        assertEquals("Unexpected value of attribute " + Port.STATE, State.ACTIVE.name(), port.get(Port.STATE));
        assertEquals("Unexpected value of attribute " + Port.TIME_TO_LIVE, 0, port.get(Port.TIME_TO_LIVE));

        @SuppressWarnings("unchecked")
        Collection<String> protocols = (Collection<String>) port.get(Port.PROTOCOLS);
        assertNotNull("Unexpected value of attribute " + Port.PROTOCOLS, protocols);
        boolean isAMQPPort = false;
        for (String protocolName : protocols)
        {
            if (Protocol.valueOf(protocolName).isAMQP())
            {
                isAMQPPort = true;
                break;
            }
        }
        if (isAMQPPort)
        {
            assertAttributesPresent(port, Port.AVAILABLE_ATTRIBUTES, Port.CREATED, Port.UPDATED, Port.AUTHENTICATION_MANAGER);
            assertNotNull("Unexpected value of attribute " + Port.BINDING_ADDRESS, port.get(Port.BINDING_ADDRESS));
        }
        else
        {
            assertAttributesPresent(port, Port.AVAILABLE_ATTRIBUTES, Port.CREATED, Port.UPDATED, Port.AUTHENTICATION_MANAGER,
                    Port.BINDING_ADDRESS, Port.TCP_NO_DELAY, Port.SEND_BUFFER_SIZE, Port.RECEIVE_BUFFER_SIZE,
                    Port.NEED_CLIENT_AUTH, Port.WANT_CLIENT_AUTH);
        }

        @SuppressWarnings("unchecked")
        Collection<String> transports = (Collection<String>) port.get(Port.TRANSPORTS);
        assertEquals("Unexpected value of attribute " + Port.TRANSPORTS, new HashSet<String>(Arrays.asList("TCP")),
                new HashSet<String>(transports));
    }

    public static void assertDurableExchange(String exchangeName, String type, Map<String, Object> exchangeData)
    {
        assertExchange(exchangeName, type, exchangeData);

        assertEquals("Unexpected value of exchange attribute " + Exchange.DURABLE, Boolean.TRUE,
                exchangeData.get(Exchange.DURABLE));
    }

    public static void assertExchange(String exchangeName, String type, Map<String, Object> exchangeData)
    {
        assertNotNull("Exchange " + exchangeName + " is not found!", exchangeData);
        assertAttributesPresent(exchangeData, Exchange.AVAILABLE_ATTRIBUTES, Exchange.CREATED, Exchange.UPDATED,
                Exchange.ALTERNATE_EXCHANGE, Exchange.TIME_TO_LIVE);

        assertEquals("Unexpected value of exchange attribute " + Exchange.NAME, exchangeName,
                exchangeData.get(Exchange.NAME));
        assertNotNull("Unexpected value of exchange attribute " + Exchange.ID, exchangeData.get(VirtualHost.ID));
        assertEquals("Unexpected value of exchange attribute " + Exchange.STATE, State.ACTIVE.name(),
                exchangeData.get(Exchange.STATE));

        assertEquals("Unexpected value of exchange attribute " + Exchange.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                exchangeData.get(Exchange.LIFETIME_POLICY));
        assertEquals("Unexpected value of exchange attribute " + Exchange.TYPE, type, exchangeData.get(Exchange.TYPE));
        assertNotNull("Unexpected value of exchange attribute statistics", exchangeData.get(STATISTICS_ATTRIBUTE));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) exchangeData.get(STATISTICS_ATTRIBUTE);
        assertAttributesPresent(statistics, Exchange.AVAILABLE_STATISTICS, Exchange.STATE_CHANGED, Exchange.PRODUCER_COUNT);
    }

    public static void assertBinding(String bindingName, String queueName, String exchange, Map<String, Object> binding)
    {
        assertNotNull("Binding map should not be null", binding);
        assertAttributesPresent(binding, Binding.AVAILABLE_ATTRIBUTES, Binding.STATE, Binding.TIME_TO_LIVE,
                Binding.CREATED, Binding.UPDATED);

        assertEquals("Unexpected binding attribute " + Binding.NAME, bindingName, binding.get(Binding.NAME));
        assertEquals("Unexpected binding attribute " + Binding.QUEUE, queueName, binding.get(Binding.QUEUE));
        assertEquals("Unexpected binding attribute " + Binding.EXCHANGE, exchange, binding.get(Binding.EXCHANGE));
        assertEquals("Unexpected binding attribute " + Binding.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                binding.get(Binding.LIFETIME_POLICY));
    }

    public static void assertBinding(String queueName, String exchange, Map<String, Object> binding)
    {
        assertBinding(queueName, queueName, exchange, binding);
    }

}
