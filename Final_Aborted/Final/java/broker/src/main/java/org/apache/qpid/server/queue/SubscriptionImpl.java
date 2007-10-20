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

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.util.ConcurrentLinkedQueueAtomicSize;
import org.apache.qpid.util.MessageQueue;
import org.apache.qpid.util.ConcurrentLinkedMessageQueueAtomicSize;

/**
 * Encapsulation of a supscription to a queue. <p/> Ties together the protocol session of a subscriber, the consumer tag
 * that was given out by the broker and the channel id. <p/>
 */
public class SubscriptionImpl implements Subscription
{

    private static final Logger _suspensionlogger = Logger.getLogger("Suspension");
    private static final Logger _logger = Logger.getLogger(SubscriptionImpl.class);

    public final AMQChannel channel;

    public final AMQProtocolSession protocolSession;

    public final AMQShortString consumerTag;

    private final Object _sessionKey;

    private MessageQueue<AMQMessage> _messages;

    private Queue<AMQMessage> _resendQueue;

    private final boolean _noLocal;

    /** True if messages need to be acknowledged */
    private final boolean _acks;
    private FilterManager _filters;
    private final boolean _isBrowser;
    private final Boolean _autoClose;
    private boolean _sentClose = false;

    private static final String CLIENT_PROPERTIES_INSTANCE = ClientProperties.instance.toString();

    private AMQQueue _queue;
    private final AtomicBoolean _sendLock = new AtomicBoolean(false);


    public static class Factory implements SubscriptionFactory
    {
        public Subscription createSubscription(int channel, AMQProtocolSession protocolSession,
                                               AMQShortString consumerTag, boolean acks, FieldTable filters,
                                               boolean noLocal, AMQQueue queue) throws AMQException
        {
            return new SubscriptionImpl(channel, protocolSession, consumerTag, acks, filters, noLocal, queue);
        }

        public SubscriptionImpl createSubscription(int channel, AMQProtocolSession protocolSession, AMQShortString consumerTag)
                throws AMQException
        {
            return new SubscriptionImpl(channel, protocolSession, consumerTag, false, null, false, null);
        }
    }

    public SubscriptionImpl(int channelId, AMQProtocolSession protocolSession,
                            AMQShortString consumerTag, boolean acks)
            throws AMQException
    {
        this(channelId, protocolSession, consumerTag, acks, null, false, null);
    }

    public SubscriptionImpl(int channelId, AMQProtocolSession protocolSession,
                            AMQShortString consumerTag, boolean acks, FieldTable filters,
                            boolean noLocal, AMQQueue queue)
            throws AMQException
    {
        AMQChannel channel = protocolSession.getChannel(channelId);
        if (channel == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "channel :" + channelId + " not found in protocol session");
        }

        this.channel = channel;
        this.protocolSession = protocolSession;
        this.consumerTag = consumerTag;
        _sessionKey = protocolSession.getKey();
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


