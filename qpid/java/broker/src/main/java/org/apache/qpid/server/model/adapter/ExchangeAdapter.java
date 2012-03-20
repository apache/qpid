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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInternalException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Publisher;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

final class ExchangeAdapter extends AbstractAdapter implements Exchange, org.apache.qpid.server.exchange.Exchange.BindingListener
{

    private final org.apache.qpid.server.exchange.Exchange _exchange;
    private final Map<Binding, BindingAdapter> _bindingAdapters =
            new HashMap<Binding, BindingAdapter>();
    private VirtualHostAdapter _vhost;


    public ExchangeAdapter(final VirtualHostAdapter virtualHostAdapter,
                           final org.apache.qpid.server.exchange.Exchange exchange)
    {
        super(virtualHostAdapter.getName(), exchange.getName());
        _vhost = virtualHostAdapter;
        _exchange = exchange;
        addParent(org.apache.qpid.server.model.VirtualHost.class, virtualHostAdapter);

        exchange.addBindingListener(this);
        populateBindings();
    }

    private void populateBindings()
    {
        Collection<Binding> actualBindings = _exchange.getBindings();
        synchronized (_bindingAdapters)
        {
            for(Binding binding : actualBindings)
            {
                if(!_bindingAdapters.containsKey(binding))
                {
                    QueueAdapter queueAdapter = _vhost.getQueueAdapter(binding.getQueue());
                    BindingAdapter adapter = new BindingAdapter(binding, this, queueAdapter);
                    _bindingAdapters.put(binding, adapter);

                    queueAdapter.bindingRegistered(binding, adapter);
                }
            }
        }

    }

    public String getExchangeType()
    {
        return _exchange.getType().getName().toString();
    }

    public Collection<org.apache.qpid.server.model.Binding> getBindings()
    {
        synchronized (_bindingAdapters)
        {
            return new ArrayList<org.apache.qpid.server.model.Binding>(_bindingAdapters.values());
        }

    }

    public Collection<Publisher> getPublishers()
    {
        // TODO
        return Collections.emptyList();
    }

    public org.apache.qpid.server.model.Binding createBinding(String bindingKey, Queue queue,
                                                              Map<String, Object> bindingArguments,
                                                              Map<String, Object> attributes)
            throws AccessControlException, IllegalStateException
    {
        VirtualHost virtualHost = _vhost.getVirtualHost();


        AMQQueue amqQueue = ((QueueAdapter)queue).getAMQQueue();

        try
        {
            if(!virtualHost.getBindingFactory().addBinding(bindingKey, amqQueue, _exchange, bindingArguments))
            {
                Binding oldBinding = virtualHost.getBindingFactory().getBinding(bindingKey, amqQueue, _exchange,
                                                                                bindingArguments);

                Map<String, Object> oldArgs = oldBinding.getArguments();
                if((oldArgs == null && !bindingArguments.isEmpty()) || (oldArgs != null && !oldArgs.equals(bindingArguments)))
                {
                    virtualHost.getBindingFactory().replaceBinding(bindingKey, amqQueue, _exchange, bindingArguments);
                }
            }
            Binding binding = virtualHost.getBindingFactory().getBinding(bindingKey, amqQueue, _exchange, bindingArguments);

            synchronized (_bindingAdapters)
            {
                return binding == null ? null : _bindingAdapters.get(binding);
            }
        }
        catch(AMQSecurityException e)
        {
            throw new AccessControlException(e.toString());
        }
        catch(AMQInternalException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public void delete()
    {
        try
        {
            _vhost.getVirtualHost().getExchangeRegistry().unregisterExchange(getName(), false);
        }
        catch(AMQException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public String getName()
    {
        return _exchange.getName();
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }

    public State getActualState()
    {
        return null;  //TODO
    }

    public boolean isDurable()
    {
        return _exchange.isDurable();
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return _exchange.isAutoDelete() ? LifetimePolicy.AUTO_DELETE : LifetimePolicy.PERMANENT;
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

    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    public void bindingAdded(org.apache.qpid.server.exchange.Exchange exchange, Binding binding)
    {
        BindingAdapter adapter = null;
        synchronized (_bindingAdapters)
        {
            if(!_bindingAdapters.containsKey(binding))
            {
                QueueAdapter queueAdapter = _vhost.getQueueAdapter(binding.getQueue());
                adapter = new BindingAdapter(binding, this, queueAdapter);
                _bindingAdapters.put(binding,adapter);
                queueAdapter.bindingRegistered(binding,adapter);
            }
        }
        if(adapter != null)
        {
            childAdded(adapter);
        }
    }

    public void bindingRemoved(org.apache.qpid.server.exchange.Exchange exchange, Binding binding)
    {
        BindingAdapter adapter = null;
        synchronized (_bindingAdapters)
        {
            adapter = _bindingAdapters.remove(binding);
        }
        if(adapter != null)
        {
            _vhost.getQueueAdapter(binding.getQueue()).bindingUnregistered(binding);
            childRemoved(adapter);
        }
    }

    org.apache.qpid.server.exchange.Exchange getExchange()
    {
        return _exchange;
    }
}
