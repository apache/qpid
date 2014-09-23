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
import org.apache.qpid.client.messaging.address.Link.SubscriptionQueue;
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
    public static final String X_SUBSCRIBES = "x-subscribes";
    public static final String X_SUBSCRIBE = "x-subscribe";
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

    private Address _address;
    private Accessor _addressPropAccess;
    private Accessor _nodePropAccess;
    private Accessor _linkPropAccess;
    private Map _addressPropMap;
    private Map _nodePropMap;
    private Map _linkPropMap;

    public AddressHelper(Address address)
    {
        this._address = address;
        this._addressPropMap = address.getOptions();
        this._addressPropAccess = new MapAccessor(_addressPropMap);
        this._nodePropMap = address.getOptions() == null
                || address.getOptions().get(NODE) == null ? null
                : (Map) address.getOptions().get(NODE);

        if (_nodePropMap != null)
        {
            _nodePropAccess = new MapAccessor(_nodePropMap);
        }

        this._linkPropMap = address.getOptions() == null
                || address.getOptions().get(LINK) == null ? null
                : (Map) address.getOptions().get(LINK);

        if (_linkPropMap != null)
        {
            _linkPropAccess = new MapAccessor(_linkPropMap);
        }
    }

    public String getCreate()
    {
        return _addressPropAccess.getString(CREATE);
    }

    public String getAssert()
    {
        return _addressPropAccess.getString(ASSERT);
    }

    public String getDelete()
    {
        return _addressPropAccess.getString(DELETE);
    }

    public boolean isBrowseOnly()
    {
        String mode = _addressPropAccess.getString(MODE);
        return mode != null && mode.equals(BROWSE) ? true : false;
    }

    @SuppressWarnings("unchecked")
    public List<Binding> getBindings(Map props)
    {
        List<Map> bindingList = (props == null) ? Collections.EMPTY_LIST : (List<Map>) props.get(X_BINDINGS);
        if (bindingList != null && !bindingList.isEmpty())
        {
            List<Binding> bindings = new ArrayList<Binding>(bindingList.size());
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
            return bindings;
        }
        else
        {
            return Collections.emptyList();
        }
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

    public int getNodeType()
    {
        if (_nodePropAccess == null || _nodePropAccess.getString(TYPE) == null)
        {
            // need to query and figure out
            return AMQDestination.UNKNOWN_TYPE;
        }
        else if (_nodePropAccess.getString(TYPE).equals("queue"))
        {
            return AMQDestination.QUEUE_TYPE;
        }
        else if (_nodePropAccess.getString(TYPE).equals("topic"))
        {
            return AMQDestination.TOPIC_TYPE;
        }
        else
        {
            throw new IllegalArgumentException("unknown exchange type");
        }
    }

    public Node getNode()
    {
        Node node = new Node(_address.getName());
        if (_nodePropAccess != null)
        {
            Map xDeclareMap = getDeclareArgs(_nodePropMap);
            MapAccessor xDeclareMapAccessor = new MapAccessor(xDeclareMap);

            node.setDurable(getBooleanProperty(_nodePropAccess,DURABLE,false));
            node.setAutoDelete(getBooleanProperty(xDeclareMapAccessor,AUTO_DELETE,false));
            node.setExclusive(getBooleanProperty(xDeclareMapAccessor,EXCLUSIVE,false));
            node.setAlternateExchange(xDeclareMapAccessor.getString(ALT_EXCHANGE));
            if (xDeclareMapAccessor.getString(TYPE) != null)
            {
                node.setExchangeType(xDeclareMapAccessor.getString(TYPE));
            }
            node.setBindings(getBindings(_nodePropMap));
            if (!xDeclareMap.isEmpty() && xDeclareMap.containsKey(ARGUMENTS))
            {
                node.setDeclareArgs((Map<String,Object>)xDeclareMap.get(ARGUMENTS));
            }
        }
        return node;
    }

    // This should really be in the Accessor interface
    private boolean getBooleanProperty(Accessor access, String propName, boolean defaultValue)
    {
        Boolean result = access.getBoolean(propName);
        return (result == null) ? defaultValue : result.booleanValue();
    }

    public Link getLink()
    {
        Link link = new Link();
        link.setSubscription(new Subscription());
        link.setSubscriptionQueue(new SubscriptionQueue());
        if (_linkPropAccess != null)
        {
            link.setDurable(getBooleanProperty(_linkPropAccess,DURABLE,false));
            link.setName(_linkPropAccess.getString(NAME));

            String reliability = _linkPropAccess.getString(RELIABILITY);
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
                    throw new IllegalArgumentException("The reliability mode '" +
                            reliability + "' is not yet supported");
                }
            }
            
            if (((Map) _address.getOptions().get(LINK)).get(CAPACITY) instanceof Map)
            {
                MapAccessor capacityProps = new MapAccessor((Map) ((Map) _address.getOptions().get(LINK)).get(CAPACITY));

                Integer sourceCapacity = capacityProps.getInt(CAPACITY_SOURCE);
                link.setConsumerCapacity(sourceCapacity == null ? -1 : sourceCapacity);

                Integer targetCapacity = capacityProps.getInt(CAPACITY_TARGET);
                link.setProducerCapacity(targetCapacity == null ? -1 : targetCapacity);
            } 
            else
            {
                int cap = _linkPropAccess.getInt(CAPACITY) == null ? -1 : _linkPropAccess.getInt(CAPACITY);
                link.setConsumerCapacity(cap);
                link.setProducerCapacity(cap);
            }
            link.setFilter(_linkPropAccess.getString(FILTER));
            // so far filter type not used
            
            Map linkMap = (Map) _address.getOptions().get(LINK);

            if (linkMap != null)
            {
                Map x_subscribe = null;

                if(linkMap.containsKey(X_SUBSCRIBE))
                {
                    x_subscribe = (Map)((Map) _address.getOptions().get(LINK)).get(X_SUBSCRIBE);
                }
                else if(linkMap.containsKey(X_SUBSCRIBES))
                {
                    // left in for backwards compatibility with old broken constant
                    x_subscribe = (Map)((Map) _address.getOptions().get(LINK)).get(X_SUBSCRIBES);
                }

                if(x_subscribe != null)
                {
                    if (x_subscribe.containsKey(ARGUMENTS))
                    {
                        link.getSubscription().setArgs((Map<String, Object>) x_subscribe.get(ARGUMENTS));
                    }

                    boolean exclusive = x_subscribe.containsKey(EXCLUSIVE) ?
                            Boolean.parseBoolean((String) x_subscribe.get(EXCLUSIVE)) : false;

                    link.getSubscription().setExclusive(exclusive);
                }
            }

            link.setBindings(getBindings(linkMap));
            Map xDeclareMap = getDeclareArgs(linkMap);
            SubscriptionQueue queue = link.getSubscriptionQueue();
            if (!xDeclareMap.isEmpty() && xDeclareMap.containsKey(ARGUMENTS))
            {
                MapAccessor xDeclareMapAccessor = new MapAccessor(xDeclareMap);
                queue.setAutoDelete(getBooleanProperty(xDeclareMapAccessor,AUTO_DELETE,true));
                queue.setExclusive(getBooleanProperty(xDeclareMapAccessor,EXCLUSIVE,true));
                queue.setAlternateExchange(xDeclareMapAccessor.getString(ALT_EXCHANGE));
                queue.setDeclareArgs((Map<String,Object>)xDeclareMap.get(ARGUMENTS));
            }
        }

        return link;
    }
}
