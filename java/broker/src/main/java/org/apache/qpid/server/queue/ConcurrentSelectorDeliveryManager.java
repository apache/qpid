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
import org.apache.qpid.util.ConcurrentLinkedQueueAtomicSize;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.server.configuration.Configurator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;


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
     * Lock used to ensure that an channel that becomes unsuspended during the start of the queueing process is forced
     * to wait till the first message is added to the queue. This will ensure that the _queue has messages to be delivered
     * via the async thread.
     * <p/>
     * Lock is used to control access to hasQueuedMessages() and over the addition of messages to the queue.
     */
    private ReentrantLock _lock = new ReentrantLock();


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
            Iterator it = msg.getContentBodies().iterator();
            while (it.hasNext())
            {
                ContentBody cb = (ContentBody) it.next();
                cb.reduceBufferToFit();
            }
        }

        _messages.offer(msg);

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


    public synchronized List<AMQMessage> getMessages()
    {
        return new ArrayList<AMQMessage>(_messages);
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

    public synchronized void removeAMessageFromTop() throws AMQException
    {
        AMQMessage msg = poll();
        if (msg != null)
        {
            msg.dequeue(_queue);
        }
    }

    public synchronized void clearAllMessages() throws AMQException
    {
        AMQMessage msg = poll();
        while (msg != null)
        {
            msg.dequeue(_queue);
            msg = poll();
        }
    }


    private AMQMessage getNextMessage(Queue<AMQMessage> messages, Subscription sub)
    {
        AMQMessage message = messages.peek();

        while (message != null && (sub.isBrowser() || message.taken()))
        {
            //remove the already taken message
            messages.poll();
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
            message = getNextMessage(messageQueue, sub);

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
        }
        catch (FailedDequeueException e)
        {
            message.release();
            _log.error("Unable to deliver message as dequeue failed: " + e, e);
        }
    }

    /**
     * Only one thread should ever execute this method concurrently, but
     * it can do so while other threads invoke deliver().
     */
    private void processQueue()
    {
        // Continue to process delivery while we haveSubscribers and messages
        boolean hasSubscribers = _subscriptions.hasActiveSubscribers();

        while (hasSubscribers && hasQueuedMessages())
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

    private AMQMessage poll()
    {
        return _messages.poll();
    }

    public void deliver(String name, AMQMessage msg) throws FailedDequeueException
    {
        if (_log.isDebugEnabled())
        {
            _log.debug(id() + "deliver :" + System.identityHashCode(msg));
        }

        //Check if we have someone to deliver the message to.
        _lock.lock();
        try
        {
            Subscription s = _subscriptions.nextSubscriber(msg);

            if (s == null) //no-one can take the message right now.
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug(id() + "Testing Message(" + System.identityHashCode(msg) + ") for Queued Delivery");
                }
                if (!msg.isImmediate())
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
            if (_lock.isLocked())
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
            while (running)
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
