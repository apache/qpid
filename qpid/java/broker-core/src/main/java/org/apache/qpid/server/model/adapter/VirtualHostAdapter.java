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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.ConflationQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ParameterizedTypeImpl;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.UnknownExchangeException;
import org.apache.qpid.server.virtualhost.VirtualHostListener;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.server.virtualhost.QueueExistsException;

public final class VirtualHostAdapter extends AbstractConfiguredObject<VirtualHostAdapter> implements VirtualHost<VirtualHostAdapter>, VirtualHostListener
{
    private static final Logger LOGGER = Logger.getLogger(VirtualHostAdapter.class);

    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(TYPE, String.class);
        put(STATE, State.class);

        put(QUEUE_ALERT_REPEAT_GAP, Long.class);
        put(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, Long.class);
        put(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, Long.class);
        put(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, Long.class);
        put(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, Long.class);
        put(QUEUE_DEAD_LETTER_QUEUE_ENABLED, Boolean.class);
        put(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, Integer.class);
        put(QUEUE_FLOW_CONTROL_SIZE_BYTES, Long.class);
        put(QUEUE_FLOW_RESUME_SIZE_BYTES, Long.class);

        put(HOUSEKEEPING_CHECK_PERIOD, Long.class);
        put(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, Long.class);
        put(STORE_TRANSACTION_IDLE_TIMEOUT_WARN, Long.class);
        put(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Long.class);
        put(STORE_TRANSACTION_OPEN_TIMEOUT_WARN, Long.class);

