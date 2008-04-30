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
    private final MessageQueue<QueueEntry> _messages = new ConcurrentLinkedMessageQueueAtomicSize<QueueEntry>();

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


    private boolean addMessageToQueue(QueueEntry entry, boolean deliverFirst)
    {
        AMQMessage msg = entry.getMessage();
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
                _messages.pushHead(entry);
            }
        }
        else
        {
            _messages.offer(entry);
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
        QueueEntry entry = _messages.peek();
        return entry == null ? Long.MAX_VALUE : entry.getMessage().getArrivalTime();
    }

    public void subscriberHasPendingResend(boolean hasContent, Subscription subscription, QueueEntry entry)
    {
        _lock.lock();
        try
        {
            if (hasContent)
            {
                _log.debug("Queue has adding subscriber content");
                _hasContent.add(subscription);
                _totalMessageSize.addAndGet(entry.getSize());
                _extraMessages.addAndGet(1);
            }
            else
            {
                _log.debug("Queue has removing subscriber content");
                if (entry == null)
                {
                    _hasContent.remove(subscription);
                }
                else
                {
                    _totalMessageSize.addAndGet(-entry.getSize());
                    _extraMessages.addAndGet(-1);
                }
            }
        }
        finally
        {
            _lock.unlock();
        }
    }

    /**
     *  NOTE : This method should only be called when there are no active subscribers
     */
    public void removeExpired() throws AMQException
    {
        _lock.lock();


        // New Context to for dealing with the MessageStore.
        StoreContext context = new StoreContext();

        for(Iterator<QueueEntry> iter = _messages.iterator(); iter.hasNext();)
        {
            QueueEntry entry = iter.next();
            if(entry.expired())
            {
                // fixme: Currently we have to update the total byte size here for the data in the queue  
                _totalMessageSize.addAndGet(-entry.getSize());

                // Remove the message from the queue in the MessageStore
                _queue.dequeue(context,entry);

                // This queue nolonger needs a reference to this message
                entry.getMessage().decrementReference(context);
                iter.remove();
            }
	    }


        _lock.unlock();
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
    public List<QueueEntry> getMessages()
    {
        _lock.lock();
        List<QueueEntry> list = new ArrayList<QueueEntry>();

        for (QueueEntry entry : _messages)
        {
            list.add(entry);
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
    public List<QueueEntry> getMessages(long fromMessageId, long toMessageId)
    {
        if (fromMessageId <= 0 || toMessageId <= 0)
        {
            return null;
        }

        long maxMessageCount = toMessageId - fromMessageId + 1;

        _lock.lock();

        List<QueueEntry> foundMessagesList = new ArrayList<QueueEntry>();

        for (QueueEntry entry : _messages)
        {
            long msgId = entry.getMessage().getMessageId();
            if (msgId >= fromMessageId && msgId <= toMessageId)
            {
                foundMessagesList.add(entry);
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
        if (_log.isDebugEnabled())
        {
            _log.debug("Populating PreDeliveryQueue for Subscription(" + System.identityHashCode(subscription) + ")");
        }

        Iterator<QueueEntry> currentQueue = _messages.iterator();

        while (currentQueue.hasNext())
        {
            QueueEntry entry = currentQueue.next();

            if (subscription.hasInterest(entry))
            {
                subscription.enqueueForPreDelivery(entry, false);
            }

        }
    }

    public boolean performGet(AMQProtocolSession protocolSession, AMQChannel channel, boolean acks) throws AMQException
    {
        QueueEntry entry = getNextMessage();
        if (entry == null)
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
                        _log.debug("No ack mode so dequeuing message immediately: " + entry.getMessage().getMessageId());
                    }
                    _queue.dequeue(channel.getStoreContext(), entry);
                }
                synchronized (channel)
                {
                    long deliveryTag = channel.getNextDeliveryTag();

                    if (acks)
                    {
                        channel.addUnacknowledgedMessage(entry, deliveryTag, null);
                    }

                    protocolSession.getProtocolOutputConverter().writeGetOk(entry.getMessage(), channel.getChannelId(),
                                                                            deliveryTag, _queue.getMessageCount());

                }
                _totalMessageSize.addAndGet(-entry.getSize());

                if (!acks)
                {
                    entry.getMessage().decrementReference(channel.getStoreContext());
                }
            }
            finally
            {
                entry.setDeliveredToConsumer();
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
    public void removeMovedMessages(List<QueueEntry> messageList)
    {
        // Remove from the
        boolean hasSubscribers = _subscriptions.hasActiveSubscribers();
        if (hasSubscribers)
        {
            for (Subscription sub : _subscriptions.getSubscriptions())
            {
                if (!sub.isSuspended() && sub.filtersMessages())
                {
                    Queue<QueueEntry> preDeliveryQueue = sub.getPreDeliveryQueue();
                    for (QueueEntry entry : messageList)
                    {
                        preDeliveryQueue.remove(entry);
                    }
                }
            }
        }

        for (QueueEntry entry : messageList)
        {
            if (_messages.remove(entry))
            {
                _totalMessageSize.getAndAdd(-entry.getSize());
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

        QueueEntry entry = _messages.poll();

        if (entry != null)
        {
            queue.dequeue(storeContext, entry);

            _totalMessageSize.addAndGet(-entry.getSize());

            //If this causes ref count to hit zero then data will be purged so message.getSize() will NPE.
            entry.getMessage().decrementReference(storeContext);

        }

        _lock.unlock();
    }

    public long clearAllMessages(StoreContext storeContext) throws AMQException
    {
        long count = 0;
        _lock.lock();

        synchronized (_queueHeadLock)
        {
            QueueEntry entry = getNextMessage();

            // todo: note: why do we need this? Why not reuse the passed 'storeContext'
            //Create a new StoreContext for decrementing the References
            StoreContext context = new StoreContext();

            while (entry != null)
            {
                //and remove it
                _messages.poll();

                // todo: NOTE: Why is this a different context to the new local 'context'?
                _queue.dequeue(storeContext, entry);

                entry.getMessage().decrementReference(context);

                entry = getNextMessage();
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
    private QueueEntry getNextMessage() throws AMQException
    {
        return getNextMessage(_messages, null, false);
    }

    private QueueEntry getNextMessage(Queue<QueueEntry> messages, Subscription sub, boolean purgeOnly) throws AMQException
    {
        QueueEntry entry = messages.peek();

        //while (we have a message) && ((The subscriber is not a browser or message is taken ) or we are clearing) && (Check message is taken.)
        while (purgeMessage(entry, sub, purgeOnly))
        {
            AMQMessage message = entry.getMessage();

            //remove the already taken message or expired
            QueueEntry removed = messages.poll();

            assert removed == entry;

            // if the message expired then the _totalMessageSize needs adjusting
            if (message.expired(_queue) && !entry.taken(sub))
            {
                _totalMessageSize.addAndGet(-entry.getSize());

                // New Store Context for removing expired messages
                StoreContext storeContext = new StoreContext();

                // Use the reapingStoreContext as any sub(if we have one) may be in a tx.
                _queue.dequeue(storeContext, entry);

                message.decrementReference(storeContext);

                if (_log.isInfoEnabled())
                {
                    _log.info(debugIdentity() + " Doing clean up of the main _message queue.");
                }
            }

            //else the clean up is not required as the message has already been taken for this queue therefore
            // it was the responsibility of the code that took the message to ensure the _totalMessageSize was updated.

            if (_log.isDebugEnabled())
            {
                _log.debug("Removed taken message:" + message.debugIdentity());
            }

            // try the next message
            entry = messages.peek();
        }

        return entry;
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
    private boolean purgeMessage(QueueEntry message, Subscription sub) throws AMQException
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
    private boolean purgeMessage(QueueEntry message, Subscription sub, boolean purgeOnly) throws AMQException
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
            if (message.expired())
            {
                return true;
            }

            // if we have a subscriber perform message checks
            if (sub != null)
            {
                // if we have a queue browser(we don't purge) so check mark the message as taken
                purge = ((!sub.isBrowser() || message.isTaken()));
            }
            else
            {
                // if there is no subscription we are doing
                // a get or purging so mark message as taken.
                message.isTaken();
                // and then ensure that it gets purged
                purge = true;
            }
        }

        if (purgeOnly)
        {
            // If we are simply purging the queue don't take the message
            // just purge up to the next non-taken msg.
            return purge && message.isTaken();
        }
        else
        {
            // if we are purging then ensure we mark this message taken for the current subscriber
            // the current subscriber may be null in the case of a get or a purge but this is ok.
            return purge && message.taken(sub);
        }
    }

    public void sendNextMessage(Subscription sub, AMQQueue queue)
    {

        Queue<QueueEntry> messageQueue = sub.getNextQueue(_messages);

        if (_log.isDebugEnabled())
        {
            _log.debug(debugIdentity() + "Async sendNextMessage for sub (" + System.identityHashCode(sub) +
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

        QueueEntry entry = null;
        QueueEntry removed = null;
        try
        {
            synchronized (_queueHeadLock)
            {
                entry = getNextMessage(messageQueue, sub, false);

                // message will be null if we have no messages in the messageQueue.
                if (entry == null)
                {
                    if (_log.isDebugEnabled())
                    {
                        _log.debug(debugIdentity() + "No messages for Subscriber(" + System.identityHashCode(sub) + ") from queue; (" + System.identityHashCode(messageQueue) + ")");
                    }
                    return;
                }
                if (_log.isDebugEnabled())
                {
                    _log.debug(debugIdentity() + "Async Delivery Message :" + entry + "(" + System.identityHashCode(entry) +
                               ") by :" + System.identityHashCode(this) +
                               ") to :" + System.identityHashCode(sub));
                }


                if (messageQueue == _messages)
                {
                    _totalMessageSize.addAndGet(-entry.getSize());
                }

                sub.send(entry, _queue);

                //remove sent message from our queue.
                removed = messageQueue.poll();
                //If we don't remove the message from _messages
                // Otherwise the Async send will never end
            }

            if (removed != entry)
            {
                _log.error("Just send message:" + entry.getMessage().debugIdentity() + " BUT removed this from queue:" + removed);
            }

            if (_log.isDebugEnabled())
            {
                _log.debug(debugIdentity() + "Async Delivered Message r:" + removed.getMessage().debugIdentity() + "d:" + entry +
                           ") by :" + System.identityHashCode(this) +
                           ") to :" + System.identityHashCode(sub));
            }


            if (messageQueue == sub.getResendQueue())
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug(debugIdentity() + "All messages sent from resendQueue for " + sub);
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
            if (entry != null)
            {
                entry.release();
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
    public void enqueueMovedMessages(StoreContext storeContext, List<QueueEntry> movedMessageList)
    {
        _lock.lock();
        for (QueueEntry entry : movedMessageList)
        {
            addMessageToQueue(entry, false);
        }

        // enqueue on the pre delivery queues
        for (Subscription sub : _subscriptions.getSubscriptions())
        {
            for (QueueEntry entry : movedMessageList)
            {
                // Only give the message to those that want them.
                if (sub.hasInterest(entry))
                {
                    sub.enqueueForPreDelivery(entry, true);
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

    public void deliver(StoreContext context, AMQShortString name, QueueEntry entry, boolean deliverFirst) throws AMQException
    {

        final boolean debugEnabled = _log.isDebugEnabled();
        if (debugEnabled)
        {
            _log.debug(debugIdentity() + "deliver :first(" + deliverFirst + ") :" + entry);
        }

        //Check if we have someone to deliver the message to.
        _lock.lock();
        try
        {
            Subscription s = _subscriptions.nextSubscriber(entry);

            if (s == null || (!s.filtersMessages() && hasQueuedMessages())) //no-one can take the message right now or we're queueing
            {
                if (debugEnabled)
                {
                    _log.debug(debugIdentity() + "Testing Message(" + entry + ") for Queued Delivery:" + currentStatus());
                }
                if (!entry.getMessage().getMessagePublishInfo().isImmediate())
                {
                    addMessageToQueue(entry, deliverFirst);

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

                        // Only give the message to those that want them.
                        if (sub.hasInterest(entry))
                        {
                            if (debugEnabled)
                            {
                                _log.debug(debugIdentity() + "Queuing message(" + System.identityHashCode(entry) +
                                           ") for PreDelivery for subscriber(" + System.identityHashCode(sub) + ")");
                            }
                            sub.enqueueForPreDelivery(entry, deliverFirst);
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
                        if (debugEnabled)
                        {
                            _log.debug(debugIdentity() + "Delivering Message:" + entry.getMessage().debugIdentity() + " to(" +
                                       System.identityHashCode(s) + ") :" + s);
                        }

                        if (entry.taken(s))
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
                        s.send(entry, _queue);
                    }
                    else
                    {
                        if (debugEnabled)
                        {
                            _log.debug(debugIdentity() + " Subscription(" + System.identityHashCode(s) + ") became " +
                                       "suspended between nextSubscriber and send for message:" + entry.getMessage().debugIdentity());
                        }
                    }
                }

                //
                // Why do we do this? What was the reasoning? We should have a better approach
                // than recursion and rejecting if someone else sends it before we do.
                //
                if (!entry.isTaken())
                {
                    if (debugEnabled)
                    {
                        _log.debug(debugIdentity() + " Message(" + entry.getMessage().debugIdentity() + ") has not been taken so recursing!:" +
                                   " Subscriber:" + System.identityHashCode(s));
                    }

                    deliver(context, name, entry, deliverFirst);
                }
                else
                {
                    if (debugEnabled)
                    {
                        _log.debug(debugIdentity() + " Message(" + entry.toString() +
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
