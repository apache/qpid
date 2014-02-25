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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.model.*;

final class BindingAdapter extends AbstractConfiguredObject<BindingAdapter> implements Binding<BindingAdapter>
{
    private final org.apache.qpid.server.binding.Binding _binding;
    private final ExchangeAdapter _exchange;
    private QueueAdapter _queue;

    public BindingAdapter(final org.apache.qpid.server.binding.Binding binding,
                          ExchangeAdapter exchangeAdapter,
                          QueueAdapter queueAdapter)
    {
        super(binding.getId(), queueAdapter.getTaskExecutor());
        _binding = binding;
        _exchange = exchangeAdapter;
        _queue = queueAdapter;
        addParent(Queue.class, queueAdapter);
        addParent(Exchange.class, exchangeAdapter);
    }


    public ExchangeAdapter getExchange()
    {
        return _exchange;
    }

    public QueueAdapter getQueue()
    {
        return _queue;
    }

    public String getName()
    {
        return _binding.getBindingKey();
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }

    public State getState()
    {
        return _binding.getState();
    }

    public boolean isDurable()
    {
        return _binding.isDurable();
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
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
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new IllegalArgumentException("Cannot add children to a binding");
    }

    public Map<String, Object> getArguments()
    {
        return new HashMap<String, Object> (_binding.getArguments());
    }

    @Override
    public long getMatches()
    {
        return _binding.getMatches();
    }

    public void delete()
    {
        _binding.delete();
    }

    @Override
    public Object getAttribute(final String name)
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
            return getState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return _queue.getLifetimePolicy() != LifetimePolicy.PERMANENT || _exchange.getLifetimePolicy() != LifetimePolicy.PERMANENT ? LifetimePolicy.IN_USE : LifetimePolicy.PERMANENT;
        }
        else if(TIME_TO_LIVE.equals(name))
        {

        }
        else if(EXCHANGE.equals(name))
        {
            return _exchange.getName();
        }
        else if(QUEUE.equals(name))
        {
            return _queue.getName();
        }
        else if(ARGUMENTS.equals(name))
        {
            return getArguments();
        }

        return super.getAttribute(name);    //TODO
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(Binding.class);
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
    public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException("Changing attributes on binding is not supported.");
    }

    @Override
    public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException("Changing attributes on binding is not supported.");
    }

}
