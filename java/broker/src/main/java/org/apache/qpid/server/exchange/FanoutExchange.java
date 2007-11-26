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

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

public class FanoutExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(FanoutExchange.class);

    /**
     * Maps from queue name to queue instances
     */
    private final CopyOnWriteArraySet<AMQQueue> _queues = new CopyOnWriteArraySet<AMQQueue>();

    /**
     * MBean class implementing the management interfaces.
     */
    @MBeanDescription("Management Bean for Fanout Exchange")
    private final class FanoutExchangeMBean extends ExchangeMBean
    {
        @MBeanConstructor("Creates an MBean for AMQ fanout exchange")
        public FanoutExchangeMBean() throws JMException
        {
            super();
            _exchangeType = "fanout";
            init();
        }

        public TabularData bindings() throws OpenDataException
        {

            _bindingList = new TabularDataSupport(_bindinglistDataType);

            for (AMQQueue queue : _queues)
            {
                String queueName = queue.getName().toString();

                Object[] bindingItemValues = {queueName, new String[]{queueName}};
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
                queue.bind(new AMQShortString(binding), null, FanoutExchange.this);
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex);
            }
        }

    } // End of MBean class

    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new FanoutExchange.FanoutExchangeMBean();
        }
        catch (JMException ex)
        {
            _logger.error("Exception occured in creating the direct exchange mbean", ex);
            throw new AMQException("Exception occured in creating the direct exchange mbean", ex);
        }
    }

    public Map<AMQShortString, List<AMQQueue>> getBindings()
    {
        return null;
    }

    public AMQShortString getType()
    {
        return ExchangeDefaults.FANOUT_EXCHANGE_CLASS;
    }

    public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;

        if (_queues.contains(queue))
        {
            _logger.debug("Queue " + queue + " is already registered");
        }
        else
        {
            _queues.add(queue);
            _logger.debug("Binding queue " + queue + " with routing key " + routingKey + " to exchange " + this);
        }
    }

    public void deregisterQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;

        if (!_queues.remove(queue))
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Queue " + queue + " was not registered with exchange " + this.getName() + ". ");
        }
    }

    public void route(AMQMessage payload) throws AMQException
    {
        final MessagePublishInfo publishInfo = payload.getMessagePublishInfo();
        final AMQShortString routingKey = publishInfo.getRoutingKey();
        if ((_queues == null) || _queues.isEmpty())
        {
            String msg = "No queues bound to " + this;
            if (publishInfo.isMandatory() || publishInfo.isImmediate())
            {
                throw new NoRouteException(msg, payload);
            }
            else
            {
                _logger.warn(msg);
            }
        }
        else
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Publishing message to queue " + _queues);
            }

            for (AMQQueue q : _queues)
            {
                payload.enqueue(q);
            }
        }
    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return _queues.contains(queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {

        return (_queues != null) && !_queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue)
    {

        return _queues.contains(queue);
    }

    public boolean hasBindings()
    {
        return !_queues.isEmpty();
    }
}
