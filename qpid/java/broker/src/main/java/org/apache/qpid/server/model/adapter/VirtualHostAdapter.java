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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;

final class VirtualHostAdapter extends AbstractAdapter implements VirtualHost, ExchangeRegistry.RegistryChangeListener,
                                                                  QueueRegistry.RegistryChangeListener,
                                                                  IConnectionRegistry.RegistryChangeListener
{

    private final org.apache.qpid.server.virtualhost.VirtualHost _virtualHost;

    private final Map<AMQConnectionModel, ConnectionAdapter> _connectionAdapters =
            new HashMap<AMQConnectionModel, ConnectionAdapter>();

    private final Map<AMQQueue, QueueAdapter> _queueAdapters =
            new HashMap<AMQQueue, QueueAdapter>();

    private final Map<org.apache.qpid.server.exchange.Exchange, ExchangeAdapter> _exchangeAdapters =
            new HashMap<org.apache.qpid.server.exchange.Exchange, ExchangeAdapter>();

    private final StatisticsAdapter _statistics;

    private final BrokerAdapter _broker;

    private final List<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();


    VirtualHostAdapter(BrokerAdapter brokerAdapter,
                       final org.apache.qpid.server.virtualhost.VirtualHost virtualHost)
    {
        super(virtualHost.getName());
        _broker = brokerAdapter;
        _virtualHost = virtualHost;
        _statistics = new StatisticsAdapter(virtualHost);
        virtualHost.getQueueRegistry().addRegistryChangeListener(this);
        populateQueues();
        virtualHost.getExchangeRegistry().addRegistryChangeListener(this);
        populateExchanges();
        virtualHost.getConnectionRegistry().addRegistryChangeListener(this);
        populateConnections();



        for(Port port :_broker.getPorts())
        {
            _aliases.add(new VirtualHostAliasAdapter(this, port));
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

        synchronized(_queueAdapters)
        {
            for(AMQQueue queue : actualQueues)
            {
                if(!_queueAdapters.containsKey(queue))
                {
                    _queueAdapters.put(queue, new QueueAdapter(this,queue));
                }
            }
        }
    }

    private void populateConnections()
    {

        List<AMQConnectionModel> actualConnections = _virtualHost.getConnectionRegistry().getConnections();

        synchronized(_connectionAdapters)
        {
            for(AMQConnectionModel conn : actualConnections)
            {
                if(!_connectionAdapters.containsKey(conn))
                {
                    _connectionAdapters.put(conn, new ConnectionAdapter(conn));
                }
            }
        }

    }

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
        
        String         name     = getStringAttribute(Exchange.NAME, attributes, null);
        State          state    = getEnumAttribute(State.class, Exchange.STATE, attributes, State.ACTIVE);
        boolean        durable  = getBooleanAttribute(Exchange.DURABLE, attributes, false);        
        LifetimePolicy lifetime = getEnumAttribute(LifetimePolicy.class, Exchange.LIFETIME_POLICY, attributes, LifetimePolicy.PERMANENT);
        String         type     = getStringAttribute(Exchange.TYPE, attributes, null);
        long           ttl      = getLongAttribute(Exchange.TIME_TO_LIVE, attributes, 0l);
        
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
        try
        {
            org.apache.qpid.server.exchange.Exchange exchange =
                    _virtualHost.getExchangeFactory().createExchange(name, type, durable,
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
        catch(AMQException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    public Queue createQueue(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        attributes = new HashMap<String, Object>(attributes);

        String         name     = getStringAttribute(Queue.NAME, attributes, null);
        State          state    = getEnumAttribute(State.class, Queue.STATE, attributes, State.ACTIVE);
        boolean        durable  = getBooleanAttribute(Queue.DURABLE, attributes, false);
        LifetimePolicy lifetime = getEnumAttribute(LifetimePolicy.class, Queue.LIFETIME_POLICY, attributes, LifetimePolicy.PERMANENT);
        long           ttl      = getLongAttribute(Queue.TIME_TO_LIVE, attributes, 0l);
        boolean        exclusive= getBooleanAttribute(Queue.EXCLUSIVE, attributes, false);

        attributes.remove(Queue.NAME);
        attributes.remove(Queue.STATE);
        attributes.remove(Queue.DURABLE);
        attributes.remove(Queue.LIFETIME_POLICY);
        attributes.remove(Queue.TIME_TO_LIVE);

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
        String owner = null;
        if(exclusive)
        {
            Set<Principal> principals =
                    SecurityManager.getThreadSubject().getPrincipals();
            if(principals != null && !principals.isEmpty())
            {
                owner = principals.iterator().next().getName();
            }
        }
        try
        {
            AMQQueue queue =
                    AMQQueueFactory.createAMQQueueImpl(UUIDGenerator.generateUUID(name, _virtualHost.getName()), name,
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
        catch(AMQException e)
        {
            throw new IllegalArgumentException(e);
        }

    }

    public String getName()
    {
        return _virtualHost.getName();
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }

    public State getActualState()
    {
        return getDesiredState();
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
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
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
                adapter = new ConnectionAdapter(connection);
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

    public void deleteQueue(Queue queue)
        throws AccessControlException, IllegalStateException
    {
        // TODO
        throw new UnsupportedOperationException("Not Yet Implemented");
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
        else if(NAME.equals(name))
        {
            return getName();
        }
        else if(STATE.equals(name))
        {
            return State.ACTIVE;
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
        else if(SUPPORTED_EXCHANGE_TYPES.equals(name))
        {
            List<String> types = new ArrayList<String>();
            for(ExchangeType type : _virtualHost.getExchangeFactory().getRegisteredTypes())
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
        else if(FEDERATION_TAG.equals(name))
        {
            return _virtualHost.getFederationTag();
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
            // TODO
        }
        else if(STORE_CONFIGURATION.equals(name))
        {
            // TODO
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
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return super.setAttribute(name, expected, desired);    //TODO - Implement
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }
}
