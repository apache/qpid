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
public class ConcurrentDeliveryManager implements DeliveryManager
{
    private static final Logger _log = Logger.getLogger(ConcurrentDeliveryManager.class);

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


    ConcurrentDeliveryManager(SubscriptionManager subscriptions, AMQQueue queue)
    {

        //Set values from configuration
        Configurator.configure(this);

        if (compressBufferOnQueue)
        {
            _log.info("Compressing Buffers on queue.");
        }

        _subscriptions = subscriptions;
        _queue = queue;
    }

    /**
     * @return boolean if we are queueing
     */
    private boolean queueing()
    {
        return hasQueuedMessages();
    }


    /**
     * @param msg to enqueue
     * @return true if we are queue this message
     */
    private boolean enqueue(AMQMessage msg)
    {
        if (msg.isImmediate())
        {
            return false;
        }
        else
        {
            _lock.lock();
            try
            {
                if (queueing())
                {
                    return addMessageToQueue(msg);
                }
                else
                {
                    return false;
                }
            }
            finally
            {
                _lock.unlock();
            }
        }
    }

    private void startQueueing(AMQMessage msg)
    {
        if (!msg.isImmediate())
        {
            addMessageToQueue(msg);
        }
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
            AMQMessage message = peek();

            //While we have messages to send and subscribers to send them to.
            while (message != null && hasSubscribers)
            {
                // _log.debug("Have messages(" + _messages.size() + ") and subscribers");
                Subscription next = _subscriptions.nextSubscriber(message);
                //FIXME Is there still not the chance that this subscribe could be suspended between here and the send?

                //We don't synchronize access to subscribers so need to re-check
                if (next != null)
                {
                    next.send(message, _queue);
                    poll();
                    message = peek();
                }
                else
                {
                    hasSubscribers = false;
                }
            }
        }
        catch (FailedDequeueException e)
        {
            _log.error("Unable to deliver message as dequeue failed: " + e, e);
        }
        finally
        {
            _log.debug("End of processQueue: (" + getQueueMessageCount() + ")" + " subscribers:" + _subscriptions.hasActiveSubscribers());
        }
    }

    private AMQMessage peek()
    {
        return _messages.peek();
    }

    private AMQMessage poll()
    {
        return _messages.poll();
    }

    Runner asyncDelivery = new Runner();

    public void processAsync(Executor executor)
    {
        _log.debug("Processing Async. Queued:" + hasQueuedMessages() + "(" + getQueueMessageCount() + ")" +
                   " Active:" + _subscriptions.hasActiveSubscribers() +
                   " Processing:" + _processing.get());

        if (hasQueuedMessages() && _subscriptions.hasActiveSubscribers())
        {
            //are we already running? if so, don't re-run
            if (_processing.compareAndSet(false, true))
            {
                // Do we need this?
                // This executor is created via Executors in AsyncDeliveryConfig which only returns a TPE so cast is ok.
                //if (executor != null && !((ThreadPoolExecutor) executor).isShutdown())
                {
                    executor.execute(asyncDelivery);
                }
            }
        }
    }

    public void deliver(String name, AMQMessage msg) throws FailedDequeueException
    {
        // first check whether we are queueing, and enqueue if we are
        if (!enqueue(msg))
        {
            // not queueing so deliver message to 'next' subscriber
            _lock.lock();
            try
            {
                Subscription s = _subscriptions.nextSubscriber(msg);
                if (s == null)
                {
                    if (!msg.isImmediate())
                    {
                        // no subscribers yet so enter 'queueing' mode and queue this message
                        startQueueing(msg);
                    }
                }
                else
                {
                    s.send(msg, _queue);
                    msg.setDeliveredToConsumer();
                }
            }
            finally
            {
                _lock.unlock();
            }
        }
    }

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
                _lock.lock();
                try
                {
                    if (!(hasQueuedMessages() && _subscriptions.hasActiveSubscribers()))
                    {
                        running = false;
                        _processing.set(false);
                    }
                }
                finally
                {
                    _lock.unlock();
                }
            }
        }
    }
}