        if (filtersMessages())
        {
            _messages = new ConcurrentLinkedMessageQueueAtomicSize<AMQMessage>();
        }
        else
        {
            // Reference the DeliveryManager
            _messages = null;
        }
    }


    public SubscriptionImpl(int channel, AMQProtocolSession protocolSession,
                            AMQShortString consumerTag)
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
     *
     * @param psc The subscriptionImpl to compare
     *
     * @return equality
     */
    private boolean equals(SubscriptionImpl psc)
    {
        return _sessionKey.equals(psc._sessionKey)
               && psc.channel == channel
               && psc.consumerTag.equals(consumerTag);
    }

    public int hashCode()
    {
        return _sessionKey.hashCode();
    }

    public String toString()
    {
        String subscriber = "[channel=" + channel +
                            ", consumerTag=" + consumerTag +
                            ", session=" + protocolSession.getKey() +
                            ", resendQueue=" + (_resendQueue != null);

        if (_resendQueue != null)
        {
            subscriber += ", resendSize=" + _resendQueue.size();
        }


        return subscriber + "]";
    }

    /**
     * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
     * thread safe.
     *
     * @param msg   The message to send
     * @param queue the Queue it has been sent from
     *
     * @throws AMQException
     */
    public void send(AMQMessage msg, AMQQueue queue) throws AMQException
    {
        if (msg != null)
        {
            if (_isBrowser)
            {
                sendToBrowser(msg, queue);
            }
            else
            {
                sendToConsumer(channel.getStoreContext(), msg, queue);
            }
        }
        else
        {
            _logger.error("Attempt to send Null message", new NullPointerException());
        }
    }

    private void sendToBrowser(AMQMessage msg, AMQQueue queue) throws AMQException
    {
        // We don't decrement the reference here as we don't want to consume the message
        // but we do want to send it to the client.

        synchronized (channel)
        {
            long deliveryTag = channel.getNextDeliveryTag();

            // We don't need to add the message to the unacknowledgedMap as we don't need to know if the client
            // received the message. If it is lost in transit that is not important.
//            if (_acks)
//            {
//                channel.addUnacknowledgedBrowsedMessage(msg, deliveryTag, consumerTag, queue);
//            }

            if (_sendLock.get())
            {
                _logger.error("Sending " + msg + " when subscriber(" + this + ") is closed!");
            }

            protocolSession.getProtocolOutputConverter().writeDeliver(msg, channel.getChannelId(), deliveryTag, consumerTag);
        }
    }

    private void sendToConsumer(StoreContext storeContext, AMQMessage msg, AMQQueue queue)
            throws AMQException
    {
        try
        { // if we do not need to wait for client acknowledgements
            // we can decrement the reference count immediately.

            // By doing this _before_ the send we ensure that it
            // doesn't get sent if it can't be dequeued, preventing
            // duplicate delivery on recovery.

            // The send may of course still fail, in which case, as
            // the message is unacked, it will be lost.
            if (!_acks)
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("No ack mode so dequeuing message immediately: " + msg.getMessageId());
                }
                queue.dequeue(storeContext, msg);
            }

            synchronized (channel)
            {
                long deliveryTag = channel.getNextDeliveryTag();

                if (_sendLock.get())
                {
                    _logger.error("Sending " + msg + " when subscriber(" + this + ") is closed!");
                }

                if (_acks)
                {
                    channel.addUnacknowledgedMessage(msg, deliveryTag, consumerTag, queue);
                }

                protocolSession.getProtocolOutputConverter().writeDeliver(msg, channel.getChannelId(), deliveryTag, consumerTag);

            }
        }
        finally
        {
            //Only set delivered if it actually was writen successfully..
            // using a try->finally would set it even if an error occured.
            // Is this what we want? 

            msg.setDeliveredToConsumer();
        }
    }

    public boolean isSuspended()
    {
//        if (_suspensionlogger.isInfoEnabled())
//        {
//            if (channel.isSuspended())
//            {
//                _suspensionlogger.debug("Subscription(" + debugIdentity() + ") channel's is susupended");
//            }
//            if (_sendLock.get())
//            {
//                _suspensionlogger.debug("Subscription(" + debugIdentity() + ") has sendLock set so closing.");
//            }
//        }
        return channel.isSuspended() || _sendLock.get();
    }

    /**
     * Callback indicating that a queue has been deleted.
     *
     * @param queue The queue to delete
     */
    public void queueDeleted(AMQQueue queue) throws AMQException
    {
        channel.queueDeleted(queue);
    }

    public boolean filtersMessages()
    {
        return _filters != null || _noLocal;
    }

    public boolean hasInterest(AMQMessage msg)
    {
        //check that the message hasn't been rejected
        if (msg.isRejectedBy(this))
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Subscription:" + debugIdentity() + " rejected message:" + msg.debugIdentity());
            }
