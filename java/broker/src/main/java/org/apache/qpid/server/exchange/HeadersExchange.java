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
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.BaseQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.binding.Binding;

import javax.management.JMException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

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


    private final List<Registration> _bindings = new CopyOnWriteArrayList<Registration>();
    private Map<AMQShortString, Registration> _bindingByKey = new ConcurrentHashMap<AMQShortString, Registration>();


    public HeadersExchange()
    {
        super(TYPE);
    }

    public void registerQueue(String routingKey, AMQQueue queue, Map<String,Object> args)
    {
        registerQueue(new AMQShortString(routingKey), queue, FieldTable.convertToFieldTable(args));
    }

    public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args)
    {
        _logger.debug("Exchange " + getNameShortString() + ": Binding " + queue.getNameShortString() + " with " + args);

        Registration registration = new Registration(new HeadersBinding(args), queue, routingKey);
        _bindings.add(registration);

    }

    public void deregisterQueue(String routingKey, AMQQueue queue, Map<String,Object> args)
    {
        _bindings.remove(new Registration(args == null ? null : new HeadersBinding(FieldTable.convertToFieldTable(args)), queue, new AMQShortString(routingKey)));
    }

    public ArrayList<BaseQueue> doRoute(InboundMessage payload)
    {
        AMQMessageHeader header = payload.getMessageHeader();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Exchange " + getNameShortString() + ": routing message with headers " + header);
        }
        boolean routed = false;
        ArrayList<BaseQueue> queues = new ArrayList<BaseQueue>();
        for (Registration e : _bindings)
        {

            if (e.binding.matches(header))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Exchange " + getNameShortString() + ": delivering message with headers " +
                                  header + " to " + e.queue.getNameShortString());
                }
                queues.add(e.queue);

                routed = true;
            }
        }
        return queues;
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        //fixme isBound here should take the arguements in to consideration.
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return isBound(queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {
        return hasBindings();
    }

    public boolean isBound(AMQQueue queue)
    {
        for (Registration r : _bindings)
        {
            if (r.queue.equals(queue))
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



    protected FieldTable getHeaders(ContentHeaderBody contentHeaderFrame)
    {
        //what if the content type is not 'basic'? 'file' and 'stream' content classes also define headers,
        //but these are not yet implemented.
        return ((BasicContentHeaderProperties) contentHeaderFrame.properties).getHeaders();
    }

    protected AbstractExchangeMBean createMBean() throws JMException
    {
        return new HeadersExchangeMBean(this);
    }

    public Logger getLogger()
    {
        return _logger;
    }


    static class Registration
    {
        private final HeadersBinding binding;
        private final AMQQueue queue;
        private final AMQShortString routingKey;

        Registration(HeadersBinding binding, AMQQueue queue, AMQShortString routingKey)
        {
            this.binding = binding;
            this.queue = queue;
            this.routingKey = routingKey;
        }

        public int hashCode()
        {
            int queueHash = queue.hashCode();
            int routingHash = routingKey == null ? 0 : routingKey.hashCode();
            return queueHash + routingHash;
        }

        public boolean equals(Object o)
        {
            return o instanceof Registration
                   && ((Registration) o).queue.equals(queue)
                   && (routingKey == null ? ((Registration)o).routingKey == null
                                          : routingKey.equals(((Registration)o).routingKey));
        }

        public HeadersBinding getBinding()
        {
            return binding;
        }

        public AMQQueue getQueue()
        {
            return queue;
        }

        public AMQShortString getRoutingKey()
        {
            return routingKey;
        }
    }

    protected void onBind(final Binding binding)
    {
        registerQueue(binding.getBindingKey(), binding.getQueue(), binding.getArguments());
    }

    protected void onUnbind(final Binding binding)
    {
        deregisterQueue(binding.getBindingKey(), binding.getQueue(), binding.getArguments());
    }

}
