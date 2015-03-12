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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.filter.AMQInvalidArgumentException;
import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

@ManagedObject( category = false, type = ExchangeDefaults.FANOUT_EXCHANGE_CLASS )
public class FanoutExchange extends AbstractExchange<FanoutExchange>
{
    private static final Logger _logger = LoggerFactory.getLogger(FanoutExchange.class);

    private static final Integer ONE = Integer.valueOf(1);

    /**
     * Maps from queue name to queue instances
     */
    private final Map<AMQQueue,Integer> _queues = new HashMap<AMQQueue,Integer>();
    private final CopyOnWriteArrayList<AMQQueue> _unfilteredQueues = new CopyOnWriteArrayList<AMQQueue>();
    private final CopyOnWriteArrayList<AMQQueue> _filteredQueues = new CopyOnWriteArrayList<AMQQueue>();

    private final AtomicReference<Map<AMQQueue,Map<BindingImpl, MessageFilter>>>  _filteredBindings =
            new AtomicReference<Map<AMQQueue,Map<BindingImpl, MessageFilter>>>();
    {
        Map<AMQQueue,Map<BindingImpl, MessageFilter>> emptyMap = Collections.emptyMap();
        _filteredBindings.set(emptyMap);
    }

    @ManagedObjectFactoryConstructor
    public FanoutExchange(final Map<String, Object> attributes, final VirtualHostImpl vhost)
    {
        super(attributes, vhost);
    }

    @Override
    public ArrayList<BaseQueue> doRoute(ServerMessage payload,
                                        final String routingKey,
                                        final InstanceProperties instanceProperties)
    {

        for(BindingImpl b : getBindings())
        {
            b.incrementMatches();
        }

        final ArrayList<BaseQueue> result = new ArrayList<BaseQueue>(_unfilteredQueues);


        final Map<AMQQueue, Map<BindingImpl, MessageFilter>> filteredBindings = _filteredBindings.get();
        if(!_filteredQueues.isEmpty())
        {
            for(AMQQueue q : _filteredQueues)
            {
                final Map<BindingImpl, MessageFilter> bindingMessageFilterMap = filteredBindings.get(q);
                if(!(bindingMessageFilterMap == null || result.contains(q)))
                {
                    for(MessageFilter filter : bindingMessageFilterMap.values())
                    {
                        if(filter.matches(Filterable.Factory.newInstance(payload,instanceProperties)))
                        {
                            result.add(q);
                            break;
                        }
                    }
                }
            }

        }


        if (_logger.isDebugEnabled())
        {
            _logger.debug("Publishing message to queue " + result);
        }

        return result;

    }

    @Override
    protected synchronized void onBindingUpdated(final BindingImpl binding, final Map<String, Object> oldArguments)
    {
        AMQQueue queue = binding.getAMQQueue();

        if (binding.getArguments() == null || binding.getArguments().isEmpty() || !FilterSupport.argumentsContainFilter(
                binding.getArguments()))
        {
            if(oldArguments != null && !oldArguments.isEmpty() && FilterSupport.argumentsContainFilter(oldArguments))
            {
                _unfilteredQueues.add(queue);
                if(_queues.containsKey(queue))
                {
                    _queues.put(queue,_queues.get(queue)+1);
                }
                else
                {
                    _queues.put(queue, ONE);
                }

                // No longer any reason to check filters for this queue
                _filteredQueues.remove(queue);
            }
            // else - nothing has changed, remains unfiltered
        }
        else
        {
            HashMap<AMQQueue,Map<BindingImpl, MessageFilter>> filteredBindings =
                    new HashMap<AMQQueue,Map<BindingImpl, MessageFilter>>(_filteredBindings.get());

            Map<BindingImpl,MessageFilter> bindingsForQueue;

            final MessageFilter messageFilter;

            try
            {
                messageFilter = FilterSupport.createMessageFilter(binding.getArguments(), binding.getAMQQueue());
            }
            catch (AMQInvalidArgumentException e)
            {
                _logger.warn("Cannot bind queue " + queue + " to exchange this " + this + " because selector cannot be parsed.", e);
                return;
            }


            if (oldArguments != null && !oldArguments.isEmpty() && FilterSupport.argumentsContainFilter(oldArguments))
            {
                bindingsForQueue = new HashMap<BindingImpl,MessageFilter>(filteredBindings.remove(binding.getAMQQueue()));
            }
            else // previously unfiltered
            {
                bindingsForQueue = new HashMap<BindingImpl,MessageFilter>();

                Integer oldValue = _queues.remove(queue);
                if (ONE.equals(oldValue))
                {
                    // should start checking filters for this queue
                    _filteredQueues.add(queue);
                    _unfilteredQueues.remove(queue);
                }
                else
                {
                    _queues.put(queue, oldValue - 1);
                }

            }
            bindingsForQueue.put(binding, messageFilter);
            filteredBindings.put(binding.getAMQQueue(),bindingsForQueue);

            _filteredBindings.set(filteredBindings);

        }

    }

