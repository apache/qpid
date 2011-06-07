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

import org.apache.commons.configuration.PropertiesConfiguration;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.queue.BaseQueue.PostEnqueueAction;
import org.apache.qpid.server.queue.SimpleAMQQueue.QueueEntryFilter;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.subscription.MockSubscription;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimpleAMQQueueTest extends InternalBrokerBaseCase
{

    protected SimpleAMQQueue _queue;
    protected VirtualHost _virtualHost;
    protected TestableMemoryMessageStore _store = new TestableMemoryMessageStore();
    protected AMQShortString _qname = new AMQShortString("qname");
    protected AMQShortString _owner = new AMQShortString("owner");
    protected AMQShortString _routingKey = new AMQShortString("routing key");
    protected DirectExchange _exchange;
    protected MockSubscription _subscription = new MockSubscription();
    protected FieldTable _arguments = null;

    MessagePublishInfo info = new MessagePublishInfo()
    {

        public AMQShortString getExchange()
        {
            return null;
        }

        public void setExchange(AMQShortString exchange)
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean isImmediate()
        {
            return false;
        }

        public boolean isMandatory()
        {
            return false;
        }

        public AMQShortString getRoutingKey()
        {
            return null;
        }
    };

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        //Create Application Registry for test
        ApplicationRegistry applicationRegistry = (ApplicationRegistry)ApplicationRegistry.getInstance();

        PropertiesConfiguration env = new PropertiesConfiguration();
        _virtualHost = new VirtualHostImpl(new VirtualHostConfiguration(getClass().getName(), env), _store);
        applicationRegistry.getVirtualHostRegistry().registerVirtualHost(_virtualHost);

        _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(_qname, false, _owner, false, false, _virtualHost, _arguments);

        _exchange = (DirectExchange)_virtualHost.getExchangeRegistry().getExchange(ExchangeDefaults.DIRECT_EXCHANGE_NAME);
    }

    @Override
    public void tearDown() throws Exception
    {
        _queue.stop();
        super.tearDown();
    }

    public void testCreateQueue() throws AMQException
    {
        _queue.stop();
        try {
            _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(null, false, _owner, false, false, _virtualHost, _arguments );
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing name",
                            e.getMessage().contains("name"));
        }

        try {
            _queue = new SimpleAMQQueue(_qname, false, _owner, false, false,null, Collections.EMPTY_MAP);
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing vhost",
                    e.getMessage().contains("Host"));
        }

        _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(_qname, false, _owner, false,
                                                                false, _virtualHost, _arguments);
        assertNotNull("Queue was not created", _queue);
    }

    public void testGetVirtualHost()
    {
        assertEquals("Virtual host was wrong", _virtualHost, _queue.getVirtualHost());
    }

    public void testBinding() throws AMQSecurityException, AMQInternalException
    {
        _virtualHost.getBindingFactory().addBinding(String.valueOf(_routingKey), _queue, _exchange, Collections.EMPTY_MAP);

        assertTrue("Routing key was not bound",
                        _exchange.isBound(_routingKey));
        assertTrue("Queue was not bound to key",
                    _exchange.isBound(_routingKey,_queue));
        assertEquals("Exchange binding count", 1,
                _queue.getBindings().size());
        assertEquals("Wrong exchange bound", String.valueOf(_routingKey),
                _queue.getBindings().get(0).getBindingKey());
        assertEquals("Wrong exchange bound", _exchange,
                _queue.getBindings().get(0).getExchange());

        _virtualHost.getBindingFactory().removeBinding(String.valueOf(_routingKey), _queue, _exchange, Collections.EMPTY_MAP);
        assertFalse("Routing key was still bound",
                _exchange.isBound(_routingKey));

    }

    public void testRegisterSubscriptionThenEnqueueMessage() throws AMQException
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
        AMQMessage messageA = createMessage(new Long(24));
        _queue.enqueue(messageA);
        assertEquals(messageA, _subscription.getQueueContext().getLastSeenEntry().getMessage());
        assertNull(((QueueContext)_subscription.getQueueContext())._releasedEntry);

        // Check removing the subscription removes it's information from the queue
        _queue.unregisterSubscription(_subscription);
        assertTrue("Subscription still had queue", _subscription.isClosed());
        assertFalse("Queue still has consumer", 1 == _queue.getConsumerCount());
        assertFalse("Queue still has active consumer",
                1 == _queue.getActiveConsumerCount());

        AMQMessage messageB = createMessage(new Long (25));
        _queue.enqueue(messageB);
         assertNull(_subscription.getQueueContext());

    }

    public void testEnqueueMessageThenRegisterSubscription() throws AMQException, InterruptedException
    {
        AMQMessage messageA = createMessage(new Long(24));
        _queue.enqueue(messageA);
        _queue.registerSubscription(_subscription, false);
        Thread.sleep(150);
        assertEquals(messageA, _subscription.getQueueContext().getLastSeenEntry().getMessage());
        assertNull("There should be no releasedEntry after an enqueue", ((QueueContext)_subscription.getQueueContext())._releasedEntry);
    }

    /**
     * Tests enqueuing two messages.
     */
    public void testEnqueueTwoMessagesThenRegisterSubscription() throws Exception
    {
        AMQMessage messageA = createMessage(new Long(24));
        AMQMessage messageB = createMessage(new Long(25));
        _queue.enqueue(messageA);
        _queue.enqueue(messageB);
        _queue.registerSubscription(_subscription, false);
        Thread.sleep(150);
        assertEquals(messageB, _subscription.getQueueContext().getLastSeenEntry().getMessage());
        assertNull("There should be no releasedEntry after enqueues", ((QueueContext)_subscription.getQueueContext())._releasedEntry);
    }

    /**
     * Tests that a released queue entry is resent to the subscriber.  Verifies also that the
     * QueueContext._releasedEntry is reset to null after the entry has been reset.
     */
    public void testReleasedMessageIsResentToSubscriber() throws Exception
    {
        _queue.registerSubscription(_subscription, false);

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        PostEnqueueAction postEnqueueAction = new PostEnqueueAction()
        {
            public void onEnqueue(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        AMQMessage messageA = createMessage(new Long(24));
        AMQMessage messageB = createMessage(new Long(25));
        AMQMessage messageC = createMessage(new Long(26));

        /* Enqueue three messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);
        _queue.enqueue(messageC, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 3, _subscription.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(2).isRedelivered());

        /* Now release the first message only, causing it to be requeued */

        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 4, _subscription.getMessages().size());
        assertTrue("Redelivery flag should now be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should remain be unset", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should remain be unset",queueEntries.get(2).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed", ((QueueContext)_subscription.getQueueContext())._releasedEntry);
    }

    /**
     * Tests that a released message that becomes expired is not resent to the subscriber.
     * This tests ensures that SimpleAMQQueueEntry.getNextAvailableEntry avoids expired entries.
     * Verifies also that the QueueContext._releasedEntry is reset to null after the entry has been reset.
     */
    public void testReleaseMessageThatBecomesExpiredIsNotRedelivered() throws Exception
    {
        _queue.registerSubscription(_subscription, false);

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        PostEnqueueAction postEnqueueAction = new PostEnqueueAction()
        {
            public void onEnqueue(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        /* Enqueue one message with expiration set for a short time in the future */

        AMQMessage messageA = createMessage(new Long(24));
        int messageExpirationOffset = 200;
        messageA.setExpiration(System.currentTimeMillis() + messageExpirationOffset);

        _queue.enqueue(messageA, postEnqueueAction);

        int subFlushWaitTime = 150;
        Thread.sleep(subFlushWaitTime); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 1, _subscription.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());

        /* Wait a little more to be sure that message will have expired, then release the first message only, causing it to be requeued */
        Thread.sleep(messageExpirationOffset - subFlushWaitTime + 10);
        queueEntries.get(0).release();

        Thread.sleep(subFlushWaitTime); // Work done by SubFlushRunner/QueueRunner Threads

        assertTrue("Expecting the queue entry to be now expired", queueEntries.get(0).expired());
        assertEquals("Total number of messages sent should not have changed", 1, _subscription.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed", ((QueueContext)_subscription.getQueueContext())._releasedEntry);

    }

    /**
     * Tests that if a client releases entries 'out of order' (the order
     * used by QueueEntryImpl.compareTo) that messages are still resent
     * successfully.  Specifically this test ensures the {@see SimpleAMQQueue#requeue()}
     * can correctly move the _releasedEntry to an earlier position in the QueueEntry list.
     */
    public void testReleasedOutOfComparableOrderAreRedelivered() throws Exception
    {
        _queue.registerSubscription(_subscription, false);

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        PostEnqueueAction postEnqueueAction = new PostEnqueueAction()
        {
            public void onEnqueue(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        AMQMessage messageA = createMessage(new Long(24));
        AMQMessage messageB = createMessage(new Long(25));
        AMQMessage messageC = createMessage(new Long(26));

        /* Enqueue three messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);
        _queue.enqueue(messageC, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 3, _subscription.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(2).isRedelivered());

        /* Now release the third and first message only, causing it to be requeued */

        queueEntries.get(2).release();
        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 5, _subscription.getMessages().size());
        assertTrue("Redelivery flag should now be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should remain be unset", queueEntries.get(1).isRedelivered());
        assertTrue("Redelivery flag should now be set",queueEntries.get(2).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed", ((QueueContext)_subscription.getQueueContext())._releasedEntry);
    }


    /**
     * Tests that a release requeues an entry for a queue with multiple subscriptions.  Verifies that a
     * requeue resends a message to a <i>single</i> subscriber.
     */
    public void testReleaseForQueueWithMultipleSubscriptions() throws Exception
    {
        MockSubscription subscription1 = new MockSubscription();
        MockSubscription subscription2 = new MockSubscription();

        _queue.registerSubscription(subscription1, false);
        _queue.registerSubscription(subscription2, false);

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        PostEnqueueAction postEnqueueAction = new PostEnqueueAction()
        {
            public void onEnqueue(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        AMQMessage messageA = createMessage(new Long(24));
        AMQMessage messageB = createMessage(new Long(25));

        /* Enqueue two messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to both after enqueue", 2, subscription1.getMessages().size() + subscription2.getMessages().size());

        /* Now release the first message only, causing it to be requeued */
        queueEntries.get(0).release();  

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to both subscriptions after release", 3, subscription1.getMessages().size() + subscription2.getMessages().size());
        assertNull("releasedEntry should be cleared after requeue processed", ((QueueContext)subscription1.getQueueContext())._releasedEntry);
        assertNull("releasedEntry should be cleared after requeue processed", ((QueueContext)subscription2.getQueueContext())._releasedEntry);
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
        AMQMessage messageA = createMessage(new Long(24));
        _queue.enqueue(messageA);
        assertEquals(messageA, _subscription.getQueueContext().getLastSeenEntry().getMessage());

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
       _queue = new SimpleAMQQueue(_qname, false, null, true, false, _virtualHost, Collections.EMPTY_MAP);
       _queue.setDeleteOnNoConsumers(true);
       _queue.registerSubscription(_subscription, false);
       AMQMessage message = createMessage(new Long(25));
       _queue.enqueue(message);
       _queue.unregisterSubscription(_subscription);
       assertTrue("Queue was not deleted when subscription was removed",
                  _queue.isDeleted());
    }

    public void testResend() throws Exception
    {
        _queue.registerSubscription(_subscription, false);
        Long id = new Long(26);
        AMQMessage message = createMessage(id);
        _queue.enqueue(message);
        QueueEntry entry = _subscription.getQueueContext().getLastSeenEntry();
        entry.setRedelivered();
        _queue.resend(entry, _subscription);

    }

    public void testGetFirstMessageId() throws Exception
    {
        // Create message
        Long messageId = new Long(23);
        AMQMessage message = createMessage(messageId);

        // Put message on queue
        _queue.enqueue(message);
        // Get message id
        Long testmsgid = _queue.getMessagesOnTheQueue(1).get(0);

        // Check message id
        assertEquals("Message ID was wrong", messageId, testmsgid);
    }

    public void testGetFirstFiveMessageIds() throws Exception
    {
        for (int i = 0 ; i < 5; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = createMessage(messageId);
            // Put message on queue
            _queue.enqueue(message);
        }
        // Get message ids
        List<Long> msgids = _queue.getMessagesOnTheQueue(5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(i);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }

    public void testGetLastFiveMessageIds() throws Exception
    {
        for (int i = 0 ; i < 10; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = createMessage(messageId);
            // Put message on queue
            _queue.enqueue(message);
        }
        // Get message ids
        List<Long> msgids = _queue.getMessagesOnTheQueue(5, 5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(i+5);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }

    public void testGetMessagesRangeOnTheQueue() throws Exception
    {
        for (int i = 1 ; i <= 10; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = createMessage(messageId);
            // Put message on queue
            _queue.enqueue(message);
        }

        // Get non-existent 0th QueueEntry & check returned list was empty
        // (the position parameters in this method are indexed from 1)
        List<QueueEntry> entries = _queue.getMessagesRangeOnTheQueue(0, 0);
        assertTrue(entries.size() == 0);

        // Check that when 'from' is 0 it is ignored and the range continues from 1
        entries = _queue.getMessagesRangeOnTheQueue(0, 2);
        assertTrue(entries.size() == 2);
        long msgID = entries.get(0).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 1L);
        msgID = entries.get(1).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 2L);

        // Check that when 'from' is greater than 'to' the returned list is empty
        entries = _queue.getMessagesRangeOnTheQueue(5, 4);
        assertTrue(entries.size() == 0);

        // Get first QueueEntry & check id
        entries = _queue.getMessagesRangeOnTheQueue(1, 1);
        assertTrue(entries.size() == 1);
        msgID = entries.get(0).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 1L);

        // Get 5th,6th,7th entries and check id's
        entries = _queue.getMessagesRangeOnTheQueue(5, 7);
        assertTrue(entries.size() == 3);
        msgID = entries.get(0).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 5L);
        msgID = entries.get(1).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 6L);
        msgID = entries.get(2).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 7L);

        // Get 10th QueueEntry & check id
        entries = _queue.getMessagesRangeOnTheQueue(10, 10);
        assertTrue(entries.size() == 1);
        msgID = entries.get(0).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 10L);

        // Get non-existent 11th QueueEntry & check returned set was empty
        entries = _queue.getMessagesRangeOnTheQueue(11, 11);
        assertTrue(entries.size() == 0);

        // Get 9th,10th, and non-existent 11th entries & check result is of size 2 with correct IDs
        entries = _queue.getMessagesRangeOnTheQueue(9, 11);
        assertTrue(entries.size() == 2);
        msgID = entries.get(0).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 9L);
        msgID = entries.get(1).getMessage().getMessageNumber();
        assertEquals("Message ID was wrong", msgID, 10L);
    }

    public void testEnqueueDequeueOfPersistentMessageToNonDurableQueue() throws AMQException
    {
        // Create IncomingMessage and nondurable queue
        final IncomingMessage msg = new IncomingMessage(info);
        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.setProperties(new BasicContentHeaderProperties());
        ((BasicContentHeaderProperties) contentHeaderBody.getProperties()).setDeliveryMode((byte) 2);
        msg.setContentHeaderBody(contentHeaderBody);

        final ArrayList<BaseQueue> qs = new ArrayList<BaseQueue>();

        // Send persistent message

        qs.add(_queue);
        MessageMetaData metaData = msg.headersReceived();
        StoredMessage handle = _store.addMessage(metaData);
        msg.setStoredMessage(handle);


        ServerTransaction txn = new AutoCommitTransaction(_store);

        txn.enqueue(qs, msg, new ServerTransaction.Action()
                                    {
                                        public void postCommit()
                                        {
                                            msg.enqueue(qs);
                                        }

                                        public void onRollback()
                                        {
                                        }
                                    });



        // Check that it is enqueued
        AMQQueue data = _store.getMessages().get(1L);
        assertNull(data);

        // Dequeue message
        MockQueueEntry entry = new MockQueueEntry();
        AMQMessage amqmsg = new AMQMessage(handle);

        entry.setMessage(amqmsg);
        _queue.dequeue(entry,null);

        // Check that it is dequeued
        data = _store.getMessages().get(1L);
        assertNull(data);
    }


    /**
     * processQueue() is used when asynchronously delivering messages to
     * subscriptions which could not be delivered immediately during the
     * enqueue() operation.
     *
     * A defect within the method would mean that delivery of these messages may
     * not occur should the Runner stop before all messages have been processed.
     * Such a defect was discovered when Selectors were used such that one and
     * only one subscription can/will accept any given messages, but multiple
     * subscriptions are present, and one of the earlier subscriptions receives
     * more messages than the others.
     *
     * This test is to validate that the processQueue() method is able to
     * correctly deliver all of the messages present for asynchronous delivery
     * to subscriptions in such a scenario.
     */
    public void testProcessQueueWithUniqueSelectors() throws Exception
    {
        TestSimpleQueueEntryListFactory factory = new TestSimpleQueueEntryListFactory();
        SimpleAMQQueue testQueue = new SimpleAMQQueue("testQueue", false, "testOwner",false,
                                                      false, _virtualHost, factory, null)
        {
            @Override
            public void deliverAsync(Subscription sub)
            {
                // do nothing, i.e prevent deliveries by the SubFlushRunner
                // when registering the new subscriptions
            }
        };

        // retrieve the QueueEntryList the queue creates and insert the test
        // messages, thus avoiding straight-through delivery attempts during
        //enqueue() process.
        QueueEntryList list = factory.getQueueEntryList();
        assertNotNull("QueueEntryList should have been created", list);

        QueueEntry msg1 = list.add(createMessage(1L));
        QueueEntry msg2 = list.add(createMessage(2L));
        QueueEntry msg3 = list.add(createMessage(3L));
        QueueEntry msg4 = list.add(createMessage(4L));
        QueueEntry msg5 = list.add(createMessage(5L));

        // Create lists of the entries each subscription should be interested
        // in.Bias over 50% of the messages to the first subscription so that
        // the later subscriptions reject them and report being done before
        // the first subscription as the processQueue method proceeds.
        List<QueueEntry> msgListSub1 = createEntriesList(msg1, msg2, msg3);
        List<QueueEntry> msgListSub2 = createEntriesList(msg4);
        List<QueueEntry> msgListSub3 = createEntriesList(msg5);

        MockSubscription sub1 = new MockSubscription(msgListSub1);
        MockSubscription sub2 = new MockSubscription(msgListSub2);
        MockSubscription sub3 = new MockSubscription(msgListSub3);

        // register the subscriptions
        testQueue.registerSubscription(sub1, false);
        testQueue.registerSubscription(sub2, false);
        testQueue.registerSubscription(sub3, false);

        //check that no messages have been delivered to the
        //subscriptions during registration
        assertEquals("No messages should have been delivered yet", 0, sub1.getMessages().size());
        assertEquals("No messages should have been delivered yet", 0, sub2.getMessages().size());
        assertEquals("No messages should have been delivered yet", 0, sub3.getMessages().size());

        // call processQueue to deliver the messages
        testQueue.processQueue(new QueueRunner(testQueue, 1)
        {
            @Override
            public void run()
            {
                // we dont actually want/need this runner to do any work
                // because we we are already doing it!
            }
        });

        // check expected messages delivered to correct consumers
        verifyRecievedMessages(msgListSub1, sub1.getMessages());
        verifyRecievedMessages(msgListSub2, sub2.getMessages());
        verifyRecievedMessages(msgListSub3, sub3.getMessages());
    }

    /**
     * Tests that dequeued message is not present in the list returned form
     * {@link SimpleAMQQueue#getMessagesOnTheQueue()}
     */
    public void testGetMessagesOnTheQueueWithDequeuedEntry()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // send test messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message
        dequeueMessage(_queue, dequeueMessageIndex);

        // get messages on the queue
        List<QueueEntry> entries = _queue.getMessagesOnTheQueue();

        // assert queue entries
        assertEquals(messageNumber - 1, entries.size());
        int expectedId = 0;
        for (int i = 0; i < messageNumber - 1; i++)
        {
            Long id = ((AMQMessage) entries.get(i).getMessage()).getMessageId();
            if (i == dequeueMessageIndex)
            {
                assertFalse("Message with id " + dequeueMessageIndex
                        + " was dequeued and should not be returned by method getMessagesOnTheQueue!",
                        new Long(expectedId).equals(id));
                expectedId++;
            }
            assertEquals("Expected message with id " + expectedId + " but got message with id " + id,
                    new Long(expectedId), id);
            expectedId++;
        }
    }

    /**
     * Tests that dequeued message is not present in the list returned form
     * {@link SimpleAMQQueue#getMessagesOnTheQueue(QueueEntryFilter)}
     */
    public void testGetMessagesOnTheQueueByQueueEntryFilterWithDequeuedEntry()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // send test messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message
        dequeueMessage(_queue, dequeueMessageIndex);

        // get messages on the queue with filter accepting all available messages
        List<QueueEntry> entries = _queue.getMessagesOnTheQueue(new QueueEntryFilter()
        {

            @Override
            public boolean accept(QueueEntry entry)
            {
                return true;
            }

            @Override
            public boolean filterComplete()
            {
                return false;
            }
        });

        // assert entries on the queue
        assertEquals(messageNumber - 1, entries.size());
        int expectedId = 0;
        for (int i = 0; i < messageNumber - 1; i++)
        {
            Long id = ((AMQMessage) entries.get(i).getMessage()).getMessageId();
            if (i == dequeueMessageIndex)
            {
                assertFalse("Message with id " + dequeueMessageIndex
                        + " was dequeued and should not be returned by method getMessagesOnTheQueue!",
                        new Long(expectedId).equals(id));
                expectedId++;
            }
            assertEquals("Expected message with id " + expectedId + " but got message with id " + id,
                    new Long(expectedId), id);
            expectedId++;
        }
    }

    /**
     * Tests that dequeued message is not copied as part of invocation of
     * {@link SimpleAMQQueue#copyMessagesToAnotherQueue(long, long, String, StoreContext)}
     */
    public void testCopyMessagesWithDequeuedEntry()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;
        String anotherQueueName = "testQueue2";

        // put test messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message
        dequeueMessage(_queue, dequeueMessageIndex);

        // create another queue
        SimpleAMQQueue queue = createQueue(anotherQueueName);

        // create transaction
        ServerTransaction txn = new LocalTransaction(_queue.getVirtualHost().getTransactionLog());

        // copy messages into another queue
        _queue.copyMessagesToAnotherQueue(0, messageNumber, anotherQueueName, txn);

        // commit transaction
        txn.commit();

        // get messages on another queue
        List<QueueEntry> entries = queue.getMessagesOnTheQueue();

        // assert another queue entries
        assertEquals(messageNumber - 1, entries.size());
        int expectedId = 0;
        for (int i = 0; i < messageNumber - 1; i++)
        {
            Long id = ((AMQMessage)entries.get(i).getMessage()).getMessageId();
            if (i == dequeueMessageIndex)
            {
                assertFalse("Message with id " + dequeueMessageIndex
                        + " was dequeued and should not been copied into another queue!",
                        new Long(expectedId).equals(id));
                expectedId++;
            }
            assertEquals("Expected message with id " + expectedId + " but got message with id " + id,
                    new Long(expectedId), id);
            expectedId++;
        }
    }

    /**
     * Tests that dequeued message is not moved as part of invocation of
     * {@link SimpleAMQQueue#moveMessagesToAnotherQueue(long, long, String, StoreContext)}
     */
    public void testMovedMessagesWithDequeuedEntry()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;
        String anotherQueueName = "testQueue2";

        // put messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message
        dequeueMessage(_queue, dequeueMessageIndex);

        // create another queue
        SimpleAMQQueue queue = createQueue(anotherQueueName);

        // create transaction
        ServerTransaction txn = new LocalTransaction(_queue.getVirtualHost().getTransactionLog());

        // move messages into another queue
        _queue.moveMessagesToAnotherQueue(0, messageNumber, anotherQueueName, txn);

        // commit transaction
        txn.commit();

        // get messages on another queue
        List<QueueEntry> entries = queue.getMessagesOnTheQueue();

        // assert another queue entries
        assertEquals(messageNumber - 1, entries.size());
        int expectedId = 0;
        for (int i = 0; i < messageNumber - 1; i++)
        {
            Long id = ((AMQMessage)entries.get(i).getMessage()).getMessageId();
            if (i == dequeueMessageIndex)
            {
                assertFalse("Message with id " + dequeueMessageIndex
                        + " was dequeued and should not been copied into another queue!",
                        new Long(expectedId).equals(id));
                expectedId++;
            }
            assertEquals("Expected message with id " + expectedId + " but got message with id " + id,
                    new Long(expectedId), id);
            expectedId++;
        }
    }

    /**
     * Tests that messages in given range including dequeued one are deleted
     * from the queue on invocation of
     * {@link SimpleAMQQueue#removeMessagesFromQueue(long, long, StoreContext)}
     */
    public void testRemoveMessagesFromQueueWithDequeuedEntry()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // put messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message
        dequeueMessage(_queue, dequeueMessageIndex);

        // remove messages
        _queue.removeMessagesFromQueue(0, messageNumber);

        // get queue entries
        List<QueueEntry> entries = _queue.getMessagesOnTheQueue();

        // assert queue entries
        assertNotNull("Null is returned from getMessagesOnTheQueue", entries);
        assertEquals("Queue should be empty", 0, entries.size());
    }

    /**
     * Tests that dequeued message on the top is not accounted and next message
     * is deleted from the queue on invocation of
     * {@link SimpleAMQQueue#deleteMessageFromTop(StoreContext)}
     */
    public void testDeleteMessageFromTopWithDequeuedEntryOnTop()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 0;

        // put messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message on top
        dequeueMessage(_queue, dequeueMessageIndex);

        //delete message from top
        _queue.deleteMessageFromTop();

        //get queue netries
        List<QueueEntry> entries = _queue.getMessagesOnTheQueue();

        // assert queue entries
        assertNotNull("Null is returned from getMessagesOnTheQueue", entries);
        assertEquals("Expected " + (messageNumber - 2) + " number of messages  but recieved " + entries.size(),
                messageNumber - 2, entries.size());
        assertEquals("Expected first entry with id 2", new Long(2),
                ((AMQMessage) entries.get(0).getMessage()).getMessageId());
    }

    /**
     * Tests that all messages including dequeued one are deleted from the queue
     * on invocation of {@link SimpleAMQQueue#clearQueue(StoreContext)}
     */
    public void testClearQueueWithDequeuedEntry()
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // put messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message on a test queue
        dequeueMessage(_queue, dequeueMessageIndex);

        // clean queue
        try
        {
            _queue.clearQueue();
        }
        catch (AMQException e)
        {
            fail("Failure to clear queue:" + e.getMessage());
        }

        // get queue entries
        List<QueueEntry> entries = _queue.getMessagesOnTheQueue();

        // assert queue entries
        assertNotNull(entries);
        assertEquals(0, entries.size());
    }

    /**
     * Tests whether dequeued entry is sent to subscriber in result of
     * invocation of {@link SimpleAMQQueue#processQueue(QueueRunner)}
     */
    public void testProcessQueueWithDequeuedEntry()
    {
        // total number of messages to send
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // create queue with overridden method deliverAsync
        SimpleAMQQueue testQueue = new SimpleAMQQueue(new AMQShortString("test"), false,
                new AMQShortString("testOwner"), false, false, _virtualHost, null)
        {
            @Override
            public void deliverAsync(Subscription sub)
            {
                // do nothing
            }
        };

        // put messages
        List<QueueEntry> entries = enqueueGivenNumberOfMessages(testQueue, messageNumber);

        // dequeue message
        dequeueMessage(testQueue, dequeueMessageIndex);

        // latch to wait for message receipt
        final CountDownLatch latch = new CountDownLatch(messageNumber -1);

        // create a subscription
        MockSubscription subscription = new MockSubscription()
        {
            /**
             * Send a message and decrement latch
             */
            public void send(QueueEntry msg) throws AMQException
            {
                super.send(msg);
                latch.countDown();
            }
        };

        try
        {
            // subscribe
            testQueue.registerSubscription(subscription, false);

            // process queue
            testQueue.processQueue(new QueueRunner(testQueue, 1)
            {
                public void run()
                {
                    // do nothing
                }
            });
        }
        catch (AMQException e)
        {
            fail("Failure to process queue:" + e.getMessage());
        }
        // wait up to 1 minute for message receipt
        try
        {
            latch.await(1, TimeUnit.MINUTES);
        }
        catch (InterruptedException e1)
        {
            Thread.currentThread().interrupt();
        }
        List<QueueEntry> expected = createEntriesList(entries.get(0), entries.get(2), entries.get(3));
        verifyRecievedMessages(expected, subscription.getMessages());
    }

    /**
     * Tests that entry in dequeued state are not enqueued and not delivered to subscription
     */
    public void testEqueueDequeuedEntry()
    {
        // create a queue where each even entry is considered a dequeued
        SimpleAMQQueue queue = new SimpleAMQQueue(new AMQShortString("test"), false, new AMQShortString("testOwner"),
                false, false, _virtualHost, new QueueEntryListFactory()
                {
                    public QueueEntryList createQueueEntryList(AMQQueue queue)
                    {
                        /**
                         * Override SimpleQueueEntryList to create a dequeued
                         * entries for messages with even id
                         */
                        return new SimpleQueueEntryList(queue)
                        {
                            /**
                             * Entries with even message id are considered
                             * dequeued!
                             */
                            protected QueueEntryImpl createQueueEntry(final ServerMessage message)
                            {
                                return new QueueEntryImpl(this, message)
                                {
                                    public boolean isDequeued()
                                    {
                                        return (((AMQMessage) message).getMessageId().longValue() % 2 == 0);
                                    }

                                    public boolean isDispensed()
                                    {
                                        return (((AMQMessage) message).getMessageId().longValue() % 2 == 0);
                                    }

                                    public boolean isAvailable()
                                    {
                                        return !(((AMQMessage) message).getMessageId().longValue() % 2 == 0);
                                    }
                                };
                            }
                        };
                    }
                }, null);
        // create a subscription
        MockSubscription subscription = new MockSubscription();

        // register subscription
        try
        {
            queue.registerSubscription(subscription, false);
        }
        catch (AMQException e)
        {
            fail("Failure to register subscription:" + e.getMessage());
        }

        // put test messages into a queue
        putGivenNumberOfMessages(queue, 4);

        // assert received messages
        List<QueueEntry> messages = subscription.getMessages();
        assertEquals("Only 2 messages should be returned", 2, messages.size());
        assertEquals("ID of first message should be 1", new Long(1),
                ((AMQMessage) messages.get(0).getMessage()).getMessageId());
        assertEquals("ID of second message should be 3", new Long(3),
                ((AMQMessage) messages.get(1).getMessage()).getMessageId());
    }

    /**
     * A helper method to create a queue with given name
     *
     * @param name
     *            queue name
     * @return queue
     */
    private SimpleAMQQueue createQueue(String name)
    {
        SimpleAMQQueue queue = null;
        try
        {
            AMQShortString queueName = new AMQShortString(name);
            AMQShortString ownerName = new AMQShortString(name + "Owner");
            queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(queueName, false, ownerName, false, false,
                    _virtualHost, _arguments);
        }
        catch (AMQException e)
        {
            fail("Failure to create a queue:" + e.getMessage());
        }
        assertNotNull("Queue was not created", queue);
        return queue;
    }

    /**
     * A helper method to put given number of messages into queue
     * <p>
     * All messages are asserted that they are present on queue
     *
     * @param queue
     *            queue to put messages into
     * @param messageNumber
     *            number of messages to put into queue
     */
    private List<QueueEntry> enqueueGivenNumberOfMessages(AMQQueue queue, int messageNumber)
    {
        putGivenNumberOfMessages(queue, messageNumber);

        // make sure that all enqueued messages are on the queue
        List<QueueEntry> entries = queue.getMessagesOnTheQueue();
        assertEquals(messageNumber, entries.size());
        for (int i = 0; i < messageNumber; i++)
        {
            assertEquals(new Long(i), ((AMQMessage)entries.get(i).getMessage()).getMessageId());
        }
        return entries;
    }

    /**
     * A helper method to put given number of messages into queue
     * <p>
     * Queue is not checked if messages are added into queue
     *
     * @param queue
     *            queue to put messages into
     * @param messageNumber
     *            number of messages to put into queue
     * @param queue
     * @param messageNumber
     */
    private void putGivenNumberOfMessages(AMQQueue queue, int messageNumber)
    {
        for (int i = 0; i < messageNumber; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = null;
            try
            {
                message = createMessage(messageId);
            }
            catch (AMQException e)
            {
                fail("Failure to create a test message:" + e.getMessage());
            }
            // Put message on queue
            try
            {
                queue.enqueue(message);
            }
            catch (AMQException e)
            {
                fail("Failure to put message on queue:" + e.getMessage());
            }
        }
    }

    /**
     * A helper method to dequeue an entry on queue with given index
     *
     * @param queue
     *            queue to dequeue message on
     * @param dequeueMessageIndex
     *            entry index to dequeue.
     */
    private QueueEntry dequeueMessage(AMQQueue queue, int dequeueMessageIndex)
    {
        List<QueueEntry> entries = queue.getMessagesOnTheQueue();
        QueueEntry entry = entries.get(dequeueMessageIndex);
        entry.acquire();
        entry.dequeue();
        assertTrue(entry.isDequeued());
        return entry;
    }

    private List<QueueEntry> createEntriesList(QueueEntry... entries)
    {
        ArrayList<QueueEntry> entriesList = new ArrayList<QueueEntry>();
        for (QueueEntry entry : entries)
        {
            entriesList.add(entry);
        }
        return entriesList;
    }

    private void verifyRecievedMessages(List<QueueEntry> expected,
            List<QueueEntry> delivered)
    {
        assertEquals("Consumer did not receive the expected number of messages",
                    expected.size(), delivered.size());

        for (QueueEntry msg : expected)
        {
            assertTrue("Consumer did not recieve msg: "
                    + msg.getMessage().getMessageNumber(), delivered.contains(msg));
        }
    }

    public class TestMessage extends AMQMessage
    {
        private final long _tag;
        private int _count;

        TestMessage(long tag, long messageId, MessagePublishInfo publishBody)
                throws AMQException
        {
            this(tag, messageId, publishBody, new ContentHeaderBody(1, 1, new BasicContentHeaderProperties(), 0));

        }
        TestMessage(long tag, long messageId, MessagePublishInfo publishBody, ContentHeaderBody chb)
                throws AMQException
        {
            super(new MockStoredMessage(messageId, publishBody, chb));
            _tag = tag;
        }

        public boolean incrementReference()
        {
            _count++;
            return true;
        }

        public void decrementReference()
        {
            _count--;
        }

        void assertCountEquals(int expected)
        {
            assertEquals("Wrong count for message with tag " + _tag, expected, _count);
        }
    }

    protected AMQMessage createMessage(Long id) throws AMQException
    {
        AMQMessage messageA = new TestMessage(id, id, info);
        return messageA;
    }

    class TestSimpleQueueEntryListFactory implements QueueEntryListFactory
    {
        QueueEntryList _list;

        public QueueEntryList createQueueEntryList(AMQQueue queue)
        {
            _list = new SimpleQueueEntryList(queue);
            return _list;
        }

        public QueueEntryList getQueueEntryList()
        {
            return _list;
        }
    }
}
