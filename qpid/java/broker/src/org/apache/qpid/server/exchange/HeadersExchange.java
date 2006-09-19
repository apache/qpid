/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.exchange;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQMessage;

import javax.management.openmbean.*;
import javax.management.ServiceNotFoundException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    private final List<Registration> _bindings = new CopyOnWriteArrayList<Registration>();

    /**
     * HeadersExchangeMBean class implements the management interface for the
     * Header Exchanges.
     */
    private final class HeadersExchangeMBean extends ExchangeMBean
    {
        private String[]   _bindingItemNames = {"Queue", "HeaderBinding"};
        private String[]   _bindingItemDescriptions = {"Queue Name", "Header attribute bindings"};
        private String[]   _bindingItemIndexNames = {"Queue"};
        private OpenType[] _bindingItemTypes = new OpenType[2];

        private CompositeType      _bindingDataType = null;
        private TabularType        _bindinglistDataType = null;
        private TabularDataSupport _bindingList = null;

        public HeadersExchangeMBean()
        {
            super();
            init();
        }
        /**
         * initialises the OpenType objects.
         */
        private void init()
        {
            try
            {
                _bindingItemTypes[0] = SimpleType.STRING;
                _bindingItemTypes[1] = new ArrayType(1, SimpleType.STRING);;

                _bindingDataType = new CompositeType("QueueAndHeaderAttributesBinding",
                                             "Queue and header attributes binding",
                                             _bindingItemNames,
                                             _bindingItemDescriptions,
                                             _bindingItemTypes);
                _bindinglistDataType = new TabularType("HeaderBindings",
                                             "List of queues and related header attribute bindings",
                                             _bindingDataType,
                                             _bindingItemIndexNames);
            }
            catch(OpenDataException ex)
            {
                //It should never occur.
                _logger.error("OpenDataTypes could not be created.", ex);
                throw new RuntimeException(ex);
            }
        }

        public TabularData viewBindings()
            throws OpenDataException
        {
            _bindingList = new TabularDataSupport(_bindinglistDataType);
            for (Iterator<Registration> itr = _bindings.iterator(); itr.hasNext();)
            {
                Registration registration = itr.next();
                String queueName = registration.queue.getName();

                HeadersBinding headers = registration.binding;
                Map<Object, Object> headerMappings = headers.getMappings();
                List<String> mappingList = new ArrayList<String>();

                for (Map.Entry<Object, Object> en : headerMappings.entrySet())
                {
                    String key = en.getKey().toString();
                    String value = en.getValue().toString();

                    mappingList.add(key + "=" + value);
                }

                Object[] bindingItemValues = {queueName, mappingList.toArray(new String[0])};
                CompositeData bindingData = new CompositeDataSupport(_bindingDataType,
                                                                     _bindingItemNames,
                                                                     bindingItemValues);
                _bindingList.put(bindingData);
            }

            return _bindingList;
        }

        public void createBinding(String QueueName, String binding)
            throws ServiceNotFoundException
        {
            throw new ServiceNotFoundException("This service is not supported by \"" + this.getName() + "\"");
        }

    } // End of MBean class

    public void registerQueue(String routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        _logger.debug("Exchange " + getName() + ": Binding " + queue.getName() + " with " + args);
        _bindings.add(new Registration(new HeadersBinding(args), queue));
    }

    public void deregisterQueue(String routingKey, AMQQueue queue) throws AMQException
    {
        _logger.debug("Exchange " + getName() + ": Unbinding " + queue.getName());
        _bindings.remove(new Registration(null, queue));
    }

    public void route(AMQMessage payload) throws AMQException
    {
        Map headers = getHeaders(payload.getContentHeaderBody());
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Exchange " + getName() + ": routing message with headers " + headers);
        }
        boolean delivered = false;
        for (Registration e : _bindings)
        {
            if (e.binding.matches(headers))
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Exchange " + getName() + ": delivering message with headers " +
                                  headers + " to " + e.queue.getName());
                }
                e.queue.deliver(payload);
                delivered = true;
            }
        }
        if (!delivered)
        {
            _logger.warn("Exchange " + getName() + ": message not routable.");
        }
    }

    protected Map getHeaders(ContentHeaderBody contentHeaderFrame)
    {
        //what if the content type is not 'basic'? 'file' and 'stream' content classes also define headers,
        //but these are not yet implemented.
        return ((BasicContentHeaderProperties) contentHeaderFrame.properties).getHeaders();
    }

    protected ExchangeMBean createMBean()
    {
        return new HeadersExchangeMBean();
    }

    private static class Registration
    {
        private final HeadersBinding binding;
        private final AMQQueue queue;

        Registration(HeadersBinding binding, AMQQueue queue)
        {
            this.binding = binding;
            this.queue = queue;
        }

        public int hashCode()
        {
            return queue.hashCode();
        }

        public boolean equals(Object o)
        {
            return o instanceof Registration && ((Registration) o).queue.equals(queue);
        }
    }
}
