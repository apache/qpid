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
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.exchange.Exchange;
 

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A deliverable message.
 */
public class AMQMessage implements Filterable<AMQException>
{
    /** Used for debugging purposes. */
    private static final Logger _log = Logger.getLogger(AMQMessage.class);

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    private final AMQMessageHandle _messageHandle;

    /** Holds the transactional context in which this message is being processed. */
    private StoreContext _storeContext;

    /**
     * Flag to indicate whether this message has been delivered to a consumer. Used in implementing return functionality
     * for messages published with the 'immediate' flag.
     */
    private boolean _deliveredToConsumer;

    /** Flag to indicate that this message requires 'immediate' delivery. */
    private boolean _immediate;

    private long _expiration;

    private Object _publisherClientInstance;
    private Object _publisherIdentifier;
    private final long _size;


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
            try
            {
                return _index < (_messageHandle.getBodyCount(getStoreContext()) - 1);
            }
            catch (AMQException e)
            {
                _log.error("Unable to get body count: " + e, e);

                return false;
            }
        }

        public AMQDataBlock next()
        {
            try
            {

                AMQBody cb =
                        getProtocolVersionMethodConverter().convertToBody(_messageHandle.getContentChunk(getStoreContext(),
                                                                                                         ++_index));

                return new AMQFrame(_channel, cb);
            }
            catch (AMQException e)
            {
                // have no choice but to throw a runtime exception
                throw new RuntimeException("Error getting content body: " + e, e);
            }

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

    public StoreContext getStoreContext()
    {
        return _storeContext;
    }

    private class BodyContentIterator implements Iterator<ContentChunk>
    {

        private int _index = -1;

        public boolean hasNext()
        {
            try
            {
                return _index < (_messageHandle.getBodyCount(getStoreContext()) - 1);
            }
            catch (AMQException e)
            {
                _log.error("Error getting body count: " + e, e);

                return false;
            }
        }

        public ContentChunk next()
        {
            try
            {
                return _messageHandle.getContentChunk(getStoreContext(), ++_index);
            }
            catch (AMQException e)
            {
                throw new RuntimeException("Error getting content body: " + e, e);
            }
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }



    /**
     * Used when recovering, i.e. when the message store is creating references to messages. In that case, the normal
     * enqueue/routingComplete is not done since the recovery process is responsible for routing the messages to
     * queues.
     *
     * @param messageId
     * @param store
     * @param factory
     *
     * @throws AMQException
     */
    public AMQMessage(Long messageId, MessageStore store, MessageHandleFactory factory, TransactionalContext txnConext)
            throws AMQException
    {
        _messageHandle = factory.createMessageHandle(messageId, store, true);
        _storeContext = txnConext.getStoreContext();
        _size = _messageHandle.getBodySize(txnConext.getStoreContext());
    }

        /**
     * Used when recovering, i.e. when the message store is creating references to messages. In that case, the normal
     * enqueue/routingComplete is not done since the recovery process is responsible for routing the messages to
     * queues.
     *
     * @param messageHandle
     *
     * @throws AMQException
     */
    public AMQMessage(
                AMQMessageHandle messageHandle,
                StoreContext storeConext,
                MessagePublishInfo info)
            throws AMQException
    {
        _messageHandle = messageHandle;
        _storeContext = storeConext;
        _immediate = info.isImmediate();
        _size = messageHandle.getBodySize(storeConext);

    }


    protected AMQMessage(AMQMessage msg) throws AMQException
    {
        _messageHandle = msg._messageHandle;
        _storeContext = msg._storeContext;
        _deliveredToConsumer = msg._deliveredToConsumer;
        _size = msg._size;

    }


    public String debugIdentity()
    {
        return "(HC:" + System.identityHashCode(this) + " ID:" + getMessageId() + " Ref:" + _referenceCount.get() + ")";
    }

    public void setExpiration(final long expiration)
    {

        _expiration = expiration;

    }

    public boolean isReferenced()
    {
        return _referenceCount.get() > 0;
    }

    public Iterator<AMQDataBlock> getBodyFrameIterator(AMQProtocolSession protocolSession, int channel)
    {
        return new BodyFrameIterator(protocolSession, channel);
    }

    public Iterator<ContentChunk> getContentBodyIterator()
    {
        return new BodyContentIterator();
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        return _messageHandle.getContentHeaderBody(getStoreContext());
    }



    public Long getMessageId()
    {
        return _messageHandle.getMessageId();
    }

    /**
     * Creates a long-lived reference to this message, and increments the count of such references, as an atomic
     * operation.
     */
    public AMQMessage takeReference()
    {
        incrementReference(); // _referenceCount.incrementAndGet();

        return this;
    }

    /** Threadsafe. Increment the reference count on the message. */
    public void incrementReference()
    {
        _referenceCount.incrementAndGet();
        // if (_log.isDebugEnabled())
        // {
        // _log.debug("Ref count on message " + debugIdentity() + " incremented " + Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 6));
        // }
    }

    /**
     * Threadsafe. This will decrement the reference count and when it reaches zero will remove the message from the
     * message store.
     *
     * @param storeContext
     *
     * @throws MessageCleanupException when an attempt was made to remove the message from the message store and that
     *                                 failed
     */
    public void decrementReference(StoreContext storeContext) throws MessageCleanupException
    {
        int count = _referenceCount.decrementAndGet();

        // note that the operation of decrementing the reference count and then removing the message does not
        // have to be atomic since the ref count starts at 1 and the exchange itself decrements that after
        // the message has been passed to all queues. i.e. we are
        // not relying on the all the increments having taken place before the delivery manager decrements.
        if (count == 0)
        {
            try
            {
                // if (_log.isDebugEnabled())
                // {
                // _log.debug("Decremented ref count on message " + debugIdentity() + " is zero; removing message" + Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 6));
                // }

                // must check if the handle is null since there may be cases where we decide to throw away a message
                // and the handle has not yet been constructed
                if (_messageHandle != null)
                {
                    _messageHandle.removeMessage(storeContext);
                }
            }
            catch (AMQException e)
            {
                // to maintain consistency, we revert the count
                incrementReference();
                throw new MessageCleanupException(getMessageId(), e);
            }
        }
        else
        {
            if (count < 0)
            {
                throw new MessageCleanupException("Reference count for message id " + debugIdentity()
                                                  + " has gone below 0.");
            }
        }
    }


    /**
     * Called selectors to determin if the message has already been sent
     *
     * @return _deliveredToConsumer
     */
    public boolean getDeliveredToConsumer()
    {
        return _deliveredToConsumer;
    }

    public boolean isPersistent() throws AMQException
    {
        return _messageHandle.isPersistent(getStoreContext());
    }

    /**
     * Called to enforce the 'immediate' flag.
     *
     * @returns  true if the message is marked for immediate delivery but has not been marked as delivered
     *                              to a consumer
     */
    public boolean immediateAndNotDelivered() 
    {

        return (_immediate && !_deliveredToConsumer);

    }

    public MessagePublishInfo getMessagePublishInfo() throws AMQException
    {
        return _messageHandle.getMessagePublishInfo(getStoreContext());
    }

    public boolean isRedelivered()
    {
        return _messageHandle.isRedelivered();
    }

    public void setRedelivered(boolean redelivered)
    {
        _messageHandle.setRedelivered(redelivered);
    }

    public long getArrivalTime()
    {
        return _messageHandle.getArrivalTime();
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
        _deliveredToConsumer = true;
    }



    public AMQMessageHandle getMessageHandle()
    {
        return _messageHandle;
    }

    public long getSize()
    {
        return _size;

    }

    public void setPublisherClientInstance(final Object publisherClientInstance)
    {
        _publisherClientInstance = publisherClientInstance;
    }

    public Object getPublisherClientInstance()
    {
        return _publisherClientInstance;
    }
                                                                                          
    public Object getPublisherIdentifier()
    {
        return _publisherIdentifier;
    }

    public void setPublisherIdentifier(final Object publisherIdentifier)
    {
        _publisherIdentifier = publisherIdentifier;
    }

    public String toString()
    {
        // return "Message[" + debugIdentity() + "]: " + _messageId + "; ref count: " + _referenceCount + "; taken : " +
        // _taken + " by :" + _takenBySubcription;

        return "Message[" + debugIdentity() + "]: " + getMessageId() + "; ref count: " + _referenceCount;
    }

}
