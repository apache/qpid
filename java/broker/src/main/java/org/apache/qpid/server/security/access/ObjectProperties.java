/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security.access;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;

/**
 * An set of properties for an access control v2 rule {@link ObjectType}.
 *
 * The {@link #matches(ObjectProperties)} method is intended to be used when determining precedence of rules, and
 * {@link #equals(Object)} and {@link #hashCode()} are intended for use in maps. This is due to the wildcard matching
 * described above.
 */
public class ObjectProperties
{
    public static final String STAR= "*";

    public static final ObjectProperties EMPTY = new ObjectProperties();

    public enum Property
    {
        ROUTING_KEY,
        NAME,
        QUEUE_NAME,
        OWNER,
        TYPE,
        ALTERNATE,
        IMMEDIATE,
        INTERNAL,
        NO_WAIT,
        NO_LOCAL,
        NO_ACK,
        PASSIVE,
        DURABLE,
        EXCLUSIVE,
        TEMPORARY,
        AUTO_DELETE,
        COMPONENT,
        PACKAGE,
        CLASS,
        FROM_NETWORK,
        FROM_HOSTNAME;

        private static final Map<String, Property> _canonicalNameToPropertyMap = new HashMap<String, ObjectProperties.Property>();

        static
        {
            for (Property property : values())
            {
                _canonicalNameToPropertyMap.put(getCanonicalName(property.name()), property);
            }
        }

        /**
         * Properties are parsed using their canonical name (see {@link #getCanonicalName(String)})
         * so that, for the sake of user-friendliness, the ACL file parses is insensitive to
         * case and underscores.
         */
        public static Property parse(String text)
        {
            String propertyName = getCanonicalName(text);
            Property property = _canonicalNameToPropertyMap.get(propertyName);

            if(property == null)
            {
                throw new IllegalArgumentException("Not a valid property: " + text
                        + " because " + propertyName
                        + " is not in " + _canonicalNameToPropertyMap.keySet());
            }
            else
            {
                return property;
            }
        }

        private static String getCanonicalName(String name)
        {
            return StringUtils.remove(name, '_').toLowerCase();
        }
    }

    private final EnumMap<Property, String> _properties = new EnumMap<Property, String>(Property.class);

    public static List<String> getAllPropertyNames()
    {
        List<String> properties = new ArrayList<String>();
        for (Property property : Property.values())
        {
            properties.add(StringUtils.remove(property.name(), '_').toLowerCase());
        }
        return properties;
    }

    public ObjectProperties()
    {
    }

    public ObjectProperties(Property property, String value)
    {
        _properties.put(property, value);
    }

    public ObjectProperties(ObjectProperties copy)
    {
        _properties.putAll(copy._properties);
    }

    public ObjectProperties(String name)
    {
        setName(name);
    }


    public ObjectProperties(AMQShortString name)
    {
        setName(name);
    }

    public ObjectProperties(AMQQueue queue)
    {
        setName(queue.getName());

        put(Property.AUTO_DELETE, queue.isAutoDelete());
        put(Property.TEMPORARY, queue.isAutoDelete());
        put(Property.DURABLE, queue.isDurable());
        put(Property.EXCLUSIVE, queue.isExclusive());
        if (queue.getAlternateExchange() != null)
        {
	        put(Property.ALTERNATE, queue.getAlternateExchange().getName());
        }
        if (queue.getOwner() != null)
        {
            put(Property.OWNER, queue.getOwner());
        }
        else if (queue.getAuthorizationHolder() != null)
        {
            put(Property.OWNER, queue.getAuthorizationHolder().getAuthorizedPrincipal().getName());
        }
    }

    public ObjectProperties(Exchange exch, AMQQueue queue, AMQShortString routingKey)
    {
        this(queue);

        setName(exch.getName());

		put(Property.QUEUE_NAME, queue.getName());
        put(Property.ROUTING_KEY, routingKey);
    }

    public ObjectProperties(Exchange exch, AMQShortString routingKey)
    {
        this(exch.getName(), routingKey.asString());
    }

