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

import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.*;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.util.MapValueConverter;

final class QueueAdapter<Q extends AMQQueue<?,Q,?>> extends AbstractConfiguredObject<QueueAdapter<Q>> implements Queue<QueueAdapter<Q>>,
                                                            MessageSource.ConsumerRegistrationListener<Q>,
                                                            AMQQueue.NotificationListener
{
    @SuppressWarnings("serial")
    static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(ALERT_REPEAT_GAP, Long.class);
        put(ALERT_THRESHOLD_MESSAGE_AGE, Long.class);
        put(ALERT_THRESHOLD_MESSAGE_SIZE, Long.class);
        put(ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, Long.class);
        put(ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, Long.class);
        put(QUEUE_FLOW_CONTROL_SIZE_BYTES, Long.class);
        put(QUEUE_FLOW_RESUME_SIZE_BYTES, Long.class);
        put(MAXIMUM_DELIVERY_ATTEMPTS, Integer.class);
        put(DESCRIPTION, String.class);
    }});

    private final AMQQueue<?,Q,?> _queue;

    private final Map<Binding, BindingAdapter> _bindingAdapters =
            new HashMap<Binding, BindingAdapter>();
    private final Map<Consumer, ConsumerAdapter> _consumerAdapters =
            new HashMap<Consumer, ConsumerAdapter>();


    private final VirtualHostAdapter _vhost;
    private QueueNotificationListener _queueNotificationListener;

    public QueueAdapter(final VirtualHostAdapter virtualHostAdapter, final AMQQueue<?,Q,?> queue)
    {
        super(queue.getId(), virtualHostAdapter.getTaskExecutor());
        _vhost = virtualHostAdapter;
        addParent(org.apache.qpid.server.model.VirtualHost.class, virtualHostAdapter);

        _queue = queue;
        _queue.addConsumerRegistrationListener(this);
        populateConsumers();
        _queue.setNotificationListener(this);
    }

    /**
     * Helper method to retrieve the SessionAdapter keyed by the AMQSessionModel.
     * This method first finds the ConnectionAdapter associated with the Session from this QueueAdapter's parent vhost
     * then it does a lookup on that to find the SessionAdapter keyed by the requested AMQSessionModel instance.
     * @param session the AMQSessionModel used to index the SessionAdapter.
     * @return the requested SessionAdapter or null if it can't be found.
     */
    private SessionAdapter getSessionAdapter(AMQSessionModel session)
    {
        // Retrieve the ConnectionModel associated with the SessionModel as a key to lookup the ConnectionAdapter.
        AMQConnectionModel connectionKey = session.getConnectionModel();

        // Lookup the ConnectionAdapter, from which we should be able to retrieve the SessionAdapter we really want.
        ConnectionAdapter connectionAdapter = _vhost.getConnectionAdapter(connectionKey);
        if (connectionAdapter == null)
        {
            return null; // If we can't find an associated ConnectionAdapter the SessionAdapter is a lost cause.
        }
        else
        {   // With a good ConnectionAdapter we can finally try to find the SessionAdapter we are actually looking for.
            SessionAdapter sessionAdapter = connectionAdapter.getSessionAdapter(session);
            if (sessionAdapter == null)
            {
                return null; // If the SessionAdapter isn't associated with the selected ConnectionAdapter give up.
            }
            else
            {
                return sessionAdapter;
            }
        }
    }

    private void populateConsumers()
    {
        Collection<? extends Consumer> actualConsumers = _queue.getConsumers();

        synchronized (_consumerAdapters)
        {
            for(Consumer consumer : actualConsumers)
            {
                if(!_consumerAdapters.containsKey(consumer))
                {
                    SessionAdapter sessionAdapter = getSessionAdapter(consumer.getSessionModel());
                    ConsumerAdapter adapter = new ConsumerAdapter(this, sessionAdapter, consumer);
                    _consumerAdapters.put(consumer, adapter);
                    if (sessionAdapter != null)
                    { // Register ConsumerAdapter with the SessionAdapter.
                        sessionAdapter.consumerRegistered(consumer, adapter);
                    }
                }
            }
        }
    }

    @Override
    public String getQueueType()
    {
        return (String) getAttribute(QUEUE_TYPE);
    }

    @Override
    public Exchange getAlternateExchange()
    {
        org.apache.qpid.server.exchange.Exchange alternateExchange = _queue.getAlternateExchange();
        return alternateExchange == null ? null :
                ConfiguredObjectFinder.findConfiguredObjectByName(_vhost.getExchanges(),
                                                                  alternateExchange.getName());
    }

    @Override
    public ExclusivityPolicy getExclusive()
    {
        return (ExclusivityPolicy) _queue.getAttribute(EXCLUSIVE);
    }

    @Override
    public String getOwner()
    {
        return _queue.getOwner();
    }

    @Override
    public boolean getNoLocal()
    {
        // TODO
        return false;
    }

    @Override
    public String getLvqKey()
    {
        return (String) _queue.getAttribute(LVQ_KEY);
    }

    @Override
    public String getSortKey()
    {
        return (String) _queue.getAttribute(SORT_KEY);
    }

    @Override
    public String getMessageGroupKey()
    {
        return (String) _queue.getAttribute(MESSAGE_GROUP_KEY);
    }

    @Override
    public int getMessageGroupSharedGroups()
    {
        return (Integer) _queue.getAttribute(MESSAGE_GROUP_SHARED_GROUPS);
    }

    @Override
    public int getMaximumDeliveryAttempts()
    {
        return _queue.getMaximumDeliveryCount();
    }

    @Override
    public long getQueueFlowControlSizeBytes()
    {
        return _queue.getCapacity();
    }

    @Override
    public long getQueueFlowResumeSizeBytes()
    {
        return _queue.getFlowResumeCapacity();
    }

    @Override
    public boolean isQueueFlowStopped()
    {
        return false;
    }

    @Override
    public long getAlertThresholdMessageAge()
    {
        return _queue.getMaximumMessageAge();
    }

    @Override
    public long getAlertThresholdMessageSize()
    {
        return _queue.getMaximumMessageSize();
    }

    @Override
    public long getAlertThresholdQueueDepthBytes()
    {
        return _queue.getMaximumQueueDepth();
    }

    @Override
    public long getAlertThresholdQueueDepthMessages()
    {
        return _queue.getMaximumMessageCount();
    }

    @Override
    public long getAlertRepeatGap()
    {
        return _queue.getMinimumAlertRepeatGap();
    }

    @Override
    public int getPriorities()
    {
        return (Integer) _queue.getAttribute(PRIORITIES);
    }

    public Collection<org.apache.qpid.server.model.Binding> getBindings()
    {
        synchronized (_bindingAdapters)
        {
            return new ArrayList<org.apache.qpid.server.model.Binding>(_bindingAdapters.values());
        }
    }

    public Collection<org.apache.qpid.server.model.Consumer> getConsumers()
    {
        synchronized (_consumerAdapters)
        {
            return new ArrayList<org.apache.qpid.server.model.Consumer>(_consumerAdapters.values());
        }

    }

    public void visit(final QueueEntryVisitor visitor)
    {
        _queue.visit(visitor);
    }

    public void delete()
    {
        _queue.getVirtualHost().removeQueue(_queue);
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

    public State getState()
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
        return _queue.getLifetimePolicy();
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
        return getAttributeNames(Queue.class);
    }

    @Override
    public boolean changeAttribute(String name, Object expected, Object desired) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        try
        {
            if(ALERT_REPEAT_GAP.equals(name))
            {
                _queue.setMinimumAlertRepeatGap((Long)desired);
                return true;
            }
            else if(ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
            {
                _queue.setMaximumMessageAge((Long)desired);
                return true;
            }
            else if(ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
            {
                _queue.setMaximumMessageSize((Long)desired);
                return true;
            }
            else if(ALERT_THRESHOLD_QUEUE_DEPTH_BYTES.equals(name))
            {
                _queue.setMaximumQueueDepth((Long)desired);
                return true;
            }
            else if(ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES.equals(name))
            {
                _queue.setMaximumMessageCount((Long)desired);
                return true;
            }
            else if(ALTERNATE_EXCHANGE.equals(name))
            {
                // In future we may want to accept a UUID as an alternative way to identifying the exchange
                ExchangeAdapter alternateExchange = (ExchangeAdapter) desired;
                _queue.setAlternateExchange(alternateExchange == null ? null : alternateExchange.getExchange());
                return true;
            }
            else if(EXCLUSIVE.equals(name))
            {
                ExclusivityPolicy desiredPolicy;
                if(desired == null)
                {
                    desiredPolicy = ExclusivityPolicy.NONE;
                }
                else if(desired instanceof  ExclusivityPolicy)
                {
                    desiredPolicy = (ExclusivityPolicy)desired;
                }
                else if (desired instanceof String)
                {
                    desiredPolicy = ExclusivityPolicy.valueOf((String)desired);
                }
                else
                {
                    throw new IllegalArgumentException("Cannot set " + Queue.EXCLUSIVE + " property to type " + desired.getClass().getName());
                }
                try
                {
                    _queue.setExclusivityPolicy(desiredPolicy);
                }
                catch (MessageSource.ExistingConsumerPreventsExclusive existingConsumerPreventsExclusive)
                {
                    throw new IllegalArgumentException("Unable to set exclusivity policy to " + desired + " as an existing combinations of consumers prevents this");
                }
                return true;

            }
            else if(MESSAGE_GROUP_KEY.equals(name))
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
                return true;
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
                return true;
            }
            else if(QUEUE_FLOW_RESUME_SIZE_BYTES.equals(name))
            {
                _queue.setFlowResumeCapacity((Long)desired);
                return true;
            }
            else if(QUEUE_FLOW_STOPPED.equals(name))
            {
                // TODO
            }
            else if(SORT_KEY.equals(name))
            {
                // TODO
            }
            else if(QUEUE_TYPE.equals(name))
            {
                // TODO
            }
            else if (DESCRIPTION.equals(name))
            {
                _queue.setDescription((String) desired);
                return true;
            }

            return super.changeAttribute(name, expected, desired);
        }
        finally
        {
            if (_queue.isDurable())
            {
                DurableConfigurationStoreHelper.updateQueue(_queue.getVirtualHost().getDurableConfigurationStore(),
                        _queue);
            }
        }
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
            org.apache.qpid.server.exchange.Exchange alternateExchange = _queue.getAlternateExchange();
            return alternateExchange == null ? null :
                                               ConfiguredObjectFinder.findConfiguredObjectByName(_vhost.getExchanges(),
                                                                                                 alternateExchange.getName());
        }
        else if(EXCLUSIVE.equals(name))
        {
            return _queue.getAttribute(Queue.EXCLUSIVE);
        }
        else if(MESSAGE_GROUP_KEY.equals(name))
        {
            return _queue.getAttribute(MESSAGE_GROUP_KEY);
        }
        else if(MESSAGE_GROUP_SHARED_GROUPS.equals(name))
        {
            //We only return the boolean value if message groups are actually in use
            return getAttribute(MESSAGE_GROUP_KEY) == null ? null : _queue.getAttribute(MESSAGE_GROUP_SHARED_GROUPS);
        }
        else if(LVQ_KEY.equals(name))
        {
            AMQQueue queue = _queue;
            if(queue instanceof ConflationQueue)
            {
                return ((ConflationQueue)queue).getConflationKey();
            }
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
            return _queue.getOwner();
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
            return _queue.isOverfull();
        }
        else if(SORT_KEY.equals(name))
        {
            AMQQueue queue = _queue;
            if(queue instanceof SortedQueue)
            {
                return ((SortedQueue)queue).getSortedPropertyName();
            }
        }
        else if(QUEUE_TYPE.equals(name))
        {
            AMQQueue queue = _queue;
            if(queue instanceof SortedQueue)
            {
                return "sorted";
            }
            if(queue instanceof ConflationQueue)
            {
                return "lvq";
            }
            if(queue instanceof PriorityQueue)
            {
                return "priority";
            }
            return "standard";
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
            return _queue.getLifetimePolicy();
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
        else if (DESCRIPTION.equals(name))
        {
            return _queue.getDescription();
        }
        else if(PRIORITIES.equals(name))
        {
            AMQQueue queue = _queue;
            if(queue instanceof PriorityQueue)
            {
                return ((PriorityQueue)queue).getPriorities();
            }
        }
        return super.getAttribute(name);
    }


    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == org.apache.qpid.server.model.Consumer.class)
        {
            return (Collection<C>) getConsumers();
        }
        else if(clazz == org.apache.qpid.server.model.Binding.class)
        {
            return (Collection<C>) getBindings();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    public org.apache.qpid.server.model.Binding createBinding(Exchange exchange, Map<String, Object> attributes)
            throws AccessControlException, IllegalStateException
    {
        attributes = new HashMap<String, Object>(attributes);
        String bindingKey = MapValueConverter.getStringAttribute(org.apache.qpid.server.model.Binding.NAME, attributes, "");
        Map<String, Object> bindingArgs = MapValueConverter.getMapAttribute(org.apache.qpid.server.model.Binding.ARGUMENTS, attributes, Collections.<String,Object>emptyMap());

        attributes.remove(org.apache.qpid.server.model.Binding.NAME);
        attributes.remove(org.apache.qpid.server.model.Binding.ARGUMENTS);

        return exchange.createBinding(bindingKey, this, bindingArgs, attributes);

    }



    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if(childClass == org.apache.qpid.server.model.Binding.class)
        {
            if(otherParents != null && otherParents.length == 1 && otherParents[0] instanceof Exchange)
            {
                Exchange exchange = (Exchange) otherParents[0];
                if(exchange.getParent(org.apache.qpid.server.model.VirtualHost.class) == getParent(org.apache.qpid.server.model.VirtualHost.class))
                {
                    return (C) createBinding(exchange, attributes);
                }
                else
                {
                    throw new IllegalArgumentException("Queue and Exchange parents of a binding must be on same virtual host");
                }
            }
            else
            {
                throw new IllegalArgumentException("Other parent must be an exchange");
            }
        }
        else
        {
            throw new IllegalArgumentException();
        }
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

    public void consumerAdded(final AMQQueue queue, final Consumer consumer)
    {
        ConsumerAdapter adapter = null;
        synchronized (_consumerAdapters)
        {
            if(!_consumerAdapters.containsKey(consumer))
            {
                SessionAdapter sessionAdapter = getSessionAdapter(consumer.getSessionModel());
                adapter = new ConsumerAdapter(this, sessionAdapter, consumer);
                _consumerAdapters.put(consumer, adapter);
                if (sessionAdapter != null)
                { // Register ConsumerAdapter with the SessionAdapter.
                    sessionAdapter.consumerRegistered(consumer, adapter);
                }
            }
        }
        if(adapter != null)
        {
            childAdded(adapter);
        }
    }

    public void consumerRemoved(final AMQQueue queue, final Consumer consumer)
    {
        ConsumerAdapter adapter = null;

        synchronized (_consumerAdapters)
        {
            adapter = _consumerAdapters.remove(consumer);
        }
        if(adapter != null)
        {
            SessionAdapter sessionAdapter = getSessionAdapter(consumer.getSessionModel());
            if (sessionAdapter != null)
            { // Unregister ConsumerAdapter with the SessionAdapter.
                sessionAdapter.consumerUnregistered(consumer);
            }
            childRemoved(adapter);
        }
    }

    VirtualHostAdapter getVirtualHost()
    {
        return _vhost;
    }


    @Override
    public long getBytesIn()
    {
        return _queue.getTotalEnqueueSize();
    }

    @Override
    public long getBytesOut()
    {
        return _queue.getTotalDequeueSize();
    }

    @Override
    public long getMessagesIn()
    {
        return _queue.getTotalEnqueueCount();
    }

    @Override
    public long getMessagesOut()
    {
        return _queue.getTotalDequeueCount();
    }
    @Override
    public long getBindingCount()
    {
        return _queue.getBindingCount();
    }

    @Override
    public long getConsumerCount()
    {
        return _queue.getConsumerCount();
    }

    @Override
    public long getConsumerCountWithCredit()
    {
        return _queue.getActiveConsumerCount();
    }

    @Override
    public long getPersistentDequeuedBytes()
    {
        return _queue.getPersistentByteDequeues();
    }

    @Override
    public long getPersistentDequeuedMessages()
    {
        return _queue.getPersistentMsgDequeues();
    }

    @Override
    public long getPersistentEnqueuedBytes()
    {
        return _queue.getPersistentByteEnqueues();
    }

    @Override
    public long getPersistentEnqueuedMessages()
    {
        return _queue.getPersistentMsgEnqueues();
    }

    @Override
    public long getQueueDepthBytes()
    {
        return _queue.getQueueDepth();
    }

    @Override
    public long getQueueDepthMessages()
    {
        return _queue.getMessageCount();
    }

    @Override
    public long getTotalDequeuedBytes()
    {
        return _queue.getTotalDequeueSize();
    }

    @Override
    public long getTotalDequeuedMessages()
    {
        return _queue.getTotalDequeueCount();
    }

    @Override
    public long getTotalEnqueuedBytes()
    {
        return _queue.getTotalEnqueueSize();
    }

    @Override
    public long getTotalEnqueuedMessages()
    {
        return _queue.getTotalEnqueueCount();
    }

    @Override
    public long getUnacknowledgedBytes()
    {
        return _queue.getUnackedMessageBytes();
    }

    @Override
    public long getUnacknowledgedMessages()
    {
        return _queue.getUnackedMessageCount();
    }


    @Override
    public void setNotificationListener(QueueNotificationListener listener)
    {
        _queueNotificationListener = listener;
    }

    @Override
    public void notifyClients(NotificationCheck notification, AMQQueue queue, String notificationMsg)
    {
        QueueNotificationListener listener = _queueNotificationListener;
        if(listener !=  null)
        {
            listener.notifyClients(notification, this, notificationMsg);
        }
    }

    @Override
    protected boolean setState(State currentState, State desiredState) throws IllegalStateTransitionException,
            AccessControlException
    {
        if (desiredState == State.DELETED)
        {
            delete();
            return true;
        }
        return false;
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        _vhost.getSecurityManager().authoriseUpdate(_queue);
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        _vhost.getSecurityManager().authoriseUpdate(_queue);
    }

    @Override
    protected void changeAttributes(final Map<String, Object> attributes)
    {
        Map<String, Object> convertedAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        validateAttributes(convertedAttributes);

        super.changeAttributes(convertedAttributes);
    }

    private void validateAttributes(Map<String, Object> convertedAttributes)
    {
        Long queueFlowControlSize = (Long) convertedAttributes.get(QUEUE_FLOW_CONTROL_SIZE_BYTES);
        Long queueFlowControlResumeSize = (Long) convertedAttributes.get(QUEUE_FLOW_RESUME_SIZE_BYTES);
        if (queueFlowControlSize != null || queueFlowControlResumeSize != null )
        {
            if (queueFlowControlSize == null)
            {
                queueFlowControlSize = (Long)getAttribute(QUEUE_FLOW_CONTROL_SIZE_BYTES);
            }
            if (queueFlowControlResumeSize == null)
            {
                queueFlowControlResumeSize = (Long)getAttribute(QUEUE_FLOW_RESUME_SIZE_BYTES);
            }
            if (queueFlowControlResumeSize > queueFlowControlSize)
            {
                throw new IllegalConfigurationException("Flow resume size can't be greater than flow control size");
            }
        }
        for (Map.Entry<String, Object> entry: convertedAttributes.entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Number && ((Number)value).longValue() < 0)
            {
                throw new IllegalConfigurationException("Only positive integer value can be specified for the attribute "
                        + entry.getKey());
            }
        }
    }

}
