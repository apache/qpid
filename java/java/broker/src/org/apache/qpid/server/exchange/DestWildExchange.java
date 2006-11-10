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
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.MBeanConstructor;

import javax.management.openmbean.*;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.NotCompliantMBeanException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DestWildExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(DestWildExchange.class);

    private ConcurrentHashMap<String, List<AMQQueue>> _routingKey2queues = new ConcurrentHashMap<String, List<AMQQueue>>();

    /**
     *  DestWildExchangeMBean class implements the management interface for the
     *  Topic exchanges.
     */
    @MBeanDescription("Management Bean for Topic Exchange")
    private final class DestWildExchangeMBean extends ExchangeMBean
    {
        private String[]   _bindingItemNames = {"BindingKey", "QueueNames"};
        private String[]   _bindingItemDescriptions = {"Binding key", "Queue Names"};
        private String[]   _bindingItemIndexNames = {"BindingKey"};
        private OpenType[] _bindingItemTypes = new OpenType[2];

        private CompositeType      _bindingDataType = null;
        private TabularType        _bindinglistDataType = null;
        private TabularDataSupport _bindingList = null;

        @MBeanConstructor("Creates an MBean for AMQ topic exchange")
        public DestWildExchangeMBean()  throws NotCompliantMBeanException
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
                _bindingItemTypes[1] = new ArrayType(1, SimpleType.STRING);

                _bindingDataType = new CompositeType("QueueBinding",
                                             "Binding key and bound Queue names",
                                             _bindingItemNames,
                                             _bindingItemDescriptions,
                                             _bindingItemTypes);
                _bindinglistDataType = new TabularType("Bindings",
                                             "List of queue bindings for " + getName(),
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

            for (Map.Entry<String, List<AMQQueue>> entry : _routingKey2queues.entrySet())
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
                throw new JMException("Queue \"" + queueName + "\" is not registered with the exchange.");

            try
            {
                registerQueue(binding, queue, null);
                queue.bind(binding, DestWildExchange.this);
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex);
            }
        }

    } // End of MBean class


    public void registerQueue(String routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;
        // we need to use putIfAbsent, which is an atomic operation, to avoid a race condition
        List<AMQQueue> queueList = _routingKey2queues.putIfAbsent(routingKey, new CopyOnWriteArrayList<AMQQueue>());
        // if we got null back, no previous value was associated with the specified routing key hence
        // we need to read back the new value just put into the map
        if (queueList == null)
        {
            queueList = _routingKey2queues.get(routingKey);
        }
        if (!queueList.contains(queue))
        {
            queueList.add(queue);
        }
        else if(_logger.isDebugEnabled())
        {
            _logger.debug("Queue " + queue + " is already registered with routing key " + routingKey);
        }

    }

    public void route(AMQMessage payload) throws AMQException
    {
        BasicPublishBody publishBody = payload.getPublishBody();

        final String routingKey = publishBody.routingKey;
        List<AMQQueue> queues = _routingKey2queues.get(routingKey);
        // if we have no registered queues we have nothing to do
        // TODO: add support for the immediate flag
        if (queues == null)
        {
            //todo Check for valid topic - mritchie
            return;
        }

        for (AMQQueue q : queues)
        {
            // TODO: modify code generator to add clone() method then clone the deliver body
            // without this addition we have a race condition - we will be modifying the body
            // before the encoder has encoded the body for delivery
            q.deliver(payload);
        }
    }

    public void deregisterQueue(String routingKey, AMQQueue queue) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;

        List<AMQQueue> queues = _routingKey2queues.get(routingKey);
        if (queues == null)
        {
            throw new AMQException("Queue " + queue + " was not registered with exchange " + this.getName() +
                                   " with routing key " + routingKey + ". No queue was registered with that routing key");

        }
        boolean removedQ = queues.remove(queue);
        if (!removedQ)
        {
            throw new AMQException("Queue " + queue + " was not registered with exchange " + this.getName() +
                                   " with routing key " + routingKey);
        }
    }

    protected ExchangeMBean createMBean()  throws AMQException
    {
        try
        {
            return new DestWildExchangeMBean();
        }
        catch (NotCompliantMBeanException ex)
        {
            _logger.error("Exception occured in creating the DestWildExchenge", ex);
            throw new AMQException("Exception occured in creating the DestWildExchenge", ex);
        }
    }
}
