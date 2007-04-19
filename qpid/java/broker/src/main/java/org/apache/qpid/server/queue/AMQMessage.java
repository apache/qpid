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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ProtocolVersionMethodConverter;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.TransactionalContext;

/** Combines the information that make up a deliverable message into a more manageable form. */

import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Combines the information that make up a deliverable message into a more manageable form. */
public class AMQMessage
{
    private static final Logger _log = Logger.getLogger(AMQMessage.class);

    /** Used in clustering */
    private Set<Object> _tokens;

    /** Only use in clustering - should ideally be removed? */
    private AMQProtocolSession _publisher;

    private final Long _messageId;

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    private AMQMessageHandle _messageHandle;

    // TODO: ideally this should be able to go into the transient message date - check this! (RG)
    private TransactionalContext _txnContext;

    /**
     * Flag to indicate whether message has been delivered to a consumer. Used in implementing return functionality for
     * messages published with the 'immediate' flag.
     */
    private boolean _deliveredToConsumer;
    /**
     * We need to keep track of whether the message was 'immediate' as in extreme circumstances, when the
     * checkDelieveredToConsumer is called, the message may already have been received and acknowledged, and the body
     * removed from the store.
     */
    private boolean _immediate;

    private AtomicBoolean _taken = new AtomicBoolean(false);
    private TransientMessageData _transientMessageData = new TransientMessageData();

    private Subscription _takenBySubcription;
    private Set<Subscription> _rejectedBy = null;
    private Map<AMQQueue, AtomicBoolean> _takenMap = new HashMap<AMQQueue, AtomicBoolean>();
    private Map<AMQQueue, Subscription> _takenBySubcriptionMap = new HashMap<AMQQueue, Subscription>();

    public boolean isTaken(AMQQueue queue)
    {
        return _taken.get();
    }

    private final int hashcode = System.identityHashCode(this);

    public String debugIdentity()
    {
        return "(HC:" + hashcode + " ID:" + _messageId + " Ref:" + _referenceCount.get() + ")";
    }

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
                return _index < _messageHandle.getBodyCount(getStoreContext(), _messageId) - 1;
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

                AMQBody cb = getProtocolVersionMethodConverter().convertToBody(_messageHandle.getContentChunk(getStoreContext(), _messageId, ++_index));
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
            return _protocolSession.getRegistry().getProtocolVersionMethodConverter();
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    public StoreContext getStoreContext()
    {
        return _txnContext.getStoreContext();
    }

    private class BodyContentIterator implements Iterator<ContentChunk>
    {

        private int _index = -1;

