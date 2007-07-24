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

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
//import org.apache.qpid.server.txn.TxnBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.txn.TransactionalContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;

/**
 * Combines the information that make up a deliverable message into a more manageable form.
 */
public class AMQMessage
{
    private static final Logger _log = Logger.getLogger(AMQMessage.class);

    private Set<Object> _tokens;

    private AMQProtocolSession _publisher;

    private final MessageTransferBody _transferBody;

    private List<ByteBuffer> _contents;

    private Iterable<ByteBuffer> _dupContentsIterable = new Iterable()
    {
        public Iterator<ByteBuffer> iterator()
        {
            return new Iterator()
            {
                private Iterator<ByteBuffer> iter = _contents.iterator();
                public boolean hasNext()
                {
                    return iter.hasNext();
                }
                public ByteBuffer next()
                {
                    return iter.next().duplicate();
                }
                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }
    };

    private final Long _messageId;

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    private AMQMessageHandle _messageHandle;

    // TODO: ideally this should be able to go into the transient message date - check this! (RG)
    private TransactionalContext _txnContext;
    
//    private List<AMQQueue> _destinationQueues = new LinkedList<AMQQueue>();
    private List<AMQQueue> _destinationQueues = new CopyOnWriteArrayList<AMQQueue>();

    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * The message store in which this message is contained.
     */
    private transient final MessageStore _store;

    /**
     * For non transactional publishes, a message can be stored as
     * soon as it is complete. For transactional messages it doesnt
     * need to be stored until the transaction is committed.
     */
    private boolean _storeWhenComplete;

    /**
     * Flag to indicate whether message has been delivered to a
     * consumer. Used in implementing return functionality for
     * messages published with the 'immediate' flag.
     */
    private boolean _deliveredToConsumer;
    private AtomicBoolean _taken = new AtomicBoolean(false);

    private long _requestId;//the request id of the transfer that this message represents

    public AMQMessage(MessageStore messageStore, MessageTransferBody transferBody, TransactionalContext txnContext)
    {
        this(messageStore, transferBody, txnContext, true);
    }

    public AMQMessage(MessageStore messageStore, MessageTransferBody transferBody, TransactionalContext txnContext, boolean storeWhenComplete)
    {
        _messageId = messageStore.getNewMessageId();
        _transferBody = transferBody;
        _store = messageStore;
        _txnContext = txnContext;
        _contents = new LinkedList();
        _storeWhenComplete = storeWhenComplete;
    }

    public AMQMessage(MessageStore store, long messageId, MessageTransferBody transferBody, List<ByteBuffer> contents, TransactionalContext txnContext)
            throws AMQException

    {
        _transferBody = transferBody;
        _contents = contents;
        _messageId = messageId;
        _store = store;
        _txnContext = txnContext;
        storeMessage();
    }

    public AMQMessage(MessageStore store, MessageTransferBody transferBody, List<ByteBuffer> contents, TransactionalContext txnContext)
            throws AMQException
    {
        this(store, store.getNewMessageId(), transferBody, contents, txnContext);
    }

    protected AMQMessage(AMQMessage msg) throws AMQException
    {
        this(msg._store, msg._messageId, msg._transferBody, msg._contents, msg._txnContext);
    }

    public long getSize()
    {
        //based on existing usage, this should return the size of the
        //data and inline data will already be included in the count
        //by getBodySize()
        return getBodySize();
    }

    public long getFullSize()
    {
        //this is used in determining whether a message can be inlined
        //or not and therefore must include the header size also
        return getHeaderSize() + getBodySize();
    }

    public int getHeaderSize()
    {
        int size = _transferBody.getBodySize();
        Content body = _transferBody.getBody();
        switch (body.getContentType())
        {
        case INLINE_T:
            size -= _transferBody.getBody().getEncodedSize();
            break;
        }
        return size;
    }

    public long getBodySize()
    {
        long size = 0L;
        for (ByteBuffer buffer : _contents)
        {
            size += buffer.limit();
        }
        return size;
    }
    

    public MessageTransferBody getTransferBody()
    {
        return _transferBody;
    }
    
    // Get methods (and some set methods) for MessageTransferBody
    
    public AMQShortString getAppId()
    {
        return _transferBody.getAppId();
    }
    
    public FieldTable getApplicationHeaders()
    {
        return _transferBody.getApplicationHeaders();
    }
    
    public Content getBody()
    {
        return _transferBody.getBody();
    }
    
    public AMQShortString getContentEncoding()
    {
        return _transferBody.getContentEncoding();
    }
    
    public AMQShortString getContentType()
    {
        return _transferBody.getContentType();
    }
    
    public AMQShortString getCorrelationId()
    {
        return _transferBody.getCorrelationId();
    }

    public void setCorrelationId(AMQShortString correlationId)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.correlationId = correlationId;
    }
    
