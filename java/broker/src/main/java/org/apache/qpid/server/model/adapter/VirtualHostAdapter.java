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

import java.io.File;
import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.configuration.XmlConfigurationUtilities.MyConfiguration;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.QueueType;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.util.FileUtils;

public final class VirtualHostAdapter extends AbstractAdapter implements VirtualHost, ExchangeRegistry.RegistryChangeListener,
                                                                  QueueRegistry.RegistryChangeListener,
                                                                  IConnectionRegistry.RegistryChangeListener
{

    private static final Logger LOGGER = Logger.getLogger(VirtualHostAdapter.class);

    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(STORE_PATH, String.class);
        put(STORE_TYPE, String.class);
        put(CONFIG_PATH, String.class);
        put(STATE, State.class);
    }});

    private org.apache.qpid.server.virtualhost.VirtualHost _virtualHost;

    private final Map<AMQConnectionModel, ConnectionAdapter> _connectionAdapters =
            new HashMap<AMQConnectionModel, ConnectionAdapter>();

    private final Map<AMQQueue, QueueAdapter> _queueAdapters =
            new HashMap<AMQQueue, QueueAdapter>();

    private final Map<org.apache.qpid.server.exchange.Exchange, ExchangeAdapter> _exchangeAdapters =
            new HashMap<org.apache.qpid.server.exchange.Exchange, ExchangeAdapter>();
    private StatisticsAdapter _statistics;
    private final Broker _broker;
    private final List<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();
    private StatisticsGatherer _brokerStatisticsGatherer;

    public VirtualHostAdapter(UUID id, Map<String, Object> attributes, Broker broker, StatisticsGatherer brokerStatisticsGatherer, TaskExecutor taskExecutor)
    {
        super(id, null, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), taskExecutor);
        validateAttributes();
        _broker = broker;
        _brokerStatisticsGatherer = brokerStatisticsGatherer;
        addParent(Broker.class, broker);
    }

    private void validateAttributes()
    {
        String name = getName();
        if (name == null || "".equals(name.trim()))
        {
            throw new IllegalConfigurationException("Virtual host name must be specified");
        }

        String configurationFile = (String) getAttribute(CONFIG_PATH);
        String storeType = (String) getAttribute(STORE_TYPE);
        boolean invalidAttributes = false;
        if (configurationFile == null)
        {
            if (storeType == null)
            {
                invalidAttributes = true;
            }
        }
        else
        {
            if (storeType != null)
            {
                invalidAttributes = true;
            }
        }
        if (invalidAttributes)
        {
            throw new IllegalConfigurationException("Please specify either the 'configPath' attribute or 'storeType' and 'storePath' attributes");
        }
    }

    private void populateExchanges()
    {
        Collection<org.apache.qpid.server.exchange.Exchange> actualExchanges =
                _virtualHost.getExchangeRegistry().getExchanges();

        synchronized (_exchangeAdapters)
        {
            for(org.apache.qpid.server.exchange.Exchange exchange : actualExchanges)
            {
                if(!_exchangeAdapters.containsKey(exchange))
                {
                    _exchangeAdapters.put(exchange, new ExchangeAdapter(this,exchange));
                }
            }
        }
    }


    private void populateQueues()
    {
        Collection<AMQQueue> actualQueues = _virtualHost.getQueueRegistry().getQueues();
        if ( actualQueues != null )
        {
            synchronized(_queueAdapters)
            {
                for(AMQQueue queue : actualQueues)
                {
                    if(!_queueAdapters.containsKey(queue))
                    {
                        _queueAdapters.put(queue, new QueueAdapter(this, queue));
                    }
                }
            }
        }
    }

    @Override
    public String getReplicationGroupName()
    {
        return null;  //TODO
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

    public Collection<Queue> getQueues()
    {
        synchronized(_queueAdapters)
        {
            return new ArrayList<Queue>(_queueAdapters.values());
        }
    }

    public Collection<Exchange> getExchanges()
    {
        synchronized (_exchangeAdapters)
        {
            return new ArrayList<Exchange>(_exchangeAdapters.values());
        }
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
        long           ttl      = MapValueConverter.getLongAttribute(Exchange.TIME_TO_LIVE, attributes, 0l);

        attributes.remove(Exchange.NAME);
        attributes.remove(Exchange.STATE);
        attributes.remove(Exchange.DURABLE);
        attributes.remove(Exchange.LIFETIME_POLICY);
        attributes.remove(Exchange.TYPE);
        attributes.remove(Exchange.TIME_TO_LIVE);

        return createExchange(name, state, durable, lifetime, ttl, type, attributes);
    }

    public Exchange createExchange(final String name,
                                   final State initialState,
                                   final boolean durable,
                                   final LifetimePolicy lifetime,
                                   final long ttl,
                                   final String type,
                                   final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        checkVHostStateIsActive();

        try
        {
            ExchangeRegistry exchangeRegistry = _virtualHost.getExchangeRegistry();
            if (exchangeRegistry.isReservedExchangeName(name))
            {
                throw new UnsupportedOperationException("'" + name + "' is a reserved exchange name");
            }
            synchronized(exchangeRegistry)
            {
                org.apache.qpid.server.exchange.Exchange exchange = exchangeRegistry.getExchange(name);
                if (exchange != null)
                {
                    throw new IllegalArgumentException("Exchange with name '" + name + "' already exists");
                }
                exchange = _virtualHost.getExchangeFactory().createExchange(name, type, durable,
                                                                     lifetime == LifetimePolicy.AUTO_DELETE);
                _virtualHost.getExchangeRegistry().registerExchange(exchange);
                if(durable)
                {
                    _virtualHost.getMessageStore().createExchange(exchange);
                }
                synchronized (_exchangeAdapters)
                {
                    return _exchangeAdapters.get(exchange);
                }
            }
        }
        catch(AMQException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public Queue createQueue(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        attributes = new HashMap<String, Object>(attributes);

        if (attributes.containsKey(Queue.TYPE))
        {
            String typeAttribute = MapValueConverter.getStringAttribute(Queue.TYPE, attributes, null);
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
                attributes.put(Queue.LVQ_KEY, AMQQueueFactory.QPID_LVQ_KEY);
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
        String         name     = MapValueConverter.getStringAttribute(Queue.NAME, attributes, null);
        State          state    = MapValueConverter.getEnumAttribute(State.class, Queue.STATE, attributes, State.ACTIVE);
        boolean        durable  = MapValueConverter.getBooleanAttribute(Queue.DURABLE, attributes, false);
        LifetimePolicy lifetime = MapValueConverter.getEnumAttribute(LifetimePolicy.class, Queue.LIFETIME_POLICY, attributes, LifetimePolicy.PERMANENT);
        long           ttl      = MapValueConverter.getLongAttribute(Queue.TIME_TO_LIVE, attributes, 0l);
        boolean        exclusive= MapValueConverter.getBooleanAttribute(Queue.EXCLUSIVE, attributes, false);

        attributes.remove(Queue.NAME);
        attributes.remove(Queue.STATE);
        attributes.remove(Queue.DURABLE);
        attributes.remove(Queue.LIFETIME_POLICY);
        attributes.remove(Queue.TIME_TO_LIVE);

        List<String> attrNames = new ArrayList<String>(attributes.keySet());
        for(String attr : attrNames)
        {
            if(QueueAdapter.ATTRIBUTE_MAPPINGS.containsKey(attr))
            {
                attributes.put(QueueAdapter.ATTRIBUTE_MAPPINGS.get(attr),attributes.remove(attr));
            }
        }

        return createQueue(name, state, durable, exclusive, lifetime, ttl, attributes);
    }

    public Queue createQueue(final String name,
                             final State initialState,
                             final boolean durable,
                             boolean exclusive,
                             final LifetimePolicy lifetime,
                             final long ttl,
                             final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        checkVHostStateIsActive();

        String owner = null;
        if(exclusive)
        {
            Principal authenticatedPrincipal = AuthenticatedPrincipal.getOptionalAuthenticatedPrincipalFromSubject(SecurityManager.getThreadSubject());
            if(authenticatedPrincipal != null)
            {
                owner = authenticatedPrincipal.getName();
            }
        }
        try
        {
            QueueRegistry queueRegistry = _virtualHost.getQueueRegistry();
            synchronized (queueRegistry)
            {
                if(_virtualHost.getQueueRegistry().getQueue(name)!=null)
                {
                    throw new IllegalArgumentException("Queue with name "+name+" already exists");
                }
                AMQQueue queue =
                        AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateQueueUUID(name, _virtualHost.getName()), name,
                                                           durable, owner, lifetime == LifetimePolicy.AUTO_DELETE,
                                                           exclusive, _virtualHost, attributes);
                _virtualHost.getBindingFactory().addBinding(name, queue, _virtualHost.getExchangeRegistry().getDefaultExchange(), null);

                if(durable)
                {
                    _virtualHost.getMessageStore().createQueue(queue, FieldTable.convertToFieldTable(attributes));
                }
                synchronized (_queueAdapters)
                {
                    return _queueAdapters.get(queue);
                }
            }

        }
        catch(AMQException e)
        {
            throw new IllegalArgumentException(e);
        }

    }

    public String getName()
    {
        return (String)getAttribute(NAME);
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }

    @Override
    public State getActualState()
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
                return State.QUIESCED;
            case STOPPED:
                return State.STOPPED;
            default:
                // unexpected state
                return null;
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

    public Statistics getStatistics()
    {
        return _statistics;
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
            return (C) createExchange(attributes);
        }
        else if(childClass == Queue.class)
        {
            return (C) createQueue(attributes);
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

    public void exchangeRegistered(org.apache.qpid.server.exchange.Exchange exchange)
    {
        ExchangeAdapter adapter = null;
        synchronized (_exchangeAdapters)
        {
            if(!_exchangeAdapters.containsKey(exchange))
            {
                adapter = new ExchangeAdapter(this, exchange);
                _exchangeAdapters.put(exchange, adapter);

            }

        }
        if(adapter != null)
        {
            childAdded(adapter);
        }

    }


    public void exchangeUnregistered(org.apache.qpid.server.exchange.Exchange exchange)
    {
        ExchangeAdapter adapter;
        synchronized (_exchangeAdapters)
        {
            adapter = _exchangeAdapters.remove(exchange);

        }

        if(adapter != null)
        {
            childRemoved(adapter);
        }
    }

    public void queueRegistered(AMQQueue queue)
    {
        QueueAdapter adapter = null;
        synchronized (_queueAdapters)
        {
            if(!_queueAdapters.containsKey(queue))
            {
                adapter = new QueueAdapter(this, queue);
                _queueAdapters.put(queue, adapter);

            }

        }
        if(adapter != null)
        {
            childAdded(adapter);
        }

    }

    public void queueUnregistered(AMQQueue queue)
    {

        QueueAdapter adapter;
        synchronized (_queueAdapters)
        {
            adapter = _queueAdapters.remove(queue);

        }

        if(adapter != null)
        {
            childRemoved(adapter);
        }
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
            childRemoved(adapter);
        }
    }

    QueueAdapter getQueueAdapter(AMQQueue queue)
    {
        synchronized (_queueAdapters)
        {
            return _queueAdapters.get(queue);
        }
    }

    public Collection<String> getExchangeTypes()
    {
        Collection<ExchangeType<? extends org.apache.qpid.server.exchange.Exchange>> types =
                _virtualHost.getExchangeFactory().getRegisteredTypes();

        Collection<String> exchangeTypes = new ArrayList<String>();

        for(ExchangeType<? extends org.apache.qpid.server.exchange.Exchange> type : types)
        {
            exchangeTypes.add(type.getName().asString());
        }
        return Collections.unmodifiableCollection(exchangeTypes);
    }

    public void executeTransaction(TransactionalOperation op)
    {
        MessageStore store = _virtualHost.getMessageStore();
        final LocalTransaction txn = new LocalTransaction(store);

        op.withinTransaction(new Transaction()
        {
            public void dequeue(final QueueEntry entry)
            {
                if(entry.acquire())
                {
                    txn.dequeue(entry.getQueue(), entry.getMessage(), new ServerTransaction.Action()
                    {
                        public void postCommit()
                        {
                            entry.discard();
                        }

                        public void onRollback()
                        {
                        }
                    });
                }
            }

            public void copy(QueueEntry entry, Queue queue)
            {
                final ServerMessage message = entry.getMessage();
                final AMQQueue toQueue = ((QueueAdapter)queue).getAMQQueue();

                txn.enqueue(toQueue, message, new ServerTransaction.Action()
                {
                    public void postCommit()
                    {
                        try
                        {
                            toQueue.enqueue(message);
                        }
                        catch(AMQException e)
                        {
                            throw new RuntimeException(e);
                        }
                    }

                    public void onRollback()
                    {
                    }
                });

            }

            public void move(final QueueEntry entry, Queue queue)
            {
                final ServerMessage message = entry.getMessage();
                final AMQQueue toQueue = ((QueueAdapter)queue).getAMQQueue();
                if(entry.acquire())
                {
                    txn.enqueue(toQueue, message,
                                new ServerTransaction.Action()
                                {

                                    public void postCommit()
                                    {
                                        try
                                        {
                                            toQueue.enqueue(message);
                                        }
                                        catch (AMQException e)
                                        {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    public void onRollback()
                                    {
                                        entry.release();
                                    }
                                });
                    txn.dequeue(entry.getQueue(), message,
                                new ServerTransaction.Action()
                                {

                                    public void postCommit()
                                    {
                                        entry.discard();
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
            return getActualState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if(CREATED.equals(name))
        {
            // TODO
        }
        else if(UPDATED.equals(name))
        {
            // TODO
        }
        else if (_virtualHost != null)
        {
            return getAttributeFromVirtualHostImplementation(name);
        }
        return super.getAttribute(name);
    }

    private Object getAttributeFromVirtualHostImplementation(String name)
    {
        if(SUPPORTED_EXCHANGE_TYPES.equals(name))
        {
            List<String> types = new ArrayList<String>();
            for(@SuppressWarnings("rawtypes") ExchangeType type : _virtualHost.getExchangeFactory().getRegisteredTypes())
            {
                types.add(type.getName().asString());
            }
            return Collections.unmodifiableCollection(types);
        }
        else if(SUPPORTED_QUEUE_TYPES.equals(name))
        {
            // TODO
        }
        else if(DEAD_LETTER_QUEUE_ENABLED.equals(name))
        {
            return _virtualHost.getConfiguration().isDeadLetterQueueEnabled();
        }
        else if(HOUSEKEEPING_CHECK_PERIOD.equals(name))
        {
            return _virtualHost.getConfiguration().getHousekeepingCheckPeriod();
        }
        else if(MAXIMUM_DELIVERY_ATTEMPTS.equals(name))
        {
            return _virtualHost.getConfiguration().getMaxDeliveryCount();
        }
        else if(QUEUE_FLOW_CONTROL_SIZE_BYTES.equals(name))
        {
            return _virtualHost.getConfiguration().getCapacity();
        }
        else if(QUEUE_FLOW_RESUME_SIZE_BYTES.equals(name))
        {
            return _virtualHost.getConfiguration().getFlowResumeCapacity();
        }
        else if(STORE_TYPE.equals(name))
        {
            return _virtualHost.getMessageStore().getStoreType();
        }
        else if(STORE_PATH.equals(name))
        {
            return _virtualHost.getMessageStore().getStoreLocation();
        }
        else if(STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE.equals(name))
        {
            return _virtualHost.getConfiguration().getTransactionTimeoutIdleClose();
        }
        else if(STORE_TRANSACTION_IDLE_TIMEOUT_WARN.equals(name))
        {
            return _virtualHost.getConfiguration().getTransactionTimeoutIdleWarn();
        }
        else if(STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE.equals(name))
        {
            return _virtualHost.getConfiguration().getTransactionTimeoutOpenClose();
        }
        else if(STORE_TRANSACTION_OPEN_TIMEOUT_WARN.equals(name))
        {
            return _virtualHost.getConfiguration().getTransactionTimeoutOpenWarn();
        }
        else if(ALERT_REPEAT_GAP.equals(name))
        {
            return _virtualHost.getConfiguration().getMinimumAlertRepeatGap();
        }
        else if(ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
        {
            return _virtualHost.getConfiguration().getMaximumMessageAge();
        }
        else if(ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
        {
            return _virtualHost.getConfiguration().getMaximumMessageSize();
        }
        else if(ALERT_THRESHOLD_QUEUE_DEPTH_BYTES.equals(name))
        {
            return _virtualHost.getConfiguration().getMaximumQueueDepth();
        }
        else if(ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES.equals(name))
        {
            return _virtualHost.getConfiguration().getMaximumMessageCount();
        }
        return super.getAttribute(name);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

    private void checkVHostStateIsActive()
    {
        if (!org.apache.qpid.server.virtualhost.State.ACTIVE.equals(_virtualHost.getState()))
        {
            throw new IllegalStateException("The virtual hosts state of " + _virtualHost.getState()
                    + " does not permit this operation.");
        }
    }


    private static class VirtualHostStatisticsAdapter extends StatisticsAdapter
    {
        private final org.apache.qpid.server.virtualhost.VirtualHost _vhost;

        private static final Collection<String> VHOST_STATS = Arrays.asList(
                VirtualHost.QUEUE_COUNT,
                VirtualHost.EXCHANGE_COUNT,
                VirtualHost.CONNECTION_COUNT);

        public VirtualHostStatisticsAdapter(org.apache.qpid.server.virtualhost.VirtualHost virtualHost)
        {
            super(virtualHost);
            _vhost = virtualHost;
        }

        @Override
        public Collection<String> getStatisticNames()
        {
            Set<String> stats = new HashSet<String>(super.getStatisticNames());
            stats.addAll(VHOST_STATS);
            return stats;
        }

        @Override
        public Object getStatistic(String name)
        {
            if(VirtualHost.QUEUE_COUNT.equals(name))
            {
                return _vhost.getQueueRegistry().getQueues().size();
            }
            else if(VirtualHost.EXCHANGE_COUNT.equals(name))
            {
                return _vhost.getExchangeRegistry().getExchanges().size();
            }
            else if(VirtualHost.CONNECTION_COUNT.equals(name))
            {
                return _vhost.getConnectionRegistry().getConnections().size();
            }
            else
            {
                return super.getStatistic(name);
            }
        }
    }


    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
            activate();
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
            //TODO: add ACL check to authorize the operation

            String hostName = getName();

            if (hostName.equals(_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST)))
            {
                throw new IntegrityViolationException("Cannot delete default virtual host '" + hostName + "'");
            }
            if (_virtualHost != null && _virtualHost.getState() == org.apache.qpid.server.virtualhost.State.ACTIVE)
            {
                setDesiredState(currentState, State.STOPPED);
            }
            _virtualHost = null;
            setAttribute(VirtualHost.STATE, getActualState(), State.DELETED);
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
            VirtualHostConfiguration configuration = createVirtualHostConfiguration(virtualHostName);
            _virtualHost = new VirtualHostImpl(_broker.getVirtualHostRegistry(), _brokerStatisticsGatherer, _broker.getSecurityManager(), configuration);
        }
        catch (Exception e)
        {
           throw new RuntimeException("Failed to create virtual host " + virtualHostName, e);
        }

        virtualHostRegistry.registerVirtualHost(_virtualHost);

        _statistics = new VirtualHostStatisticsAdapter(_virtualHost);
        _virtualHost.getQueueRegistry().addRegistryChangeListener(this);
        populateQueues();
        _virtualHost.getExchangeRegistry().addRegistryChangeListener(this);
        populateExchanges();
        _virtualHost.getConnectionRegistry().addRegistryChangeListener(this);

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

    private VirtualHostConfiguration createVirtualHostConfiguration(String virtualHostName) throws ConfigurationException
    {
        VirtualHostConfiguration configuration;
        String configurationFile = (String)getAttribute(CONFIG_PATH);
        if (configurationFile == null)
        {
            final MyConfiguration basicConfiguration = new MyConfiguration();
            PropertiesConfiguration config = new PropertiesConfiguration();
            config.addProperty("store.type", (String)getAttribute(STORE_TYPE));
            config.addProperty("store.environment-path", (String)getAttribute(STORE_PATH));
            basicConfiguration.addConfiguration(config);

            CompositeConfiguration compositeConfiguration = new CompositeConfiguration();
            compositeConfiguration.addConfiguration(new SystemConfiguration());
            compositeConfiguration.addConfiguration(basicConfiguration);
            configuration = new VirtualHostConfiguration(virtualHostName, compositeConfiguration , _broker);
        }
        else
        {
            configuration = new VirtualHostConfiguration(virtualHostName, new File(configurationFile) , _broker);
        }
        return configuration;
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
        if (State.ACTIVE.equals(getActualState()))
        {
            throw new IllegalStateException("Cannot change host attributes on active virtual host. This operation is only supported in management mode.");
        }
        Map<String, Object> newAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        validateConfigurationAndCopyStoreIntoNewLocationIfRequired(newAttributes);
        super.changeAttributes(newAttributes);
    }

    private void validateConfigurationAndCopyStoreIntoNewLocationIfRequired(Map<String, Object> newAttributes)
    {
        String name = (String)getAttribute(NAME);
        String configPath =  (String)getAttribute(CONFIG_PATH);
        String storePath =  (String)getAttribute(STORE_PATH);
        String storeType = (String)getAttribute(STORE_TYPE);

        String newConfigPath =  (String)newAttributes.get(CONFIG_PATH);
        String newStorePath =  (String)newAttributes.get(STORE_PATH);
        String newStoreType = (String)newAttributes.get(STORE_TYPE);

        String newName = (String)newAttributes.get(NAME);
        if (newName != null && !newName.equals(name))
        {
            if (name.equals(_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST)))
            {
                throw new IntegrityViolationException("Cannot rename virtual host '" + name + "' as it is set as a default." +
                        " Change the broker default virtual host before renaming");
            }
        }
        if (newConfigPath != null)
        {
            // try to open new configuration xml and extract information about message store
            try
            {
                Map<String, String> storeDetails = getStoreDetailsFromVirtualHostConfigXml(name, configPath);
                newStorePath = storeDetails.get(STORE_PATH);
                newStoreType = storeDetails.get(STORE_TYPE);
            }
            catch (Exception e)
            {
                throw new IllegalConfigurationException("Cannot open new virtual host configuration at " + newConfigPath, e);
            }
            newAttributes.put(STORE_PATH, null);
            newAttributes.put(STORE_TYPE, null);
        }
        else
        {
            newAttributes.put(CONFIG_PATH, null);
        }

        if (configPath != null )
        {
            // try to identify store type and location in order to copy old store into a new location
            try
            {
                Map<String, String> storeDetails = getStoreDetailsFromVirtualHostConfigXml(name, configPath);
                storePath = storeDetails.get(STORE_PATH);
                storeType = storeDetails.get(STORE_TYPE);
            }
            catch (Exception e)
            {
                // old configuration might be broken
                LOGGER.warn("Cannot open virtual host cofiguration at " + configPath + ". Ignoring old broken configuration.", e);
            }
        }

        if (storeType != null && storePath != null && newStoreType != null)
        {
            File oldStoreLocation = new File(storePath);
            if (oldStoreLocation.exists())
            {
                if (newStoreType.equals(newStoreType))
                {
                    File newStoreLocation = new File(newStorePath);
                    if (!oldStoreLocation.equals(newStoreLocation))
                    {
                        if (LOGGER.isInfoEnabled())
                        {
                            LOGGER.info("Copying store for virtual host '" + name + "' from '"
                                    + oldStoreLocation.getAbsolutePath() + "' into '" + newStoreLocation.getAbsolutePath() + "'");
                        }
                        copyStoreFiles(oldStoreLocation, newStoreLocation);
                    }
                }
                else
                {
                    LOGGER.warn("Requested a message store of different type ("
                            + newStoreType + ") than existing store (" + storeType
                            + "). At the moment, copying of data is not supported for stores of different types."
                            + " As result an empty new store will be created and old data will be lost.");
                }
            }
            else
            {
                if (LOGGER.isInfoEnabled())
                {
                    LOGGER.info("Virtual host '" + name + "' store does not exists at " + oldStoreLocation.getAbsolutePath() + ". Skipping srore copying...");
                }
            }
        }
    }

    private void copyStoreFiles(File oldStoreLocation, File newStoreLocation)
    {
        if (!newStoreLocation.exists() && !newStoreLocation.getParentFile().exists())
        {
            newStoreLocation.getParentFile().mkdirs();
        }
        try
        {
            if (oldStoreLocation.isFile())
            {
                if (!newStoreLocation.exists())
                {
                    newStoreLocation.createNewFile();
                }
                FileUtils.copy(oldStoreLocation, newStoreLocation);
            }
            else
            {
                if (!newStoreLocation.exists())
                {
                    newStoreLocation.mkdir();
                }
                FileUtils.copyRecursive(oldStoreLocation, newStoreLocation);
            }
        }
        catch (Exception e)
        {
            throw new IllegalConfigurationException("Cannot copy store data into a new location at " + newStoreLocation, e);
        }
    }

    private Map<String, String> getStoreDetailsFromVirtualHostConfigXml(String name, String configPath) throws Exception
    {
        Map<String, String> storeDetails = new HashMap<String, String>();
        VirtualHostConfiguration configuration = new VirtualHostConfiguration(name, new File(configPath) , _broker);
        String storePath = configuration.getStoreConfiguration().getString("environment-path");
        String storeType = configuration.getStoreConfiguration().getString("type");
        if (storeType == null)
        {
            String storeClass = configuration.getStoreConfiguration().getString("class");
            if (storeClass != null)
            {
                final Class<?> clazz = Class.forName(storeClass);
                final Object o = clazz.newInstance();

                if (o instanceof MessageStore)
                {
                    MessageStore ms = (MessageStore)o;
                    storeType =  ms.getStoreType();
                }
            }
        }

        storeDetails.put(STORE_PATH, storePath);
        storeDetails.put(STORE_TYPE, storeType);
        return storeDetails;
    }
}
