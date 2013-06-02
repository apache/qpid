/*
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
 */
package org.apache.qpid.server.exchange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DefaultExchange implements Exchange
{

    private UUID _id;
    private VirtualHost _virtualHost;
    private int _ticket;
    private static final Logger _logger = Logger.getLogger(DefaultExchange.class);
    private final AtomicBoolean _closed = new AtomicBoolean();

    private LogSubject _logSubject;
    private Map<ExchangeReferrer,Object> _referrers = new ConcurrentHashMap<ExchangeReferrer,Object>();


    @Override
    public void initialise(UUID id,
                           VirtualHost host,
                           AMQShortString name,
                           boolean durable,
                           int ticket,
                           boolean autoDelete) throws AMQException
    {
        _id = id;
        _virtualHost = host;
        _ticket = ticket;
    }

    @Override
    public String getName()
    {
        return ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString();
    }

    @Override
    public ExchangeType getType()
    {
        return DirectExchange.TYPE;
    }

    @Override
    public long getBindingCount()
    {
        return _virtualHost.getQueueRegistry().getQueues().size();
    }

    @Override
    public long getByteDrops()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public long getByteReceives()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public long getMsgDrops()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public long getMsgReceives()
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean addBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
            throws AMQSecurityException, AMQInternalException
    {
        throw new AMQSecurityException("Cannot add bindings to the default exchange");
    }

    @Override
    public boolean replaceBinding(UUID id, String bindingKey, AMQQueue queue, Map<String, Object> arguments)
            throws AMQSecurityException, AMQInternalException
    {
        throw new AMQSecurityException("Cannot replace bindings on the default exchange");
    }

    @Override
    public void restoreBinding(UUID id, String bindingKey, AMQQueue queue, Map<String, Object> argumentMap)
            throws AMQSecurityException, AMQInternalException
    {
        _logger.warn("Bindings to the default exchange should not be stored in the configuration store");
    }

    @Override
    public void removeBinding(Binding b) throws AMQSecurityException, AMQInternalException
    {
        throw new AMQSecurityException("Cannot remove bindings to the default exchange");
    }

    @Override
    public Binding removeBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
            throws AMQSecurityException, AMQInternalException
    {
        throw new AMQSecurityException("Cannot remove bindings to the default exchange");
    }

    @Override
    public Binding getBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        if(_virtualHost.getQueueRegistry().getQueue(bindingKey) == queue && (arguments == null || arguments.isEmpty()))
        {
            return convertToBinding(queue);
        }
        else
        {
            return null;
        }

    }

    private Binding convertToBinding(AMQQueue queue)
    {
        String queueName = queue.getName();

        UUID exchangeId = UUIDGenerator.generateBindingUUID(ExchangeDefaults.DEFAULT_EXCHANGE_NAME.asString(),
                                                            queueName,
                                                            queueName,
                                                            _virtualHost.getName());

        return new Binding(exchangeId, queueName, queue, this, Collections.EMPTY_MAP);
    }

    @Override
    public AMQShortString getNameShortString()
    {
        return AMQShortString.EMPTY_STRING;
    }

    @Override
    public AMQShortString getTypeShortString()
    {
        return getType().getName();
    }

    @Override
    public boolean isDurable()
    {
        return false;
    }

    @Override
    public boolean isAutoDelete()
    {
        return false;
    }

    @Override
    public int getTicket()
    {
        return _ticket;
    }

    @Override
    public void close() throws AMQException
    {
        if(_closed.compareAndSet(false,true))
        {

            CurrentActor.get().message(_logSubject, ExchangeMessages.DELETED());

        }
    }

    @Override
    public List<AMQQueue> route(InboundMessage message)
    {
        AMQQueue q = _virtualHost.getQueueRegistry().getQueue(message.getRoutingKey());
        if(q == null)
        {
            List<AMQQueue> noQueues = Collections.emptyList();
            return noQueues;
        }
        else
        {
            return Collections.singletonList(q);
        }

    }

    @Override
    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey, queue) && (arguments == null || arguments.isEmpty());
    }

    @Override
    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return isBound(routingKey) && isBound(queue) && queue.getNameShortString().equals(routingKey);  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isBound(AMQShortString routingKey)
    {
        return _virtualHost.getQueueRegistry().getQueue(routingKey) != null;
    }

    @Override
    public boolean isBound(AMQQueue queue)
    {
        return _virtualHost.getQueueRegistry().getQueue(queue.getName()) == queue;
    }

    @Override
    public boolean hasBindings()
    {
        return getBindingCount() != 0;
    }

    @Override
    public boolean isBound(String bindingKey, AMQQueue queue)
    {
        return isBound(queue) && queue.getName().equals(bindingKey);
    }

    @Override
    public boolean isBound(String bindingKey, Map<String, Object> arguments, AMQQueue queue)
    {
        return isBound(bindingKey, queue) && (arguments == null || arguments.isEmpty());
    }

    @Override
    public boolean isBound(Map<String, Object> arguments, AMQQueue queue)
    {
        return (arguments == null || arguments.isEmpty()) && isBound(queue);
    }

    @Override
    public boolean isBound(String bindingKey, Map<String, Object> arguments)
    {
        return (arguments == null || arguments.isEmpty()) && isBound(bindingKey);
    }

    @Override
    public boolean isBound(Map<String, Object> arguments)
    {
        return (arguments == null || arguments.isEmpty()) && hasBindings();
    }

    @Override
    public boolean isBound(String bindingKey)
    {
        return _virtualHost.getQueueRegistry().getQueue(bindingKey) != null;
    }

    @Override
    public Exchange getAlternateExchange()
    {
        return null;
    }

    @Override
    public void setAlternateExchange(Exchange exchange)
    {
        _logger.warn("Cannot set the alternate exchange for the default exchange");
    }

    @Override
    public void removeReference(ExchangeReferrer exchange)
    {
        _referrers.remove(exchange);
    }

    @Override
    public void addReference(ExchangeReferrer exchange)
    {
        _referrers.put(exchange, Boolean.TRUE);
    }

    @Override
    public boolean hasReferrers()
    {
        return !_referrers.isEmpty();
    }

    @Override
    public Collection<Binding> getBindings()
    {
        List<Binding> bindings = new ArrayList<Binding>();
        for(AMQQueue q : _virtualHost.getQueueRegistry().getQueues())
        {
            bindings.add(convertToBinding(q));
        }
        return bindings;
    }

    @Override
    public void addBindingListener(BindingListener listener)
    {
        _virtualHost.getQueueRegistry().addRegistryChangeListener(convertListener(listener));//To change body of implemented methods use File | Settings | File Templates.
    }

    private QueueRegistry.RegistryChangeListener convertListener(final BindingListener listener)
    {
        return new QueueRegistry.RegistryChangeListener()
        {
            @Override
            public void queueRegistered(AMQQueue queue)
            {
                listener.bindingAdded(DefaultExchange.this, convertToBinding(queue));
            }

            @Override
            public void queueUnregistered(AMQQueue queue)
            {
                listener.bindingRemoved(DefaultExchange.this, convertToBinding(queue));
            }
        };
    }

    @Override
    public void removeBindingListener(BindingListener listener)
    {
        // TODO
    }

    @Override
    public UUID getId()
    {
        return _id;
    }
}
