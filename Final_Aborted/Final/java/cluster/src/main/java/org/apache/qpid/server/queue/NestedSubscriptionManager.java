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
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Distributes messages among a list of subsscription managers, using their
 * weighting.
 */
class NestedSubscriptionManager implements SubscriptionManager
{
    private final List<WeightedSubscriptionManager> _subscribers = new CopyOnWriteArrayList<WeightedSubscriptionManager>();
    private int _iterations;
    private int _index;

    void addSubscription(WeightedSubscriptionManager s)
    {
        _subscribers.add(s);
    }

    void removeSubscription(WeightedSubscriptionManager s)
    {
        _subscribers.remove(s);
    }


    public List<Subscription> getSubscriptions()
    {
        List<Subscription> allSubs = new LinkedList<Subscription>();

        for (WeightedSubscriptionManager subMans : _subscribers)
        {
            allSubs.addAll(subMans.getSubscriptions());
        }

        return allSubs;
    }

    public boolean hasActiveSubscribers()
    {
        for (WeightedSubscriptionManager s : _subscribers)
        {
            if (s.hasActiveSubscribers())
            {
                return true;
            }
        }
        return false;
    }

    public Subscription nextSubscriber(AMQMessage msg)
    {
        WeightedSubscriptionManager start = current();
        for (WeightedSubscriptionManager s = start; s != null; s = next(start))
        {
            if (hasMore(s))
            {
                return nextSubscriber(s);
            }
        }
        return null;
    }

    private Subscription nextSubscriber(WeightedSubscriptionManager s)
    {
        _iterations++;
        return s.nextSubscriber(null);
    }

    private WeightedSubscriptionManager current()
    {
        return _subscribers.isEmpty() ? null : _subscribers.get(_index);
    }

    private boolean hasMore(WeightedSubscriptionManager s)
    {
        return _iterations < s.getWeight();
    }

    private WeightedSubscriptionManager next(WeightedSubscriptionManager start)
    {
        WeightedSubscriptionManager s = next();
        return s == start && !hasMore(s) ? null : s;
    }

    private WeightedSubscriptionManager next()
    {
        _iterations = 0;
        if (++_index >= _subscribers.size())
        {
            _index = 0;
        }
        return _subscribers.get(_index);
    }
}