    public ObjectProperties(String exchangeName, String routingKey, Boolean immediate)
    {
        this(exchangeName, routingKey);

        put(Property.IMMEDIATE, immediate);
    }

    public ObjectProperties(String exchangeName, String routingKey)
    {
        super();

        setName(exchangeName);

        put(Property.ROUTING_KEY, routingKey);
    }

    public ObjectProperties(Boolean autoDelete, Boolean durable, AMQShortString exchangeName,
            Boolean internal, Boolean nowait, Boolean passive, AMQShortString exchangeType)
    {
        super();

        setName(exchangeName);

        put(Property.AUTO_DELETE, autoDelete);
        put(Property.TEMPORARY, autoDelete);
        put(Property.DURABLE, durable);
        put(Property.INTERNAL, internal);
        put(Property.NO_WAIT, nowait);
        put(Property.PASSIVE, passive);
        put(Property.TYPE, exchangeType);
    }

    public ObjectProperties(Boolean autoDelete, Boolean durable, Boolean exclusive, Boolean nowait, Boolean passive,
            String queueName, String owner)
    {
        super();

        setName(queueName);

        put(Property.AUTO_DELETE, autoDelete);
        put(Property.TEMPORARY, autoDelete);
        put(Property.DURABLE, durable);
        put(Property.EXCLUSIVE, exclusive);
        put(Property.NO_WAIT, nowait);
        put(Property.PASSIVE, passive);
        put(Property.OWNER, owner);
    }

    public ObjectProperties(Boolean exclusive, Boolean noAck, Boolean noLocal, Boolean nowait, AMQQueue queue)
    {
        this(queue);

        put(Property.NO_LOCAL, noLocal);
        put(Property.NO_ACK, noAck);
        put(Property.EXCLUSIVE, exclusive);
        put(Property.NO_WAIT, nowait);
    }

    public Boolean isSet(Property key)
    {
        return _properties.containsKey(key) && Boolean.valueOf(_properties.get(key));
    }

    public String get(Property key)
    {
        return _properties.get(key);
    }

    public String getName()
    {
        return _properties.get(Property.NAME);
    }

    public void setName(String name)
    {
        _properties.put(Property.NAME, name);
    }

    public void setName(AMQShortString name)
    {
        put(Property.NAME, name);
    }

    public String put(Property key, AMQShortString value)
    {
        return put(key, value == null ? "" : value.asString());
    }

    public String put(Property key, String value)
    {
        return _properties.put(key, value == null ? "" : value.trim());
    }

    public void put(Property key, Boolean value)
    {
        if (value != null)
        {
            _properties.put(key, Boolean.toString(value));
        }
    }

    public boolean matches(ObjectProperties properties)
    {
        if (properties._properties.keySet().isEmpty())
        {
            return true;
        }

        if (!_properties.keySet().containsAll(properties._properties.keySet()))
        {
            return false;
        }

        for (Map.Entry<Property,String> entry : properties._properties.entrySet())
        {
            Property key = entry.getKey();
            String ruleValue = entry.getValue();

            String thisValue = _properties.get(key);

            if (!valueMatches(thisValue, ruleValue))
            {
                return false;
            }
        }

        return true;
    }

    private boolean valueMatches(String thisValue, String ruleValue)
    {
        return (StringUtils.isEmpty(ruleValue)
                || StringUtils.equals(thisValue, ruleValue))
                || ruleValue.equals(STAR)
                || (ruleValue.endsWith(STAR)
                        && thisValue != null
                        && thisValue.length() >= ruleValue.length() - 1
                        && thisValue.startsWith(ruleValue.substring(0, ruleValue.length() - 1)));
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }
        if (obj.getClass() != getClass())
        {
            return false;
        }
        ObjectProperties rhs = (ObjectProperties) obj;
        return new EqualsBuilder()
            .append(_properties, rhs._properties).isEquals();
    }

    @Override
    public int hashCode()
    {
        return _properties != null ? _properties.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return _properties.toString();
    }

    public boolean isEmpty()
    {
        return _properties.isEmpty();
    }
}
