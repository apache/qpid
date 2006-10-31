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
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Manages delivery of messages on behalf of a queue
 */
public class DeliveryManager
{
    private static final Logger _log = Logger.getLogger(DeliveryManager.class);

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


    DeliveryManager(SubscriptionManager subscriptions, AMQQueue queue)
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
            if (queueing())
            {
                return addMessageToQueue(msg);
            }
            else
            {
                return false;
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


    /**
     * Determines whether there are queued messages. Sets _queueing to false if
     * there are no queued messages. This needs to be atomic.
     *
     * @return true if there are queued messages
     */
    public boolean hasQueuedMessages()
    {
        return !_messages.isEmpty();
    }

    public int getQueueMessageCount()
    {
        return getMessageCount();
    }

    /**
     * This is an EXPENSIVE opperation to perform with a ConcurrentLinkedQueue as it must run the queue to determine size.
     *
     * @return int the number of messages in the delivery queue.
     */

    private int getMessageCount()
    {
        return _messages.size();
    }


    protected synchronized List<AMQMessage> getMessages()
    {
        return new ArrayList<AMQMessage>(_messages);
    }

    protected synchronized void removeAMessageFromTop() throws AMQException
    {
        AMQMessage msg = poll();
        if (msg != null)
        {
            msg.dequeue(_queue);
        }
    }

    protected synchronized void clearAllMessages() throws AMQException
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
                // _log.debug("Have messages(" + _messages.size() + ") and subscribers");
                Subscription next = _subscriptions.nextSubscriber(peek());

                //We don't synchronize access to subscribers so need to re-check
                if (next != null)
                {
                    next.send(peek(), _queue);
                    poll();
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
            _processing.set(false);
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
    void processAsync(Executor executor)
    {
        _log.debug("Processing Async. Queued:" + hasQueuedMessages() + "(" + getQueueMessageCount() + ")" +
                   " Active:" + _subscriptions.hasActiveSubscribers() +
                   " Processing:" + _processing.get());

        if (hasQueuedMessages() && _subscriptions.hasActiveSubscribers())
        {
            //are we already running? if so, don't re-run
            if (_processing.compareAndSet(false, true))
            {
                executor.execute(asyncDelivery);
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
     * @throws FailedDequeueException if the message could not be dequeued
     */
    void deliver(String name, AMQMessage msg) throws FailedDequeueException
    {
        // first check whether we are queueing, and enqueue if we are
        if (!enqueue(msg))
        {
            // not queueing so deliver message to 'next' subscriber
            Subscription s = _subscriptions.nextSubscriber(msg);
            if (s == null)
            {
                if (!msg.isImmediate())
                {
                    if (_subscriptions instanceof SubscriptionSet)
                    {
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Start Queueing messages Active Subs:" + _subscriptions.hasActiveSubscribers()
                                       + " Size :" + ((SubscriptionSet) _subscriptions).size()
                                       + " Empty :" + ((SubscriptionSet) _subscriptions).isEmpty());
                        }
                    }
                    else
                    {
                        if (_log.isDebugEnabled())
                        {
                            _log.debug("Start Queueing messages Active Subs:" + _subscriptions.hasActiveSubscribers());
                        }
                    }
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
    }

    private class Runner implements Runnable
    {
        public void run()
        {
            processQueue();
        }
    }
}
