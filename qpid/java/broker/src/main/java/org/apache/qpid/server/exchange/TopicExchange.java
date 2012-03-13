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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.JMException;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInvalidArgumentException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.filter.SelectorParsingException;
import org.apache.qpid.filter.selector.ParseException;
import org.apache.qpid.filter.selector.TokenMgrError;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.topic.TopicExchangeResult;
import org.apache.qpid.server.exchange.topic.TopicMatcherResult;
import org.apache.qpid.server.exchange.topic.TopicNormalizer;
import org.apache.qpid.server.exchange.topic.TopicParser;
import org.apache.qpid.server.filter.JMSSelectorFilter;
import org.apache.qpid.server.filter.MessageFilter;
import org.apache.qpid.server.management.AbstractExchangeMBean;
import org.apache.qpid.server.management.TopicExchangeMBean;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.queue.Filterable;
import org.apache.qpid.server.virtualhost.VirtualHost;

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

            if(argumentsContainFilter(args))
            {
                if(argumentsContainFilter(oldArgs))
                {
                    result.replaceQueueFilter(queue,
                                              createMessageFilter(oldArgs, queue),
                                              createMessageFilter(args, queue));
                }
                else
                {
                    result.addFilteredQueue(queue, createMessageFilter(args, queue));
                    result.removeUnfilteredQueue(queue);
                }
            }
            else
            {
                if(argumentsContainFilter(oldArgs))
                {
                    result.addUnfilteredQueue(queue);
                    result.removeFilteredQueue(queue, createMessageFilter(oldArgs, queue));
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
                if(argumentsContainFilter(args))
                {
                    result.addFilteredQueue(queue, createMessageFilter(args, queue));
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
                if(argumentsContainFilter(args))
                {
                    result.addFilteredQueue(queue, createMessageFilter(args, queue));
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

    private MessageFilter createMessageFilter(final FieldTable args, AMQQueue queue) throws AMQInvalidArgumentException
    {
        if(argumentsContainNoLocal(args))
        {
            MessageFilter filter = new NoLocalFilter(queue);

            if(argumentsContainJMSSelector(args))
            {
                filter = new CompoundFilter(filter, createJMSSelectorFilter(args));
            }
            return filter;
        }
        else
        {
            return createJMSSelectorFilter(args);
        }

    }


    private MessageFilter createJMSSelectorFilter(FieldTable args) throws AMQInvalidArgumentException
    {
        final String selectorString = args.getString(AMQPFilterTypes.JMS_SELECTOR.getValue());
        WeakReference<JMSSelectorFilter> selectorRef = _selectorCache.get(selectorString);
        JMSSelectorFilter selector = null;

        if(selectorRef == null || (selector = selectorRef.get())==null)
        {
            try
            {
                selector = new JMSSelectorFilter(selectorString);
            }
            catch (ParseException e)
            {
                throw new AMQInvalidArgumentException("Cannot parse JMS selector \"" + selectorString + "\"", e);
            }
            catch (SelectorParsingException e)
            {
                throw new AMQInvalidArgumentException("Cannot parse JMS selector \"" + selectorString + "\"", e);
            }
            catch (TokenMgrError e)
            {
                throw new AMQInvalidArgumentException("Cannot parse JMS selector \"" + selectorString + "\"", e);
            }
            _selectorCache.put(selectorString, new WeakReference<JMSSelectorFilter>(selector));
        }
        return selector;
    }

    private static boolean argumentsContainFilter(final FieldTable args)
    {
        return argumentsContainNoLocal(args) || argumentsContainJMSSelector(args);
    }

    private static boolean argumentsContainNoLocal(final FieldTable args)
    {
        return args != null
                && args.containsKey(AMQPFilterTypes.NO_LOCAL.getValue())
                && Boolean.TRUE.equals(args.get(AMQPFilterTypes.NO_LOCAL.getValue()));
    }

    private static boolean argumentsContainJMSSelector(final FieldTable args)
    {
        return args != null && (args.containsKey(AMQPFilterTypes.JMS_SELECTOR.getValue())
                       && args.getString(AMQPFilterTypes.JMS_SELECTOR.getValue()).trim().length() != 0);
    }


    public ArrayList<BaseQueue> doRoute(InboundMessage payload)
    {

        final AMQShortString routingKey = payload.getRoutingKeyShortString() == null
                                          ? AMQShortString.EMPTY_STRING
                                          : payload.getRoutingKeyShortString();

        final Collection<AMQQueue> matchedQueues = getMatchedQueues(payload, routingKey);

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

    public boolean isBound(String bindingKey, Map<String, Object> arguments, AMQQueue queue)
    {
        Binding binding = new Binding(null, bindingKey, queue, this, arguments);
        if (arguments == null)
        {
            return _bindings.containsKey(binding);
        }
        else
        {
            FieldTable o = _bindings.get(binding);
            if (o != null)
            {
                return arguments.equals(FieldTable.convertToMap(o));
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
            
            if(argumentsContainFilter(bindingArgs))
            {
                try
                {
                    result.removeFilteredQueue(binding.getQueue(), createMessageFilter(bindingArgs, binding.getQueue()));
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

    private static final class NoLocalFilter implements MessageFilter
    {
        private final AMQQueue _queue;

        public NoLocalFilter(AMQQueue queue)
        {
            _queue = queue;
        }

        public boolean matches(Filterable message)
        {
            InboundMessage inbound = (InboundMessage) message;
            final AMQSessionModel exclusiveOwningSession = _queue.getExclusiveOwningSession();
            return exclusiveOwningSession == null || !exclusiveOwningSession.onSameConnection(inbound);

        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            NoLocalFilter that = (NoLocalFilter) o;

            return _queue == null ? that._queue == null : _queue.equals(that._queue);
        }

        @Override
        public int hashCode()
        {
            return _queue != null ? _queue.hashCode() : 0;
        }
    }

    private static final class CompoundFilter implements MessageFilter
    {
        private MessageFilter _noLocalFilter;
        private MessageFilter _jmsSelectorFilter;

        public CompoundFilter(MessageFilter filter, MessageFilter jmsSelectorFilter)
        {
            _noLocalFilter = filter;
            _jmsSelectorFilter = jmsSelectorFilter;
        }

        public boolean matches(Filterable message)
        {
            return _noLocalFilter.matches(message) && _jmsSelectorFilter.matches(message);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            CompoundFilter that = (CompoundFilter) o;

            if (_jmsSelectorFilter != null ? !_jmsSelectorFilter.equals(that._jmsSelectorFilter) : that._jmsSelectorFilter != null)
            {
                return false;
            }
            if (_noLocalFilter != null ? !_noLocalFilter.equals(that._noLocalFilter) : that._noLocalFilter != null)
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = _noLocalFilter != null ? _noLocalFilter.hashCode() : 0;
            result = 31 * result + (_jmsSelectorFilter != null ? _jmsSelectorFilter.hashCode() : 0);
            return result;
        }
    }
}
