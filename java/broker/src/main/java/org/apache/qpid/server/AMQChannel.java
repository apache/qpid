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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.ack.UnacknowledgedMessageMapImpl;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.NoRouteException;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.flow.Pre0_10CreditManager;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.UnauthorizedAccessException;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionFactoryImpl;
import org.apache.qpid.server.subscription.ClientDeliveryMethod;
import org.apache.qpid.server.subscription.RecordDeliveryMethod;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.txn.LocalTransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AMQChannel
{
    public static final int DEFAULT_PREFETCH = 5000;

    private static final Logger _log = Logger.getLogger(AMQChannel.class);

    private final int _channelId;


    private final Pre0_10CreditManager _creditManager = new Pre0_10CreditManager(0l,0l);

    /**
     * The delivery tag is unique per channel. This is pre-incremented before putting into the deliver frame so that
     * value of this represents the <b>last</b> tag sent out
     */
    private long _deliveryTag = 0;

    /** A channel has a default queue (the last declared) that is used when no queue name is explictily set */
    private AMQQueue _defaultQueue;

    /** This tag is unique per subscription to a queue. The server returns this in response to a basic.consume request. */
    private int _consumerTag;

    /**
     * The current message - which may be partial in the sense that not all frames have been received yet - which has
     * been received by this channel. As the frames are received the message gets updated and once all frames have been
     * received the message can then be routed.
     */
    private IncomingMessage _currentMessage;

    /** Maps from consumer tag to subscription instance. Allows us to unsubscribe from a queue. */
    protected final Map<AMQShortString, Subscription> _tag2SubscriptionMap = new HashMap<AMQShortString, Subscription>();

    private final MessageStore _messageStore;

    private UnacknowledgedMessageMap _unacknowledgedMessageMap = new UnacknowledgedMessageMapImpl(DEFAULT_PREFETCH);

    private final AtomicBoolean _suspended = new AtomicBoolean(false);

    private TransactionalContext _txnContext;

    /**
     * A context used by the message store enabling it to track context for a given channel even across thread
     * boundaries
     */
    private final StoreContext _storeContext;

    private final List<RequiredDeliveryException> _returnMessages = new LinkedList<RequiredDeliveryException>();

    private MessageHandleFactory _messageHandleFactory = new MessageHandleFactory();

    // Why do we need this reference ? - ritchiem
    private final AMQProtocolSession _session;
    private boolean _closing; 

    public AMQChannel(AMQProtocolSession session, int channelId, MessageStore messageStore)
            throws AMQException
    {
        //Set values from configuration
        Configurator.configure(this);

        _session = session;
        _channelId = channelId;
        _storeContext = new StoreContext("Session: " + session.getClientIdentifier() + "; channel: " + channelId);


        _messageStore = messageStore;

        // by default the session is non-transactional
        _txnContext = new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages);
    }

    /** Sets this channel to be part of a local transaction */
    public void setLocalTransactional()
    {
        _txnContext = new LocalTransactionalContext(this);
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

    public void setPublishFrame(MessagePublishInfo info, final Exchange e) throws AMQException
    {

        _currentMessage = new IncomingMessage(_messageStore.getNewMessageId(), info, _txnContext, _session);
        _currentMessage.setMessageStore(_messageStore);
        _currentMessage.setExchange(e);
    }

    public void publishContentHeader(ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        if (_currentMessage == null)
        {
            throw new AMQException("Received content header without previously receiving a BasicPublish frame");
        }
        else
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("Content header received on channel " + _channelId);
            }

            _currentMessage.setContentHeaderBody(contentHeaderBody);

            _currentMessage.setExpiration();

            routeCurrentMessage();

            _currentMessage.routingComplete(_messageStore, _messageHandleFactory);

            deliverCurrentMessageIfComplete();

        }
    }

    private void deliverCurrentMessageIfComplete()
            throws AMQException
    {
        // check and deliver if header says body length is zero
        if (_currentMessage.allContentReceived())
        {
            try
            {
                _currentMessage.deliverToQueues();
            }
            catch (NoRouteException e)
            {
                _returnMessages.add(e);
            }
            catch(UnauthorizedAccessException ex)
            {
                _returnMessages.add(ex);
            }
            finally
            {
                // callback to allow the context to do any post message processing
                // primary use is to allow message return processing in the non-tx case
                _txnContext.messageProcessed(_session);
                _currentMessage = null;
            }
        }

    }

    public void publishContentBody(ContentBody contentBody) throws AMQException
    {
        if (_currentMessage == null)
        {
            throw new AMQException("Received content body without previously receiving a JmsPublishBody");
        }

        if (_log.isDebugEnabled())
        {
            _log.debug(debugIdentity() + "Content body received on channel " + _channelId);
        }

        try
        {

            // returns true iff the message was delivered (i.e. if all data was
            // received
            _currentMessage.addContentBodyFrame(
                    _session.getMethodRegistry().getProtocolVersionMethodConverter().convertToContentChunk(
                            contentBody));

            deliverCurrentMessageIfComplete();
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
            _currentMessage.route();            
        }
        catch (NoRouteException e)
        {
            //_currentMessage.incrementReference();
            _returnMessages.add(e);
        }
    }

    public long getNextDeliveryTag()
    {
        return ++_deliveryTag;
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
     * @param acks      Are acks enabled for this subscriber
     * @param filters   Filters to apply to this subscriber
     *
     * @param noLocal   Flag stopping own messages being receivied.
     * @param exclusive Flag requesting exclusive access to the queue
     * @return the consumer tag. This is returned to the subscriber and used in subsequent unsubscribe requests
     *
     * @throws ConsumerTagNotUniqueException if the tag is not unique
     * @throws AMQException                  if something goes wrong
     */
    public AMQShortString subscribeToQueue(AMQShortString tag, AMQQueue queue, boolean acks,
                                           FieldTable filters, boolean noLocal, boolean exclusive) throws AMQException, ConsumerTagNotUniqueException
    {
        if (tag == null)
        {
            tag = new AMQShortString("sgen_" + getNextConsumerTag());
        }

        if (_tag2SubscriptionMap.containsKey(tag))
        {
            throw new ConsumerTagNotUniqueException();
        }

         Subscription subscription =
                SubscriptionFactoryImpl.INSTANCE.createSubscription(_channelId, _session, tag, acks, filters, noLocal, _creditManager);


        // So to keep things straight we put before the call and catch all exceptions from the register and tidy up.
        // We add before we register as the Async Delivery process may AutoClose the subscriber
        // so calling _cT2QM.remove before we have done put which was after the register succeeded.
        // So to keep things straight we put before the call and catch all exceptions from the register and tidy up.

        _tag2SubscriptionMap.put(tag, subscription);

        try
        {
            queue.registerSubscription(subscription, exclusive);
        }
        catch (AMQException e)
        {
            _tag2SubscriptionMap.remove(tag);
            throw e;
        }
        return tag;
    }

    /**
     * Unsubscribe a consumer from a queue.
     * @param consumerTag
     * @return true if the consumerTag had a mapped queue that could be unregistered.
     * @throws AMQException
     */
    public boolean unsubscribeConsumer(AMQShortString consumerTag) throws AMQException
    {

        Subscription sub = _tag2SubscriptionMap.remove(consumerTag);
        if (sub != null)
        {
            try 
            {
                sub.getSendLock();
                sub.getQueue().unregisterSubscription(sub);
            }
            finally 
            {
                sub.releaseSendLock();
            }
            return true;
        }
        else
        {
            _log.warn("Attempt to unsubscribe consumer with tag '"+consumerTag+"' which is not registered.");
        }
        return false;
    }

    /**
     * Called from the protocol session to close this channel and clean up. T
     *
     * @throws AMQException if there is an error during closure
     */
    public void close() throws AMQException
    {
        _txnContext.rollback();
        unsubscribeAllConsumers();
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

    private void unsubscribeAllConsumers() throws AMQException
    {
        if (_log.isInfoEnabled())
        {
            if (!_tag2SubscriptionMap.isEmpty())
            {
                _log.info("Unsubscribing all consumers on channel " + toString());
            }
            else
            {
                _log.info("No consumers to unsubscribe on channel " + toString());
            }
        }

        for (Map.Entry<AMQShortString, Subscription> me : _tag2SubscriptionMap.entrySet())
        {
            if (_log.isInfoEnabled())
            {
                _log.info("Unsubscribing consumer '" + me.getKey() + "' on channel " + toString());
            }

            Subscription sub = me.getValue();

            try
            {
                sub.getSendLock();
                sub.getQueue().unregisterSubscription(sub);
            }
            finally
            {
                sub.releaseSendLock();
            }
            
        }

        _tag2SubscriptionMap.clear();
    }

    /**
     * Add a message to the channel-based list of unacknowledged messages
     *
     * @param entry       the record of the message on the queue that was delivered
     * @param deliveryTag the delivery tag used when delivering the message (see protocol spec for description of the
     *                    delivery tag)
     * @param subscription The consumer that is to acknowledge this message.
     */
    public void addUnacknowledgedMessage(QueueEntry entry, long deliveryTag, Subscription subscription)
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
                               + ") with a queue(" + entry.getQueue() + ") for " + subscription);
                }
            }
        }

        _unacknowledgedMessageMap.add(deliveryTag, entry);

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
        Collection<QueueEntry> messagesToBeDelivered = _unacknowledgedMessageMap.cancelAllMessages();

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

                    deliveryContext =
                            new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages);
            }
            else
            {
                deliveryContext = _txnContext;
            }
        }

        for (QueueEntry unacked : messagesToBeDelivered)
        {
            if (!unacked.isQueueDeleted())
            {
                // Mark message redelivered
                unacked.getMessage().setRedelivered(true);

                // Ensure message is released for redelivery
                unacked.release();

                // Deliver Message
                deliveryContext.requeue(unacked);

            }
            else
            {
                unacked.discard(_storeContext);
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
        QueueEntry unacked = _unacknowledgedMessageMap.remove(deliveryTag);

        if (unacked != null)
        {
            // Mark message redelivered
            unacked.getMessage().setRedelivered(true);

            // Ensure message is released for redelivery
            if (!unacked.isQueueDeleted())
            {
                unacked.release();
            }


            // Deliver these messages out of the transaction as their delivery was never
            // part of the transaction only the receive.
            TransactionalContext deliveryContext;
            if (!(_txnContext instanceof NonTransactionalContext))
            {

                deliveryContext =
                            new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages);

            }
            else
            {
                deliveryContext = _txnContext;
            }

            if (!unacked.isQueueDeleted())
            {
                // Redeliver the messages to the front of the queue
                deliveryContext.requeue(unacked);
                // Deliver increments the message count but we have already deliverted this once so don't increment it again
                // this was because deliver did an increment changed this.
            }
            else
            {
                _log.warn(System.identityHashCode(this) + " Requested requeue of message(" + unacked.getMessage().debugIdentity()
                          + "):" + deliveryTag + " but no queue defined and no DeadLetter queue so DROPPING message.");

                unacked.discard(_storeContext);
            }
        }
        else
        {
            _log.warn("Requested requeue of message:" + deliveryTag + " but no such delivery tag exists."
                      + _unacknowledgedMessageMap.size());

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


        final Map<Long, QueueEntry> msgToRequeue = new LinkedHashMap<Long, QueueEntry>();
        final Map<Long, QueueEntry> msgToResend = new LinkedHashMap<Long, QueueEntry>();

        if (_log.isDebugEnabled())
        {
            _log.debug("unacked map Size:" + _unacknowledgedMessageMap.size());
        }

        // Process the Unacked-Map.
        // Marking messages who still have a consumer for to be resent
        // and those that don't to be requeued.
        _unacknowledgedMessageMap.visit(new ExtractResendAndRequeue(_unacknowledgedMessageMap, msgToRequeue,
                                                                    msgToResend, requeue, _storeContext));


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

        for (Map.Entry<Long, QueueEntry> entry : msgToResend.entrySet())
        {
            QueueEntry message = entry.getValue();
            long deliveryTag = entry.getKey();



            AMQMessage msg = message.getMessage();
            AMQQueue queue = message.getQueue();

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

            // Without any details from the client about what has been processed we have to mark
            // all messages in the unacked map as redelivered.
            msg.setRedelivered(true);

            Subscription sub = message.getDeliveredSubscription();

            if (sub != null)
            {
                
                if(!queue.resend(message, sub))
                {
                    msgToRequeue.put(deliveryTag, message);
                }
            }
            else
            {

                if (_log.isInfoEnabled())
                {
                    _log.info("DeliveredSubscription not recorded so just requeueing(" + message.toString()
                              + ")to prevent loss");
                }
                // move this message to requeue
                msgToRequeue.put(deliveryTag, message);
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

            deliveryContext =
                        new NonTransactionalContext(_messageStore, _storeContext, this, _returnMessages);
        }
        else
        {
            deliveryContext = _txnContext;
        }

        // Process Messages to Requeue at the front of the queue
        for (Map.Entry<Long, QueueEntry> entry : msgToRequeue.entrySet())
        {
            QueueEntry message = entry.getValue();
            long deliveryTag = entry.getKey();
            
            message.release();
            message.setRedelivered(true);

            deliveryContext.requeue(message);

            _unacknowledgedMessageMap.remove(deliveryTag);
        }
    }

    /**
     * Callback indicating that a queue has been deleted. We must update the structure of unacknowledged messages to
     * remove the queue reference and also decrement any message reference counts, without actually removing the item
     * since we may get an ack for a delivery tag that was generated from the deleted queue.
     *
     * @param queue the queue that has been deleted
     *
     */
 /*   public void queueDeleted(final AMQQueue queue)
    {
        try
        {
            _unacknowledgedMessageMap.visit(new UnacknowledgedMessageMap.Visitor()
            {
                public boolean callback(UnacknowledgedMessage message)
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
                            throw new RuntimeException(e);
                        }
                    }

                    return false;
                }

                public void visitComplete()
                {
                }
            });
        }
        catch (AMQException e)
        {
            _log.error("Unexpected Error while handling deletion of queue", e);
            throw new RuntimeException(e);
        }

    }
*/
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
        _unacknowledgedMessageMap.acknowledgeMessage(deliveryTag, multiple, _txnContext);
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


    public void setSuspended(boolean suspended)
    {


        boolean wasSuspended = _suspended.getAndSet(suspended);
        if (wasSuspended != suspended)
        {
            if (wasSuspended)
            {
                // may need to deliver queued messages
                for (Subscription s : _tag2SubscriptionMap.values())
                {
                    s.getQueue().deliverAsync(s);
                }
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
        return "["+_session.toString()+":"+_channelId+"]";
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

    public void processReturns() throws AMQException
    {
        if (!_returnMessages.isEmpty())
        {
            for (RequiredDeliveryException bouncedMessage : _returnMessages)
            {
                AMQMessage message = bouncedMessage.getAMQMessage();
                _session.getProtocolOutputConverter().writeReturn(message, _channelId, bouncedMessage.getReplyCode().getCode(),
                                                                 new AMQShortString(bouncedMessage.getMessage()));

                message.decrementReference(_storeContext);
            }

            _returnMessages.clear();
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

    public AMQProtocolSession getProtocolSession()
    {
        return _session;
    }

    public FlowCreditManager getCreditManager()
    {
        return _creditManager;
    }

    public void setCredit(final long prefetchSize, final int prefetchCount)
    {
        _creditManager.setCreditLimits(prefetchSize, prefetchCount);
    }

    public List<RequiredDeliveryException> getReturnMessages()
    {
        return _returnMessages;
    }

    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    private final ClientDeliveryMethod _clientDeliveryMethod = new ClientDeliveryMethod()
        {

            public void deliverToClient(final Subscription sub, final QueueEntry entry, final long deliveryTag)
                    throws AMQException
            {
               getProtocolSession().getProtocolOutputConverter().writeDeliver(entry.getMessage(), getChannelId(), deliveryTag, sub.getConsumerTag());
            }
        };

    public ClientDeliveryMethod getClientDeliveryMethod()
    {
        return _clientDeliveryMethod;
    }

    private final RecordDeliveryMethod _recordDeliveryMethod = new RecordDeliveryMethod()
        {

            public void recordMessageDelivery(final Subscription sub, final QueueEntry entry, final long deliveryTag)
            {
                addUnacknowledgedMessage(entry, deliveryTag, sub);
            }
        };

    public RecordDeliveryMethod getRecordDeliveryMethod()
    {
        return _recordDeliveryMethod;
    }
}