        put(MESSAGE_STORE_SETTINGS, new ParameterizedTypeImpl(Map.class, String.class, Object.class));
        put(CONFIGURATION_STORE_SETTINGS, new ParameterizedTypeImpl(Map.class, String.class, Object.class));

    }});

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>(){{
        put(HOUSE_KEEPING_THREAD_COUNT, Runtime.getRuntime().availableProcessors());
    }});

    private org.apache.qpid.server.virtualhost.VirtualHost _virtualHost;

    private final Map<AMQConnectionModel, ConnectionAdapter> _connectionAdapters =
            new HashMap<AMQConnectionModel, ConnectionAdapter>();

    private final Broker<?> _broker;
    private final List<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();
    private StatisticsGatherer _brokerStatisticsGatherer;

    public VirtualHostAdapter(UUID id, Map<String, Object> attributes, Broker<?> broker, StatisticsGatherer brokerStatisticsGatherer, TaskExecutor taskExecutor)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES, false), taskExecutor, false);
        _broker = broker;
        _brokerStatisticsGatherer = brokerStatisticsGatherer;
        validateAttributes();
        addParent(Broker.class, broker);
    }

    private void validateAttributes()
    {
        String name = getName();
        if (name == null || "".equals(name.trim()))
        {
            throw new IllegalConfigurationException("Virtual host name must be specified");
        }
        String type = getType();
        if (type == null || "".equals(type.trim()))
        {
            throw new IllegalConfigurationException("Virtual host type must be specified");
        }
        final VirtualHostFactory factory = VirtualHostFactory.FACTORIES.get(type);
        if(factory == null)
        {
            throw new IllegalArgumentException("Unknown virtual host type '"+ type +"'.  Valid types are: " + VirtualHostFactory.TYPES.get());
        }
        factory.validateAttributes(getActualAttributes());
    }

    public Collection<VirtualHostAlias> getAliases()
    {
        return Collections.unmodifiableCollection(_aliases);
    }

    public Collection<Connection> getConnections()
    {
        synchronized(_connectionAdapters)
        {
            return new ArrayList<Connection>(_connectionAdapters.values());
        }

    }

    /**
     * Retrieve the ConnectionAdapter instance keyed by the AMQConnectionModel from this VirtualHost.
     * @param connection the AMQConnectionModel used to index the ConnectionAdapter.
     * @return the requested ConnectionAdapter.
     */
    ConnectionAdapter getConnectionAdapter(AMQConnectionModel connection)
    {
        synchronized (_connectionAdapters)
        {
            return _connectionAdapters.get(connection);
        }
    }

    public Collection<Queue> getQueues()
    {
        return _virtualHost == null ? Collections.<Queue>emptyList() : new ArrayList<Queue>(_virtualHost.getQueues());
    }

    public Collection<Exchange> getExchanges()
    {
        return _virtualHost == null ? Collections.<Exchange>emptyList() : new ArrayList<Exchange>(_virtualHost.getExchanges());
    }


    public Exchange createExchange(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        attributes = new HashMap<String, Object>(attributes);

        String         name     = MapValueConverter.getStringAttribute(Exchange.NAME, attributes, null);
        State          state    = MapValueConverter.getEnumAttribute(State.class, Exchange.STATE, attributes, State.ACTIVE);
        boolean        durable  = MapValueConverter.getBooleanAttribute(Exchange.DURABLE, attributes, false);
        LifetimePolicy lifetime = MapValueConverter.getEnumAttribute(LifetimePolicy.class, Exchange.LIFETIME_POLICY, attributes, LifetimePolicy.PERMANENT);
        String         type     = MapValueConverter.getStringAttribute(Exchange.TYPE, attributes, null);

        attributes.remove(Exchange.NAME);
        attributes.remove(Exchange.STATE);
        attributes.remove(Exchange.DURABLE);
        attributes.remove(Exchange.LIFETIME_POLICY);
        attributes.remove(Exchange.TYPE);

        return createExchange(name, state, durable, lifetime, type, attributes);
    }

    public Exchange createExchange(final String name,
                                   final State initialState,
                                   final boolean durable,
                                   final LifetimePolicy lifetime,
                                   final String type,
                                   final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        checkVHostStateIsActive();

        try
        {
            String alternateExchange = null;
            if(attributes.containsKey(Exchange.ALTERNATE_EXCHANGE))
            {
                Object altExchangeObject = attributes.get(Exchange.ALTERNATE_EXCHANGE);
                if(altExchangeObject instanceof Exchange)
                {
                    alternateExchange = ((Exchange) altExchangeObject).getName();
                }
                else if(altExchangeObject instanceof UUID)
                {
                    for(Exchange ex : getExchanges())
                    {
                        if(altExchangeObject.equals(ex.getId()))
                        {
                            alternateExchange = ex.getName();
                            break;
                        }
                    }
                }
                else if(altExchangeObject instanceof String)
                {

                    for(Exchange ex : getExchanges())
                    {
                        if(altExchangeObject.equals(ex.getName()))
                        {
                            alternateExchange = ex.getName();
                            break;
                        }
                    }
                    if(alternateExchange == null)
                    {
                        try
                        {
                            UUID id = UUID.fromString(altExchangeObject.toString());
                            for(Exchange ex : getExchanges())
                            {
                                if(id.equals(ex.getId()))
                                {
                                    alternateExchange = ex.getName();
                                    break;
                                }
                            }
                        }
                        catch(IllegalArgumentException e)
                        {
                            // ignore
                        }

                    }
                }
            }
            Map<String,Object> attributes1 = new HashMap<String, Object>();

            attributes1.put(ID, null);
            attributes1.put(NAME, name);
            attributes1.put(Exchange.TYPE, type);
            attributes1.put(Exchange.DURABLE, durable);
            attributes1.put(Exchange.LIFETIME_POLICY,
                            lifetime != null && lifetime != LifetimePolicy.PERMANENT
                                    ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
            attributes1.put(Exchange.ALTERNATE_EXCHANGE, alternateExchange);
            ExchangeImpl exchange = _virtualHost.createExchange(attributes1);
            return exchange;

        }
        catch(ExchangeExistsException e)
        {
            throw new IllegalArgumentException("Exchange with name '" + name + "' already exists");
        }
        catch(ReservedExchangeNameException e)
        {
            throw new UnsupportedOperationException("'" + name + "' is a reserved exchange name");
        }
        catch(UnknownExchangeException e)
        {
            throw new IllegalArgumentException("Alternate Exchange with name '" + e.getExchangeName() + "' does not exist");
        }
        catch(AMQUnknownExchangeType e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public Queue createQueue(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        checkVHostStateIsActive();

        if (attributes.containsKey(Queue.QUEUE_TYPE))
        {
            String typeAttribute = MapValueConverter.getStringAttribute(Queue.QUEUE_TYPE, attributes, null);
            QueueType queueType = null;
            try
            {
                queueType = QueueType.valueOf(typeAttribute.toUpperCase());
            }
            catch(Exception e)
            {
                throw new IllegalArgumentException("Unsupported queue type :" + typeAttribute);
            }
            if (queueType == QueueType.LVQ && attributes.get(Queue.LVQ_KEY) == null)
            {
                attributes.put(Queue.LVQ_KEY, ConflationQueue.DEFAULT_LVQ_KEY);
            }
            else if (queueType == QueueType.PRIORITY && attributes.get(Queue.PRIORITIES) == null)
            {
                attributes.put(Queue.PRIORITIES, 10);
            }
            else if (queueType == QueueType.SORTED && attributes.get(Queue.SORT_KEY) == null)
            {
                throw new IllegalArgumentException("Sort key is not specified for sorted queue");
            }
        }



        try
        {

            AMQQueue<?> queue = _virtualHost.createQueue(attributes);


            return queue;


        }
        catch(QueueExistsException qe)
        {
            throw new IllegalArgumentException("Queue with name "+MapValueConverter.getStringAttribute(Queue.NAME,attributes)+" already exists");
        }
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }


    public String getType()
    {
        return (String)getAttribute(TYPE);
    }

    public String setType(final String currentType, final String desiredType)
            throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }


    @Override
    public State getState()
    {
        if (_virtualHost == null)
        {
            State state = (State)super.getAttribute(STATE);
            if (state == null)
            {
                return State.INITIALISING;
            }
            return state;
        }
        else
        {
            org.apache.qpid.server.virtualhost.State implementationState = _virtualHost.getState();
            switch(implementationState)
            {
            case INITIALISING:
                return State.INITIALISING;
            case ACTIVE:
                return State.ACTIVE;
            case PASSIVE:
                return State.REPLICA;
            case STOPPED:
                return State.STOPPED;
            case ERRORED:
                return State.ERRORED;
            default:
                throw new IllegalStateException("Unsupported state:" + implementationState);
            }
        }
    }

    public boolean isDurable()
    {
        return true;
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public long getTimeToLive()
    {
        return 0;
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Exchange.class)
        {
            return (Collection<C>) getExchanges();
        }
        else if(clazz == Queue.class)
        {
            return (Collection<C>) getQueues();
        }
        else if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else if(clazz == VirtualHostAlias.class)
        {
            return (Collection<C>) getAliases();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if(childClass == Exchange.class)
        {
            createExchange(attributes);

            // return null to avoid double notification of VirtualHostMBean
            // as we already notify it in the exchangeRegistered
            return null;
        }
        else if(childClass == Queue.class)
        {
            createQueue(attributes);

            // return null to avoid double notification of VirtualHostMBean
            // as we already notify it in the queueRegistered
            return null;
        }
        else if(childClass == VirtualHostAlias.class)
        {
            throw new UnsupportedOperationException();
        }
        else if(childClass == Connection.class)
        {
            throw new UnsupportedOperationException();
        }
        throw new IllegalArgumentException("Cannot create a child of class " + childClass.getSimpleName());
    }

    public void exchangeRegistered(ExchangeImpl exchange)
    {
        childAdded(exchange);
    }


    public void exchangeUnregistered(ExchangeImpl exchange)
    {
        childRemoved(exchange);
    }

    public void queueRegistered(AMQQueue queue)
    {
        childAdded(queue);
    }

    public void queueUnregistered(AMQQueue queue)
    {
        childRemoved(queue);
    }

    public void connectionRegistered(AMQConnectionModel connection)
    {
        ConnectionAdapter adapter = null;
        synchronized (_connectionAdapters)
        {
            if(!_connectionAdapters.containsKey(connection))
            {
                adapter = new ConnectionAdapter(connection, getTaskExecutor());
                _connectionAdapters.put(connection, adapter);

            }

        }
        if(adapter != null)
        {
            childAdded(adapter);
        }
    }

    public void connectionUnregistered(AMQConnectionModel connection)
    {

        ConnectionAdapter adapter;
        synchronized (_connectionAdapters)
        {
            adapter = _connectionAdapters.remove(connection);

        }

        if(adapter != null)
        {
            // Call getSessions() first to ensure that any SessionAdapter children are cleanly removed and any
            // corresponding ConfigurationChangeListener childRemoved() callback is called for child SessionAdapters.
            adapter.getSessions();

            childRemoved(adapter);
        }
    }

    public Collection<String> getExchangeTypes()
    {
        Collection<ExchangeType<? extends ExchangeImpl>> types =
                _virtualHost.getExchangeTypes();

        Collection<String> exchangeTypes = new ArrayList<String>();

        for(ExchangeType<? extends ExchangeImpl> type : types)
        {
            exchangeTypes.add(type.getType());
        }
        return Collections.unmodifiableCollection(exchangeTypes);
    }

    public void executeTransaction(TransactionalOperation op)
    {
        MessageStore store = _virtualHost.getMessageStore();
        final LocalTransaction txn = new LocalTransaction(store);

        op.withinTransaction(new Transaction()
        {
            public void dequeue(final MessageInstance entry)
            {
                if(entry.acquire())
                {
                    txn.dequeue(entry.getOwningResource(), entry.getMessage(), new ServerTransaction.Action()
                    {
                        public void postCommit()
                        {
                            entry.delete();
                        }

                        public void onRollback()
                        {
                        }
                    });
                }
            }

            public void copy(MessageInstance entry, Queue queue)
            {
                final ServerMessage message = entry.getMessage();
                final AMQQueue toQueue = (AMQQueue)queue;

                txn.enqueue(toQueue, message, new ServerTransaction.Action()
                {
                    public void postCommit()
                    {
                        toQueue.enqueue(message, null);
                    }

                    public void onRollback()
                    {
                    }
                });

            }

            public void move(final MessageInstance entry, Queue queue)
            {
                final ServerMessage message = entry.getMessage();
                final AMQQueue toQueue = (AMQQueue)queue;
                if(entry.acquire())
                {
                    txn.enqueue(toQueue, message,
                                new ServerTransaction.Action()
                                {

                                    public void postCommit()
                                    {
                                        toQueue.enqueue(message, null);
                                    }

                                    public void onRollback()
                                    {
                                        entry.release();
                                    }
                                });
                    txn.dequeue(entry.getOwningResource(), message,
                                new ServerTransaction.Action()
                                {

                                    public void postCommit()
                                    {
                                        entry.delete();
                                    }

                                    public void onRollback()
                                    {

                                    }
                                });
                }
            }

        });
        txn.commit();
    }

    org.apache.qpid.server.virtualhost.VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    @Override
    public Object getAttribute(String name)
    {
        if(ID.equals(name))
        {
            return getId();
        }
        else if(STATE.equals(name))
        {
            return getState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(QUEUE_ALERT_REPEAT_GAP.equals(name))
        {
            return getAttribute(QUEUE_ALERT_REPEAT_GAP, Broker.QUEUE_ALERT_REPEAT_GAP);
        }
        else if(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE);
        }
        else if(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE);
        }
        else if(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES);
        }
        else if(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES.equals(name))
        {
            return getAttribute(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES);
        }
        else if(QUEUE_DEAD_LETTER_QUEUE_ENABLED.equals(name))
        {
            return getAttribute(QUEUE_DEAD_LETTER_QUEUE_ENABLED, Broker.QUEUE_DEAD_LETTER_QUEUE_ENABLED);
        }
        else if(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS.equals(name))
        {
            return getAttribute(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, Broker.QUEUE_MAXIMUM_DELIVERY_ATTEMPTS);
        }
        else if(QUEUE_FLOW_CONTROL_SIZE_BYTES.equals(name))
        {
            return getAttribute(QUEUE_FLOW_CONTROL_SIZE_BYTES, Broker.QUEUE_FLOW_CONTROL_SIZE_BYTES);
        }
        else if(QUEUE_FLOW_RESUME_SIZE_BYTES.equals(name))
        {
            return getAttribute(QUEUE_FLOW_RESUME_SIZE_BYTES, Broker.QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES);
        }
        else if(HOUSEKEEPING_CHECK_PERIOD.equals(name))
        {
            return getAttribute(HOUSEKEEPING_CHECK_PERIOD, Broker.VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD);
        }
        else if(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE);
        }
        else if(STORE_TRANSACTION_IDLE_TIMEOUT_WARN.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_IDLE_TIMEOUT_WARN, Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN);
        }
        else if(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE);
        }
        else if(STORE_TRANSACTION_OPEN_TIMEOUT_WARN.equals(name))
        {
            return getAttribute(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN);
        }
        else if (_virtualHost != null)
        {
            if(SUPPORTED_EXCHANGE_TYPES.equals(name))
            {
                List<String> types = new ArrayList<String>();
                for(@SuppressWarnings("rawtypes") ExchangeType type : _virtualHost.getExchangeTypes())
                {
                    types.add(type.getType());
                }
                return Collections.unmodifiableCollection(types);
            }
            else if(SUPPORTED_QUEUE_TYPES.equals(name))
            {
                // TODO
            }
        }
        return super.getAttribute(name);
    }


    Object getAttribute(String name, String brokerAttributeName)
    {
        return getAttribute(name, _broker, brokerAttributeName);
    }


    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(VirtualHost.class);
    }

    private void checkVHostStateIsActive()
    {
        if (!org.apache.qpid.server.virtualhost.State.ACTIVE.equals(_virtualHost.getState()))
        {
            throw new IllegalStateException("The virtual hosts state of " + _virtualHost.getState()
                    + " does not permit this operation.");
        }
    }

    @Override
    public Collection<String> getSupportedExchangeTypes()
    {
        List<String> types = new ArrayList<String>();
        for(@SuppressWarnings("rawtypes") ExchangeType type : _virtualHost.getExchangeTypes())
        {
            types.add(type.getType());
        }
        return Collections.unmodifiableCollection(types);
    }

    @Override
    public Collection<String> getSupportedQueueTypes()
    {
        // TODO
        return null;
    }

    @Override
    public boolean isQueue_deadLetterQueueEnabled()
    {
        return (Boolean)getAttribute(VirtualHost.QUEUE_DEAD_LETTER_QUEUE_ENABLED);
    }

    @Override
    public long getHousekeepingCheckPeriod()
    {
        return (Long)getAttribute(VirtualHost.HOUSEKEEPING_CHECK_PERIOD);
    }

    @Override
    public int getQueue_maximumDeliveryAttempts()
    {
        return (Integer)getAttribute(VirtualHost.QUEUE_MAXIMUM_DELIVERY_ATTEMPTS);
    }

    @Override
    public long getQueue_flowControlSizeBytes()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_FLOW_CONTROL_SIZE_BYTES);
    }

    @Override
    public long getQueue_flowResumeSizeBytes()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_FLOW_RESUME_SIZE_BYTES);
    }

    @Override
    public long getStoreTransactionIdleTimeoutClose()
    {
        return (Long)getAttribute(VirtualHost.STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE);
    }

    @Override
    public long getStoreTransactionIdleTimeoutWarn()
    {
        return (Long)getAttribute(VirtualHost.STORE_TRANSACTION_IDLE_TIMEOUT_WARN);
    }

    @Override
    public long getStoreTransactionOpenTimeoutClose()
    {
        return (Long)getAttribute(VirtualHost.STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE);
    }

    @Override
    public long getStoreTransactionOpenTimeoutWarn()
    {
        return (Long)getAttribute(VirtualHost.STORE_TRANSACTION_OPEN_TIMEOUT_WARN);
    }

    @Override
    public long getQueue_alertRepeatGap()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_ALERT_REPEAT_GAP);
    }

    @Override
    public long getQueue_alertThresholdMessageAge()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE);
    }

    @Override
    public long getQueue_alertThresholdMessageSize()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE);
    }

    @Override
    public long getQueue_alertThresholdQueueDepthBytes()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES);
    }

    @Override
    public long getQueue_alertThresholdQueueDepthMessages()
    {
        return (Long)getAttribute(VirtualHost.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getMessageStoreSettings()
    {
        return (Map<String, Object>)getAttribute(VirtualHost.MESSAGE_STORE_SETTINGS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> getConfigurationStoreSettings()
    {
        return (Map<String, Object>)getAttribute(VirtualHost.CONFIGURATION_STORE_SETTINGS);
    }

    @Override
    public long getQueueCount()
    {
        return _virtualHost.getQueues().size();
    }

    @Override
    public long getExchangeCount()
    {
        return _virtualHost.getExchanges().size();
    }

    @Override
    public long getConnectionCount()
    {
        return _virtualHost.getConnectionRegistry().getConnections().size();
    }

    @Override
    public long getBytesIn()
    {
        return _virtualHost.getDataReceiptStatistics().getTotal();
    }

    @Override
    public long getBytesOut()
    {
        return _virtualHost.getDataDeliveryStatistics().getTotal();
    }

    @Override
    public long getMessagesIn()
    {
        return _virtualHost.getMessageReceiptStatistics().getTotal();
    }

    @Override
    public long getMessagesOut()
    {
        return _virtualHost.getMessageDeliveryStatistics().getTotal();
    }

    @Override
    public String getSecurityAcl()
    {
        return (String)getAttribute(SECURITY_ACL);
    }

    @Override
    public int getHouseKeepingThreadCount()
    {
        return (Integer)getAttribute(HOUSE_KEEPING_THREAD_COUNT);
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
            try
            {
                activate();
            }
            catch(RuntimeException e)
            {
                changeAttribute(STATE, State.INITIALISING, State.ERRORED);
                if (_broker.isManagementMode())
                {
                    LOGGER.warn("Failed to activate virtual host: " + getName(), e);
                }
                else
                {
                    throw e;
                }
            }
            return true;
        }
        else if (desiredState == State.STOPPED)
        {
            if (_virtualHost != null)
            {
                try
                {
                    _virtualHost.close();
                }
                finally
                {
                    _broker.getVirtualHostRegistry().unregisterVirtualHost(_virtualHost);
                }
            }
            return true;
        }
        else if (desiredState == State.DELETED)
        {
            String hostName = getName();

            if (hostName.equals(_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST)))
            {
                throw new IntegrityViolationException("Cannot delete default virtual host '" + hostName + "'");
            }
            if (_virtualHost != null)
            {
                if (_virtualHost.getState() == org.apache.qpid.server.virtualhost.State.ACTIVE)
                {
                    setDesiredState(currentState, State.STOPPED);
                }

                MessageStore ms = _virtualHost.getMessageStore();
                if (ms != null)
                {
                    try
                    {
                        ms.onDelete();
                    }
                    catch(Exception e)
                    {
                        LOGGER.warn("Exception occurred on store deletion", e);
                    }
                }

                _virtualHost = null;
            }
            setAttribute(VirtualHost.STATE, getState(), State.DELETED);
            return true;
        }
        return false;
    }

    private void activate()
    {
        VirtualHostRegistry virtualHostRegistry = _broker.getVirtualHostRegistry();
        String virtualHostName = getName();
        try
        {
            String type = (String) getAttribute(TYPE);
            final VirtualHostFactory factory = VirtualHostFactory.FACTORIES.get(type);
            if(factory == null)
            {
                throw new IllegalArgumentException("Unknown virtual host type: " + type);
            }
            else
            {
                _virtualHost = factory.createVirtualHost(_broker.getVirtualHostRegistry(),
                                                         _brokerStatisticsGatherer,
                                                         _broker.getSecurityManager(),
                                                         this);
            }
        }
        catch (Exception e)
        {
            throw new ServerScopedRuntimeException("Failed to create virtual host " + virtualHostName, e);
        }

        virtualHostRegistry.registerVirtualHost(_virtualHost);

        _virtualHost.addVirtualHostListener(this);

        synchronized(_aliases)
        {
            for(Port port :_broker.getPorts())
            {
               if (Protocol.hasAmqpProtocol(port.getProtocols()))
               {
                   _aliases.add(new VirtualHostAliasAdapter(this, port));
               }
            }
        }
    }

    @Override
    public SecurityManager getSecurityManager()
    {
        return _virtualHost.getSecurityManager();
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _virtualHost.getMessageStore();
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        throw new UnsupportedOperationException("Changing attributes on virtualhosts is not supported.");
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), VirtualHost.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of virtual host is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), VirtualHost.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of virtual host attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), VirtualHost.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of virtual host attributes is denied");
        }
    }

    public TaskExecutor getTaskExecutor()
    {
        return super.getTaskExecutor();
    }

}
