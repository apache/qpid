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


import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.store.StoredMessage;

public class Message_1_0 implements ServerMessage<MessageMetaData_1_0>, InboundMessage
{


    private static final AtomicIntegerFieldUpdater<Message_1_0> _refCountUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Message_1_0.class, "_referenceCount");

    private volatile int _referenceCount = 0;

    private final StoredMessage<MessageMetaData_1_0> _storedMessage;
    private List<ByteBuffer> _fragments;
    private WeakReference<Session_1_0> _session;
    private long _arrivalTime;


    public Message_1_0(final StoredMessage<MessageMetaData_1_0> storedMessage)
    {
        _storedMessage = storedMessage;
        _session = null;
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
                       final Session_1_0 session)
    {
        _storedMessage = storedMessage;
        _fragments = fragments;
        _session = new WeakReference<Session_1_0>(session);
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
        return _storedMessage.getMetaData();
    }

    public MessageMetaData_1_0.MessageHeader_1_0 getMessageHeader()
    {
        return getMessageMetaData().getMessageHeader();
    }

    public StoredMessage getStoredMessage()
    {
        return _storedMessage;
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

    public long getMessageNumber()
    {
        return _storedMessage.getMessageNumber();
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

    public int getContent(final ByteBuffer buf, final int offset)
    {
        return _storedMessage.getContent(offset, buf);
    }

    public ByteBuffer getContent(int offset, int size)
    {
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.limit(getContent(buf, offset));

        return buf;
    }

    public List<ByteBuffer> getFragments()
    {
        return _fragments;
    }

    public Session_1_0 getSession()
    {
        return _session == null ? null : _session.get();
    }


    public boolean incrementReference()
    {
        if(_refCountUpdater.incrementAndGet(this) <= 0)
        {
            _refCountUpdater.decrementAndGet(this);
            return false;
        }
        else
        {
            return true;
        }
    }

    /**
     * Threadsafe. This will decrement the reference count and when it reaches zero will remove the message from the
     * message store.
     */

    public void decrementReference()
    {
        int count = _refCountUpdater.decrementAndGet(this);

        // note that the operation of decrementing the reference count and then removing the message does not
        // have to be atomic since the ref count starts at 1 and the exchange itself decrements that after
        // the message has been passed to all queues. i.e. we are
        // not relying on the all the increments having taken place before the delivery manager decrements.
        if (count == 0)
        {
            // set the reference count way below 0 so that we can detect that the message has been deleted
            // this is to guard against the message being spontaneously recreated (from the mgmt console)
            // by copying from other queues at the same time as it is being removed.
            _refCountUpdater.set(this,Integer.MIN_VALUE/2);

            // must check if the handle is null since there may be cases where we decide to throw away a message
            // and the handle has not yet been constructed
            if (_storedMessage != null)
            {
                _storedMessage.remove();
            }
        }
        else
        {
            if (count < 0)
            {
                throw new RuntimeException("Reference count for message id " + getMessageNumber()
                                                  + " has gone below 0.");
            }
        }
    }

    public static class Reference extends MessageReference<Message_1_0>
    {
        public Reference(Message_1_0 message)
        {
            super(message);
        }

        protected void onReference(Message_1_0 message)
        {
            message.incrementReference();
        }

        protected void onRelease(Message_1_0 message)
        {
            message.decrementReference();
        }

    }
}
