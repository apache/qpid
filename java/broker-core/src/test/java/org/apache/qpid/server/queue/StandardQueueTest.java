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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.consumer.MockConsumer;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class StandardQueueTest extends AbstractQueueTestBase
{

    public void testAutoDeleteQueue() throws Exception
    {
        getQueue().close();
        getQueue().deleteAndReturnCount();
        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, getQname());
        queueAttributes.put(Queue.LIFETIME_POLICY, LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS);
        final StandardQueueImpl queue = new StandardQueueImpl(queueAttributes, getVirtualHost());
        queue.open();
        setQueue(queue);

        ServerMessage message = createMessage(25l);
        QueueConsumer consumer =
                (QueueConsumer) getQueue().addConsumer(getConsumerTarget(), null, message.getClass(), "test",
                                       EnumSet.of(ConsumerImpl.Option.ACQUIRES,
                                                  ConsumerImpl.Option.SEES_REQUEUES));

        getQueue().enqueue(message, null);
        consumer.close();
        assertTrue("Queue was not deleted when consumer was removed",
                   getQueue().isDeleted());
    }

    public void testActiveConsumerCount() throws Exception
    {

        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, "testActiveConsumerCount");
        queueAttributes.put(Queue.OWNER, "testOwner");
        final StandardQueueImpl queue = new StandardQueueImpl(queueAttributes, getVirtualHost());
        queue.open();
        //verify adding an active consumer increases the count
        final MockConsumer consumer1 = new MockConsumer();
        consumer1.setActive(true);
        consumer1.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 0, queue.getConsumerCountWithCredit());
        queue.addConsumer(consumer1,
                          null,
                          createMessage(-1l).getClass(),
                          "test",
                          EnumSet.of(ConsumerImpl.Option.ACQUIRES,
                                     ConsumerImpl.Option.SEES_REQUEUES));
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());

        //verify adding an inactive consumer doesn't increase the count
        final MockConsumer consumer2 = new MockConsumer();
        consumer2.setActive(false);
        consumer2.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());
        queue.addConsumer(consumer2,
                          null,
                          createMessage(-1l).getClass(),
                          "test",
                          EnumSet.of(ConsumerImpl.Option.ACQUIRES,
                                     ConsumerImpl.Option.SEES_REQUEUES));
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());

        //verify behaviour in face of expected state changes:

        //verify a consumer going suspended->active increases the count
        consumer2.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 2, queue.getConsumerCountWithCredit());

        //verify a consumer going active->suspended decreases the count
        consumer2.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());

        //verify a consumer going suspended->closed doesn't change the count
        consumer2.setState(ConsumerTarget.State.CLOSED);
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());

        //verify a consumer going active->active doesn't change the count
        consumer1.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());

        consumer1.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 0, queue.getConsumerCountWithCredit());

        //verify a consumer going suspended->suspended doesn't change the count
        consumer1.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 0, queue.getConsumerCountWithCredit());

        consumer1.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 1, queue.getConsumerCountWithCredit());

        //verify a consumer going active->closed  decreases the count
        consumer1.setState(ConsumerTarget.State.CLOSED);
        assertEquals("Unexpected active consumer count", 0, queue.getConsumerCountWithCredit());

    }


    /**
     * Tests that entry in dequeued state are not enqueued and not delivered to consumer
     */
    public void testEnqueueDequeuedEntry() throws Exception
    {
        // create a queue where each even entry is considered a dequeued
        AbstractQueue queue = new DequeuedQueue(getVirtualHost());
        queue.create();
        // create a consumer
        MockConsumer consumer = new MockConsumer();

        // register consumer
        queue.addConsumer(consumer,
                          null,
                          createMessage(-1l).getClass(),
                          "test",
                          EnumSet.of(ConsumerImpl.Option.ACQUIRES,
                                     ConsumerImpl.Option.SEES_REQUEUES));

        // put test messages into a queue
        putGivenNumberOfMessages(queue, 4);

        // assert received messages
        List<MessageInstance> messages = consumer.getMessages();
        assertEquals("Only 2 messages should be returned", 2, messages.size());
        assertEquals("ID of first message should be 1", 1l,
                     (messages.get(0).getMessage()).getMessageNumber());
        assertEquals("ID of second message should be 3", 3l,
                     (messages.get(1).getMessage()).getMessageNumber());
    }

    /**
     * Tests whether dequeued entry is sent to subscriber in result of
     * invocation of {@link AbstractQueue#processQueue(QueueRunner)}
     */
    public void testProcessQueueWithDequeuedEntry() throws Exception
    {
        // total number of messages to send
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, "test");
        // create queue with overridden method deliverAsync
        StandardQueueImpl testQueue = new StandardQueueImpl(queueAttributes, getVirtualHost())
        {
            @Override
            public void deliverAsync(QueueConsumer sub)
            {
                // do nothing
            }
        };
        testQueue.create();

        // put messages
        List<StandardQueueEntry> entries =
                (List<StandardQueueEntry>) enqueueGivenNumberOfMessages(testQueue, messageNumber);

        // dequeue message
        dequeueMessage(testQueue, dequeueMessageIndex);

        // latch to wait for message receipt
        final CountDownLatch latch = new CountDownLatch(messageNumber -1);

        // create a consumer
        MockConsumer consumer = new MockConsumer()
        {
            /**
             * Send a message and decrement latch
             * @param entry
             * @param batch
             */
            public long send(MessageInstance entry, boolean batch)
            {
                long size = super.send(entry, batch);
                latch.countDown();
                return size;
            }
        };

        // subscribe
        testQueue.addConsumer(consumer,
                              null,
                              entries.get(0).getMessage().getClass(),
                              "test",
                              EnumSet.of(ConsumerImpl.Option.ACQUIRES,
                                         ConsumerImpl.Option.SEES_REQUEUES));

        // process queue
        testQueue.processQueue(new QueueRunner(testQueue)
        {
            public void run()
            {
                // do nothing
            }
        });

        // wait up to 1 minute for message receipt
        try
        {
            latch.await(1, TimeUnit.MINUTES);
        }
        catch (InterruptedException e1)
        {
            Thread.currentThread().interrupt();
        }
        List<MessageInstance> expected = Arrays.asList((MessageInstance) entries.get(0), entries.get(2), entries.get(3));
        verifyReceivedMessages(expected, consumer.getMessages());
    }


    private static class DequeuedQueue extends AbstractQueue
    {

        private QueueEntryList _entries = new DequeuedQueueEntryList(this);

        public DequeuedQueue(VirtualHostImpl virtualHost)
        {
            super(attributes(), virtualHost);
        }

        @Override
        QueueEntryList getEntries()
        {
            return _entries;
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
    }

    private static class DequeuedQueueEntryList extends OrderedQueueEntryList
    {
        private static final HeadCreator HEAD_CREATOR =
                new HeadCreator()
                {

                    @Override
                    public DequeuedQueueEntry createHead(final QueueEntryList list)
                    {
                        return new DequeuedQueueEntry((DequeuedQueueEntryList) list);
                    }
                };

        public DequeuedQueueEntryList(final DequeuedQueue queue)
        {
            super(queue, HEAD_CREATOR);
        }

        /**
         * Entries with even message id are considered
         * dequeued!
         */
        protected DequeuedQueueEntry createQueueEntry(final ServerMessage message)
        {
            return new DequeuedQueueEntry(this, message);
        }


    }

    private static class DequeuedQueueEntry extends OrderedQueueEntry
    {

        private final ServerMessage _message;

        private DequeuedQueueEntry(final DequeuedQueueEntryList queueEntryList)
        {
            super(queueEntryList);
            _message = null;
        }

        public DequeuedQueueEntry(DequeuedQueueEntryList list, final ServerMessage message)
        {
            super(list, message);
            _message = message;
        }

        public boolean isDeleted()
        {
            return (_message.getMessageNumber() % 2 == 0);
        }

        public boolean isAvailable()
        {
            return !(_message.getMessageNumber() % 2 == 0);
        }

        @Override
        public boolean acquire(ConsumerImpl sub)
        {
            if(_message.getMessageNumber() % 2 == 0)
            {
                return false;
            }
            else
            {
                return super.acquire(sub);
            }
        }

        @Override
        public boolean lockAcquisition()
        {
            return true;
        }

        @Override
        public boolean unlockAcquisition()
        {
            return true;
        }
    }
}