//            return false;
        }

        final AMQProtocolSession publisher = msg.getPublisher();

        //todo - client id should be recoreded and this test removed but handled below
        if (_noLocal && publisher != null)
        {
            // We don't want local messages so check to see if message is one we sent
            Object localInstance;
            Object msgInstance;

            if ((protocolSession.getClientProperties() != null) &&
                (localInstance = protocolSession.getClientProperties().getObject(CLIENT_PROPERTIES_INSTANCE)) != null)
            {

                if ((publisher.getClientProperties() != null) &&
                    (msgInstance = publisher.getClientProperties().getObject(CLIENT_PROPERTIES_INSTANCE)) != null)
                {
                    if (localInstance == msgInstance || localInstance.equals(msgInstance))
                    {
//                        if (_logger.isTraceEnabled())
//                        {
//                            _logger.trace("(" + debugIdentity() + ") has no interest as it is a local message(" +
//                                          msg.debugIdentity() + ")");
//                        }
                        return false;
                    }
                }
            }
            else
            {

                localInstance = protocolSession.getClientIdentifier();
                //todo - client id should be recoreded and this test removed but handled here

                msgInstance = publisher.getClientIdentifier();
                if (localInstance == msgInstance || ((localInstance != null) && localInstance.equals(msgInstance)))
                {
//                    if (_logger.isTraceEnabled())
//                    {
//                        _logger.trace("(" + debugIdentity() + ") has no interest as it is a local message(" +
//                                      msg.debugIdentity() + ")");
//                    }
                    return false;
                }
            }


        }


        if (_logger.isTraceEnabled())
        {
            _logger.trace("(" + debugIdentity() + ") checking filters for message (" + msg.debugIdentity());
        }
        return checkFilters(msg);

    }

    private String id = String.valueOf(System.identityHashCode(this));

    private String debugIdentity()
    {
        return id;
    }

    private boolean checkFilters(AMQMessage msg)
    {
        if (_filters != null)
        {
//            if (_logger.isTraceEnabled())
//            {
//                _logger.trace("(" + debugIdentity() + ") has filters.");
//            }
            return _filters.allAllow(msg);
        }
        else
        {
//            if (_logger.isTraceEnabled())
//            {
//                _logger.trace("(" + debugIdentity() + ") has no filters");
//            }

            return true;
        }
    }

    public Queue<AMQMessage> getPreDeliveryQueue()
    {
        return _messages;
    }

    public void enqueueForPreDelivery(AMQMessage msg, boolean deliverFirst)
    {
        if (_messages != null)
        {
            if (deliverFirst)
            {
                _messages.pushHead(msg);
            }
            else
            {
                _messages.offer(msg);
            }
        }
    }

    public boolean isAutoClose()
    {
        return _autoClose;
    }

    public void close()
    {
        boolean closed = false;
        synchronized (_sendLock)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Setting SendLock true:" + debugIdentity());
            }

            closed = _sendLock.getAndSet(true);
        }

        if (closed)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Called close() on a closed subscription");
            }

            return;
        }

        if (_logger.isInfoEnabled())
        {
            _logger.info("Closing subscription (" + debugIdentity() + "):" + this);
        }

        if (_resendQueue != null && !_resendQueue.isEmpty())
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Requeuing closing subscription (" + debugIdentity() + "):" + this);
            }
            requeue();
        }

        //remove references in PDQ
        if (_messages != null)
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Clearing PDQ (" + debugIdentity() + "):" + this);
            }

            _messages.clear();
        }
    }

    private void autoclose()
    {
        close();

        if (_autoClose && !_sentClose)
        {
            _logger.info("Closing autoclose subscription (" + debugIdentity() + "):" + this);

            ProtocolOutputConverter converter = protocolSession.getProtocolOutputConverter();
            converter.confirmConsumerAutoClose(channel.getChannelId(), consumerTag);
            _sentClose = true;

            //fixme JIRA do this better
            try
            {
                channel.unsubscribeConsumer(protocolSession, consumerTag);
            }
            catch (AMQException e)
            {
                // Occurs if we cannot find the subscriber in the channel with protocolSession and consumerTag.
            }
        }
    }

    private void requeue()
    {
        if (_queue != null)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Requeuing :" + _resendQueue.size() + " messages");
            }

            while (!_resendQueue.isEmpty())
            {
                AMQMessage resent = _resendQueue.poll();

                if (_logger.isTraceEnabled())
                {
                    _logger.trace("Removed for resending:" + resent.debugIdentity());
                }

                resent.release(_queue);
                _queue.subscriberHasPendingResend(false, this, resent);

                try
                {
                    channel.getTransactionalContext().deliver(resent, _queue, true);
                }
                catch (AMQException e)
                {
                    _logger.error("MESSAGE LOSS : Unable to re-deliver messages", e);
                }
            }

            if (!_resendQueue.isEmpty())
            {
                _logger.error("[MESSAGES LOST]Unable to re-deliver messages as queue is null.");
            }

            _queue.subscriberHasPendingResend(false, this, null);
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


    public boolean isClosed()
    {
        return _sendLock.get(); // This rather than _close is used to signify the subscriber is now closed.
    }

    public boolean isBrowser()
    {
        return _isBrowser;
    }

    public boolean wouldSuspend(AMQMessage msg)
    {
        return channel.wouldSuspend(msg);
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

        if (filtersMessages())
        {
            if (isAutoClose())
            {
                if (_messages.isEmpty())
                {
                    autoclose();
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
            _logger.error("Queue is null won't be able to resend messages");
        }
        else
        {
            _queue.subscriberHasPendingResend(true, this, msg);
        }
    }

    public Object getSendLock()
    {
        return _sendLock;
    }

    public AMQChannel getChannel()
    {
        return channel;
    }

}
