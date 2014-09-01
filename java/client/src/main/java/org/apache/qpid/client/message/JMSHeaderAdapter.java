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
package org.apache.qpid.client.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;

import org.apache.qpid.AMQPInvalidClassException;
import org.apache.qpid.framing.FieldTable;


public final class JMSHeaderAdapter
{
    private static final Map<String,String> AMQP_TO_JMS_HEADER_NAME_MAPPINGS;
    private static final Map<String,String> JMS_TO_AMQP_HEADER_NAME_MAPPINGS;



    static
    {
        String[][] mappings = {
                { QpidMessageProperties.QPID_SUBJECT, QpidMessageProperties.QPID_SUBJECT_JMS_PROPERTY }
        };

        Map<String,String> amqpToJmsHeaderNameMappings = new HashMap<>();
        Map<String,String> jmsToAmqpHeaderNameMappings = new HashMap<>();

        for(String[] mapping : mappings)
        {
            amqpToJmsHeaderNameMappings.put(mapping[0], mapping[1]);
            jmsToAmqpHeaderNameMappings.put(mapping[1], mapping[0]);
        }

        AMQP_TO_JMS_HEADER_NAME_MAPPINGS = Collections.unmodifiableMap(amqpToJmsHeaderNameMappings);
        JMS_TO_AMQP_HEADER_NAME_MAPPINGS = Collections.unmodifiableMap(jmsToAmqpHeaderNameMappings);
    }

    private static final boolean STRICT_JMS = Boolean.getBoolean("strict-jms");


    private final FieldTable _headers;

    public JMSHeaderAdapter(FieldTable headers)
    {
        _headers = headers;
    }


    private String mapJmsToAmqpName(String name)
    {
        return JMS_TO_AMQP_HEADER_NAME_MAPPINGS.containsKey(name) ? JMS_TO_AMQP_HEADER_NAME_MAPPINGS.get(name) : name;
    }

    public boolean getBoolean(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Boolean b = _headers.getBoolean(amqpName);

        if (b == null)
        {
            if (_headers.containsKey(amqpName))
            {
                Object str = _headers.getObject(amqpName);

                if (!(str instanceof String))
                {
                    throw new MessageFormatException("getBoolean can't use " + name + " item.");
                }
                else
                {
                    return Boolean.valueOf((String) str);
                }
            }
            else
            {
                b = Boolean.valueOf(null);
            }
        }

        return b;
    }


    public byte[] getBytes(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        byte[] bs = _headers.getBytes(amqpName);

        if (bs == null)
        {
            throw new MessageFormatException("getBytes can't use " + name + " item.");
        }
        else
        {
            return bs;
        }
    }

    public byte getByte(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Byte b = _headers.getByte(amqpName);
        if (b == null)
        {
            if (_headers.containsKey(amqpName))
            {
                Object str = _headers.getObject(amqpName);

                if (!(str instanceof String))
                {
                    throw new MessageFormatException("getByte can't use " + name + " item.");
                }
                else
                {
                    return Byte.valueOf((String) str);
                }
            }
            else
            {
                b = Byte.valueOf(null);
            }
        }

        return b;
    }

    public short getShort(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Short s = _headers.getShort(amqpName);

        if (s == null)
        {
            s = Short.valueOf(getByte(amqpName));
        }

        return s;
    }

    public int getInteger(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Integer i = _headers.getInteger(amqpName);

        if (i == null)
        {
            i = Integer.valueOf(getShort(amqpName));
        }

        return i;
    }

    public long getLong(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Long l = _headers.getLong(amqpName);

        if (l == null)
        {
            l = Long.valueOf(getInteger(amqpName));
        }

        return l;
    }

    public float getFloat(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Float f = _headers.getFloat(amqpName);

        if (f == null)
        {
            if (_headers.containsKey(amqpName))
            {
                Object str = _headers.getObject(amqpName);

                if (!(str instanceof String))
                {
                    throw new MessageFormatException("getFloat can't use " + name + " item.");
                }
                else
                {
                    return Float.valueOf((String) str);
                }
            }
            else
            {
                throw new NullPointerException("No such property: " + name);
            }

        }

        return f;
    }

    public double getDouble(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        Double d = _headers.getDouble(amqpName);

        if (d == null)
        {
            d = Double.valueOf(getFloat(amqpName));
        }

        return d;
    }

    public String getString(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        String s = _headers.getString(amqpName);

        if (s == null)
        {
            if (_headers.containsKey(amqpName))
            {
                Object o = _headers.getObject(amqpName);
                if (o instanceof byte[])
                {
                    throw new MessageFormatException("getObject couldn't find " + name + " item.");
                }
                else
                {
                    if (o == null)
                    {
                        return null;
                    }
                    else
                    {
                        s = String.valueOf(o);
                    }
                }
            }
        }

        return s;
    }

