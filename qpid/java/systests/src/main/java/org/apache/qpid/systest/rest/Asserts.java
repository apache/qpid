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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import javax.jms.JMSException;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.queue.LastValueQueue;
import org.apache.qpid.server.queue.PriorityQueue;
import org.apache.qpid.server.queue.SortedQueue;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class Asserts
{
    public static final String STATISTICS_ATTRIBUTE = "statistics";

    public static void assertVirtualHostNode(final String nodeName, final Map<String, Object> node)
    {
        assertNotNull("Virtualhostnode " + nodeName + " data is not found", node);
        assertEquals("Unexpected value of attribute " + VirtualHostNode.NAME,
                     nodeName,
                     node.get(VirtualHostNode.NAME));
    }

    public static void assertVirtualHost(String virtualHostName, Map<String, Object> virtualHost)
    {
        assertNotNull("Virtualhost " + virtualHostName + " data are not found", virtualHost);
        assertAttributesPresent(virtualHost,
                                BrokerModel.getInstance().getTypeRegistry().getAttributeNames(VirtualHost.class),
                                ConfiguredObject.CREATED_BY,
                                ConfiguredObject.CREATED_TIME,
                                ConfiguredObject.LAST_UPDATED_BY,
                                ConfiguredObject.LAST_UPDATED_TIME,
                                ConfiguredObject.DESCRIPTION,
                                ConfiguredObject.CONTEXT,
                                ConfiguredObject.DESIRED_STATE,
                                VirtualHost.ENABLED_CONNECTION_VALIDATORS,
                                VirtualHost.DISABLED_CONNECTION_VALIDATORS,
                                VirtualHost.TYPE);

        assertEquals("Unexpected value of attribute " + VirtualHost.NAME,
                     virtualHostName,
                     virtualHost.get(VirtualHost.NAME));
        assertNotNull("Unexpected value of attribute " + VirtualHost.ID, virtualHost.get(VirtualHost.ID));
        assertEquals("Unexpected value of attribute " + VirtualHost.STATE, State.ACTIVE.name(),
                     virtualHost.get(VirtualHost.STATE));
        assertEquals("Unexpected value of attribute " + VirtualHost.DURABLE, Boolean.TRUE,
                     virtualHost.get(VirtualHost.DURABLE));
        assertEquals("Unexpected value of attribute " + VirtualHost.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                     virtualHost.get(VirtualHost.LIFETIME_POLICY));
        assertEquals("Unexpected value of attribute " + VirtualHost.QUEUE_DEAD_LETTER_QUEUE_ENABLED, Boolean.FALSE,
                     virtualHost.get(VirtualHost.QUEUE_DEAD_LETTER_QUEUE_ENABLED));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) virtualHost.get(STATISTICS_ATTRIBUTE);
        Asserts.assertAttributesPresent(statistics,
                                        "queueCount","exchangeCount","bytesIn","bytesOut","messagesIn", "messagesOut");

    }

    public static void assertQueue(String queueName, String queueType, Map<String, Object> queueData)
    {
        assertQueue(queueName, queueType, queueData, null);
    }

    public static void assertQueue(String queueName,
                                   String queueType,
                                   Map<String, Object> queueData,
                                   Map<String, Object> expectedAttributes)
    {
        assertNotNull("Queue " + queueName + " is not found!", queueData);
        Asserts.assertAttributesPresent(queueData,
                                        BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Queue.class),
                                        Queue.CREATED_BY,
                                        Queue.CREATED_TIME,
                                        Queue.LAST_UPDATED_BY,
                                        Queue.LAST_UPDATED_TIME,
                                        Queue.TYPE,
                                        Queue.DESCRIPTION,
                                        Queue.ALTERNATE_EXCHANGE,
                                        Queue.OWNER,
                                        Queue.NO_LOCAL,
                                        LastValueQueue.LVQ_KEY,
                                        SortedQueue.SORT_KEY,
                                        Queue.MESSAGE_GROUP_KEY,
                                        Queue.MESSAGE_GROUP_SHARED_GROUPS,
                                        PriorityQueue.PRIORITIES,
                                        ConfiguredObject.CONTEXT,
                                        ConfiguredObject.DESIRED_STATE,
                                        Queue.DEFAULT_FILTERS,
                                        Queue.ENSURE_NONDESTRUCTIVE_CONSUMERS);

        assertEquals("Unexpected value of queue attribute " + Queue.NAME, queueName, queueData.get(Queue.NAME));
        assertNotNull("Unexpected value of queue attribute " + Queue.ID, queueData.get(Queue.ID));
        assertEquals("Unexpected value of queue attribute " + Queue.STATE,
                     State.ACTIVE.name(),
                     queueData.get(Queue.STATE));
        assertEquals("Unexpected value of queue attribute " + Queue.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                     queueData.get(Queue.LIFETIME_POLICY));
        assertEquals("Unexpected value of queue attribute " + Queue.TYPE,
                     queueType,
                     queueData.get(Queue.TYPE));
        if (expectedAttributes == null)
        {
            assertEquals("Unexpected value of queue attribute " + Queue.EXCLUSIVE,
                         ExclusivityPolicy.NONE.name(), queueData.get(Queue.EXCLUSIVE));
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

        Asserts.assertAttributesPresent(statistics,
                                        "bindingCount",
                                        "consumerCount",
                                        "consumerCountWithCredit",
                                        "persistentDequeuedBytes",
                                        "persistentDequeuedMessages",
                                        "persistentEnqueuedBytes",
                                        "persistentEnqueuedMessages",
                                        "queueDepthBytes",
                                        "queueDepthMessages",
                                        "totalDequeuedBytes",
                                        "totalDequeuedMessages",
                                        "totalEnqueuedBytes",
                                        "totalEnqueuedMessages",
                                        "unacknowledgedBytes",
                                        "unacknowledgedMessages");
    }

    public static void assertAttributesPresent(Map<String, Object> data, String... attributes)
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

    public static void assertConnection(Map<String, Object> connectionData, AMQConnection connection)
            throws JMSException
    {
        assertNotNull("Unexpected connection data", connectionData);
        assertAttributesPresent(connectionData,
                                BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Connection.class),
                                Connection.STATE,
                                Connection.DURABLE,
                                Connection.LIFETIME_POLICY,
                                Connection.INCOMING,
                                Connection.REMOTE_PROCESS_NAME,
                                Connection.LOCAL_ADDRESS,
                                Connection.PROPERTIES,
                                ConfiguredObject.TYPE,
                                ConfiguredObject.CREATED_BY,
                                ConfiguredObject.CREATED_TIME,
                                ConfiguredObject.LAST_UPDATED_BY,
                                ConfiguredObject.LAST_UPDATED_TIME,
                                ConfiguredObject.DESCRIPTION,
                                ConfiguredObject.CONTEXT,
                                ConfiguredObject.DESIRED_STATE);

        assertEquals("Unexpected value for connection attribute " + Connection.PORT,
                     TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT, connectionData.get(Connection.PORT));
        assertEquals("Unexpected value of connection attribute " + Connection.SESSION_COUNT_LIMIT,
                     (int) connection.getMaximumChannelCount(), connectionData.get(Connection.SESSION_COUNT_LIMIT));
        assertEquals("Unexpected value of connection attribute " + Connection.CLIENT_ID, "clientid",
                     connectionData.get(Connection.CLIENT_ID));
        assertEquals("Unexpected value of connection attribute " + Connection.PRINCIPAL, "guest",
                     connectionData.get(Connection.PRINCIPAL));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) connectionData.get(STATISTICS_ATTRIBUTE);


        assertAttributesPresent(statistics,
                                "bytesIn",
                                "bytesOut",
                                "lastIoTime",
                                "messagesIn",
                                "messagesOut",
                                "sessionCount");
        assertEquals("Unexpected value of connection statistics attribute sessionCount ", 1,
                     statistics.get("sessionCount"));
    }

    public static void assertPortAttributes(Map<String, Object> port)
    {
        assertPortAttributes(port, State.ACTIVE);
    }

    public static void assertPortAttributes(Map<String, Object> port, State state)
    {
        assertNotNull("Unexpected value of attribute " + Port.ID, port.get(Port.ID));
        assertEquals("Unexpected value of attribute " + Port.DURABLE, Boolean.TRUE, port.get(Port.DURABLE));
        assertEquals("Unexpected value of attribute " + Port.LIFETIME_POLICY, LifetimePolicy.PERMANENT.name(),
                     port.get(Broker.LIFETIME_POLICY));
        assertEquals("Unexpected value of attribute " + Port.STATE, state.name(), port.get(Port.STATE));

        @SuppressWarnings("unchecked")
        Collection<String> protocols = (Collection<String>) port.get(Port.PROTOCOLS);

        if ("AMQP".equals(port.get(ConfiguredObject.TYPE)))
        {
            assertAttributesPresent(port,
                                    BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Port.class),
                                    ConfiguredObject.TYPE,
                                    ConfiguredObject.CREATED_BY,
                                    ConfiguredObject.CREATED_TIME,
                                    ConfiguredObject.LAST_UPDATED_BY,
                                    ConfiguredObject.LAST_UPDATED_TIME,
                                    ConfiguredObject.DESCRIPTION,
                                    ConfiguredObject.CONTEXT,
                                    ConfiguredObject.DESIRED_STATE,
                                    Port.AUTHENTICATION_PROVIDER,
                                    Port.KEY_STORE,
                                    Port.TRUST_STORES,
                                    Port.PROTOCOLS);
            assertNotNull("Unexpected value of attribute " + Port.BINDING_ADDRESS, port.get(Port.BINDING_ADDRESS));
        }
        else
        {
            assertAttributesPresent(port,
                                    BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Port.class),
                                    ConfiguredObject.TYPE,
                                    ConfiguredObject.CREATED_BY,
                                    ConfiguredObject.CREATED_TIME,
                                    ConfiguredObject.LAST_UPDATED_BY,
                                    ConfiguredObject.LAST_UPDATED_TIME,
                                    ConfiguredObject.DESCRIPTION,
                                    ConfiguredObject.CONTEXT,
                                    ConfiguredObject.DESIRED_STATE,
                                    Port.AUTHENTICATION_PROVIDER,
                                    Port.BINDING_ADDRESS,
                                    Port.TCP_NO_DELAY,
                                    AmqpPort.SEND_BUFFER_SIZE,
                                    AmqpPort.RECEIVE_BUFFER_SIZE,
                                    Port.NEED_CLIENT_AUTH,
                                    Port.WANT_CLIENT_AUTH,
                                    Port.KEY_STORE,
                                    Port.TRUST_STORES,
                                    Port.PROTOCOLS);
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
        assertAttributesPresent(exchangeData, BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Exchange.class),
                                Exchange.ALTERNATE_EXCHANGE,
                                ConfiguredObject.CREATED_BY,
                                ConfiguredObject.CREATED_TIME,
                                ConfiguredObject.LAST_UPDATED_BY,
                                ConfiguredObject.LAST_UPDATED_TIME,
                                ConfiguredObject.DESCRIPTION,
                                ConfiguredObject.CONTEXT,
                                ConfiguredObject.DESIRED_STATE);

        assertEquals("Unexpected value of exchange attribute " + Exchange.NAME, exchangeName,
                     exchangeData.get(Exchange.NAME));
        assertNotNull("Unexpected value of exchange attribute " + Exchange.ID, exchangeData.get(VirtualHost.ID));
        assertEquals("Unexpected value of exchange attribute " + Exchange.STATE, State.ACTIVE.name(),
                     exchangeData.get(Exchange.STATE));

        assertEquals("Unexpected value of exchange attribute " + Exchange.LIFETIME_POLICY,
                     LifetimePolicy.PERMANENT.name(),
                     exchangeData.get(Exchange.LIFETIME_POLICY));
        assertEquals("Unexpected value of exchange attribute " + Exchange.TYPE, type, exchangeData.get(Exchange.TYPE));
        assertNotNull("Unexpected value of exchange attribute statistics", exchangeData.get(STATISTICS_ATTRIBUTE));

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) exchangeData.get(STATISTICS_ATTRIBUTE);

        assertAttributesPresent(statistics,"bindingCount",
                                "bytesDropped",
                                "bytesIn",
                                "messagesDropped",
                                "messagesIn");
    }

    public static void assertBinding(String bindingName, String queueName, String exchange, Map<String, Object> binding)
    {
        assertNotNull("Binding map should not be null", binding);
        assertAttributesPresent(binding,
                                BrokerModel.getInstance().getTypeRegistry().getAttributeNames(Binding.class),
                                Binding.STATE,
                                Binding.ARGUMENTS,
                                ConfiguredObject.TYPE,
                                ConfiguredObject.CREATED_BY,
                                ConfiguredObject.CREATED_TIME,
                                ConfiguredObject.LAST_UPDATED_BY,
                                ConfiguredObject.LAST_UPDATED_TIME,
                                ConfiguredObject.DESCRIPTION,
                                ConfiguredObject.CONTEXT,
                                ConfiguredObject.DESIRED_STATE);

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

    public static void assertActualAndDesiredState(final String expectedDesiredState,
                                             final String expectedActualState,
                                             final Map<String, Object> data)
    {
        String name = (String) data.get(ConfiguredObject.NAME);
        TestCase.assertEquals("Object with name " + name + " has unexpected desired state",
                              expectedDesiredState,
                              data.get(ConfiguredObject.DESIRED_STATE));
        TestCase.assertEquals("Object with name " + name + " has unexpected actual state",
                              expectedActualState, data.get(ConfiguredObject.STATE));
    }
}
