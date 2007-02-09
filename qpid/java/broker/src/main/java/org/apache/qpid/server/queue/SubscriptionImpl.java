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
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.util.ConcurrentLinkedQueueAtomicSize;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.protocol.AMQProtocolSession;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulation of a supscription to a queue. <p/> Ties together the protocol session of a subscriber, the consumer tag
 * that was given out by the broker and the channel id. <p/>
 */
public class SubscriptionImpl implements Subscription
{
    private static final Logger _logger = Logger.getLogger(SubscriptionImpl.class);

    public final AMQChannel channel;

    public final AMQProtocolSession protocolSession;

    public final String consumerTag;

    private final Object sessionKey;

    private Queue<AMQMessage> _messages;

    private Queue<AMQMessage> _resendQueue;

    private final boolean _noLocal;

    /** True if messages need to be acknowledged */
    private final boolean _acks;
    private FilterManager _filters;
    private final boolean _isBrowser;
    private final Boolean _autoClose;
    private boolean _closed = false;

    private AMQQueue _queue;
    private final AtomicBoolean _resending = new AtomicBoolean(false);

    public static class Factory implements SubscriptionFactory
    {
        public Subscription createSubscription(int channel, AMQProtocolSession protocolSession, String consumerTag,
                                               boolean acks, FieldTable filters, boolean noLocal,
                                               AMQQueue queue) throws AMQException
        {
            return new SubscriptionImpl(channel, protocolSession, consumerTag, acks, filters, noLocal, queue);
        }

        public SubscriptionImpl createSubscription(int channel, AMQProtocolSession protocolSession, String consumerTag)
                throws AMQException
        {
            return new SubscriptionImpl(channel, protocolSession, consumerTag, false, null, false, null);
        }
    }

    public SubscriptionImpl(int channelId, AMQProtocolSession protocolSession,
                            String consumerTag, boolean acks, AMQQueue queue)
            throws AMQException
    {
        this(channelId, protocolSession, consumerTag, acks, null, false, queue);
    }

    public SubscriptionImpl(int channelId, AMQProtocolSession protocolSession,
                            String consumerTag, boolean acks, FieldTable filters, boolean noLocal,
                            AMQQueue queue)
            throws AMQException
    {
        AMQChannel channel = protocolSession.getChannel(channelId);
        if (channel == null)
        {
            throw new NullPointerException("channel not found in protocol session");
        }

        this.channel = channel;
        this.protocolSession = protocolSession;
        this.consumerTag = consumerTag;
        sessionKey = protocolSession.getKey();
        _acks = acks;
        _noLocal = noLocal;
        _queue = queue;

        _filters = FilterManagerFactory.createManager(filters);


        if (_filters != null)
        {
            Object isBrowser = filters.get(AMQPFilterTypes.NO_CONSUME.getValue());
            if (isBrowser != null)
            {
                _isBrowser = (Boolean) isBrowser;
            }
            else
            {
                _isBrowser = false;
            }
        }
        else
        {
            _isBrowser = false;
        }


        if (_filters != null)
        {
            Object autoClose = filters.get(AMQPFilterTypes.AUTO_CLOSE.getValue());
            if (autoClose != null)
            {
                _autoClose = (Boolean) autoClose;
            }
            else
            {
                _autoClose = false;
            }
        }
        else
        {
            _autoClose = false;
        }


        if (_filters != null)
        {
            _messages = new ConcurrentLinkedQueueAtomicSize<AMQMessage>();


        }
        else
        {
            // Reference the DeliveryManager
            _messages = null;
        }
    }


    public SubscriptionImpl(int channel, AMQProtocolSession protocolSession,
                            String consumerTag)
            throws AMQException
    {
        this(channel, protocolSession, consumerTag, false, null);
    }

    public boolean equals(Object o)
    {
        return (o instanceof SubscriptionImpl) && equals((SubscriptionImpl) o);
    }

    /** Equality holds if the session matches and the channel and consumer tag are the same. */
    private boolean equals(SubscriptionImpl psc)
    {
        return sessionKey.equals(psc.sessionKey)
               && psc.channel == channel
               && psc.consumerTag.equals(consumerTag);
    }

    public int hashCode()
    {
        return sessionKey.hashCode();
    }

