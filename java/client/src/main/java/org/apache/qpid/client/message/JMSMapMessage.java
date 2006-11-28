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
 *
 * 
 */
package org.apache.qpid.client.message;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.PropertyFieldTable;
import org.apache.qpid.AMQException;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.Enumeration;

public class JMSMapMessage extends JMSTextMessage implements javax.jms.MapMessage
{

    public static final String MIME_TYPE = "jms/map-message";

    private PropertyFieldTable _map;

    JMSMapMessage() throws JMSException
    {
        this(null, null);
    }

    JMSMapMessage(ByteBuffer data, String encoding) throws JMSException
    {
        super(data); // this instantiates a content header
        getJmsContentHeaderProperties().setContentType(MIME_TYPE);
        getJmsContentHeaderProperties().setEncoding(encoding);
        _map = new PropertyFieldTable();
    }


    JMSMapMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data)
            throws AMQException
    {
        super(deliveryTag, contentHeader, data);
        contentHeader.setContentType(MIME_TYPE);

        try
        {
            _map = new PropertyFieldTable(getText());
        }
        catch (JMSException e)
        {
            throw new AMQException(e.getMessage(), e);
        }
    }

    // AbstractJMSMessage Interface

    public void clearBodyImpl() throws JMSException
    {
        if (_data != null)
        {
            _data.release();
        }
        _data = null;     
    }

    public String toBodyString() throws JMSException
    {
        return _map.toString();
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    // MapMessage  Interface

    public boolean getBoolean(String string) throws JMSException
    {
        Boolean b = _map.getBoolean(string);

        if (b == null)
        {
            b = Boolean.valueOf(_map.getString(string));
        }

        return b;
    }

    public byte getByte(String string) throws JMSException
    {
        Byte b = _map.getByte(string);
        if (b == null)
        {
            b = Byte.valueOf(_map.getString(string));
        }
        return b;
    }

    public short getShort(String string) throws JMSException
    {
        Short s = _map.getShort(string);

        if (s == null)
        {
            s = Short.valueOf(getByte(string));
        }

        return s;
    }

    public char getChar(String string) throws JMSException
    {
        return _map.getCharacter(string);
    }

    public int getInt(String string) throws JMSException
    {
        Integer i = _map.getInteger(string);

        if (i == null)
        {
            i = Integer.valueOf(getShort(string));
        }

        return i;
    }

    public long getLong(String string) throws JMSException
    {
        Long l = _map.getLong(string);

        if (l == null)
        {
            l = Long.valueOf(getInt(string));
        }

        return l;
    }

    public float getFloat(String string) throws JMSException
    {
        Float f = _map.getFloat(string);

        if (f == null)
        {
            f = Float.valueOf(_map.getString(string));
        }

        return f;
    }

    public double getDouble(String string) throws JMSException
    {
        Double d = _map.getDouble(string);

        if (d == null)
        {
            d = Double.valueOf(getFloat(string));
        }

        return d;
    }

    public String getString(String string) throws JMSException
    {
        String s = _map.getString(string);

        if (s == null)
        {
            Object o = _map.getObject(string);
            s = o.toString();
        }

        return s;
    }

    public byte[] getBytes(String string) throws JMSException
    {
        return _map.getBytes(string);
    }

    public Object getObject(String string) throws JMSException
    {
        return _map.getObject(string);
    }

    public Enumeration getMapNames() throws JMSException
    {
        return _map.getPropertyNames();
    }

    public void setBoolean(String string, boolean b) throws JMSException
    {
        checkWritable();
        _map.setBoolean(string, b);
    }

    public void setByte(String string, byte b) throws JMSException
    {
        checkWritable();
        _map.setByte(string, b);
    }

    public void setShort(String string, short i) throws JMSException
    {
        checkWritable();
        _map.setShort(string, i);
    }

    public void setChar(String string, char c) throws JMSException
    {
        checkWritable();
        _map.setChar(string, c);
    }

    public void setInt(String string, int i) throws JMSException
    {
        checkWritable();
        _map.setInteger(string, i);
    }

    public void setLong(String string, long l) throws JMSException
    {
        checkWritable();
        _map.setLong(string, l);
    }

    public void setFloat(String string, float v) throws JMSException
    {
        checkWritable();
        _map.setFloat(string, v);
    }

    public void setDouble(String string, double v) throws JMSException
    {
        checkWritable();
        _map.setDouble(string, v);
    }

    public void setString(String string, String string1) throws JMSException
    {
        checkWritable();
        _map.setString(string, string1);
    }

    public void setBytes(String string, byte[] bytes) throws JMSException
    {
        this.setBytes(string, bytes, 0, bytes.length);
    }

    public void setBytes(String string, byte[] bytes, int i, int i1) throws JMSException
    {
        checkWritable();
        _map.setBytes(string, bytes, i, i1);
    }

    public void setObject(String string, Object object) throws JMSException
    {
        checkWritable();
        _map.setObject(string, object);
    }

    public boolean itemExists(String string) throws JMSException
    {
        return _map.itemExists(string);
    }

    public ByteBuffer getData()
    {

        try
        {
            setText(toString());
            return super.getData();
        }
        catch (JMSException e)
        {
            // should never occur according to setText
            //fixme -- what to do if it does occur.
        }

        return ByteBuffer.allocate(0);
    }

}
