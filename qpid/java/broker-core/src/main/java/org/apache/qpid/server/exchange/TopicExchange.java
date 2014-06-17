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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.exchange.topic.TopicExchangeResult;
import org.apache.qpid.server.exchange.topic.TopicMatcherResult;
import org.apache.qpid.server.exchange.topic.TopicNormalizer;
import org.apache.qpid.server.exchange.topic.TopicParser;
import org.apache.qpid.server.filter.AMQInvalidArgumentException;
import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

@ManagedObject( category = false, type = ExchangeDefaults.TOPIC_EXCHANGE_CLASS )
public class TopicExchange extends AbstractExchange<TopicExchange>
{
    private static final Logger _logger = Logger.getLogger(TopicExchange.class);

    private final TopicParser _parser = new TopicParser();

    private final Map<String, TopicExchangeResult> _topicExchangeResults =
            new ConcurrentHashMap<String, TopicExchangeResult>();

    private final Map<BindingImpl, Map<String,Object>> _bindings = new HashMap<BindingImpl, Map<String,Object>>();

    @ManagedObjectFactoryConstructor
    public TopicExchange(final Map<String,Object> attributes, final VirtualHostImpl vhost)
    {
        super(attributes, vhost);
    }

    @Override
    protected synchronized void onBindingUpdated(final BindingImpl binding, final Map<String, Object> oldArguments)
    {
        final String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getAMQQueue();
        Map<String,Object> args = binding.getArguments();

        assert queue != null;
        assert bindingKey != null;

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Updating binding of queue " + queue.getName() + " with routing key " + bindingKey);
        }

        String routingKey = TopicNormalizer.normalize(bindingKey);

