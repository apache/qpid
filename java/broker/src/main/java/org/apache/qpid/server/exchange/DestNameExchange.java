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
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.exchange.ExchangeDefaults;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.openmbean.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DestNameExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(DestNameExchange.class);

    /**
     * Maps from queue name to queue instances
     */
    private final Index _index = new Index();

    /**
     * MBean class implementing the management interfaces.
     */
    @MBeanDescription("Management Bean for Direct Exchange")
    private final class DestNameExchangeMBean extends ExchangeMBean
    {
        // open mbean data types for representing exchange bindings
        private String[]   _bindingItemNames = {"Routing Key", "Queue Names"};
        private String[]   _bindingItemIndexNames = {_bindingItemNames[0]};
        private OpenType[] _bindingItemTypes = new OpenType[2];
        private CompositeType      _bindingDataType = null;
        private TabularType        _bindinglistDataType = null;
        private TabularDataSupport _bindingList = null;

        @MBeanConstructor("Creates an MBean for AMQ direct exchange")
        public DestNameExchangeMBean()  throws JMException
        {
            super();
            _exchangeType = "direct";
            init();
        }

        /**
         * initialises the OpenType objects.
         */
        private void init() throws OpenDataException
        {
            _bindingItemTypes[0] = SimpleType.STRING;
            _bindingItemTypes[1] = new ArrayType(1, SimpleType.STRING);
            _bindingDataType = new CompositeType("Exchange Binding", "Routing key and Queue names",
                                                 _bindingItemNames, _bindingItemNames, _bindingItemTypes);
            _bindinglistDataType = new TabularType("Exchange Bindings", "Exchange Bindings for " + getName(),
                                                 _bindingDataType, _bindingItemIndexNames);
        }

        public TabularData bindings() throws OpenDataException
        {
            Map<String, List<AMQQueue>> bindings = _index.getBindingsMap();
            _bindingList = new TabularDataSupport(_bindinglistDataType);

            for (Map.Entry<String, List<AMQQueue>> entry : bindings.entrySet())
            {
                String key = entry.getKey();
                List<String> queueList = new ArrayList<String>();

                List<AMQQueue> queues = entry.getValue();
                for (AMQQueue q : queues)
                {
                    queueList.add(q.getName());
                }

                Object[] bindingItemValues = {key, queueList.toArray(new String[0])};
                CompositeData bindingData = new CompositeDataSupport(_bindingDataType, _bindingItemNames, bindingItemValues);
                _bindingList.put(bindingData);
            }

            return _bindingList;
        }

        public void createNewBinding(String queueName, String binding) throws JMException
        {
            AMQQueue queue = ApplicationRegistry.getInstance().getQueueRegistry().getQueue(queueName);
            if (queue == null)
            {
                throw new JMException("Queue \"" + queueName + "\" is not registered with the exchange.");
            }

            try
            {
                registerQueue(binding, queue, null);
                queue.bind(binding, DestNameExchange.this);
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex);
            }
        }

    }// End of MBean class


    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new DestNameExchangeMBean();
        }
        catch (JMException ex)
        {
            _logger.error("Exception occured in creating the direct exchange mbean", ex);
            throw new AMQException("Exception occured in creating the direct exchange mbean", ex);
        }
    }

    public void registerQueue(String routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;
        if (!_index.add(routingKey, queue))
        {
            _logger.debug("Queue " + queue + " is already registered with routing key " + routingKey);
        }
        else
        {
            _logger.debug("Binding queue " + queue + " with routing key " + routingKey + " to exchange " + this);
        }
    }

    public void deregisterQueue(String routingKey, AMQQueue queue) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;

        if (!_index.remove(routingKey, queue))
        {
            throw new AMQException("Queue " + queue + " was not registered with exchange " + this.getName() +
                                   " with routing key " + routingKey + ". No queue was registered with that routing key");
        }
    }

    public void route(AMQMessage payload) throws AMQException
    {
        MessageTransferBody transferBody = payload.getTransferBody();

        final String routingKey = transferBody.routingKey;
        final List<AMQQueue> queues = (routingKey == null) ? null : _index.get(routingKey);
        if (queues == null || queues.isEmpty())
        {
            String msg = "Routing key " + routingKey + " is not known to " + this;
            // XXX
            /*if (transferBody.mandatory)
            {
                throw new NoRouteException(msg, payload);
            }
            else*/
            {
                _logger.warn(msg);
            }
        }
        else
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Publishing message to queue " + queues);
            }

            for (AMQQueue q : queues)
            {
                q.deliver(payload);
            }
        }
    }

    public boolean isBound(String routingKey, AMQQueue queue) throws AMQException
    {
        final List<AMQQueue> queues = _index.get(routingKey);
        return queues != null && queues.contains(queue);
    }

    public boolean isBound(String routingKey) throws AMQException
    {
        final List<AMQQueue> queues = _index.get(routingKey);
        return queues != null && !queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue) throws AMQException
    {
        Map<String, List<AMQQueue>> bindings = _index.getBindingsMap();
        for (List<AMQQueue> queues : bindings.values())
        {
            if (queues.contains(queue))
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasBindings() throws AMQException
    {
        return !_index.getBindingsMap().isEmpty();
    }

    public String getType()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
    }
}
