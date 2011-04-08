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
package org.apache.qpid.client.messaging.address;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQDestination.Binding;
import org.apache.qpid.client.messaging.address.Link.Reliability;
import org.apache.qpid.client.messaging.address.Link.Subscription;
import org.apache.qpid.client.messaging.address.Node.ExchangeNode;
import org.apache.qpid.client.messaging.address.Node.QueueNode;
import org.apache.qpid.client.messaging.address.Node.UnknownNodeType;
import org.apache.qpid.configuration.Accessor;
import org.apache.qpid.configuration.Accessor.MapAccessor;
import org.apache.qpid.messaging.Address;

/**
 * Utility class for extracting information from the address class
 */
public class AddressHelper
{
    public static final String NODE = "node";
    public static final String LINK = "link";
    public static final String X_DECLARE = "x-declare";
    public static final String X_BINDINGS = "x-bindings";
    public static final String X_SUBSCRIBE = "x-subscribes";
    public static final String CREATE = "create";
    public static final String ASSERT = "assert";
    public static final String DELETE = "delete";
    public static final String FILTER = "filter";
    public static final String NO_LOCAL = "no-local";
    public static final String DURABLE = "durable";
    public static final String EXCLUSIVE = "exclusive";
    public static final String AUTO_DELETE = "auto-delete";
    public static final String TYPE = "type";
    public static final String ALT_EXCHANGE = "alternate-exchange";
    public static final String BINDINGS = "bindings";
    public static final String BROWSE = "browse";
    public static final String MODE = "mode";
    public static final String CAPACITY = "capacity";
    public static final String CAPACITY_SOURCE = "source";
    public static final String CAPACITY_TARGET = "target";
    public static final String NAME = "name";
    public static final String EXCHANGE = "exchange";
    public static final String QUEUE = "queue";
    public static final String KEY = "key";
    public static final String ARGUMENTS = "arguments";
    public static final String RELIABILITY = "reliability";

    private Address address;
    private Accessor addressProps;
    private Accessor nodeProps;
    private Accessor linkProps;

    public AddressHelper(Address address)
    {
        this.address = address;
        addressProps = new MapAccessor(address.getOptions());
        Map node_props = address.getOptions() == null
                || address.getOptions().get(NODE) == null ? null
                : (Map) address.getOptions().get(NODE);

        if (node_props != null)
        {
            nodeProps = new MapAccessor(node_props);
        }

        Map link_props = address.getOptions() == null
                || address.getOptions().get(LINK) == null ? null
                : (Map) address.getOptions().get(LINK);

        if (link_props != null)
        {
            linkProps = new MapAccessor(link_props);
        }
    }

    public String getCreate()
    {
        return addressProps.getString(CREATE);
    }

    public String getAssert()
    {
        return addressProps.getString(ASSERT);
    }

    public String getDelete()
    {
        return addressProps.getString(DELETE);
    }

    public boolean isNoLocal()
    {
        Boolean b = nodeProps.getBoolean(NO_LOCAL);
        return b == null ? false : b;
    }

    public boolean isBrowseOnly()
    {
        String mode = addressProps.getString(MODE);
        return mode != null && mode.equals(BROWSE) ? true : false;
    }

    @SuppressWarnings("unchecked")
    public List<Binding> getBindings(Map props)
    {
        List<Binding> bindings = new ArrayList<Binding>();
        List<Map> bindingList = (List<Map>) props.get(X_BINDINGS);
        if (bindingList != null)
        {
            for (Map bindingMap : bindingList)
            {
                Binding binding = new Binding(
                        (String) bindingMap.get(EXCHANGE),
                        (String) bindingMap.get(QUEUE),
                        (String) bindingMap.get(KEY),
                        bindingMap.get(ARGUMENTS) == null ? Collections.EMPTY_MAP
                                : (Map<String, Object>) bindingMap
                                        .get(ARGUMENTS));
                bindings.add(binding);
            }
        }
        return bindings;
    }

    public Map getDeclareArgs(Map props)
    {
        if (props != null && props.get(X_DECLARE) != null)
        {
            return (Map) props.get(X_DECLARE);
            
        } else
        {
            return Collections.EMPTY_MAP;
        }
    }

    public int getTargetNodeType() throws Exception
    {
        if (nodeProps == null || nodeProps.getString(TYPE) == null)
        {
            // need to query and figure out
            return AMQDestination.UNKNOWN_TYPE;
        } else if (nodeProps.getString(TYPE).equals("queue"))
        {
            return AMQDestination.QUEUE_TYPE;
        } else if (nodeProps.getString(TYPE).equals("topic"))
        {
            return AMQDestination.TOPIC_TYPE;
        } else
        {
            throw new Exception("unkown exchange type");
        }
    }

    public Node getTargetNode(int addressType)
    {
        // target node here is the default exchange
        if (nodeProps == null || addressType == AMQDestination.QUEUE_TYPE)
        {
            return new ExchangeNode();
        } else if (addressType == AMQDestination.TOPIC_TYPE)
        {
            Map node = (Map) address.getOptions().get(NODE);
            return createExchangeNode(node);
        } else
        {
            // don't know yet
            return null;
        }
    }

