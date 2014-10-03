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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;

import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.connection.SessionPrincipal;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
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
import org.apache.qpid.server.virtualhost.TestMemoryVirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.JsonVirtualHostNode;
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

    private VirtualHostImpl<?,?,?> _virtualHost;
    private String _storePath;
    private VirtualHostNode<?> _node;
    private TaskExecutor _taskExecutor;

    public void setUp() throws Exception
    {
        super.setUp();

        String nodeName = "node" + getName();
        String hostName = "host" + getName();
        _storePath = System.getProperty("QPID_WORK", TMP_FOLDER + File.separator + getTestName()) + File.separator + nodeName;
        cleanup(new File(_storePath));

        Broker<?> broker = BrokerTestHelper.createBrokerMock();
        _taskExecutor = new TaskExecutorImpl();
        _taskExecutor.start();
        when(broker.getTaskExecutor()).thenReturn(_taskExecutor);

        ConfiguredObjectFactory factory = broker.getObjectFactory();
        Map<String, Object> nodeAttributes = new HashMap<>();
        nodeAttributes.put(ConfiguredObject.TYPE, getTestProfileVirtualHostNodeType());
        nodeAttributes.put(JsonVirtualHostNode.STORE_PATH, _storePath);
        nodeAttributes.put(VirtualHostNode.NAME, nodeName);

        _node = factory.create(VirtualHostNode.class, nodeAttributes, broker);
        _node.start();

        final Map<String,Object> virtualHostAttributes = new HashMap<>();
        virtualHostAttributes.put(VirtualHost.NAME, hostName);
        virtualHostAttributes.put(VirtualHost.NAME, hostName);
        String bluePrint = getTestProfileVirtualHostNodeBlueprint();
        if (bluePrint == null)
        {
            bluePrint = "{type=\"" + TestMemoryVirtualHost.VIRTUAL_HOST_TYPE + "\"}";
        }
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> attrs =  objectMapper.readValue(bluePrint, Map.class);
        virtualHostAttributes.putAll(attrs);
        _node.createChild(VirtualHost.class, virtualHostAttributes, _node);

        _virtualHost = (VirtualHostImpl<?,?,?>)_node.getVirtualHost();

    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                VirtualHostNode<?> node = _virtualHost.getParent(VirtualHostNode.class);
                node.close();
            }
        }
        finally
        {
            _taskExecutor.stopImmediately();
            super.tearDown();
        }
    }

    protected void reloadVirtualHost()
    {
        assertEquals("Virtual host node is not active", State.ACTIVE, _virtualHost.getState());
        _node.stop();
        State currentState = _node.getState();
        assertEquals("Virtual host node is not stopped", State.STOPPED, currentState);

        _node.start();
        currentState = _node.getState();
        assertEquals("Virtual host node is not active", State.ACTIVE, currentState);
        _virtualHost = (VirtualHostImpl<?, ?, ?>) _node.getVirtualHost();
    }

    public void testQueueExchangeAndBindingCreation() throws Exception
    {
        assertEquals("Should not be any existing queues", 0,  _virtualHost.getQueues().size());

        createAllQueues();
        createAllTopicQueues();

        //Register Non-Durable DirectExchange
        ExchangeImpl<?> nonDurableExchange = createExchange(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, nonDurableExchangeName, false);
        bindAllQueuesToExchange(nonDurableExchange, directRouting);

        //Register DirectExchange
        ExchangeImpl<?> directExchange = createExchange(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, directExchangeName, true);
        bindAllQueuesToExchange(directExchange, directRouting);

        //Register TopicExchange
        ExchangeImpl<?> topicExchange = createExchange(ExchangeDefaults.TOPIC_EXCHANGE_CLASS, topicExchangeName, true);
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
                10, _virtualHost.getQueues().size());
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
                6,  _virtualHost.getQueues().size());

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
                0, _virtualHost.getQueues().size());

        //create durable and non durable queues/topics
        createAllQueues();
        createAllTopicQueues();

        //reload the virtual host, prompting recovery of the queues/topics
        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after recovery",
                6,  _virtualHost.getQueues().size());

        //Validate the non-Durable Queues were not recovered.
        assertNull("Non-Durable queue still registered:" + priorityQueueName,
                _virtualHost.getQueue(priorityQueueName));
        assertNull("Non-Durable queue still registered:" + queueName,
                _virtualHost.getQueue(queueName));
        assertNull("Non-Durable queue still registered:" + priorityTopicQueueName,
                _virtualHost.getQueue(priorityTopicQueueName));
        assertNull("Non-Durable queue still registered:" + topicQueueName,
                _virtualHost.getQueue(topicQueueName));

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
                1,  _virtualHost.getQueues().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after first recovery",
                1,  _virtualHost.getQueues().size());

        //test that removing the queue means it is not recovered next time

        final AMQQueue<?> queue = _virtualHost.getQueue(durableQueueName);
        _virtualHost.getDurableConfigurationStore().remove(queue.asObjectRecord());

        reloadVirtualHost();

        assertEquals("Incorrect number of queues registered after second recovery",
                0,  _virtualHost.getQueues().size());
        assertNull("Durable queue was not removed:" + durableQueueName,
                _virtualHost.getQueue(durableQueueName));
    }

    /**
     * Tests exchange persistence by creating a selection of exchanges, both durable
     * and non durable, and ensuring that following the recovery process the correct
     * durable exchanges are still present.
     */
    public void testExchangePersistence() throws Exception
    {
        int origExchangeCount = _virtualHost.getExchanges().size();

        Map<String, ExchangeImpl<?>> oldExchanges = createExchanges();

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 3, _virtualHost.getExchanges().size());

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
        int origExchangeCount = _virtualHost.getExchanges().size();

        createExchange(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, directExchangeName, true);

        assertEquals("Incorrect number of exchanges registered before recovery",
                origExchangeCount + 1,  _virtualHost.getExchanges().size());

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after first recovery",
                origExchangeCount + 1,  _virtualHost.getExchanges().size());

        //test that removing the exchange means it is not recovered next time

        final ExchangeImpl<?> exchange = _virtualHost.getExchange(directExchangeName);
        _virtualHost.getDurableConfigurationStore().remove(exchange.asObjectRecord());

        reloadVirtualHost();

        assertEquals("Incorrect number of exchanges registered after second recovery",
                origExchangeCount,  _virtualHost.getExchanges().size());
        assertNull("Durable exchange was not removed:" + directExchangeName,
                _virtualHost.getExchange(directExchangeName));
    }

    /**
     * Tests binding persistence by creating a selection of queues and exchanges, both durable
     * and non durable, then adding bindings with and without selectors before reloading the
     * virtual host and verifying that following the recovery process the correct durable
     * bindings (those for durable queues to durable exchanges) are still present.
     */
    public void testBindingPersistence() throws Exception
    {
        int origExchangeCount = _virtualHost.getExchanges().size();

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
                origExchangeCount + 3, _virtualHost.getExchanges().size());

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
        ExchangeImpl<?> exch = createExchange(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, directExchangeName, true);
        createQueue(durableQueueName, false, true, false, false);
        bindQueueToExchange(exch, directRouting, _virtualHost.getQueue(durableQueueName), false);

        assertEquals("Incorrect number of bindings registered before recovery",
                1, _virtualHost.getQueue(durableQueueName).getBindings().size());

        //verify binding is actually normally recovered
        reloadVirtualHost();

        assertEquals("Incorrect number of bindings registered after first recovery",
                1, _virtualHost.getQueue(durableQueueName).getBindings().size());

        exch = _virtualHost.getExchange(directExchangeName);
        assertNotNull("Exchange was not recovered", exch);

        //remove the binding and verify result after recovery
        unbindQueueFromExchange(exch, directRouting, _virtualHost.getQueue(durableQueueName), false);

        reloadVirtualHost();

        assertEquals("Incorrect number of bindings registered after second recovery",
                0, _virtualHost.getQueue(durableQueueName).getBindings().size());
    }

    /**
     * Validates that the durable exchanges are still present, the non durable exchange is not,
     * and that the new exchanges are not the same objects as the provided list (i.e. that the
     * reload actually generated new exchange objects)
     */
    private void validateExchanges(int originalNumExchanges, Map<String, ExchangeImpl<?>> oldExchanges)
    {
        Collection<ExchangeImpl<?>> exchanges = (Collection<ExchangeImpl<?>>) _virtualHost.getExchanges();
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
                _virtualHost.getExchange(directExchangeName) != oldExchanges.get(directExchangeName));
        assertTrue(topicExchangeName + " exchange NOT reloaded",
                _virtualHost.getExchange(topicExchangeName) != oldExchanges.get(topicExchangeName));

        // There should only be the original exchanges + our 2 recovered durable exchanges
        assertEquals("Incorrect number of exchanges available",
                originalNumExchanges + 2, _virtualHost.getExchanges().size());
    }

    /** Validates the Durable queues and their properties are as expected following recovery */
    private void validateBindingProperties()
    {

        assertEquals("Incorrect number of (durable) queues following recovery", 6, _virtualHost.getQueues().size());

        validateBindingProperties(_virtualHost.getQueue(durablePriorityQueueName).getBindings(), false);
        validateBindingProperties(_virtualHost.getQueue(durablePriorityTopicQueueName).getBindings(), true);
        validateBindingProperties(_virtualHost.getQueue(durableQueueName).getBindings(), false);
        validateBindingProperties(_virtualHost.getQueue(durableTopicQueueName).getBindings(), true);
        validateBindingProperties(_virtualHost.getQueue(durableExclusiveQueueName).getBindings(), false);
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
        AMQQueue<?> queue = _virtualHost.getQueue(durableExclusiveQueueName);
        queue.setAttribute(Queue.EXCLUSIVE, queue.getExclusive(), exclusive ? ExclusivityPolicy.CONTAINER : ExclusivityPolicy.NONE);
    }

    private void validateQueueExclusivityProperty(boolean expected)
    {
        AMQQueue<?> queue = _virtualHost.getQueue(durableExclusiveQueueName);

        assertEquals("Queue exclusivity was incorrect", queue.isExclusive(), expected);
    }


    private void validateDurableQueueProperties()
    {
        validateQueueProperties(_virtualHost.getQueue(durablePriorityQueueName), true, true, false, false);
        validateQueueProperties(_virtualHost.getQueue(durablePriorityTopicQueueName), true, true, false, false);
        validateQueueProperties(_virtualHost.getQueue(durableQueueName), false, true, false, false);
        validateQueueProperties(_virtualHost.getQueue(durableTopicQueueName), false, true, false, false);
        validateQueueProperties(_virtualHost.getQueue(durableExclusiveQueueName), false, true, true, false);
        validateQueueProperties(_virtualHost.getQueue(durableLastValueQueueName), false, true, true, true);
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
            assertEquals("LastValue Queue Key has changed", LVQ_KEY, ((LastValueQueueImpl) queue).getLvqKey());
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

        MessagePublishInfo messageInfo = new MessagePublishInfo(new AMQShortString(exchange.getName()), false, false, new AMQShortString(routingKey));

        ContentHeaderBody headerBody = new ContentHeaderBody(properties,0l);

        MessageMetaData mmd = new MessageMetaData(messageInfo, headerBody, System.currentTimeMillis());

        final StoredMessage<MessageMetaData> storedMessage = _virtualHost.getMessageStore().addMessage(mmd);
        final AMQMessage currentMessage = new AMQMessage(storedMessage);



        ServerTransaction trans = new AutoCommitTransaction(_virtualHost.getMessageStore());
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

        final Map<String,Object> queueArguments = new HashMap<String, Object>();

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
        AMQSessionModel sessionModel = mock(AMQSessionModel.class);
        AMQConnectionModel connectionModel = mock(AMQConnectionModel.class);
        when(sessionModel.getConnectionModel()).thenReturn(connectionModel);
        when(connectionModel.getRemoteContainerName()).thenReturn(queueOwner);
        SessionPrincipal principal = new SessionPrincipal(sessionModel);
        AMQQueue<?> queue = Subject.doAs(new Subject(true,
                                                     Collections.singleton(principal),
                                                     Collections.emptySet(),
                                                     Collections.emptySet()),
                                         new PrivilegedAction<AMQQueue<?>>()
                                         {
                                             @Override
                                             public AMQQueue<?> run()
                                             {
                                                 return _virtualHost.createQueue(queueArguments);

                                             }
                                         });


        validateQueueProperties(queue, usePriority, durable, exclusive, lastValueQueue);
    }

    private Map<String, ExchangeImpl<?>> createExchanges() throws Exception
    {
        Map<String, ExchangeImpl<?>> exchanges = new HashMap<String, ExchangeImpl<?>>();

        //Register non-durable DirectExchange
        exchanges.put(nonDurableExchangeName, createExchange(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, nonDurableExchangeName, false));

        //Register durable DirectExchange and TopicExchange
        exchanges.put(directExchangeName ,createExchange(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, directExchangeName, true));
        exchanges.put(topicExchangeName,createExchange(ExchangeDefaults.TOPIC_EXCHANGE_CLASS, topicExchangeName, true));

        return exchanges;
    }

    private ExchangeImpl<?> createExchange(String type, String name, boolean durable) throws Exception
    {
        ExchangeImpl<?> exchange = null;

        Map<String,Object> attributes = new HashMap<String, Object>();

        attributes.put(org.apache.qpid.server.model.Exchange.NAME, name);
        attributes.put(org.apache.qpid.server.model.Exchange.TYPE, type);
        attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, durable);
        attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                durable ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
        attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
        exchange = _virtualHost.createExchange(attributes);

        return exchange;
    }

    private void bindAllQueuesToExchange(ExchangeImpl<?> exchange, String routingKey)
    {
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(durablePriorityQueueName), false);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(durableQueueName), false);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(priorityQueueName), false);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(queueName), false);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(durableExclusiveQueueName), false);
    }

    private void bindAllTopicQueuesToExchange(ExchangeImpl<?> exchange, String routingKey)
    {

        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(durablePriorityTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(durableTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(priorityTopicQueueName), true);
        bindQueueToExchange(exchange, routingKey, _virtualHost.getQueue(topicQueueName), true);
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
        AMQQueue<?> queue = _virtualHost.getQueue(queueName);

        assertNotNull("Queue(" + queueName + ") not correctly registered:", queue);

        assertEquals("Incorrect Message count on queue:" + queueName, messageCount, queue.getQueueDepthMessages());
    }
}
