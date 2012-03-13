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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.security.SecurityManager;

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


    public VirtualHostAdapter(final org.apache.qpid.server.virtualhost.VirtualHost virtualHost)
    {
        _virtualHost = virtualHost;
        _statistics = new StatisticsAdapter(virtualHost);
        virtualHost.getQueueRegistry().addRegistryChangeListener(this);
        populateQueues();
        virtualHost.getExchangeRegistry().addRegistryChangeListener(this);
        populateExchanges();
        virtualHost.getConnectionRegistry().addRegistryChangeListener(this);
        populateConnections();

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
        return null;  //TODO
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
                _virtualHost.getDurableConfigurationStore().createExchange(exchange);
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

    public Queue createQueue(final String name,
                             final State initialState,
                             final boolean durable,
                             final LifetimePolicy lifetime,
                             final long ttl,
                             final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        boolean exclusive = false;
        String owner;
        if(exclusive)
        {
            owner = null;
        }
        else
        {
            Set<Principal> principals =
                    SecurityManager.getThreadSubject().getPrincipals();
            if(principals != null && !principals.isEmpty())
            {
                owner = principals.iterator().next().getName();
            }
            else
            {
                owner = null;
            }
        }
        try
        {
            AMQQueue queue =
                    AMQQueueFactory.createAMQQueueImpl(name, durable, owner, lifetime == LifetimePolicy.AUTO_DELETE,
                                                       exclusive,
                                                       _virtualHost, attributes);
            _virtualHost.getQueueRegistry().registerQueue(queue);
            if(durable)
            {
                _virtualHost.getDurableConfigurationStore().createQueue(queue, FieldTable.convertToFieldTable(attributes));
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

    private final Map<org.apache.qpid.server.subscription.Subscription, SubscriptionAdapter> _allSubscriptions =
            new WeakHashMap<org.apache.qpid.server.subscription.Subscription, SubscriptionAdapter>();

    public SubscriptionAdapter getOrCreateAdapter(final org.apache.qpid.server.subscription.Subscription subscription)
    {
        synchronized(_allSubscriptions)
        {
            SubscriptionAdapter adapter = _allSubscriptions.get(subscription);
            if(adapter == null)
            {
                adapter = new SubscriptionAdapter(subscription);
                _allSubscriptions.put(subscription, adapter);
            }
            return adapter;
        }
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
}
