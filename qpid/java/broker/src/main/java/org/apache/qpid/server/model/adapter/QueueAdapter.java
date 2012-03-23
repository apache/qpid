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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.server.subscription.Subscription;

final class QueueAdapter extends AbstractAdapter implements Queue, AMQQueue.SubscriptionRegistrationListener
{

    private final AMQQueue _queue;
    private final Map<Binding, BindingAdapter> _bindingAdapters =
            new HashMap<Binding, BindingAdapter>();
    private Map<org.apache.qpid.server.subscription.Subscription, ConsumerAdapter> _consumerAdapters =
            new HashMap<org.apache.qpid.server.subscription.Subscription, ConsumerAdapter>();


    private final VirtualHostAdapter _vhost;
    private QueueStatisticsAdapter _statistics;

    public QueueAdapter(final VirtualHostAdapter virtualHostAdapter, final AMQQueue queue)
    {
        super(virtualHostAdapter.getName(), queue.getName());
        _vhost = virtualHostAdapter;
        addParent(org.apache.qpid.server.model.VirtualHost.class, virtualHostAdapter);

        _queue = queue;
        _queue.addSubscriptionRegistrationListener(this);
        populateConsumers();
        _statistics = new QueueStatisticsAdapter(queue);
    }

    private void populateConsumers()
    {
        Collection<org.apache.qpid.server.subscription.Subscription> actualSubscriptions = _queue.getConsumers();

        synchronized (_consumerAdapters)
        {
            Iterator<org.apache.qpid.server.subscription.Subscription> iter = _consumerAdapters.keySet().iterator();
            for(org.apache.qpid.server.subscription.Subscription subscription : actualSubscriptions)
            {
                if(!_consumerAdapters.containsKey(subscription))
                {
                    _consumerAdapters.put(subscription, new ConsumerAdapter(this, subscription));
                }
            }
        }
    }

    public Collection<org.apache.qpid.server.model.Binding> getBindings()
    {
        synchronized (_bindingAdapters)
        {
            return new ArrayList<org.apache.qpid.server.model.Binding>(_bindingAdapters.values());
        }
    }

    public Collection<Consumer> getConsumers()
    {
        synchronized (_consumerAdapters)
        {
            return new ArrayList<Consumer>(_consumerAdapters.values());
        }

    }

    public void visit(final QueueEntryVisitor visitor)
    {
        _queue.visit(visitor);
    }