        try
        {

            if (_bindings.containsKey(binding))
            {
                Map<String, Object> oldArgs = _bindings.get(binding);
                _bindings.put(binding, args);
                TopicExchangeResult result = _topicExchangeResults.get(routingKey);

                if (FilterSupport.argumentsContainFilter(args))
                {
                    if (FilterSupport.argumentsContainFilter(oldArgs))
                    {
                        result.replaceQueueFilter(queue,
                                                  FilterSupport.createMessageFilter(oldArgs, queue),
                                                  FilterSupport.createMessageFilter(args, queue));
                    }
                    else
                    {
                        result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                        result.removeUnfilteredQueue(queue);
                    }
                }
                else
                {
                    if (FilterSupport.argumentsContainFilter(oldArgs))
                    {
                        result.addUnfilteredQueue(queue);
                        result.removeFilteredQueue(queue, FilterSupport.createMessageFilter(oldArgs, queue));
                    }
                    else
                    {
                        // TODO - fix control flow
                        return;
                    }
                }

            }
        }
        catch (AMQInvalidArgumentException e)
        {
            throw new ConnectionScopedRuntimeException(e);
        }


    }

    protected synchronized void registerQueue(final BindingImpl binding) throws AMQInvalidArgumentException
    {
        final String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getAMQQueue();
        Map<String,Object> args = binding.getArguments();

        assert queue != null;
        assert bindingKey != null;

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Registering queue " + queue.getName() + " with routing key " + bindingKey);
        }

        String routingKey = TopicNormalizer.normalize(bindingKey);

        if(_bindings.containsKey(binding))
        {
            Map<String,Object> oldArgs = _bindings.get(binding);
            TopicExchangeResult result = _topicExchangeResults.get(routingKey);

            if(FilterSupport.argumentsContainFilter(args))
            {
                if(FilterSupport.argumentsContainFilter(oldArgs))
                {
                    result.replaceQueueFilter(queue,
                                              FilterSupport.createMessageFilter(oldArgs, queue),
                                              FilterSupport.createMessageFilter(args, queue));
                }
                else
                {
                    result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                    result.removeUnfilteredQueue(queue);
                }
            }
            else
            {
                if(FilterSupport.argumentsContainFilter(oldArgs))
                {
                    result.addUnfilteredQueue(queue);
                    result.removeFilteredQueue(queue, FilterSupport.createMessageFilter(oldArgs, queue));
                }
                else
                {
                    // TODO - fix control flow
                    return;
                }
            }

            result.addBinding(binding);

        }
        else
        {

            TopicExchangeResult result = _topicExchangeResults.get(routingKey);
            if(result == null)
            {
                result = new TopicExchangeResult();
                if(FilterSupport.argumentsContainFilter(args))
                {
                    result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                }
                else
                {
                    result.addUnfilteredQueue(queue);
                }
                _parser.addBinding(routingKey, result);
                _topicExchangeResults.put(routingKey,result);
            }
            else
            {
                if(FilterSupport.argumentsContainFilter(args))
                {
                    result.addFilteredQueue(queue, FilterSupport.createMessageFilter(args, queue));
                }
                else
                {
                    result.addUnfilteredQueue(queue);
                }
            }

            result.addBinding(binding);
            _bindings.put(binding, args);
        }

    }

    @Override
    public ArrayList<BaseQueue> doRoute(ServerMessage payload,
                                        final String routingAddress,
                                        final InstanceProperties instanceProperties)
    {

        final String routingKey = routingAddress == null
                                          ? ""
                                          : routingAddress;

        final Collection<AMQQueue> matchedQueues =
                getMatchedQueues(Filterable.Factory.newInstance(payload,instanceProperties), routingKey);

        ArrayList<BaseQueue> queues;

        if(matchedQueues.getClass() == ArrayList.class)
        {
            queues = (ArrayList) matchedQueues;
        }
        else
        {
            queues = new ArrayList<BaseQueue>();
            queues.addAll(matchedQueues);
        }

        if(queues == null || queues.isEmpty())
        {
            _logger.info("Message routing key: " + routingAddress + " No routes.");
        }

        return queues;

    }

    private synchronized boolean deregisterQueue(final BindingImpl binding)
    {
        if(_bindings.containsKey(binding))
        {
            Map<String,Object> bindingArgs = _bindings.remove(binding);

            if (_logger.isDebugEnabled())
            {
                _logger.debug("deregisterQueue " + bindingArgs);
            }

            String bindingKey = TopicNormalizer.normalize(binding.getBindingKey());
            TopicExchangeResult result = _topicExchangeResults.get(bindingKey);

            result.removeBinding(binding);

            if(FilterSupport.argumentsContainFilter(bindingArgs))
            {
                try
                {
                    result.removeFilteredQueue(binding.getAMQQueue(), FilterSupport.createMessageFilter(bindingArgs,
                            binding.getAMQQueue()));
                }
                catch (AMQInvalidArgumentException e)
                {
                    return false;
                }
            }
            else
            {
                result.removeUnfilteredQueue(binding.getAMQQueue());
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    private Collection<AMQQueue> getMatchedQueues(Filterable message, String routingKey)
    {

        Collection<TopicMatcherResult> results = _parser.parse(routingKey);
        switch(results.size())
        {
            case 0:
                return Collections.EMPTY_SET;
            case 1:
                TopicMatcherResult[] resultQueues = new TopicMatcherResult[1];
                results.toArray(resultQueues);
                return ((TopicExchangeResult)resultQueues[0]).processMessage(message, null);
            default:
                Collection<AMQQueue> queues = new HashSet<AMQQueue>();
                for(TopicMatcherResult result : results)
                {
                    TopicExchangeResult res = (TopicExchangeResult)result;

                    for(BindingImpl b : res.getBindings())
                    {
                        b.incrementMatches();
                    }

                    queues = res.processMessage(message, queues);
                }
                return queues;
        }


    }

    protected void onBind(final BindingImpl binding)
    {
        try
        {
            registerQueue(binding);
        }
        catch (AMQInvalidArgumentException e)
        {
            // TODO - this seems incorrect, handling of invalid bindings should be propagated more cleanly
            throw new ConnectionScopedRuntimeException(e);
        }
    }

    protected void onUnbind(final BindingImpl binding)
    {
        deregisterQueue(binding);
    }

}
