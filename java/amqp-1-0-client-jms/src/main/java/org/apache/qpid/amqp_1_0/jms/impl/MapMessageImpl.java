/*
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
 */

package org.apache.qpid.amqp_1_0.jms.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;

import org.apache.qpid.amqp_1_0.jms.MapMessage;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class MapMessageImpl extends MessageImpl implements MapMessage
{
    private Map _map;

    public MapMessageImpl(Header header,
                          DeliveryAnnotations deliveryAnnotations,
                          MessageAnnotations messageAnnotations,
                          Properties properties,
                          ApplicationProperties appProperties,
                          Map map,
                          Footer footer,
                          SessionImpl session)
    {
        super(header, deliveryAnnotations, messageAnnotations, properties, appProperties, footer, session);
        _map = map;
    }

    MapMessageImpl(final SessionImpl session)
    {
        super(new Header(), new DeliveryAnnotations(new HashMap()), new MessageAnnotations(new HashMap()),
              new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        _map = new HashMap();
    }

    public boolean getBoolean(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Boolean)
        {
            return ((Boolean) value).booleanValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Boolean.valueOf((String) value);
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to boolean.");
        }
    }

    public byte getByte(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Byte)
        {
            return ((Byte) value).byteValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Byte.valueOf((String) value).byteValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to byte.");
        }    }

    public short getShort(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Short)
        {
            return ((Short) value).shortValue();
        }
        else if (value instanceof Byte)
        {
            return ((Byte) value).shortValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Short.valueOf((String) value).shortValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to short.");
        }    }

    public char getChar(String name) throws JMSException
    {
        Object value = get(name);

        if (!itemExists(name))
        {
            throw new MessageFormatException("Property " + name + " not present");
        }
        else if (value instanceof Character)
        {
            return ((Character) value).charValue();
        }
        else if (value == null)
        {
            throw new NullPointerException("Property " + name + " has null value and therefore cannot "
                + "be converted to char.");
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to boolan.");
        }    }

    public int getInt(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Integer)
        {
            return ((Integer) value).intValue();
        }
        else if (value instanceof Short)
        {
            return ((Short) value).intValue();
        }
        else if (value instanceof Byte)
        {
            return ((Byte) value).intValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Integer.valueOf((String) value).intValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to int.");
        }
    }

    public long getLong(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Long)
        {
            return ((Long) value).longValue();
        }
        else if (value instanceof Integer)
        {
            return ((Integer) value).longValue();
        }

        if (value instanceof Short)
        {
            return ((Short) value).longValue();
        }

        if (value instanceof Byte)
        {
            return ((Byte) value).longValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Long.valueOf((String) value).longValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to long.");
        }
    }

    public float getFloat(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Float)
        {
            return ((Float) value).floatValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Float.valueOf((String) value).floatValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to float.");
        }
    }

    public double getDouble(String name) throws JMSException
    {
        Object value = get(name);

        if (value instanceof Double)
        {
            return ((Double) value).doubleValue();
        }
        else if (value instanceof Float)
        {
            return ((Float) value).doubleValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            return Double.valueOf((String) value).doubleValue();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to double.");
        }
    }

    public String getString(String name) throws JMSException
    {
        Object value = get(name);

        if ((value instanceof String) || (value == null))
        {
            return (String) value;
        }
        else if (value instanceof byte[] || value instanceof Binary)
        {
            throw new MessageFormatException("Property " + name + " of type byte[] " + "cannot be converted to String.");
        }
        else
        {
            return value.toString();
        }
    }

    public byte[] getBytes(String name) throws JMSException
    {
        Object value = get(name);

        if (!itemExists(name))
        {
            throw new MessageFormatException("Property " + name + " not present");
        }
        else if ((value instanceof byte[]) || (value == null))
        {
            return (byte[]) value;
        }
        else if(value instanceof Binary)
        {
            return ((Binary)value).getArray();
        }
        else
        {
            throw new MessageFormatException("Property " + name + " of type " + value.getClass().getName()
                + " cannot be converted to byte[].");
        }    }

    public Object getObject(String s) throws JMSException
    {
        Object val = get(s);
        return val instanceof Binary ? ((Binary)val).getArray() : val;
    }

    public Enumeration getMapNames() throws JMSException
    {
        return Collections.enumeration(keySet());
    }

    public void setBoolean(String name, boolean val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setByte(String name, byte val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setShort(String name, short val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setChar(String name, char val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setInt(String name, int val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setLong(String name, long val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setFloat(String name, float val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setDouble(String name, double val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setString(String name, String val) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        put(name, val);
    }

    public void setBytes(String name, byte[] val) throws JMSException
    {
        setBytes(name, val, 0, val == null ? 0 : val.length);
    }

    public void setBytes(String name, byte[] bytes, int offset, int length) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        byte[] val;

        if(bytes == null)
        {
            val = null;
        }
        else
        {
            val = new byte[length];
            System.arraycopy(bytes,offset,val,0,length);
        }

        put(name, new Binary(val));
    }

    public void setObject(String name, Object value) throws JMSException
    {
        checkWritable();
        checkPropertyName(name);
        if ((value instanceof Boolean) || (value instanceof Byte) || (value instanceof Short) || (value instanceof Integer)
                || (value instanceof Long) || (value instanceof Character) || (value instanceof Float)
                || (value instanceof Double) || (value instanceof String) || (value instanceof byte[]) || (value == null))
        {
            put(name, value);
        }
        else
        {
            throw new MessageFormatException("Cannot set property " + name + " to value " + value + "of type "
                + value.getClass().getName() + ".");
        }    }

    public boolean itemExists(String s)
    {
        return _map.containsKey(s);
    }

    public Object get(final Object key)
    {
        return _map.get(key);
    }

    public Object put(final Object key, final Object val)
    {
        return _map.put(key, val);
    }

    public boolean itemExists(final Object key)
    {
        return _map.containsKey(key);
    }

    public Set<Object> keySet()
    {
        return _map.keySet();
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _map.clear();
    }

    private void checkPropertyName(String propName)
    {
        if ((propName == null) || propName.equals(""))
        {
            throw new IllegalArgumentException("Property name cannot be null, or the empty String.");
        }
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        if(getDeliveryAnnotations() != null && getDeliveryAnnotations().getValue() != null && !getDeliveryAnnotations().getValue().isEmpty())
        {
            sections.add(getDeliveryAnnotations());
        }
        if(getMessageAnnotations() != null && getMessageAnnotations().getValue() != null && !getMessageAnnotations().getValue().isEmpty())
        {
            sections.add(getMessageAnnotations());
        }
        sections.add(getProperties());
        sections.add(getApplicationProperties());
        sections.add(new AmqpValue(_map));
        sections.add(getFooter());
        return sections;
    }
}
