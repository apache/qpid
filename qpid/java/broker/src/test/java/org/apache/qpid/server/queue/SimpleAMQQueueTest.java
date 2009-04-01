package org.apache.qpid.server.queue;
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

import junit.framework.TestCase;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.framing.amqp_8_0.BasicConsumeBodyImpl;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestTransactionLog;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.subscription.MockSubscription;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.List;

public class SimpleAMQQueueTest extends TestCase
{

    protected SimpleAMQQueue _queue;
    protected VirtualHost _virtualHost;
    protected TestableMemoryMessageStore _transactionLog = new TestableMemoryMessageStore();
    protected AMQShortString _qname = new AMQShortString("qname");
    protected AMQShortString _owner = new AMQShortString("owner");
    protected AMQShortString _routingKey = new AMQShortString("routing key");
    protected DirectExchange _exchange = new DirectExchange();
    protected MockSubscription _subscription = new MockSubscription();
    protected FieldTable _arguments = null;

    MessagePublishInfo info = new MessagePublishInfoImpl();
    protected static long MESSAGE_SIZE = 100;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        //Create Application Registry for test
        ApplicationRegistry applicationRegistry = (ApplicationRegistry) ApplicationRegistry.getInstance(1);

        PropertiesConfiguration env = new PropertiesConfiguration();
        _virtualHost = new VirtualHost(new VirtualHostConfiguration(getClass().getSimpleName(), env), _transactionLog);
        applicationRegistry.getVirtualHostRegistry().registerVirtualHost(_virtualHost);

