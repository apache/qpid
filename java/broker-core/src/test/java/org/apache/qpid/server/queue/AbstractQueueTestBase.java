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

import java.util.*;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.AbstractQueue.QueueEntryFilter;
import org.apache.qpid.server.consumer.MockConsumer;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

abstract class AbstractQueueTestBase<E extends QueueEntryImpl<E,Q,L>, Q extends AbstractQueue<E,Q,L>, L extends QueueEntryListBase<E,Q,L>> extends QpidTestCase
{
    private static final Logger _logger = Logger.getLogger(AbstractQueueTestBase.class);


    private Q _queue;
    private VirtualHost _virtualHost;
    private String _qname = "qname";
    private String _owner = "owner";
    private String _routingKey = "routing key";
    private DirectExchange _exchange;
    private MockConsumer _consumerTarget = new MockConsumer();
    private QueueConsumer _consumer;
    private Map<String,Object> _arguments = Collections.emptyMap();

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();

        _virtualHost = BrokerTestHelper.createVirtualHost(getClass().getName());

        Map<String,Object> attributes = new HashMap<String, Object>(_arguments);
        attributes.put(Queue.ID, UUIDGenerator.generateRandomUUID());
        attributes.put(Queue.NAME, _qname);
        attributes.put(Queue.OWNER, _owner);

        _queue = (Q) _virtualHost.createQueue(attributes);

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

    public void testCreateQueue() throws Exception
    {
        _queue.stop();
        try
        {
            Map<String,Object> attributes = new HashMap<String, Object>(_arguments);
            attributes.put(Queue.ID, UUIDGenerator.generateRandomUUID());

            _queue = (Q) _virtualHost.createQueue(attributes);
            assertNull("Queue was created", _queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing name",
                            e.getMessage().contains("name"));
        }

        Map<String,Object> attributes = new HashMap<String, Object>(_arguments);
        attributes.put(Queue.ID, UUIDGenerator.generateRandomUUID());
        attributes.put(Queue.NAME, "differentName");
        _queue = (Q) _virtualHost.createQueue(attributes);
        assertNotNull("Queue was not created", _queue);
    }


    public void testGetVirtualHost()
    {
        assertEquals("Virtual host was wrong", _virtualHost, _queue.getVirtualHost());
    }

    public void testBinding()
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

