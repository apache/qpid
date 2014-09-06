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
import java.util.HashMap;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;

import org.apache.qpid.amqp_1_0.jms.StreamMessage;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class StreamMessageImpl extends MessageImpl implements StreamMessage
{
    private List _list;
    private boolean _readOnly;
    private int _position = -1;
    private int _offset = -1;



    protected StreamMessageImpl(Header header,
                                DeliveryAnnotations deliveryAnnotations,
                                MessageAnnotations messageAnnotations,
                                Properties properties,
                                ApplicationProperties appProperties,
                                List list,
                                Footer footer,
                                SessionImpl session)
    {
        super(header, deliveryAnnotations, messageAnnotations, properties, appProperties, footer, session);
        _list = list;
    }

    StreamMessageImpl(final SessionImpl session)
    {
        super(new Header(), new DeliveryAnnotations(new HashMap()), new MessageAnnotations(new HashMap()), new Properties(),
              new ApplicationProperties(new HashMap()), new Footer(Collections.EMPTY_MAP),
              session);
        _list = new ArrayList();
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
            try
            {
                return Byte.valueOf((String)obj);
            }
            catch(RuntimeException e)
            {
                backup();
                throw e;
            }
        }
        else
        {
            backup();
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    private void backup()
    {
        _position--;
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
            try
            {
                return Short.valueOf((String)obj);
            }
            catch(RuntimeException e)
            {
                backup();
                throw e;
            }
        }
        else
        {
            backup();
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
            backup();
            throw new NullPointerException();
        }
        else
        {
            backup();
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
            try
            {
                return Integer.valueOf((String)obj);
            }
            catch (RuntimeException e)
            {
                backup();
                throw e;
            }
        }
        else
        {
            backup();
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
            try
            {
                return Long.valueOf((String)obj);
            }
            catch (RuntimeException e)
            {
                backup();
                throw e;
            }
        }
        else
        {
            backup();
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
            try
            {
                return Float.valueOf((String)obj);
            }
            catch (RuntimeException e)
            {
                backup();
                throw e;
            }
        }
        else
        {
            backup();
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
            try
            {
                return Double.valueOf((String)obj);
            }
            catch (RuntimeException e)
            {
                backup();
                throw e;
            }
        }
        else
        {
            backup();
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
    }

    public String readString() throws JMSException
    {
        Object obj = readObject();
        if(obj instanceof Binary)
        {
            backup();
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
        return String.valueOf(obj);
    }

    public int readBytes(final byte[] bytes) throws JMSException
    {
        Object obj = readObject();
        if(!(obj instanceof Binary))
        {
            backup();
            if(_position > -1 && _list.get(_position) instanceof Binary)
            {
                return -1;
            }
            throw new MessageFormatException("Cannot convert value of type " + obj.getClass().getName());
        }
        Binary binary = (Binary) obj;
        if(bytes.length >= binary.getLength())
        {
            System.arraycopy(binary.getArray(),binary.getArrayOffset(),bytes,0,binary.getLength());
            return binary.getLength();
        }
        return -1;
    }

    public Object readObject() throws JMSException
    {
        checkReadable();
        if(_offset == -1)
        {
            try
            {
                return _list.get(++_position);
            }
            catch (IndexOutOfBoundsException e)
            {
                throw new MessageEOFException("No more data in message stream");
            }
        }
        else
        {
            return null;  //TODO
        }
    }

    public void writeBoolean(final boolean b) throws JMSException
    {
        checkWritable();
        _list.add(b);
    }

    public void writeByte(final byte b) throws JMSException
    {
        checkWritable();
        _list.add(b);
    }

    public void writeShort(final short i) throws JMSException
    {
        checkWritable();
        _list.add(i);
    }

    public void writeChar(final char c) throws JMSException
    {
        checkWritable();
        _list.add(c);
    }

    public void writeInt(final int i) throws JMSException
    {
        checkWritable();
        _list.add(i);
    }

    public void writeLong(final long l) throws JMSException
    {
        checkWritable();
        _list.add(l);
    }

    public void writeFloat(final float v) throws JMSException
    {
        checkWritable();
        _list.add(v);
    }

    public void writeDouble(final double v) throws JMSException
    {
        checkWritable();
        _list.add(v);
    }

    public void writeString(final String s) throws JMSException
    {
        checkWritable();
        _list.add(s);
    }

    public void writeBytes(final byte[] bytes) throws JMSException
    {
        checkWritable();
        writeBytes(bytes, 0, bytes.length);
    }

    public void writeBytes(final byte[] bytes, final int offset, final int size) throws JMSException
    {
        checkWritable();
        
        if(!_list.isEmpty() && _list.get(_list.size()-1) instanceof byte[])
        {
            Binary oldVal = (Binary) _list.get(_list.size()-1);
            byte[] allBytes = new byte[oldVal.getLength() + size];
            System.arraycopy(oldVal.getArray(),oldVal.getArrayOffset(),allBytes,0,oldVal.getLength());
            System.arraycopy(bytes, offset, allBytes, oldVal.getLength(), size);
            _list.set(_list.size()-1, allBytes);
        }
        else
        {
            byte[] dup = new byte[size];
            System.arraycopy(bytes,offset,dup,0,size);
            _list.add(new Binary(dup));
        }
    }

    public void writeObject(final Object o) throws JMSException
    {
        checkWritable();
        if(o == null || _supportedClasses.contains(o.getClass()))
        {
            _list.add(o);
        }
    }

    public void reset() throws JMSException
    {
        super.reset();
        _position = -1;
        _offset = -1;
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
        sections.add(new AmqpValue(_list));
        sections.add(getFooter());
        return sections;
    }
}
