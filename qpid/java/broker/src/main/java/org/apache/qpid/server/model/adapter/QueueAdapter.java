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
package org.apache.qpid.server.model.adapter;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.Subscription;
import org.apache.qpid.server.queue.AMQQueue;

final class QueueAdapter extends AbstractAdapter implements Queue
{

    private final AMQQueue _queue;
    private final Map<Binding, BindingAdapter> _bindingAdapters =
            new HashMap<Binding, BindingAdapter>();
    private Map<org.apache.qpid.server.subscription.Subscription, SubscriptionAdapter> _subscriptionAdapters =
            new HashMap<org.apache.qpid.server.subscription.Subscription, SubscriptionAdapter>();


    private final VirtualHostAdapter _vhost;
    private QueueStatisticsAdapter _statistics;

    public QueueAdapter(final VirtualHostAdapter virtualHostAdapter, final AMQQueue queue)
    {
        _vhost = virtualHostAdapter;
        _queue = queue;
        _statistics = new QueueStatisticsAdapter(queue);
    }

    public Collection<org.apache.qpid.server.model.Binding> getBindings()
    {
        synchronized (_bindingAdapters)
        {
            return new ArrayList<org.apache.qpid.server.model.Binding>(_bindingAdapters.values());
        }
    }

    public Collection<Subscription> getSubscriptions()
    {
        Collection<org.apache.qpid.server.subscription.Subscription> actualSubscriptions = _queue.getConsumers();

        synchronized (_subscriptionAdapters)
        {
            Iterator<org.apache.qpid.server.subscription.Subscription> iter = _subscriptionAdapters.keySet().iterator();
            while(iter.hasNext())
            {
                org.apache.qpid.server.subscription.Subscription subscription = iter.next();
                if(!actualSubscriptions.contains(subscription))
                {
                    iter.remove();
                }
            }
            for(org.apache.qpid.server.subscription.Subscription subscription : actualSubscriptions)
            {
                if(!_subscriptionAdapters.containsKey(subscription))
                {
                    _subscriptionAdapters.put(subscription, _vhost.getOrCreateAdapter(subscription));
                }
            }
            return new ArrayList<Subscription>(_subscriptionAdapters.values());
        }

    }

    public String getName()
    {
        return _queue.getName();
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }

    public State getActualState()
    {
        return null;  //TODO
    }

    public boolean isDurable()
    {
        return _queue.isDurable();
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return _queue.isAutoDelete() ? LifetimePolicy.AUTO_DELETE : LifetimePolicy.PERMANENT;
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;  //TODO
    }

    public long getTimeToLive()
    {
        return 0;  //TODO
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return 0;  //TODO
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }

    void bindingRegistered(Binding binding, BindingAdapter adapter)
    {
        synchronized (_bindingAdapters)
        {
            _bindingAdapters.put(binding, adapter);
        }
        childAdded(adapter);
    }

    void bindingUnregistered(Binding binding)
    {
        BindingAdapter adapter = null;
        synchronized (_bindingAdapters)
        {
            adapter = _bindingAdapters.remove(binding);
        }
        if(adapter != null)
        {
            childRemoved(adapter);
        }
    }

    private static class QueueStatisticsAdapter implements Statistics
    {
        private static final String MSGS_QUEUE_SIZE = "msgs-queue-size";
        private static final String BYTES_QUEUE_SIZE = "bytes-queue-size";
        private static final String BYTES_TOTAL_IN = "bytes-total-in";
        private static final String BYTES_TOTAL_OUT = "bytes-total-out";
        private static final String MSGS_QUEUE_UNDELIVERED_SIZE = "msgs-queue-undelivered-size";
        
        private static final Collection<String> STATISTIC_NAMES = 
                Collections.unmodifiableCollection(Arrays.asList(MSGS_QUEUE_SIZE,BYTES_QUEUE_SIZE, BYTES_TOTAL_IN,
                                                                 BYTES_TOTAL_OUT,MSGS_QUEUE_UNDELIVERED_SIZE));
        
        
        private final AMQQueue _queue;

        public QueueStatisticsAdapter(AMQQueue queue)
        {
            _queue = queue;
        }

        public Collection<String> getStatisticNames()
        {
            return STATISTIC_NAMES;
        }

        public Number getStatistic(String name)
        {
            if(MSGS_QUEUE_SIZE.equals(name))
            {
                return _queue.getMessageCount();
            }
            else if(BYTES_QUEUE_SIZE.equals(name))
            {
                return _queue.getQueueDepth();
            }
            else
                if(BYTES_TOTAL_IN.equals(name))
            {
                return _queue.getTotalEnqueueSize();
            }
            else if(BYTES_TOTAL_OUT.equals(name))
            {
                return _queue.getTotalDequeueSize();
            }
            else if(MSGS_QUEUE_UNDELIVERED_SIZE.equals(name))
            {
                return _queue.getUndeliveredMessageCount();
            }
            return null;
        }
    }
}