    public String toString()
    {
        return "[channel=" + channel + ", consumerTag=" + consumerTag + ", session=" + protocolSession.getKey() + "]";
    }

    /**
     * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
     * thread safe.
     *
     * @param msg
     * @param queue
     *
     * @throws AMQException
     */
    public void send(AMQMessage msg, AMQQueue queue) throws FailedDequeueException
    {
        if (msg != null)
        {
            if (_isBrowser)
            {
                sendToBrowser(msg, queue);
            }
            else
            {
                sendToConsumer(msg, queue);
            }
        }
        else
        {
            _logger.error("Attempt to send Null message", new NullPointerException());
        }
    }

    private void sendToBrowser(AMQMessage msg, AMQQueue queue) throws FailedDequeueException
    {
        // We don't decrement the reference here as we don't want to consume the message
        // but we do want to send it to the client.

        synchronized (channel)
        {
            long deliveryTag = channel.getNextDeliveryTag();

            // We don't need to add the message to the unacknowledgedMap as we don't need to know if the client
            // received the message. If it is lost in transit that is not important.
            if (_acks)
            {
                channel.addUnacknowledgedBrowsedMessage(msg, deliveryTag, consumerTag, queue);
            }
            ByteBuffer deliver = createEncodedDeliverFrame(deliveryTag, msg.getRoutingKey(), msg.getExchangeName(),msg.isRedelivered());
            AMQDataBlock frame = msg.getDataBlock(deliver, channel.getChannelId());

            //fixme what is wrong with this?
            //AMQDataBlock frame = msg.getDataBlock(channel.getChannelId(),consumerTag,deliveryTag);

            protocolSession.writeFrame(frame);
        }
    }

    private void sendToConsumer(AMQMessage msg, AMQQueue queue) throws FailedDequeueException
    {
        try
        {
            // if we do not need to wait for client acknowledgements
            // we can decrement the reference count immediately.

            // By doing this _before_ the send we ensure that it
            // doesn't get sent if it can't be dequeued, preventing
            // duplicate delivery on recovery.

            // The send may of course still fail, in which case, as
            // the message is unacked, it will be lost.
            if (!_acks)
            {
                queue.dequeue(msg);
            }
            synchronized (channel)
            {
                long deliveryTag = channel.getNextDeliveryTag();

                if (_acks)
                {
                    channel.addUnacknowledgedMessage(msg, deliveryTag, consumerTag, queue);
                }

                ByteBuffer deliver = createEncodedDeliverFrame(deliveryTag, msg.getRoutingKey(), msg.getExchangeName(),msg.isRedelivered());
                AMQDataBlock frame = msg.getDataBlock(deliver, channel.getChannelId());

                //fixme what is wrong with this?
                //AMQDataBlock frame = msg.getDataBlock(channel.getChannelId(),consumerTag,deliveryTag);                

                protocolSession.writeFrame(frame);
            }
        }
        finally
        {
            msg.setDeliveredToConsumer();
        }
    }

    public boolean isSuspended()
    {
        return channel.isSuspended() && !_resending.get();
    }

    /**
     * Callback indicating that a queue has been deleted.
     *
     * @param queue
     */
    public void queueDeleted(AMQQueue queue)
    {
        channel.queueDeleted(queue);
    }

    public boolean hasFilters()
    {
        return _filters != null;
    }

    public boolean hasInterest(AMQMessage msg)
    {
        if (_noLocal)
        {
            // We don't want local messages so check to see if message is one we sent
            if (protocolSession.getClientProperties().get(ClientProperties.instance.toString()).equals(
                    msg.getPublisher().getClientProperties().get(ClientProperties.instance.toString())))
            {
                if (_logger.isTraceEnabled())
                {
                    _logger.trace("(" + System.identityHashCode(this) + ") has no interest as it is a local message(" +
                                  System.identityHashCode(msg) + ")");
                }
                return false;
            }
            else // if not then filter the message.
            {
                if (_logger.isTraceEnabled())
                {
                    _logger.trace("(" + System.identityHashCode(this) + ") local message(" + System.identityHashCode(msg) +
                                  ") but not ours so filtering");
                }
                return checkFilters(msg);
            }
        }
        else
        {
            if (_logger.isTraceEnabled())
            {
                _logger.trace("(" + System.identityHashCode(this) + ") checking filters for message (" + System.identityHashCode(msg));
            }
            return checkFilters(msg);
        }
    }

