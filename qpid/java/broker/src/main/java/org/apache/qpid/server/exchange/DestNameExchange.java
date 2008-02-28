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
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
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

    public static final ExchangeType<DestNameExchange> TYPE = new ExchangeType<DestNameExchange>()
    {

        public AMQShortString getName()
        {
            return ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
        }

        public Class<DestNameExchange> getExchangeClass()
        {
            return DestNameExchange.class;
        }

        public DestNameExchange newInstance(VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            boolean autoDelete) throws AMQException
        {
            DestNameExchange exch = new DestNameExchange();
            exch.initialise(host, name, durable, autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        }
    };

    /**
     * MBean class implementing the management interfaces.
     */
    @MBeanDescription("Management Bean for Direct Exchange")
    private final class DestNameExchangeMBean extends ExchangeMBean
    {
        @MBeanConstructor("Creates an MBean for AMQ direct exchange")
        public DestNameExchangeMBean() throws JMException
        {
            super();
            _exchangeType = "direct";
            init();
        }

        public TabularData bindings() throws OpenDataException
        {
            Map<AMQShortString, List<AMQQueue>> bindings = _index.getBindingsMap();
            _bindingList = new TabularDataSupport(_bindinglistDataType);

            for (Map.Entry<AMQShortString, List<AMQQueue>> entry : bindings.entrySet())
            {
                AMQShortString key = entry.getKey();
                List<String> queueList = new ArrayList<String>();

                List<AMQQueue> queues = entry.getValue();
                for (AMQQueue q : queues)
                {
                    queueList.add(q.getName().toString());
                }

                Object[] bindingItemValues = {key.toString(), queueList.toArray(new String[0])};
                CompositeData bindingData = new CompositeDataSupport(_bindingDataType, _bindingItemNames, bindingItemValues);
                _bindingList.put(bindingData);
            }

            return _bindingList;
        }

        public void createNewBinding(String queueName, String binding) throws JMException
        {
            AMQQueue queue = getQueueRegistry().getQueue(new AMQShortString(queueName));
            if (queue == null)
            {
                throw new JMException("Queue \"" + queueName + "\" is not registered with the exchange.");
            }

            try
            {
                queue.bind(new AMQShortString(binding), null, DestNameExchange.this);
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
            throw new AMQException(null, "Exception occured in creating the direct exchange mbean", ex);
        }
    }

    public AMQShortString getType()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_CLASS;
    }

    public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
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

    public void deregisterQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;

        if (!_index.remove(routingKey, queue))
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Queue " + queue + " was not registered with exchange " + this.getName() +
                                   " with routing key " + routingKey + ". No queue was registered with that _routing key",
				   null);
        }
    }

    public void route(AMQMessage payload) throws AMQException
    {
        final MessagePublishInfo info = payload.getMessagePublishInfo();
        final AMQShortString routingKey = info.getRoutingKey() == null ? AMQShortString.EMPTY_STRING : info.getRoutingKey();
        final List<AMQQueue> queues = (routingKey == null) ? null : _index.get(routingKey);
        if (queues == null || queues.isEmpty())
        {
            String msg = "Routing key " + routingKey + " is not known to " + this;
            if (info.isMandatory() || info.isImmediate())
            {
                throw new NoRouteException(msg, payload, null);
            }
            else
            {
                _logger.error("MESSAGE LOSS: Message should be sent on a Dead Letter Queue");
                _logger.warn(msg);
            }
        }
        else
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Publishing message to queue " + queues);
            }

            payload.enqueue(queues);


        }
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        final List<AMQQueue> queues = _index.get(routingKey);
        return queues != null && queues.contains(queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {
        final List<AMQQueue> queues = _index.get(routingKey);
        return queues != null && !queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue)
    {
        Map<AMQShortString, List<AMQQueue>> bindings = _index.getBindingsMap();
        for (List<AMQQueue> queues : bindings.values())
        {
            if (queues.contains(queue))
            {
                return true;
            }
        }
        return false;
    }

    public boolean hasBindings()
    {
        return !_index.getBindingsMap().isEmpty();
    }

    public Map<AMQShortString, List<AMQQueue>> getBindings()
    {
        return _index.getBindingsMap();
    }
}