    public short getDeliveryMode()
    {
        return _transferBody.getDeliveryMode();
    }

    public void setDeliveryMode(short deliveryMode)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.deliveryMode = deliveryMode;
    }
    
    public AMQShortString getDestination()
    {
        return _transferBody.getDestination();
    }
    
    public AMQShortString getExchange()
    {
        return _transferBody.getExchange();
    }
    
    public long getExpiration()
    {
        return _transferBody.getExpiration();
    }

    public void setExpiration(long expiration)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.expiration = expiration;
    }
    
    public boolean isImmediate()
    {
        return _transferBody.getImmediate();
    }
    
    public boolean isMandatory()
    {
        return _transferBody.getMandatory();
    }
    
    // TODO - how does this relate to _messageId in this class? See other getMessageId() method below.    
//     public AMQShortString getMessageId()
//     {
//         return _transferBody.getMessageId();
//     }

    public void setMessageId(AMQShortString messageId)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.messageId = messageId;
    }
    
    public short getPriority()
    {
        return _transferBody.getPriority();
    }

    public void setPriority(short priority)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.priority = priority;
    }


    public boolean isRedelivered()
    {
        return _transferBody.getRedelivered();
    }
    
    public AMQShortString getReplyTo()
    {
        return _transferBody.getReplyTo();
    }

    public void setReplyTo(AMQShortString replyTo)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.replyTo = replyTo;
    }
    
    public AMQShortString getRoutingKey()
    {
        return _transferBody.getRoutingKey();
    }
    
    public byte[] getSecurityToken()
    {
        return _transferBody.getSecurityToken();
    }
    
    public int getTicket()
    {
        return _transferBody.getTicket();
    }
    
    public long getTimestamp()
    {
        return _transferBody.getTimestamp();
    }

    public void setTimestamp(long timestamp)
    {
        // TODO - if/when MethodBody classes have set methods, then the public access
        // to these members should be revoked
        _transferBody.timestamp = timestamp;
    }
    
    public AMQShortString getTransactionId()
    {
        return _transferBody.getTransactionId();
    }
    
    public long getTtl()
    {
        return _transferBody.getTtl();
    }
    
    public AMQShortString getUserId()
    {
        return _transferBody.getUserId();
    }

    public void setType(String type)
    {
        throw new Error("XXX");
    }

    public String getType()
    {
        throw new Error("XXX");
    }

    public byte[] getMessageBytes()
    {
        byte[] result = new byte[(int) getBodySize()];
        int offset = 0;
        for (ByteBuffer bb : getContents())
        {
            bb.get(result, offset, bb.remaining());
        }
        return result;
    }

    public void storeMessage() throws AMQException
    {
        if (isPersistent())
        {
//            _store.put(this);
        }
    }

    public Iterable<ByteBuffer> getContents()
    {
        return _dupContentsIterable;
    }

    public List<AMQBody> getPayload()
    {
        throw new Error("XXX");
    }

    public boolean isAllContentReceived()
    {
        if (true) throw new Error("XXX");
        /*XXX*/return false;
        //return _bodyLengthReceived == _contentHeaderBody.bodySize;
    }

    NoConsumersException getNoConsumersException(String queue)
    {
        return new NoConsumersException(queue, this);
    }

    public void setRedelivered(boolean redelivered)
    {
        _transferBody.redelivered = redelivered;
    }

    public long getMessageId()
    {
        return _messageId;
    }

    /**
     * Threadsafe. Increment the reference count on the message.
     */
    public void incrementReference()
    {
        _referenceCount.incrementAndGet();
        if (_log.isDebugEnabled())
        {
            _log.debug("Ref count on message " + _messageId + " incremented to " + _referenceCount);
        }
    }

    /**
     * Threadsafe. This will decrement the reference count and when it reaches zero will remove the message from the
     * message store.
     */
    public void decrementReference(StoreContext storeContext) throws MessageCleanupException
    {
        // note that the operation of decrementing the reference count and then removing the message does not
        // have to be atomic since the ref count starts at 1 and the exchange itself decrements that after
        // the message has been passed to all queues. i.e. we are
        // not relying on the all the increments having taken place before the delivery manager decrements.
        if (_referenceCount.decrementAndGet() == 0)
        {
            try
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug("Ref count on message " + _messageId + " is zero; removing message");
                }

                // must check if the handle is null since there may be cases where we decide to throw away a message
                // and the handle has not yet been constructed
                // New:
//                 if (_messageHandle != null)
//                 {
//                     _messageHandle.removeMessage(storeContext, _messageId);
//                 }
                // Old:
                _store.removeMessage(storeContext, _messageId);
            }
            catch (AMQException e)
            {
                //to maintain consistency, we revert the count
                incrementReference();
                throw new MessageCleanupException(_messageId, e);
            }
        }
    }

    public void setPublisher(AMQProtocolSession publisher)
    {
        _publisher = publisher;
    }

    public AMQProtocolSession getPublisher()
    {
        return _publisher;
    }

    public boolean checkToken(Object token)
    {
        if(_tokens==null)
        {
            _tokens  = new HashSet<Object>();
        }
        
        if (_tokens.contains(token))
        {
            return true;
        }
        else
        {
            _tokens.add(token);
            return false;
        }
    }

    public void enqueue(AMQQueue queue) throws AMQException
    {
        _destinationQueues.add(queue);
    }

    public void dequeue(StoreContext storeContext, AMQQueue queue) throws AMQException
    {
        _messageHandle.dequeue(storeContext, _messageId, queue);
        //only record associations where both queue and message will survive
        //a restart, so only need to remove association if this is the case
        if (isPersistent() && queue.isDurable())
        {
            _store.dequeueMessage(storeContext, queue.getName(), _messageId);
        }
    }

    public boolean isPersistent() throws AMQException
    {
        return getDeliveryMode() == 2;
    }

    /**
     * Called to enforce the 'immediate' flag.
     * @throws NoConsumersException if the message is marked for
     * immediate delivery but has not been marked as delivered to a
     * consumer
     */
    public void checkDeliveredToConsumer() throws NoConsumersException
    {
        if (isImmediate() && !_deliveredToConsumer)
        {
            throw new NoConsumersException(this);
        }
    }

    /**
     * Called when this message is delivered to a consumer. (used to
     * implement the 'immediate' flag functionality).
     * And by selectors to determin if the message has already been sent
     */
    public void setDeliveredToConsumer()
    {
        _deliveredToConsumer = true;
    }

    /**
     * Called selectors to determin if the message has already been sent
     * @return   _deliveredToConsumer
     */
    public boolean getDeliveredToConsumer()
    {
        return _deliveredToConsumer;
    }

    public boolean taken()
    {
        return _taken.getAndSet(true);
    }

    public void release()
    {
        _taken.set(false);
    }

    public void routingComplete(MessageStore store, StoreContext storeContext, MessageHandleFactory factory) throws AMQException
    {
        final boolean persistent = isPersistent();
        _messageHandle = factory.createMessageHandle(_messageId, store, persistent);
        if (persistent)
        {
            _txnContext.beginTranIfNecessary();
        }

        // enqueuing the messages ensure that if required the destinations are recorded to a
        // persistent store
        for (AMQQueue q : _destinationQueues)
        {
            _messageHandle.enqueue(storeContext, _messageId, q);
        }
        deliver(storeContext);
    }
    
    private void deliver(StoreContext storeContext) throws AMQException
    {
        // we get a reference to the destination queues now so that we can clear the
        // transient message data as quickly as possible
        if (_log.isDebugEnabled())
        {
            _log.debug("Delivering message " + _messageId);
        }
        try
        {
            // first we allow the handle to know that the message has been fully received. This is useful if it is
            // maintaining any calculated values based on content chunks
            _messageHandle.setPublishAndContentHeaderBody(storeContext, _messageId, _transferBody);

            // we then allow the transactional context to do something with the message content
            // now that it has all been received, before we attempt delivery
            _txnContext.messageFullyReceived(isPersistent());

            for (AMQQueue q : _destinationQueues)
            {
                _txnContext.deliver(this, q);
            }
        }
        finally
        {
            _destinationQueues.clear();
            decrementReference(storeContext);
        }
    }
    
    // Robert Godfrey added these in r497770
    public void writeDeliver(AMQProtocolSession protocolSession, int channelId, long deliveryTag, AMQShortString consumerTag)
            throws AMQException
    {
        throw new Error("XXX");
    }

    // Robert Godfrey added these in r503604
    public long getArrivalTime()
    {
        throw new Error("XXX");
    }

    public void setRequestId(long requestId) 
    {
        _requestId = requestId;
    }

    public long getRequestId() 
    {
        return _requestId;
    }

    public MessageStore getMessageStore()
    {
    	return _store;
    }
    
    public TransactionalContext getTransactionContext()
    {
    	return _txnContext;
    }
}
