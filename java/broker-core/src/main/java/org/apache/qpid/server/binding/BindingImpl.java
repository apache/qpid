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
package org.apache.qpid.server.binding;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.server.configuration.updater.VoidTask;
import org.apache.qpid.server.exchange.AbstractExchange;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.BindingMessages;
import org.apache.qpid.server.logging.subjects.BindingLogSubject;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class BindingImpl
        extends AbstractConfiguredObject<BindingImpl>
        implements org.apache.qpid.server.model.Binding<BindingImpl>
{
    private String _bindingKey;
    private final AMQQueue _queue;
    private final ExchangeImpl _exchange;
    @ManagedAttributeField
    private Map<String, Object> _arguments;
    private final AtomicLong _matches = new AtomicLong();
    private BindingLogSubject _logSubject;

    final AtomicBoolean _deleted = new AtomicBoolean();
    final CopyOnWriteArrayList<StateChangeListener<BindingImpl,State>> _stateChangeListeners =
            new CopyOnWriteArrayList<StateChangeListener<BindingImpl, State>>();

    public BindingImpl(Map<String, Object> attributes, AMQQueue queue, ExchangeImpl exchange)
    {
        super(parentsMap(queue,exchange),stripEmptyArguments(enhanceWithDurable(attributes, queue, exchange)));
        _bindingKey = getName();
        _queue = queue;
        _exchange = exchange;
    }

    private static Map<String, Object> stripEmptyArguments(final Map<String, Object> attributes)
    {
        Map<String,Object> returnVal;
        if(attributes != null
           && attributes.containsKey(Binding.ARGUMENTS)
           && (attributes.get(Binding.ARGUMENTS) instanceof Map)
           && ((Map)(attributes.get(Binding.ARGUMENTS))).isEmpty())
        {
            returnVal = new HashMap<>(attributes);
            returnVal.remove(Binding.ARGUMENTS);
        }
        else
        {
            returnVal = attributes;
        }

        return returnVal;
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _logSubject = new BindingLogSubject(_bindingKey,_exchange,_queue);

        getEventLogger().message(_logSubject, BindingMessages.CREATED(String.valueOf(getArguments()),
                                                                      getArguments() != null
                                                                      && !getArguments().isEmpty()));
        if(_exchange instanceof AbstractExchange)
        {
            ((AbstractExchange)_exchange).doAddBinding(this);
        }
    }

    @Override
    protected void onCreate()
    {
        super.onCreate();
        try
        {
            _queue.getVirtualHost().getSecurityManager().authoriseCreateBinding(this);
        }
        catch(AccessControlException e)
        {
            deleted();
            throw e;
        }
        if (isDurable())
        {
            _queue.getVirtualHost().getDurableConfigurationStore().create(asObjectRecord());
        }

    }

    private static Map<String, Object> enhanceWithDurable(Map<String, Object> attributes,
                                                          final AMQQueue queue,
                                                          final ExchangeImpl exchange)
    {
        if(!attributes.containsKey(DURABLE))
        {
            attributes = new HashMap(attributes);
            attributes.put(DURABLE, queue.isDurable() && exchange.isDurable());
        }
        return attributes;
    }

    public String getBindingKey()
    {
        return _bindingKey;
    }

    public AMQQueue getAMQQueue()
    {
        return _queue;
    }

    @Override
    public Queue<?> getQueue()
    {
        return _queue;
    }

    @Override
    public ExchangeImpl<?> getExchange()
    {
        return _exchange;
    }

    public Map<String, Object> getArguments()
    {
        return _arguments;
    }

    public void incrementMatches()
    {
        _matches.incrementAndGet();
    }

    public long getMatches()
    {
        return _matches.get();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(final Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        
        if (!(o instanceof BindingImpl))
        {
            return false;
        }

        final BindingImpl binding = (BindingImpl) o;

        return (_bindingKey == null ? binding.getBindingKey() == null : _bindingKey.equals(binding.getBindingKey()))
            && (_exchange == null ? binding.getExchange() == null : _exchange.equals(binding.getExchange()))
            && (_queue == null ? binding.getAMQQueue() == null : _queue.equals(binding.getAMQQueue()));
    }

    @Override
    public int hashCode()
    {
        int result = _bindingKey == null ? 1 : _bindingKey.hashCode();
        result = 31 * result + (_queue == null ? 3 : _queue.hashCode());
        result = 31 * result + (_exchange == null ? 5 : _exchange.hashCode());
        return result;
    }

    public String toString()
    {
        return "Binding{bindingKey="+_bindingKey+", exchange="+_exchange+", queue="+_queue+", id= " + getId() + " }";
    }

    @StateTransition(currentState = State.ACTIVE, desiredState = State.DELETED)
    private void doDelete()
    {
        if(_deleted.compareAndSet(false,true))
        {
            for(StateChangeListener<BindingImpl,State> listener : _stateChangeListeners)
            {
                listener.stateChanged(this, State.ACTIVE, State.DELETED);
            }
            getEventLogger().message(_logSubject, BindingMessages.DELETED());
        }
        setState(State.DELETED);
    }

    @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.ACTIVE)
    private void activate()
    {
        setState(State.ACTIVE);
    }

    public void addStateChangeListener(StateChangeListener<BindingImpl,State> listener)
    {
        _stateChangeListeners.add(listener);
    }

    public void removeStateChangeListener(StateChangeListener<BindingImpl,State> listener)
    {
        _stateChangeListeners.remove(listener);
    }

    private EventLogger getEventLogger()
    {
        return _exchange.getEventLogger();
    }

    public void setArguments(final Map<String, Object> arguments)
    {
        runTask(new VoidTask()
                {
                    @Override
                    public void execute()
                    {
                        _arguments = arguments;
                        BindingImpl.super.setAttribute(ARGUMENTS, getActualAttributes().get(ARGUMENTS), arguments);
                        if (isDurable())
                        {
                            VirtualHostImpl<?, ?, ?> vhost =
                                    (VirtualHostImpl<?, ?, ?>) _exchange.getParent(VirtualHost.class);
                            vhost.getDurableConfigurationStore().update(true, asObjectRecord());
                        }
                    }
                }
               );

    }
}
