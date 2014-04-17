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
package org.apache.qpid.server.store;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.amqp_8_0.BasicConsumeBodyImpl;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v0_8.MessageMetaData;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.LastValueQueue;
import org.apache.qpid.server.queue.LastValueQueueImpl;
import org.apache.qpid.server.queue.PriorityQueue;
import org.apache.qpid.server.queue.PriorityQueueImpl;
import org.apache.qpid.server.queue.StandardQueueImpl;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;
import org.apache.qpid.server.virtualhost.StandardVirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

/**
 *
 * Virtualhost/store integration test. Tests for correct behaviour of the message store
 * when exercised via the higher level functions of the store.
 *
 * For persistent stores, it validates that Exchanges, Queues, Bindings and
 * Messages are persisted and recovered correctly.
 */
public class VirtualHostMessageStoreTest extends QpidTestCase
{
    private static final Logger _logger = Logger.getLogger(VirtualHostMessageStoreTest.class);

    public static final int DEFAULT_PRIORTY_LEVEL = 5;
    public static final String SELECTOR_VALUE = "Test = 'MST'";
    public static final String LVQ_KEY = "MST-LVQ-KEY";

    private String nonDurableExchangeName = "MST-NonDurableDirectExchange";
    private String directExchangeName = "MST-DirectExchange";
    private String topicExchangeName = "MST-TopicExchange";

    private String durablePriorityTopicQueueName = "MST-PriorityTopicQueue-Durable";
    private String durableTopicQueueName = "MST-TopicQueue-Durable";
    private String priorityTopicQueueName = "MST-PriorityTopicQueue";
    private String topicQueueName = "MST-TopicQueue";

    private String durableExclusiveQueueName = "MST-Queue-Durable-Exclusive";
    private String durablePriorityQueueName = "MST-PriorityQueue-Durable";
    private String durableLastValueQueueName = "MST-LastValueQueue-Durable";
    private String durableQueueName = "MST-Queue-Durable";
    private String priorityQueueName = "MST-PriorityQueue";
    private String queueName = "MST-Queue";

    private String directRouting = "MST-direct";
    private String topicRouting = "MST-topic";

    private String queueOwner = "MST";

    private AbstractVirtualHost<?> _virtualHost;
    private String _storePath;
    private Map<String, Object> _attributes;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();

        String hostName = getName();
        _storePath = System.getProperty("QPID_WORK", TMP_FOLDER + File.separator + getTestName()) + File.separator + hostName;

        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(MessageStore.STORE_PATH, _storePath);
        messageStoreSettings.put(MessageStore.STORE_TYPE, getTestProfileMessageStoreType());

        _attributes = new HashMap<String, Object>();
        _attributes.put(org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS, messageStoreSettings);
        _attributes.put(org.apache.qpid.server.model.VirtualHost.TYPE, StandardVirtualHost.TYPE);
        _attributes.put(org.apache.qpid.server.model.VirtualHost.NAME, hostName);


        cleanup(new File(_storePath));

