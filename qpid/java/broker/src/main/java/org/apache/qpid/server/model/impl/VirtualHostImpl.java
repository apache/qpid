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
package org.apache.qpid.server.model.impl;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

class VirtualHostImpl extends AbstractConfiguredObject implements VirtualHost
{
    // attribute names
    private static final String REPLICATION_GROUP_NAME = "replicationGroupName";

    private final Collection<VirtualHostAlias> _aliases = new ArrayList<VirtualHostAlias>();
    private final Collection<Connection> _connections = new ArrayList<Connection>();
    private final Collection<Queue> _queues = new ArrayList<Queue>();
    private final Collection<Exchange> _exchanges = new ArrayList<Exchange>();

    private final Map<String, Queue> _queueMap = new ConcurrentHashMap<String, Queue>();
    private final Map<String, Exchange> _exchangeMap= new ConcurrentHashMap<String, Exchange>();

    private final BrokerImpl _broker;


    VirtualHostImpl(final UUID id,
                    final String name,
                    final State state,
                    final boolean durable,
                    final LifetimePolicy lifetimePolicy,
                    final long timeToLive,
                    final Map<String, Object> attributes,
                    final BrokerImpl parent)
    {
        super(id, name, state, durable, lifetimePolicy, timeToLive, attributes,
              (Map) Collections.singletonMap(Broker.class, parent));

        _broker = parent;
    }

    @Override
    protected Object getLock()
    {
        return this;
    }

    public String getReplicationGroupName()
    {
        return (String) getAttribute(REPLICATION_GROUP_NAME);
    }

    @Override
    public Object setAttribute(final String name, final Object expected, final Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        synchronized(getLock())
        {
            if(REPLICATION_GROUP_NAME.equals(name))
            {
                if(getActualState() != State.STOPPED)
                {
                    throw new IllegalStateException("A virtual host must be stopped before you can change the replication group");
                }
                if(!(desired instanceof String))
                {
                    throw new IllegalArgumentException("The desired replication group MUST be a String");
                }
            }
            return super.setAttribute(name, expected, desired);
        }
    }

    public Statistics getStatistics()
    {
        return null;  //TODO
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException(); // TODO
    }

    public Collection<VirtualHostAlias> getAliases()
    {
        synchronized(getLock())
        {
            return new ArrayList<VirtualHostAlias>(_aliases);
        }
    }

    public Collection<Connection> getConnections()
    {
        synchronized (getLock())
        {
            return new ArrayList<Connection>(_connections);
        }
    }

    public Collection<Queue> getQueues()
    {
        synchronized (getLock())
        {
            return new ArrayList<Queue>(_queues);
        }
    }

    public Collection<Exchange> getExchanges()
    {
        synchronized (getLock())
        {
            return new ArrayList<Exchange>(_exchanges);
        }
    }

    public State getActualState()
    {
        final State brokerActualState = _broker.getActualState();
        return brokerActualState == State.ACTIVE ? getDesiredState() : brokerActualState;
    }


    public Exchange createExchange(String name, State initialState,boolean durable,
                                         LifetimePolicy lifetime, long ttl, String type, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        // TODO - check name is valid and not reserved
        // TODO - check type
        // TODO - check permissions

        synchronized (getLock())
        {
            for(Exchange exchange : _exchanges)
            {
                if(exchange.getName().equals(name))
                {
                    throw new IllegalArgumentException("A exchange with the name '"+name+"' already exists");
                }
            }
            ExchangeImpl exchange = new ExchangeImpl(UUID.randomUUID(),
                                                        name,
                                                        initialState,
                                                        durable,
                                                        lifetime,
                                                        ttl,
                                                        type,
                                                        attributes,
                                                        this);
            _exchanges.add(exchange);
            _exchangeMap.put(name, exchange);

            notifyChildAddedListener(exchange);
            return exchange;
        }
    }


    public Queue createQueue(String name, State initialState,boolean durable,
                                   boolean exclusive, LifetimePolicy lifetime, long ttl, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        // TODO - check name is valid and not reserved
        // TODO - check permissions

        synchronized (getLock())
        {
            for(Queue queue : _queues)
            {
                if(queue.getName().equals(name))
                {
                    throw new IllegalArgumentException("A queue with the name '"+name+"' already exists");
                }
            }
            QueueImpl queue = new QueueImpl(UUID.randomUUID(),
                                            name,
                                            initialState,
                                            durable,
                                            lifetime,
                                            ttl,
                                            attributes,
                                            this);

            _queues.add(queue);
            _queueMap.put(name, queue);

            notifyChildAddedListener(queue);
            // TODO - add binding to default exchange?, or make the default exchange work directly off the map held here

            return queue;
        }
    }

    public void deleteQueue(Queue queue)
    {
        synchronized (getLock())
        {
            boolean found = _queues.remove(queue);
            if (!found)
            {
                throw new IllegalArgumentException("A queue with the name '" + queue.getName()+ "' does not exist");
            }
            _queueMap.remove(queue.getName());
            notifyChildRemovedListener(queue);
        }
    }

    public Collection<String> getExchangeTypes()
    {
        return null;  // TODO - Implement
    }

    public void executeTransaction(TransactionalOperation op)
    {
        // TODO - Implement
    }
}
