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
package org.apache.qpid.server.exchange;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.management.AbstractExchangeMBean;
import org.apache.qpid.server.management.DirectExchangeMBean;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class DirectExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(DirectExchange.class);

    private static final class BindingSet
    {
        private CopyOnWriteArraySet<Binding> _bindings = new CopyOnWriteArraySet<Binding>();
        private List<BaseQueue> _queues = new ArrayList<BaseQueue>();

        public synchronized void addBinding(Binding binding)
        {
            _bindings.add(binding);
            recalculateQueues();
        }


        public synchronized void removeBinding(Binding binding)
        {
            _bindings.remove(binding);
            recalculateQueues();
        }

        private void recalculateQueues()
        {
            List<BaseQueue> queues = new ArrayList<BaseQueue>(_bindings.size());

            for(Binding b : _bindings)
            {
                if(!queues.contains(b.getQueue()))
                {
                    queues.add(b.getQueue());
                }
            }
            _queues = queues;
        }


        public List<BaseQueue> getQueues()
        {
            return _queues;
        }

        public CopyOnWriteArraySet<Binding> getBindings()
        {
            return _bindings;
        }
    }

    private final ConcurrentHashMap<String, BindingSet> _bindingsByKey =
            new ConcurrentHashMap<String, BindingSet>();

    public static final ExchangeType<DirectExchange> TYPE = new ExchangeType<DirectExchange>()
    {

        public AMQShortString getName()
        {
            return ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
        }

        public Class<DirectExchange> getExchangeClass()
        {
            return DirectExchange.class;
        }

        public DirectExchange newInstance(UUID id, VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            int ticket,
                                            boolean autoDelete) throws AMQException
        {
            DirectExchange exch = new DirectExchange();
            exch.initialise(id, host,name,durable,ticket,autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        }
    };


    public DirectExchange()
    {
        super(TYPE);
    }

    protected AbstractExchangeMBean createMBean() throws JMException
    {
        return new DirectExchangeMBean(this);
    }

    public List<? extends BaseQueue> doRoute(InboundMessage payload)
    {

        final String routingKey = payload.getRoutingKey();

        BindingSet bindings = _bindingsByKey.get(routingKey == null ? "" : routingKey);

        if(bindings != null)
        {
            return bindings.getQueues();
        }
        else
        {
            return Collections.emptyList();
        }



    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey,queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        String bindingKey = (routingKey == null) ? "" : routingKey.toString();
        BindingSet bindings = _bindingsByKey.get(bindingKey);
        if(bindings != null)
        {
            return bindings.getQueues().contains(queue);
        }
        return false;

    }

    public boolean isBound(AMQShortString routingKey)
    {
        String bindingKey = (routingKey == null) ? "" : routingKey.toString();
        BindingSet bindings = _bindingsByKey.get(bindingKey);
        return bindings != null && !bindings.getQueues().isEmpty();
    }

    public boolean isBound(AMQQueue queue)
    {

        for (BindingSet bindings : _bindingsByKey.values())
        {
            if(bindings.getQueues().contains(queue))
            {
                return true;
            }

        }
        return false;
    }

    public boolean hasBindings()
    {
        return !getBindings().isEmpty();
    }

    protected void onBind(final Binding binding)
    {
        String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getQueue();
        AMQShortString routingKey = AMQShortString.valueOf(bindingKey);

        assert queue != null;
        assert routingKey != null;

        BindingSet bindings = _bindingsByKey.get(bindingKey);

        if(bindings == null)
        {
            bindings = new BindingSet();
            BindingSet newBindings;
            if((newBindings = _bindingsByKey.putIfAbsent(bindingKey, bindings)) != null)
            {
                bindings = newBindings;
            }
        }

        bindings.addBinding(binding);

    }

    protected void onUnbind(final Binding binding)
    {
        assert binding != null;

        BindingSet bindings = _bindingsByKey.get(binding.getBindingKey());
        if(bindings != null)
        {
            bindings.removeBinding(binding);
        }

    }


}
