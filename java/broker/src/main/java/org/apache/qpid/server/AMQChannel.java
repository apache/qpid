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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.ack.UnacknowledgedMessage;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.ack.UnacknowledgedMessageMapImpl;
import org.apache.qpid.server.exchange.MessageRouter;
import org.apache.qpid.server.exchange.NoRouteException;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.*;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.LocalTransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.configuration.Configurator;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AMQChannel
{
    public static final int DEFAULT_PREFETCH = 5000;

    private static final Logger _log = Logger.getLogger(AMQChannel.class);

    private final int _channelId;

    // private boolean _transactional;

    private long _prefetch_HighWaterMark;

    private long _prefetch_LowWaterMark;

    private long _prefetchSize;

    /**
     * The delivery tag is unique per channel. This is pre-incremented before putting into the deliver frame so that
     * value of this represents the <b>last</b> tag sent out
     */
    private AtomicLong _deliveryTag = new AtomicLong(0);

    /** A channel has a default queue (the last declared) that is used when no queue name is explictily set */
    private AMQQueue _defaultQueue;

    /** This tag is unique per subscription to a queue. The server returns this in response to a basic.consume request. */
    private int _consumerTag;

    /**
     * The current message - which may be partial in the sense that not all frames have been received yet - which has
     * been received by this channel. As the frames are received the message gets updated and once all frames have been
     * received the message can then be routed.
     */
    private AMQMessage _currentMessage;

    /** Maps from consumer tag to queue instance. Allows us to unsubscribe from a queue. */
    private final Map<AMQShortString, AMQQueue> _consumerTag2QueueMap = new HashMap<AMQShortString, AMQQueue>();

    private final MessageStore _messageStore;

    private UnacknowledgedMessageMap _unacknowledgedMessageMap = new UnacknowledgedMessageMapImpl(DEFAULT_PREFETCH);

    private final AtomicBoolean _suspended = new AtomicBoolean(false);

    private final MessageRouter _exchanges;

    private TransactionalContext _txnContext, _nonTransactedContext;

    /**
     * A context used by the message store enabling it to track context for a given channel even across thread
     * boundaries
     */
    private final StoreContext _storeContext;

    private final List<RequiredDeliveryException> _returnMessages = new LinkedList<RequiredDeliveryException>();

    private MessageHandleFactory _messageHandleFactory = new MessageHandleFactory();

    private Set<Long> _browsedAcks = new HashSet<Long>();

    // Why do we need this reference ? - ritchiem
    private final AMQProtocolSession _session;
    private boolean _closing;

    @Configured(path = "advanced.enableJMSXUserID",
                defaultValue = "true")
    public boolean ENABLE_JMSXUserID;


    public AMQChannel(AMQProtocolSession session, int channelId, MessageStore messageStore, MessageRouter exchanges)
            throws AMQException
    {
        //Set values from configuration
        Configurator.configure(this);

        _session = session;
        _channelId = channelId;
        _storeContext = new StoreContext("Session: " + session.getClientIdentifier() + "; channel: " + channelId);
        _prefetch_HighWaterMark = DEFAULT_PREFETCH;
        _prefetch_LowWaterMark = _prefetch_HighWaterMark / 2;
        _messageStore = messageStore;
        _exchanges = exchanges;
        // by default the session is non-transactional
        _txnContext = new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages, _browsedAcks);
    }

    /** Sets this channel to be part of a local transaction */
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

    public void setPublishFrame(MessagePublishInfo info, AMQProtocolSession publisher) throws AMQException
    {

        _currentMessage = new AMQMessage(_messageStore.getNewMessageId(), info, _txnContext);
        _currentMessage.setPublisher(publisher);
    }

    public void publishContentHeader(ContentHeaderBody contentHeaderBody, AMQProtocolSession protocolSession)
            throws AMQException
    {
        if (_currentMessage == null)
        {
            throw new AMQException("Received content header without previously receiving a BasicPublish frame");
        }
        else
        {
            if (_log.isTraceEnabled())
            {
                _log.trace(debugIdentity() + "Content header received on channel " + _channelId);
            }

            if (ENABLE_JMSXUserID)
            {
                //Set JMSXUserID
                BasicContentHeaderProperties properties = (BasicContentHeaderProperties) contentHeaderBody.properties;
                //fixme: fudge for QPID-677
                properties.getHeaders().keySet();

                properties.setUserId(protocolSession.getAuthorizedID().getName());
            }

            _currentMessage.setContentHeaderBody(contentHeaderBody);
            _currentMessage.setExpiration();

            routeCurrentMessage();
            _currentMessage.routingComplete(_messageStore, _storeContext, _messageHandleFactory);

            // check and deliver if header says body length is zero
            if (contentHeaderBody.bodySize == 0)
            {
                _txnContext.messageProcessed(protocolSession);
                _currentMessage = null;
            }
        }
    }

    public void publishContentBody(ContentBody contentBody, AMQProtocolSession protocolSession) throws AMQException
    {
        if (_currentMessage == null)
        {
            throw new AMQException("Received content body without previously receiving a JmsPublishBody");
        }

        if (_log.isTraceEnabled())
        {
            _log.trace(debugIdentity() + "Content body received on channel " + _channelId);
        }

        try
        {

            // returns true iff the message was delivered (i.e. if all data was
            // received
            if (_currentMessage.addContentBodyFrame(_storeContext,
                        protocolSession.getMethodRegistry().getProtocolVersionMethodConverter().convertToContentChunk(
                            contentBody)))
            {
                // callback to allow the context to do any post message processing
                // primary use is to allow message return processing in the non-tx case
                _txnContext.messageProcessed(protocolSession);
                _currentMessage = null;
            }
        }
        catch (AMQException e)
        {
            // we want to make sure we don't keep a reference to the message in the
            // event of an error
            _currentMessage = null;
            throw e;
        }
    }

    protected void routeCurrentMessage() throws AMQException
    {
        try
        {
            _exchanges.routeContent(_currentMessage);
        }
        catch (NoRouteException e)
        {
            _returnMessages.add(e);
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
     * Subscribe to a queue. We register all subscriptions in the channel so that if the channel is closed we can clean
     * up all subscriptions, even if the client does not explicitly unsubscribe from all queues.
     *
     * @param tag       the tag chosen by the client (if null, server will generate one)
     * @param queue     the queue to subscribe to
     * @param session   the protocol session of the subscriber
     * @param noLocal   Flag stopping own messages being receivied.
     * @param exclusive Flag requesting exclusive access to the queue
     * @param acks      Are acks enabled for this subscriber
     * @param filters   Filters to apply to this subscriber
     *
     * @return the consumer tag. This is returned to the subscriber and used in subsequent unsubscribe requests
     *
     * @throws ConsumerTagNotUniqueException if the tag is not unique
     * @throws AMQException                  if something goes wrong
     */
    public AMQShortString subscribeToQueue(AMQShortString tag, AMQQueue queue, AMQProtocolSession session, boolean acks,
                                           FieldTable filters, boolean noLocal, boolean exclusive) throws AMQException, ConsumerTagNotUniqueException
    {
        if (tag == null)
        {
            tag = new AMQShortString("sgen_" + getNextConsumerTag());
        }

        if (_consumerTag2QueueMap.containsKey(tag))
        {
            throw new ConsumerTagNotUniqueException();
        }

        queue.registerProtocolSession(session, _channelId, tag, acks, filters, noLocal, exclusive);
        _consumerTag2QueueMap.put(tag, queue);

        return tag;
    }

    public void unsubscribeConsumer(AMQProtocolSession session, AMQShortString consumerTag) throws AMQException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Unacked Map Dump size:" + _unacknowledgedMessageMap.size());
            _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
            {

                public boolean callback(UnacknowledgedMessage message) throws AMQException
                {
                    _log.debug(message);

                    return true;
                }

                public void visitComplete()
                {
                }
            });
        }

        AMQQueue q = _consumerTag2QueueMap.remove(consumerTag);
        if (q != null)
        {
            q.unregisterProtocolSession(session, _channelId, consumerTag);
        }
    }

    /**
     * Called from the protocol session to close this channel and clean up. T
     *
     * @param session The session to close
     *
     * @throws AMQException if there is an error during closure
     */
    public void close(AMQProtocolSession session) throws AMQException
    {
        _txnContext.rollback();
        unsubscribeAllConsumers(session);
        try
        {
            requeue();
        }
        catch (AMQException e)
        {
            _log.error("Caught AMQException whilst attempting to reque:" + e);        
        }

        setClosing(true);
    }

    private void setClosing(boolean closing)
    {
        _closing = closing;
    }

    private void unsubscribeAllConsumers(AMQProtocolSession session) throws AMQException
    {
        if (_log.isInfoEnabled())
        {
            if (!_consumerTag2QueueMap.isEmpty())
            {
                _log.info("Unsubscribing all consumers on channel " + toString());
            }
            else
            {
                _log.info("No consumers to unsubscribe on channel " + toString());
            }
        }

        for (Map.Entry<AMQShortString, AMQQueue> me : _consumerTag2QueueMap.entrySet())
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Unsubscribing consumer '" + me.getKey() + "' on channel " + toString());
            }

            me.getValue().unregisterProtocolSession(session, _channelId, me.getKey());
        }

        _consumerTag2QueueMap.clear();
    }

    /**
     * Add a message to the channel-based list of unacknowledged messages
     *
     * @param entry       the record of the message on the queue that was delivered
     * @param deliveryTag the delivery tag used when delivering the message (see protocol spec for description of the
     *                    delivery tag)
     * @param consumerTag The tag for the consumer that is to acknowledge this message.
     */
    public void addUnacknowledgedMessage(QueueEntry entry, long deliveryTag, AMQShortString consumerTag)
    {
        if (_log.isDebugEnabled())
        {
            if (entry.getQueue() == null)
            {
                _log.debug("Adding unacked message with a null queue:" + entry.debugIdentity());
            }
            else
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug(debugIdentity() + " Adding unacked message(" + entry.getMessage().toString() + " DT:" + deliveryTag
                               + ") with a queue(" + entry.getQueue() + ") for " + consumerTag);
                }
            }
        }

        synchronized (_unacknowledgedMessageMap.getLock())
        {
            _unacknowledgedMessageMap.add(deliveryTag, new UnacknowledgedMessage(entry, consumerTag, deliveryTag));
            checkSuspension();
        }
    }

    private final String id = "(" + System.identityHashCode(this) + ")";

    public String debugIdentity()
    {
        return _channelId + id;
    }

    /**
     * Called to attempt re-delivery all outstanding unacknowledged messages on the channel. May result in delivery to
     * this same channel or to other subscribers.
     *
     * @throws org.apache.qpid.AMQException if the requeue fails
     */
    public void requeue() throws AMQException
    {
        // we must create a new map since all the messages will get a new delivery tag when they are redelivered
        Collection<UnacknowledgedMessage> messagesToBeDelivered = _unacknowledgedMessageMap.cancelAllMessages();

        // Deliver these messages out of the transaction as their delivery was never
        // part of the transaction only the receive.
        TransactionalContext deliveryContext = null;

        if (!messagesToBeDelivered.isEmpty())
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Requeuing " + messagesToBeDelivered.size() + " unacked messages. for " + toString());
            }

            if (!(_txnContext instanceof NonTransactionalContext))
            {
                // if (_nonTransactedContext == null)
                {
                    _nonTransactedContext =
                            new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages, _browsedAcks);
                }

                deliveryContext = _nonTransactedContext;
            }
            else
            {
                deliveryContext = _txnContext;
            }
        }

        for (UnacknowledgedMessage unacked : messagesToBeDelivered)
        {
            if (!unacked.isQueueDeleted())
            {
                // Ensure message is released for redelivery
                unacked.entry.release();

                // Mark message redelivered
                unacked.getMessage().setRedelivered(true);

                // Deliver Message
                deliveryContext.deliver(unacked.entry, false);

                // Should we allow access To the DM to directy deliver the message?
                // As we don't need to check for Consumers or worry about incrementing the message count?
                // unacked.queue.getDeliveryManager().deliver(_storeContext, unacked.queue.getName(), unacked.message, false);
            }
        }

    }

    /**
     * Requeue a single message
     *
     * @param deliveryTag The message to requeue
     *
     * @throws AMQException If something goes wrong.
     */
    public void requeue(long deliveryTag) throws AMQException
    {
        UnacknowledgedMessage unacked = _unacknowledgedMessageMap.remove(deliveryTag);

        if (unacked != null)
        {

            // Ensure message is released for redelivery
            if (!unacked.isQueueDeleted())
            {
                unacked.entry.release();
            }

            // Mark message redelivered
            unacked.getMessage().setRedelivered(true);

            // Deliver these messages out of the transaction as their delivery was never
            // part of the transaction only the receive.
            TransactionalContext deliveryContext;
            if (!(_txnContext instanceof NonTransactionalContext))
            {
                // if (_nonTransactedContext == null)
                {
                    _nonTransactedContext =
                            new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages, _browsedAcks);
                }

                deliveryContext = _nonTransactedContext;
            }
            else
            {
                deliveryContext = _txnContext;
            }

            if (!unacked.isQueueDeleted())
            {
                // Redeliver the messages to the front of the queue
                deliveryContext.deliver(unacked.entry, true);
                // Deliver increments the message count but we have already deliverted this once so don't increment it again
                // this was because deliver did an increment changed this.
            }
            else
            {
                _log.warn(System.identityHashCode(this) + " Requested requeue of message(" + unacked.getMessage().debugIdentity()
                          + "):" + deliveryTag + " but no queue defined and no DeadLetter queue so DROPPING message.");
                // _log.error("Requested requeue of message:" + deliveryTag +
                // " but no queue defined using DeadLetter queue:" + getDeadLetterQueue());
                //
                // deliveryContext.deliver(unacked.message, getDeadLetterQueue(), false);
                //
            }
        }
        else
        {
            _log.warn("Requested requeue of message:" + deliveryTag + " but no such delivery tag exists."
                      + _unacknowledgedMessageMap.size());

            if (_log.isDebugEnabled())
            {
                _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
                {
                    int count = 0;

                    public boolean callback(UnacknowledgedMessage message) throws AMQException
                    {
                        _log.debug(
                                (count++) + ": (" + message.getMessage().debugIdentity() + ")" + "[" + message.deliveryTag + "]");

                        return false; // Continue
                    }

                    public void visitComplete()
                    {
                    }
                });
            }
        }

    }

    /**
     * Called to resend all outstanding unacknowledged messages to this same channel.
     *
     * @param requeue Are the messages to be requeued or dropped.
     *
     * @throws AMQException When something goes wrong.
     */
    public void resend(final boolean requeue) throws AMQException
    {
        final List<UnacknowledgedMessage> msgToRequeue = new LinkedList<UnacknowledgedMessage>();
        final List<UnacknowledgedMessage> msgToResend = new LinkedList<UnacknowledgedMessage>();

        if (_log.isDebugEnabled())
        {
            _log.debug("unacked map Size:" + _unacknowledgedMessageMap.size());
        }

        // Process the Unacked-Map.
        // Marking messages who still have a consumer for to be resent
        // and those that don't to be requeued.
        _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
        {
            public boolean callback(UnacknowledgedMessage message) throws AMQException
            {
                AMQShortString consumerTag = message.consumerTag;
                AMQMessage msg = message.getMessage();
                msg.setRedelivered(true);
                if (consumerTag != null)
                {
                    // Consumer exists
                    if (_consumerTag2QueueMap.containsKey(consumerTag))
                    {
                        msgToResend.add(message);
                    }
                    else // consumer has gone
                    {
                        msgToRequeue.add(message);
                    }
                }
                else
                {
                    // Message has no consumer tag, so was "delivered" to a GET
                    // or consumer no longer registered
                    // cannot resend, so re-queue.
                    if (!message.isQueueDeleted())
                    {
                        if (requeue)
                        {
                            msgToRequeue.add(message);
                        }
                        else
                        {
                            _log.info("No DeadLetter Queue and requeue not requested so dropping message:" + message);
                        }
                    }
                    else
                    {
                        _log.info("Message.queue is null and no DeadLetter Queue so dropping message:" + message);
                    }
                }

                // false means continue processing
                return false;
            }

            public void visitComplete()
            {
            }
        });

        // Process Messages to Resend
        if (_log.isDebugEnabled())
        {
            if (!msgToResend.isEmpty())
            {
                _log.debug("Preparing (" + msgToResend.size() + ") message to resend.");
            }
            else
            {
                _log.debug("No message to resend.");
            }
        }

        for (UnacknowledgedMessage message : msgToResend)
        {
            AMQMessage msg = message.getMessage();

            // Our Java Client will always suspend the channel when resending!
            // If the client has requested the messages be resent then it is
            // their responsibility to ensure that thay are capable of receiving them
            // i.e. The channel hasn't been server side suspended.
            // if (isSuspended())
            // {
            // _log.info("Channel is suspended so requeuing");
            // //move this message to requeue
            // msgToRequeue.add(message);
            // }
            // else
            // {
            // release to allow it to be delivered
            message.entry.release();

            // Without any details from the client about what has been processed we have to mark
            // all messages in the unacked map as redelivered.
            msg.setRedelivered(true);

            Subscription sub = message.entry.getDeliveredSubscription();

            if (sub != null)
            {
                // Get the lock so we can tell if the sub scription has closed.
                // will stop delivery to this subscription until the lock is released.
                // note: this approach would allow the use of a single queue if the
                // PreDeliveryQueue would allow head additions.
                // In the Java Qpid client we are suspended whilst doing this so it is all rather Mute..
                // needs guidance from AMQP WG Model SIG
                synchronized (sub.getSendLock())
                {
                    if (sub.isClosed())
                    {
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Subscription(" + System.identityHashCode(sub)
                                       + ") closed during resend so requeuing message");
                        }
                        // move this message to requeue
                        msgToRequeue.add(message);
                    }
                    else
                    {
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Requeuing " + msg.debugIdentity() + " for resend via sub:"
                                       + System.identityHashCode(sub));
                        }

                        sub.addToResendQueue(message.entry);
                        _unacknowledgedMessageMap.remove(message.deliveryTag);
                    }
                } // sync(sub.getSendLock)
            }
            else
            {

                if (_log.isInfoEnabled())
                {
                    _log.info("DeliveredSubscription not recorded so just requeueing(" + message.toString()
                              + ")to prevent loss");
                }
                // move this message to requeue
                msgToRequeue.add(message);
            }
        } // for all messages
        // } else !isSuspend

        if (_log.isInfoEnabled())
        {
            if (!msgToRequeue.isEmpty())
            {
                _log.info("Preparing (" + msgToRequeue.size() + ") message to requeue to.");
            }
        }

        // Deliver these messages out of the transaction as their delivery was never
        // part of the transaction only the receive.
        TransactionalContext deliveryContext;
        if (!(_txnContext instanceof NonTransactionalContext))
        {
            if (_nonTransactedContext == null)
            {
                _nonTransactedContext =
                        new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages, _browsedAcks);
            }

            deliveryContext = _nonTransactedContext;
        }
        else
        {
            deliveryContext = _txnContext;
        }

        // Process Messages to Requeue at the front of the queue
        for (UnacknowledgedMessage message : msgToRequeue)
        {
            message.entry.release();
            message.entry.setRedelivered(true);

            deliveryContext.deliver(message.entry, true);

            _unacknowledgedMessageMap.remove(message.deliveryTag);
        }
    }

    /**
     * Callback indicating that a queue has been deleted. We must update the structure of unacknowledged messages to
     * remove the queue reference and also decrement any message reference counts, without actually removing the item
     * since we may get an ack for a delivery tag that was generated from the deleted queue.
     *
     * @param queue the queue that has been deleted
     *
     * @throws org.apache.qpid.AMQException if there is an error processing the unacked messages
     */
    public void queueDeleted(final AMQQueue queue) throws AMQException
    {
        _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
        {
            public boolean callback(UnacknowledgedMessage message) throws AMQException
            {
                if (message.getQueue() == queue)
                {
                    try
                    {
                        message.discard(_storeContext);
                        message.setQueueDeleted(true);
                        
                    }
                    catch (AMQException e)
                    {
                        _log.error(
                                "Error decrementing ref count on message " + message.getMessage().getMessageId() + ": " + e, e);
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
     *
     * @throws AMQException if the delivery tag is unknown (e.g. not outstanding) on this channel
     */
    public void acknowledgeMessage(long deliveryTag, boolean multiple) throws AMQException
    {
        synchronized (_unacknowledgedMessageMap.getLock())
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Unacked (PreAck) Size:" + _unacknowledgedMessageMap.size());
            }

            _unacknowledgedMessageMap.acknowledgeMessage(deliveryTag, multiple, _txnContext);

            if (_log.isDebugEnabled())
            {
                _log.debug("Unacked (PostAck) Size:" + _unacknowledgedMessageMap.size());
            }

        }

        checkSuspension();
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

    private void checkSuspension()
    {
        boolean suspend;

        suspend =
                ((_prefetch_HighWaterMark != 0) && (_unacknowledgedMessageMap.size() >= _prefetch_HighWaterMark))
                || ((_prefetchSize != 0) && (_prefetchSize < _unacknowledgedMessageMap.getUnacknowledgeBytes()));

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
                // may need to deliver queued messages
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
            session.getProtocolOutputConverter().writeReturn(message, _channelId, bouncedMessage.getReplyCode().getCode(),
                                                             new AMQShortString(bouncedMessage.getMessage()));

            message.decrementReference(_storeContext);
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
            boolean willSuspend =
                    ((_prefetch_HighWaterMark != 0) && ((_unacknowledgedMessageMap.size() + 1) > _prefetch_HighWaterMark));
            if (!willSuspend)
            {
                final long unackedSize = _unacknowledgedMessageMap.getUnacknowledgeBytes();

                willSuspend = (_prefetchSize != 0) && (unackedSize != 0) && (_prefetchSize < (msg.getSize() + unackedSize));
            }

            if (willSuspend)
            {
                setSuspended(true);
            }

            return willSuspend;
        }

    }

    public TransactionalContext getTransactionalContext()
    {
        return _txnContext;
    }

    public boolean isClosing()
    {
        return _closing;
    }
}
