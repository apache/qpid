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
import org.apache.qpid.util.ConcurrentLinkedQueueAtomicSize;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.BasicDeliverBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.protocol.AMQProtocolSession;

import java.util.Queue;

/**
 * Encapsulation of a supscription to a queue.
 * <p/>
 * Ties together the protocol session of a subscriber, the consumer tag that
 * was given out by the broker and the channel id.
 * <p/>
 */
public class SubscriptionImpl implements Subscription
{
    private static final Logger _logger = Logger.getLogger(SubscriptionImpl.class);

    public final AMQChannel channel;

    public final AMQProtocolSession protocolSession;

    public final String consumerTag;

    private final Object sessionKey;

    private Queue<AMQMessage> _messages;

    private final boolean _noLocal;

    /**
     * True if messages need to be acknowledged
     */
    private final boolean _acks;
    private FilterManager _filters;

    public static class Factory implements SubscriptionFactory
    {
        public Subscription createSubscription(int channel, AMQProtocolSession protocolSession, String consumerTag, boolean acks, FieldTable filters, boolean noLocal) throws AMQException
        {
            return new SubscriptionImpl(channel, protocolSession, consumerTag, acks, filters, noLocal);
        }

        public SubscriptionImpl createSubscription(int channel, AMQProtocolSession protocolSession, String consumerTag)
                throws AMQException
        {
            return new SubscriptionImpl(channel, protocolSession, consumerTag, false, null, false);
        }
    }

    public SubscriptionImpl(int channelId, AMQProtocolSession protocolSession,
                            String consumerTag, boolean acks)
            throws AMQException
    {
        this(channelId, protocolSession, consumerTag, acks, null, false);
    }

    public SubscriptionImpl(int channelId, AMQProtocolSession protocolSession,
                            String consumerTag, boolean acks, FieldTable filters, boolean noLocal)
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

        _filters = FilterManagerFactory.createManager(filters);

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
        this(channel, protocolSession, consumerTag, false);
    }

    public boolean equals(Object o)
    {
        return (o instanceof SubscriptionImpl) && equals((SubscriptionImpl) o);
    }

    /**
     * Equality holds if the session matches and the channel and consumer tag are the same.
     */
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
     * This method can be called by each of the publisher threads.
     * As a result all changes to the channel object must be thread safe.
     *
     * @param msg
     * @param queue
     * @throws AMQException
     */
    public void send(AMQMessage msg, AMQQueue queue) throws FailedDequeueException
    {
        if (msg != null)
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
                synchronized(channel)
                {
                    long deliveryTag = channel.getNextDeliveryTag();

                    if (_acks)
                    {
                        channel.addUnacknowledgedMessage(msg, deliveryTag, consumerTag, queue);
                    }

                    ByteBuffer deliver = createEncodedDeliverFrame(deliveryTag, msg.getRoutingKey(), msg.getExchangeName());
                    AMQDataBlock frame = msg.getDataBlock(deliver, channel.getChannelId());

                    protocolSession.writeFrame(frame);
                }
            }
            finally
            {
                msg.setDeliveredToConsumer();
            }
        }
        else
        {
            _logger.error("Attempt to send Null message", new NullPointerException());
        }
    }

    public boolean isSuspended()
    {
        return channel.isSuspended();
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


    private ByteBuffer createEncodedDeliverFrame(long deliveryTag, String routingKey, String exchange)
    {
        AMQFrame deliverFrame = BasicDeliverBody.createAMQFrame(channel.getChannelId(), consumerTag,
                                                                deliveryTag, false, exchange,
                                                                routingKey);
        ByteBuffer buf = ByteBuffer.allocate((int) deliverFrame.getSize()); // XXX: Could cast be a problem?
        deliverFrame.writePayload(buf);
        buf.flip();
        return buf;
    }
}