    protected synchronized void onBind(final BindingImpl binding)
    {
        AMQQueue queue = binding.getAMQQueue();
        assert queue != null;
        if(binding.getArguments() == null || binding.getArguments().isEmpty() || !FilterSupport.argumentsContainFilter(binding.getArguments()))
        {

            Integer oldVal;
            if(_queues.containsKey(queue))
            {
                _queues.put(queue,_queues.get(queue)+1);
            }
            else
            {
                _queues.put(queue, ONE);
                _unfilteredQueues.add(queue);
                // No longer any reason to check filters for this queue
                _filteredQueues.remove(queue);
            }

        }
        else
        {
            try
            {

                HashMap<AMQQueue,Map<BindingImpl, MessageFilter>> filteredBindings =
                        new HashMap<AMQQueue,Map<BindingImpl, MessageFilter>>(_filteredBindings.get());

                Map<BindingImpl, MessageFilter> bindingsForQueue = filteredBindings.remove(binding.getAMQQueue());
                final MessageFilter messageFilter =
                        FilterSupport.createMessageFilter(binding.getArguments(), binding.getAMQQueue());

                if(bindingsForQueue != null)
                {
                    bindingsForQueue = new HashMap<BindingImpl,MessageFilter>(bindingsForQueue);
                    bindingsForQueue.put(binding, messageFilter);
                }
                else
                {
                    bindingsForQueue = Collections.singletonMap(binding, messageFilter);
                    if(!_unfilteredQueues.contains(queue))
                    {
                        _filteredQueues.add(queue);
                    }
                }

                filteredBindings.put(binding.getAMQQueue(), bindingsForQueue);

                _filteredBindings.set(filteredBindings);

            }
            catch (AMQInvalidArgumentException e)
            {
                _logger.warn("Cannot bind queue " + queue + " to exchange this " + this + " because selector cannot be parsed.", e);
                return;
            }
        }
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Binding queue " + queue
                          + " with routing key " + binding.getBindingKey() + " to exchange " + this);
        }
    }

    protected synchronized void onUnbind(final BindingImpl binding)
    {
        AMQQueue queue = binding.getAMQQueue();
        if(binding.getArguments() == null || binding.getArguments().isEmpty() || !FilterSupport.argumentsContainFilter(binding.getArguments()))
        {
            Integer oldValue = _queues.remove(queue);
            if(ONE.equals(oldValue))
            {
                // should start checking filters for this queue
                if(_filteredBindings.get().containsKey(queue))
                {
                    _filteredQueues.add(queue);
                }
                _unfilteredQueues.remove(queue);
            }
            else
            {
                _queues.put(queue,oldValue-1);
            }
        }
        else // we are removing a binding with filters
        {
            HashMap<AMQQueue,Map<BindingImpl, MessageFilter>> filteredBindings =
                    new HashMap<AMQQueue,Map<BindingImpl, MessageFilter>>(_filteredBindings.get());

            Map<BindingImpl,MessageFilter> bindingsForQueue = filteredBindings.remove(binding.getAMQQueue());
            if(bindingsForQueue.size()>1)
            {
                bindingsForQueue = new HashMap<BindingImpl,MessageFilter>(bindingsForQueue);
                bindingsForQueue.remove(binding);
                filteredBindings.put(binding.getAMQQueue(),bindingsForQueue);
            }
            else
            {
                _filteredQueues.remove(queue);
            }
            _filteredBindings.set(filteredBindings);

        }
    }
}
