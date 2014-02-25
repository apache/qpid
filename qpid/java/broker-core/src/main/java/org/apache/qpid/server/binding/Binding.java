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

import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BindingMessages;
import org.apache.qpid.server.logging.subjects.BindingLogSubject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.adapter.AbstractConfiguredObject;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.util.StateChangeListener;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Binding
{
    private final String _bindingKey;
    private final AMQQueue _queue;
    private final Exchange _exchange;
    private final Map<String, Object> _arguments;
    private final UUID _id;
    private final AtomicLong _matches = new AtomicLong();
    private final BindingLogSubject _logSubject;

    final AtomicBoolean _deleted = new AtomicBoolean();
    final CopyOnWriteArrayList<StateChangeListener<Binding,State>> _stateChangeListeners =
            new CopyOnWriteArrayList<StateChangeListener<Binding, State>>();


    public Binding(UUID id,
                   final String bindingKey,
                   final AMQQueue queue,
                   final Exchange exchange,
                   final Map<String, Object> arguments)
    {
        this(id, convertToAttributes(bindingKey, arguments), queue, exchange);
    }

    private static Map<String, Object> convertToAttributes(final String bindingKey, final Map<String, Object> arguments)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(org.apache.qpid.server.model.Binding.NAME,bindingKey);
        if(arguments != null)
        {
            attributes.put(org.apache.qpid.server.model.Binding.ARGUMENTS, arguments);
        }
        return attributes;
    }

    public Binding(UUID id, Map<String,Object> attributes, AMQQueue queue, Exchange exchange)
    {
        _id = id;
        _bindingKey = (String)attributes.get(org.apache.qpid.server.model.Binding.NAME);
        _queue = queue;
        _exchange = exchange;
        Map<String,Object> arguments = (Map<String, Object>) attributes.get(org.apache.qpid.server.model.Binding.ARGUMENTS);
        _arguments = arguments == null ? Collections.EMPTY_MAP : Collections.unmodifiableMap(arguments);

        //Perform ACLs
        queue.getVirtualHost().getSecurityManager().authoriseCreateBinding(this);
        _logSubject = new BindingLogSubject(_bindingKey,exchange,queue);
        CurrentActor.get().message(_logSubject, BindingMessages.CREATED(String.valueOf(getArguments()),
                                                                        getArguments() != null
                                                                        && !getArguments().isEmpty()));


    }

    public UUID getId()
    {
        return _id;
    }

    public String getBindingKey()
    {
        return _bindingKey;
    }

    public AMQQueue getAMQQueue()
    {
        return _queue;
    }

    public Exchange getExchangeImpl()
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

    public boolean isDurable()
    {
        return _queue.isDurable() && _exchange.isDurable();
    }


    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.IN_USE;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        
        if (!(o instanceof Binding))
        {
            return false;
        }

        final Binding binding = (Binding) o;

        return (_bindingKey == null ? binding.getBindingKey() == null : _bindingKey.equals(binding.getBindingKey()))
            && (_exchange == null ? binding.getExchangeImpl() == null : _exchange.equals(binding.getExchangeImpl()))
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

    protected boolean setState(final State currentState, final State desiredState)
    {
        if(desiredState == State.DELETED)
        {
            delete();
            return true;
        }
        else
        {
            return false;
        }
    }

    public String toString()
    {
        return "Binding{bindingKey="+_bindingKey+", exchange="+_exchange+", queue="+_queue+", id= " + _id + " }";
    }

    public void delete()
    {
        if(_deleted.compareAndSet(false,true))
        {
            for(StateChangeListener<Binding,State> listener : _stateChangeListeners)
            {
                listener.stateChanged(this, State.ACTIVE, State.DELETED);
            }
            CurrentActor.get().message(_logSubject, BindingMessages.DELETED());
        }
    }

    public String getName()
    {
        return _bindingKey;
    }

    public State getState()
    {
        return _deleted.get() ? State.DELETED : State.ACTIVE;
    }

    public void addStateChangeListener(StateChangeListener<Binding,State> listener)
    {
        _stateChangeListeners.add(listener);
    }

    public void removeStateChangeListener(StateChangeListener<Binding,State> listener)
    {
        _stateChangeListeners.remove(listener);
    }
}
