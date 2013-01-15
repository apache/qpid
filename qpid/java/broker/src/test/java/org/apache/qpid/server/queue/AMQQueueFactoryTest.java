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

import static org.mockito.Mockito.when;

import org.apache.commons.configuration.XMLConfiguration;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

public class AMQQueueFactoryTest extends QpidTestCase
{
    private QueueRegistry _queueRegistry;
    private VirtualHost _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        BrokerTestHelper.setUp();
        XMLConfiguration configXml = new XMLConfiguration();
        configXml.addProperty("store.class", TestableMemoryMessageStore.class.getName());


        Broker broker = BrokerTestHelper.createBrokerMock();
        if (getName().equals("testDeadLetterQueueDoesNotInheritDLQorMDCSettings"))
        {
            when(broker.getAttribute(Broker.MAXIMUM_DELIVERY_ATTEMPTS)).thenReturn(5);
            when(broker.getAttribute(Broker.DEAD_LETTER_QUEUE_ENABLED)).thenReturn(true);
        }

        _virtualHost = BrokerTestHelper.createVirtualHost(new VirtualHostConfiguration(getName(), configXml, broker));

        _queueRegistry = _virtualHost.getQueueRegistry();

    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _virtualHost.close();
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    private void verifyRegisteredQueueCount(int count)
    {
        assertEquals("Queue was not registered in virtualhost", count, _queueRegistry.getQueues().size());
    }


    private void verifyQueueRegistered(String queueName)
    {
        assertNotNull("Queue " + queueName + " was not created", _queueRegistry.getQueue(queueName));
    }

