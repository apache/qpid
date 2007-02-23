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

import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.framing.RequestManager;
import org.apache.qpid.framing.ResponseManager;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.protocol.AMQProtocolWriter;
import org.apache.qpid.protocol.RequestToken;
import org.apache.qpid.server.ack.TxAck;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.ack.UnacknowledgedMessageMapImpl;
import org.apache.qpid.server.exchange.MessageRouter;
import org.apache.qpid.server.exchange.NoRouteException;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQReference;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.LocalTransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.mina.common.ByteBuffer;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AMQChannel
{
    public static final int DEFAULT_PREFETCH = 5000;

    private static final Logger _log = Logger.getLogger(AMQChannel.class);

    private final int _channelId;

    //private boolean _transactional;

    private long _prefetch_HighWaterMark;

    private long _prefetch_LowWaterMark;

    private RequestManager _requestManager;
    private ResponseManager _responseManager;
    private AMQProtocolSession _session;

    private long _prefetchSize;

    /**
     * The delivery tag is unique per channel. This is pre-incremented before putting into the deliver frame so that
     * value of this represents the <b>last</b> tag sent out
     */
    private AtomicLong _deliveryTag = new AtomicLong(0);

    /**
     * A channel has a default queue (the last declared) that is used when no queue name is
     * explictily set
     */
    private AMQQueue _defaultQueue;

    /**
     * This tag is unique per subscription to a queue. The server returns this in response to a
     * basic.consume request.
     */
    private int _consumerTag;

    /**
     * The set of open references on this channel.
     */
    private Map<String, AMQReference> _references = new LinkedHashMap();

    /**
     * Maps from consumer tag to queue instance. Allows us to unsubscribe from a queue.
     */
    private final Map<AMQShortString, AMQQueue> _consumerTag2QueueMap = new HashMap<AMQShortString, AMQQueue>();

    private final MessageStore _messageStore;

    private UnacknowledgedMessageMap _unacknowledgedMessageMap = new UnacknowledgedMessageMapImpl(DEFAULT_PREFETCH);

    private final AtomicBoolean _suspended = new AtomicBoolean(false);

    private final MessageRouter _exchanges;

    private TransactionalContext _txnContext;

    /**
     * A context used by the message store enabling it to track context for a given channel even across
     * thread boundaries
     */
    private final StoreContext _storeContext = new StoreContext();

    private final List<RequiredDeliveryException> _returnMessages = new LinkedList<RequiredDeliveryException>();

    private MessageHandleFactory _messageHandleFactory = new MessageHandleFactory();

    private Set<Long> _browsedAcks = new HashSet<Long>();
    
    /**
     * Used in creating unique references. 
     */
    private static AtomicLong _refIdCounter = new AtomicLong();

    // XXX: clean up arguments
    public AMQChannel(int channelId, AMQProtocolSession session, MessageStore messageStore, MessageRouter exchanges, AMQMethodListener methodListener)
    {
        _channelId = channelId;
        _session = session;
        _prefetch_HighWaterMark = DEFAULT_PREFETCH;
        _prefetch_LowWaterMark = _prefetch_HighWaterMark / 2;
        _messageStore = messageStore;
        _exchanges = exchanges;
        _requestManager = new RequestManager(_session.getConnectionId(), channelId, _session, true);
        _responseManager = new ResponseManager(_session.getConnectionId(), channelId, methodListener, _session, true);
        // by default the session is non-transactional
        _txnContext = new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages, _browsedAcks);
    }

    /**
     * Sets this channel to be part of a local transaction
     */
    public void setLocalTransactional()
    {
        _txnContext = new LocalTransactionalContext(_messageStore, _storeContext, _returnMessages);
    }

    public boolean isTransactional()
    {
        // this does not look great but there should only be one "non-transactional"
        // transactional context, while there could be several transactional ones in
        // theory
        return !(_txnContext instanceof NonTransactionalContext);
    }

    public int getChannelId()
    {
        return _channelId;
    }

    public long getPrefetchCount()
    {
        return _prefetch_HighWaterMark;
    }

    public void setPrefetchCount(long prefetchCount)
    {
        _prefetch_HighWaterMark = prefetchCount;
    }

    public long getPrefetchSize()
    {
        return _prefetchSize;
    }


    public void setPrefetchSize(long prefetchSize)
    {
        _prefetchSize = prefetchSize;
    }

    public long getPrefetchLowMarkCount()
    {
        return _prefetch_LowWaterMark;
    }

    public void setPrefetchLowMarkCount(long prefetchCount)
    {
        _prefetch_LowWaterMark = prefetchCount;
    }

    public long getPrefetchHighMarkCount()
    {
        return _prefetch_HighWaterMark;
    }

    public void setPrefetchHighMarkCount(long prefetchCount)
    {
        _prefetch_HighWaterMark = prefetchCount;
    }

    public void addMessageTransfer(MessageTransferBody transferBody, long requestId, AMQProtocolSession publisher) throws AMQException
    {
        Content body = transferBody.getBody();
        AMQMessage message;
        switch (body.getContentType()) {
        case INLINE_T:
            message = new AMQMessage(_messageStore, transferBody, Collections.singletonList(body.getContent()), _txnContext);
            message.setPublisher(publisher);
            message.setRequestId(requestId);
            routeCurrentMessage(message);
            break;
        case REF_T:
            try {
                AMQReference ref = getReference(body.getContentAsByteArray());
                message = new AMQMessage(_messageStore, transferBody, ref.getContentList(), _txnContext);
                message.setPublisher(publisher);
                message.setRequestId(requestId);
                ref.addRefTransferBody(message);
            } catch (IllegalArgumentException e) {
                throw transferBody.getConnectionException(503, "Reference is not open");
            }

            break;
        }
    }

    private static String key(byte[] id)
    {
        return new String(id);
    }

    private AMQReference getReference(byte[] id)
    {
        String key = key(id);
        AMQReference ref = _references.get(key);
        if (ref == null)
        {
            throw new IllegalArgumentException(key);
        }
        return ref;
    }

    private AMQReference createReference(byte[] id)
    {
        String key = key(id);
        if (_references.containsKey(key))
        {
            throw new IllegalArgumentException(key);
        }
        AMQReference ref = new AMQReference(id);
        _references.put(key, ref);
        return ref;
    }

    private AMQReference removeReference(byte[] id)
    {
        String key = key(id);
        AMQReference ref = _references.remove(key);
        if (ref == null)
        {
            throw new IllegalArgumentException(key);
        }
        return ref;
    }

    public void addMessageOpen(MessageOpenBody open) throws AMQException
    {
        try
        {
            createReference(open.reference);
        }
        catch (IllegalArgumentException e)
        {
            throw open.getConnectionException(503, "Reference is already open");
        }
    }

    public void addMessageAppend(MessageAppendBody append) throws AMQException
    {
        try
        {
            AMQReference ref = getReference(append.reference);
            if (append.bytes != null) // sending an empty string results in a null
            {
                ref.appendContent(ByteBuffer.wrap(append.bytes));
            }
        }
        catch (IllegalArgumentException e)
        {
            throw append.getConnectionException(503, "Reference is not open");
        }
    }

    public void addMessageClose(MessageCloseBody close) throws AMQException
    {
        try
        {
            AMQReference ref = removeReference(close.reference);
            for (AMQMessage msg : ref.getMessageList())
            {
                routeCurrentMessage(msg);
            }
        }
        catch (IllegalArgumentException e)
        {
            throw close.getConnectionException(503, "Reference is not open");
        }
    }

    protected void routeCurrentMessage(AMQMessage msg) throws AMQException
    {
        try
        {
            _exchanges.routeContent(msg);
        }
        catch (NoRouteException e)
        {
            _returnMessages.add(e);
        }
        msg.routingComplete(_messageStore, _storeContext, _messageHandleFactory);

        MessageOkBody ok = MessageOkBody.createMethodBody(
                               _session.getProtocolMajorVersion(),
                               _session.getProtocolMinorVersion()
                           );
        _session.writeResponse(_channelId, msg.getRequestId(), ok);
    }

    public void deliverGet(RequestToken<MessageGetBody> request, long deliveryTag, AMQMessage msg)
    {                            
        request.respond(MessageOkBody.createMethodBody(request.getMajor(), request.getMinor()));
        deliver(msg, request.getRequest().destination, deliveryTag);
    }

    public void deliver(AMQMessage msg, AMQShortString destination, final long deliveryTag)
    {
        AMQMethodListener listener = new AMQMethodListener()
        {
            public boolean methodReceived(AMQMethodEvent evt) throws AMQException
            {
                AMQMethodBody method = evt.getMethod();
                if (_log.isDebugEnabled())
                {
                    _log.debug(method + " received on channel " + _channelId);
                }
                // XXX: multiple?
                if (method instanceof MessageOkBody)
                {
                    acknowledgeMessage(deliveryTag, false);
                    return true;
                }
                else
                {
                    // TODO: implement reject
                    return false;
                }
            }
            public void error(Exception e) {}
        };
        long maxFrameSize = _session.getFrameMax();
        if (msg.getFullSize() > maxFrameSize)
        {
            //need to send as reference
            deliverRef(msg, destination, listener);
        }
        else
        {
            //message will fit inline 
            deliverInline(msg, destination, listener);
        }
    }
    
    public void deliverInline(AMQMessage msg, AMQShortString destination, AMQMethodListener listener)
    {
        MessageTransferBody mtb = msg.getTransferBody().copy();
        mtb.destination = destination;
        mtb.redelivered = msg.isRedelivered();
        ByteBuffer buf = ByteBuffer.allocate((int)msg.getBodySize());
        for (ByteBuffer bb : msg.getContents())
        {
            buf.put(bb);
        }
        buf.flip();
        mtb.body = new Content(Content.TypeEnum.INLINE_T, buf);
        _session.writeRequest(_channelId, mtb, listener);
    }

    private synchronized byte[] nextRefId()
    {
        // clumsy
        return String.valueOf(_refIdCounter.incrementAndGet()).getBytes();
        //return new byte[]{_refIdCounter.getAndIncrement()};
    }
    
    public void deliverRef(AMQMessage msg, AMQShortString destination, AMQMethodListener listener)
    {
        AMQMethodListener dummy = new AMQMethodListener()
        { 
            public boolean methodReceived(AMQMethodEvent evt){ return true; } 
            public void error(Exception e) {}
        };
        byte major = _session.getProtocolMajorVersion();
        byte minor = _session.getProtocolMinorVersion();
        byte[] refId = nextRefId();
        _session.writeRequest(_channelId, MessageOpenBody.createMethodBody(major, minor, refId), dummy);
        MessageTransferBody mtb = msg.getTransferBody().copy();
        mtb.destination = destination;
        mtb.body = new Content(Content.TypeEnum.REF_T, refId);
        _session.writeRequest(_channelId, mtb, listener);
        for (ByteBuffer buffer : msg.getContents()) 
        {            
            //TODO: try and avoid all this copying!
            while (buffer.remaining() > 0) 
            {
                byte[] data = new byte[Math.min((int) _session.getFrameMax(), buffer.remaining())];
                buffer.get(data);
                _session.writeRequest(_channelId, MessageAppendBody.createMethodBody(major, minor, data, refId), dummy);
            }
        }
        _session.writeRequest(_channelId, MessageCloseBody.createMethodBody(major, minor, refId), dummy);
    }

    public RequestManager getRequestManager()
    {
        return _requestManager;
    }

    public ResponseManager getResponseManager()
    {
        return _responseManager;
    }

    public long getNextDeliveryTag()
    {
        return _deliveryTag.incrementAndGet();
    }

    public int getNextConsumerTag()
    {
        return ++_consumerTag;
    }

    /**
     * Subscribe to a queue. We register all subscriptions in the channel so that
     * if the channel is closed we can clean up all subscriptions, even if the
     * client does not explicitly unsubscribe from all queues.
     *
     * @param tag     the tag chosen by the client (if null, server will generate one)
     * @param queue   the queue to subscribe to
     * @param session the protocol session of the subscriber
     * @param noLocal
     * @param exclusive
     * @return the consumer tag. This is returned to the subscriber and used in
     *         subsequent unsubscribe requests
     * @throws ConsumerTagNotUniqueException if the tag is not unique
     * @throws AMQException                  if something goes wrong
     */
    public AMQShortString subscribeToQueue(AMQShortString tag, AMQQueue queue,
                        AMQProtocolSession session, boolean acks, FieldTable filters,
                        boolean noLocal, boolean exclusive) throws AMQException, ConsumerTagNotUniqueException
    {
        if (tag == null)
        {
            tag = new AMQShortString("sgen_" + getNextConsumerTag());
        }
        if (_consumerTag2QueueMap.containsKey(tag))
        {
            throw new ConsumerTagNotUniqueException();
        }
        acks = acks;
        queue.registerProtocolSession(session, _channelId, tag, acks, filters, noLocal, exclusive);
        _consumerTag2QueueMap.put(tag, queue);
        return tag;
    }


    public void unsubscribeConsumer(AMQProtocolSession session, AMQShortString consumerTag) throws AMQException
    {
        AMQQueue q = _consumerTag2QueueMap.remove(consumerTag);
        if (q != null)
        {
            q.unregisterProtocolSession(session, _channelId, consumerTag);
        }
    }

    /**
     * Called from the protocol session to close this channel and clean up.
     *
     * @throws AMQException if there is an error during closure
     */
    public void close(AMQProtocolSession session) throws AMQException
    {
        _txnContext.rollback();
        unsubscribeAllConsumers(session);
        requeue();
		_txnContext.commit();
    }

    private void unsubscribeAllConsumers(AMQProtocolSession session) throws AMQException
    {
        _log.info("Unsubscribing all consumers on channel " + toString());
        for (Map.Entry<AMQShortString, AMQQueue> me : _consumerTag2QueueMap.entrySet())
        {
            me.getValue().unregisterProtocolSession(session, _channelId, me.getKey());
        }
        _consumerTag2QueueMap.clear();
    }

    /**
     * Add a message to the channel-based list of unacknowledged messages
     *
     * @param message     the message that was delivered
     * @param deliveryTag the delivery tag used when delivering the message (see protocol spec for description of
     *                    the delivery tag)
     * @param queue       the queue from which the message was delivered
     */
    public void addUnacknowledgedMessage(AMQMessage message, long deliveryTag, AMQShortString consumerTag, AMQQueue queue)
    {
        synchronized (_unacknowledgedMessageMap.getLock())
        {
            _unacknowledgedMessageMap.add(deliveryTag, new UnacknowledgedMessage(queue, message, consumerTag, deliveryTag));
            checkSuspension();
        }
    }

    /**
     * Called to attempt re-enqueue all outstanding unacknowledged messages on the channel.
     * May result in delivery to this same channel or to other subscribers.
     *
     * @throws org.apache.qpid.AMQException if the requeue fails
     */
    public void requeue() throws AMQException
    {
        // we must create a new map since all the messages will get a new delivery tag when they are redelivered
        Collection<UnacknowledgedMessage> messagesToBeDelivered = _unacknowledgedMessageMap.cancelAllMessages();

        for (UnacknowledgedMessage unacked : messagesToBeDelivered)
        {
            unacked.message.setRedelivered(true);
            if (unacked.queue != null)
            {
                _txnContext.deliver(unacked.message, unacked.queue);
            }
        }
    }

    /**
     * Called to resend all outstanding unacknowledged messages to this same channel.
     */
    public void resend(final AMQProtocolSession session, final boolean requeue) throws AMQException
    {
        final List<UnacknowledgedMessage> msgToRequeue = new LinkedList<UnacknowledgedMessage>();

        _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
        {
           public boolean callback(UnacknowledgedMessage message) throws AMQException
            {
                long deliveryTag = message.deliveryTag;
                AMQShortString consumerTag = message.consumerTag;
                AMQMessage msg = message.message;
                msg.setRedelivered(true);
                if((consumerTag != null) && _consumerTag2QueueMap.containsKey(consumerTag))
                {
                    deliver(msg, consumerTag, deliveryTag);
                    //msg.writeDeliver(session, _channelId, deliveryTag, consumerTag);
                }
                else
                {
                    // Message has no consumer tag, so was "delivered" to a GET
                    // or consumer no longer registered
                    // cannot resend, so re-queue.
                    if (message.queue != null && (consumerTag == null || requeue))
                    {
                        msgToRequeue.add(message);                         
                    }
                }
                // false means continue processing
                return false;
            }

            public void visitComplete()
            {
            }
        });

        for(UnacknowledgedMessage message : msgToRequeue)
        {
            _txnContext.deliver(message.message, message.queue);
            _unacknowledgedMessageMap.remove(message.deliveryTag);
        }
     }

    /**
     * Callback indicating that a queue has been deleted. We must update the structure of unacknowledged
     * messages to remove the queue reference and also decrement any message reference counts, without
     * actually removing the item since we may get an ack for a delivery tag that was generated from the
     * deleted queue.
     *
     * @param queue the queue that has been deleted
     * @throws org.apache.qpid.AMQException if there is an error processing the unacked messages
     */
    public void queueDeleted(final AMQQueue queue) throws AMQException
    {
        _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
        {
            public boolean callback(UnacknowledgedMessage message) throws AMQException
            {
                if (message.queue == queue)
                {
                    try
                    {
                        message.discard(_storeContext);
                        message.queue = null;
                    }
                    catch (AMQException e)
                    {
                        _log.error("Error decrementing ref count on message " + message.message.getMessageId() + ": " +
                                   e, e);
                    }
                }
                return false;
            }

            public void visitComplete()
            {
            }
        });
    }

    /**
     * Acknowledge one or more messages.
     *
     * @param deliveryTag the last delivery tag
     * @param multiple    if true will acknowledge all messages up to an including the delivery tag. if false only
     *                    acknowledges the single message specified by the delivery tag
     * @throws AMQException if the delivery tag is unknown (e.g. not outstanding) on this channel
     */
    public void acknowledgeMessage(long deliveryTag, boolean multiple) throws AMQException
    {
        synchronized (_unacknowledgedMessageMap.getLock())
        {
            _unacknowledgedMessageMap.acknowledgeMessage(deliveryTag, multiple, _txnContext);
            checkSuspension();
        }
    }

    /**
     * Used only for testing purposes.
     *
     * @return the map of unacknowledged messages
     */
    public UnacknowledgedMessageMap getUnacknowledgedMessageMap()
    {
        return _unacknowledgedMessageMap;
    }

    public void addUnacknowledgedBrowsedMessage(AMQMessage msg, long deliveryTag, AMQShortString consumerTag, AMQQueue queue)
    {
        _browsedAcks.add(deliveryTag);
        addUnacknowledgedMessage(msg, deliveryTag, consumerTag, queue);
    }

    private void checkSuspension()
    {
        boolean suspend;
        
        suspend = ((_prefetch_HighWaterMark != 0) &&  _unacknowledgedMessageMap.size() >= _prefetch_HighWaterMark)
                 || ((_prefetchSize != 0) && _prefetchSize < _unacknowledgedMessageMap.getUnacknowledgeBytes());
        
        setSuspended(suspend);
    }

    public void setSuspended(boolean suspended)
    {
        boolean isSuspended = _suspended.get();

        if (isSuspended && !suspended)
        {
            // Continue being suspended if we are above the _prefetch_LowWaterMark
            suspended = _unacknowledgedMessageMap.size() > _prefetch_LowWaterMark;
        }

        boolean wasSuspended = _suspended.getAndSet(suspended);
        if (wasSuspended != suspended)
        {
            if (wasSuspended)
            {
                _log.debug("Unsuspending channel " + this);
                //may need to deliver queued messages
                for (AMQQueue q : _consumerTag2QueueMap.values())
                {
                    q.deliverAsync();
                }
            }
            else
            {
                _log.debug("Suspending channel " + this);
            }
        }
    }

    public boolean isSuspended()
    {
        return _suspended.get();
    }

    public void commit() throws AMQException
    {
        if (!isTransactional())
        {
            throw new AMQException("Fatal error: commit called on non-transactional channel");
        }
        _txnContext.commit();
    }

    public void rollback() throws AMQException
    {
        _txnContext.rollback();
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder(30);
        sb.append("Channel: id ").append(_channelId).append(", transaction mode: ").append(isTransactional());
        sb.append(", prefetch marks: ").append(_prefetch_LowWaterMark);
        sb.append("/").append(_prefetch_HighWaterMark);
        return sb.toString();
    }

    public void setDefaultQueue(AMQQueue queue)
    {
        _defaultQueue = queue;
    }

    public AMQQueue getDefaultQueue()
    {
        return _defaultQueue;
    }

    public StoreContext getStoreContext()
    {
        return _storeContext;
    }

    public void processReturns(AMQProtocolSession session) throws AMQException
    {
        for (RequiredDeliveryException bouncedMessage : _returnMessages)
        {
            AMQMessage message = bouncedMessage.getAMQMessage();
            session.writeResponse(_channelId, message.getMessageId(), message.getTransferBody());
//            message.writeReturn(session, _channelId, bouncedMessage.getReplyCode(), new AMQShortString(bouncedMessage.getMessage()));
        }
        _returnMessages.clear();
    }


    public boolean wouldSuspend(AMQMessage msg)
    {
        if (isSuspended())
        {
            return true;
        }
        else
        {
            boolean willSuspend = ((_prefetch_HighWaterMark != 0) &&  _unacknowledgedMessageMap.size() + 1 > _prefetch_HighWaterMark);
            if(!willSuspend)
            {
                final long unackedSize = _unacknowledgedMessageMap.getUnacknowledgeBytes();

                willSuspend = (_prefetchSize != 0) && (unackedSize != 0) && (_prefetchSize < msg.getSize() + unackedSize);
            }


            if(willSuspend)
            {
                setSuspended(true);
            }
            return willSuspend;
        }

    }
}
