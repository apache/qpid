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
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Combines the information that make up a deliverable message into a more manageable form.
 */
public class AMQMessage
{
    private static final Logger _log = Logger.getLogger(AMQMessage.class);

    /**
     * Used in clustering
     */
    private final Set<Object> _tokens = new HashSet<Object>();

    /**
     * Only use in clustering - should ideally be removed?
     */
    private AMQProtocolSession _publisher;

    private final long _messageId;

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    private AMQMessageHandle _messageHandle;

    /**
     * Stored temporarily until the header has been received at which point it is used when
     * constructing the handle
     */
    private BasicPublishBody _publishBody;

    /**
     * Also stored temporarily.
     */
    private ContentHeaderBody _contentHeaderBody;

    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    private final TransactionalContext _txnContext;

    /**
     * Flag to indicate whether message has been delivered to a
     * consumer. Used in implementing return functionality for
     * messages published with the 'immediate' flag.
     */
    private boolean _deliveredToConsumer;

    /**
     * This is stored during routing, to know the queues to which this message should immediately be
     * delivered. It is <b>cleared after delivery has been attempted</b>. Any persistent record of destinations is done
     * by the message handle.
     */
    private List<AMQQueue> _destinationQueues = new LinkedList<AMQQueue>();

    /**
     * Used to iterate through all the body frames associated with this message. Will not
     * keep all the data in memory therefore is memory-efficient.
     */
    private class BodyFrameIterator implements Iterator<AMQDataBlock>
    {
        private int _channel;

        private int _index = -1;

        private BodyFrameIterator(int channel)
        {
            _channel = channel;
        }

        public boolean hasNext()
        {
            return _index < _messageHandle.getBodyCount() - 1;
        }

