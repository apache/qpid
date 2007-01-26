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
import org.apache.qpid.framing.*;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.exchange.ExchangeDefaults;

import javax.management.JMException;
import javax.management.openmbean.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
    @MBeanDescription("Management Bean for Headers Exchange")
    private final class HeadersExchangeMBean extends ExchangeMBean
    {
        // open mbean data types for representing exchange bindings
        private String[]   _bindingItemNames = {"S.No.", "Queue Name", "Header Bindings"};
        private String[]   _bindingItemIndexNames = {_bindingItemNames[0]};
        private OpenType[] _bindingItemTypes = new OpenType[3];
        private CompositeType      _bindingDataType = null;
        private TabularType        _bindinglistDataType = null;
        private TabularDataSupport _bindingList = null;

        @MBeanConstructor("Creates an MBean for AMQ Headers exchange")
        public HeadersExchangeMBean()  throws JMException
        {
            super();
            _exchangeType = "headers";
            init();
        }
        /**
         * initialises the OpenType objects.
         */
        private void init() throws OpenDataException
        {
            _bindingItemTypes[0] = SimpleType.INTEGER;
            _bindingItemTypes[1] = SimpleType.STRING;
            _bindingItemTypes[2] = new ArrayType(1, SimpleType.STRING);
            _bindingDataType = new CompositeType("Exchange Binding", "Queue name and header bindings",
                                                 _bindingItemNames, _bindingItemNames, _bindingItemTypes);
            _bindinglistDataType = new TabularType("Exchange Bindings", "List of exchange bindings for " + getName(),
                                                 _bindingDataType, _bindingItemIndexNames);
        }

        public TabularData bindings() throws OpenDataException
        {
            _bindingList = new TabularDataSupport(_bindinglistDataType);
            int count = 1;
            for (Iterator<Registration> itr = _bindings.iterator(); itr.hasNext();)
            {
                Registration registration = itr.next();
                String queueName = registration.queue.getName();

                HeadersBinding headers = registration.binding;
                FieldTable headerMappings = headers.getMappings();
                final List<String> mappingList = new ArrayList<String>();

                headerMappings.processOverElements(new FieldTable.FieldTableElementProcessor()
                {

                    public boolean processElement(String propertyName, AMQTypedValue value)
                    {
                        mappingList.add(propertyName + "=" + value.getValue());
                        return true;
                    }

                    public Object getResult()
                    {
                        return mappingList;
                    }
                });


                Object[] bindingItemValues = {count++, queueName, mappingList.toArray(new String[0])};
                CompositeData bindingData = new CompositeDataSupport(_bindingDataType, _bindingItemNames, bindingItemValues);
                _bindingList.put(bindingData);
            }

            return _bindingList;
        }

        /**
         * Creates bindings. Binding pattern is as follows-
         * <attributename>=<value>,<attributename>=<value>,...
         * @param queueName
         * @param binding
         * @throws JMException
         */
        public void createNewBinding(String queueName, String binding) throws JMException
        {
            AMQQueue queue = ApplicationRegistry.getInstance().getQueueRegistry().getQueue(queueName);

            if (queue == null)
            {
                throw new JMException("Queue \"" + queueName + "\" is not registered with the exchange.");
            }

            String[] bindings  = binding.split(",");
            FieldTable bindingMap = new FieldTable();
            for (int i = 0; i < bindings.length; i++)
            {
                String[] keyAndValue = bindings[i].split("=");
                if (keyAndValue == null || keyAndValue.length < 2)
                {
                    throw new JMException("Format for headers binding should be \"<attribute1>=<value1>,<attribute2>=<value2>\" ");
                }
                bindingMap.setString(keyAndValue[0], keyAndValue[1]);
            }

            _bindings.add(new Registration(new HeadersBinding(bindingMap), queue));
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
        FieldTable headers = payload.getApplicationHeaders();
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

            String msg = "Exchange " + getName() + ": message not routable.";

            // XXX
            /*            if (payload.getTransferBody().mandatory)
            {
                throw new NoRouteException(msg, payload);
            }
            else*/
            {
                _logger.warn(msg);
            }

        }
    }

    public boolean isBound(String routingKey, AMQQueue queue) throws AMQException
    {
        return isBound(queue);
    }

    public boolean isBound(String routingKey) throws AMQException
    {
        return hasBindings();
    }

    public boolean isBound(AMQQueue queue) throws AMQException
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

    public boolean hasBindings() throws AMQException
    {
        return !_bindings.isEmpty();
    }

    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new HeadersExchangeMBean();
        }
        catch (JMException ex)
        {
            _logger.error("Exception occured in creating the HeadersExchangeMBean", ex);
            throw new AMQException("Exception occured in creating the HeadersExchangeMBean", ex);
        }
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

    public String getType()
    {
        return ExchangeDefaults.HEADERS_EXCHANGE_CLASS;
    }
}
