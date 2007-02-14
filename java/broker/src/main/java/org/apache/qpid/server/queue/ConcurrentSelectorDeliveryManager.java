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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.util.ConcurrentLinkedQueueAtomicSize;


/**
 * Manages delivery of messages on behalf of a queue
 */
public class ConcurrentSelectorDeliveryManager implements DeliveryManager
{
    private static final Logger _log = Logger.getLogger(ConcurrentSelectorDeliveryManager.class);

    @Configured(path = "advanced.compressBufferOnQueue",
                defaultValue = "false")
    public boolean compressBufferOnQueue;
    /**
     * Holds any queued messages
     */
    private final Queue<AMQMessage> _messages = new ConcurrentLinkedQueueAtomicSize<AMQMessage>();

    private final ReentrantLock _messageAccessLock = new ReentrantLock();

    //private int _messageCount;
    /**
     * Ensures that only one asynchronous task is running for this manager at
     * any time.
     */
    private final AtomicBoolean _processing = new AtomicBoolean();
    /**
     * The subscriptions on the queue to whom messages are delivered
     */
    private final SubscriptionManager _subscriptions;

    /**
     * A reference to the queue we are delivering messages for. We need this to be able
     * to pass the code that handles acknowledgements a handle on the queue.
     */
    private final AMQQueue _queue;

    /**
     * Flag used while moving messages from this queue to another. For moving messages the async delivery
     * should also stop. This flat should be set to true to stop async delivery and set to false to enable
     * async delivery again.
     */
    private AtomicBoolean _movingMessages = new AtomicBoolean();
    
    /**
     * Lock used to ensure that an channel that becomes unsuspended during the start of the queueing process is forced
     * to wait till the first message is added to the queue. This will ensure that the _queue has messages to be delivered
     * via the async thread.
     * <p/>
     * Lock is used to control access to hasQueuedMessages() and over the addition of messages to the queue.
     */
    private ReentrantLock _lock = new ReentrantLock();
    private AtomicLong _totalMessageSize = new AtomicLong();


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


    private boolean addMessageToQueue(AMQMessage msg)
    {
        // Shrink the ContentBodies to their actual size to save memory.
        if (compressBufferOnQueue)
        {
            Iterator<ContentBody> it = msg.getContentBodyIterator();
            while (it.hasNext())
            {
                ContentBody cb = it.next();
                cb.reduceBufferToFit();
            }
        }

        _messages.offer(msg);

        _totalMessageSize.addAndGet(msg.getSize());

        return true;
    }


