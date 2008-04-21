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
import org.apache.qpid.framing.AMQShortStringTokenizer;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TopicExchange extends AbstractExchange
{

    public static final ExchangeType<TopicExchange> TYPE = new ExchangeType<TopicExchange>()
    {

        public AMQShortString getName()
        {
            return ExchangeDefaults.TOPIC_EXCHANGE_CLASS;
        }

        public Class<TopicExchange> getExchangeClass()
        {
            return TopicExchange.class;
        }

        public TopicExchange newInstance(VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            int ticket,
                                            boolean autoDelete) throws AMQException
        {
            TopicExchange exch = new TopicExchange();
            exch.initialise(host, name, durable, ticket, autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return ExchangeDefaults.TOPIC_EXCHANGE_NAME;
        }
    };


    private static final Logger _logger = Logger.getLogger(TopicExchange.class);

    private final ConcurrentHashMap<AMQShortString, List<AMQQueue>> _bindingKey2queues =
            new ConcurrentHashMap<AMQShortString, List<AMQQueue>>();
    private final ConcurrentHashMap<AMQShortString, List<AMQQueue>> _simpleBindingKey2queues =
            new ConcurrentHashMap<AMQShortString, List<AMQQueue>>();
    private final ConcurrentHashMap<AMQShortString, List<AMQQueue>> _wildCardBindingKey2queues =
            new ConcurrentHashMap<AMQShortString, List<AMQQueue>>();
    // private ConcurrentHashMap<AMQShortString, AMQQueue> _routingKey2queue = new ConcurrentHashMap<AMQShortString, AMQQueue>();
    private static final byte TOPIC_SEPARATOR = (byte)'.';
    private static final AMQShortString TOPIC_SEPARATOR_AS_SHORTSTRING = new AMQShortString(".");
    private static final AMQShortString AMQP_STAR_TOKEN = new AMQShortString("*");
    private static final AMQShortString AMQP_HASH_TOKEN = new AMQShortString("#");
    private ConcurrentHashMap<AMQShortString, AMQShortString[]> _bindingKey2Tokenized =
            new ConcurrentHashMap<AMQShortString, AMQShortString[]>();
    private static final byte HASH_BYTE = (byte)'#';
    private static final byte STAR_BYTE = (byte)'*';

    /** TopicExchangeMBean class implements the management interface for the Topic exchanges. */
    @MBeanDescription("Management Bean for Topic Exchange")
    private final class TopicExchangeMBean extends ExchangeMBean
    {
        @MBeanConstructor("Creates an MBean for AMQ topic exchange")
        public TopicExchangeMBean() throws JMException
        {
            super();
            _exchangeType = "topic";
            init();
        }

        /** returns exchange bindings in tabular form */
        public TabularData bindings() throws OpenDataException
        {
            _bindingList = new TabularDataSupport(_bindinglistDataType);
            for (Map.Entry<AMQShortString, List<AMQQueue>> entry : _bindingKey2queues.entrySet())
            {
                AMQShortString key = entry.getKey();
                List<String> queueList = new ArrayList<String>();

                List<AMQQueue> queues = getMatchedQueues(key);
                for (AMQQueue q : queues)
                {
                    queueList.add(q.getName().toString());
                }

                Object[] bindingItemValues = {key.toString(), queueList.toArray(new String[queueList.size()])};
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
                queue.bind(TopicExchange.this, new AMQShortString(binding), null);
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

        _logger.debug("Registering queue " + queue.getName() + " with routing key " + rKey);

        // we need to use putIfAbsent, which is an atomic operation, to avoid a race condition
        List<AMQQueue> queueList = _bindingKey2queues.putIfAbsent(rKey, new CopyOnWriteArrayList<AMQQueue>());







        // if we got null back, no previous value was associated with the specified routing key hence
        // we need to read back the new value just put into the map
        if (queueList == null)
        {
            queueList = _bindingKey2queues.get(rKey);
        }



        if (!queueList.contains(queue))
        {
            queueList.add(queue);


            if(rKey.contains(HASH_BYTE) || rKey.contains(STAR_BYTE))
            {
                AMQShortString routingKey = normalize(rKey);
                List<AMQQueue> queueList2 = _wildCardBindingKey2queues.putIfAbsent(routingKey, new CopyOnWriteArrayList<AMQQueue>());

                if(queueList2 == null)
                {
                    queueList2 = _wildCardBindingKey2queues.get(routingKey);
                    AMQShortStringTokenizer keyTok = routingKey.tokenize(TOPIC_SEPARATOR);

                    ArrayList<AMQShortString> keyTokList = new ArrayList<AMQShortString>(keyTok.countTokens());

                    while (keyTok.hasMoreTokens())
                    {
                        keyTokList.add(keyTok.nextToken());
                    }

                    _bindingKey2Tokenized.put(routingKey, keyTokList.toArray(new AMQShortString[keyTokList.size()]));
                }
                queueList2.add(queue);

            }
            else
            {
                List<AMQQueue> queueList2 = _simpleBindingKey2queues.putIfAbsent(rKey, new CopyOnWriteArrayList<AMQQueue>());
                if(queueList2 == null)
                {
                    queueList2 = _simpleBindingKey2queues.get(rKey);
                }
                queueList2.add(queue);

            }




        }
        else if (_logger.isDebugEnabled())
        {
            _logger.debug("Queue " + queue + " is already registered with routing key " + rKey);
        }



    }

    private AMQShortString normalize(AMQShortString routingKey)
    {
        if(routingKey == null)
        {
            routingKey = AMQShortString.EMPTY_STRING;
        }
        
        AMQShortStringTokenizer routingTokens = routingKey.tokenize(TOPIC_SEPARATOR);

        List<AMQShortString> subscriptionList = new ArrayList<AMQShortString>();

        while (routingTokens.hasMoreTokens())
        {
            subscriptionList.add(routingTokens.nextToken());
        }

        int size = subscriptionList.size();

        for (int index = 0; index < size; index++)
        {
            // if there are more levels
            if ((index + 1) < size)
            {
                if (subscriptionList.get(index).equals(AMQP_HASH_TOKEN))
                {
                    if (subscriptionList.get(index + 1).equals(AMQP_HASH_TOKEN))
                    {
                        // we don't need #.# delete this one
                        subscriptionList.remove(index);
                        size--;
                        // redo this normalisation
                        index--;
                    }

                    if (subscriptionList.get(index + 1).equals(AMQP_STAR_TOKEN))
                    {
                        // we don't want #.* swap to *.#
                        // remove it and put it in at index + 1
                        subscriptionList.add(index + 1, subscriptionList.remove(index));
                    }
                }
            } // if we have more levels
        }



        AMQShortString normalizedString = AMQShortString.join(subscriptionList, TOPIC_SEPARATOR_AS_SHORTSTRING);
/*
        StringBuilder sb = new StringBuilder();
        for (AMQShortString s : subscriptionList)
        {
            sb.append(s);
            sb.append(TOPIC_SEPARATOR);
        }

        sb.deleteCharAt(sb.length() - 1);
*/

        return normalizedString;
    }

    public void route(IncomingMessage payload) throws AMQException
    {

        final AMQShortString routingKey = payload.getRoutingKey();

        List<AMQQueue> queues = getMatchedQueues(routingKey);

        if(queues == null || queues.isEmpty())
        {
            _logger.info("Message routing key: " + payload.getRoutingKey() + " No routes - " + _bindingKey2queues);
        }

        payload.enqueue(queues);

    }

    public boolean isBound(AMQShortString routingKey, FieldTable arguments, AMQQueue queue)
    {
        return isBound(routingKey, queue);
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        List<AMQQueue> queues = _bindingKey2queues.get(normalize(routingKey));

        return (queues != null) && queues.contains(queue);
    }

    public boolean isBound(AMQShortString routingKey)
    {
        List<AMQQueue> queues = _bindingKey2queues.get(normalize(routingKey));

        return (queues != null) && !queues.isEmpty();
    }

    public boolean isBound(AMQQueue queue)
    {
        for (List<AMQQueue> queues : _bindingKey2queues.values())
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
        return !_bindingKey2queues.isEmpty();
    }

    public synchronized void deregisterQueue(AMQShortString rKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        assert queue != null;
        assert rKey != null;

        List<AMQQueue> queues = _bindingKey2queues.get(rKey);
        if (queues == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Queue " + queue + " was not registered with exchange " + this.getName()
                                   + " with routing key " + rKey + ". No queue was registered with that _routing key");

        }

        boolean removedQ = queues.remove(queue);
        if (!removedQ)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Queue " + queue + " was not registered with exchange " + this.getName()
                                   + " with routing key " + rKey);
        }


        if(rKey.contains(HASH_BYTE) || rKey.contains(STAR_BYTE))
        {
            AMQShortString bindingKey = normalize(rKey);
            List<AMQQueue> queues2 = _wildCardBindingKey2queues.get(bindingKey);
            queues2.remove(queue);
            if(queues2.isEmpty())
            {
                _wildCardBindingKey2queues.remove(bindingKey);
                _bindingKey2Tokenized.remove(bindingKey);
            }

        }
        else
        {
            List<AMQQueue> queues2 = _simpleBindingKey2queues.get(rKey);
            queues2.remove(queue);
            if(queues2.isEmpty())
            {
                _simpleBindingKey2queues.remove(rKey);
            }

        }




        if (queues.isEmpty())
        {
            _bindingKey2queues.remove(rKey);
        }
    }

    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new TopicExchangeMBean();
        }
        catch (JMException ex)
        {
            _logger.error("Exception occured in creating the topic exchenge mbean", ex);
            throw new AMQException("Exception occured in creating the topic exchenge mbean", ex);
        }
    }

    public Map<AMQShortString, List<AMQQueue>> getBindings()
    {
        return _bindingKey2queues;
    }

    private List<AMQQueue> getMatchedQueues(AMQShortString routingKey)
    {

        List<AMQQueue> list = null;

        if(!_wildCardBindingKey2queues.isEmpty())
        {


            AMQShortStringTokenizer routingTokens = routingKey.tokenize(TOPIC_SEPARATOR);

            final int routingTokensCount = routingTokens.countTokens();


            AMQShortString[] routingkeyTokens = new AMQShortString[routingTokensCount];

            if(routingTokensCount == 1)
            {
                routingkeyTokens[0] =routingKey;
            }
            else
            {


                int token = 0;
                while (routingTokens.hasMoreTokens())
                {

                    AMQShortString next = routingTokens.nextToken();
        /*            if (next.equals(AMQP_HASH) && routingkeyTokens.get(routingkeyTokens.size() - 1).equals(AMQP_HASH))
                    {
                        continue;
                    }
        */

                    routingkeyTokens[token++] = next;
                }
            }

            _logger.info("Routing key tokens: " + Arrays.asList(routingkeyTokens));

            for (AMQShortString bindingKey : _wildCardBindingKey2queues.keySet())
            {

                AMQShortString[] bindingKeyTokens = _bindingKey2Tokenized.get(bindingKey);


                boolean matching = true;
                boolean done = false;

                int depthPlusRoutingSkip = 0;
                int depthPlusQueueSkip = 0;

                final int bindingKeyTokensCount = bindingKeyTokens.length;

                while (matching && !done)
                {

                    if ((bindingKeyTokensCount == depthPlusQueueSkip) || (routingTokensCount == depthPlusRoutingSkip))
                    {
                        done = true;

                        // if it was the routing key that ran out of digits
                        if (routingTokensCount == depthPlusRoutingSkip)
                        {
                            if (bindingKeyTokensCount > depthPlusQueueSkip)
                            { // a hash and it is the last entry
                                matching =
                                        bindingKeyTokens[depthPlusQueueSkip].equals(AMQP_HASH_TOKEN)
                                        && (bindingKeyTokensCount == (depthPlusQueueSkip + 1));
                            }
                        }
                        else if (routingTokensCount > depthPlusRoutingSkip)
                        {
                            // There is still more routing key to check
                            matching = false;
                        }

                        continue;
                    }

                    // if the values on the two topics don't match
                    if (!bindingKeyTokens[depthPlusQueueSkip].equals(routingkeyTokens[depthPlusRoutingSkip]))
                    {
                        if (bindingKeyTokens[depthPlusQueueSkip].equals(AMQP_STAR_TOKEN))
                        {
                            depthPlusQueueSkip++;
                            depthPlusRoutingSkip++;

                            continue;
                        }
                        else if (bindingKeyTokens[depthPlusQueueSkip].equals(AMQP_HASH_TOKEN))
                        {
                            // Is this a # at the end
                            if (bindingKeyTokensCount == (depthPlusQueueSkip + 1))
                            {
                                done = true;

                                continue;
                            }

                            // otherwise # in the middle
                            while (routingTokensCount > depthPlusRoutingSkip)
                            {
                                if (routingkeyTokens[depthPlusRoutingSkip].equals(bindingKeyTokens[depthPlusQueueSkip + 1]))
                                {
                                    depthPlusQueueSkip += 2;
                                    depthPlusRoutingSkip++;

                                    break;
                                }

                                depthPlusRoutingSkip++;
                            }

                            continue;
                        }

                        matching = false;
                    }

                    depthPlusQueueSkip++;
                    depthPlusRoutingSkip++;
                }

                if (matching)
                {
                    if(list == null)
                    {
                        list = new ArrayList<AMQQueue>(_wildCardBindingKey2queues.get(bindingKey));
                    }
                    else
                    {
                        list.addAll(_wildCardBindingKey2queues.get(bindingKey));
                    }
                }
            }

        }
        if(!_simpleBindingKey2queues.isEmpty())
        {
            List<AMQQueue> queues = _simpleBindingKey2queues.get(routingKey);
            if(list == null)
            {
                if(queues == null)
                {
                    list =  Collections.EMPTY_LIST;
                }
                else
                {
                    list = new ArrayList<AMQQueue>(queues);
                }
            }
            else if(queues != null)
            {
                list.addAll(queues);
            }

        }

        return list;

    }
}
