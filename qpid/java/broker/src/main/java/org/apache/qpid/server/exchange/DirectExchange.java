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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.filter.JMSSelectorFilter;
import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class DirectExchange extends AbstractExchange
{

    private static final Logger _logger = Logger.getLogger(DirectExchange.class);

    private static final class BindingSet
    {
        private CopyOnWriteArraySet<Binding> _bindings = new CopyOnWriteArraySet<Binding>();
        private List<BaseQueue> _unfilteredQueues = new ArrayList<BaseQueue>();
        private Map<BaseQueue, MessageFilter> _filteredQueues = new HashMap<BaseQueue, MessageFilter>();

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
            Map<BaseQueue, MessageFilter> filteredQueues = new HashMap<BaseQueue,MessageFilter>();

            for(Binding b : _bindings)
            {

                if(FilterSupport.argumentsContainFilter(b.getArguments()))
                {
                    try
                    {
                        MessageFilter filter = FilterSupport.createMessageFilter(b.getArguments(), b.getQueue());
                        filteredQueues.put(b.getQueue(),filter);
                    }
                    catch (AMQInvalidArgumentException e)
                    {
                        _logger.warn("Binding ignored: cannot parse filter on binding of queue '"+b.getQueue().getName()
                                     + "' to exchange '" + b.getExchange().getName()
                                     + "' with arguments: " + b.getArguments(), e);
                    }

                }
                else
                {

                    if(!queues.contains(b.getQueue()))
                    {
                        queues.add(b.getQueue());
                    }
                }
            }
            _unfilteredQueues = queues;
            _filteredQueues = filteredQueues;
        }


        public List<BaseQueue> getUnfilteredQueues()
        {
            return _unfilteredQueues;
        }

        public CopyOnWriteArraySet<Binding> getBindings()
        {
            return _bindings;
        }

        public boolean hasFilteredQueues()
        {
            return !_filteredQueues.isEmpty();
        }

        public Map<BaseQueue,MessageFilter> getFilteredQueues()
        {
            return _filteredQueues;
        }
    }

    private final ConcurrentHashMap<String, BindingSet> _bindingsByKey =
            new ConcurrentHashMap<String, BindingSet>();

    public static final ExchangeType<DirectExchange> TYPE = new DirectExchangeType();

    public DirectExchange()
    {
        super(TYPE);
    }

    public List<? extends BaseQueue> doRoute(InboundMessage payload)
    {

        final String routingKey = payload.getRoutingKey();

        BindingSet bindings = _bindingsByKey.get(routingKey == null ? "" : routingKey);

        if(bindings != null)
        {
            List<BaseQueue> queues = bindings.getUnfilteredQueues();

            if(bindings.hasFilteredQueues())
            {
                Set<BaseQueue> queuesSet = new HashSet<BaseQueue>(queues);

                Map<BaseQueue, MessageFilter> filteredQueues = bindings.getFilteredQueues();
                for(Map.Entry<BaseQueue, MessageFilter> entry : filteredQueues.entrySet())
                {
                    if(!queuesSet.contains(entry.getKey()))
                    {
                        MessageFilter filter = entry.getValue();
                        if(filter.matches(payload))
                        {
                            queuesSet.add(entry.getKey());
                        }
                    }
                }
                if(queues.size() != queuesSet.size())
                {
                    queues = new ArrayList<BaseQueue>(queuesSet);
                }
            }
            return queues;
        }
        else
        {
            return Collections.emptyList();
        }


    }

    protected void onBind(final Binding binding)
    {
        String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getQueue();

        assert queue != null;
        assert bindingKey != null;

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
