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

import org.apache.qpid.amqp_1_0.jms.StreamMessage;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.*;

public class StreamMessageImpl extends MessageImpl implements StreamMessage
{
    private List _list;
    private boolean _readOnly;
    private int _position = -1;
    private int _offset = -1;



    protected StreamMessageImpl(Header header, Properties properties, ApplicationProperties appProperties, List list,
                                Footer footer, SessionImpl session)
    {
        super(header, properties, appProperties, footer, session);
        _list = list;
    }

    StreamMessageImpl(final SessionImpl session)
    {
        super(new Header(), new Properties(), new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        _list = new ArrayList();
    }

    public StreamMessageImpl(final Header header,
                             final Properties properties,
                             final ApplicationProperties appProperties,
                             final List amqpListSection, final Footer footer)
    {
        super(header, properties, appProperties, footer, null);
        _list = amqpListSection;
    }

    public boolean readBoolean() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Boolean)
        {
            return (Boolean) obj;
        }
        if(obj instanceof String || obj == null)
        {
            return Boolean.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot read " + obj.getClass().getName() + " as boolean");
        }
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _list.clear();
        _position = -1;
        _offset = -1;
    }

    public byte readByte() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Byte)
        {
            return (Byte) obj;
        }
        else if(obj instanceof String || obj == null)
        {
            return Byte.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    public short readShort() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Short)
        {
            return (Short) obj;
        }
        else if(obj instanceof Byte)
        {
            return (Byte) obj;
        }
        else if(obj instanceof String || obj == null)
        {
            return Short.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }

    }

    public char readChar() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Character)
        {
            return (Character) obj;
        }
        if(obj == null)
        {
            throw new NullPointerException();
        }
        else
        {
            throw new MessageFormatException("Cannot read " + obj.getClass().getName() + " as boolean");
        }

    }

    public int readInt() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Integer)
        {
            return (Integer) obj;
        }
        else if(obj instanceof Short)
        {
            return (Short) obj;
        }
        else if(obj instanceof Byte)
        {
            return (Byte) obj;
        }
        else if(obj instanceof String || obj == null)
        {
            return Integer.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    public long readLong() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Long)
        {
            return (Long) obj;
        }
        else if(obj instanceof Integer)
        {
            return (Integer) obj;
        }
        else if(obj instanceof Short)
        {
            return (Short) obj;
        }
        else if(obj instanceof Byte)
        {
            return (Byte) obj;
        }
        else if(obj instanceof String || obj == null)
        {
            return Long.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    public float readFloat() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Float)
        {
            return (Float) obj;
        }
        else if(obj instanceof String || obj == null)
        {
            return Float.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    public double readDouble() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Double)
        {
            return (Double) obj;
        }
        else if(obj instanceof Float)
        {
            return (Float) obj;
        }
        else if(obj instanceof String || obj == null)
        {
            return Double.valueOf((String)obj);
        }
        else
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    public String readString() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof byte[])
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
        return String.valueOf(obj);
    }

    public int readBytes(final byte[] bytes) throws JMSException
    {
        Object obj = readObject();
        if(!(obj instanceof byte[]))
        {
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
        return -1;
    }

    public Object readObject() throws JMSException
    {
        if(_offset == -1)
        {
            return _list.get(++_position);
        }
        else
        {
            return null;  //TODO
        }
    }

    public void writeBoolean(final boolean b) throws JMSException
    {
        _list.add(b);
    }

    public void writeByte(final byte b) throws JMSException
    {
        _list.add(b);
    }

    public void writeShort(final short i) throws JMSException
    {
        _list.add(i);
    }

    public void writeChar(final char c) throws JMSException
    {
        _list.add(c);
    }

    public void writeInt(final int i) throws JMSException
    {
        _list.add(i);
    }

    public void writeLong(final long l) throws JMSException
    {
        _list.add(l);
    }

    public void writeFloat(final float v) throws JMSException
    {
        _list.add(v);
    }

    public void writeDouble(final double v) throws JMSException
    {
        _list.add(v);
    }

    public void writeString(final String s) throws JMSException
    {
        _list.add(s);
    }

    public void writeBytes(final byte[] bytes) throws JMSException
    {
        //TODO
    }

    public void writeBytes(final byte[] bytes, final int i, final int i1) throws JMSException
    {
        //TODO
    }

    public void writeObject(final Object o) throws JMSException
    {
        if(o == null || _supportedClasses.contains(o.getClass()))
        {
            _list.add(o);
        }
    }

    public void reset() throws JMSException
    {
        _position = -1;
        _offset = -1;
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        sections.add(getProperties());
        sections.add(getApplicationProperties());
        sections.add(new AmqpValue(_list));
        sections.add(getFooter());
        return sections;
    }
}
