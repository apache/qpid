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
import org.apache.qpid.framing.*;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.AMQException;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Combines the information that make up a deliverable message into a more manageable form.
 */
public class AMQMessage
{
    private final Set<Object> _tokens = new HashSet<Object>();

    private AMQProtocolSession _publisher;

    private final BasicPublishBody  _publishBody;

    private ContentHeaderBody _contentHeaderBody;

    private List<ContentBody> _contentBodies;

    private boolean _redelivered;

    private final long _messageId;

    private final AtomicInteger _referenceCount = new AtomicInteger(1);

    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * The message store in which this message is contained.
     */
    private transient final MessageStore _store;

    public AMQMessage(MessageStore messageStore, BasicPublishBody publishBody)
    {
        _messageId = messageStore.getNewMessageId();
        _publishBody = publishBody;
        _store = messageStore;
        _contentBodies = new LinkedList<ContentBody>();
    }

    public AMQMessage(MessageStore store, long messageId, BasicPublishBody publishBody,
                      ContentHeaderBody contentHeaderBody, List<ContentBody> contentBodies)
            throws AMQException
    {
        _publishBody = publishBody;
        _contentHeaderBody = contentHeaderBody;
        _contentBodies = contentBodies;
        _messageId = messageId;
        _store = store;
        storeMessage();
    }

    public AMQMessage(MessageStore store, BasicPublishBody publishBody,
                      ContentHeaderBody contentHeaderBody, List<ContentBody> contentBodies)
            throws AMQException
    {
        this(store, store.getNewMessageId(), publishBody, contentHeaderBody, contentBodies);        
    }

    protected AMQMessage(AMQMessage msg) throws AMQException
    {
        this(msg._store, msg._messageId, msg._publishBody, msg._contentHeaderBody, msg._contentBodies);
    }

    private void storeMessage() throws AMQException
    {
        if (isPersistent())
        {
            _store.put(this);
        }
    }

    public CompositeAMQDataBlock getDataBlock(ByteBuffer encodedDeliverBody, int channel)
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
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody) throws AMQException
    {
        _contentHeaderBody = contentHeaderBody;
        if (isAllContentReceived())
        {
            storeMessage();
        }
    }

    public List<ContentBody> getContentBodies()
    {
        return _contentBodies;
    }

    public void setContentBodies(List<ContentBody> contentBodies)
    {
        _contentBodies = contentBodies;
    }

    public void addContentBodyFrame(ContentBody contentBody) throws AMQException
    {
        _contentBodies.add(contentBody);
        _bodyLengthReceived += contentBody.getSize();
        if (isAllContentReceived())
        {
            storeMessage();
        }
    }

    public boolean isAllContentReceived()
    {
        return _bodyLengthReceived == _contentHeaderBody.bodySize;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }

    String getExchangeName()
    {
        return _publishBody.exchange;
    }

    String getRoutingKey()
    {
        return _publishBody.routingKey;
    }

    boolean isImmediate()
    {
        return _publishBody.immediate;
    }

    NoConsumersException getNoConsumersException(String queue)
    {
        return new NoConsumersException(queue, _publishBody, _contentHeaderBody, _contentBodies);
    }

    void setRedelivered(boolean redelivered)
    {
        _redelivered = redelivered;
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
     */
    public void decrementReference() throws AMQException
    {
        // note that the operation of decrementing the reference count and then removing the message does not
        // have to be atomic since the ref count starts at 1 and the exchange itself decrements that after
        // the message has been passed to all queues. i.e. we are
        // not relying on the all the increments having taken place before the delivery manager decrements.
        if (_referenceCount.decrementAndGet() == 0)
        {
            _store.removeMessage(_messageId);
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
        if(_tokens.contains(token))
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
        if(isPersistent() && queue.isDurable())
        {
            _store.enqueueMessage(queue.getName(), _messageId);
        }
    }

    public void dequeue(AMQQueue queue) throws AMQException
    {
        //only record associations where both queue and message will survive
        //a restart, so only need to remove association if this is the case
        if(isPersistent() && queue.isDurable())
        {
            _store.dequeueMessage(queue.getName(), _messageId);
        }
    }

    public boolean isPersistent() throws AMQException
    {
        if(_contentHeaderBody == null)
        {
            throw new AMQException("Cannot determine delivery mode of message. Content header not found.");
        }

        //todo remove literal values to a constant file such as AMQConstants in common
        return _contentHeaderBody.properties instanceof BasicContentHeaderProperties
                &&((BasicContentHeaderProperties) _contentHeaderBody.properties).getDeliveryMode() == 2;
    }
}
