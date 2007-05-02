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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;

public class DestWildExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(DestWildExchange.class);

    private ConcurrentHashMap<AMQShortString, List<AMQQueue>> _routingKey2queues = new ConcurrentHashMap<AMQShortString, List<AMQQueue>>();

    /** DestWildExchangeMBean class implements the management interface for the Topic exchanges. */
    @MBeanDescription("Management Bean for Topic Exchange")
    private final class DestWildExchangeMBean extends ExchangeMBean
    {
        @MBeanConstructor("Creates an MBean for AMQ topic exchange")
        public DestWildExchangeMBean() throws JMException
        {
            super();
            _exchangeType = "topic";
            init();
        }

        /** returns exchange bindings in tabular form */
        public TabularData bindings() throws OpenDataException
        {
            _bindingList = new TabularDataSupport(_bindinglistDataType);
            for (Map.Entry<AMQShortString, List<AMQQueue>> entry : _routingKey2queues.entrySet())
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
                queue.bind(new AMQShortString(binding), null, DestWildExchange.this);
            }
            catch (AMQException ex)
            {
                throw new MBeanException(ex);
            }
        }

    } // End of MBean class


    public AMQShortString getType()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
    }

    public synchronized void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert routingKey != null;
        _logger.debug("Registering queue " + queue.getName() + " with routing key " + routingKey);
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
        else if (_logger.isDebugEnabled())
        {
            _logger.debug("Queue " + queue + " is already registered with routing key " + routingKey);
        }

    }

    public void route(AMQMessage payload) throws AMQException
    {
        MessagePublishInfo info = payload.getMessagePublishInfo();

        final AMQShortString routingKey = info.getRoutingKey();
        List<AMQQueue> queues = _routingKey2queues.get(routingKey);
        // if we have no registered queues we have nothing to do
        // TODO: add support for the immediate flag
        if (queues == null)
        {
            if (info.isMandatory())
            {
                String msg = "Topic " + routingKey + " is not known to " + this;
                throw new NoRouteException(msg, payload);
            }
            else
            {
                _logger.warn("No queues found for routing key " + routingKey);
                _logger.warn("Routing map contains: " + _routingKey2queues);
                //todo Check for valid topic - mritchie
                return;
            }
        }

        for (AMQQueue q : queues)
        {
            // TODO: modify code generator to add clone() method then clone the deliver body
            // without this addition we have a race condition - we will be modifying the body
            // before the encoder has encoded the body for delivery
            payload.enqueue(q);
        }
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue) throws AMQException
    {
        List<AMQQueue> queues = _routingKey2queues.get(routingKey);
        return queues != null && queues.contains(queue);
    }


    public boolean isBound(AMQShortString routingKey) throws AMQException
    {
        List<AMQQueue> queues = _routingKey2queues.get(routingKey);
        return queues != null && !queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue) throws AMQException
    {
        for (List<AMQQueue> queues : _routingKey2queues.values())
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
        return !_routingKey2queues.isEmpty();
    }

    public synchronized void deregisterQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
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
        if (queues.isEmpty())
        {
            _routingKey2queues.remove(queues);
        }
    }

    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new DestWildExchangeMBean();
        }
        catch (JMException ex)
        {
            _logger.error("Exception occured in creating the topic exchenge mbean", ex);
            throw new AMQException("Exception occured in creating the topic exchenge mbean", ex);
        }
    }
}
