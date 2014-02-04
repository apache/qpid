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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.SimpleAMQQueue.QueueEntryFilter;
import org.apache.qpid.server.subscription.MockSubscription;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionTarget;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SimpleAMQQueueTest extends QpidTestCase
{
    private static final Logger _logger = Logger.getLogger(SimpleAMQQueueTest.class);

    private SimpleAMQQueue _queue;
    private VirtualHost _virtualHost;
    private String _qname = "qname";
    private String _owner = "owner";
    private String _routingKey = "routing key";
    private DirectExchange _exchange;
    private MockSubscription _subscriptionTarget = new MockSubscription();
    private QueueSubscription _subscription;
    private Map<String,Object> _arguments = null;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();

        _virtualHost = BrokerTestHelper.createVirtualHost(getClass().getName());

        _queue = (SimpleAMQQueue) _virtualHost.createQueue(UUIDGenerator.generateRandomUUID(), _qname, false, _owner,
                false, false, false, _arguments);

        _exchange = (DirectExchange) _virtualHost.getExchange(ExchangeDefaults.DIRECT_EXCHANGE_NAME);
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _queue.stop();
            _virtualHost.close();
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testCreateQueue() throws AMQException
    {
        _queue.stop();
        try
        {
            _queue = (SimpleAMQQueue) _virtualHost.createQueue(UUIDGenerator.generateRandomUUID(), null,
                                                                         false, _owner, false,
                                                                         false, false, _arguments);
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing name",
                            e.getMessage().contains("name"));
        }

        try
        {
            _queue = new SimpleAMQQueue(UUIDGenerator.generateRandomUUID(), _qname, false, _owner, false,false, null, Collections.EMPTY_MAP);
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing vhost",
                    e.getMessage().contains("Host"));
        }

        _queue = (SimpleAMQQueue) _virtualHost.createQueue(UUIDGenerator.generateRandomUUID(),
                                                                     "differentName", false,
                                                                     _owner, false,
                                                                     false, false, _arguments);
        assertNotNull("Queue was not created", _queue);
    }

    public void testGetVirtualHost()
    {
        assertEquals("Virtual host was wrong", _virtualHost, _queue.getVirtualHost());
    }

    public void testBinding() throws AMQSecurityException, AMQInternalException
    {
        _exchange.addBinding(_routingKey, _queue, Collections.EMPTY_MAP);

        assertTrue("Routing key was not bound",
                        _exchange.isBound(_routingKey));
        assertTrue("Queue was not bound to key",
                    _exchange.isBound(_routingKey,_queue));
        assertEquals("Exchange binding count", 1,
                _queue.getBindings().size());
        assertEquals("Wrong exchange bound", _routingKey,
                _queue.getBindings().get(0).getBindingKey());
        assertEquals("Wrong exchange bound", _exchange,
                _queue.getBindings().get(0).getExchange());

        _exchange.removeBinding(_routingKey, _queue, Collections.EMPTY_MAP);
        assertFalse("Routing key was still bound",
                _exchange.isBound(_routingKey));

    }

    public void testRegisterSubscriptionThenEnqueueMessage() throws AMQException
    {
        ServerMessage messageA = createMessage(new Long(24));

        // Check adding a subscription adds it to the queue
        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.noneOf(Subscription.Option.class));
        assertEquals("Queue does not have consumer", 1,
                     _queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1,
                     _queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        _queue.enqueue(messageA);
        try
        {
            Thread.sleep(2000L);
        }
        catch(InterruptedException e)
        {
        }
        assertEquals(messageA, _subscription.getQueueContext().getLastSeenEntry().getMessage());
        assertNull(_subscription.getQueueContext().getReleasedEntry());

        // Check removing the subscription removes it's information from the queue
        _subscription.close();
        assertTrue("Subscription still had queue", _subscriptionTarget.isClosed());
        assertFalse("Queue still has consumer", 1 == _queue.getConsumerCount());
        assertFalse("Queue still has active consumer",
                    1 == _queue.getActiveConsumerCount());

        ServerMessage messageB = createMessage(new Long (25));
        _queue.enqueue(messageB);
         assertNull(_subscription.getQueueContext());

    }

    public void testEnqueueMessageThenRegisterSubscription() throws AMQException, InterruptedException
    {
        ServerMessage messageA = createMessage(new Long(24));
        _queue.enqueue(messageA);
        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.noneOf(Subscription.Option.class));
        Thread.sleep(150);
        assertEquals(messageA, _subscription.getQueueContext().getLastSeenEntry().getMessage());
        assertNull("There should be no releasedEntry after an enqueue", _subscription.getQueueContext().getReleasedEntry());
    }

    /**
     * Tests enqueuing two messages.
     */
    public void testEnqueueTwoMessagesThenRegisterSubscription() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));
        _queue.enqueue(messageA);
        _queue.enqueue(messageB);
        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.noneOf(Subscription.Option.class));
        Thread.sleep(150);
        assertEquals(messageB, _subscription.getQueueContext().getLastSeenEntry().getMessage());
        assertNull("There should be no releasedEntry after enqueues",  _subscription.getQueueContext().getReleasedEntry());
    }

    /**
     * Tests that a released queue entry is resent to the subscriber.  Verifies also that the
     * QueueContext._releasedEntry is reset to null after the entry has been reset.
     */
    public void testReleasedMessageIsResentToSubscriber() throws Exception
    {

        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));
        ServerMessage messageC = createMessage(new Long(26));


        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.of(Subscription.Option.ACQUIRES,
                                                               Subscription.Option.SEES_REQUEUES));

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        Action<QueueEntry> postEnqueueAction = new Action<QueueEntry>()
        {
            public void performAction(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        /* Enqueue three messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);
        _queue.enqueue(messageC, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 3, _subscriptionTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(2).isRedelivered());

        /* Now release the first message only, causing it to be requeued */

        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 4, _subscriptionTarget.getMessages().size());
        assertTrue("Redelivery flag should now be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should remain be unset", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should remain be unset",queueEntries.get(2).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed", _subscription.getQueueContext().getReleasedEntry());
    }

    /**
     * Tests that a released message that becomes expired is not resent to the subscriber.
     * This tests ensures that SimpleAMQQueueEntry.getNextAvailableEntry avoids expired entries.
     * Verifies also that the QueueContext._releasedEntry is reset to null after the entry has been reset.
     */
    public void testReleaseMessageThatBecomesExpiredIsNotRedelivered() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));

        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.of(Subscription.Option.SEES_REQUEUES,
                                                               Subscription.Option.ACQUIRES));

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        Action<QueueEntry> postEnqueueAction = new Action<QueueEntry>()
        {
            public void performAction(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        /* Enqueue one message with expiration set for a short time in the future */

        int messageExpirationOffset = 200;
        final long expiration = System.currentTimeMillis() + messageExpirationOffset;
        when(messageA.getExpiration()).thenReturn(expiration);

        _queue.enqueue(messageA, postEnqueueAction);

        int subFlushWaitTime = 150;
        Thread.sleep(subFlushWaitTime); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 1, _subscriptionTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());

        /* Wait a little more to be sure that message will have expired, then release the first message only, causing it to be requeued */
        Thread.sleep(messageExpirationOffset - subFlushWaitTime + 10);
        queueEntries.get(0).release();

        Thread.sleep(subFlushWaitTime); // Work done by SubFlushRunner/QueueRunner Threads

        assertTrue("Expecting the queue entry to be now expired", queueEntries.get(0).expired());
        assertEquals("Total number of messages sent should not have changed", 1, _subscriptionTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed", _subscription.getQueueContext().getReleasedEntry());

    }

    /**
     * Tests that if a client releases entries 'out of order' (the order
     * used by QueueEntryImpl.compareTo) that messages are still resent
     * successfully.  Specifically this test ensures the {@see SimpleAMQQueue#requeue()}
     * can correctly move the _releasedEntry to an earlier position in the QueueEntry list.
     */
    public void testReleasedOutOfComparableOrderAreRedelivered() throws Exception
    {

        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));
        ServerMessage messageC = createMessage(new Long(26));

        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.of(Subscription.Option.ACQUIRES,
                                                               Subscription.Option.SEES_REQUEUES));

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        Action<QueueEntry> postEnqueueAction = new Action<QueueEntry>()
        {
            public void performAction(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };

        /* Enqueue three messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);
        _queue.enqueue(messageC, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 3, _subscriptionTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(2).isRedelivered());

        /* Now release the third and first message only, causing it to be requeued */

        queueEntries.get(2).release();
        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to subscription", 5, _subscriptionTarget.getMessages().size());
        assertTrue("Redelivery flag should now be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should remain be unset", queueEntries.get(1).isRedelivered());
        assertTrue("Redelivery flag should now be set",queueEntries.get(2).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed", _subscription.getQueueContext().getReleasedEntry());
    }


    /**
     * Tests that a release requeues an entry for a queue with multiple subscriptions.  Verifies that a
     * requeue resends a message to a <i>single</i> subscriber.
     */
    public void testReleaseForQueueWithMultipleSubscriptions() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));

        MockSubscription target1 = new MockSubscription();
        MockSubscription target2 = new MockSubscription();


        QueueSubscription subscription1 = _queue.registerSubscription(target1, null, messageA.getClass(), "test",
                                                                 EnumSet.of(Subscription.Option.ACQUIRES,
                                                                            Subscription.Option.SEES_REQUEUES));

        QueueSubscription subscription2 = _queue.registerSubscription(target2, null, messageA.getClass(), "test",
                                                                 EnumSet.of(Subscription.Option.ACQUIRES,
                                                                            Subscription.Option.SEES_REQUEUES));


        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        Action<QueueEntry> postEnqueueAction = new Action<QueueEntry>()
        {
            public void performAction(QueueEntry entry)
            {
                queueEntries.add(entry);
            }
        };


        /* Enqueue two messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to both after enqueue",
                     2,
                     target1.getMessages().size() + target2.getMessages().size());

        /* Now release the first message only, causing it to be requeued */
        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to both subscriptions after release",
                     3,
                     target1.getMessages().size() + target2.getMessages().size());
        assertNull("releasedEntry should be cleared after requeue processed", subscription1.getQueueContext().getReleasedEntry());
        assertNull("releasedEntry should be cleared after requeue processed", subscription2.getQueueContext().getReleasedEntry());
    }

    public void testExclusiveConsumer() throws AMQException
    {
        ServerMessage messageA = createMessage(new Long(24));
        // Check adding an exclusive subscription adds it to the queue

        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.of(Subscription.Option.EXCLUSIVE));

        assertEquals("Queue does not have consumer", 1,
                     _queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1,
                     _queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        _queue.enqueue(messageA);
        try
        {
            Thread.sleep(2000L);
        }
        catch (InterruptedException e)
        {
        }
        assertEquals(messageA, _subscription.getQueueContext().getLastSeenEntry().getMessage());

        // Check we cannot add a second subscriber to the queue
        MockSubscription subB = new MockSubscription();
        Exception ex = null;
        try
        {

            _queue.registerSubscription(subB, null, messageA.getClass(), "test",
                                        EnumSet.noneOf(Subscription.Option.class));

        }
        catch (AMQException e)
        {
           ex = e;
        }
        assertNotNull(ex);

        // Check we cannot add an exclusive subscriber to a queue with an
        // existing subscription
        _subscription.close();
        _subscription = _queue.registerSubscription(_subscriptionTarget, null, messageA.getClass(), "test",
                                                    EnumSet.noneOf(Subscription.Option.class));

        try
        {

            _subscription = _queue.registerSubscription(subB, null, messageA.getClass(), "test",
                                                        EnumSet.of(Subscription.Option.EXCLUSIVE));

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
       _queue = new SimpleAMQQueue(UUIDGenerator.generateRandomUUID(), _qname, false, null, true, false, _virtualHost, Collections.EMPTY_MAP);
       _queue.setDeleteOnNoConsumers(true);

        ServerMessage message = createMessage(new Long(25));
        _subscription = _queue.registerSubscription(_subscriptionTarget, null, message.getClass(), "test",
                                                    EnumSet.noneOf(Subscription.Option.class));

       _queue.enqueue(message);
       _subscription.close();
       assertTrue("Queue was not deleted when subscription was removed",
                  _queue.isDeleted());
    }

    public void testResend() throws Exception
    {
        Long id = new Long(26);
        ServerMessage message = createMessage(id);

        _subscription = _queue.registerSubscription(_subscriptionTarget, null, message.getClass(), "test",
                                                    EnumSet.noneOf(Subscription.Option.class));

        _queue.enqueue(message);
        QueueEntry entry = _subscription.getQueueContext().getLastSeenEntry();
        entry.setRedelivered();
        _subscription.resend(entry);

    }

    public void testGetFirstMessageId() throws Exception
    {
        // Create message
        Long messageId = new Long(23);
        ServerMessage message = createMessage(messageId);

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
            ServerMessage message = createMessage(messageId);
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
            ServerMessage message = createMessage(messageId);
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
            ServerMessage message = createMessage(messageId);
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
        SimpleAMQQueue testQueue = new SimpleAMQQueue(UUIDGenerator.generateRandomUUID(), "testQueue", false,"testOwner",
                                                      false, false, _virtualHost, factory, null)
        {
            @Override
            public void deliverAsync(QueueSubscription sub)
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
        List<String> msgListSub1 = createEntriesList(msg1, msg2, msg3);
        List<String> msgListSub2 = createEntriesList(msg4);
        List<String> msgListSub3 = createEntriesList(msg5);

        MockSubscription sub1 = new MockSubscription(msgListSub1);
        MockSubscription sub2 = new MockSubscription(msgListSub2);
        MockSubscription sub3 = new MockSubscription(msgListSub3);

        // register the subscriptions
        testQueue.registerSubscription(sub1, sub1.getFilters(), msg1.getMessage().getClass(), "test",
                                       EnumSet.of(Subscription.Option.ACQUIRES, Subscription.Option.SEES_REQUEUES));
        testQueue.registerSubscription(sub2, sub2.getFilters(), msg1.getMessage().getClass(), "test",
                                       EnumSet.of(Subscription.Option.ACQUIRES, Subscription.Option.SEES_REQUEUES));
        testQueue.registerSubscription(sub3, sub3.getFilters(), msg1.getMessage().getClass(), "test",
                                       EnumSet.of(Subscription.Option.ACQUIRES, Subscription.Option.SEES_REQUEUES));

        //check that no messages have been delivered to the
        //subscriptions during registration
        assertEquals("No messages should have been delivered yet", 0, sub1.getMessages().size());
        assertEquals("No messages should have been delivered yet", 0, sub2.getMessages().size());
        assertEquals("No messages should have been delivered yet", 0, sub3.getMessages().size());

        // call processQueue to deliver the messages
        testQueue.processQueue(new QueueRunner(testQueue)
        {
            @Override
            public void run()
            {
                // we don't actually want/need this runner to do any work
                // because we we are already doing it!
            }
        });

        // check expected messages delivered to correct consumers
        verifyReceivedMessages(Arrays.asList(msg1,msg2,msg3), sub1.getMessages());
        verifyReceivedMessages(Collections.singletonList(msg4), sub2.getMessages());
        verifyReceivedMessages(Collections.singletonList(msg5), sub3.getMessages());
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
            Long id = ( entries.get(i).getMessage()).getMessageNumber();
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
            public boolean accept(QueueEntry entry)
            {
                return true;
            }

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
            Long id = (entries.get(i).getMessage()).getMessageNumber();
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
     * Tests that dequeued message on the top is not accounted and next message
     * is deleted from the queue on invocation of
     * {@link SimpleAMQQueue#deleteMessageFromTop()}
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

        //get queue entries
        List<QueueEntry> entries = _queue.getMessagesOnTheQueue();

        // assert queue entries
        assertNotNull("Null is returned from getMessagesOnTheQueue", entries);
        assertEquals("Expected " + (messageNumber - 2) + " number of messages  but recieved " + entries.size(),
                messageNumber - 2, entries.size());
        assertEquals("Expected first entry with id 2", 2l,
                (entries.get(0).getMessage()).getMessageNumber());
    }

    /**
     * Tests that all messages including dequeued one are deleted from the queue
     * on invocation of {@link SimpleAMQQueue#clearQueue()}
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
        SimpleAMQQueue testQueue = new SimpleAMQQueue(UUIDGenerator.generateRandomUUID(), "test",
                false, "testOwner", false, false, _virtualHost, null)
        {
            @Override
            public void deliverAsync(QueueSubscription sub)
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
             * @param entry
             * @param batch
             */
            public void send(QueueEntry entry, boolean batch) throws AMQException
            {
                super.send(entry, batch);
                latch.countDown();
            }
        };

        try
        {
            // subscribe
            testQueue.registerSubscription(subscription, null, entries.get(0).getMessage().getClass(), "test", EnumSet.noneOf(Subscription.Option.class));

            // process queue
            testQueue.processQueue(new QueueRunner(testQueue)
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
        List<QueueEntry> expected = Arrays.asList(entries.get(0), entries.get(2), entries.get(3));
        verifyReceivedMessages(expected, subscription.getMessages());
    }

    /**
     * Tests that entry in dequeued state are not enqueued and not delivered to subscription
     */
    public void testEnqueueDequeuedEntry()
    {
        // create a queue where each even entry is considered a dequeued
        SimpleAMQQueue queue = new SimpleAMQQueue(UUIDGenerator.generateRandomUUID(), "test", false,
                "testOwner", false, false, _virtualHost, new QueueEntryListFactory()
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
                            protected SimpleQueueEntryImpl createQueueEntry(final ServerMessage message)
                            {
                                return new SimpleQueueEntryImpl(this, message)
                                {

                                    public boolean isDeleted()
                                    {
                                        return (message.getMessageNumber() % 2 == 0);
                                    }

                                    public boolean isAvailable()
                                    {
                                        return !(message.getMessageNumber() % 2 == 0);
                                    }

                                    @Override
                                    public boolean acquire(Subscription sub)
                                    {
                                        if(message.getMessageNumber() % 2 == 0)
                                        {
                                            return false;
                                        }
                                        else
                                        {
                                            return super.acquire(sub);
                                        }
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
            queue.registerSubscription(subscription, null, createMessage(-1l).getClass(), "test", EnumSet.noneOf(Subscription.Option.class));
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
        assertEquals("ID of first message should be 1", 1l,
                (messages.get(0).getMessage()).getMessageNumber());
        assertEquals("ID of second message should be 3", 3l,
                (messages.get(1).getMessage()).getMessageNumber());
    }

    public void testActiveConsumerCount() throws Exception
    {
        final SimpleAMQQueue queue = new SimpleAMQQueue(UUIDGenerator.generateRandomUUID(), "testActiveConsumerCount", false,
                "testOwner", false, false, _virtualHost, new SimpleQueueEntryList.Factory(), null);

        //verify adding an active subscription increases the count
        final MockSubscription subscription1 = new MockSubscription();
        subscription1.setActive(true);
        subscription1.setState(SubscriptionTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());
        queue.registerSubscription(subscription1, null, createMessage(-1l).getClass(), "test", EnumSet.noneOf(Subscription.Option.class));
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify adding an inactive subscription doesn't increase the count
        final MockSubscription subscription2 = new MockSubscription();
        subscription2.setActive(false);
        subscription2.setState(SubscriptionTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());
        queue.registerSubscription(subscription2, null, createMessage(-1l).getClass(), "test", EnumSet.noneOf(Subscription.Option.class));
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify behaviour in face of expected state changes:

        //verify a subscription going suspended->active increases the count
        subscription2.setState(SubscriptionTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 2, queue.getActiveConsumerCount());

        //verify a subscription going active->suspended decreases the count
        subscription2.setState(SubscriptionTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify a subscription going suspended->closed doesn't change the count
        subscription2.setState(SubscriptionTarget.State.CLOSED);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify a subscription going active->active doesn't change the count
        subscription1.setState(SubscriptionTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        subscription1.setState(SubscriptionTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());

        //verify a subscription going suspended->suspended doesn't change the count
        subscription1.setState(SubscriptionTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());

        subscription1.setState(SubscriptionTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify a subscription going active->closed  decreases the count
        subscription1.setState(SubscriptionTarget.State.CLOSED);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());

    }

    public void testNotificationFiredOnEnqueue() throws Exception
    {
        AMQQueue.NotificationListener listener = mock(AMQQueue.NotificationListener.class);

        _queue.setNotificationListener(listener);
        _queue.setMaximumMessageCount(2);

        _queue.enqueue(createMessage(new Long(24)));
        verifyZeroInteractions(listener);

        _queue.enqueue(createMessage(new Long(25)));

        verify(listener, atLeastOnce()).notifyClients(eq(NotificationCheck.MESSAGE_COUNT_ALERT), eq(_queue), contains("Maximum count on queue threshold"));
    }

    public void testNotificationFiredAsync() throws Exception
    {
        AMQQueue.NotificationListener listener = mock(AMQQueue.NotificationListener.class);

        _queue.enqueue(createMessage(new Long(24)));
        _queue.enqueue(createMessage(new Long(25)));
        _queue.enqueue(createMessage(new Long(26)));

        _queue.setNotificationListener(listener);
        _queue.setMaximumMessageCount(2);

        verifyZeroInteractions(listener);

        _queue.checkMessageStatus();

        verify(listener, atLeastOnce()).notifyClients(eq(NotificationCheck.MESSAGE_COUNT_ALERT), eq(_queue), contains("Maximum count on queue threshold"));
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
            assertEquals((long)i, (entries.get(i).getMessage()).getMessageNumber());
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
            ServerMessage message = null;
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
        try
        {
            Thread.sleep(2000L);
        }
        catch (InterruptedException e)
        {
            _logger.error("Thread interrupted", e);
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
        entry.delete();
        assertTrue(entry.isDeleted());
        return entry;
    }

    private List<String> createEntriesList(QueueEntry... entries)
    {
        ArrayList<String> entriesList = new ArrayList<String>();
        for (QueueEntry entry : entries)
        {
            entriesList.add(entry.getMessage().getMessageHeader().getMessageId());
        }
        return entriesList;
    }

    private void verifyReceivedMessages(List<QueueEntry> expected,
                                        List<QueueEntry> delivered)
    {
        assertEquals("Consumer did not receive the expected number of messages",
                    expected.size(), delivered.size());

        for (QueueEntry msg : expected)
        {
            assertTrue("Consumer did not receive msg: "
                    + msg.getMessage().getMessageNumber(), delivered.contains(msg));
        }
    }

    public SimpleAMQQueue getQueue()
    {
        return _queue;
    }

    public MockSubscription getSubscription()
    {
        return _subscriptionTarget;
    }

    public Map<String,Object> getArguments()
    {
        return _arguments;
    }

    public void setArguments(Map<String,Object> arguments)
    {
        _arguments = arguments;
    }


    protected ServerMessage createMessage(Long id) throws AMQException
    {
        AMQMessageHeader header = mock(AMQMessageHeader.class);
        when(header.getMessageId()).thenReturn(String.valueOf(id));
        ServerMessage message = mock(ServerMessage.class);
        when(message.getMessageNumber()).thenReturn(id);
        when(message.getMessageHeader()).thenReturn(header);

        MessageReference ref = mock(MessageReference.class);
        when(ref.getMessage()).thenReturn(message);


        when(message.newReference()).thenReturn(ref);

        return message;
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
