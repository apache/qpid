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
import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.util.ConcurrentLinkedMessageQueueAtomicSize;
import org.apache.qpid.util.MessageQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;


/** Manages delivery of messages on behalf of a queue */
public class ConcurrentSelectorDeliveryManager implements DeliveryManager
{
    private static final Logger _log = Logger.getLogger(ConcurrentSelectorDeliveryManager.class);

    @Configured(path = "advanced.compressBufferOnQueue",
                defaultValue = "false")
    public boolean compressBufferOnQueue;
    /** Holds any queued messages */
    private final MessageQueue<AMQMessage> _messages = new ConcurrentLinkedMessageQueueAtomicSize<AMQMessage>();

    /** Ensures that only one asynchronous task is running for this manager at any time. */
    private final AtomicBoolean _processing = new AtomicBoolean();
    /** The subscriptions on the queue to whom messages are delivered */
    private final SubscriptionManager _subscriptions;

    /**
     * A reference to the queue we are delivering messages for. We need this to be able to pass the code that handles
     * acknowledgements a handle on the queue.
     */
    private final AMQQueue _queue;

    /**
     * Flag used while moving messages from this queue to another. For moving messages the async delivery should also
     * stop. This flat should be set to true to stop async delivery and set to false to enable async delivery again.
     */
    private AtomicBoolean _movingMessages = new AtomicBoolean();

    /**
     * Lock used to ensure that an channel that becomes unsuspended during the start of the queueing process is forced
     * to wait till the first message is added to the queue. This will ensure that the _queue has messages to be
     * delivered via the async thread. <p/> Lock is used to control access to hasQueuedMessages() and over the addition
     * of messages to the queue.
     */
    private ReentrantLock _lock = new ReentrantLock();
    private AtomicLong _totalMessageSize = new AtomicLong();
    private AtomicInteger _extraMessages = new AtomicInteger();
    private Set<Subscription> _hasContent = Collections.synchronizedSet(new HashSet<Subscription>());
    private final Object _queueHeadLock = new Object();
    private String _processingThreadName = "";


    /** Used by any reaping thread to purge messages */
    private StoreContext _reapingStoreContext = new StoreContext();

    ConcurrentSelectorDeliveryManager(SubscriptionManager subscriptions, AMQQueue queue)
    {

        //Set values from configuration
        Configurator.configure(this);

        if (compressBufferOnQueue)
        {
            _log.warn("Compressing Buffers on queue.");
        }

        _subscriptions = subscriptions;
        _queue = queue;
    }


    private boolean addMessageToQueue(AMQMessage msg, boolean deliverFirst)
    {
        // Shrink the ContentBodies to their actual size to save memory.
        if (compressBufferOnQueue)
        {
            Iterator<ContentChunk> it = msg.getContentBodyIterator();
            while (it.hasNext())
            {
                ContentChunk cb = it.next();
                cb.reduceToFit();
            }
        }

        if (deliverFirst)
        {
            synchronized (_queueHeadLock)
            {
                _messages.pushHead(msg);
            }
        }
        else
        {
            _messages.offer(msg);
        }

        _totalMessageSize.addAndGet(msg.getSize());

        return true;
    }


    public boolean hasQueuedMessages()
    {
        _lock.lock();
        try
        {
            return !(_messages.isEmpty() && _hasContent.isEmpty());
        }
        finally
        {
            _lock.unlock();
        }
    }

    public int getQueueMessageCount()
    {
        return getMessageCount();
    }

    /**
     * This is an EXPENSIVE opperation to perform with a ConcurrentLinkedQueue as it must run the queue to determine
     * size. The ConcurrentLinkedQueueAtomicSize uses an AtomicInteger to record the number of elements on the queue.
     *
     * @return int the number of messages in the delivery queue.
     */
    private int getMessageCount()
    {
        return _messages.size() + _extraMessages.get();
    }


    public long getTotalMessageSize()
    {
        return _totalMessageSize.get();
    }

    public long getOldestMessageArrival()
    {
        AMQMessage msg = _messages.peek();
        return msg == null ? Long.MAX_VALUE : msg.getArrivalTime();
    }