    public Object getObject(String name) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        return _headers.getObject(amqpName);
    }

    public void setBoolean(String name, boolean b) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setBoolean(amqpName, b);
    }

    public void setByte(String name, byte b) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setByte(amqpName, b);
    }

    public void setShort(String name, short i) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setShort(amqpName, i);
    }

    public void setInteger(String name, int i) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setInteger(amqpName, i);
    }

    public void setLong(String name, long l) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setLong(amqpName, l);
    }

    public void setFloat(String name, float v) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setFloat(amqpName, v);
    }

    public void setDouble(String name, double v) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setDouble(amqpName, v);
    }

    public void setString(String name, String value) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        _headers.setString(amqpName, value);
    }

    public void setObject(String name, Object object) throws JMSException
    {
        checkPropertyName(name);
        String amqpName = mapJmsToAmqpName(name);
        try
        {
            _headers.setObject(amqpName, object);
        }
        catch (AMQPInvalidClassException aice)
        {
            MessageFormatException mfe = new MessageFormatException(AMQPInvalidClassException.INVALID_OBJECT_MSG + (object == null ? "null" : object.getClass()));
            mfe.setLinkedException(aice);
            mfe.initCause(aice);
            throw mfe;
        }
    }

    public Set<String> getPropertyNames()
    {
        Set<String> names = new LinkedHashSet<>(_headers.keys());
        for(Map.Entry<String,String> entry : AMQP_TO_JMS_HEADER_NAME_MAPPINGS.entrySet())
        {
            if(names.contains(entry.getKey()))
            {
                names.add(entry.getValue());
                if(STRICT_JMS)
                {
                    names.remove(entry.getKey());
                }
            }
        }
        return names;
    }

    public void clear()
    {
        _headers.clear();
    }

    public boolean propertyExists(String name)
    {
        checkPropertyName(name);
        String propertyName = mapJmsToAmqpName(name);
        return _headers.propertyExists(propertyName);
    }

    public Object remove(String name)
    {
        checkPropertyName(name);
        String propertyName = mapJmsToAmqpName(name);
        return _headers.remove(propertyName);
    }

    public boolean isEmpty()
    {
        return _headers.isEmpty();
    }

    protected void checkPropertyName(CharSequence propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if (propertyName.length() == 0)
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentifierFormat(propertyName);
    }

    protected void checkIdentifierFormat(CharSequence propertyName)
    {
//        JMS requirements 3.5.1 Property Names
//        Identifiers:
//        - An identifier is an unlimited-length character sequence that must begin
//          with a Java identifier start character; all following characters must be Java
//          identifier part characters. An identifier start character is any character for
//          which the method Character.isJavaIdentifierStart returns true. This includes
//          '_' and '$'. An identifier part character is any character for which the
//          method Character.isJavaIdentifierPart returns true.
//        - Identifiers cannot be the names NULL, TRUE, or FALSE.
//          Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
//          ESCAPE.
//          Identifiers are either header field references or property references. The
//          type of a property value in a message selector corresponds to the type
//          used to set the property. If a property that does not exist in a message is
//          referenced, its value is NULL. The semantics of evaluating NULL values
//          in a selector are described in Section 3.8.1.2, Null Values.
//          The conversions that apply to the get methods for properties do not
//          apply when a property is used in a message selector expression. For
//          example, suppose you set a property as a string value, as in the
//          following:
//              myMessage.setStringProperty("NumberOfOrders", "2")
//          The following expression in a message selector would evaluate to false,
//          because a string cannot be used in an arithmetic expression:
//          "NumberOfOrders > 1"
//          Identifiers are case sensitive.
//          Message header field references are restricted to JMSDeliveryMode,
//          JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
//          JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
//          null and if so are treated as a NULL value.

        if (STRICT_JMS)
        {
            // JMS start character
            if (!(Character.isJavaIdentifierStart(propertyName.charAt(0))))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid JMS identifier start character");
            }

            // JMS part character
            int length = propertyName.length();
            for (int c = 1; c < length; c++)
            {
                if (!(Character.isJavaIdentifierPart(propertyName.charAt(c))))
                {
                    throw new IllegalArgumentException("Identifier '" + propertyName + "' contains an invalid JMS identifier character");
                }
            }

            // JMS invalid names
            if ((propertyName.equals("NULL")
                 || propertyName.equals("TRUE")
                 || propertyName.equals("FALSE")
                 || propertyName.equals("NOT")
                 || propertyName.equals("AND")
                 || propertyName.equals("OR")
                 || propertyName.equals("BETWEEN")
                 || propertyName.equals("LIKE")
                 || propertyName.equals("IN")
                 || propertyName.equals("IS")
                 || propertyName.equals("ESCAPE")))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' is not allowed in JMS");
            }
        }

    }
}
