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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DestWildExchange extends AbstractExchange
{

    public static final ExchangeType<DestWildExchange> TYPE = new ExchangeType<DestWildExchange>()
    {

        public AMQShortString getName()
        {
            return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
        }

        public Class<DestWildExchange> getExchangeClass()
        {
            return DestWildExchange.class;
        }

        public DestWildExchange newInstance(VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            int ticket,
                                            boolean autoDelete) throws AMQException
        {
            DestWildExchange exch = new DestWildExchange();
            exch.initialise(host, name, durable, ticket, autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return ExchangeDefaults.TOPIC_EXCHANGE_NAME;
        }
    };


    private static final Logger _logger = Logger.getLogger(DestWildExchange.class);

    private ConcurrentHashMap<AMQShortString, List<AMQQueue>> _routingKey2queues =
            new ConcurrentHashMap<AMQShortString, List<AMQQueue>>();
    // private ConcurrentHashMap<AMQShortString, AMQQueue> _routingKey2queue = new ConcurrentHashMap<AMQShortString, AMQQueue>();
    private static final String TOPIC_SEPARATOR = ".";
    private static final String AMQP_STAR = "*";
    private static final String AMQP_HASH = "#";

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

                List<AMQQueue> queues = getMatchedQueues(key);
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

    public synchronized void registerQueue(AMQShortString rKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert rKey != null;

        AMQShortString routingKey = normalize(rKey);

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

    private AMQShortString normalize(AMQShortString routingKey)
    {
        StringTokenizer routingTokens = new StringTokenizer(routingKey.toString(), TOPIC_SEPARATOR);
        List<String> _subscription = new ArrayList<String>();

        while (routingTokens.hasMoreTokens())
        {
            _subscription.add(routingTokens.nextToken());
        }

        int size = _subscription.size();

        for (int index = 0; index < size; index++)
        {
            // if there are more levels
            if ((index + 1) < size)
            {
                if (_subscription.get(index).equals(AMQP_HASH))
                {
                    if (_subscription.get(index + 1).equals(AMQP_HASH))
                    {
                        // we don't need #.# delete this one
                        _subscription.remove(index);
                        size--;
                        // redo this normalisation
                        index--;
                    }

                    if (_subscription.get(index + 1).equals(AMQP_STAR))
                    {
                        // we don't want #.* swap to *.#
                        // remove it and put it in at index + 1
                        _subscription.add(index + 1, _subscription.remove(index));
                    }
                }
            } // if we have more levels
        }

        StringBuilder sb = new StringBuilder();

        for (String s : _subscription)
        {
            sb.append(s);
            sb.append(TOPIC_SEPARATOR);
        }

        sb.deleteCharAt(sb.length() - 1);

        return new AMQShortString(sb.toString());
    }

    public void route(AMQMessage payload) throws AMQException
    {
        MessagePublishInfo info = payload.getMessagePublishInfo();

        final AMQShortString routingKey = normalize(info.getRoutingKey());

        List<AMQQueue> queues = getMatchedQueues(routingKey);
        // if we have no registered queues we have nothing to do
        // TODO: add support for the immediate flag
        if ((queues == null) || queues.isEmpty())
        {
            if (info.isMandatory() || info.isImmediate())
            {
                String msg = "Topic " + routingKey + " is not known to " + this;
                throw new NoRouteException(msg, payload);
            }
            else
            {
                _logger.warn("No queues found for routing key " + routingKey);
                _logger.warn("Routing map contains: " + _routingKey2queues);

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

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        List<AMQQueue> queues = _routingKey2queues.get(normalize(routingKey));

        return (queues != null) && queues.contains(queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {
        List<AMQQueue> queues = _routingKey2queues.get(normalize(routingKey));

        return (queues != null) && !queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue)
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

    public boolean hasBindings()
    {
        return !_routingKey2queues.isEmpty();
    }

    public synchronized void deregisterQueue(AMQShortString rKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert rKey != null;

        AMQShortString routingKey = normalize(rKey);

        List<AMQQueue> queues = _routingKey2queues.get(routingKey);
        if (queues == null)
        {
            throw new AMQException("Queue " + queue + " was not registered with exchange " + this.getName()
                                   + " with routing key " + routingKey + ". No queue was registered with that _routing key");

        }

        boolean removedQ = queues.remove(queue);
        if (!removedQ)
        {
            throw new AMQException("Queue " + queue + " was not registered with exchange " + this.getName()
                                   + " with routing key " + routingKey);
        }

        if (queues.isEmpty())
        {
            _routingKey2queues.remove(routingKey);
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

    public Map<AMQShortString, List<AMQQueue>> getBindings()
    {
        return _routingKey2queues;
    }

    private List<AMQQueue> getMatchedQueues(AMQShortString routingKey)
    {
        List<AMQQueue> list = new LinkedList<AMQQueue>();
        StringTokenizer routingTokens = new StringTokenizer(routingKey.toString(), TOPIC_SEPARATOR);

        ArrayList<String> routingkeyList = new ArrayList<String>();

        while (routingTokens.hasMoreTokens())
        {
            String next = routingTokens.nextToken();
            if (next.equals(AMQP_HASH) && routingkeyList.get(routingkeyList.size() - 1).equals(AMQP_HASH))
            {
                continue;
            }

            routingkeyList.add(next);
        }

        for (AMQShortString queue : _routingKey2queues.keySet())
        {
            StringTokenizer queTok = new StringTokenizer(queue.toString(), TOPIC_SEPARATOR);

            ArrayList<String> queueList = new ArrayList<String>();

            while (queTok.hasMoreTokens())
            {
                queueList.add(queTok.nextToken());
            }

            int depth = 0;
            boolean matching = true;
            boolean done = false;
            int routingskip = 0;
            int queueskip = 0;

            while (matching && !done)
            {
                if ((queueList.size() == (depth + queueskip)) || (routingkeyList.size() == (depth + routingskip)))
                {
                    done = true;

                    // if it was the routing key that ran out of digits
                    if (routingkeyList.size() == (depth + routingskip))
                    {
                        if (queueList.size() > (depth + queueskip))
                        { // a hash and it is the last entry
                            matching =
                                    queueList.get(depth + queueskip).equals(AMQP_HASH)
                                    && (queueList.size() == (depth + queueskip + 1));
                        }
                    }
                    else if (routingkeyList.size() > (depth + routingskip))
                    {
                        // There is still more routing key to check
                        matching = false;
                    }

                    continue;
                }

                // if the values on the two topics don't match
                if (!queueList.get(depth + queueskip).equals(routingkeyList.get(depth + routingskip)))
                {
                    if (queueList.get(depth + queueskip).equals(AMQP_STAR))
                    {
                        depth++;

                        continue;
                    }
                    else if (queueList.get(depth + queueskip).equals(AMQP_HASH))
                    {
                        // Is this a # at the end
                        if (queueList.size() == (depth + queueskip + 1))
                        {
                            done = true;

                            continue;
                        }

                        // otherwise # in the middle
                        while (routingkeyList.size() > (depth + routingskip))
                        {
                            if (routingkeyList.get(depth + routingskip).equals(queueList.get(depth + queueskip + 1)))
                            {
                                queueskip++;
                                depth++;

                                break;
                            }

                            routingskip++;
                        }

                        continue;
                    }

                    matching = false;
                }

                depth++;
            }

            if (matching)
            {
                list.addAll(_routingKey2queues.get(queue));
            }
        }

        return list;
    }
}
