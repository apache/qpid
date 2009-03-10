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

import junit.framework.AssertionFailedError;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.txn.NonTransactionalContext;

import java.util.ArrayList;

public class AMQPriorityQueueTest extends SimpleAMQQueueTest
{
    private static final int PRIORITIES = 3;

    @Override
    protected void setUp() throws Exception
    {        
        _arguments = new FieldTable();
        _arguments.put(AMQQueueFactory.X_QPID_PRIORITIES, PRIORITIES);
        super.setUp();
    }

    public void testPriorityOrdering() throws AMQException, InterruptedException
    {

        // Enqueue messages in order
        AMQMessage message = createMessage((byte) 10);
        Long messagIDOffset = message.getMessageId() - 1;
        _queue.enqueue(null, message);
        _queue.enqueue(null, createMessage((byte) 4));
        _queue.enqueue(null, createMessage((byte) 0));

        // Enqueue messages in reverse order
        _queue.enqueue(null, createMessage((byte) 0));
        _queue.enqueue(null, createMessage((byte) 4));
        _queue.enqueue(null, createMessage((byte) 10));

        // Enqueue messages out of order
        _queue.enqueue(null, createMessage((byte) 4));
        _queue.enqueue(null, createMessage((byte) 10));
        _queue.enqueue(null, createMessage((byte) 0));

        // Register subscriber
        _queue.registerSubscription(_subscription, false);
        Thread.sleep(150);

        ArrayList<QueueEntry> msgs = _subscription.getQueueEntries();
        try
        {
            assertEquals(new Long(1 + messagIDOffset), msgs.get(0).getMessageId());
            assertEquals(new Long(6 + messagIDOffset), msgs.get(1).getMessageId());
            assertEquals(new Long(8 + messagIDOffset), msgs.get(2).getMessageId());

            assertEquals(new Long(2 + messagIDOffset), msgs.get(3).getMessageId());
            assertEquals(new Long(5 + messagIDOffset), msgs.get(4).getMessageId());
            assertEquals(new Long(7 + messagIDOffset), msgs.get(5).getMessageId());

            assertEquals(new Long(3 + messagIDOffset), msgs.get(6).getMessageId());
            assertEquals(new Long(4 + messagIDOffset), msgs.get(7).getMessageId());
            assertEquals(new Long(9 + messagIDOffset), msgs.get(8).getMessageId());
        }
        catch (AssertionFailedError afe)
        {
            // Show message order on failure.
            int index = 1;
            for (QueueEntry qe : msgs)
            {
                index++;
            }

            throw afe;
        }

    }

    protected AMQMessage createMessage(byte i) throws AMQException
    {
        AMQMessage message = super.createMessage();

        ((BasicContentHeaderProperties) message.getContentHeaderBody().properties).setPriority(i);

        return message;
    }


    public void testMessagesFlowToDiskWithPriority() throws AMQException, InterruptedException
    {
        int PRIORITIES = 1;
        FieldTable arguments = new FieldTable();
        arguments.put(AMQQueueFactory.X_QPID_PRIORITIES, PRIORITIES);

        // Create IncomingMessage and nondurable queue
        NonTransactionalContext txnContext = new NonTransactionalContext(_transactionLog, null, null, null);

        //Create a priorityQueue
        _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(new AMQShortString("testMessagesFlowToDiskWithPriority"), false, _owner, false, _virtualHost, arguments);

        MESSAGE_SIZE = 1;
        long MEMORY_MAX = PRIORITIES * 2;
        int MESSAGE_COUNT = (int) MEMORY_MAX * 2;
        //Set the Memory Usage to be very low
        _queue.setMemoryUsageMaximum(MEMORY_MAX);

        for (int msgCount = 0; msgCount < MESSAGE_COUNT / 2; msgCount++)
        {
            sendMessage(txnContext, (msgCount % 10));
        }

        //Check that we can hold 10 messages without flowing
        assertEquals(MESSAGE_COUNT / 2, _queue.getMessageCount());
        assertEquals(MEMORY_MAX, _queue.getMemoryUsageMaximum());        
        assertEquals(_queue.getMemoryUsageMaximum(), _queue.getMemoryUsageCurrent());
        assertTrue("Queue is flowed.", !_queue.isFlowed());

        // Send another and ensure we are flowed
        sendMessage(txnContext, 9);

        //Give the Purging Thread a chance to run
        Thread.yield();
        Thread.sleep(500);

        assertTrue("Queue is not flowed.", _queue.isFlowed());
        assertEquals("Queue contains more messages than expected.", MESSAGE_COUNT / 2 + 1, _queue.getMessageCount());
        assertEquals("Queue over memory quota.",MESSAGE_COUNT / 2, _queue.getMemoryUsageCurrent());


        //send another batch of messagse so the total in each queue is equal
        for (int msgCount = 0; msgCount < (MESSAGE_COUNT / 2) ; msgCount++)
        {
            sendMessage(txnContext, (msgCount % 10));

            long usage = _queue.getMemoryUsageCurrent();
            assertTrue("Queue has gone over quota:" + usage,
                       usage <= _queue.getMemoryUsageMaximum());

            assertTrue("Queue has a negative quota:" + usage, usage > 0);

        }
        assertEquals(MESSAGE_COUNT + 1, _queue.getMessageCount());
        assertEquals(MEMORY_MAX, _queue.getMemoryUsageCurrent());
        assertTrue("Queue is not flowed.", _queue.isFlowed());

        _queue.registerSubscription(_subscription, false);

        int slept = 0;
        while (_subscription.getQueueEntries().size() != MESSAGE_COUNT + 1 && slept < 10)
        {
            Thread.yield();
            Thread.sleep(500);
            slept++;
        }

        //Ensure the messages are retreived
        assertEquals("Not all messages were received, slept:" + slept / 2 + "s", MESSAGE_COUNT + 1, _subscription.getQueueEntries().size());

        //Check the queue is still within it's limits.
        assertTrue("Queue has gone over quota:" + _queue.getMemoryUsageCurrent(),
                   _queue.getMemoryUsageCurrent() <= _queue.getMemoryUsageMaximum());

        assertTrue("Queue has a negative quota:" + _queue.getMemoryUsageCurrent(), _queue.getMemoryUsageCurrent() >= 0);

        for (int index = 0; index < MESSAGE_COUNT; index++)
        {
            // Ensure that we have received the messages and it wasn't flushed to disk before we received it.
            AMQMessage message = _subscription.getMessages().get(index);
            assertNotNull("Message:" + message.debugIdentity() + " was null.", message);
        }

    }

}