    public void testPriorityQueueRegistration() throws Exception
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.put(new AMQShortString(AMQQueueFactory.X_QPID_PRIORITIES), 5);


        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "testPriorityQueue", false, "owner", false,
                                           false, _virtualHost, FieldTable.convertToMap(fieldTable));

        assertEquals("Queue not a priorty queue", AMQPriorityQueue.class, queue.getClass());
        verifyQueueRegistered("testPriorityQueue");
        verifyRegisteredQueueCount(1);
    }


    public void testSimpleQueueRegistration() throws Exception
    {
        String queueName = getName();
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner", false,
                                           false, _virtualHost, null);
        assertEquals("Queue not a simple queue", SimpleAMQQueue.class, queue.getClass());
        verifyQueueRegistered(queueName);

        //verify that no alternate exchange or DLQ were produced
        QueueRegistry qReg = _virtualHost.getQueueRegistry();

        assertNull("Queue should not have an alternate exchange as DLQ wasnt enabled", queue.getAlternateExchange());
        assertNull("The DLQ should not exist", qReg.getQueue(dlQueueName));

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true does
     * cause the alternate exchange to be set and DLQ to be produced.
     * @throws AMQException
     */
    public void testDeadLetterQueueEnabled() throws AMQException
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);

        String queueName = "testDeadLetterQueueEnabled";
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not yet exist", exReg.getExchange(dlExchangeName));

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner", false, false,
                                           _virtualHost, FieldTable.convertToMap(fieldTable));

        Exchange altExchange = queue.getAlternateExchange();
        assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
        assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getType().getName());

        assertNotNull("The alternate exchange was not registered as expected", exReg.getExchange(dlExchangeName));
        assertEquals("The registered exchange was not the expected exchange instance", altExchange, exReg.getExchange(dlExchangeName));

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
        assertNull("DLQ should have no alternate exchange", dlQueue.getAlternateExchange());
        assertEquals("DLQ should have a zero maximum delivery count", 0, dlQueue.getMaximumDeliveryCount());

        //2 queues should have been registered
        verifyRegisteredQueueCount(2);
    }

    /**
     * Tests that the deadLetterQueues/maximumDeliveryCount settings from the configuration
     * are not applied to the DLQ itself.
     * @throws AMQException
     */
    public void testDeadLetterQueueDoesNotInheritDLQorMDCSettings() throws Exception
    {
        String queueName = "testDeadLetterQueueEnabled";
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not yet exist", exReg.getExchange(dlExchangeName));

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner", false, false,
                                           _virtualHost, null);

        assertEquals("Unexpected maximum delivery count", 5, queue.getMaximumDeliveryCount());
        Exchange altExchange = queue.getAlternateExchange();
        assertNotNull("Queue should have an alternate exchange as DLQ is enabled", altExchange);
        assertEquals("Alternate exchange name was not as expected", dlExchangeName, altExchange.getName());
        assertEquals("Alternate exchange type was not as expected", ExchangeDefaults.FANOUT_EXCHANGE_CLASS, altExchange.getType().getName());

        assertNotNull("The alternate exchange was not registered as expected", exReg.getExchange(dlExchangeName));
        assertEquals("The registered exchange was not the expected exchange instance", altExchange, exReg.getExchange(dlExchangeName));

        AMQQueue dlQueue = qReg.getQueue(dlQueueName);
        assertNotNull("The DLQ was not registered as expected", dlQueue);
        assertTrue("DLQ should have been bound to the alternate exchange", altExchange.isBound(dlQueue));
        assertNull("DLQ should have no alternate exchange", dlQueue.getAlternateExchange());
        assertEquals("DLQ should have a zero maximum delivery count", 0, dlQueue.getMaximumDeliveryCount());

        //2 queues should have been registered
        verifyRegisteredQueueCount(2);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument false does not
     * result in the alternate exchange being set and DLQ being created.
     * @throws AMQException
     */
    public void testDeadLetterQueueDisabled() throws AMQException
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, false);

        String queueName = "testDeadLetterQueueDisabled";
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner", false, false,
                                           _virtualHost, FieldTable.convertToMap(fieldTable));

        assertNull("Queue should not have an alternate exchange as DLQ is disabled", queue.getAlternateExchange());
        assertNull("The alternate exchange should still not exist", exReg.getExchange(dlExchangeName));

        assertNull("The DLQ should still not exist", qReg.getQueue(dlQueueName));

        //only 1 queue should have been registered
        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_DLQ_ENABLED} argument true but
     * creating an auto-delete queue, does not result in the alternate exchange
     * being set and DLQ being created.
     * @throws AMQException
     */
    public void testDeadLetterQueueNotCreatedForAutodeleteQueues() throws AMQException
    {
        FieldTable fieldTable = new FieldTable();
        fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);

        String queueName = "testDeadLetterQueueNotCreatedForAutodeleteQueues";
        AMQShortString dlExchangeName = new AMQShortString(queueName + DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
        AMQShortString dlQueueName = new AMQShortString(queueName + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX);

        QueueRegistry qReg = _virtualHost.getQueueRegistry();
        ExchangeRegistry exReg = _virtualHost.getExchangeRegistry();

        assertNull("The DLQ should not yet exist", qReg.getQueue(dlQueueName));
        assertNull("The alternate exchange should not exist", exReg.getExchange(dlExchangeName));

        //create an autodelete queue
        AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner", true, false,
                                           _virtualHost, FieldTable.convertToMap(fieldTable));
        assertTrue("Queue should be autodelete", queue.isAutoDelete());

        //ensure that the autodelete property overrides the request to enable DLQ
        assertNull("Queue should not have an alternate exchange as queue is autodelete", queue.getAlternateExchange());
        assertNull("The alternate exchange should not exist as queue is autodelete", exReg.getExchange(dlExchangeName));
        assertNull("The DLQ should not exist as queue is autodelete", qReg.getQueue(dlQueueName));

        //only 1 queue should have been registered
        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that setting the {@link AMQQueueFactory#X_QPID_MAXIMUM_DELIVERY_COUNT} argument has
     * the desired effect.
     */
    public void testMaximumDeliveryCount() throws Exception
    {
        final FieldTable fieldTable = new FieldTable();
        fieldTable.setInteger(AMQQueueFactory.X_QPID_MAXIMUM_DELIVERY_COUNT, 5);

        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "testMaximumDeliveryCount", false, "owner", false, false,
                                           _virtualHost, FieldTable.convertToMap(fieldTable));

        assertNotNull("The queue was not registered as expected ", queue);
        assertEquals("Maximum delivery count not as expected", 5, queue.getMaximumDeliveryCount());

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests that omitting the {@link AMQQueueFactory#X_QPID_MAXIMUM_DELIVERY_COUNT} argument means
     * that queue is created with a default maximumDeliveryCount of zero (unless set in config).
     */
    public void testMaximumDeliveryCountDefault() throws Exception
    {
        final AMQQueue queue = AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), "testMaximumDeliveryCount", false, "owner", false, false,
                                           _virtualHost, null);

        assertNotNull("The queue was not registered as expected ", queue);
        assertEquals("Maximum delivery count not as expected", 0, queue.getMaximumDeliveryCount());

        verifyRegisteredQueueCount(1);
    }

    /**
     * Tests queue creation with queue name set to null
     */
    public void testQueueNameNullValidation()
    {
        try
        {
            AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), null, false, "owner", true, false, _virtualHost, null);
            fail("queue with null name can not be created!");
        }
        catch (Exception e)
        {
            assertTrue(e instanceof IllegalArgumentException);
            assertEquals("Queue name must not be null", e.getMessage());
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
            FieldTable fieldTable = new FieldTable();
            fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);
            AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner",
                    false, false, _virtualHost, FieldTable.convertToMap(fieldTable));
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
            FieldTable fieldTable = new FieldTable();
            fieldTable.setBoolean(AMQQueueFactory.X_QPID_DLQ_ENABLED, true);
            AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateRandomUUID(), queueName, false, "owner",
                    false, false, _virtualHost, FieldTable.convertToMap(fieldTable));
            fail("queue with DLE name having more than 255 characters can not be created!");
        }
        catch (Exception e)
        {
            assertTrue("Unexpected exception is thrown!", e instanceof IllegalArgumentException);
            assertTrue("Unexpected exception message!", e.getMessage().contains("DL exchange name")
                    && e.getMessage().contains("length exceeds limit of 255"));
        }
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
