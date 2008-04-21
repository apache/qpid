package org.apache.qpid.server.queue;

import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.util.MessageQueue;
import org.apache.qpid.util.ConcurrentLinkedMessageQueueAtomicSize;
import org.apache.qpid.util.ConcurrentLinkedQueueAtomicSize;
import org.apache.log4j.Logger;

import java.util.Queue;

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
public class DeliveryAgent 
{

    private static final Logger _logger = Logger.getLogger(DeliveryAgent.class);

    private final Subscription _subscription;



    private MessageQueue<QueueEntry> _messages;

    private Queue<QueueEntry> _resendQueue;

    private boolean _sentClose = false;

    public DeliveryAgent(final Subscription subscription)
    {
        _subscription = subscription;

        if (filtersMessages())
        {
            _messages = new ConcurrentLinkedMessageQueueAtomicSize<QueueEntry>();
        }
        else
        {
            // Reference the DeliveryManager
            _messages = null;
        }
    }


    public Subscription getSubscription()
    {
        return _subscription;
    }


    public AMQQueue getQueue()
    {
        return _subscription.getQueue();
    }

    public void setQueue(final AMQQueue queue)
    {
        _subscription.setQueue(queue);
    }

    public AMQChannel getChannel()
    {
        return _subscription.getChannel();
    }

    public boolean isSuspended()
    {
        return _subscription.isSuspended();
    }

    public boolean hasInterest(final QueueEntry msg)
    {
        return _subscription.hasInterest(msg);
    }

    public boolean isAutoClose()
    {
        return _subscription.isAutoClose();
    }

    public boolean isClosed()
    {
        return _subscription.isClosed();
    }

    public boolean isBrowser()
    {
        return _subscription.isBrowser();
    }

    public void close()
    {
        _subscription.close();

        if (_resendQueue != null && !_resendQueue.isEmpty())
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Requeuing closing subscription " + this);
            }
            requeue();
        }

        //remove references in PDQ
        if (_messages != null)
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Clearing PDQ " + this);
            }

            _messages.clear();
        }
    }

    public boolean filtersMessages()
    {
        return _subscription.filtersMessages();
    }

    public void send(final QueueEntry msg)
            throws AMQException
    {
        _subscription.send(msg);
    }

    public void queueDeleted(final AMQQueue queue)
    {
        _subscription.queueDeleted(queue);
    }

    public Queue<QueueEntry> getPreDeliveryQueue()
    {
        return _messages;
    }

    public Queue<QueueEntry> getResendQueue()
    {
        if (_resendQueue == null)
        {
            _resendQueue = new ConcurrentLinkedQueueAtomicSize<QueueEntry>();
        }
        return _resendQueue;
    }

    public Queue<QueueEntry> getNextQueue(final Queue<QueueEntry> messages)
    {
        if (_resendQueue != null && !_resendQueue.isEmpty())
        {
            return _resendQueue;
        }

        if (filtersMessages())
        {
            if (isAutoClose())
            {
                if (_messages.isEmpty())
                {
                    autoclose();
                    return null;
                }
            }
            return _messages;
        }
        else // we want the DM queue
        {
            return messages;
        }
    }


    private void autoclose()
    {
        close();

        if (isAutoClose() && !_sentClose)
        {
            _logger.info("Closing autoclose subscription" + this);

            ProtocolOutputConverter converter = getChannel().getProtocolSession().getProtocolOutputConverter();
            converter.confirmConsumerAutoClose(getChannel().getChannelId(), getConsumerTag());
            _sentClose = true;

            //fixme JIRA do this better
            try
            {
                getChannel().unsubscribeConsumer(getConsumerTag());
            }
            catch (AMQException e)
            {
                // Occurs if we cannot find the subscriber in the channel with protocolSession and consumerTag.
            }
        }
    }


    public void enqueueForPreDelivery(final QueueEntry msg, final boolean deliverFirst)
    {
        if (_messages != null)
        {
            if (deliverFirst)
            {
                _messages.pushHead(msg);
            }
            else
            {
                _messages.offer(msg);
            }
        }

    }

    public boolean wouldSuspend(final QueueEntry msg)
    {
        return _subscription.wouldSuspend(msg);
    }

    public void addToResendQueue(final QueueEntry msg)
    {
                // add to our resend queue
        getResendQueue().add(msg);

        // Mark Queue has having content.
        if (getQueue() == null)
        {
            _logger.error("Queue is null won't be able to resend messages");
        }
        else
        {
            ((AMQQueueImpl) getQueue()).subscriberHasPendingResend(true, getSubscription(), msg);
        }
    }

    public Object getSendLock()
    {
        return _subscription.getSendLock();
    }

    private void requeue()
    {
        if (getQueue() != null)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Requeuing :" + _resendQueue.size() + " messages");
            }

            while (!_resendQueue.isEmpty())
            {
                QueueEntry resent = _resendQueue.poll();

                if (_logger.isTraceEnabled())
                {
                    _logger.trace("Removed for resending:" + resent.debugIdentity());
                }

                resent.release();
                ((AMQQueueImpl) getQueue()).subscriberHasPendingResend(false, getSubscription(), resent);

                try
                {
                    getChannel().getTransactionalContext().requeue(resent);
                }
                catch (AMQException e)
                {
                    _logger.error("MESSAGE LOSS : Unable to re-deliver messages", e);
                }
            }

            if (!_resendQueue.isEmpty())
            {
                _logger.error("[MESSAGES LOST]Unable to re-deliver messages as queue is null.");
            }

            ((AMQQueueImpl) getQueue()).subscriberHasPendingResend(false, getSubscription(), null);
        }
        else
        {
            if (!_resendQueue.isEmpty())
            {
                _logger.error("Unable to re-deliver messages as queue is null.");
            }
        }

        // Clear the messages
        _resendQueue = null;
    }


    public String toString()
    {
        return "[Delivery Agent for: "+getSubscription()+"]";
    }

    public AMQShortString getConsumerTag()
    {
        return getSubscription().getConumerTag();
    }

    boolean ableToDeliver()
    {
        // if the queue is not empty then this client is ready to receive a message.
        //FIXME the queue could be full of sent messages.
        // Either need to clean all PDQs after sending a message
        // OR have a clean up thread that runs the PDQs expunging the messages.
        return (!_subscription.filtersMessages() || getPreDeliveryQueue().isEmpty());
    }

    public void start()
    {
        // Check to see if we need to autoclose
        if (filtersMessages())
        {
            if (isAutoClose())
            {
                if (_messages.isEmpty())
                {
                      autoclose();
                }
            }
        }
    }
}
