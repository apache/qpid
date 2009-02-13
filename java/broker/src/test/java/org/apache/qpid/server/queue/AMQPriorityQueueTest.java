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
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;

import java.util.ArrayList;

public class AMQPriorityQueueTest extends SimpleAMQQueueTest
{
    private static final long MESSAGE_SIZE = 100L;

    @Override
    protected void setUp() throws Exception
    {
        _arguments = new FieldTable();
        _arguments.put(AMQQueueFactory.X_QPID_PRIORITIES, 3);
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

        ArrayList<QueueEntry> msgs = _subscription.getMessages();
        try
        {
            assertEquals(new Long(1 + messagIDOffset), msgs.get(0).getMessage().getMessageId());
            assertEquals(new Long(6 + messagIDOffset), msgs.get(1).getMessage().getMessageId());
            assertEquals(new Long(8 + messagIDOffset), msgs.get(2).getMessage().getMessageId());

            assertEquals(new Long(2 + messagIDOffset), msgs.get(3).getMessage().getMessageId());
            assertEquals(new Long(5 + messagIDOffset), msgs.get(4).getMessage().getMessageId());
            assertEquals(new Long(7 + messagIDOffset), msgs.get(5).getMessage().getMessageId());

            assertEquals(new Long(3 + messagIDOffset), msgs.get(6).getMessage().getMessageId());
            assertEquals(new Long(4 + messagIDOffset), msgs.get(7).getMessage().getMessageId());
            assertEquals(new Long(9 + messagIDOffset), msgs.get(8).getMessage().getMessageId());
        }
        catch (AssertionFailedError afe)
        {
            // Show message order on failure.
            int index = 1;
            for (QueueEntry qe : msgs)
            {
                System.err.println(index + ":" + qe.getMessage().getMessageId());
                index++;
            }

            throw afe;
        }

    }

    protected AMQMessage createMessage(byte i) throws AMQException
    {
        AMQMessage message = super.createMessage();
                
        ((BasicContentHeaderProperties)message.getContentHeaderBody().properties).setPriority(i);

        return message;
    }
}