        _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(_qname, false, _owner, false, _virtualHost, _arguments);
    }

    @Override
    protected void tearDown()
    {
        _queue.stop();
        ApplicationRegistry.remove(1);
    }

    public void testCreateQueue() throws AMQException
    {
        _queue.stop();
        try
        {
            _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(null, false, _owner, false, _virtualHost, _arguments);
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing name",
                       e.getMessage().contains("name"));
        }

        try
        {
            _queue = new SimpleAMQQueue(_qname, false, _owner, false, null);
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing vhost",
                       e.getMessage().contains("Host"));
        }

        _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(_qname, false, _owner, false,
                                                                     _virtualHost, _arguments);
        assertNotNull("Queue was not created", _queue);
    }

    public void testGetVirtualHost()
    {
        assertEquals("Virtual host was wrong", _virtualHost, _queue.getVirtualHost());
    }

    public void testBinding()
    {
        try
        {
            _queue.bind(_exchange, _routingKey, null);
            assertTrue("Routing key was not bound",
                       _exchange.getBindings().containsKey(_routingKey));
            assertEquals("Queue was not bound to key",
                         _exchange.getBindings().get(_routingKey).get(0),
                         _queue);
            assertEquals("Exchange binding count", 1,
                         _queue.getExchangeBindings().size());
            assertEquals("Wrong exchange bound", _routingKey,
                         _queue.getExchangeBindings().get(0).getRoutingKey());
            assertEquals("Wrong exchange bound", _exchange,
                         _queue.getExchangeBindings().get(0).getExchange());

            _queue.unBind(_exchange, _routingKey, null);
            assertFalse("Routing key was still bound",
                        _exchange.getBindings().containsKey(_routingKey));
            assertNull("Routing key was not empty",
                       _exchange.getBindings().get(_routingKey));
        }
        catch (AMQException e)
        {
            assertNull("Unexpected exception", e);
        }
    }

    public void testSubscription() throws AMQException
    {
        // Check adding a subscription adds it to the queue
        _queue.registerSubscription(_subscription, false);
        assertEquals("Subscription did not get queue", _queue,
                     _subscription.getQueue());
        assertEquals("Queue does not have consumer", 1,
                     _queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1,
                     _queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        AMQMessage messageA = createMessage();
        _queue.enqueue(null, messageA);
        assertEquals(messageA, _subscription.getLastSeenEntry().getMessage());

        // Check removing the subscription removes it's information from the queue
        _queue.unregisterSubscription(_subscription);
        assertTrue("Subscription still had queue", _subscription.isClosed());
        assertFalse("Queue still has consumer", 1 == _queue.getConsumerCount());
        assertFalse("Queue still has active consumer",
                    1 == _queue.getActiveConsumerCount());

        AMQMessage messageB = createMessage();
        _queue.enqueue(null, messageB);
        QueueEntry entry = _subscription.getLastSeenEntry();
        assertNull(entry);
    }

    public void testQueueNoSubscriber() throws AMQException, InterruptedException
    {
        AMQMessage messageA = createMessage();
        _queue.enqueue(null, messageA);
        _queue.registerSubscription(_subscription, false);
        Thread.sleep(150);
        assertEquals(messageA, _subscription.getLastSeenEntry().getMessage());
    }

    public void testExclusiveConsumer() throws AMQException
    {
        // Check adding an exclusive subscription adds it to the queue
        _queue.registerSubscription(_subscription, true);
        assertEquals("Subscription did not get queue", _queue,
                     _subscription.getQueue());
        assertEquals("Queue does not have consumer", 1,
                     _queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1,
                     _queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        AMQMessage messageA = createMessage();
        _queue.enqueue(null, messageA);
        assertEquals(messageA, _subscription.getLastSeenEntry().getMessage());

        // Check we cannot add a second subscriber to the queue
        Subscription subB = new MockSubscription();
        Exception ex = null;
        try
        {
            _queue.registerSubscription(subB, false);
        }
        catch (AMQException e)
        {
            ex = e;
        }
        assertNotNull(ex);
        assertTrue(ex instanceof AMQException);

        // Check we cannot add an exclusive subscriber to a queue with an 
        // existing subscription
        _queue.unregisterSubscription(_subscription);
        _queue.registerSubscription(_subscription, false);
        try
        {
            _queue.registerSubscription(subB, true);
        }
        catch (AMQException e)
        {
            ex = e;
        }
        assertNotNull(ex);
    }

    public void testAutoDeleteQueue() throws Exception
    {
        _queue.stop();
        _queue = new SimpleAMQQueue(_qname, false, _owner, true, _virtualHost);
        _queue.registerSubscription(_subscription, false);
        AMQMessage message = createMessage();
        _queue.enqueue(null, message);
        _queue.unregisterSubscription(_subscription);
        assertTrue("Queue was not deleted when subscription was removed",
                   _queue.isDeleted());
    }

    public void testResend() throws Exception
    {
        _queue.registerSubscription(_subscription, false);
        AMQMessage message = createMessage();
        Long id = message.getMessageId();
        _queue.enqueue(null, message);
        QueueEntry entry = _subscription.getLastSeenEntry();
        entry.setRedelivered(true);
        _queue.resend(entry, _subscription);

    }

    public void testGetFirstMessageId() throws Exception
    {
        // Create message
        AMQMessage message = createMessage();
        Long messageId = message.getMessageId();

        // Put message on queue
        _queue.enqueue(null, message);
        // Get message id
        Long testmsgid = _queue.getMessagesOnTheQueue(1).get(0);

        // Check message id
        assertEquals("Message ID was wrong", messageId, testmsgid);
    }

    public void testGetFirstFiveMessageIds() throws Exception
    {
        // Create message

        AMQMessage message = createMessage();
        Long initialMessageID = message.getMessageId();

        for (int i = 0; i < 5; i++)
        {
            // Put message on queue
            _queue.enqueue(null, message);
            // Create message
            message = createMessage();
        }
        // Get message ids
        List<Long> msgids = _queue.getMessagesOnTheQueue(5);

        Long messageId = initialMessageID;
        // Check message id
        for (int i = 0; i < 5; i++)
        {
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
            messageId++;
        }
    }

    public void testGetLastFiveMessageIds() throws Exception
    {
        AMQMessage message = createMessage();
        Long messageIdOffset = message.getMessageId() - 1;
        for (int i = 0; i < 10; i++)
        {
            // Put message on queue
            _queue.enqueue(null, message);
            // Create message
            message = createMessage();
        }
        // Get message ids
        List<Long> msgids = _queue.getMessagesOnTheQueue(5, 5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(messageIdOffset + 1 + i + 5);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }

    public void testEnqueueDequeueOfPersistentMessageToNonDurableQueue() throws AMQException
    {
        // Create IncomingMessage and nondurable queue
        NonTransactionalContext txnContext = new NonTransactionalContext(_transactionLog, new StoreContext(), null, null);
        IncomingMessage msg = new IncomingMessage(info, txnContext, new MockProtocolSession(_transactionLog), _transactionLog);

        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.properties = new BasicContentHeaderProperties();
        ((BasicContentHeaderProperties) contentHeaderBody.properties).setDeliveryMode((byte) 2);
        msg.setContentHeaderBody(contentHeaderBody);

        long messageId = msg.getMessageId();

        ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();

        // Send persistent message
        qs.add(_queue);
        msg.enqueue(qs);
        msg.routingComplete(_transactionLog);

        _transactionLog.storeMessageMetaData(null, messageId, new MessageMetaData(info, contentHeaderBody, 1));

        // Check that it is enqueued
        List<AMQQueue> data = _transactionLog.getMessageReferenceMap(messageId);
        assertNotNull(data);

        // Dequeue message
        ContentHeaderBody header = new ContentHeaderBody();
        header.bodySize = MESSAGE_SIZE;
        AMQMessage message = new MockPersistentAMQMessage(msg.getMessageId(), _transactionLog);
        message.setPublishAndContentHeaderBody(new StoreContext(), info, header);

        MockQueueEntry entry = new MockQueueEntry(message, _queue);
        entry.getQueueEntryList().add(message);
        entry.acquire();
        entry.dequeue(new StoreContext());

        // Check that it is dequeued
        data = _transactionLog.getMessageReferenceMap(messageId);
        assertTrue(data == null || data.isEmpty());
    }

    public void testMessagesFlowToDisk() throws AMQException, InterruptedException
    {
        // Create IncomingMessage and nondurable queue
        NonTransactionalContext txnContext = new NonTransactionalContext(_transactionLog, new StoreContext(), null, null);

        MESSAGE_SIZE = 1;
        long MEMORY_MAX = 500;
        int MESSAGE_COUNT = (int) MEMORY_MAX * 2;
        //Set the Memory Usage to be very low
        _queue.setMemoryUsageMaximum(MEMORY_MAX);

        for (int msgCount = 0; msgCount < MESSAGE_COUNT / 2; msgCount++)
        {
            sendMessage(txnContext);
        }

        //Check that we can hold 10 messages without flowing
        assertEquals(MESSAGE_COUNT / 2, _queue.getMessageCount());
        assertEquals(MEMORY_MAX, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is flowed.", !_queue.isFlowed());

        // Send anothe and ensure we are flowed
        sendMessage(txnContext);
        assertEquals(MESSAGE_COUNT / 2 + 1, _queue.getMessageCount());
        assertEquals(MESSAGE_COUNT / 2, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is not flowed.", _queue.isFlowed());

        //send another 99 so there are 200msgs in total on the queue
        for (int msgCount = 0; msgCount < (MESSAGE_COUNT / 2) - 1; msgCount++)
        {
            sendMessage(txnContext);

            long usage = _queue.getMemoryUsageCurrent();
            assertTrue("Queue has gone over quota:" + usage,
                       usage <= _queue.getMemoryUsageMaximum());

            assertTrue("Queue has a negative quota:" + usage, usage > 0);

        }
        assertEquals(MESSAGE_COUNT, _queue.getMessageCount());
        assertEquals(MEMORY_MAX, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is not flowed.", _queue.isFlowed());

        _queue.registerSubscription(_subscription, false);

        int slept = 0;
        while (_subscription.getQueueEntries().size() != MESSAGE_COUNT && slept < 10)
        {
            Thread.sleep(500);
            slept++;
        }

        //Ensure the messages are retreived
        assertEquals("Not all messages were received, slept:" + slept / 2 + "s", MESSAGE_COUNT, _subscription.getQueueEntries().size());

        //Check the queue is still within it's limits.
        long current = _queue.getMemoryUsageCurrent();
        assertTrue("Queue has gone over quota:" + current + "/" + _queue.getMemoryUsageMaximum(),
                   current <= _queue.getMemoryUsageMaximum());

        assertTrue("Queue has a negative quota:" + _queue.getMemoryUsageCurrent(), _queue.getMemoryUsageCurrent() >= 0);

        for (int index = 0; index < MESSAGE_COUNT; index++)
        {
            // Ensure that we have received the messages and it wasn't flushed to disk before we received it.
            AMQMessage message = _subscription.getMessages().get(index);
            assertNotNull("Message:" + message.debugIdentity() + " was null.", message);
        }
    }

    public void testMessagesFlowToDiskPurger() throws AMQException, InterruptedException
    {
        // Create IncomingMessage and nondurable queue
        NonTransactionalContext txnContext = new NonTransactionalContext(_transactionLog, new StoreContext(), null, null);

        MESSAGE_SIZE = 1;
        /** Set to larger than the purge batch size. Default 100.
         * @see FlowableBaseQueueEntryList.BATCH_PROCESS_COUNT */
        long MEMORY_MAX = 500;
        int MESSAGE_COUNT = (int) MEMORY_MAX;
        //Set the Memory Usage to be very low
        _queue.setMemoryUsageMaximum(MEMORY_MAX);

        for (int msgCount = 0; msgCount < MESSAGE_COUNT; msgCount++)
        {
            sendMessage(txnContext);
        }

        //Check that we can hold all messages without flowing
        assertEquals(MESSAGE_COUNT, _queue.getMessageCount());
        assertEquals(MEMORY_MAX, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is flowed.", !_queue.isFlowed());

        // Send anothe and ensure we are flowed
        sendMessage(txnContext);
        assertEquals(MESSAGE_COUNT + 1, _queue.getMessageCount());
        assertEquals(MESSAGE_COUNT, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is not flowed.", _queue.isFlowed());

        _queue.setMemoryUsageMaximum(0L);

        //Give the purger time to work maximum of 1s
        int slept = 0;
        while (_queue.getMemoryUsageCurrent() > 0 && slept < 5)
        {
            Thread.yield();
            Thread.sleep(200);
            slept++;
        }

        assertEquals(MESSAGE_COUNT + 1, _queue.getMessageCount());
        assertEquals(0L, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is not flowed.", _queue.isFlowed());

    }

    protected void sendMessage(TransactionalContext txnContext) throws AMQException
    {
        sendMessage(txnContext, 5);
    }

    protected void sendMessage(TransactionalContext txnContext, int priority) throws AMQException
    {
        IncomingMessage msg = new IncomingMessage(info, txnContext, new MockProtocolSession(_transactionLog), _transactionLog);

        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.classId = BasicConsumeBodyImpl.CLASS_ID;
        contentHeaderBody.bodySize = MESSAGE_SIZE;
        contentHeaderBody.properties = new BasicContentHeaderProperties();
        ((BasicContentHeaderProperties) contentHeaderBody.properties).setDeliveryMode((byte) 2);
        ((BasicContentHeaderProperties) contentHeaderBody.properties).setPriority((byte) priority);
        msg.setContentHeaderBody(contentHeaderBody);

        long messageId = msg.getMessageId();

        ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();

        // Send persistent 10 messages

        qs.add(_queue);
        msg.enqueue(qs);

        msg.routingComplete(_transactionLog);

        msg.addContentBodyFrame(new MockContentChunk(1));

        msg.deliverToQueues();

        //Check message was correctly enqueued
        List<AMQQueue> data = _transactionLog.getMessageReferenceMap(messageId);
        assertNotNull(data);
    }


    // FIXME: move this to somewhere useful
    private static AMQMessage createMessage(final MessagePublishInfo publishBody)
    {
        final AMQMessage amqMessage = (MessageFactory.getInstance()).createMessage(null, false);
        try
        {
            //Safe to use a null StoreContext as we have created a TransientMessage (see false param above)
            amqMessage.setPublishAndContentHeaderBody(null, publishBody, new ContentHeaderBody()
            {
                public int getSize()
                {
                    return 1;
                }
            });
        }
        catch (AMQException e)
        {
            // won't happen
        }

        return amqMessage;
    }

    public AMQMessage createMessage() throws AMQException
    {
        AMQMessage message = new TestMessage(info, _transactionLog);

        ContentHeaderBody header = new ContentHeaderBody();
        header.bodySize = MESSAGE_SIZE;

        //The createMessage above is for a Transient Message so it is safe to have no context.
        message.setPublishAndContentHeaderBody(null, info, header);
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        message.getContentHeaderBody().properties = props;

        return message;
    }

    public class TestMessage extends TransientAMQMessage
    {
        private final long _tag;
        private TestTransactionLog _transactionLog;

        TestMessage(MessagePublishInfo publishBody, TestTransactionLog transactionLog)
                throws AMQException
        {
            super(SimpleAMQQueueTest.createMessage(publishBody));
            _tag = getMessageId();
            _transactionLog = transactionLog;
        }

        void assertCountEquals(int expected)
        {
            assertEquals("Wrong count for message with tag " + _tag, expected,
                         _transactionLog.getMessageReferenceMap(_messageId).size());
        }
    }

}
