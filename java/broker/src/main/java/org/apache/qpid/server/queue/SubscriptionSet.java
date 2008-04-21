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

import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.server.subscription.Subscription;

/** Holds a set of subscriptions for a queue and manages the round robin-ing of deliver etc. */
class SubscriptionSet implements SubscriptionManager
{
    private static final Logger _log = Logger.getLogger(SubscriptionSet.class);

    /** List of registered subscribers */
    private final List<Subscription> _subscriptions = new CopyOnWriteArrayList<Subscription>();

    private final Map<Subscription, DeliveryAgent> _deliveryAgents =
            new ConcurrentHashMap<Subscription, DeliveryAgent>();


    /** Used to control the round robin delivery of content */
    private int _currentSubscriber;
    private final Object _changeLock = new Object();
    private volatile boolean _exclusive;


    /** Accessor for unit tests. */
    int getCurrentSubscriber()
    {
        return _currentSubscriber;
    }

    public void addSubscriber(Subscription subscription)
    {
        synchronized (_changeLock)
        {
            _subscriptions.add(subscription);
            _deliveryAgents.put(subscription, new DeliveryAgent(subscription));
        }
    }

    /**
     * Remove the subscription, returning it if it was found
     *
     * @param subscription
     *
     * @return null if no match was found
     */
    public Subscription removeSubscriber(Subscription subscription)
    {
        // TODO: possibly need O(1) operation here.
        
        synchronized (_changeLock)
        {

            if (_subscriptions.remove(subscription))
            {
                _deliveryAgents.remove(subscription);
                return subscription;
            }
            else
            {
                _log.error("Unable to remove subscription:" + subscription);
                debugDumpSubscription(subscription);
                return null;
            }
        }
    }

    private void debugDumpSubscription(Subscription subscription)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Subscription " + subscription + " not found. Dumping subscriptions:");
            for (Subscription s : _subscriptions)
            {
                _log.debug("Subscription: " + s);
            }
            _log.debug("Subscription dump complete");
        }
    }

    /**
     * Return the next unsuspended subscription or null if not found. <p/> Performance note: This method can scan all
     * items twice when looking for a subscription that is not suspended. The worst case occcurs when all subscriptions
     * are suspended. However, it is does this without synchronisation and subscriptions may be added and removed
     * concurrently. Also note that because of race conditions and when subscriptions are removed between calls to
     * nextSubscriber, the IndexOutOfBoundsException also causes the scan to start at the beginning.
     */
    public Subscription nextSubscriber(QueueEntry msg)
    {


        try
        {
            final Subscription result = nextSubscriberImpl(msg);
            if (result == null)
            {
                _currentSubscriber = 0;
                return nextSubscriberImpl(msg);
            }
            else
            {
                return result;
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            _currentSubscriber = 0;
            return nextSubscriber(msg);
        }
    }

    private Subscription nextSubscriberImpl(QueueEntry msg)
    {
        if(_exclusive)
        {
            try
            {
                Subscription subscription = _subscriptions.get(0);
                subscriberScanned();

                if (!subscription.isSuspended() )
                {
                    if (subscription.hasInterest(msg))
                    {
                        DeliveryAgent deliverAgent = _deliveryAgents.get(subscription);

                        if (deliverAgent.ableToDeliver())
                        {
                            if(!subscription.wouldSuspend(msg))
                            {
                                return subscription;
                            }
                        }
                    }
                }
            }
            catch(IndexOutOfBoundsException e)
            {
            }
            return null;
        }
        else
        {
            if (_subscriptions.isEmpty())
            {
                return null;
            }
            final ListIterator<Subscription> iterator = _subscriptions.listIterator(_currentSubscriber);
            while (iterator.hasNext())
            {

                Subscription subscription = iterator.next();
                DeliveryAgent deliverAgent = _deliveryAgents.get(subscription);
                ++_currentSubscriber;
                subscriberScanned();

                if (!(deliverAgent == null || subscription.isSuspended()))
                {
                    if (subscription.hasInterest(msg) && deliverAgent.ableToDeliver())
                    {
                        if (!subscription.wouldSuspend(msg))
                        {
                            return subscription;
                        }
                    }
                }
            }

            return null;
        }
    }

    /** Overridden in test classes. */
    protected void subscriberScanned()
    {
    }

    public boolean isEmpty()
    {
        return _subscriptions.isEmpty();
    }

    public List<Subscription> getSubscriptions()
    {
        return _subscriptions;
    }

    public boolean hasActiveSubscribers()
    {
        for (Subscription s : _subscriptions)
        {
            if (!s.isSuspended())
            {
                return true;
            }
        }
        return false;
    }

    public int getActiveConsumerCount()
    {
        int count = 0;
        for (Subscription s : _subscriptions)
        {
            if (s.isActive())
            {
                count++;
            }
        }
        return count;
    }

    public int getConsumerCount()
    {
        return size();
    }

    /**
     * Notification that a queue has been deleted. This is called so that the subscription can inform the channel, which
     * in turn can update its list of unacknowledged messages.
     *
     * @param queue
     */
    public void queueDeleted(AMQQueue queue)
    {
        for (Subscription s : _subscriptions)
        {
            s.queueDeleted(queue);
        }
    }

    int size()
    {
        return _subscriptions.size();
    }


    public Object getChangeLock()
    {
        return _changeLock;
    }

    public void setExclusive(final boolean exclusive)
    {
        _exclusive = exclusive;
    }

    
    public DeliveryAgent getDeliveryAgent(final Subscription sub)
    {
        return _deliveryAgents.get(sub);
    }    
}