    public void subscriberHasPendingResend(boolean hasContent, Subscription subscription, AMQMessage msg)
    {
        _lock.lock();
        try
        {
            if (hasContent)
            {
                _log.debug("Queue has adding subscriber content");
                _hasContent.add(subscription);
                _totalMessageSize.addAndGet(msg.getSize());
                _extraMessages.addAndGet(1);
            }
            else
            {
                _log.debug("Queue has removing subscriber content");
                if (msg == null)
                {
                    _hasContent.remove(subscription);
                }
                else
                {
                    _totalMessageSize.addAndGet(-msg.getSize());
                    _extraMessages.addAndGet(-1);
                }
            }
        }
        finally
        {
            _lock.unlock();
        }
    }

    /** @return the state of the async processor. */
    public boolean isProcessingAsync()
    {
        return _processing.get();
    }

    /**
     * Returns all the messages in the Queue
     *
     * @return List of messages
     */
    public List<AMQMessage> getMessages()
    {
        _lock.lock();
        List<AMQMessage> list = new ArrayList<AMQMessage>();

        for (AMQMessage message : _messages)
        {
            list.add(message);
        }
        _lock.unlock();

        return list;
    }

    /**
     * Returns messages within the range of given messageIds
     *
     * @param fromMessageId
     * @param toMessageId
     *
     * @return
     */
    public List<AMQMessage> getMessages(long fromMessageId, long toMessageId)
    {
        if (fromMessageId <= 0 || toMessageId <= 0)
        {
            return null;
        }

        long maxMessageCount = toMessageId - fromMessageId + 1;

        _lock.lock();

        List<AMQMessage> foundMessagesList = new ArrayList<AMQMessage>();

        for (AMQMessage message : _messages)
        {
            long msgId = message.getMessageId();
            if (msgId >= fromMessageId && msgId <= toMessageId)
            {
                foundMessagesList.add(message);
            }
            // break if the no of messages are found
            if (foundMessagesList.size() == maxMessageCount)
            {
                break;
            }
        }
        _lock.unlock();

        return foundMessagesList;
    }

    public void populatePreDeliveryQueue(Subscription subscription)
    {
        if (_log.isTraceEnabled())
        {
            _log.trace("Populating PreDeliveryQueue for Subscription(" + System.identityHashCode(subscription) + ")");
        }

        Iterator<AMQMessage> currentQueue = _messages.iterator();

        while (currentQueue.hasNext())
        {
            AMQMessage message = currentQueue.next();
            if (!message.getDeliveredToConsumer())
            {
                if (subscription.hasInterest(message))
                {
                    subscription.enqueueForPreDelivery(message, false);
                }
            }
        }
    }