    private boolean checkFilters(AMQMessage msg)
    {
        if (_filters != null)
        {
            if (_logger.isTraceEnabled())
            {
                _logger.trace("(" + System.identityHashCode(this) + ") has filters.");
            }
            return _filters.allAllow(msg);
        }
        else
        {
            if (_logger.isTraceEnabled())
            {
                _logger.trace("(" + System.identityHashCode(this) + ") has no filters");
            }

            return true;
        }
    }

    public Queue<AMQMessage> getPreDeliveryQueue()
    {
        return _messages;
    }

    public void enqueueForPreDelivery(AMQMessage msg)
    {
        if (_messages != null)
        {
            _messages.offer(msg);
        }
    }

    public boolean isAutoClose()
    {
        return _autoClose;
    }

    public void close()
    {
        _logger.info("Closing subscription:" + this);

        if (_resendQueue != null && !_resendQueue.isEmpty())
        {
            requeue();
        }

        //remove references in PDQ
        if (_messages != null)
        {
            _messages.clear();
        }

        if (_autoClose && !_closed)
        {
            _logger.info("Closing autoclose subscription:" + this);
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            protocolSession.writeFrame(BasicCancelOkBody.createAMQFrame(channel.getChannelId(),
                                                                        (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                        consumerTag    // consumerTag
            ));
            _closed = true;
        }
    }

    private void requeue()
    {

        if (_queue != null)
        {
            _logger.trace("Requeuing :" + _resendQueue.size() + " messages");

            //Take  control over to this thread for delivering messages from the Async Delivery.
            setResending(true);

            while (!_resendQueue.isEmpty())
            {
                AMQMessage resent = _resendQueue.poll();

                resent.setTxnBuffer(null);

                resent.release();

                try
                {
                    _queue.deliver(resent);
                }
                catch (AMQException e)
                {
                    _logger.error("Unable to re-deliver messages", e);
                }
            }

            setResending(false);

            if (!_resendQueue.isEmpty())
            {
                _logger.error("[MESSAGES LOST]Unable to re-deliver messages as queue is null.");
            }

            _queue.setQueueHasContent(false, this);
        }
        else
        {
            if (!_resendQueue.isEmpty())
            {
                _logger.error("Unable to re-deliver messages as queue is null.");
            }
        }

        // Clear the messages
        _resendQueue = null;
    }

    private void setResending(boolean resending)
    {
        synchronized (_resending)
        {
            _resending.set(resending);
        }
    }

    public boolean isBrowser()
    {
        return _isBrowser;
    }

    public Queue<AMQMessage> getResendQueue()
    {
        if (_resendQueue == null)
        {
            _resendQueue = new ConcurrentLinkedQueueAtomicSize<AMQMessage>();
        }
        return _resendQueue;
    }


    public Queue<AMQMessage> getNextQueue(Queue<AMQMessage> messages)
    {
        if (_resendQueue != null && !_resendQueue.isEmpty())
        {
            return _resendQueue;
        }

        if (_filters != null)
        {
            if (isAutoClose())
            {
                if (_messages.isEmpty())
                {
                    close();
                    return null;
                }
            }
            return _messages;
        }
        else // we want the DM queue
        {
            return messages;
        }
    }

    public void addToResendQueue(AMQMessage msg)
    {
        // add to our resend queue
        getResendQueue().add(msg);

        // Mark Queue has having content.
        if (_queue == null)
        {
            _logger.error("Delivery Manager is null won't be able to resend messages");
        }
        else
        {
            _queue.setQueueHasContent(true, this);
        }
    }

    public Object sendlock()
    {
        return _resending;
    }

    private ByteBuffer createEncodedDeliverFrame(long deliveryTag, String routingKey, String exchange, boolean redelivered)
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQFrame deliverFrame = BasicDeliverBody.createAMQFrame(channel.getChannelId(),
                                                                (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                consumerTag,    // consumerTag
                                                                deliveryTag,    // deliveryTag
                                                                exchange,    // exchange
                                                                redelivered,    // redelivered
                                                                routingKey    // routingKey
        );
        ByteBuffer buf = ByteBuffer.allocate((int) deliverFrame.getSize()); // XXX: Could cast be a problem?
        deliverFrame.writePayload(buf);
        buf.flip();
        return buf;
    }
}