        public boolean hasNext()
        {
            try
            {
                return _index < _messageHandle.getBodyCount(getStoreContext(), _messageId) - 1;
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
                return _messageHandle.getContentChunk(getStoreContext(), _messageId, ++_index);
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

    public AMQMessage(Long messageId, MessagePublishInfo info,
                      TransactionalContext txnContext)
    {
        _messageId = messageId;
        _txnContext = txnContext;
        _immediate = info.isImmediate();
        _transientMessageData.setMessagePublishInfo(info);

        _taken = new AtomicBoolean(false);

        if (_log.isDebugEnabled())
        {
            _log.debug("Message(" + System.identityHashCode(this) + ") created (" + debugIdentity() + ")");
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
    public AMQMessage(Long messageId, MessageStore store, MessageHandleFactory factory, TransactionalContext txnConext) throws AMQException
    {
        _messageId = messageId;
        _messageHandle = factory.createMessageHandle(messageId, store, true);
        _txnContext = txnConext;
        _transientMessageData = null;
    }

    /**
     * Used in testing only. This allows the passing of the content header immediately on construction.
     *
     * @param messageId
     * @param info
     * @param txnContext
     * @param contentHeader
     */
    public AMQMessage(Long messageId, MessagePublishInfo info,
                      TransactionalContext txnContext, ContentHeaderBody contentHeader) throws AMQException
    {
        this(messageId, info, txnContext);
        setContentHeaderBody(contentHeader);
    }

    /**
     * Used in testing only. This allows the passing of the content header and some body fragments on construction.
     *
     * @param messageId
     * @param info
     * @param txnContext
     * @param contentHeader
     * @param destinationQueues
     * @param contentBodies
     *
     * @throws AMQException
     */
    public AMQMessage(Long messageId, MessagePublishInfo info,
                      TransactionalContext txnContext,
                      ContentHeaderBody contentHeader, List<AMQQueue> destinationQueues,
                      List<ContentChunk> contentBodies, MessageStore messageStore, StoreContext storeContext,
                      MessageHandleFactory messageHandleFactory) throws AMQException
    {
        this(messageId, info, txnContext, contentHeader);
        _transientMessageData.setDestinationQueues(destinationQueues);
        routingComplete(messageStore, storeContext, messageHandleFactory);
        for (ContentChunk cb : contentBodies)
        {
            addContentBodyFrame(storeContext, cb);
        }
    }

    protected AMQMessage(AMQMessage msg) throws AMQException
    {
        _messageId = msg._messageId;
        _messageHandle = msg._messageHandle;
        _txnContext = msg._txnContext;
        _deliveredToConsumer = msg._deliveredToConsumer;
        _transientMessageData = msg._transientMessageData;
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
        if (_transientMessageData != null)
        {
            return _transientMessageData.getContentHeaderBody();
        }
        else
        {
            return _messageHandle.getContentHeaderBody(getStoreContext(), _messageId);
        }
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _transientMessageData.setContentHeaderBody(contentHeaderBody);
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

        for (AMQQueue q : _transientMessageData.getDestinationQueues())
        {
            _takenMap.put(q, new AtomicBoolean(false));
            _messageHandle.enqueue(storeContext, _messageId, q);
        }

        if (_transientMessageData.getContentHeaderBody().bodySize == 0)
        {
            deliver(storeContext);
        }
    }

    public boolean addContentBodyFrame(StoreContext storeContext, ContentChunk contentChunk) throws AMQException
    {
        _transientMessageData.addBodyLength(contentChunk.getSize());
        final boolean allContentReceived = isAllContentReceived();
        _messageHandle.addContentBodyFrame(storeContext, _messageId, contentChunk, allContentReceived);
        if (allContentReceived)
        {
            deliver(storeContext);
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean isAllContentReceived() throws AMQException
    {
        return _transientMessageData.isAllContentReceived();
    }

    public long getMessageId()
    {
        return _messageId;
    }

    /**
     * Creates a long-lived reference to this message, and increments the count of such references, as an atomic
     * operation.
     */
    public AMQMessage takeReference()
    {
        _referenceCount.incrementAndGet();
        return this;
    }

    /** Threadsafe. Increment the reference count on the message. */
    protected void incrementReference()
    {
        _referenceCount.incrementAndGet();
        if (_log.isDebugEnabled())
        {
            _log.debug("Ref count on message " + debugIdentity() + " incremented " + Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 6));
        }
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
                if (_log.isDebugEnabled())
                {
                    _log.debug("Decremented ref count on message " + debugIdentity() + " is zero; removing message" + Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 6));
                }

                // must check if the handle is null since there may be cases where we decide to throw away a message
                // and the handle has not yet been constructed
                if (_messageHandle != null)
                {
                    _messageHandle.removeMessage(storeContext, _messageId);
                }
            }
            catch (AMQException e)
            {
                //to maintain consistency, we revert the count
                incrementReference();
                throw new MessageCleanupException(_messageId, e);
            }
        }
        else
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Decremented ref count is now " + count + " for message id " + debugIdentity() + "\n" + Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 5));
                if (count < 0)
                {
                    Thread.dumpStack();
                }
            }
            if (count < 0)
            {
                throw new MessageCleanupException("Reference count for message id " + debugIdentity() + " has gone below 0.");
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

    /**
     * Called selectors to determin if the message has already been sent
     *
     * @return _deliveredToConsumer
     */
    public boolean getDeliveredToConsumer()
    {
        return _deliveredToConsumer;
    }


    public boolean taken(AMQQueue queue, Subscription sub)
    {
        if (_taken.getAndSet(true))
        {
            return true;
        }
        else
        {
            _takenBySubcription = sub;
            return false;
        }
    }

    public void release(AMQQueue queue)
    {
        if (_log.isTraceEnabled())
        {
            _log.trace("Releasing Message:" + debugIdentity());
        }
        _taken.set(false);
        _takenBySubcription = null;
    }

    public boolean checkToken(Object token)
    {

        if (_tokens == null)
        {
            _tokens = new HashSet<Object>();
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

    /**
     * Registers a queue to which this message is to be delivered. This is called from the exchange when it is routing
     * the message. This will be called before any content bodies have been received so that the choice of
     * AMQMessageHandle implementation can be picked based on various criteria.
     *
     * @param queue the queue
     *
     * @throws org.apache.qpid.AMQException if there is an error enqueuing the message
     */
    public void enqueue(AMQQueue queue) throws AMQException
    {
        _transientMessageData.addDestinationQueue(queue);
    }

    public void dequeue(StoreContext storeContext, AMQQueue queue) throws AMQException
    {
        _messageHandle.dequeue(storeContext, _messageId, queue);
    }

    public boolean isPersistent() throws AMQException
    {
        if (_transientMessageData != null)
        {
            return _transientMessageData.isPersistent();
        }
        else
        {
            return _messageHandle.isPersistent(getStoreContext(), _messageId);
        }
    }

    /**
     * Called to enforce the 'immediate' flag.
     *
     * @throws NoConsumersException if the message is marked for immediate delivery but has not been marked as delivered
     *                              to a consumer
     */
    public void checkDeliveredToConsumer() throws NoConsumersException
    {

        if (_immediate && !_deliveredToConsumer)
        {
            throw new NoConsumersException(this);
        }
    }

    public MessagePublishInfo getMessagePublishInfo() throws AMQException
    {
        MessagePublishInfo pb;
        if (_transientMessageData != null)
        {
            pb = _transientMessageData.getMessagePublishInfo();
        }
        else
        {
            pb = _messageHandle.getMessagePublishInfo(getStoreContext(), _messageId);
        }
        return pb;
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


    /** Called when this message is delivered to a consumer. (used to implement the 'immediate' flag functionality). */
    public void setDeliveredToConsumer()
    {
        _deliveredToConsumer = true;
    }

    private void deliver(StoreContext storeContext) throws AMQException
    {
        // we get a reference to the destination queues now so that we can clear the
        // transient message data as quickly as possible
        List<AMQQueue> destinationQueues = _transientMessageData.getDestinationQueues();
        if (_log.isDebugEnabled())
        {
            _log.debug("Delivering message " + debugIdentity() + " to " + destinationQueues);
        }
        try
        {
            // first we allow the handle to know that the message has been fully received. This is useful if it is
            // maintaining any calculated values based on content chunks
            _messageHandle.setPublishAndContentHeaderBody(storeContext, _messageId, _transientMessageData.getMessagePublishInfo(),
                                                          _transientMessageData.getContentHeaderBody());

            // we then allow the transactional context to do something with the message content
            // now that it has all been received, before we attempt delivery
            _txnContext.messageFullyReceived(isPersistent());

            _transientMessageData = null;

            for (AMQQueue q : destinationQueues)
            {
                //Increment the references to this message for each queue delivery.
                incrementReference();
                //normal deliver so add this message at the end.
                _txnContext.deliver(this, q, false);
            }
        }
        finally
        {
            destinationQueues.clear();
            // Remove refence for routing process . Reference count should now == delivered queue count
            decrementReference(storeContext);
        }
    }

/*
    public void writeDeliver(AMQProtocolSession protocolSession, int channelId, long deliveryTag, AMQShortString consumerTag)
            throws AMQException
    {
        ByteBuffer deliver = createEncodedDeliverFrame(protocolSession, channelId, deliveryTag, consumerTag);
        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      getContentHeaderBody());

        final int bodyCount = _messageHandle.getBodyCount(getStoreContext(), _messageId);
        if (bodyCount == 0)
        {
            SmallCompositeAMQDataBlock compositeBlock = new SmallCompositeAMQDataBlock(deliver,
                                                                                       contentHeader);

            protocolSession.writeFrame(compositeBlock);
        }
        else
        {

            //
            // Optimise the case where we have a single content body. In that case we create a composite block
            // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
            //
            ContentChunk cb = _messageHandle.getContentChunk(getStoreContext(), _messageId, 0);

            AMQDataBlock firstContentBody = new AMQFrame(channelId, protocolSession.getRegistry().getProtocolVersionMethodConverter().convertToBody(cb));
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(deliver, headerAndFirstContent);
            protocolSession.writeFrame(compositeBlock);

            //
            // Now start writing out the other content bodies
            //
            for (int i = 1; i < bodyCount; i++)
            {
                cb = _messageHandle.getContentChunk(getStoreContext(), _messageId, i);
                protocolSession.writeFrame(new AMQFrame(channelId, protocolSession.getRegistry().getProtocolVersionMethodConverter().convertToBody(cb)));
            }


        }


    }

    public void writeGetOk(AMQProtocolSession protocolSession, int channelId, long deliveryTag, int queueSize) throws AMQException
    {
        ByteBuffer deliver = createEncodedGetOkFrame(protocolSession, channelId, deliveryTag, queueSize);
        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      getContentHeaderBody());

        final int bodyCount = _messageHandle.getBodyCount(getStoreContext(), _messageId);
        if (bodyCount == 0)
        {
            SmallCompositeAMQDataBlock compositeBlock = new SmallCompositeAMQDataBlock(deliver,
                                                                                       contentHeader);
            protocolSession.writeFrame(compositeBlock);
        }
        else
        {

            //
            // Optimise the case where we have a single content body. In that case we create a composite block
            // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
            //
            ContentChunk cb = _messageHandle.getContentChunk(getStoreContext(), _messageId, 0);

            AMQDataBlock firstContentBody = new AMQFrame(channelId, protocolSession.getRegistry().getProtocolVersionMethodConverter().convertToBody(cb));
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(deliver, headerAndFirstContent);
            protocolSession.writeFrame(compositeBlock);

            //
            // Now start writing out the other content bodies
            //
            for (int i = 1; i < bodyCount; i++)
            {
                cb = _messageHandle.getContentChunk(getStoreContext(), _messageId, i);
                protocolSession.writeFrame(new AMQFrame(channelId, protocolSession.getRegistry().getProtocolVersionMethodConverter().convertToBody(cb)));
            }


        }


    }


    private ByteBuffer createEncodedDeliverFrame(AMQProtocolSession protocolSession, int channelId, long deliveryTag, AMQShortString consumerTag)
            throws AMQException
    {
        MessagePublishInfo pb = getMessagePublishInfo();
        AMQFrame deliverFrame = BasicDeliverBody.createAMQFrame(channelId, protocolSession.getProtocolMajorVersion(), (byte) 0, consumerTag,
                                                                deliveryTag, pb.getExchange(), _messageHandle.isRedelivered(),
                                                                pb.getRoutingKey());
        ByteBuffer buf = ByteBuffer.allocate((int) deliverFrame.getSize()); // XXX: Could cast be a problem?
        deliverFrame.writePayload(buf);
        buf.flip();
        return buf;
    }

    private ByteBuffer createEncodedGetOkFrame(AMQProtocolSession protocolSession, int channelId, long deliveryTag, int queueSize)
            throws AMQException
    {
        MessagePublishInfo pb = getMessagePublishInfo();
        AMQFrame getOkFrame = BasicGetOkBody.createAMQFrame(channelId,
                                                            protocolSession.getProtocolMajorVersion(),
                                                            protocolSession.getProtocolMinorVersion(),
                                                            deliveryTag, pb.getExchange(),
                                                            queueSize,
                                                            _messageHandle.isRedelivered(),
                                                            pb.getRoutingKey());
        ByteBuffer buf = ByteBuffer.allocate((int) getOkFrame.getSize()); // XXX: Could cast be a problem?
        getOkFrame.writePayload(buf);
        buf.flip();
        return buf;
    }

    private ByteBuffer createEncodedReturnFrame(AMQProtocolSession protocolSession, int channelId, int replyCode, AMQShortString replyText) throws AMQException
    {
        AMQFrame returnFrame = BasicReturnBody.createAMQFrame(channelId,
                                                              protocolSession.getProtocolMajorVersion(),
                                                              protocolSession.getProtocolMinorVersion(),
                                                              getMessagePublishInfo().getExchange(),
                                                              replyCode, replyText,
                                                              getMessagePublishInfo().getRoutingKey());
        ByteBuffer buf = ByteBuffer.allocate((int) returnFrame.getSize()); // XXX: Could cast be a problem?
        returnFrame.writePayload(buf);
        buf.flip();
        return buf;
    }

    public void writeReturn(AMQProtocolSession protocolSession, int channelId, int replyCode, AMQShortString replyText)
            throws AMQException
    {
        ByteBuffer returnFrame = createEncodedReturnFrame(protocolSession, channelId, replyCode, replyText);

        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      getContentHeaderBody());

        Iterator<AMQDataBlock> bodyFrameIterator = getBodyFrameIterator(protocolSession, channelId);
        //
        // Optimise the case where we have a single content body. In that case we create a composite block
        // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
        //
        if (bodyFrameIterator.hasNext())
        {
            AMQDataBlock firstContentBody = bodyFrameIterator.next();
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(returnFrame, headerAndFirstContent);
            protocolSession.writeFrame(compositeBlock);
        }
        else
        {
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(returnFrame,
                                                                             new AMQDataBlock[]{contentHeader});
            protocolSession.writeFrame(compositeBlock);
        }

        //
        // Now start writing out the other content bodies
        // TODO: MINA needs to be fixed so the the pending writes buffer is not unbounded
        //
        while (bodyFrameIterator.hasNext())
        {
            protocolSession.writeFrame(bodyFrameIterator.next());
        }
    }
*/

    public AMQMessageHandle getMessageHandle()
    {
        return _messageHandle;
    }


    public long getSize()
    {
        try
        {
            long size = getContentHeaderBody().bodySize;

            return size;
        }
        catch (AMQException e)
        {
            _log.error(e.toString(), e);
            return 0;
        }

    }


    public void restoreTransientMessageData() throws AMQException
    {
        TransientMessageData transientMessageData = new TransientMessageData();
        transientMessageData.setMessagePublishInfo(getMessagePublishInfo());
        transientMessageData.setContentHeaderBody(getContentHeaderBody());
        transientMessageData.addBodyLength(getContentHeaderBody().getSize());
        _transientMessageData = transientMessageData;
    }


    public void clearTransientMessageData()
    {
        _transientMessageData = null;
    }


    public String toString()
    {
        return "Message[" + debugIdentity() + "]: " + _messageId + "; ref count: " + _referenceCount + "; taken : " +
               _taken + " by :" + _takenBySubcription;

//        return "Message[" + debugIdentity() + "]: " + _messageId + "; ref count: " + _referenceCount + "; taken for queues: " +
//               _takenMap.toString() + " by Subs:" + _takenBySubcriptionMap.toString();
    }

    public Subscription getDeliveredSubscription(AMQQueue queue)
    {
        return _takenBySubcription;
    }

    public void reject(Subscription subscription)
    {
        if (subscription != null)
        {
            if (_rejectedBy == null)
            {
                _rejectedBy = new HashSet<Subscription>();
            }

            _rejectedBy.add(subscription);
        }
        else
        {
            _log.warn("Requesting rejection by null subscriber:" + debugIdentity());
        }
    }

    public boolean isRejectedBy(Subscription subscription)
    {
        boolean rejected = _rejectedBy != null;

        if (rejected)  // We have subscriptions that rejected this message
        {
            return _rejectedBy.contains(subscription);
        }
        else // This messasge hasn't been rejected yet.
        {
            return rejected;
        }
    }
}