        _exchange.getBinding(_routingKey, _queue).delete();
        assertFalse("Routing key was still bound",
                _exchange.isBound(_routingKey));

    }

    public void testRegisterConsumerThenEnqueueMessage() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));

        // Check adding a consumer adds it to the queue
        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                       EnumSet.of(Consumer.Option.ACQUIRES,
                                                  Consumer.Option.SEES_REQUEUES));
        assertEquals("Queue does not have consumer", 1,
                     _queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1,
                     _queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        _queue.enqueue(messageA, null);
        try
        {
            Thread.sleep(2000L);
        }
        catch(InterruptedException e)
        {
        }
        assertEquals(messageA, _consumer.getQueueContext().getLastSeenEntry().getMessage());
        assertNull(_consumer.getQueueContext().getReleasedEntry());

        // Check removing the consumer removes it's information from the queue
        _consumer.close();
        assertTrue("Consumer still had queue", _consumerTarget.isClosed());
        assertFalse("Queue still has consumer", 1 == _queue.getConsumerCount());
        assertFalse("Queue still has active consumer",
                    1 == _queue.getActiveConsumerCount());

        ServerMessage messageB = createMessage(new Long (25));
        _queue.enqueue(messageB, null);
         assertNull(_consumer.getQueueContext());

    }

    public void testEnqueueMessageThenRegisterConsumer() throws Exception, InterruptedException
    {
        ServerMessage messageA = createMessage(new Long(24));
        _queue.enqueue(messageA, null);
        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                       EnumSet.of(Consumer.Option.ACQUIRES,
                                                  Consumer.Option.SEES_REQUEUES));
        Thread.sleep(150);
        assertEquals(messageA, _consumer.getQueueContext().getLastSeenEntry().getMessage());
        assertNull("There should be no releasedEntry after an enqueue",
                   _consumer.getQueueContext().getReleasedEntry());
    }

    /**
     * Tests enqueuing two messages.
     */
    public void testEnqueueTwoMessagesThenRegisterConsumer() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));
        _queue.enqueue(messageA, null);
        _queue.enqueue(messageB, null);
        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                       EnumSet.of(Consumer.Option.ACQUIRES,
                                                  Consumer.Option.SEES_REQUEUES));
        Thread.sleep(150);
        assertEquals(messageB, _consumer.getQueueContext().getLastSeenEntry().getMessage());
        assertNull("There should be no releasedEntry after enqueues",
                   _consumer.getQueueContext().getReleasedEntry());
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


        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                           EnumSet.of(Consumer.Option.ACQUIRES,
                                                      Consumer.Option.SEES_REQUEUES));

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        EntryListAddingAction postEnqueueAction = new EntryListAddingAction(queueEntries);

        /* Enqueue three messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);
        _queue.enqueue(messageC, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to consumer",
                     3,
                     _consumerTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(2).isRedelivered());

        /* Now release the first message only, causing it to be requeued */

        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to consumer",
                     4,
                     _consumerTarget.getMessages().size());
        assertTrue("Redelivery flag should now be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should remain be unset", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should remain be unset",queueEntries.get(2).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed",
                   _consumer.getQueueContext().getReleasedEntry());
    }

    /**
     * Tests that a released message that becomes expired is not resent to the subscriber.
     * This tests ensures that SimpleAMQQueueEntry.getNextAvailableEntry avoids expired entries.
     * Verifies also that the QueueContext._releasedEntry is reset to null after the entry has been reset.
     */
    public void testReleaseMessageThatBecomesExpiredIsNotRedelivered() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));

        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                           EnumSet.of(Consumer.Option.SEES_REQUEUES,
                                                      Consumer.Option.ACQUIRES));

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        EntryListAddingAction postEnqueueAction = new EntryListAddingAction(queueEntries);

        /* Enqueue one message with expiration set for a short time in the future */

        int messageExpirationOffset = 200;
        final long expiration = System.currentTimeMillis() + messageExpirationOffset;
        when(messageA.getExpiration()).thenReturn(expiration);

        _queue.enqueue(messageA, postEnqueueAction);

        int subFlushWaitTime = 150;
        Thread.sleep(subFlushWaitTime); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to consumer",
                     1,
                     _consumerTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());

        /* Wait a little more to be sure that message will have expired, then release the first message only, causing it to be requeued */
        Thread.sleep(messageExpirationOffset - subFlushWaitTime + 10);
        queueEntries.get(0).release();

        Thread.sleep(subFlushWaitTime); // Work done by SubFlushRunner/QueueRunner Threads

        assertTrue("Expecting the queue entry to be now expired", queueEntries.get(0).expired());
        assertEquals("Total number of messages sent should not have changed",
                     1,
                     _consumerTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed",
                   _consumer.getQueueContext().getReleasedEntry());

    }

    /**
     * Tests that if a client releases entries 'out of order' (the order
     * used by QueueEntryImpl.compareTo) that messages are still resent
     * successfully.  Specifically this test ensures the {@see AbstractQueue#requeue()}
     * can correctly move the _releasedEntry to an earlier position in the QueueEntry list.
     */
    public void testReleasedOutOfComparableOrderAreRedelivered() throws Exception
    {

        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));
        ServerMessage messageC = createMessage(new Long(26));

        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                           EnumSet.of(Consumer.Option.ACQUIRES,
                                                      Consumer.Option.SEES_REQUEUES));

        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        EntryListAddingAction postEnqueueAction = new EntryListAddingAction(queueEntries);

        /* Enqueue three messages */

        _queue.enqueue(messageA, postEnqueueAction);
        _queue.enqueue(messageB, postEnqueueAction);
        _queue.enqueue(messageC, postEnqueueAction);

        Thread.sleep(150);  // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to consumer",
                     3,
                     _consumerTarget.getMessages().size());
        assertFalse("Redelivery flag should not be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(1).isRedelivered());
        assertFalse("Redelivery flag should not be set", queueEntries.get(2).isRedelivered());

        /* Now release the third and first message only, causing it to be requeued */

        queueEntries.get(2).release();
        queueEntries.get(0).release();

        Thread.sleep(150); // Work done by SubFlushRunner/QueueRunner Threads

        assertEquals("Unexpected total number of messages sent to consumer",
                     5,
                     _consumerTarget.getMessages().size());
        assertTrue("Redelivery flag should now be set", queueEntries.get(0).isRedelivered());
        assertFalse("Redelivery flag should remain be unset", queueEntries.get(1).isRedelivered());
        assertTrue("Redelivery flag should now be set",queueEntries.get(2).isRedelivered());
        assertNull("releasedEntry should be cleared after requeue processed",
                   _consumer.getQueueContext().getReleasedEntry());
    }


    /**
     * Tests that a release requeues an entry for a queue with multiple consumers.  Verifies that a
     * requeue resends a message to a <i>single</i> subscriber.
     */
    public void testReleaseForQueueWithMultipleConsumers() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));
        ServerMessage messageB = createMessage(new Long(25));

        MockConsumer target1 = new MockConsumer();
        MockConsumer target2 = new MockConsumer();


        QueueConsumer consumer1 = _queue.addConsumer(target1, null, messageA.getClass(), "test",
                                                         EnumSet.of(Consumer.Option.ACQUIRES,
                                                                    Consumer.Option.SEES_REQUEUES));

        QueueConsumer consumer2 = _queue.addConsumer(target2, null, messageA.getClass(), "test",
                                                         EnumSet.of(Consumer.Option.ACQUIRES,
                                                                    Consumer.Option.SEES_REQUEUES));


        final ArrayList<QueueEntry> queueEntries = new ArrayList<QueueEntry>();
        EntryListAddingAction postEnqueueAction = new EntryListAddingAction(queueEntries);

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

        assertEquals("Unexpected total number of messages sent to both consumers after release",
                     3,
                     target1.getMessages().size() + target2.getMessages().size());
        assertNull("releasedEntry should be cleared after requeue processed",
                   consumer1.getQueueContext().getReleasedEntry());
        assertNull("releasedEntry should be cleared after requeue processed",
                   consumer2.getQueueContext().getReleasedEntry());
    }

    public void testExclusiveConsumer() throws Exception
    {
        ServerMessage messageA = createMessage(new Long(24));
        // Check adding an exclusive consumer adds it to the queue

        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                           EnumSet.of(Consumer.Option.EXCLUSIVE, Consumer.Option.ACQUIRES,
                                                      Consumer.Option.SEES_REQUEUES));

        assertEquals("Queue does not have consumer", 1,
                     _queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1,
                     _queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        _queue.enqueue(messageA, null);
        try
        {
            Thread.sleep(2000L);
        }
        catch (InterruptedException e)
        {
        }
        assertEquals(messageA, _consumer.getQueueContext().getLastSeenEntry().getMessage());

        // Check we cannot add a second subscriber to the queue
        MockConsumer subB = new MockConsumer();
        Exception ex = null;
        try
        {

            _queue.addConsumer(subB, null, messageA.getClass(), "test",
                               EnumSet.of(Consumer.Option.ACQUIRES,
                                          Consumer.Option.SEES_REQUEUES));

        }
        catch (MessageSource.ExistingExclusiveConsumer e)
        {
           ex = e;
        }
        assertNotNull(ex);

        // Check we cannot add an exclusive subscriber to a queue with an
        // existing consumer
        _consumer.close();
        _consumer = _queue.addConsumer(_consumerTarget, null, messageA.getClass(), "test",
                                       EnumSet.of(Consumer.Option.ACQUIRES,
                                                  Consumer.Option.SEES_REQUEUES));

        try
        {

            _consumer = _queue.addConsumer(subB, null, messageA.getClass(), "test",
                                               EnumSet.of(Consumer.Option.EXCLUSIVE));

        }
        catch (MessageSource.ExistingConsumerPreventsExclusive e)
        {
           ex = e;
        }
        assertNotNull(ex);
    }


    public void testResend() throws Exception
    {
        Long id = new Long(26);
        ServerMessage message = createMessage(id);

        _consumer = _queue.addConsumer(_consumerTarget, null, message.getClass(), "test",
                                           EnumSet.of(Consumer.Option.ACQUIRES, Consumer.Option.SEES_REQUEUES));

        _queue.enqueue(message, new Action<MessageInstance<?,? extends Consumer>>()
        {
            @Override
            public void performAction(final MessageInstance<?,? extends Consumer> object)
            {
                QueueEntryImpl entry = (QueueEntryImpl) object;
                entry.setRedelivered();
                _consumer.resend(entry);

            }
        });



    }

    public void testGetFirstMessageId() throws Exception
    {
        // Create message
        Long messageId = new Long(23);
        ServerMessage message = createMessage(messageId);

        // Put message on queue
        _queue.enqueue(message, null);
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
            _queue.enqueue(message, null);
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
            _queue.enqueue(message, null);
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
            _queue.enqueue(message, null);
        }

        // Get non-existent 0th QueueEntry & check returned list was empty
        // (the position parameters in this method are indexed from 1)
        List<E> entries = _queue.getMessagesRangeOnTheQueue(0, 0);
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
     * consumers which could not be delivered immediately during the
     * enqueue() operation.
     *
     * A defect within the method would mean that delivery of these messages may
     * not occur should the Runner stop before all messages have been processed.
     * Such a defect was discovered when Selectors were used such that one and
     * only one consumer can/will accept any given messages, but multiple
     * consumers are present, and one of the earlier consumers receives
     * more messages than the others.
     *
     * This test is to validate that the processQueue() method is able to
     * correctly deliver all of the messages present for asynchronous delivery
     * to consumers in such a scenario.
     */
    public void testProcessQueueWithUniqueSelectors() throws Exception
    {
        AbstractQueue testQueue = createNonAsyncDeliverQueue();

        // retrieve the QueueEntryList the queue creates and insert the test
        // messages, thus avoiding straight-through delivery attempts during
        //enqueue() process.
        QueueEntryList list = testQueue.getEntries();
        assertNotNull("QueueEntryList should have been created", list);

        QueueEntry msg1 = list.add(createMessage(1L));
        QueueEntry msg2 = list.add(createMessage(2L));
        QueueEntry msg3 = list.add(createMessage(3L));
        QueueEntry msg4 = list.add(createMessage(4L));
        QueueEntry msg5 = list.add(createMessage(5L));

        // Create lists of the entries each consumer should be interested
        // in.Bias over 50% of the messages to the first consumer so that
        // the later consumers reject them and report being done before
        // the first consumer as the processQueue method proceeds.
        List<String> msgListSub1 = createEntriesList(msg1, msg2, msg3);
        List<String> msgListSub2 = createEntriesList(msg4);
        List<String> msgListSub3 = createEntriesList(msg5);

        MockConsumer sub1 = new MockConsumer(msgListSub1);
        MockConsumer sub2 = new MockConsumer(msgListSub2);
        MockConsumer sub3 = new MockConsumer(msgListSub3);

        // register the consumers
        testQueue.addConsumer(sub1, sub1.getFilters(), msg1.getMessage().getClass(), "test",
                              EnumSet.of(Consumer.Option.ACQUIRES, Consumer.Option.SEES_REQUEUES));
        testQueue.addConsumer(sub2, sub2.getFilters(), msg1.getMessage().getClass(), "test",
                              EnumSet.of(Consumer.Option.ACQUIRES, Consumer.Option.SEES_REQUEUES));
        testQueue.addConsumer(sub3, sub3.getFilters(), msg1.getMessage().getClass(), "test",
                              EnumSet.of(Consumer.Option.ACQUIRES, Consumer.Option.SEES_REQUEUES));

        //check that no messages have been delivered to the
        //consumers during registration
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
        verifyReceivedMessages(Arrays.asList((MessageInstance)msg1,msg2,msg3), sub1.getMessages());
        verifyReceivedMessages(Collections.singletonList((MessageInstance)msg4), sub2.getMessages());
        verifyReceivedMessages(Collections.singletonList((MessageInstance)msg5), sub3.getMessages());
    }

    private AbstractQueue createNonAsyncDeliverQueue()
    {
        TestSimpleQueueEntryListFactory factory = new TestSimpleQueueEntryListFactory();
        return new NonAsyncDeliverQueue(factory, getVirtualHost());
    }

    /**
     * Tests that dequeued message is not present in the list returned form
     * {@link AbstractQueue#getMessagesOnTheQueue()}
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
        List<E> entries = _queue.getMessagesOnTheQueue();

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
     * {@link AbstractQueue#getMessagesOnTheQueue(QueueEntryFilter)}
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
        List<E> entries = _queue.getMessagesOnTheQueue(new QueueEntryFilter<E>()
        {
            public boolean accept(E entry)
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
     * Tests that all messages including dequeued one are deleted from the queue
     * on invocation of {@link AbstractQueue#clearQueue()}
     */
    public void testClearQueueWithDequeuedEntry() throws Exception
    {
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // put messages into a test queue
        enqueueGivenNumberOfMessages(_queue, messageNumber);

        // dequeue message on a test queue
        dequeueMessage(_queue, dequeueMessageIndex);

        // clean queue
        _queue.clearQueue();

        // get queue entries
        List<E> entries = _queue.getMessagesOnTheQueue();

        // assert queue entries
        assertNotNull(entries);
        assertEquals(0, entries.size());
    }


    public void testNotificationFiredOnEnqueue() throws Exception
    {
        AMQQueue.NotificationListener listener = mock(AMQQueue.NotificationListener.class);

        _queue.setNotificationListener(listener);
        _queue.setMaximumMessageCount(2);

        _queue.enqueue(createMessage(new Long(24)), null);
        verifyZeroInteractions(listener);

        _queue.enqueue(createMessage(new Long(25)), null);

        verify(listener, atLeastOnce()).notifyClients(eq(NotificationCheck.MESSAGE_COUNT_ALERT), eq(_queue), contains("Maximum count on queue threshold"));
    }

    public void testNotificationFiredAsync() throws Exception
    {
        AMQQueue.NotificationListener listener = mock(AMQQueue.NotificationListener.class);

        _queue.enqueue(createMessage(new Long(24)), null);
        _queue.enqueue(createMessage(new Long(25)), null);
        _queue.enqueue(createMessage(new Long(26)), null);

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
    protected List<E> enqueueGivenNumberOfMessages(Q queue, int messageNumber)
    {
        putGivenNumberOfMessages(queue, messageNumber);

        // make sure that all enqueued messages are on the queue
        List<E> entries = queue.getMessagesOnTheQueue();
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
    protected <T extends AbstractQueue> void putGivenNumberOfMessages(T queue, int messageNumber)
    {
        for (int i = 0; i < messageNumber; i++)
        {
            // Create message
            ServerMessage message = null;
            message = createMessage((long)i);

            // Put message on queue
            queue.enqueue(message,null);

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
    protected QueueEntry dequeueMessage(AMQQueue queue, int dequeueMessageIndex)
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

    protected void verifyReceivedMessages(List<MessageInstance> expected,
                                        List<MessageInstance> delivered)
    {
        assertEquals("Consumer did not receive the expected number of messages",
                    expected.size(), delivered.size());

        for (MessageInstance msg : expected)
        {
            assertTrue("Consumer did not receive msg: "
                    + msg.getMessage().getMessageNumber(), delivered.contains(msg));
        }
    }

    public Q getQueue()
    {
        return _queue;
    }

    protected void setQueue(Q queue)
    {
        _queue = queue;
    }

    public MockConsumer getConsumer()
    {
        return _consumerTarget;
    }

    public Map<String,Object> getArguments()
    {
        return _arguments;
    }

    public void setArguments(Map<String,Object> arguments)
    {
        _arguments = arguments;
    }


    protected ServerMessage createMessage(Long id)
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

    private static class EntryListAddingAction implements Action<MessageInstance<?,? extends Consumer>>
    {
        private final ArrayList<QueueEntry> _queueEntries;

        public EntryListAddingAction(final ArrayList<QueueEntry> queueEntries)
        {
            _queueEntries = queueEntries;
        }

        public void performAction(MessageInstance<?,? extends Consumer> entry)
        {
            _queueEntries.add((QueueEntry) entry);
        }
    }


    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public String getQname()
    {
        return _qname;
    }

    public String getOwner()
    {
        return _owner;
    }

    public String getRoutingKey()
    {
        return _routingKey;
    }

    public DirectExchange getExchange()
    {
        return _exchange;
    }

    public MockConsumer getConsumerTarget()
    {
        return _consumerTarget;
    }


    static class TestSimpleQueueEntryListFactory implements QueueEntryListFactory<NonAsyncDeliverEntry, NonAsyncDeliverQueue, NonAsyncDeliverList>
    {

        @Override
        public NonAsyncDeliverList createQueueEntryList(final NonAsyncDeliverQueue queue)
        {
            return new NonAsyncDeliverList(queue);
        }
    }

    private static class NonAsyncDeliverEntry extends OrderedQueueEntry<NonAsyncDeliverEntry, NonAsyncDeliverQueue, NonAsyncDeliverList>
    {

        public NonAsyncDeliverEntry(final NonAsyncDeliverList queueEntryList)
        {
            super(queueEntryList);
        }

        public NonAsyncDeliverEntry(final NonAsyncDeliverList queueEntryList,
                                    final ServerMessage message,
                                    final long entryId)
        {
            super(queueEntryList, message, entryId);
        }

        public NonAsyncDeliverEntry(final NonAsyncDeliverList queueEntryList, final ServerMessage message)
        {
            super(queueEntryList, message);
        }
    }

    private static class NonAsyncDeliverList extends OrderedQueueEntryList<NonAsyncDeliverEntry, NonAsyncDeliverQueue, NonAsyncDeliverList>
    {

        private static final HeadCreator<NonAsyncDeliverEntry, NonAsyncDeliverQueue, NonAsyncDeliverList> HEAD_CREATOR =
                new HeadCreator<NonAsyncDeliverEntry, NonAsyncDeliverQueue, NonAsyncDeliverList>()
                {

                    @Override
                    public NonAsyncDeliverEntry createHead(final NonAsyncDeliverList list)
                    {
                        return new NonAsyncDeliverEntry(list);
                    }
                };

        public NonAsyncDeliverList(final NonAsyncDeliverQueue queue)
        {
            super(queue, HEAD_CREATOR);
        }

        @Override
        protected NonAsyncDeliverEntry createQueueEntry(final ServerMessage<?> message)
        {
            return new NonAsyncDeliverEntry(this,message);
        }
    }


    private static class NonAsyncDeliverQueue extends AbstractQueue<NonAsyncDeliverEntry, NonAsyncDeliverQueue, NonAsyncDeliverList>
    {
        public NonAsyncDeliverQueue(final TestSimpleQueueEntryListFactory factory, VirtualHost vhost)
        {
            super(vhost, attributes(), factory);
        }

        private static Map<String,Object> attributes()
        {
            Map<String,Object> attributes = new HashMap<String, Object>();
            attributes.put(Queue.ID, UUID.randomUUID());
            attributes.put(Queue.NAME, "test");
            attributes.put(Queue.DURABLE, false);
            attributes.put(Queue.LIFETIME_POLICY, LifetimePolicy.PERMANENT);
            return attributes;
        }

        @Override
        public void deliverAsync(QueueConsumer sub)
        {
            // do nothing, i.e prevent deliveries by the SubFlushRunner
            // when registering the new consumers
        }
    }
}
