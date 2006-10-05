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
package org.apache.qpid.server;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.exchange.MessageRouter;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.TxnBuffer;
import org.apache.qpid.server.txn.TxnOp;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AMQChannel
{
    public static final int DEFAULT_PREFETCH = 5000;

    private static final Logger _log = Logger.getLogger(AMQChannel.class);

    private final int _channelId;

    private boolean _transactional;

    private long _prefetchCount;

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
     * The current message - which may be partial in the sense that not all frames have been received yet -
     * which has been received by this channel. As the frames are received the message gets updated and once all
     * frames have been received the message can then be routed.
     */
    private AMQMessage _currentMessage;

    /**
     * Maps from consumer tag to queue instance. Allows us to unsubscribe from a queue.
     */
    private final Map<String, AMQQueue> _consumerTag2QueueMap = new TreeMap<String, AMQQueue>();

    private final MessageStore _messageStore;

    private final Object _unacknowledgedMessageMapLock = new Object();

    private Map<Long, UnacknowledgedMessage> _unacknowledgedMessageMap = new LinkedHashMap<Long, UnacknowledgedMessage>(DEFAULT_PREFETCH);

    private final AtomicBoolean _suspended = new AtomicBoolean(false);

    private final MessageRouter _exchanges;

    private final TxnBuffer _txnBuffer;

    public static class UnacknowledgedMessage
    {
        public final AMQMessage message;
        public final String consumerTag;
        public AMQQueue queue;

        public UnacknowledgedMessage(AMQQueue queue, AMQMessage message, String consumerTag)
        {
            this.queue = queue;
            this.message = message;
            this.consumerTag = consumerTag;
        }

        private void discard() throws AMQException
        {
            if (queue != null)
            {
                message.dequeue(queue);
            }
            message.decrementReference();
        }
    }

    public AMQChannel(int channelId, MessageStore messageStore, MessageRouter exchanges)
            throws AMQException
    {
        _channelId = channelId;
        _prefetchCount = DEFAULT_PREFETCH;
        _messageStore = messageStore;
        _exchanges = exchanges;
        _txnBuffer = new TxnBuffer(_messageStore);
    }

    public int getChannelId()
    {
        return _channelId;
    }

    public boolean isTransactional()
    {
        return _transactional;
    }

    public void setTransactional(boolean transactional)
    {
        _transactional = transactional;
    }

    public long getPrefetchCount()
    {
        return _prefetchCount;
    }

    public void setPrefetchCount(long prefetchCount)
    {
        _prefetchCount = prefetchCount;
    }

    public void setPublishFrame(BasicPublishBody publishBody, AMQProtocolSession publisher) throws AMQException
    {
        _currentMessage = new AMQMessage(_messageStore, publishBody);
        _currentMessage.setPublisher(publisher);
    }

    public void publishContentHeader(ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        if (_currentMessage == null)
        {
            throw new AMQException("Received content header without previously receiving a BasicDeliver frame");
        }
        else
        {
            _currentMessage.setContentHeaderBody(contentHeaderBody);
            // check and route if header says body length is zero
            if (contentHeaderBody.bodySize == 0)
            {
                routeCurrentMessage();
            }
        }
    }

    public void publishContentBody(ContentBody contentBody)
            throws AMQException
    {
        if (_currentMessage == null)
        {
            throw new AMQException("Received content body without previously receiving a JmsPublishBody");
        }
        if (_currentMessage.getContentHeaderBody() == null)
        {
            throw new AMQException("Received content body without previously receiving a content header");
        }

        _currentMessage.addContentBodyFrame(contentBody);
        if (_currentMessage.isAllContentReceived())
        {
            routeCurrentMessage();
        }
    }

    protected void routeCurrentMessage() throws AMQException
    {
        if (_transactional)
        {
            //don't create a transaction unless needed
            if(_currentMessage.isPersistent())
            {
                _txnBuffer.setPersistentMessageRecevied();
            }

            //don't route this until commit
            _txnBuffer.enlist(new Publish(_currentMessage));
            _currentMessage = null;
        }
        else
        {
            _exchanges.routeContent(_currentMessage);
            _currentMessage.decrementReference();
            _currentMessage = null;
        }
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
     * @return the consumer tag. This is returned to the subscriber and used in
     *         subsequent unsubscribe requests
     * @throws ConsumerTagNotUniqueException if the tag is not unique
     * @throws AMQException                  if something goes wrong
     */
    public String subscribeToQueue(String tag, AMQQueue queue, AMQProtocolSession session, boolean acks) throws AMQException, ConsumerTagNotUniqueException
    {
        if (tag == null)
        {
            tag = "sgen_" + getNextConsumerTag();
        }
        if (_consumerTag2QueueMap.containsKey(tag))
        {
            throw new ConsumerTagNotUniqueException();
        }

        queue.registerProtocolSession(session, _channelId, tag, acks);
        _consumerTag2QueueMap.put(tag, queue);
        return tag;
    }


    public void unsubscribeConsumer(AMQProtocolSession session, String consumerTag) throws AMQException
    {
        AMQQueue q = _consumerTag2QueueMap.remove(consumerTag);
        if (q != null)
        {
            q.unregisterProtocolSession(session, _channelId, consumerTag);
        }
        else
        {
            throw new AMQException(_log, "Consumer tag " + consumerTag + " not known to channel " +
                                         _channelId);
        }
    }

    /**
     * Called from the protocol session to close this channel and clean up.
     *
     * @throws AMQException if there is an error during closure
     */
    public void close(AMQProtocolSession session) throws AMQException
    {
        if (_transactional)
        {
            synchronized(_txnBuffer)
            {
                _txnBuffer.rollback();//releases messages
            }
        }
        unsubscribeAllConsumers(session);
        requeue();
    }

    private void unsubscribeAllConsumers(AMQProtocolSession session) throws AMQException
    {
        _log.info("Unsubscribing all consumers on channel " + toString());
        for (Map.Entry<String, AMQQueue> me : _consumerTag2QueueMap.entrySet())
        {
            me.getValue().unregisterProtocolSession(session, _channelId, me.getKey());
        }
        _consumerTag2QueueMap.clear();
    }

    /**
     * Add a message to the channel-based list of unacknowledged messages
     *
     * @param message
     * @param deliveryTag
     * @param queue
     */
    public void addUnacknowledgedMessage(AMQMessage message, long deliveryTag, String consumerTag, AMQQueue queue)
    {
        synchronized(_unacknowledgedMessageMapLock)
        {
            _unacknowledgedMessageMap.put(deliveryTag, new UnacknowledgedMessage(queue, message, consumerTag));
            checkSuspension();
        }
    }

    /**
     * Called to attempt re-enqueue all outstanding unacknowledged messages on the channel.
     * May result in delivery to this same channel or to other subscribers.
     */
    public void requeue() throws AMQException
    {
        // we must create a new map since all the messages will get a new delivery tag when they are redelivered
        Map<Long, UnacknowledgedMessage> currentList;
        synchronized(_unacknowledgedMessageMapLock)
        {
            currentList = _unacknowledgedMessageMap;
            _unacknowledgedMessageMap = new LinkedHashMap<Long, UnacknowledgedMessage>(DEFAULT_PREFETCH);
        }

        for (UnacknowledgedMessage unacked : currentList.values())
        {
            if (unacked.queue != null)
            {
                unacked.queue.deliver(unacked.message);
            }
        }
    }

    /**
     * Called to resend all outstanding unacknowledged messages to this same channel.
     */
    public void resend(AMQProtocolSession session)
    {
        //messages go to this channel
        synchronized(_unacknowledgedMessageMapLock)
        {
            for (Map.Entry<Long, UnacknowledgedMessage> entry : _unacknowledgedMessageMap.entrySet())
            {
                long deliveryTag = entry.getKey();
                String consumerTag = entry.getValue().consumerTag;
                AMQMessage msg = entry.getValue().message;

                session.writeFrame(msg.getDataBlock(_channelId, consumerTag, deliveryTag));
            }
        }
    }

    /**
     * Callback indicating that a queue has been deleted. We must update the structure of unacknowledged
     * messages to remove the queue reference and also decrement any message reference counts, without
     * actually removing the item sine we may get an ack for a delivery tag that was generated from the
     * deleted queue.
     *
     * @param queue
     */
    public void queueDeleted(AMQQueue queue)
    {
        synchronized(_unacknowledgedMessageMapLock)
        {
            for (Map.Entry<Long, UnacknowledgedMessage> unacked : _unacknowledgedMessageMap.entrySet())
            {
                final UnacknowledgedMessage unackedMsg = unacked.getValue();
                // we can compare the reference safely in this case
                if (unackedMsg.queue == queue)
                {
                    unackedMsg.queue = null;
                    try
                    {
                        unackedMsg.message.decrementReference();
                    }
                    catch (AMQException e)
                    {
                        _log.error("Error decrementing ref count on message " + unackedMsg.message.getMessageId() + ": " +
                                   e, e);
                    }
                }
            }
        }
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
        if (_transactional)
        {
            //don't handle this until commit
            _txnBuffer.enlist(new Ack(deliveryTag, multiple));
        }
        else
        {
            handleAcknowledgement(deliveryTag, multiple);
        }
    }

    private void handleAcknowledgement(long deliveryTag, boolean multiple) throws AMQException
    {
        if (multiple)
        {
            LinkedList<UnacknowledgedMessage> acked = new LinkedList<UnacknowledgedMessage>();
            synchronized(_unacknowledgedMessageMapLock)
            {
                if (deliveryTag == 0)
                {
                    //Spec 2.1.6.11 ... If the multiple field is 1, and the delivery tag is zero, tells the server to acknowledge all outstanding mesages.
                    _log.info("Multiple ack on delivery tag 0. ACKing all messages. Current count:" + _unacknowledgedMessageMap.size());
                    acked = new LinkedList<UnacknowledgedMessage>(_unacknowledgedMessageMap.values());
                    _unacknowledgedMessageMap.clear();
                }
                else
                {
                    if (!_unacknowledgedMessageMap.containsKey(deliveryTag))
                    {
                        throw new AMQException("Multiple ack on delivery tag " + deliveryTag + " not known for channel");
                    }
                    Iterator<Map.Entry<Long, UnacknowledgedMessage>> i = _unacknowledgedMessageMap.entrySet().iterator();

                    while (i.hasNext())
                    {

                        Map.Entry<Long, UnacknowledgedMessage> unacked = i.next();

                        if (unacked.getKey() > deliveryTag)
                        {
                            //This should not occur now.
                            throw new AMQException("UnacknowledgedMessageMap is out of order:" + unacked.getKey() + " When deliveryTag is:" + deliveryTag + "ES:" + _unacknowledgedMessageMap.entrySet().toString());
                        }

                        i.remove();

                        acked.add(unacked.getValue());
                        if (unacked.getKey() == deliveryTag)
                        {
                            break;
                        }
                    }
                }
            }// synchronized

            if (_log.isDebugEnabled())
            {
                _log.debug("Received multiple ack for delivery tag " + deliveryTag + ". Removing " +
                           acked.size() + " items.");
            }

            for (UnacknowledgedMessage msg : acked)
            {
                msg.discard();
            }

        }
        else
        {
            UnacknowledgedMessage msg;
            synchronized(_unacknowledgedMessageMapLock)
            {
                msg = _unacknowledgedMessageMap.remove(deliveryTag);
            }

            if (msg == null)
            {
                _log.info("Single ack on delivery tag " + deliveryTag + " not known for channel:" + _channelId);
                throw new AMQException("Single ack on delivery tag " + deliveryTag + " not known for channel:" + _channelId);
            }
            msg.discard();
            if (_log.isDebugEnabled())
            {
                _log.debug("Received non-multiple ack for messaging with delivery tag " + deliveryTag);
            }
        }

        checkSuspension();
    }

    /**
     * Used only for testing purposes.
     *
     * @return the map of unacknowledged messages
     */
    public Map<Long, UnacknowledgedMessage> getUnacknowledgedMessageMap()
    {
        return _unacknowledgedMessageMap;
    }

    private void checkSuspension()
    {
        boolean suspend;
        //noinspection SynchronizeOnNonFinalField
        synchronized(_unacknowledgedMessageMapLock)
        {
            suspend = _unacknowledgedMessageMap.size() >= _prefetchCount;
        }
        setSuspended(suspend);
    }

    public void setSuspended(boolean suspended)
    {
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
        _txnBuffer.commit();
    }

    public void rollback() throws AMQException
    {
        //need to protect rollback and close from each other...
        synchronized(_txnBuffer)
        {
            _txnBuffer.rollback();
        }
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder(30);
        sb.append("Channel: id ").append(_channelId).append(", transaction mode: ").append(_transactional);
        sb.append(", prefetch count: ").append(_prefetchCount);
        return sb.toString();
    }

    public ObjectName getObjectName()
            throws MalformedObjectNameException
    {
        StringBuilder sb = new StringBuilder(30);
        sb.append("Channel:id=").append(_channelId);
        sb.append(",transaction mode=").append(_transactional);
        return new ObjectName(sb.toString());
    }

    public void setDefaultQueue(AMQQueue queue)
    {
        _defaultQueue = queue;
    }

    public AMQQueue getDefaultQueue()
    {
        return _defaultQueue;
    }

    private class Ack implements TxnOp
    {
        private final long _msgId;
        private final boolean _multi;

        Ack(long msgId, boolean multi)
        {
            _msgId = msgId;
            _multi = multi;
        }

        public void commit() throws AMQException
        {
            handleAcknowledgement(_msgId, _multi);
        }

        public void rollback()
        {
        }
    }

    //TODO:
    //implement a scheme whereby messages can be stored on disk
    //until commit, then reloaded...
    private class Publish implements TxnOp
    {
        private final AMQMessage _msg;

        Publish(AMQMessage msg)
        {
            _msg = msg;
        }

        public boolean isPersistent() throws AMQException
        {
            return _msg.isPersistent();
        }

        public void commit() throws AMQException
        {
            _exchanges.routeContent(_msg);
            _msg.decrementReference();
        }

        public void rollback()
        {
            try
            {
                _msg.decrementReference();
            }
            catch (AMQException e)
            {
                _log.error("Error rolling back a publish request: " + e, e);
            }
        }
    }

}
