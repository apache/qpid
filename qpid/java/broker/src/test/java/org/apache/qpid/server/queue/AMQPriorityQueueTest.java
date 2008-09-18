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

import java.util.ArrayList;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;

public class AMQPriorityQueueTest extends SimpleAMQQueueTest
{

    @Override
    protected void setUp() throws Exception
    {
        arguments = new FieldTable();
        arguments.put(AMQQueueFactory.X_QPID_PRIORITIES, 3);
        super.setUp();
    }

    public void testPriorityOrdering() throws AMQException, InterruptedException
    {

        // Enqueue messages in order
        queue.enqueue(null, createMessage(1L, (byte) 10));
        queue.enqueue(null, createMessage(2L, (byte) 4));
        queue.enqueue(null, createMessage(3L, (byte) 0));
        
        // Enqueue messages in reverse order
        queue.enqueue(null, createMessage(4L, (byte) 0));
        queue.enqueue(null, createMessage(5L, (byte) 4));
        queue.enqueue(null, createMessage(6L, (byte) 10));
        
        // Enqueue messages out of order
        queue.enqueue(null, createMessage(7L, (byte) 4));
        queue.enqueue(null, createMessage(8L, (byte) 10));
        queue.enqueue(null, createMessage(9L, (byte) 0));
        
        // Register subscriber
        queue.registerSubscription(subscription, false);
        Thread.sleep(150);
        
        ArrayList<QueueEntry> msgs = subscription.getMessages();
        assertEquals(new Long(1L), msgs.get(0).getMessage().getMessageId());
        assertEquals(new Long(6L), msgs.get(1).getMessage().getMessageId());
        assertEquals(new Long(8L), msgs.get(2).getMessage().getMessageId());

        assertEquals(new Long(2L), msgs.get(3).getMessage().getMessageId());
        assertEquals(new Long(5L), msgs.get(4).getMessage().getMessageId());
        assertEquals(new Long(7L), msgs.get(5).getMessage().getMessageId());
        
        assertEquals(new Long(3L), msgs.get(6).getMessage().getMessageId());
        assertEquals(new Long(4L), msgs.get(7).getMessage().getMessageId());
        assertEquals(new Long(9L), msgs.get(8).getMessage().getMessageId());
    }

    protected AMQMessage createMessage(Long id, byte i) throws AMQException
    {
        AMQMessage msg = super.createMessage(id);
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        props.setPriority(i);
        msg.getContentHeaderBody().properties = props;
        return msg;
    }
    
    protected AMQMessage createMessage(Long id) throws AMQException
    {
        return createMessage(id, (byte) 0);
    }
    
}