    public boolean performGet(AMQProtocolSession protocolSession, AMQChannel channel, boolean acks) throws AMQException
    {
        AMQMessage msg = getNextMessage();
        if (msg == null)
        {
            return false;
        }
        else
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
                if (!acks)
                {
                    if (_log.isDebugEnabled())
                    {
                        _log.debug("No ack mode so dequeuing message immediately: " + msg.getMessageId());
                    }
                    _queue.dequeue(channel.getStoreContext(), msg);
                }
                synchronized (channel)
                {
                    long deliveryTag = channel.getNextDeliveryTag();

                    if (acks)
                    {
                        channel.addUnacknowledgedMessage(msg, deliveryTag, null, _queue);
                    }

                    protocolSession.getProtocolOutputConverter().writeGetOk(msg, channel.getChannelId(),
                                                                            deliveryTag, _queue.getMessageCount());
                    _totalMessageSize.addAndGet(-msg.getSize());
                }

                if (!acks)
                {
                    msg.decrementReference(channel.getStoreContext());
                }
            }
            finally
            {
                msg.setDeliveredToConsumer();
            }
            return true;

        }
    }

    /**
     * For feature of moving messages, this method is used. It sets the lock and sets the movingMessages flag, so that
     * the asyn delivery is also stopped.
     */
    public void startMovingMessages()
    {
        _movingMessages.set(true);
    }

    /**
     * Once moving messages to another queue is done or aborted, remove lock and unset the movingMessages flag, so that
     * the async delivery can start again.
     */
    public void stopMovingMessages()
    {
        _movingMessages.set(false);
        if (_lock.isHeldByCurrentThread())
        {
            _lock.unlock();
        }
    }

    /**
     * Messages will be removed from this queue and all preDeliveryQueues
     *
     * @param messageList
     */
    public void removeMovedMessages(List<AMQMessage> messageList)
    {
        // Remove from the
        boolean hasSubscribers = _subscriptions.hasActiveSubscribers();
        if (hasSubscribers)
        {
            for (Subscription sub : _subscriptions.getSubscriptions())
            {
                if (!sub.isSuspended() && sub.filtersMessages())
                {
                    Queue<AMQMessage> preDeliveryQueue = sub.getPreDeliveryQueue();
                    for (AMQMessage msg : messageList)
                    {
                        preDeliveryQueue.remove(msg);
                    }
                }
            }
        }

        for (AMQMessage msg : messageList)
        {
            if (_messages.remove(msg))
            {
                _totalMessageSize.getAndAdd(-msg.getSize());
            }
        }
    }

    /**
     * Now with implementation of predelivery queues, this method will mark the message on the top as taken.
     *
     * @param storeContext
     *
     * @throws AMQException
     */
    public void removeAMessageFromTop(StoreContext storeContext, AMQQueue queue) throws AMQException
    {
        _lock.lock();

        AMQMessage message = _messages.poll();

        if (message != null)
        {
            queue.dequeue(storeContext, message);

            _totalMessageSize.addAndGet(-message.getSize());

            //If this causes ref count to hit zero then data will be purged so message.getSize() will NPE.
            message.decrementReference(storeContext);

        }

        _lock.unlock();
    }

    public long clearAllMessages(StoreContext storeContext) throws AMQException
    {
        long count = 0;
        _lock.lock();

        synchronized (_queueHeadLock)
        {
            AMQMessage msg = getNextMessage();
            while (msg != null)
            {
                //and remove it
                _messages.poll();

                _queue.dequeue(storeContext, msg);

                msg.decrementReference(_reapingStoreContext);

                msg = getNextMessage();
                count++;
            }
            _totalMessageSize.set(0L);
        }
        _lock.unlock();
        return count;
    }

    /**
     * This can only be used to clear the _messages queue. Any subscriber resend queue will not be purged.
     *
     * @return the next message or null
     *
     * @throws org.apache.qpid.AMQException
     */
    private AMQMessage getNextMessage() throws AMQException
    {
        return getNextMessage(_messages, null, false);
    }

    private AMQMessage getNextMessage(Queue<AMQMessage> messages, Subscription sub, boolean purgeOnly) throws AMQException
    {
        AMQMessage message = messages.peek();

        //while (we have a message) && ((The subscriber is not a browser or message is taken ) or we are clearing) && (Check message is taken.)
        while (purgeMessage(message, sub, purgeOnly))
        {
            // if we are purging then ensure we mark this message taken for the current subscriber
            // the current subscriber may be null in the case of a get or a purge but this is ok.
//            boolean alreadyTaken = message.taken(_queue, sub);

            //remove the already taken message or expired
            AMQMessage removed = messages.poll();

            assert removed == message;

            // if the message expired then the _totalMessageSize needs adjusting
            if (message.expired(_queue) && !message.getDeliveredToConsumer())
            {
                _totalMessageSize.addAndGet(-message.getSize());

                // Use the reapingStoreContext as any sub(if we have one) may be in a tx.
                _queue.dequeue(_reapingStoreContext, message);

                message.decrementReference(_reapingStoreContext);

                if (_log.isInfoEnabled())
                {
                    _log.info(debugIdentity() + " Doing clean up of the main _message queue.");
                }
            }

            //else the clean up is not required as the message has already been taken for this queue therefore
            // it was the responsibility of the code that took the message to ensure the _totalMessageSize was updated.

            if (_log.isTraceEnabled())
            {
                _log.trace("Removed taken message:" + message.debugIdentity());
            }

            // try the next message
            message = messages.peek();
        }

        return message;
    }

    /**
     * This method will return true if the message is to be purged from the queue.
     *
     *
     * SIDE-EFFECT: The message will be taken by the Subscription(sub) for the current Queue(_queue)
     *
     * @param message
     * @param sub
     *
     * @return
     *
     * @throws AMQException
     */
    private boolean purgeMessage(AMQMessage message, Subscription sub) throws AMQException
    {
        return purgeMessage(message, sub, false);
    }

    /**
     * This method will return true if the message is to be purged from the queue.
     * \
     * SIDE-EFFECT: The msg will be taken by the Subscription(sub) for the current Queue(_queue) when purgeOnly is false
     *
     * @param message
     * @param sub
     * @param purgeOnly When set to false the message will be taken by the given Subscription.
     *
     * @return if the msg should be purged
     *
     * @throws AMQException
     */
    private boolean purgeMessage(AMQMessage message, Subscription sub, boolean purgeOnly) throws AMQException
    {
        //Original.. complicated while loop control
//                (message != null
//                            && (
//                ((sub != null && !sub.isBrowser()) || message.isTaken(_queue))
//                || sub == null)
//                            && message.taken(_queue, sub));

        boolean purge = false;

        // if the message is null then don't purge as we have no messagse.
        if (message != null)
        {
            // Check that the message hasn't expired.
            if (message.expired(_queue))
            {
                return true;
            }

            // if we have a subscriber perform message checks
            if (sub != null)
            {
                // if we have a queue browser(we don't purge) so check mark the message as taken
                purge = ((!sub.isBrowser() || message.isTaken(_queue)));
            }
            else
            {
                // if there is no subscription we are doing
                // a get or purging so mark message as taken.
                message.isTaken(_queue);
                // and then ensure that it gets purged
                purge = true;
            }
        }

        if (purgeOnly)
        {
            // If we are simply purging the queue don't take the message
            // just purge up to the next non-taken msg.
            return purge && message.isTaken(_queue);
        }
        else
        {
            // if we are purging then ensure we mark this message taken for the current subscriber
            // the current subscriber may be null in the case of a get or a purge but this is ok.
            return purge && message.taken(_queue, sub);
        }
    }

    public void sendNextMessage(Subscription sub, AMQQueue queue)//Queue<AMQMessage> messageQueue)
    {

        Queue<AMQMessage> messageQueue = sub.getNextQueue(_messages);

        if (_log.isTraceEnabled())
        {
            _log.trace(debugIdentity() + "Async sendNextMessage for sub (" + System.identityHashCode(sub) +
                       ") from queue (" + System.identityHashCode(messageQueue) +
                       ") AMQQueue (" + System.identityHashCode(queue) + ")");
        }

        if (messageQueue == null)
        {
            // There is no queue with messages currently. This is ok... just means the queue has no msgs matching selector
            if (_log.isInfoEnabled())
            {
                _log.info(debugIdentity() + sub + ": asked to send messages but has none on given queue:" + queue);
            }
            return;
        }

        AMQMessage message = null;
        AMQMessage removed = null;
        try
        {
            synchronized (_queueHeadLock)
            {
                message = getNextMessage(messageQueue, sub, false);

                // message will be null if we have no messages in the messageQueue.
                if (message == null)
                {
                    if (_log.isTraceEnabled())
                    {
                        _log.trace(debugIdentity() + "No messages for Subscriber(" + System.identityHashCode(sub) + ") from queue; (" + System.identityHashCode(messageQueue) + ")");
                    }
                    return;
                }
                if (_log.isDebugEnabled())
                {
                    _log.debug(debugIdentity() + "Async Delivery Message :" + message + "(" + System.identityHashCode(message) +
                               ") by :" + System.identityHashCode(this) +
                               ") to :" + System.identityHashCode(sub));
                }


                if (messageQueue == _messages)
                {
                    _totalMessageSize.addAndGet(-message.getSize());
                }

                sub.send(message, _queue);

                //remove sent message from our queue.
                removed = messageQueue.poll();
                //If we don't remove the message from _messages
                // Otherwise the Async send will never end
            }

            if (removed != message)
            {
                _log.error("Just send message:" + message.debugIdentity() + " BUT removed this from queue:" + removed);
            }

            if (_log.isDebugEnabled())
            {
                _log.debug(debugIdentity() + "Async Delivered Message r:" + removed.debugIdentity() + "d:" + message +
                           ") by :" + System.identityHashCode(this) +
                           ") to :" + System.identityHashCode(sub));
            }


            if (messageQueue == sub.getResendQueue())
            {
                if (_log.isTraceEnabled())
                {
                    _log.trace(debugIdentity() + "All messages sent from resendQueue for " + sub);
                }
                if (messageQueue.isEmpty())
                {
                    subscriberHasPendingResend(false, sub, null);
                    //better to use the above method as this keeps all the tracking in one location.
                    // _hasContent.remove(sub);
                }

                _extraMessages.decrementAndGet();
            }
            else if (messageQueue == sub.getPreDeliveryQueue() && !sub.isBrowser())
            {
                if (_log.isInfoEnabled())
                {
                    //fixme - we should do the clean up as the message remains on the _message queue
                    // this is resulting in the next consumer receiving the message and then attempting to purge it
                    //
                    cleanMainQueue(sub);
                }
            }

        }
        catch (AMQException e)
        {
            if (message != null)
            {
                message.release(_queue);
            }
            else
            {
                _log.error(debugIdentity() + "Unable to release message as it is null. " + e, e);
            }
            _log.error(debugIdentity() + "Unable to deliver message as dequeue failed: " + e, e);
        }
    }

    private void cleanMainQueue(Subscription sub)
    {
        try
        {
            getNextMessage(_messages, sub, true);
        }
        catch (AMQException e)
        {
            _log.warn("Problem during main queue purge:" + e.getMessage());
        }
    }

    /**
     * enqueues the messages in the list on the queue and all required predelivery queues
     *
     * @param storeContext
     * @param movedMessageList
     */
    public void enqueueMovedMessages(StoreContext storeContext, List<AMQMessage> movedMessageList)
    {
        _lock.lock();
        for (AMQMessage msg : movedMessageList)
        {
            addMessageToQueue(msg, false);
        }

        // enqueue on the pre delivery queues
        for (Subscription sub : _subscriptions.getSubscriptions())
        {
            for (AMQMessage msg : movedMessageList)
            {
                // Only give the message to those that want them.
                if (sub.hasInterest(msg))
                {
                    sub.enqueueForPreDelivery(msg, true);
                }
            }
        }
        _lock.unlock();
    }

    /**
     * Only one thread should ever execute this method concurrently, but it can do so while other threads invoke
     * deliver().
     */
    private void processQueue()
    {
        //record thread name
        if (_log.isDebugEnabled())
        {
            _processingThreadName = Thread.currentThread().getName();
        }

        if (_log.isDebugEnabled())
        {
            _log.debug(debugIdentity() + "Running process Queue." + currentStatus());
        }

        // Continue to process delivery while we haveSubscribers and messages
        boolean hasSubscribers = _subscriptions.hasActiveSubscribers();

        while (hasSubscribers && hasQueuedMessages() && !_movingMessages.get())
        {
            hasSubscribers = false;

            for (Subscription sub : _subscriptions.getSubscriptions())
            {
                synchronized (sub.getSendLock())
                {
                    if (!sub.isSuspended())
                    {
                        sendNextMessage(sub, _queue);

                        hasSubscribers = true;
                    }
                }
            }
        }

        if (_log.isDebugEnabled())
        {
            _log.debug(debugIdentity() + "Done process Queue." + currentStatus());
        }

    }

    public void deliver(StoreContext context, AMQShortString name, AMQMessage msg, boolean deliverFirst) throws AMQException
    {

        final boolean debugEnabled = _log.isDebugEnabled();
        if (debugEnabled)
        {
            _log.debug(debugIdentity() + "deliver :first(" + deliverFirst + ") :" + msg);
        }

        //Check if we have someone to deliver the message to.
        _lock.lock();
        try
        {
            Subscription s = _subscriptions.nextSubscriber(msg);

            if (s == null || (!s.filtersMessages() && hasQueuedMessages())) //no-one can take the message right now or we're queueing
            {
                if (debugEnabled)
                {
                    _log.debug(debugIdentity() + "Testing Message(" + msg + ") for Queued Delivery:" + currentStatus());
                }
                if (!msg.getMessagePublishInfo().isImmediate())
                {
                    addMessageToQueue(msg, deliverFirst);

                    //release lock now message is on queue.
                    _lock.unlock();

                    //Pre Deliver to all subscriptions
                    if (debugEnabled)
                    {
                        _log.debug(debugIdentity() + "We have " + _subscriptions.getSubscriptions().size() +
                                   " subscribers to give the message to:" + currentStatus());
                    }
                    for (Subscription sub : _subscriptions.getSubscriptions())
                    {

                        // stop if the message gets delivered whilst PreDelivering if we have a shared queue.
                        if (_queue.isShared() && msg.getDeliveredToConsumer())
                        {
                            if (debugEnabled)
                            {
                                _log.debug(debugIdentity() + "Stopping PreDelivery as message(" + System.identityHashCode(msg) +
                                           ") is already delivered.");
                            }
                            continue;
                        }

                        // Only give the message to those that want them.
                        if (sub.hasInterest(msg))
                        {
                            if (debugEnabled)
                            {
                                _log.debug(debugIdentity() + "Queuing message(" + System.identityHashCode(msg) +
                                           ") for PreDelivery for subscriber(" + System.identityHashCode(sub) + ")");
                            }
                            sub.enqueueForPreDelivery(msg, deliverFirst);
                        }
                    }

                    //if  we have a non-filtering subscriber but queued messages && we're not Async && we have other Active subs then something is wrong!
                     if ((s != null && hasQueuedMessages()) && !isProcessingAsync() && _subscriptions.hasActiveSubscribers())
                     {
                         _queue.deliverAsync();
                     }

                }
            }
            else
            {

                if (s.filtersMessages())
                {
                    if (s.getPreDeliveryQueue().size() > 0)
                    {
                        _log.error("Direct delivery from PDQ with queued msgs:" + s.getPreDeliveryQueue().size());
                    }
                }
                else if (_messages.size() > 0)
                {
                    _log.error("Direct delivery from MainQueue queued msgs:" + _messages.size());
                }

                //release lock now
                _lock.unlock();
                synchronized (s.getSendLock())
                {
                    if (!s.isSuspended())
                    {
                        if (_log.isTraceEnabled())
                        {
                            _log.trace(debugIdentity() + "Delivering Message:" + msg.debugIdentity() + " to(" +
                                       System.identityHashCode(s) + ") :" + s);
                        }

                        if (msg.taken(_queue, s))
                        {
                            //Message has been delivered so don't redeliver.
                            // This can currently occur because of the recursive call below
                            // During unit tests the send can occur
                            // client then rejects
                            // this reject then releases the message by the time the
                            // if(!msg.isTaken()) call is made below
                            // the message has been released so that thread loops to send the message again
                            // of course by the time it gets back to here. the thread that released the
                            // message is now ready to send it. Here is a sample trace for reference
//1192627162613:Thread[pool-917-thread-4,5,main]:CSDM:delivery:(true)message:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=false} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=null}:sub:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=41, session=anonymous(5050419), resendQueue=false]
//1192627162613:Thread[pool-917-thread-4,5,main]:Msg:taken:Q:Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326:sub:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=41, session=anonymous(5050419), resendQueue=false]:this:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=false} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=null}
//1192627162613:Thread[pool-917-thread-4,5,main]:28398657 Sent :dt:214 msg:(HC:5529738 ID:145 Ref:1)
//1192627162613:Thread[pool-917-thread-2,5,main]:Reject message by:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=41, session=anonymous(5050419), resendQueue=false]
//1192627162613:Thread[pool-917-thread-2,5,main]:Releasing Message:(HC:5529738 ID:145 Ref:1)
//1192627162613:Thread[pool-917-thread-2,5,main]:Msg:Release:Q:Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326:This:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=false} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=41, session=anonymous(5050419), resendQueue=false]}
//1192627162613:Thread[pool-917-thread-2,5,main]:CSDM:delivery:(true)message:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=false} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=null}:sub:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=33, session=anonymous(26960027), resendQueue=false]
//1192627162629:Thread[pool-917-thread-4,5,main]:CSDM:suspended: Message((HC:5529738 ID:145 Ref:1)) has not been taken so recursing!: Subscriber:28398657
//1192627162629:Thread[pool-917-thread-4,5,main]:CSDM:delivery:(true)message:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=false} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=null}:sub:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=33, session=anonymous(26960027), resendQueue=false]
//1192627162629:Thread[pool-917-thread-2,5,main]:Msg:taken:Q:Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326:sub:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=33, session=anonymous(26960027), resendQueue=false]:this:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=false} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=null}
//1192627162629:Thread[pool-917-thread-2,5,main]:25386607 Sent :dt:172 msg:(HC:5529738 ID:145 Ref:1)
//1192627162629:Thread[pool-917-thread-4,5,main]:Msg:taken:Q:Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326:sub:[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=33, session=anonymous(26960027), resendQueue=false]:this:Message[(HC:5529738 ID:145 Ref:1)]: 145; ref count: 1; taken for queues: {Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=true} by Subs:{Queue(queue-596fb10e-2968-4e51-a751-1e6643bf9dd6)@16017326=[channel=Channel: id 1, transaction mode: true, prefetch marks: 2500/5000, consumerTag=33, session=anonymous(26960027), resendQueue=false]}
                            // Note: In the last request to take the message from thread 4,5 the message has been
                            // taken by the previous call done by thread 2,5


                            return;
                        }
                        //Deliver the message
                        s.send(msg, _queue);
                    }
                    else
                    {
                        if (debugEnabled)
                        {
                            _log.debug(debugIdentity() + " Subscription(" + System.identityHashCode(s) + ") became " +
                                       "suspended between nextSubscriber and send for message:" + msg.debugIdentity());
                        }
                    }
                }

                //
                // Why do we do this? What was the reasoning? We should have a better approach
                // than recursion and rejecting if someone else sends it before we do.
                //
                if (!msg.isTaken(_queue))
                {
                    if (debugEnabled)
                    {
                        _log.debug(debugIdentity() + " Message(" + msg.debugIdentity() + ") has not been taken so recursing!:" +
                                   " Subscriber:" + System.identityHashCode(s));
                    }

                    deliver(context, name, msg, deliverFirst);
                }
                else
                {
                    if (debugEnabled)
                    {
                        _log.debug(debugIdentity() + " Message(" + msg.toString() +
                                   ") has been taken so disregarding deliver request to Subscriber:" +
                                   System.identityHashCode(s));
                    }
                }
            }

        }
        finally
        {
            //ensure lock is released
            if (_lock.isHeldByCurrentThread())
            {
                _lock.unlock();
            }
        }
    }

    private final String id = "(" + String.valueOf(System.identityHashCode(this)) + ")";

    private String debugIdentity()
    {
        return id;
    }

    final Runner _asyncDelivery = new Runner();

    private class Runner implements Runnable
    {
        public void run()
        {
            String startName = Thread.currentThread().getName();
            Thread.currentThread().setName("CSDM-AsyncDelivery:" + startName);
            boolean running = true;
            while (running && !_movingMessages.get())
            {
                processQueue();

                //Check that messages have not been added since we did our last peek();
                // Synchronize with the thread that adds to the queue.
                // If the queue is still empty then we can exit
                synchronized (_asyncDelivery)
                {
                    if (!(hasQueuedMessages() && _subscriptions.hasActiveSubscribers()))
                    {
                        running = false;
                        _processing.set(false);
                    }
                }
            }
            Thread.currentThread().setName(startName);
        }
    }

    public void processAsync(Executor executor)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug(debugIdentity() + "Processing Async." + currentStatus());
        }

        synchronized (_asyncDelivery)
        {
            if (hasQueuedMessages() && _subscriptions.hasActiveSubscribers())
            {
                //are we already running? if so, don't re-run
                if (_processing.compareAndSet(false, true))
                {
                    if (_log.isDebugEnabled())
                    {
                        _log.debug(debugIdentity() + "Executing Async process.");
                    }
                    executor.execute(_asyncDelivery);
                }
            }
        }
    }

    private String currentStatus()
    {
        return " Queued:" + (_messages.isEmpty() ? "Empty " : "Contains(H:M)") +
               "(" + ((ConcurrentLinkedMessageQueueAtomicSize) _messages).headSize() +
               ":" + (_messages.size() - ((ConcurrentLinkedMessageQueueAtomicSize) _messages).headSize()) + ") " +
               " Extra: " + (_hasContent.isEmpty() ? "Empty " : "Contains") +
               "(" + _hasContent.size() + ":" + _extraMessages.get() + ") " +
               " Active:" + _subscriptions.hasActiveSubscribers() +
               " Processing:" + (_processing.get() ? " true : Processing Thread: " + _processingThreadName : " false");
    }

}
