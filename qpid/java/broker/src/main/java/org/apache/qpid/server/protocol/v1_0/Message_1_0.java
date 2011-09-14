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


import org.apache.qpid.server.configuration.SessionConfig;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.MessageMetaData_1_0;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.StoredMessage;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

public class Message_1_0 implements ServerMessage<Message_1_0>, InboundMessage
{
    private final StoredMessage<MessageMetaData_1_0> _storedMessage;
    private List<ByteBuffer> _fragments;
    private WeakReference<Session_1_0> _session;


    public Message_1_0(final StoredMessage<MessageMetaData_1_0> storedMessage,
                       final List<ByteBuffer> fragments,
                       final Session_1_0 session)
    {
        _storedMessage = storedMessage;
        _fragments = fragments;
        _session = new WeakReference<Session_1_0>(session);
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
        return _storedMessage.getMetaData();
    }

    public MessageMetaData_1_0.MessageHeader_1_0 getMessageHeader()
    {
        return getMessageMetaData().getMessageHeader();
    }

    public boolean isPersistent()
    {
        return getMessageMetaData().isPersistent();
    }

    public boolean isRedelivered()
    {
        // TODO
        return false;
    }

    public long getSize()
    {
        // TODO
        return 0l;
    }

    public boolean isImmediate()
    {
        return false;
    }

    public long getExpiration()
    {
        return getMessageHeader().getExpiration();
    }

    public MessageReference<Message_1_0> newReference()
    {
        return new Reference(this);
    }

    public Long getMessageNumber()
    {
        return _storedMessage.getMessageNumber();
    }

    public long getArrivalTime()
    {
        return 0;  //TODO
    }

    public int getContent(final ByteBuffer buf, final int offset)
    {
        return _storedMessage.getContent(offset, buf);
    }

    public SessionConfig getSessionConfig()
    {
        return null;  //TODO
    }

    public List<ByteBuffer> getFragments()
    {
        return _fragments;
    }

    public Session_1_0 getSession()
    {
        return _session.get();
    }

    public static class Reference extends MessageReference<Message_1_0>
    {
        public Reference(Message_1_0 message)
        {
            super(message);
        }

        protected void onReference(Message_1_0 message)
        {

        }

        protected void onRelease(Message_1_0 message)
        {

        }

}
}
