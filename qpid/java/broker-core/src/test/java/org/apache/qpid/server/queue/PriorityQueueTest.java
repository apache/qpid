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

import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;

import junit.framework.AssertionFailedError;

import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;

public class PriorityQueueTest extends AbstractQueueTestBase
{

    @Override
    public void setUp() throws Exception
    {
        setArguments(Collections.singletonMap(PriorityQueue.PRIORITIES,(Object)3));
        super.setUp();
    }

    public void testPriorityOrdering() throws Exception, InterruptedException
    {

        // Enqueue messages in order
        AbstractQueue queue = (AbstractQueue) getQueue();
        queue.enqueue(createMessage(1L, (byte) 10), null, null);
        queue.enqueue(createMessage(2L, (byte) 4), null, null);
        queue.enqueue(createMessage(3L, (byte) 0), null, null);

        // Enqueue messages in reverse order
        queue.enqueue(createMessage(4L, (byte) 0), null, null);
        queue.enqueue(createMessage(5L, (byte) 4), null, null);
        queue.enqueue(createMessage(6L, (byte) 10), null, null);

        // Enqueue messages out of order
        queue.enqueue(createMessage(7L, (byte) 4), null, null);
        queue.enqueue(createMessage(8L, (byte) 10), null, null);
        queue.enqueue(createMessage(9L, (byte) 0), null, null);

        // Register subscriber
        queue.addConsumer(getConsumer(), null, null, "test", EnumSet.noneOf(ConsumerImpl.Option.class));
        Thread.sleep(150);

        ArrayList<MessageInstance> msgs = getConsumer().getMessages();
        try
        {
            assertEquals(1L, msgs.get(0).getMessage().getMessageNumber());
            assertEquals(6L, msgs.get(1).getMessage().getMessageNumber());
            assertEquals(8L, msgs.get(2).getMessage().getMessageNumber());

            assertEquals(2L, msgs.get(3).getMessage().getMessageNumber());
            assertEquals(5L, msgs.get(4).getMessage().getMessageNumber());
            assertEquals(7L, msgs.get(5).getMessage().getMessageNumber());

            assertEquals(3L, msgs.get(6).getMessage().getMessageNumber());
            assertEquals(4L, msgs.get(7).getMessage().getMessageNumber());
            assertEquals(9L, msgs.get(8).getMessage().getMessageNumber());
        }
        catch (AssertionFailedError afe)
        {
            // Show message order on failure.
            int index = 1;
            for (MessageInstance qe : msgs)
            {
                System.err.println(index + ":" + qe.getMessage().getMessageNumber());
                index++;
            }

            throw afe;
        }

    }

    protected ServerMessage createMessage(Long id, byte i)
    {

        ServerMessage msg = super.createMessage(id);
        AMQMessageHeader hdr = msg.getMessageHeader();
        when(hdr.getPriority()).thenReturn(i);
        return msg;
    }

    protected ServerMessage createMessage(Long id)
    {
        return createMessage(id, (byte) 0);
    }

}
