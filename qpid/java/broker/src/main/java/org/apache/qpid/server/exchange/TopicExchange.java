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
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.topic.*;
import org.apache.qpid.server.filter.JMSSelectorFilter;
import org.apache.qpid.server.message.InboundMessage;

import javax.management.JMException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.WeakReference;

public class TopicExchange extends AbstractExchange
{

    public static final ExchangeType<TopicExchange> TYPE = new ExchangeType<TopicExchange>()
    {

        public AMQShortString getName()
        {
            return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
        }

        public Class<TopicExchange> getExchangeClass()
        {
            return TopicExchange.class;
        }

        public TopicExchange newInstance(VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            int ticket,
                                            boolean autoDelete) throws AMQException
        {
            TopicExchange exch = new TopicExchange();
            exch.initialise(host, name, durable, ticket, autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return ExchangeDefaults.TOPIC_EXCHANGE_NAME;
        }
    };


    private static final Logger _logger = Logger.getLogger(TopicExchange.class);



    private final TopicParser _parser = new TopicParser();

    private final Map<AMQShortString, TopicExchangeResult> _topicExchangeResults =
            new ConcurrentHashMap<AMQShortString, TopicExchangeResult>();

    private final Map<Binding, FieldTable> _bindings = new HashMap<Binding, FieldTable>();

    private final Map<String, WeakReference<JMSSelectorFilter>> _selectorCache = new WeakHashMap<String, WeakReference<JMSSelectorFilter>>();

    public TopicExchange()
    {
        super(TYPE);
    }

    protected synchronized void registerQueue(final Binding binding) throws AMQInvalidArgumentException
    {
        AMQShortString rKey = new AMQShortString(binding.getBindingKey()) ;
        AMQQueue queue = binding.getQueue();
        FieldTable args = FieldTable.convertToFieldTable(binding.getArguments());
        
        assert queue != null;
        assert rKey != null;

        _logger.debug("Registering queue " + queue.getNameShortString() + " with routing key " + rKey);


        AMQShortString routingKey = TopicNormalizer.normalize(rKey);

        if(_bindings.containsKey(binding))
        {
            FieldTable oldArgs = _bindings.get(binding);
            TopicExchangeResult result = _topicExchangeResults.get(routingKey);

            if(argumentsContainSelector(args))
            {
                if(argumentsContainSelector(oldArgs))
                {
                    result.replaceQueueFilter(queue,createSelectorFilter(oldArgs), createSelectorFilter(args));
                }
                else
                {
                    result.addFilteredQueue(queue,createSelectorFilter(args));
                    result.removeUnfilteredQueue(queue);
                }
            }
            else
            {
                if(argumentsContainSelector(oldArgs))
                {
                    result.addUnfilteredQueue(queue);
                    result.removeFilteredQueue(queue, createSelectorFilter(oldArgs));
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
                if(argumentsContainSelector(args))
                {
                    result.addFilteredQueue(queue, createSelectorFilter(args));
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
                if(argumentsContainSelector(args))
                {
                    result.addFilteredQueue(queue, createSelectorFilter(args));
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

    private JMSSelectorFilter createSelectorFilter(final FieldTable args) throws AMQInvalidArgumentException
    {

        final String selectorString = args.getString(AMQPFilterTypes.JMS_SELECTOR.getValue());
        WeakReference<JMSSelectorFilter> selectorRef = _selectorCache.get(selectorString);
        JMSSelectorFilter selector = null;

        if(selectorRef == null || (selector = selectorRef.get())==null)
        {
            selector = new JMSSelectorFilter(selectorString);
            _selectorCache.put(selectorString, new WeakReference<JMSSelectorFilter>(selector));
        }
        return selector;
    }

    private static boolean argumentsContainSelector(final FieldTable args)
    {
        return args != null && args.containsKey(AMQPFilterTypes.JMS_SELECTOR.getValue()) && args.getString(AMQPFilterTypes.JMS_SELECTOR.getValue()).trim().length() != 0;
    }

    public ArrayList<BaseQueue> doRoute(InboundMessage payload)
    {

        final AMQShortString routingKey = payload.getRoutingKey() == null
                                          ? AMQShortString.EMPTY_STRING
                                          : new AMQShortString(payload.getRoutingKey());

        // The copy here is unfortunate, but not too bad relevant to the amount of
        // things created and copied in getMatchedQueues
        ArrayList<BaseQueue> queues = new ArrayList<BaseQueue>();
        queues.addAll(getMatchedQueues(payload, routingKey));

        if(queues == null || queues.isEmpty())
        {
            _logger.info("Message routing key: " + payload.getRoutingKey() + " No routes.");
        }

        return queues;

    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        Binding binding = new Binding(null, routingKey.toString(), queue, this, FieldTable.convertToMap(arguments));
        
        if (arguments == null)
        {
            return _bindings.containsKey(binding);
        }
        else
        {
            FieldTable o = _bindings.get(binding);
            if (o != null)
            {
                return o.equals(arguments);
            }
            else
            {
                return false;
            }

        }
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return isBound(routingKey, null, queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {
        for(Binding b : _bindings.keySet())
        {
            if(b.getBindingKey().equals(routingKey.toString()))
            {
                return true;
            }
        }

        return false;
    }

    public boolean isBound(AMQQueue queue)
    {
        for(Binding b : _bindings.keySet())
        {
            if(b.getQueue().equals(queue))
            {
                return true;
            }
        }

        return false;
    }

    public boolean hasBindings()
    {
        return !_bindings.isEmpty();
    }

    private boolean deregisterQueue(final Binding binding)
    {
        if(_bindings.containsKey(binding))
        {
            FieldTable bindingArgs = _bindings.remove(binding);
            AMQShortString bindingKey = TopicNormalizer.normalize(new AMQShortString(binding.getBindingKey()));
            TopicExchangeResult result = _topicExchangeResults.get(bindingKey);
            
            result.removeBinding(binding);
            
            if(argumentsContainSelector(bindingArgs))
            {
                try
                {
                    result.removeFilteredQueue(binding.getQueue(), createSelectorFilter(bindingArgs));
                }
                catch (AMQInvalidArgumentException e)
                {
                    return false;
                }
            }
            else
            {
                result.removeUnfilteredQueue(binding.getQueue());
            }
            return true;
        }
        else
        {
            return false;
        }
    }

    protected AbstractExchangeMBean createMBean() throws JMException
    {
        return new TopicExchangeMBean(this);
    }

    public Logger getLogger()
    {
        return _logger;
    }

    private Collection<AMQQueue> getMatchedQueues(InboundMessage message, AMQShortString routingKey)
    {

        Collection<TopicMatcherResult> results = _parser.parse(routingKey);
        if(results.isEmpty())
        {
            return Collections.EMPTY_SET;
        }
        else
        {
            Collection<AMQQueue> queues = results.size() == 1 ? null : new HashSet<AMQQueue>();
            for(TopicMatcherResult result : results)
            {
                TopicExchangeResult res = (TopicExchangeResult)result;

                for(Binding b : res.getBindings())
                {
                    b.incrementMatches();
                }
                
                queues = res.processMessage(message, queues);
            }
            return queues;
        }


    }

    protected void onBind(final Binding binding)
    {
        try
        {
            registerQueue(binding);
        }
        catch (AMQInvalidArgumentException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected void onUnbind(final Binding binding)
    {
        deregisterQueue(binding);
    }

}
