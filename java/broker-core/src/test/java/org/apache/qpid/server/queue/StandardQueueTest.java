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

import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.consumer.MockConsumer;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StandardQueueTest extends SimpleAMQQueueTestBase<StandardQueueEntry, StandardQueue, StandardQueueEntryList>
{

    public void testCreationFailsWithNoVhost()
    {
        try
        {
            setQueue(new StandardQueue(UUIDGenerator.generateRandomUUID(), getQname(), false, getOwner(), false,false, null, getArguments()));
            assertNull("Queue was created", getQueue());
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing vhost",
                       e.getMessage().contains("Host"));
        }
    }


    public void testAutoDeleteQueue() throws Exception
    {
        getQueue().stop();
        setQueue(new StandardQueue(UUIDGenerator.generateRandomUUID(), getQname(), false, null, true, false, getVirtualHost(), Collections.<String,Object>emptyMap()));
        getQueue().setDeleteOnNoConsumers(true);

        ServerMessage message = createMessage(25l);
        QueueConsumer consumer =
                getQueue().addConsumer(getConsumerTarget(), null, message.getClass(), "test",
                                       EnumSet.of(Consumer.Option.ACQUIRES,
                                                  Consumer.Option.SEES_REQUEUES));

        getQueue().enqueue(message, null);
        consumer.close();
        assertTrue("Queue was not deleted when consumer was removed",
                   getQueue().isDeleted());
    }

    public void testActiveConsumerCount() throws Exception
    {
        final StandardQueue queue = new StandardQueue(UUIDGenerator.generateRandomUUID(), "testActiveConsumerCount", false,
                                                        "testOwner", false, false, getVirtualHost(), null);

        //verify adding an active consumer increases the count
        final MockConsumer consumer1 = new MockConsumer();
        consumer1.setActive(true);
        consumer1.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());
        queue.addConsumer(consumer1,
                          null,
                          createMessage(-1l).getClass(),
                          "test",
                          EnumSet.of(Consumer.Option.ACQUIRES,
                                     Consumer.Option.SEES_REQUEUES));
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify adding an inactive consumer doesn't increase the count
        final MockConsumer consumer2 = new MockConsumer();
        consumer2.setActive(false);
        consumer2.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());
        queue.addConsumer(consumer2,
                          null,
                          createMessage(-1l).getClass(),
                          "test",
                          EnumSet.of(Consumer.Option.ACQUIRES,
                                     Consumer.Option.SEES_REQUEUES));
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify behaviour in face of expected state changes:

        //verify a consumer going suspended->active increases the count
        consumer2.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 2, queue.getActiveConsumerCount());

        //verify a consumer going active->suspended decreases the count
        consumer2.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify a consumer going suspended->closed doesn't change the count
        consumer2.setState(ConsumerTarget.State.CLOSED);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify a consumer going active->active doesn't change the count
        consumer1.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        consumer1.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());

        //verify a consumer going suspended->suspended doesn't change the count
        consumer1.setState(ConsumerTarget.State.SUSPENDED);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());

        consumer1.setState(ConsumerTarget.State.ACTIVE);
        assertEquals("Unexpected active consumer count", 1, queue.getActiveConsumerCount());

        //verify a consumer going active->closed  decreases the count
        consumer1.setState(ConsumerTarget.State.CLOSED);
        assertEquals("Unexpected active consumer count", 0, queue.getActiveConsumerCount());

    }


    /**
     * Tests that entry in dequeued state are not enqueued and not delivered to consumer
     */
    public void testEnqueueDequeuedEntry() throws Exception
    {
        // create a queue where each even entry is considered a dequeued
        SimpleAMQQueue queue = new DequeuedQueue(UUIDGenerator.generateRandomUUID(), "test", false,
                                                  "testOwner", false, false, getVirtualHost(), null);
        // create a consumer
        MockConsumer consumer = new MockConsumer();

        // register consumer
        queue.addConsumer(consumer,
                          null,
                          createMessage(-1l).getClass(),
                          "test",
                          EnumSet.of(Consumer.Option.ACQUIRES,
                                     Consumer.Option.SEES_REQUEUES));

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
     * invocation of {@link SimpleAMQQueue#processQueue(QueueRunner)}
     */
    public void testProcessQueueWithDequeuedEntry() throws Exception
    {
        // total number of messages to send
        int messageNumber = 4;
        int dequeueMessageIndex = 1;

        // create queue with overridden method deliverAsync
        StandardQueue testQueue = new StandardQueue(UUIDGenerator.generateRandomUUID(), "test",
                                                    false, "testOwner", false, false, getVirtualHost(), null)
        {
            @Override
            public void deliverAsync(QueueConsumer sub)
            {
                // do nothing
            }
        };

        // put messages
        List<StandardQueueEntry> entries = enqueueGivenNumberOfMessages(testQueue, messageNumber);

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
            public void send(MessageInstance entry, boolean batch)
            {
                super.send(entry, batch);
                latch.countDown();
            }
        };

        // subscribe
        testQueue.addConsumer(consumer,
                              null,
                              entries.get(0).getMessage().getClass(),
                              "test",
                              EnumSet.of(Consumer.Option.ACQUIRES,
                                         Consumer.Option.SEES_REQUEUES));

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


    private static class DequeuedQueue extends SimpleAMQQueue<DequeuedQueueEntry, DequeuedQueue, DequeuedQueueEntryList>
    {

        public DequeuedQueue(final UUID id,
                             final String queueName,
                             final boolean durable,
                             final String owner,
                             final boolean autoDelete,
                             final boolean exclusive,
                             final VirtualHost virtualHost,
                             final Map<String, Object> arguments)
        {
            super(id, queueName, durable, owner, autoDelete, exclusive, virtualHost, new DequeuedQueueEntryListFactory(), arguments);
        }
    }
    private static class DequeuedQueueEntryListFactory implements QueueEntryListFactory<DequeuedQueueEntry, DequeuedQueue, DequeuedQueueEntryList>
    {
        public DequeuedQueueEntryList createQueueEntryList(DequeuedQueue queue)
        {
            /**
             * Override SimpleQueueEntryList to create a dequeued
             * entries for messages with even id
             */
            return new DequeuedQueueEntryList(queue);
        }


    }

    private static class DequeuedQueueEntryList extends OrderedQueueEntryList<DequeuedQueueEntry, DequeuedQueue, DequeuedQueueEntryList>
    {
        private static final HeadCreator<DequeuedQueueEntry,DequeuedQueue,DequeuedQueueEntryList> HEAD_CREATOR =
                new HeadCreator<DequeuedQueueEntry,DequeuedQueue,DequeuedQueueEntryList>()
                {

                    @Override
                    public DequeuedQueueEntry createHead(final DequeuedQueueEntryList list)
                    {
                        return new DequeuedQueueEntry(list);
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

    private static class DequeuedQueueEntry extends OrderedQueueEntry<DequeuedQueueEntry,DequeuedQueue,DequeuedQueueEntryList>
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
        public boolean acquire(QueueConsumer<?,DequeuedQueueEntry,DequeuedQueue,DequeuedQueueEntryList> sub)
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
    }
}