    public void delete()
    {
        try
        {
            _queue.delete();
            if (_queue.isDurable())
            {

                _queue.getVirtualHost().getDurableConfigurationStore().removeQueue(_queue);
            }
        }
        catch(AMQException e)
        {
            throw new IllegalStateException(e);
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


    @Override
    public Collection<String> getAttributeNames()
    {
        return Queue.AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        if(ALERT_REPEAT_GAP.equals(name))
        {
            _queue.setMinimumAlertRepeatGap((Long)desired);
            return desired;
        }
        else if(ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
        {
            _queue.setMaximumMessageAge((Long)desired);
            return desired;
        }
        else if(ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
        {
            _queue.setMaximumMessageSize((Long)desired);
            return desired;
        }
        else if(ALERT_THRESHOLD_QUEUE_DEPTH_BYTES.equals(name))
        {
            _queue.setMaximumQueueDepth((Long)desired);
            return desired;
        }
        else if(ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES.equals(name))
        {
            _queue.setMaximumMessageCount((Long)desired);
            return desired;
        }
        else if(ALTERNATE_EXCHANGE.equals(name))
        {
            // TODO
        }
        else if(EXCLUSIVE.equals(name))
        {
            // TODO
        }
        else if(MESSAGE_GROUP_KEY.equals(name))
        {
            // TODO
        }
        else if(MESSAGE_GROUP_DEFAULT_GROUP.equals(name))
        {
            // TODO
        }
        else if(MESSAGE_GROUP_SHARED_GROUPS.equals(name))
        {
            // TODO
        }
        else if(LVQ_KEY.equals(name))
        {
            // TODO
        }
        else if(MAXIMUM_DELIVERY_ATTEMPTS.equals(name))
        {
            _queue.setMaximumDeliveryCount((Integer)desired);
            return desired;
        }
        else if(NO_LOCAL.equals(name))
        {
            // TODO
        }
        else if(OWNER.equals(name))
        {
            // TODO
        }
        else if(QUEUE_FLOW_CONTROL_SIZE_BYTES.equals(name))
        {
            _queue.setCapacity((Long)desired);
            return desired;
        }
        else if(QUEUE_FLOW_RESUME_SIZE_BYTES.equals(name))
        {
            _queue.setFlowResumeCapacity((Long)desired);
            return desired;
        }
        else if(QUEUE_FLOW_STOPPED.equals(name))
        {
            // TODO
        }
        else if(SORT_KEY.equals(name))
        {
            // TODO
        }
        else if(TYPE.equals(name))
        {
            // TODO
        }

        return super.setAttribute(name, expected, desired);
    }

    @Override
    public Object getAttribute(String name)
    {

        if(ALERT_REPEAT_GAP.equals(name))
        {
            return _queue.getMinimumAlertRepeatGap();
        }
        else if(ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
        {
            return _queue.getMaximumMessageAge();
        }
        else if(ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
        {
            return _queue.getMaximumMessageSize();
        }
        else if(ALERT_THRESHOLD_QUEUE_DEPTH_BYTES.equals(name))
        {
            return _queue.getMaximumQueueDepth();
        }
        else if(ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES.equals(name))
        {
            return _queue.getMaximumMessageCount();
        }
        else if(ALTERNATE_EXCHANGE.equals(name))
        {
            return _queue.getAlternateExchange() == null ? null : _queue.getAlternateExchange().getName();
        }
        else if(EXCLUSIVE.equals(name))
        {
            return _queue.isExclusive();
        }
        else if(MESSAGE_GROUP_KEY.equals(name))
        {
            // TODO
        }
        else if(MESSAGE_GROUP_DEFAULT_GROUP.equals(name))
        {
            // TODO
        }
        else if(MESSAGE_GROUP_SHARED_GROUPS.equals(name))
        {
            // TODO
        }
        else if(LVQ_KEY.equals(name))
        {
            // TODO
        }
        else if(MAXIMUM_DELIVERY_ATTEMPTS.equals(name))
        {
            return _queue.getMaximumDeliveryCount();
        }
        else if(NO_LOCAL.equals(name))
        {
            // TODO
        }
        else if(OWNER.equals(name))
        {
            // TODO
        }
        else if(QUEUE_FLOW_CONTROL_SIZE_BYTES.equals(name))
        {
            return _queue.getCapacity();
        }
        else if(QUEUE_FLOW_RESUME_SIZE_BYTES.equals(name))
        {
            return _queue.getFlowResumeCapacity();
        }
        else if(QUEUE_FLOW_STOPPED.equals(name))
        {
            // TODO
        }
        else if(SORT_KEY.equals(name))
        {
            // TODO
        }
        else if(TYPE.equals(name))
        {
            // TODO
        }
        else if(CREATED.equals(name))
        {
            // TODO
        }
        else if(DURABLE.equals(name))
        {
            return _queue.isDurable();
        }
        else if(ID.equals(name))
        {
            return getId();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return _queue.isAutoDelete() ? LifetimePolicy.AUTO_DELETE : LifetimePolicy.PERMANENT;
        }
        else if(NAME.equals(name))
        {
            return _queue.getName();
        }
        else if(STATE.equals(name))
        {
            return State.ACTIVE; // TODO
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if(UPDATED.equals(name))
        {
            // TODO
        }

        return super.getAttribute(name);
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

    AMQQueue getAMQQueue()
    {
        return _queue;
    }

    public void subscriptionRegistered(final AMQQueue queue, final Subscription subscription)
    {
        ConsumerAdapter adapter = null;
        synchronized (_consumerAdapters)
        {
            if(!_consumerAdapters.containsKey(subscription))
            {
                adapter = new ConsumerAdapter(this, subscription);
                _consumerAdapters.put(subscription,adapter);
                // TODO - register with session
            }
        }
        if(adapter != null)
        {
            childAdded(adapter);
        }
    }

    public void subscriptionUnregistered(final AMQQueue queue, final Subscription subscription)
    {
        ConsumerAdapter adapter = null;

        synchronized (_consumerAdapters)
        {
            adapter = _consumerAdapters.remove(subscription);
            // TODO - register with session
        }
        if(adapter != null)
        {
            childRemoved(adapter);
        }
    }

    VirtualHostAdapter getVirtualHost()
    {
        return _vhost;
    }


    private static class QueueStatisticsAdapter implements Statistics
    {

        private final AMQQueue _queue;

        public QueueStatisticsAdapter(AMQQueue queue)
        {
            _queue = queue;
        }

        public Collection<String> getStatisticNames()
        {
            return Queue.AVAILABLE_STATISTICS;
        }

        public Object getStatistic(String name)
        {
            if(BINDING_COUNT.equals(name))
            {
                return _queue.getBindingCount();
            }
            else if(CONSUMER_COUNT.equals(name))
            {
                return _queue.getConsumerCount();
            }
            else if(CONSUMER_COUNT_WITH_CREDIT.equals(name))
            {
                return _queue.getActiveConsumerCount();
            }
            else if(DISCARDS_TTL_BYTES.equals(name))
            {
                return null; // TODO
            }
            else if(DISCARDS_TTL_MESSAGES.equals(name))
            {
                return null; // TODO
            }
            else if(PERSISTENT_DEQUEUED_BYTES.equals(name))
            {
                return _queue.getPersistentByteDequeues();
            }
            else if(PERSISTENT_DEQUEUED_MESSAGES.equals(name))
            {
                return _queue.getPersistentMsgDequeues();
            }
            else if(PERSISTENT_ENQUEUED_BYTES.equals(name))
            {
                return _queue.getPersistentByteEnqueues();
            }
            else if(PERSISTENT_ENQUEUED_MESSAGES.equals(name))
            {
                return _queue.getPersistentMsgEnqueues();
            }
            else if(QUEUE_DEPTH_BYTES.equals(name))
            {
                return _queue.getQueueDepth();
            }
            else if(QUEUE_DEPTH_MESSAGES.equals(name))
            {
                return _queue.getMessageCount();
            }
            else if(STATE_CHANGED.equals(name))
            {
                return null; // TODO
            }
            else if(TOTAL_DEQUEUED_BYTES.equals(name))
            {
                return _queue.getTotalDequeueSize();
            }
            else if(TOTAL_DEQUEUED_MESSAGES.equals(name))
            {
                return _queue.getTotalDequeueCount();
            }
            else if(TOTAL_ENQUEUED_BYTES.equals(name))
            {
                return _queue.getTotalEnqueueSize();
            }
            else if(TOTAL_ENQUEUED_MESSAGES.equals(name))
            {
                return _queue.getTotalEnqueueCount();
            }
            else if(UNACKNOWLEDGED_BYTES.equals(name))
            {
                return _queue.getUnackedMessageBytes();
            }
            else if(UNACKNOWLEDGED_MESSAGES.equals(name))
            {
                return _queue.getUnackedMessageCount();
            }

            return null;
        }
    }

}
