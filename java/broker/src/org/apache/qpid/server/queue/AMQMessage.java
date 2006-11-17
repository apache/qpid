/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.TransactionalContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Combines the information that make up a deliverable message into a more manageable form.
 */
public class AMQMessage
{
    /**
     * Used in clustering
     */
    private final Set<Object> _tokens = new HashSet<Object>();

    /**
     * Used in clustering
     * TODO need to get rid of this
     */
    private AMQProtocolSession _publisher;

    private long _messageId;

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    private AMQMessageHandle _messageHandle;

    /**
     * Stored temporarily until the header has been received at which point it is used when
     * constructing the handle
     */
    private BasicPublishBody _publishBody;

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
                ContentBody cb = _messageHandle.getContentBody(++_index);
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
                return _messageHandle.getContentBody(++_index);
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

    public AMQMessage(long messageId, BasicPublishBody publishBody, TransactionalContext txnContext)
    {
        _messageId = messageId;
        _txnContext = txnContext;
        _publishBody = publishBody;
    }

    protected AMQMessage(AMQMessage msg) throws AMQException
    {
        _publisher = msg._publisher;
        _messageId = msg._messageId;
        _messageHandle = msg._messageHandle;
        _txnContext = msg._txnContext;
        _deliveredToConsumer = msg._deliveredToConsumer;
    }

    public void storeMessage() throws AMQException
    {
        /*if (isPersistent())
        {
            _store.put(this);
        } */
    }

    public Iterator<AMQDataBlock> getBodyFrameIterator(int channel)
    {
        return new BodyFrameIterator(channel);
    }

    public Iterator<ContentBody> getContentBodyIterator()
    {
        return new BodyContentIterator();
    }

    /*public CompositeAMQDataBlock getDataBlock(ByteBuffer encodedDeliverBody, int channel)
    {
        AMQFrame[] allFrames = new AMQFrame[1 + _contentBodies.size()];

        allFrames[0] = ContentHeaderBody.createAMQFrame(channel, _contentHeaderBody);
        for (int i = 1; i < allFrames.length; i++)
        {
            allFrames[i] = ContentBody.createAMQFrame(channel, _contentBodies.get(i - 1));
        }
        return new CompositeAMQDataBlock(encodedDeliverBody, allFrames);
    }

    public CompositeAMQDataBlock getDataBlock(int channel, String consumerTag, long deliveryTag)
    {
        AMQFrame[] allFrames = new AMQFrame[2 + _contentBodies.size()];

        allFrames[0] = BasicDeliverBody.createAMQFrame(channel, consumerTag, deliveryTag, _redelivered,
                                                       getExchangeName(), getRoutingKey());
        allFrames[1] = ContentHeaderBody.createAMQFrame(channel, _contentHeaderBody);
        for (int i = 2; i < allFrames.length; i++)
        {
            allFrames[i] = ContentBody.createAMQFrame(channel, _contentBodies.get(i - 2));
        }
        return new CompositeAMQDataBlock(allFrames);
    }

    public List<AMQBody> getPayload()
    {
        List<AMQBody> payload = new ArrayList<AMQBody>(2 + _contentBodies.size());
        payload.add(_publishBody);
        payload.add(_contentHeaderBody);
        payload.addAll(_contentBodies);
        return payload;
    }

    public BasicPublishBody getPublishBody()
    {
        return _publishBody;
    } */

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        return _messageHandle.getContentHeaderBody();
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public void routingComplete(MessageStore store, MessageHandleFactory factory) throws AMQException
    {
        final boolean persistent = isPersistent();
        _messageId = store.getNewMessageId();
        _messageHandle = factory.createMessageHandle(_messageId, store, persistent);
        if (persistent)
        {
            _txnContext.beginTranIfNecessary();
        }

        if (_contentHeaderBody.bodySize == 0)
        {
            deliver();
        }
    }

    public boolean addContentBodyFrame(ContentBody contentBody) throws AMQException
    {
        _bodyLengthReceived += contentBody.getSize();
        _messageHandle.addContentBodyFrame(contentBody);
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
        return _bodyLengthReceived == _messageHandle.getBodySize();
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
        /*if (_referenceCount.decrementAndGet() == 0)
        {
            try
            {
                _store.removeMessage(_messageId);
            }
            catch (AMQException e)
            {
                //to maintain consistency, we revert the count
                incrementReference();
                throw new MessageCleanupException(_messageId, e);
            }
        } */
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

    public void enqueue(AMQQueue queue) throws AMQException
    {
        //if the message is not persistent or the queue is not durable
        //we will not need to recover the association and so do not
        //need to record it
        /*if (isPersistent() && queue.isDurable())
        {
            _store.enqueueMessage(queue.getName(), _messageId);
        } */
    }

    public void dequeue(AMQQueue queue) throws AMQException
    {
        //only record associations where both queue and message will survive
        //a restart, so only need to remove association if this is the case
        /*if (isPersistent() && queue.isDurable())
        {
            _store.dequeueMessage(queue.getName(), _messageId);
        } */
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
            return _messageHandle.isPersistent();
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
            pb = _messageHandle.getPublishBody();
        }
        return pb;
    }

    /**
     * Called when this message is delivered to a consumer. (used to
     * implement the 'immediate' flag functionality).
     */
    public void setDeliveredToConsumer()
    {
        _deliveredToConsumer = true;
    }

    /**
     * Registers a queue to which this message is to be delivered. This is
     * called from the exchange when it is routing the message. This will be called before any content bodies have
     * been received so that the choice of AMQMessageHandle implementation can be picked based on various criteria.
     *
     * @param queue the queue
     */
    public void registerQueue(AMQQueue queue)
    {
        _destinationQueues.add(queue);
    }

    private void deliver() throws AMQException
    {
        // first we allow the handle to know that the message has been fully received. This is useful if it is
        // maintaining any calculated values based on content chunks
        _messageHandle.setPublishAndContentHeaderBody(_publishBody, _contentHeaderBody);
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

    private ByteBuffer createEncodedReturnFrame(int channelId, int replyCode, String replyText)
    {
        AMQFrame returnFrame = BasicReturnBody.createAMQFrame(channelId, replyCode, replyText, _publishBody.exchange,
                                                              _publishBody.routingKey);
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
