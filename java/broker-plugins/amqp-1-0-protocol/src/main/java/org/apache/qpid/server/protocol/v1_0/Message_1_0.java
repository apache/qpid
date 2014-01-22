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
package org.apache.qpid.server.protocol.v1_0;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.qpid.server.message.AbstractServerMessageImpl;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.store.StoredMessage;

public class Message_1_0 extends AbstractServerMessageImpl<Message_1_0, MessageMetaData_1_0> implements InboundMessage
{

    private List<ByteBuffer> _fragments;
    private long _arrivalTime;


    public Message_1_0(final StoredMessage<MessageMetaData_1_0> storedMessage)
    {
        super(storedMessage, null);
        _fragments = restoreFragments(storedMessage);
    }

    private static List<ByteBuffer> restoreFragments(StoredMessage<MessageMetaData_1_0> storedMessage)
    {
        ArrayList<ByteBuffer> fragments = new ArrayList<ByteBuffer>();
        final int FRAGMENT_SIZE = 2048;
        int offset = 0;
        ByteBuffer b;
        do
        {

            b = storedMessage.getContent(offset,FRAGMENT_SIZE);
            if(b.hasRemaining())
            {
                fragments.add(b);
                offset+= b.remaining();
            }
        }
        while(b.hasRemaining());
        return fragments;
    }

    public Message_1_0(final StoredMessage<MessageMetaData_1_0> storedMessage,
                       final List<ByteBuffer> fragments,
                       final Object connectionReference)
    {
        super(storedMessage, connectionReference);
        _fragments = fragments;
        _arrivalTime = System.currentTimeMillis();
    }

    public String getRoutingKey()
    {
        Object routingKey = getMessageHeader().getHeader("routing-key");
        if(routingKey != null)
        {
            return routingKey.toString();
        }
        else
        {
            return getMessageHeader().getSubject();
        }
    }

    private MessageMetaData_1_0 getMessageMetaData()
    {
        return getStoredMessage().getMetaData();
    }

    public MessageMetaData_1_0.MessageHeader_1_0 getMessageHeader()
    {
        return getMessageMetaData().getMessageHeader();
    }

    public boolean isRedelivered()
    {
        // TODO
        return false;
    }

    public long getSize()
    {
        long size = 0l;
        if(_fragments != null)
        {
            for(ByteBuffer buf : _fragments)
            {
                size += buf.remaining();
            }
        }

        return size;
    }

    public long getExpiration()
    {
        return getMessageHeader().getExpiration();
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

    public List<ByteBuffer> getFragments()
    {
        return _fragments;
    }

}
