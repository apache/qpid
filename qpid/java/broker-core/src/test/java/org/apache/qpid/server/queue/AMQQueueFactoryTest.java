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
package org.apache.qpid.server.queue;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.NonDefaultExchange;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AMQQueueFactoryTest extends QpidTestCase
{
    private QueueRegistry _queueRegistry;
    private VirtualHost _virtualHost;
    private AMQQueueFactory _queueFactory;
    private List<AMQQueue> _queues;
    private QueueConfiguration _queueConfiguration;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _queues = new ArrayList<AMQQueue>();

        _virtualHost = mock(VirtualHost.class);
        when(_virtualHost.getSecurityManager()).thenReturn(mock(SecurityManager.class));

        VirtualHostConfiguration vhostConfig = mock(VirtualHostConfiguration.class);
        when(_virtualHost.getConfiguration()).thenReturn(vhostConfig);
        _queueConfiguration = mock(QueueConfiguration.class);
        when(vhostConfig.getQueueConfiguration(anyString())).thenReturn(_queueConfiguration);
        LogActor logActor = mock(LogActor.class);
        CurrentActor.set(logActor);
        RootMessageLogger rootLogger = mock(RootMessageLogger.class);
        when(logActor.getRootMessageLogger()).thenReturn(rootLogger);
        DurableConfigurationStore store = mock(DurableConfigurationStore.class);
        when(_virtualHost.getDurableConfigurationStore()).thenReturn(store);

        mockExchangeCreation();
        mockQueueRegistry();
        delegateVhostQueueCreation();

        when(_virtualHost.getQueues()).thenReturn(_queues);


        _queueFactory = new AMQQueueFactory(_virtualHost, _queueRegistry);



    }

    private void delegateVhostQueueCreation() throws Exception
    {

        final ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);

        when(_virtualHost.createQueue(attributes.capture())).then(
                new Answer<AMQQueue>()
                {
                    @Override
                    public AMQQueue answer(InvocationOnMock invocation) throws Throwable
                    {
                        return _queueFactory.createQueue(attributes.getValue());
                    }
                }
            );
    }

    private void mockQueueRegistry()
    {
        _queueRegistry = mock(QueueRegistry.class);

        final ArgumentCaptor<AMQQueue> capturedQueue = ArgumentCaptor.forClass(AMQQueue.class);
        doAnswer(new Answer()
        {

            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                AMQQueue queue = capturedQueue.getValue();
                when(_queueRegistry.getQueue(eq(queue.getId()))).thenReturn(queue);
                when(_queueRegistry.getQueue(eq(queue.getName()))).thenReturn(queue);
                when(_virtualHost.getQueue(eq(queue.getId()))).thenReturn(queue);
                when(_virtualHost.getQueue(eq(queue.getName()))).thenReturn(queue);
                _queues.add(queue);

                return null;
            }
        }).when(_queueRegistry).registerQueue(capturedQueue.capture());
    }

    private void mockExchangeCreation() throws Exception
    {
        final ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);


        when(_virtualHost.createExchange(attributes.capture())).then(
                new Answer<ExchangeImpl>()
                {
                    @Override
                    public ExchangeImpl answer(InvocationOnMock invocation) throws Throwable
                    {
                        Map attributeValues = attributes.getValue();
                        final String name = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Exchange.NAME, attributeValues);
                        final UUID id = MapValueConverter.getUUIDAttribute(org.apache.qpid.server.model.Exchange.ID, attributeValues);

                        final NonDefaultExchange exchange = mock(NonDefaultExchange.class);
                        ExchangeType exType = mock(ExchangeType.class);

                        when(exchange.getName()).thenReturn(name);
                        when(exchange.getId()).thenReturn(id);
                        when(exchange.getExchangeType()).thenReturn(exType);

                        final String typeName = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Exchange.TYPE, attributeValues);
                        when(exType.getType()).thenReturn(typeName);
                        when(exchange.getTypeName()).thenReturn(typeName);

                        when(_virtualHost.getExchange(eq(name))).thenReturn(exchange);
                        when(_virtualHost.getExchange(eq(id))).thenReturn(exchange);

                        final ArgumentCaptor<AMQQueue> queue = ArgumentCaptor.forClass(AMQQueue.class);

                        when(exchange.addBinding(anyString(), queue.capture(), anyMap())).then(new Answer<Boolean>()
                        {

                            @Override
                            public Boolean answer(InvocationOnMock invocation) throws Throwable
                            {
                                when(exchange.isBound(eq(queue.getValue()))).thenReturn(true);
                                return true;
                            }
                        });

                        return exchange;
                    }
                }
                                                                    );
    }

    @Override
    public void tearDown() throws Exception
    {
        super.tearDown();
    }

    private void verifyRegisteredQueueCount(int count)
    {
        assertEquals("Queue was not registered in virtualhost", count, _virtualHost.getQueues().size());
    }


    private void verifyQueueRegistered(String queueName)
    {
        assertNotNull("Queue " + queueName + " was not created", _virtualHost.getQueue(queueName));
    }

    public void testPriorityQueueRegistration() throws Exception
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, "testPriorityQueue");

        attributes.put(Queue.PRIORITIES, 5);


        AMQQueue queue = _queueFactory.createQueue(attributes);

        assertEquals("Queue not a priority queue", PriorityQueue.class, queue.getClass());
        verifyQueueRegistered("testPriorityQueue");
        verifyRegisteredQueueCount(1);
    }


    public void testSimpleQueueRegistration() throws Exception
    {
        String queueName = getName();
        String dlQueueName = queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;

        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, queueName);


        AMQQueue queue = _queueFactory.createQueue(attributes);
        assertEquals("Queue not a simple queue", StandardQueue.class, queue.getClass());
        verifyQueueRegistered(queueName);

        //verify that no alternate exchange or DLQ were produced

        assertNull("Queue should not have an alternate exchange as DLQ wasn't enabled", queue.getAlternateExchange());
        assertNull("The DLQ should not exist", _virtualHost.getQueue(dlQueueName));

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link QueueArgumentsConverter#X_QPID_DLQ_ENABLED} argument true does
     * cause the alternate exchange to be set and DLQ to be produced.
     */
    public void testDeadLetterQueueEnabled() throws Exception
    {

        String queueName = "testDeadLetterQueueEnabled";
        String dlExchangeName = queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX;
        String dlQueueName = queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;

        assertNull("The DLQ should not yet exist", _virtualHost.getQueue(dlQueueName));
        assertNull("The alternate exchange should not yet exist", _virtualHost.getExchange(dlExchangeName));

        Map<String,Object> attributes = new HashMap<String, Object>();

        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, queueName);
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, true);

        AMQQueue queue = _queueFactory.createQueue(attributes);

        ExchangeImpl altExchange = queue.getAlternateExchange();
        assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
        assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getTypeName());

        assertNotNull("The alternate exchange was not registered as expected", _virtualHost.getExchange(dlExchangeName));
        assertEquals("The registered exchange was not the expected exchange instance", altExchange, _virtualHost.getExchange(dlExchangeName));

        AMQQueue dlQueue = _virtualHost.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
        assertNull("DLQ should have no alternate exchange", dlQueue.getAlternateExchange());
        assertEquals("DLQ should have a zero maximum delivery count", 0, dlQueue.getMaximumDeliveryAttempts());

        //2 queues should have been registered
        verifyRegisteredQueueCount(2);
    }

    /**
     * Tests that the deadLetterQueues/maximumDeliveryCount settings from the configuration
     * are not applied to the DLQ itself.
     */
    public void testDeadLetterQueueDoesNotInheritDLQorMDCSettings() throws Exception
    {

        String queueName = "testDeadLetterQueueEnabled";
        String dlExchangeName = queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX;
        String dlQueueName = queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;

        when(_queueConfiguration.getMaxDeliveryCount()).thenReturn(5);
        when(_queueConfiguration.isDeadLetterQueueEnabled()).thenReturn(true);

        assertNull("The DLQ should not yet exist", _virtualHost.getQueue(dlQueueName));
        assertNull("The alternate exchange should not yet exist", _virtualHost.getExchange(dlExchangeName));

        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, queueName);

        AMQQueue queue = _queueFactory.createQueue(attributes);

        assertEquals("Unexpected maximum delivery count", 5, queue.getMaximumDeliveryAttempts());
        ExchangeImpl altExchange = queue.getAlternateExchange();
        assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
        assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getTypeName());

        assertNotNull("The alternate exchange was not registered as expected", _virtualHost.getExchange(dlExchangeName));
        assertEquals("The registered exchange was not the expected exchange instance", altExchange, _virtualHost.getExchange(dlExchangeName));

        AMQQueue dlQueue = _virtualHost.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
        assertNull("DLQ should have no alternate exchange", dlQueue.getAlternateExchange());
        assertEquals("DLQ should have a zero maximum delivery count", 0, dlQueue.getMaximumDeliveryAttempts());

        //2 queues should have been registered
        verifyRegisteredQueueCount(2);
    }

    /**
     * Tests that setting the {@link QueueArgumentsConverter#X_QPID_DLQ_ENABLED} argument false does not
     * result in the alternate exchange being set and DLQ being created.
     */
    public void testDeadLetterQueueDisabled() throws Exception
    {
        Map<String,Object> attributes = new HashMap<String, Object>();


        String queueName = "testDeadLetterQueueDisabled";
        String dlExchangeName = queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX;
        String dlQueueName = queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;

        assertNull("The DLQ should not yet exist", _virtualHost.getQueue(dlQueueName));
        assertNull("The alternate exchange should not exist", _virtualHost.getExchange(dlExchangeName));

        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, queueName);
        attributes.put(Queue.CREATE_DLQ_ON_CREATION, false);

        AMQQueue queue = _queueFactory.createQueue(attributes);

        assertNull("Queue should not have an alternate exchange as DLQ is disabled", queue.getAlternateExchange());
        assertNull("The alternate exchange should still not exist", _virtualHost.getExchange(dlExchangeName));

        assertNull("The DLQ should still not exist", _virtualHost.getQueue(dlQueueName));

        //only 1 queue should have been registered
        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link QueueArgumentsConverter#X_QPID_DLQ_ENABLED} argument true but
     * creating an auto-delete queue, does not result in the alternate exchange
     * being set and DLQ being created.
     */
    public void testDeadLetterQueueNotCreatedForAutodeleteQueues() throws Exception
    {

        String queueName = "testDeadLetterQueueNotCreatedForAutodeleteQueues";
        String dlExchangeName = queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX;
        String dlQueueName = queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;

        assertNull("The DLQ should not yet exist", _virtualHost.getQueue(dlQueueName));
        assertNull("The alternate exchange should not exist", _virtualHost.getExchange(dlExchangeName));

        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, queueName);

        attributes.put(Queue.CREATE_DLQ_ON_CREATION, true);
        attributes.put(Queue.LIFETIME_POLICY, LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS);

        //create an autodelete queue
        AMQQueue queue = _queueFactory.createQueue(attributes);
        assertEquals("Queue should be autodelete",
                     LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS,
                     queue.getLifetimePolicy());

        //ensure that the autodelete property overrides the request to enable DLQ
        assertNull("Queue should not have an alternate exchange as queue is autodelete", queue.getAlternateExchange());
        assertNull("The alternate exchange should not exist as queue is autodelete", _virtualHost.getExchange(dlExchangeName));
        assertNull("The DLQ should not exist as queue is autodelete", _virtualHost.getQueue(dlQueueName));

        //only 1 queue should have been registered
        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link QueueArgumentsConverter#X_QPID_MAXIMUM_DELIVERY_COUNT} argument has
     * the desired effect.
     */
    public void testMaximumDeliveryCount() throws Exception
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, "testMaximumDeliveryCount");

        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, (Object) 5);

        final AMQQueue queue = _queueFactory.createQueue(attributes);

        assertNotNull("The queue was not registered as expected ", queue);
        assertEquals("Maximum delivery count not as expected", 5, queue.getMaximumDeliveryAttempts());

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that omitting the {@link QueueArgumentsConverter#X_QPID_MAXIMUM_DELIVERY_COUNT} argument means
     * that queue is created with a default maximumDeliveryCount of zero (unless set in config).
     */
    public void testMaximumDeliveryCountDefault() throws Exception
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUID.randomUUID());
        attributes.put(Queue.NAME, "testMaximumDeliveryCountDefault");

        final AMQQueue queue = _queueFactory.createQueue(attributes);

        assertNotNull("The queue was not registered as expected ", queue);
        assertEquals("Maximum delivery count not as expected", 0, queue.getMaximumDeliveryAttempts());

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests queue creation with queue name set to null
     */
    public void testQueueNameNullValidation()
    {
        try
        {
            Map<String,Object> attributes = new HashMap<String, Object>();
            attributes.put(Queue.ID, UUID.randomUUID());

            _queueFactory.createQueue(attributes);
            fail("queue with null name can not be created!");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof IllegalArgumentException);
            assertEquals("Value for attribute name is not found", e.getMessage());
        }
    }

    /**
     * Tests queue creation with queue name length less 255 characters but
     * corresponding DLQ name length greater than 255.
     */
    public void testQueueNameWithLengthLessThan255ButDLQNameWithLengthGreaterThan255()
    {
        String queueName = "test-" + generateStringWithLength('a', 245);
        try
        {
            // change DLQ name to make its length bigger than exchange name
            setTestSystemProperty(BrokerProperties.PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX, "_DLE");
            setTestSystemProperty(BrokerProperties.PROPERTY_DEAD_LETTER_QUEUE_SUFFIX, "_DLQUEUE");

            Map<String,Object> attributes = new HashMap<String, Object>();
            attributes.put(Queue.ID, UUID.randomUUID());
            attributes.put(Queue.NAME, queueName);

            attributes.put(Queue.CREATE_DLQ_ON_CREATION, true);

            _queueFactory.createQueue(attributes);
            fail("queue with DLQ name having more than 255 characters can not be created!");
        }
        catch (Exception e)
        {
            assertTrue("Unexpected exception is thrown!", e instanceof IllegalArgumentException);
            assertTrue("Unexpected exception message!", e.getMessage().contains("DLQ queue name")
                    && e.getMessage().contains("length exceeds limit of 255"));
        }
    }

    /**
     * Tests queue creation with queue name length less 255 characters but
     * corresponding DL exchange name length greater than 255.
     */
    public void testQueueNameWithLengthLessThan255ButDLExchangeNameWithLengthGreaterThan255()
    {
        String queueName = "test-" + generateStringWithLength('a', 245);
        try
        {
            // change DLQ name to make its length bigger than exchange name
            setTestSystemProperty(BrokerProperties.PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX, "_DLEXCHANGE");
            setTestSystemProperty(BrokerProperties.PROPERTY_DEAD_LETTER_QUEUE_SUFFIX, "_DLQ");

            Map<String,Object> attributes = new HashMap<String, Object>();
            attributes.put(Queue.ID, UUID.randomUUID());
            attributes.put(Queue.NAME, queueName);

            attributes.put(Queue.CREATE_DLQ_ON_CREATION, (Object) true);

            _queueFactory.createQueue(attributes);
            fail("queue with DLE name having more than 255 characters can not be created!");
        }
        catch (Exception e)
        {
            assertTrue("Unexpected exception is thrown!", e instanceof IllegalArgumentException);
            assertTrue("Unexpected exception message!", e.getMessage().contains("DL exchange name")
                    && e.getMessage().contains("length exceeds limit of 255"));
        }
    }

    public void testMessageGroupFromConfig() throws Exception
    {

        Map<String,String> arguments = new HashMap<String, String>();

        arguments.put(QueueArgumentsConverter.QPID_GROUP_HEADER_KEY,"mykey");
        arguments.put(QueueArgumentsConverter.QPID_SHARED_MSG_GROUP,"1");

        QueueConfiguration qConf = mock(QueueConfiguration.class);
        when(qConf.getArguments()).thenReturn(arguments);
        when(qConf.getName()).thenReturn("test");

        AMQQueue queue = _queueFactory.createAMQQueueImpl(qConf);
        assertEquals("mykey", queue.getAttribute(Queue.MESSAGE_GROUP_KEY));
        assertEquals(Boolean.TRUE, queue.getAttribute(Queue.MESSAGE_GROUP_SHARED_GROUPS));
    }

    private String generateStringWithLength(char ch, int length)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            sb.append(ch);
        }
        return sb.toString();
    }


}
