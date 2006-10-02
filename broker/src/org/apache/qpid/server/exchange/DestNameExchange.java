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
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.NotCompliantMBeanException;
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
        private String[]   _bindingItemNames = {"BindingKey", "QueueNames"};
        private String[]   _bindingItemDescriptions = {"Binding key", "Queue Names"};
        private String[]   _bindingItemIndexNames = {"BindingKey"};
        private OpenType[] _bindingItemTypes = new OpenType[2];

        private CompositeType      _bindingDataType = null;
        private TabularType        _bindinglistDataType = null;
        private TabularDataSupport _bindingList = null;

        @MBeanConstructor("Creates an MBean for AMQ direct exchange")
        public DestNameExchangeMBean()  throws NotCompliantMBeanException
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
                //_bindingItemTypes[1] = ArrayType.getArrayType(SimpleType.STRING);
                _bindingItemTypes[1] = new ArrayType(1, SimpleType.STRING);

                _bindingDataType = new CompositeType("QueueBinding",
                                             "Queue and binding keye",
                                             _bindingItemNames,
                                             _bindingItemDescriptions,
                                             _bindingItemTypes);
                _bindinglistDataType = new TabularType("Bindings",
                                             "List of queues and binding keys",
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
                CompositeData bindingData = new CompositeDataSupport(_bindingDataType,
                                                                     _bindingItemNames,
                                                                     bindingItemValues);
                _bindingList.put(bindingData);
            }

            return _bindingList;
        }

        public void createBinding(String queueName, String binding)
                throws JMException
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
        catch (NotCompliantMBeanException ex)
        {
            _logger.error("Exception occured in creating the DestNameExchenge", ex);
            throw new AMQException("Exception occured in creating the DestNameExchenge", ex);
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
            _logger.debug("Binding queue " + queue + " with routing key " + routingKey
                          + " to exchange " + this);
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
        BasicPublishBody publishBody = payload.getPublishBody();

        final String routingKey = publishBody.routingKey;
        final List<AMQQueue> queues = _index.get(routingKey);
        if (queues == null || queues.isEmpty())
        {
            String msg = "Routing key " + routingKey + " is not known to " + this;
            if (publishBody.mandatory)
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
                _logger.debug("Publishing message to queue " + queues);
            }

            for (AMQQueue q : queues)
            {
                q.deliver(payload);
            }
        }
    }
}
