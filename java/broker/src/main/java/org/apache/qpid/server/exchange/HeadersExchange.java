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
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.binding.Binding;

import javax.management.JMException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * An exchange that binds queues based on a set of required headers and header values
 * and routes messages to these queues by matching the headers of the message against
 * those with which the queues were bound.
 * <p/>
 * <pre>
 * The Headers Exchange
 *
 *  Routes messages according to the value/presence of fields in the message header table.
 *  (Basic and JMS content has a content header field called "headers" that is a table of
 *   message header fields).
 *
 *  class = "headers"
 *  routing key is not used
 *
 *  Has the following binding arguments:
 *
 *  the X-match field - if "all", does an AND match (used for GRM), if "any", does an OR match.
 *  other fields prefixed with "X-" are ignored (and generate a console warning message).
 *  a field with no value or empty value indicates a match on presence only.
 *  a field with a value indicates match on field presence and specific value.
 *
 *  Standard instances:
 *
 *  amq.match - pub/sub on field content/value
 *  </pre>
 */
public class HeadersExchange extends AbstractExchange
{

    private static final Logger _logger = Logger.getLogger(HeadersExchange.class);
    
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<Binding>> _bindingsByKey =
                            new ConcurrentHashMap<String, CopyOnWriteArraySet<Binding>>();
    
    private final CopyOnWriteArrayList<HeadersBinding> _bindingHeaderMatchers =
                            new CopyOnWriteArrayList<HeadersBinding>();

    
    public static final ExchangeType<HeadersExchange> TYPE = new ExchangeType<HeadersExchange>()
    {

        public AMQShortString getName()
        {
            return ExchangeDefaults.HEADERS_EXCHANGE_CLASS;
        }

        public Class<HeadersExchange> getExchangeClass()
        {
            return HeadersExchange.class;
        }

        public HeadersExchange newInstance(VirtualHost host, AMQShortString name, boolean durable, int ticket,
                boolean autoDelete) throws AMQException
        {
            HeadersExchange exch = new HeadersExchange();

            exch.initialise(host, name, durable, ticket, autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {

            return ExchangeDefaults.HEADERS_EXCHANGE_NAME;
        }
    };

    public HeadersExchange()
    {
        super(TYPE);
    }
    


    public ArrayList<BaseQueue> doRoute(InboundMessage payload)
    {
        AMQMessageHeader header = payload.getMessageHeader();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Exchange " + getNameShortString() + ": routing message with headers " + header);
        }
        
        LinkedHashSet<BaseQueue> queues = new LinkedHashSet<BaseQueue>();
        
        for (HeadersBinding hb : _bindingHeaderMatchers)
        {
            if (hb.matches(header))
            {
                Binding b = hb.getBinding();
                
                b.incrementMatches();
                
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Exchange " + getNameShortString() + ": delivering message with headers " +
                                  header + " to " + b.getQueue().getNameShortString());
                }
                queues.add(b.getQueue());
            }
        }
        
        return new ArrayList<BaseQueue>(queues);
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        //fixme isBound here should take the arguements in to consideration.
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        String bindingKey = (routingKey == null) ? "" : routingKey.toString();
        CopyOnWriteArraySet<Binding> bindings = _bindingsByKey.get(bindingKey);
        
        if(bindings != null)
        {
            for(Binding binding : bindings)
            {
                if(binding.getQueue().equals(queue))
                {
                    return true;
                }
            }
        }
        
        return false;
    }

    public boolean isBound(AMQShortString routingKey)
    {
        String bindingKey = (routingKey == null) ? "" : routingKey.toString();
        CopyOnWriteArraySet<Binding> bindings = _bindingsByKey.get(bindingKey);
        return bindings != null && !bindings.isEmpty();
    }

    public boolean isBound(AMQQueue queue)
    {
        for (CopyOnWriteArraySet<Binding> bindings : _bindingsByKey.values())
        {
            for(Binding binding : bindings)
            {
                if(binding.getQueue().equals(queue))
                {
                    return true;
                }
            }
        }
        
        return false;
    }

    public boolean hasBindings()
    {
        return !getBindings().isEmpty();
    }

    protected AbstractExchangeMBean createMBean() throws JMException
    {
        return new HeadersExchangeMBean(this);
    }

    public Logger getLogger()
    {
        return _logger;
    }

    protected void onBind(final Binding binding)
    {
        String bindingKey = binding.getBindingKey();
        AMQQueue queue = binding.getQueue();
        AMQShortString routingKey = AMQShortString.valueOf(bindingKey);
        Map<String,Object> args = binding.getArguments();

        assert queue != null;
        assert routingKey != null;

        CopyOnWriteArraySet<Binding> bindings = _bindingsByKey.get(bindingKey);

        if(bindings == null)
        {
            bindings = new CopyOnWriteArraySet<Binding>();
            CopyOnWriteArraySet<Binding> newBindings;
            if((newBindings = _bindingsByKey.putIfAbsent(bindingKey, bindings)) != null)
            {
                bindings = newBindings;
            }
        }
        
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Exchange " + getNameShortString() + ": Binding " + queue.getNameShortString() +
                          " with binding key '" +bindingKey + "' and args: " + args);
        }

        _bindingHeaderMatchers.add(new HeadersBinding(binding));
        bindings.add(binding);

    }

    protected void onUnbind(final Binding binding)
    {
        assert binding != null;

        CopyOnWriteArraySet<Binding> bindings = _bindingsByKey.get(binding.getBindingKey());
        if(bindings != null)
        {
            bindings.remove(binding);
        }
        
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Removing Binding: " + _bindingHeaderMatchers.remove(new HeadersBinding(binding)));
        }
    }

}