    public boolean hasQueuedMessages()
    {
        _lock.lock();
        try
        {
            return !_messages.isEmpty();
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
     * This is an EXPENSIVE opperation to perform with a ConcurrentLinkedQueue as it must run the queue to determine size.
     * The ConcurrentLinkedQueueAtomicSize uses an AtomicInteger to record the number of elements on the queue.
     *
     * @return int the number of messages in the delivery queue.
     */
    private int getMessageCount()
    {
        return _messages.size();
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


    public List<AMQMessage> getMessages()
    {
        _lock.lock();
        ArrayList<AMQMessage> list = new ArrayList<AMQMessage>(_messages);
        _lock.unlock();
        return list;
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
            if (subscription.hasInterest(message))
            {
                subscription.enqueueForPreDelivery(message);
            }
        }
    }

    public boolean performGet(AMQProtocolSession protocolSession, AMQChannel channel, boolean acks) throws AMQException
    {
        AMQMessage msg = getNextMessage();
        if(msg == null)
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
                synchronized(channel)
                {
                    long deliveryTag = channel.getNextDeliveryTag();

                    if (acks)
                    {
                        channel.addUnacknowledgedMessage(msg, deliveryTag, null, _queue);
                    }

                    msg.writeGetOk(protocolSession, channel.getChannelId(), deliveryTag, _queue.getMessageCount());
                    _totalMessageSize.addAndGet(-msg.getSize());
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
     * For feature of moving messages, this method is used. It sets the lock and sets the movingMessages flag,
     * so that the asyn delivery is also stopped.
     */
    public void startMovingMessages()
    {
        _lock.lock();
        _movingMessages.set(true);
    }

    /**
     * Once moving messages to another queue is done or aborted, remove lock and unset the movingMessages flag,
     * so that the async delivery can start again.
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
                if (!sub.isSuspended() && sub.hasFilters())
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
     * @param storeContext
     * @throws AMQException
     */
    public void removeAMessageFromTop(StoreContext storeContext) throws AMQException
    {
        _lock.lock();
        AMQMessage msg = getNextMessage();
        if (msg != null)
        {
            // mark this message as taken and get it removed
            msg.taken();
            _queue.dequeue(storeContext, msg);
            getNextMessage();
        }
        
        _lock.unlock();
    }

    public long clearAllMessages(StoreContext storeContext) throws AMQException
    {
        long count = 0;
        _lock.lock();

        AMQMessage msg = getNextMessage();
        while (msg != null)
        {
            //mark this message as taken and get it removed
            msg.taken();
            _queue.dequeue(storeContext, msg);
            msg = getNextMessage();
            count++;
        }

        _lock.unlock();
        return count;
    }

    public synchronized AMQMessage getNextMessage() throws AMQException
    {
        return getNextMessage(_messages);
    }


    private AMQMessage getNextMessage(Queue<AMQMessage> messages)
    {
        return getNextMessage(messages, false);
    }

    private AMQMessage getNextMessage(Queue<AMQMessage> messages, boolean browsing)
    {
        AMQMessage message = messages.peek();

        while (message != null && (browsing || message.taken()))
        {
            //remove the already taken message
            messages.poll();
            _totalMessageSize.addAndGet(-message.getSize());
            // try the next message
            message = messages.peek();
        }
        return message;
    }

    public void sendNextMessage(Subscription sub, Queue<AMQMessage> messageQueue)
    {
        AMQMessage message = null;
        try
        {
            message = getNextMessage(messageQueue, sub.isBrowser());

            // message will be null if we have no messages in the messageQueue.
            if (message == null)
            {
                return;
            }
            if (_log.isDebugEnabled())
            {
                _log.debug("Async Delivery Message:" + message + " to :" + sub);
            }

            sub.send(message, _queue);

            //remove sent message from our queue.
            messageQueue.poll();
            _totalMessageSize.addAndGet(-message.getSize());
        }
        catch (AMQException e)
        {
            message.release();
            _log.error("Unable to deliver message as dequeue failed: " + e, e);
        }
    }

    /**
     * enqueues the messages in the list on the queue and all required predelivery queues
     * @param storeContext
     * @param movedMessageList
     */
    public void enqueueMovedMessages(StoreContext storeContext, List<AMQMessage> movedMessageList)
    {
        _lock.lock();
        for (AMQMessage msg : movedMessageList)
        {
            addMessageToQueue(msg);
        }

        // enqueue on the pre delivery queues
        for (Subscription sub : _subscriptions.getSubscriptions())
        {
            for (AMQMessage msg : movedMessageList)
            {
                // Only give the message to those that want them.
                if (sub.hasInterest(msg))
                {
                    sub.enqueueForPreDelivery(msg);
                }
            }
        }
        _lock.unlock();
    }

    /**
     * Only one thread should ever execute this method concurrently, but
     * it can do so while other threads invoke deliver().
     */
    private void processQueue()
    {
        // Continue to process delivery while we haveSubscribers and messages
        boolean hasSubscribers = _subscriptions.hasActiveSubscribers();

        while (hasSubscribers && hasQueuedMessages() && !_movingMessages.get())
        {
            hasSubscribers = false;

            for (Subscription sub : _subscriptions.getSubscriptions())
            {
                if (!sub.isSuspended())
                {
                    sendNextMessage(sub);

                    hasSubscribers = true;
                }
            }
        }
    }

    private void sendNextMessage(Subscription sub)
    {
        if (sub.hasFilters())
        {
            sendNextMessage(sub, sub.getPreDeliveryQueue());
            if (sub.isAutoClose())
            {
                if (sub.getPreDeliveryQueue().isEmpty())
                {
                    sub.close();
                }
            }
        }
        else
        {
            sendNextMessage(sub, _messages);
        }
    }

    public void deliver(StoreContext context, AMQShortString name, AMQMessage msg) throws AMQException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug(id() + "deliver :" + msg);
        }
        msg.release();

        //Check if we have someone to deliver the message to.
        _lock.lock();
        try
        {
            Subscription s = _subscriptions.nextSubscriber(msg);

            if (s == null) //no-one can take the message right now.
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug(id() + "Testing Message(" + msg + ") for Queued Delivery");
                }
                if (!msg.getPublishBody().immediate)
                {
                    addMessageToQueue(msg);

                    //release lock now message is on queue.
                    _lock.unlock();

                    //Pre Deliver to all subscriptions
                    if (_log.isDebugEnabled())
                    {
                        _log.debug(id() + "We have " + _subscriptions.getSubscriptions().size() +
                                   " subscribers to give the message to.");
                    }
                    for (Subscription sub : _subscriptions.getSubscriptions())
                    {

                        // stop if the message gets delivered whilst PreDelivering if we have a shared queue.
                        if (_queue.isShared() && msg.getDeliveredToConsumer())
                        {
                            if (_log.isDebugEnabled())
                            {
                                _log.debug(id() + "Stopping PreDelivery as message(" + System.identityHashCode(msg) +
                                           ") is already delivered.");
                            }
                            continue;
                        }

                        // Only give the message to those that want them.
                        if (sub.hasInterest(msg))
                        {
                            if (_log.isDebugEnabled())
                            {
                                _log.debug(id() + "Queuing message(" + System.identityHashCode(msg) +
                                           ") for PreDelivery for subscriber(" + System.identityHashCode(sub) + ")");
                            }
                            sub.enqueueForPreDelivery(msg);
                        }
                    }
                }
            }
            else
            {
                //release lock now
                _lock.unlock();

                if (_log.isDebugEnabled())
                {
                    _log.debug(id() + "Delivering Message:" + System.identityHashCode(msg) + " to(" +
                               System.identityHashCode(s) + ") :" + s);
                }
                //Deliver the message
                s.send(msg, _queue);
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

    //fixme remove
    private final String id = "(" + String.valueOf(System.identityHashCode(this)) + ")";

    private String id()
    {
        return id;
    }

    Runner asyncDelivery = new Runner();

    private class Runner implements Runnable
    {
        public void run()
        {
            boolean running = true;
            while (running && !_movingMessages.get())
            {
                processQueue();

                //Check that messages have not been added since we did our last peek();
                // Synchronize with the thread that adds to the queue.
                // If the queue is still empty then we can exit

                if (!(hasQueuedMessages() && _subscriptions.hasActiveSubscribers()))
                {
                    running = false;
                    _processing.set(false);
                }
            }
        }
    }

    public void processAsync(Executor executor)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Processing Async. Queued:" + hasQueuedMessages() + "(" + getQueueMessageCount() + ")" +
                       " Active:" + _subscriptions.hasActiveSubscribers() +
                       " Processing:" + _processing.get());
        }

        if (hasQueuedMessages() && _subscriptions.hasActiveSubscribers())
        {
            //are we already running? if so, don't re-run
            if (_processing.compareAndSet(false, true))
            {
                executor.execute(asyncDelivery);
            }
        }
    }

}
