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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A deliverable message. */
public class TransientAMQMessage implements AMQMessage
{
    /** Used for debugging purposes. */
    protected static final Logger _log = Logger.getLogger(AMQMessage.class);

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    protected ContentHeaderBody _contentHeaderBody;

    protected MessagePublishInfo _messagePublishInfo;

    protected List<ContentChunk> _contentBodies;

    protected long _arrivalTime;

    protected final Long _messageId;

    /** Flag to indicate that this message requires 'immediate' delivery. */

    private static final byte IMMEDIATE = 0x01;

    /**
     * Flag to indicate whether this message has been delivered to a consumer. Used in implementing return functionality
     * for messages published with the 'immediate' flag.
     */

    private static final byte DELIVERED_TO_CONSUMER = 0x02;

    private byte _flags = 0;

    private long _expiration;

    private AMQProtocolSession.ProtocolSessionIdentifier _sessionIdentifier;
    private static final byte IMMEDIATE_AND_DELIVERED = (byte) (IMMEDIATE | DELIVERED_TO_CONSUMER);

    /**
     * Used to iterate through all the body frames associated with this message. Will not keep all the data in memory
     * therefore is memory-efficient.
     */
    private class BodyFrameIterator implements Iterator<AMQDataBlock>
    {
        private int _channel;

        private int _index = -1;
        private AMQProtocolSession _protocolSession;

        private BodyFrameIterator(AMQProtocolSession protocolSession, int channel)
        {
            _channel = channel;
            _protocolSession = protocolSession;
        }

        public boolean hasNext()
        {
            return _index < (getBodyCount() - 1);
        }

        public AMQDataBlock next()
        {
            AMQBody cb =
                    getProtocolVersionMethodConverter().convertToBody(getContentChunk(++_index));

            return new AMQFrame(_channel, cb);
        }

        private ProtocolVersionMethodConverter getProtocolVersionMethodConverter()
        {
            return _protocolSession.getMethodRegistry().getProtocolVersionMethodConverter();
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    private class BodyContentIterator implements Iterator<ContentChunk>
    {

        private int _index = -1;

        public boolean hasNext()
        {
            return _index < (getBodyCount() - 1);
        }

        public ContentChunk next()
        {
            return getContentChunk(++_index);
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Used by SimpleAMQQueueTest, TxAckTest.TestMessage, AbstractHeaderExchangeTestBase.Message
     * These all need refactoring to some sort of MockAMQMessageFactory.
     */
    @Deprecated
    protected TransientAMQMessage(AMQMessage message) throws AMQException
    {
        _messageId = message.getMessageId();
        _flags = ((TransientAMQMessage) message)._flags;
        _contentHeaderBody = message.getContentHeaderBody();
        _messagePublishInfo = message.getMessagePublishInfo();
    }

    /**
     * Normal message creation via the MessageFactory uses this constructor
     * Package scope limited as MessageFactory should be used
     *
     * @param messageId
     *
     * @see MessageFactory
     */
    TransientAMQMessage(Long messageId)
    {
        _messageId = messageId;
    }

    public String debugIdentity()
    {
        return "(HC:" + System.identityHashCode(this) + " ID:" + getMessageId() + " Ref:" + _referenceCount.get() + ")";
    }

    public void setExpiration(final long expiration)
    {
        _expiration = expiration;
    }

    public Iterator<AMQDataBlock> getBodyFrameIterator(AMQProtocolSession protocolSession, int channel)
    {
        return new BodyFrameIterator(protocolSession, channel);
    }

    public Iterator<ContentChunk> getContentBodyIterator()
    {
        return new BodyContentIterator();
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }

    public Long getMessageId()
    {
        return _messageId;
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

    /**
     * Called to enforce the 'immediate' flag.
     *
     * @returns true if the message is marked for immediate delivery but has not been marked as delivered
     * to a consumer
     */
    public boolean immediateAndNotDelivered()
    {

        return (_flags & IMMEDIATE_AND_DELIVERED) == IMMEDIATE;

    }

    /**
     * Checks to see if the message has expired. If it has the message is dequeued.
     *
     * @return true if the message has expire
     *
     * @throws AMQException
     */
    public boolean expired() throws AMQException
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
        return _contentHeaderBody.bodySize;
    }

    public Object getPublisherClientInstance()
    {
        return _sessionIdentifier.getSessionInstance();
    }

    public Object getPublisherIdentifier()
    {
        return _sessionIdentifier.getSessionIdentifier();
    }

    public void setClientIdentifier(final AMQProtocolSession.ProtocolSessionIdentifier sessionIdentifier)
    {
        _sessionIdentifier = sessionIdentifier;
    }

    /** From AMQMessageHandle * */

    public int getBodyCount()
    {
        return _contentBodies.size();
    }

    public ContentChunk getContentChunk(int index)
    {
        if (_contentBodies == null)
        {
            throw new RuntimeException("No ContentBody has been set");
        }

        if (index > _contentBodies.size() - 1 || index < 0)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(StoreContext storeContext, ContentChunk contentChunk, boolean isLastContentBody)
            throws AMQException
    {
        if (_contentBodies == null)
        {
            if (isLastContentBody)
            {
                _contentBodies = Collections.singletonList(contentChunk);
            }
            else
            {
                _contentBodies = new ArrayList<ContentChunk>();
                _contentBodies.add(contentChunk);
            }
        }
        else
        {
            _contentBodies.add(contentChunk);
        }
    }

    public MessagePublishInfo getMessagePublishInfo()
    {
        return _messagePublishInfo;
    }

    public boolean isPersistent()
    {
        return false;
    }

    /**
     * This is called when all the content has been received.
     *
     * @param storeContext
     * @param messagePublishInfo
     * @param contentHeaderBody  @throws AMQException
     */
    public void setPublishAndContentHeaderBody(StoreContext storeContext, MessagePublishInfo messagePublishInfo,
                                               ContentHeaderBody contentHeaderBody)
            throws AMQException
    {

        if (contentHeaderBody == null)
        {
            throw new NullPointerException("HeaderBody cannot be null");
        }

        if (messagePublishInfo == null)
        {
            throw new NullPointerException("PublishInfo cannot be null");
        }

        _arrivalTime = System.currentTimeMillis();


        _contentHeaderBody = contentHeaderBody;
        _messagePublishInfo = messagePublishInfo;

        updateHeaderAndFlags();
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

    public void recoverFromMessageMetaData(MessageMetaData mmd)
    {
        _arrivalTime = mmd.getArrivalTime();
        _contentHeaderBody = mmd.getContentHeaderBody();
        _messagePublishInfo = mmd.getMessagePublishInfo();

        updateHeaderAndFlags();
    }

    private void updateHeaderAndFlags()
    {
        if (_contentHeaderBody.bodySize == 0)
        {
            _contentBodies = Collections.EMPTY_LIST;
        }

        if (_messagePublishInfo.isImmediate())
        {
            _flags |= IMMEDIATE;
        }
    }

    public void recoverContentBodyFrame(ContentChunk contentChunk, boolean isLastContentBody) throws AMQException
    {
        addContentBodyFrame(null, contentChunk, isLastContentBody);
    }


    public String toString()
    {
        // return "Message[" + debugIdentity() + "]: " + _messageId + "; ref count: " + _referenceCount + "; taken : " +
        // _taken + " by :" + _takenBySubcription;

        return "Message[" + debugIdentity() + "]: " + getMessageId() + "; ref count: " + _referenceCount;
    }

}
