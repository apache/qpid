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
package org.apache.qpid.server;

import java.util.ArrayList;
import java.util.Set;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.InboundMessage;
import org.apache.qpid.server.queue.QueueEntry;

public class InboundMessageAdapter implements InboundMessage
{
    private AMQMessage _message;
    private ArrayList<AMQQueue> _queues;

    public InboundMessageAdapter(AMQMessage msg)
    {
        _message = msg;
    }

    public Long getMessageId()
    {
        return _message.getMessageId();
    }

    public boolean isPersistent()
    {
        return _message.isPersistent();
    }

    public boolean isRedelivered()
    {
        return _message.isRedelivered();
    }

    public AMQShortString getRoutingKey() throws AMQException
    {
        return _message.getRoutingKey();
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        try
        {
            return _message.getContentHeaderBody();
        }
        catch (AMQException e)
        {
            throw new RuntimeException("Error retrieving ContentHeaderBody: " + e, e);
        }
    }

    public void enqueue(ArrayList<AMQQueue> queues)
    {
        _queues = queues;
    }
    
    public ArrayList<AMQQueue> getEnqueuedList()
    {
        return _queues;
    }
}