        reloadVirtualHost();
    }

    protected String getStorePath()
    {
        return _storePath;
    }

    protected org.apache.qpid.server.model.VirtualHost<?,?,?> getVirtualHostModel()
    {
        return _virtualHost;
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                _virtualHost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public VirtualHostImpl getVirtualHost()
    {
        return _virtualHost;
    }

    protected void reloadVirtualHost()
    {
        VirtualHostImpl original = getVirtualHost();

        if (getVirtualHost() != null)
        {
            try
            {
                getVirtualHost().close();
            }
            catch (Exception e)
            {
                _logger.error("Error closing virtual host", e);
                fail(e.getMessage());
            }
        }

        try
        {
            _virtualHost = (AbstractVirtualHost<?>) BrokerTestHelper.createVirtualHost(new VirtualHostRegistry(new EventLogger()), _attributes);
        }
        catch (Exception e)
        {
            _logger.error("Error creating virtual host", e);
            fail(e.getMessage());
        }

        assertTrue("Virtualhost has not changed, reload was not successful", original != getVirtualHost());
    }

    public void testQueueExchangeAndBindingCreation() throws Exception
    {
        assertEquals("Should not be any existing queues", 0,  getVirtualHost().getQueues().size());

        createAllQueues();
        createAllTopicQueues();

        //Register Non-Durable DirectExchange
        ExchangeImpl<?> nonDurableExchange = createExchange(DirectExchange.TYPE, nonDurableExchangeName, false);
        bindAllQueuesToExchange(nonDurableExchange, directRouting);

        //Register DirectExchange
        ExchangeImpl<?> directExchange = createExchange(DirectExchange.TYPE, directExchangeName, true);
        bindAllQueuesToExchange(directExchange, directRouting);

        //Register TopicExchange
        ExchangeImpl<?> topicExchange = createExchange(TopicExchange.TYPE, topicExchangeName, true);
        bindAllTopicQueuesToExchange(topicExchange, topicRouting);

        //Send Message To NonDurable direct Exchange = persistent
        sendMessageOnExchange(nonDurableExchange, directRouting, true);
        // and non-persistent
        sendMessageOnExchange(nonDurableExchange, directRouting, false);

        //Send Message To direct Exchange = persistent
        sendMessageOnExchange(directExchange, directRouting, true);
        // and non-persistent
        sendMessageOnExchange(directExchange, directRouting, false);

        //Send Message To topic Exchange = persistent
        sendMessageOnExchange(topicExchange, topicRouting, true);
        // and non-persistent
        sendMessageOnExchange(topicExchange, topicRouting, false);

        //Ensure all the Queues have four messages (one transient, one persistent) x 2 exchange routings
        validateMessageOnQueues(4, true);
        //Ensure all the topics have two messages (one transient, one persistent)
        validateMessageOnTopics(2, true);

        assertEquals("Not all queues correctly registered",
                10, getVirtualHost().getQueues().size());
    }

    public void testMessagePersistence() throws Exception
    {
        testQueueExchangeAndBindingCreation();

        reloadVirtualHost();

        //Validate durable queues and subscriptions still have the persistent messages
        validateMessageOnQueues(2, false);
        validateMessageOnTopics(1, false);
    }

    /**
     * Tests message removal by running the testMessagePersistence() method above before
     * clearing the queues, reloading the virtual host, and ensuring that the persistent
     * messages were removed from the queues.
     */
    public void testMessageRemoval() throws Exception
    {
        testMessagePersistence();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  getVirtualHost().getQueues().size());

        //clear the queue
        _virtualHost.getQueue(durableQueueName).clearQueue();

        //check the messages are gone
        validateMessageOnQueue(durableQueueName, 0);

        //reload and verify messages arent restored
        reloadVirtualHost();

        validateMessageOnQueue(durableQueueName, 0);
    }

    /**
     * Tests queue persistence by creating a selection of queues with differing properties, both
     * durable and non durable, and ensuring that following the recovery process the correct queues
     * are present and any property manipulations (eg queue exclusivity) are correctly recovered.
     */
    public void testQueuePersistence() throws Exception
    {
        assertEquals("Should not be any existing queues",
                0, getVirtualHost().getQueues().size());

        //create durable and non durable queues/topics
        createAllQueues();
        createAllTopicQueues();

        //reload the virtual host, prompting recovery of the queues/topics
        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  getVirtualHost().getQueues().size());

        //Validate the non-Durable Queues were not recovered.
        assertNull("Non-Durable queue still registered:" + priorityQueueName,
                getVirtualHost().getQueue(priorityQueueName));
        assertNull("Non-Durable queue still registered:" + queueName,
                getVirtualHost().getQueue(queueName));
        assertNull("Non-Durable queue still registered:" + priorityTopicQueueName,
                getVirtualHost().getQueue(priorityTopicQueueName));
        assertNull("Non-Durable queue still registered:" + topicQueueName,
                getVirtualHost().getQueue(topicQueueName));

        //Validate normally expected properties of Queues/Topics
        validateDurableQueueProperties();

        //Update the durable exclusive queue's exclusivity
        setQueueExclusivity(false);
        validateQueueExclusivityProperty(false);
    }

    /**
     * Tests queue removal by creating a durable queue, verifying it recovers, and
     * then removing it from the store, and ensuring that following the second reload
     * process it is not recovered.
     */
    public void testDurableQueueRemoval() throws Exception
    {
        //Register Durable Queue
        createQueue(durableQueueName, false, true, false, false);

        assertEquals("Incorrect number of queues registered before recovery",
                1,  getVirtualHost().getQueues().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after first recovery",
                1,  getVirtualHost().getQueues().size());

        //test that removing the queue means it is not recovered next time
        final AMQQueue<?> queue = getVirtualHost().getQueue(durableQueueName);
        DurableConfigurationStoreHelper.removeQueue(getVirtualHost().getDurableConfigurationStore(),queue);

        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after second recovery",
                0,  getVirtualHost().getQueues().size());
        assertNull("Durable queue was not removed:" + durableQueueName,
                getVirtualHost().getQueue(durableQueueName));
    }

    /**
     * Tests exchange persistence by creating a selection of exchanges, both durable
     * and non durable, and ensuring that following the recovery process the correct
     * durable exchanges are still present.
     */
    public void testExchangePersistence() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        Map<String, ExchangeImpl<?>> oldExchanges = createExchanges();

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        //verify the exchanges present after recovery
        validateExchanges(origExchangeCount, oldExchanges);
    }

    /**
     * Tests exchange removal by creating a durable exchange, verifying it recovers, and
     * then removing it from the store, and ensuring that following the second reload
     * process it is not recovered.
     */
    public void testDurableExchangeRemoval() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        createExchange(DirectExchange.TYPE, directExchangeName, true);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 1,  getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after first recovery",
                origExchangeCount + 1,  getVirtualHost().getExchanges().size());

        //test that removing the exchange means it is not recovered next time
        final ExchangeImpl<?> exchange = getVirtualHost().getExchange(directExchangeName);
        DurableConfigurationStoreHelper.removeExchange(getVirtualHost().getDurableConfigurationStore(), exchange);

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after second recovery",
                origExchangeCount,  getVirtualHost().getExchanges().size());
        assertNull("Durable exchange was not removed:" + directExchangeName,
                getVirtualHost().getExchange(directExchangeName));
    }

    /**
     * Tests binding persistence by creating a selection of queues and exchanges, both durable
     * and non durable, then adding bindings with and without selectors before reloading the
     * virtual host and verifying that following the recovery process the correct durable
     * bindings (those for durable queues to durable exchanges) are still present.
     */
    public void testBindingPersistence() throws Exception
    {
        int origExchangeCount = getVirtualHost().getExchanges().size();

        createAllQueues();
        createAllTopicQueues();

        Map<String, ExchangeImpl<?>> exchanges = createExchanges();

        ExchangeImpl<?> nonDurableExchange = exchanges.get(nonDurableExchangeName);
        ExchangeImpl<?> directExchange = exchanges.get(directExchangeName);
        ExchangeImpl<?> topicExchange = exchanges.get(topicExchangeName);

        bindAllQueuesToExchange(nonDurableExchange, directRouting);
        bindAllQueuesToExchange(directExchange, directRouting);
        bindAllTopicQueuesToExchange(topicExchange, topicRouting);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, getVirtualHost().getExchanges().size());

        reloadVirtualHost();

        validateExchanges(origExchangeCount, exchanges);

        validateBindingProperties();
    }

    /**
     * Tests binding removal by creating a durable exchange, and queue, binding them together,
     * recovering to verify the persistence, then removing it from the store, and ensuring
     * that following the second reload process it is not recovered.
     */
    public void testDurableBindingRemoval() throws Exception
    {
        //create durable queue and exchange, bind them
        ExchangeImpl<?> exch = createExchange(DirectExchange.TYPE, directExchangeName, true);
        createQueue(durableQueueName, false, true, false, false);
        bindQueueToExchange(exch, directRouting, getVirtualHost().getQueue(durableQueueName), false);

        assertEquals("Incorrect number of bindings registered before recovery",
                1, getVirtualHost().getQueue(durableQueueName).getBindings().size());

        //verify binding is actually normally recovered
        reloadVirtualHost();

        assertEquals("Incorrect number of bindings registered after first recovery",
                1, getVirtualHost().getQueue(durableQueueName).getBindings().size());

        exch = getVirtualHost().getExchange(directExchangeName);
        assertNotNull("Exchange was not recovered", exch);

        //remove the binding and verify result after recovery
        unbindQueueFromExchange(exch, directRouting, getVirtualHost().getQueue(durableQueueName), false);

        reloadVirtualHost();

        assertEquals("Incorrect number of bindings registered after second recovery",
                0, getVirtualHost().getQueue(durableQueueName).getBindings().size());
    }

    /**
     * Validates that the durable exchanges are still present, the non durable exchange is not,
     * and that the new exchanges are not the same objects as the provided list (i.e. that the
     * reload actually generated new exchange objects)
     */
    private void validateExchanges(int originalNumExchanges, Map<String, ExchangeImpl<?>> oldExchanges)
    {
        Collection<ExchangeImpl<?>> exchanges = getVirtualHost().getExchanges();
        Collection<String> exchangeNames = new ArrayList<String>(exchanges.size());
        for(ExchangeImpl<?> exchange : exchanges)
        {
            exchangeNames.add(exchange.getName());
        }
        assertTrue(directExchangeName + " exchange NOT reloaded",
                exchangeNames.contains(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                exchangeNames.contains(topicExchangeName));
        assertTrue(nonDurableExchangeName + " exchange reloaded",
                !exchangeNames.contains(nonDurableExchangeName));

        //check the old exchange objects are not the same as the new exchanges
        assertTrue(directExchangeName + " exchange NOT reloaded",
                getVirtualHost().getExchange(directExchangeName) != oldExchanges.get(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                getVirtualHost().getExchange(topicExchangeName) != oldExchanges.get(topicExchangeName));

        // There should only be the original exchanges + our 2 recovered durable exchanges
        assertEquals("Incorrect number of exchanges available",
                originalNumExchanges + 2, getVirtualHost().getExchanges().size());
    }

    /** Validates the Durable queues and their properties are as expected following recovery */
    @SuppressWarnings("unchecked")
    private void validateBindingProperties()
    {

        assertEquals("Incorrect number of (durable) queues following recovery", 6, getVirtualHost().getQueues().size());

        validateBindingProperties(getVirtualHost().getQueue(durablePriorityQueueName).getBindings(), false);
        validateBindingProperties(getVirtualHost().getQueue(durablePriorityTopicQueueName).getBindings(), true);
        validateBindingProperties(getVirtualHost().getQueue(durableQueueName).getBindings(), false);
        validateBindingProperties(getVirtualHost().getQueue(durableTopicQueueName).getBindings(), true);
        validateBindingProperties(getVirtualHost().getQueue(durableExclusiveQueueName).getBindings(), false);
    }

    /**
     * Validate that each queue is bound only once following recovery (i.e. that bindings for non durable
     * queues or to non durable exchanges are not recovered), and if a selector should be present
     * that it is and contains the correct value
     *
     * @param bindings     the set of bindings to validate
     * @param useSelectors if set, check the binding has a JMS_SELECTOR argument and the correct value for it
     */
    private void validateBindingProperties(Collection<? extends Binding<?>> bindings, boolean useSelectors)
    {
        assertEquals("Each queue should only be bound once.", 1, bindings.size());

        Binding<?> binding = bindings.iterator().next();

        if (useSelectors)
        {
            assertTrue("Binding does not contain a Selector argument.",
                    binding.getArguments().containsKey(AMQPFilterTypes.JMS_SELECTOR.toString()));
            assertEquals("The binding selector argument is incorrect", SELECTOR_VALUE,
                    binding.getArguments().get(AMQPFilterTypes.JMS_SELECTOR.toString()).toString());
        }
    }

    private void setQueueExclusivity(boolean exclusive) throws MessageSource.ExistingConsumerPreventsExclusive
    {
        AMQQueue<?> queue = getVirtualHost().getQueue(durableExclusiveQueueName);
        queue.setAttribute(Queue.EXCLUSIVE, queue.getExclusive(), exclusive ? ExclusivityPolicy.CONTAINER : ExclusivityPolicy.NONE);
    }

    private void validateQueueExclusivityProperty(boolean expected)
    {
        AMQQueue<?> queue = getVirtualHost().getQueue(durableExclusiveQueueName);

        assertEquals("Queue exclusivity was incorrect", queue.isExclusive(), expected);
    }


    private void validateDurableQueueProperties()
    {
        validateQueueProperties(getVirtualHost().getQueue(durablePriorityQueueName), true, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durablePriorityTopicQueueName), true, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durableQueueName), false, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durableTopicQueueName), false, true, false, false);
        validateQueueProperties(getVirtualHost().getQueue(durableExclusiveQueueName), false, true, true, false);
        validateQueueProperties(getVirtualHost().getQueue(durableLastValueQueueName), false, true, true, true);
    }

    private void validateQueueProperties(AMQQueue<?> queue, boolean usePriority, boolean durable, boolean exclusive, boolean lastValueQueue)
    {
        if(usePriority || lastValueQueue)
        {
            assertNotSame("Queues cant be both Priority and LastValue based", usePriority, lastValueQueue);
        }

        if (usePriority)
        {
            assertEquals("Queue is no longer a Priority Queue", PriorityQueueImpl.class, queue.getClass());
            assertEquals("Priority Queue does not have set priorities",
                    DEFAULT_PRIORTY_LEVEL, ((PriorityQueueImpl) queue).getPriorities());
        }
        else if (lastValueQueue)
        {
            assertEquals("Queue is no longer a LastValue Queue", LastValueQueueImpl.class, queue.getClass());
            assertEquals("LastValue Queue Key has changed", LVQ_KEY, ((LastValueQueueImpl) queue).getConflationKey());
        }
        else
        {
            assertEquals("Queue is not 'simple'", StandardQueueImpl.class, queue.getClass());
        }

        assertEquals("Queue owner is not as expected for queue " + queue.getName(), exclusive ? queueOwner : null, queue.getOwner());
        assertEquals("Queue durability is not as expected for queue " + queue.getName(), durable, queue.isDurable());
        assertEquals("Queue exclusivity is not as expected for queue " + queue.getName(), exclusive, queue.isExclusive());
    }

    /**
     * Delete the Store Environment path
     *
     * @param environmentPath The configuration that contains the store environment path.
     */
    private void cleanup(File environmentPath)
    {
        if (environmentPath.exists())
        {
            FileUtils.delete(environmentPath, true);
        }
    }

    private void sendMessageOnExchange(ExchangeImpl<?> exchange, String routingKey, boolean deliveryMode)
    {
        //Set MessagePersistence
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setDeliveryMode(deliveryMode ? Integer.valueOf(2).byteValue() : Integer.valueOf(1).byteValue());
        FieldTable headers = properties.getHeaders();
        headers.setString("Test", "MST");
        properties.setHeaders(headers);

        MessagePublishInfo messageInfo = new TestMessagePublishInfo(exchange, false, false, routingKey);

        ContentHeaderBody headerBody = new ContentHeaderBody(BasicConsumeBodyImpl.CLASS_ID,0,properties,0l);

        MessageMetaData mmd = new MessageMetaData(messageInfo, headerBody, System.currentTimeMillis());

        final StoredMessage<MessageMetaData> storedMessage = getVirtualHost().getMessageStore().addMessage(mmd);
        storedMessage.flushToStore();
        final AMQMessage currentMessage = new AMQMessage(storedMessage);



        ServerTransaction trans = new AutoCommitTransaction(getVirtualHost().getMessageStore());
        exchange.send(currentMessage, routingKey, InstanceProperties.EMPTY, trans, null);

    }

    private void createAllQueues() throws Exception
    {
        //Register Durable Priority Queue
        createQueue(durablePriorityQueueName, true, true, false, false);

        //Register Durable Simple Queue
        createQueue(durableQueueName, false, true, false, false);

        //Register Durable Exclusive Simple Queue
        createQueue(durableExclusiveQueueName, false, true, true, false);

        //Register Durable LastValue Queue
        createQueue(durableLastValueQueueName, false, true, true, true);

        //Register NON-Durable Priority Queue
        createQueue(priorityQueueName, true, false, false, false);

        //Register NON-Durable Simple Queue
        createQueue(queueName, false, false, false, false);
    }

    private void createAllTopicQueues() throws Exception
    {
        //Register Durable Priority Queue
        createQueue(durablePriorityTopicQueueName, true, true, false, false);

        //Register Durable Simple Queue
        createQueue(durableTopicQueueName, false, true, false, false);

        //Register NON-Durable Priority Queue
        createQueue(priorityTopicQueueName, true, false, false, false);

        //Register NON-Durable Simple Queue
        createQueue(topicQueueName, false, false, false, false);
    }

    private void createQueue(String queueName, boolean usePriority, boolean durable, boolean exclusive, boolean lastValueQueue)
            throws Exception
    {

        Map<String,Object> queueArguments = new HashMap<String, Object>();

        if(usePriority || lastValueQueue)
        {
            assertNotSame("Queues cant be both Priority and LastValue based", usePriority, lastValueQueue);
        }

        if (usePriority)
        {
            queueArguments.put(PriorityQueue.PRIORITIES, DEFAULT_PRIORTY_LEVEL);
        }

        if (lastValueQueue)
        {
            queueArguments.put(LastValueQueue.LVQ_KEY, LVQ_KEY);
        }

        queueArguments.put(Queue.ID, UUIDGenerator.generateRandomUUID());
        queueArguments.put(Queue.NAME, queueName);
        queueArguments.put(Queue.DURABLE, durable);
        queueArguments.put(Queue.LIFETIME_POLICY, LifetimePolicy.PERMANENT);
        queueArguments.put(Queue.EXCLUSIVE, exclusive ? ExclusivityPolicy.CONTAINER : ExclusivityPolicy.NONE);
        if(exclusive && queueOwner != null)
        {
            queueArguments.put(Queue.OWNER, queueOwner);
        }
        AMQQueue<?> queue = null;

        //Ideally we would be able to use the QueueDeclareHandler here.
        queue = getVirtualHost().createQueue(queueArguments);

        validateQueueProperties(queue, usePriority, durable, exclusive, lastValueQueue);
    }

    private Map<String, ExchangeImpl<?>> createExchanges() throws Exception
    {
        Map<String, ExchangeImpl<?>> exchanges = new HashMap<String, ExchangeImpl<?>>();

        //Register non-durable DirectExchange
        exchanges.put(nonDurableExchangeName, createExchange(DirectExchange.TYPE, nonDurableExchangeName, false));

        //Register durable DirectExchange and TopicExchange
        exchanges.put(directExchangeName ,createExchange(DirectExchange.TYPE, directExchangeName, true));
        exchanges.put(topicExchangeName,createExchange(TopicExchange.TYPE, topicExchangeName, true));

        return exchanges;
    }

    private ExchangeImpl<?> createExchange(ExchangeType<?> type, String name, boolean durable) throws Exception
    {
        ExchangeImpl<?> exchange = null;

        Map<String,Object> attributes = new HashMap<String, Object>();

        attributes.put(org.apache.qpid.server.model.Exchange.NAME, name);
        attributes.put(org.apache.qpid.server.model.Exchange.TYPE, type.getType());
        attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, durable);
        attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                durable ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
        attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
        exchange = getVirtualHost().createExchange(attributes);

        return exchange;
    }

    private void bindAllQueuesToExchange(ExchangeImpl<?> exchange, String routingKey)
    {
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durablePriorityQueueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durableQueueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(priorityQueueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(queueName), false);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durableExclusiveQueueName), false);
    }

    private void bindAllTopicQueuesToExchange(ExchangeImpl<?> exchange, String routingKey)
    {

        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durablePriorityTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(durableTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(priorityTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, getVirtualHost().getQueue(topicQueueName), true);
    }


    protected void bindQueueToExchange(ExchangeImpl<?> exchange,
                                       String routingKey,
                                       AMQQueue<?> queue,
                                       boolean useSelector)
    {
        Map<String,Object> bindArguments = new HashMap<String, Object>();

        if (useSelector)
        {
            bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.toString(), SELECTOR_VALUE );
        }

        try
        {
            exchange.addBinding(routingKey, queue, bindArguments);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    protected void unbindQueueFromExchange(ExchangeImpl<?> exchange,
                                           String routingKey,
                                           AMQQueue<?> queue,
                                           boolean useSelector)
    {
        Map<String,Object> bindArguments = new HashMap<String, Object>();

        if (useSelector)
        {
            bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.toString(), SELECTOR_VALUE );
        }

        try
        {
            exchange.deleteBinding(routingKey, queue);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }

    private void validateMessageOnTopics(long messageCount, boolean allQueues)
    {
        validateMessageOnQueue(durablePriorityTopicQueueName, messageCount);
        validateMessageOnQueue(durableTopicQueueName, messageCount);

        if (allQueues)
        {
            validateMessageOnQueue(priorityTopicQueueName, messageCount);
            validateMessageOnQueue(topicQueueName, messageCount);
        }
    }

    private void validateMessageOnQueues(long messageCount, boolean allQueues)
    {
        validateMessageOnQueue(durablePriorityQueueName, messageCount);
        validateMessageOnQueue(durableQueueName, messageCount);

        if (allQueues)
        {
            validateMessageOnQueue(priorityQueueName, messageCount);
            validateMessageOnQueue(queueName, messageCount);
        }
    }

    private void validateMessageOnQueue(String queueName, long messageCount)
    {
        AMQQueue<?> queue = getVirtualHost().getQueue(queueName);

        assertNotNull("Queue(" + queueName + ") not correctly registered:", queue);

        assertEquals("Incorrect Message count on queue:" + queueName, messageCount, queue.getQueueDepthMessages());
    }

    private class TestMessagePublishInfo implements MessagePublishInfo
    {

        ExchangeImpl<?> _exchange;
        boolean _immediate;
        boolean _mandatory;
        String _routingKey;

        TestMessagePublishInfo(ExchangeImpl<?> exchange, boolean immediate, boolean mandatory, String routingKey)
        {
            _exchange = exchange;
            _immediate = immediate;
            _mandatory = mandatory;
            _routingKey = routingKey;
        }

        @Override
        public AMQShortString getExchange()
        {
            return new AMQShortString(_exchange.getName());
        }

        @Override
        public void setExchange(AMQShortString exchange)
        {
            //no-op
        }

        @Override
        public boolean isImmediate()
        {
            return _immediate;
        }

        @Override
        public boolean isMandatory()
        {
            return _mandatory;
        }

        @Override
        public AMQShortString getRoutingKey()
        {
            return new AMQShortString(_routingKey);
        }
    }

}
