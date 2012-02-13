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
package org.apache.qpid.server.message;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoredMessage;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * A deliverable message.
 */
public class AMQMessage extends AbstractServerMessageImpl<MessageMetaData>
{
    /** Used for debugging purposes. */
    private static final Logger _log = Logger.getLogger(AMQMessage.class);

    /** Flag to indicate that this message requires 'immediate' delivery. */

    private static final byte IMMEDIATE = 0x01;

    /**
     * Flag to indicate whether this message has been delivered to a consumer. Used in implementing return functionality
     * for messages published with the 'immediate' flag.
     */

    private static final byte DELIVERED_TO_CONSUMER = 0x02;

    private byte _flags = 0;

    private long _expiration;

    private final long _size;

    private Object _connectionIdentifier;
    private static final byte IMMEDIATE_AND_DELIVERED = (byte) (IMMEDIATE | DELIVERED_TO_CONSUMER);

    public AMQMessage(StoredMessage<MessageMetaData> handle)
    {
        this(handle, null);
    }
    
    public AMQMessage(StoredMessage<MessageMetaData> handle, WeakReference<AMQChannel> channelRef)
    {
        super(handle);


        final MessageMetaData metaData = handle.getMetaData();
        _size = metaData.getContentSize();
        final MessagePublishInfo messagePublishInfo = metaData.getMessagePublishInfo();

        if(messagePublishInfo.isImmediate())
        {
            _flags |= IMMEDIATE;
        }
    }

    public void setExpiration(final long expiration)
    {

        _expiration = expiration;

    }

    public MessageMetaData getMessageMetaData()
    {
        return getStoredMessage().getMetaData();
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        return getMessageMetaData().getContentHeaderBody();
    }

    public Long getMessageId()
    {
        return getStoredMessage().getMessageNumber();
    }

    /**
     * Called selectors to determin if the message has already been sent
     *
     * @return _deliveredToConsumer
     */
    public boolean getDeliveredToConsumer()
    {
        return (_flags & DELIVERED_TO_CONSUMER) != 0;
    }

    public String getRoutingKey()
    {
        MessageMetaData messageMetaData = getMessageMetaData();
        if (messageMetaData != null)
        {
            AMQShortString routingKey = messageMetaData.getMessagePublishInfo().getRoutingKey();
            if (routingKey != null)
            {
                return routingKey.asString();
            }
        }
        return null;
    }

    public AMQMessageHeader getMessageHeader()
    {
        return getMessageMetaData().getMessageHeader();
    }

    public boolean isPersistent()
    {
        return getMessageMetaData().isPersistent();
    }

    /**
     * Called to enforce the 'immediate' flag.
     *
     * @returns  true if the message is marked for immediate delivery but has not been marked as delivered
     *                              to a consumer
     */
    public boolean immediateAndNotDelivered()
    {

        return (_flags & IMMEDIATE_AND_DELIVERED) == IMMEDIATE;

    }

    public MessagePublishInfo getMessagePublishInfo() throws AMQException
    {
        return getMessageMetaData().getMessagePublishInfo();
    }

    public long getArrivalTime()
    {
        return getMessageMetaData().getArrivalTime();
    }

    /**
     * Checks to see if the message has expired. If it has the message is dequeued.
     *
     * @param queue The queue to check the expiration against. (Currently not used)
     *
     * @return true if the message has expire
     *
     * @throws AMQException
     */
    public boolean expired(AMQQueue queue) throws AMQException
    {

        if (_expiration != 0L)
        {
            long now = System.currentTimeMillis();

            return (now > _expiration);
        }

        return false;
    }

    /**
     * Called when this message is delivered to a consumer. (used to implement the 'immediate' flag functionality).
     * And for selector efficiency.
     */
    public void setDeliveredToConsumer()
    {
        _flags |= DELIVERED_TO_CONSUMER;
    }

    public long getSize()
    {
        return _size;

    }

    public boolean isImmediate()
    {
        return (_flags & IMMEDIATE) == IMMEDIATE;
    }

    public long getExpiration()
    {
        return _expiration;
    }

    public MessageReference newReference()
    {
        return new AMQMessageReference(this);
    }

    public long getMessageNumber()
    {
        return getStoredMessage().getMessageNumber();
    }


    public Object getConnectionIdentifier()
    {
        return _connectionIdentifier;

    }

    public void setConnectionIdentifier(final Object connectionIdentifier)
    {
        _connectionIdentifier = connectionIdentifier;
    }


    public String toString()
    {
        return "Message[" + debugIdentity() + "]: " + getMessageId() + "; ref count: " + getReferenceCount();
    }

    public int getContent(ByteBuffer buf, int offset)
    {
        return getStoredMessage().getContent(offset, buf);
    }


    public ByteBuffer getContent(int offset, int size)
    {
        return getStoredMessage().getContent(offset, size);
    }

}
