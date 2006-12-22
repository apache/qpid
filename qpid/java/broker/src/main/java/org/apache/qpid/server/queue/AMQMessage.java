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
import org.apache.qpid.server.txn.TxnBuffer;
import org.apache.qpid.server.message.MessageDecorator;
import org.apache.qpid.server.message.jms.JMSMessage;
import org.apache.qpid.AMQException;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combines the information that make up a deliverable message into a more manageable form.
 */
public class AMQMessage
{
    public static final String JMS_MESSAGE = "jms.message";

    private final Set<Object> _tokens = new HashSet<Object>();

    private AMQProtocolSession _publisher;

    private final BasicPublishBody _publishBody;

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

    /**
     * For non transactional publishes, a message can be stored as
     * soon as it is complete. For transactional messages it doesnt
     * need to be stored until the transaction is committed.
     */
    private boolean _storeWhenComplete;

    /**
     * TxnBuffer for transactionally published messages
     */
    private TxnBuffer _txnBuffer;

    /**
     * Flag to indicate whether message has been delivered to a
     * consumer. Used in implementing return functionality for
     * messages published with the 'immediate' flag.
     */
    private boolean _deliveredToConsumer;
    private ConcurrentHashMap<String, MessageDecorator> _decodedMessages;
    private AtomicBoolean _taken;


    public AMQMessage(MessageStore messageStore, BasicPublishBody publishBody)
    {
        this(messageStore, publishBody, true);
    }

    public AMQMessage(MessageStore messageStore, BasicPublishBody publishBody, boolean storeWhenComplete)
    {
        _messageId = messageStore.getNewMessageId();
        _publishBody = publishBody;
        _store = messageStore;
        _contentBodies = new LinkedList<ContentBody>();
        _decodedMessages = new ConcurrentHashMap<String, MessageDecorator>();
        _storeWhenComplete = storeWhenComplete;
        _taken = new AtomicBoolean(false);
    }

    public AMQMessage(MessageStore store, long messageId, BasicPublishBody publishBody,
                      ContentHeaderBody contentHeaderBody, List<ContentBody> contentBodies)
            throws AMQException

    {
        _publishBody = publishBody;
        _contentHeaderBody = contentHeaderBody;
        _contentBodies = contentBodies;
        _decodedMessages = new ConcurrentHashMap<String, MessageDecorator>();
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

    public void storeMessage() throws AMQException
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

        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        allFrames[0] = BasicDeliverBody.createAMQFrame(channel,
        	(byte)8, (byte)0,	// AMQP version (major, minor)
            consumerTag,	// consumerTag
        	deliveryTag,	// deliveryTag
            getExchangeName(),	// exchange
            _redelivered,	// redelivered
            getRoutingKey()	// routingKey
            );
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
        if (_storeWhenComplete && isAllContentReceived())
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
        if (_storeWhenComplete && isAllContentReceived())
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

    public void setRedelivered(boolean redelivered)
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
                _store.removeMessage(_messageId);
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
        if (isPersistent() && queue.isDurable())
        {
            _store.enqueueMessage(queue.getName(), _messageId);
        }
    }

    public void dequeue(AMQQueue queue) throws AMQException
    {
        //only record associations where both queue and message will survive
        //a restart, so only need to remove association if this is the case
        if (isPersistent() && queue.isDurable())
        {
            _store.dequeueMessage(queue.getName(), _messageId);
        }
    }

    public boolean isPersistent() throws AMQException
    {
        if (_contentHeaderBody == null)
        {
            throw new AMQException("Cannot determine delivery mode of message. Content header not found.");
        }

        //todo remove literal values to a constant file such as AMQConstants in common
        return _contentHeaderBody.properties instanceof BasicContentHeaderProperties
               && ((BasicContentHeaderProperties) _contentHeaderBody.properties).getDeliveryMode() == 2;
    }

    public void setTxnBuffer(TxnBuffer buffer)
    {
        _txnBuffer = buffer;
    }

    public TxnBuffer getTxnBuffer()
    {
        return _txnBuffer;
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
            throw new NoConsumersException(_publishBody, _contentHeaderBody, _contentBodies);
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


    public MessageDecorator getDecodedMessage(String type)
    {
        MessageDecorator msgtype = null;

        if (_decodedMessages != null)
        {
            msgtype = _decodedMessages.get(type);

            if (msgtype == null)
            {
                msgtype = decorateMessage(type);
            }
        }

        return msgtype;
    }

    private MessageDecorator decorateMessage(String type)
    {
        MessageDecorator msgdec = null;

        if (type.equals(JMS_MESSAGE))
        {
            msgdec = new JMSMessage(this);
        }

        if (msgdec != null)
        {
            _decodedMessages.put(type, msgdec);
        }

        return msgdec;
    }

    public boolean taken()
    {
        return _taken.getAndSet(true);
    }

    public void release()
    {
        _taken.set(false);
    }
}
