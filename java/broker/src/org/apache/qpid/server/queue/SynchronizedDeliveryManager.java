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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages delivery of messages on behalf of a queue
 */
class SynchronizedDeliveryManager implements DeliveryManager
{
    private static final Logger _log = Logger.getLogger(ConcurrentDeliveryManager.class);

    /**
     * Holds any queued messages
     */
    private final Queue<AMQMessage> _messages = new LinkedList<AMQMessage>();
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
     * An indication of the mode we are in. If this is true then messages are
     * being queued up in _messages for asynchronous delivery. If it is false
     * then messages can be delivered directly as they come in.
     */
    private volatile boolean _queueing;

    /**
     * A reference to the queue we are delivering messages for. We need this to be able
     * to pass the code that handles acknowledgements a handle on the queue.
     */
    private final AMQQueue _queue;

    SynchronizedDeliveryManager(SubscriptionManager subscriptions, AMQQueue queue)
    {
        _subscriptions = subscriptions;
        _queue = queue;
    }

    private synchronized boolean enqueue(AMQMessage msg)
    {
        if (msg.isImmediate())
        {
            return false;
        }
        else
        {
            if (_queueing)
            {
                _messages.offer(msg);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    private synchronized void startQueueing(AMQMessage msg)
    {
        _queueing = true;
        enqueue(msg);
    }

    /**
     * Determines whether there are queued messages. Sets _queueing to false if
     * there are no queued messages. This needs to be atomic.
     *
     * @return true if there are queued messages
     */
    public synchronized boolean hasQueuedMessages()
    {
        boolean empty = _messages.isEmpty();
        if (empty)
        {
            _queueing = false;
        }
        return !empty;
    }

    public synchronized int getQueueMessageCount()
    {
        return _messages.size();
    }

    public synchronized List<AMQMessage> getMessages()
    {
        return new ArrayList<AMQMessage>(_messages);
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

    /**
     * Only one thread should ever execute this method concurrently, but
     * it can do so while other threads invoke deliver().
     */
    private void processQueue()
    {
        try
        {
            boolean hasSubscribers = _subscriptions.hasActiveSubscribers();
            while (hasQueuedMessages() && hasSubscribers)
            {
                Subscription next = _subscriptions.nextSubscriber(peek());
                //We don't synchronize access to subscribers so need to re-check
                if (next != null)
                {
                    try
                    {
                        next.send(poll(), _queue);
                    }
                    catch (AMQException e)
                    {
                        _log.error("Unable to deliver message: " + e, e);
                    }
                }
                else
                {
                    hasSubscribers = false;
                }
            }
        }
        finally
        {
            _processing.set(false);
        }
    }

    private synchronized AMQMessage peek()
    {
        return _messages.peek();
    }

    private synchronized AMQMessage poll()
    {
        return _messages.poll();
    }

    /**
     * Requests that the delivery manager start processing the queue asynchronously
     * if there is work that can be done (i.e. there are messages queued up and
     * subscribers that can receive them.
     * <p/>
     * This should be called when subscribers are added, but only after the consume-ok
     * message has been returned as message delivery may start immediately. It should also
     * be called after unsuspending a client.
     * <p/>
     *
     * @param executor the executor on which the delivery should take place
     */
    public void processAsync(Executor executor)
    {
        if (hasQueuedMessages() && _subscriptions.hasActiveSubscribers())
        {
            //are we already running? if so, don't re-run
            if (_processing.compareAndSet(false, true))
            {
                executor.execute(new Runner());
            }
        }
    }

    /**
     * Handles message delivery. The delivery manager is always in one of two modes;
     * it is either queueing messages for asynchronous delivery or delivering
     * directly.
     *
     * @param name the name of the entity on whose behalf we are delivering the message
     * @param msg  the message to deliver
     * @throws NoConsumersException if there are no active subscribers to deliver
     *                              the message to
     */
    public void deliver(String name, AMQMessage msg) throws FailedDequeueException
    {
        // first check whether we are queueing, and enqueue if we are
        if (!enqueue(msg))
        {
            synchronized(this)
            {
                // not queueing so deliver message to 'next' subscriber
                Subscription s = _subscriptions.nextSubscriber(msg);
                if (s == null)
                {
                    // no subscribers yet so enter 'queueing' mode and queue this message
                    startQueueing(msg);
                }
                else
                {
                    s.send(msg, _queue);
                    msg.setDeliveredToConsumer();
                }
            }
        }

    }

    private class Runner implements Runnable
    {
        public void run()
        {
            processQueue();
        }
    }
}