    private Node createExchangeNode(Map parent)
    {
        Map declareArgs = getDeclareArgs(parent);
        MapAccessor argsMap = new MapAccessor(declareArgs);
        ExchangeNode node = new ExchangeNode();
        node.setExchangeType(argsMap.getString(TYPE) == null ? null : argsMap
                .getString(TYPE));
        fillInCommonNodeArgs(node, parent, argsMap);
        return node;
    }

    private Node createQueueNode(Map parent)
    {
        Map declareArgs = getDeclareArgs(parent);
        MapAccessor argsMap = new MapAccessor(declareArgs);
        QueueNode node = new QueueNode();
        node.setAlternateExchange(argsMap.getString(ALT_EXCHANGE));
        node.setExclusive(argsMap.getBoolean(EXCLUSIVE) == null ? false
                : argsMap.getBoolean(EXCLUSIVE));
        fillInCommonNodeArgs(node, parent, argsMap);

        return node;
    }

    private void fillInCommonNodeArgs(Node node, Map parent, MapAccessor argsMap)
    {
        node.setDurable(getDurability(parent));
        node.setAutoDelete(argsMap.getBoolean(AUTO_DELETE) == null ? false
                : argsMap.getBoolean(AUTO_DELETE));
        node.setAlternateExchange(argsMap.getString(ALT_EXCHANGE));
        node.setBindings(getBindings(parent));
        if (getDeclareArgs(parent).containsKey(ARGUMENTS))
        {
            node.setDeclareArgs((Map<String,Object>)getDeclareArgs(parent).get(ARGUMENTS));
        }
    }
    
    private boolean getDurability(Map map)
    {
        Accessor access = new MapAccessor(map);
        Boolean result = access.getBoolean(DURABLE);
        return (result == null) ? false : result.booleanValue();
    }

    /**
     * if the type == queue x-declare args from the node props is used. if the
     * type == exchange x-declare args from the link props is used else just
     * create a default temp queue.
     */
    public Node getSourceNode(int addressType)
    {
        if (addressType == AMQDestination.QUEUE_TYPE && nodeProps != null)
        {
            return createQueueNode((Map) address.getOptions().get(NODE));
        }
        if (addressType == AMQDestination.TOPIC_TYPE && linkProps != null)
        {
            return createQueueNode((Map) address.getOptions().get(LINK));
        } else
        {
            // need to query the info
            return new QueueNode();
        }
    }

    public Link getLink() throws Exception
    {
        Link link = new Link();
        link.setSubscription(new Subscription());
        if (linkProps != null)
        {
            link.setDurable(linkProps.getBoolean(DURABLE) == null ? false
                    : linkProps.getBoolean(DURABLE));
            link.setName(linkProps.getString(NAME));

            String reliability = linkProps.getString(RELIABILITY);
            if ( reliability != null)
            {
                if (reliability.equalsIgnoreCase("unreliable"))
                {
                    link.setReliability(Reliability.UNRELIABLE);
                }
                else if (reliability.equalsIgnoreCase("at-least-once"))
                {
                    link.setReliability(Reliability.AT_LEAST_ONCE);
                }
                else
                {
                    throw new Exception("The reliability mode '" + 
                            reliability + "' is not yet supported");
                }
                
            }
            
            if (((Map) address.getOptions().get(LINK)).get(CAPACITY) instanceof Map)
            {
                MapAccessor capacityProps = new MapAccessor(
                        (Map) ((Map) address.getOptions().get(LINK))
                                .get(CAPACITY));
                link
                        .setConsumerCapacity(capacityProps
                                .getInt(CAPACITY_SOURCE) == null ? 0
                                : capacityProps.getInt(CAPACITY_SOURCE));
                link
                        .setProducerCapacity(capacityProps
                                .getInt(CAPACITY_TARGET) == null ? 0
                                : capacityProps.getInt(CAPACITY_TARGET));
            } 
            else
            {
                int cap = linkProps.getInt(CAPACITY) == null ? 0 : linkProps
                        .getInt(CAPACITY);
                link.setConsumerCapacity(cap);
                link.setProducerCapacity(cap);
            }
            link.setFilter(linkProps.getString(FILTER));
            // so far filter type not used
            
            if (((Map) address.getOptions().get(LINK)).containsKey(X_SUBSCRIBE))
            {   
                Map x_subscribe = (Map)((Map) address.getOptions().get(LINK)).get(X_SUBSCRIBE);
                
                if (x_subscribe.containsKey(ARGUMENTS))
                {
                    link.getSubscription().setArgs((Map<String,Object>)x_subscribe.get(ARGUMENTS));
                }
                
                boolean exclusive = x_subscribe.containsKey(EXCLUSIVE) ?
                                    Boolean.parseBoolean((String)x_subscribe.get(EXCLUSIVE)): false;
                
                link.getSubscription().setExclusive(exclusive);
            }
        }

        return link;
    }
}