        public AMQDataBlock next()
        {
            try
            {
                ContentBody cb = _messageHandle.getContentBody(_messageId, ++_index);
                return ContentBody.createAMQFrame(_channel, cb);
            }
            catch (AMQException e)
            {
                // have no choice but to throw a runtime exception
                throw new RuntimeException("Error getting content body: " + e, e);
            }

        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    private class BodyContentIterator implements Iterator<ContentBody>
    {

        private int _index = -1;

        public boolean hasNext()
        {
            return _index < _messageHandle.getBodyCount() - 1;
        }

        public ContentBody next()
        {
            try
            {
                return _messageHandle.getContentBody(_messageId, ++_index);
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

    public AMQMessage(long messageId, BasicPublishBody publishBody,
                      TransactionalContext txnContext)
    {
        _messageId = messageId;
        _txnContext = txnContext;
        _publishBody = publishBody;
        if (_log.isDebugEnabled())
        {
            _log.debug("Message created with id " + messageId);
        }
    }

    /**
     * Used in testing only. This allows the passing of the content header immediately
     * on construction.
     * @param messageId
     * @param publishBody
     * @param txnContext
     * @param contentHeader
     */
    public AMQMessage(long messageId, BasicPublishBody publishBody,
                      TransactionalContext txnContext, ContentHeaderBody contentHeader) throws AMQException
    {
        this(messageId, publishBody, txnContext);
        setContentHeaderBody(contentHeader);
    }

    /**
     * Used in testing only. This allows the passing of the content header and some body fragments on
     * construction.
     * @param messageId
     * @param publishBody
     * @param txnContext
     * @param contentHeader
     * @param destinationQueues
     * @param contentBodies
     * @throws AMQException
     */
    public AMQMessage(long messageId, BasicPublishBody publishBody,
                      TransactionalContext txnContext,
                      ContentHeaderBody contentHeader, List<AMQQueue> destinationQueues,
                      List<ContentBody> contentBodies, MessageStore messageStore,
                      MessageHandleFactory messageHandleFactory) throws AMQException
    {
        this(messageId, publishBody, txnContext, contentHeader);
        _destinationQueues = destinationQueues;
        routingComplete(messageStore, messageHandleFactory);
        for (ContentBody cb : contentBodies)
        {
            addContentBodyFrame(cb);
        }
    }

    protected AMQMessage(AMQMessage msg) throws AMQException
    {
        _messageId = msg._messageId;
        _messageHandle = msg._messageHandle;
        _txnContext = msg._txnContext;
        _deliveredToConsumer = msg._deliveredToConsumer;
    }

    public Iterator<AMQDataBlock> getBodyFrameIterator(int channel)
    {
        return new BodyFrameIterator(channel);
    }

    public Iterator<ContentBody> getContentBodyIterator()
    {
        return new BodyContentIterator();
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        if (_contentHeaderBody != null)
        {
            return _contentHeaderBody;
        }
        else
        {
            return _messageHandle.getContentHeaderBody(_messageId);
        }
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public void routingComplete(MessageStore store, MessageHandleFactory factory) throws AMQException
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
            _messageHandle.enqueue(_messageId, q);
        }

        if (_contentHeaderBody.bodySize == 0)
        {
            deliver();
        }
    }

    public boolean addContentBodyFrame(ContentBody contentBody) throws AMQException
    {
        _bodyLengthReceived += contentBody.getSize();
        _messageHandle.addContentBodyFrame(_messageId, contentBody);
        if (isAllContentReceived())
        {
            deliver();
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean isAllContentReceived() throws AMQException
    {
        return _bodyLengthReceived == _contentHeaderBody.bodySize;
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
     *
     * @throws MessageCleanupException when an attempt was made to remove the message from the message store and that
     *                                 failed
     */
    public void decrementReference() throws MessageCleanupException
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
                if (_messageHandle != null)
                {
                    _messageHandle.removeMessage(_messageId);
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
                _log.debug("Ref count is now " + _referenceCount + " for message id " + _messageId);
                if (_referenceCount.get() < 0)
                {
                    Thread.dumpStack();
                }
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
     * Registers a queue to which this message is to be delivered. This is
     * called from the exchange when it is routing the message. This will be called before any content bodies have
     * been received so that the choice of AMQMessageHandle implementation can be picked based on various criteria.
     *
     * @param queue the queue
     * @throws org.apache.qpid.AMQException if there is an error enqueuing the message
     */
    public void enqueue(AMQQueue queue) throws AMQException
    {
        _destinationQueues.add(queue);
    }

    public void dequeue(AMQQueue queue) throws AMQException
    {
        _messageHandle.dequeue(_messageId, queue);
    }

    public boolean isPersistent() throws AMQException
    {
        if (_contentHeaderBody != null)
        {
            //todo remove literal values to a constant file such as AMQConstants in common
            return _contentHeaderBody.properties instanceof BasicContentHeaderProperties &&
                 ((BasicContentHeaderProperties) _contentHeaderBody.properties).getDeliveryMode() == 2;
        }
        else
        {
            return _messageHandle.isPersistent(_messageId);
        }
    }

    /**
     * Called to enforce the 'immediate' flag.
     *
     * @throws NoConsumersException if the message is marked for
     *                              immediate delivery but has not been marked as delivered to a
     *                              consumer
     */
    public void checkDeliveredToConsumer() throws NoConsumersException, AMQException
    {
        BasicPublishBody pb = getPublishBody();
        if (pb.immediate && !_deliveredToConsumer)
        {
            throw new NoConsumersException(this);
        }
    }

    public BasicPublishBody getPublishBody() throws AMQException
    {
        BasicPublishBody pb;
        if (_publishBody != null)
        {
            pb = _publishBody;
        }
        else
        {
            pb = _messageHandle.getPublishBody(_messageId);
        }
        return pb;
    }

    public boolean isRedelivered()
    {
        return _messageHandle.isRedelivered();
    }

    /**
     * Called when this message is delivered to a consumer. (used to
     * implement the 'immediate' flag functionality).
     */
    public void setDeliveredToConsumer()
    {
        _deliveredToConsumer = true;
    }

    private void deliver() throws AMQException
    {
        // first we allow the handle to know that the message has been fully received. This is useful if it is
        // maintaining any calculated values based on content chunks
        try
        {
            _messageHandle.setPublishAndContentHeaderBody(_messageId, _publishBody, _contentHeaderBody);
            _publishBody = null;
            _contentHeaderBody = null;

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
            _destinationQueues = null;
            decrementReference();
        }
    }

    public void writeDeliver(AMQProtocolSession protocolSession, int channelId, long deliveryTag, String consumerTag)
            throws AMQException
    {
        ByteBuffer deliver = createEncodedDeliverFrame(channelId, deliveryTag, consumerTag);
        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      getContentHeaderBody());

        Iterator<AMQDataBlock> bodyFrameIterator = getBodyFrameIterator(channelId);
        //
        // Optimise the case where we have a single content body. In that case we create a composite block
        // so that we can writeDeliver out the deliver, header and body with a single network writeDeliver.
        //
        if (bodyFrameIterator.hasNext())
        {
            AMQDataBlock firstContentBody = bodyFrameIterator.next();
            AMQDataBlock[] headerAndFirstContent = new AMQDataBlock[]{contentHeader, firstContentBody};
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(deliver, headerAndFirstContent);
            protocolSession.writeFrame(compositeBlock);
        }
        else
        {
            CompositeAMQDataBlock compositeBlock = new CompositeAMQDataBlock(deliver,
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

    private ByteBuffer createEncodedDeliverFrame(int channelId, long deliveryTag, String consumerTag)
            throws AMQException
    {
        BasicPublishBody pb = getPublishBody();
        AMQFrame deliverFrame = BasicDeliverBody.createAMQFrame(channelId, consumerTag,
                                                                deliveryTag, false, pb.exchange,
                                                                pb.routingKey);
        ByteBuffer buf = ByteBuffer.allocate((int) deliverFrame.getSize()); // XXX: Could cast be a problem?
        deliverFrame.writePayload(buf);
        buf.flip();
        return buf;
    }

    private ByteBuffer createEncodedReturnFrame(int channelId, int replyCode, String replyText) throws AMQException
    {
        AMQFrame returnFrame = BasicReturnBody.createAMQFrame(channelId, replyCode, replyText, getPublishBody().exchange,
                                                              getPublishBody().routingKey);
        ByteBuffer buf = ByteBuffer.allocate((int) returnFrame.getSize()); // XXX: Could cast be a problem?
        returnFrame.writePayload(buf);
        buf.flip();
        return buf;
    }

    public void writeReturn(AMQProtocolSession protocolSession, int channelId, int replyCode, String replyText)
            throws AMQException
    {
        ByteBuffer returnFrame = createEncodedReturnFrame(channelId, replyCode, replyText);

        AMQDataBlock contentHeader = ContentHeaderBody.createAMQFrame(channelId,
                                                                      getContentHeaderBody());

        Iterator<AMQDataBlock> bodyFrameIterator = getBodyFrameIterator(channelId);
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
}
