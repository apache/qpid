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
import org.apache.qpid.framing.PropertyFieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.EncodingUtils;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.Enumeration;

public class JMSMapMessage extends JMSBytesMessage implements javax.jms.MapMessage
{
    private static final Logger _logger = Logger.getLogger(JMSMapMessage.class);


    public static final String MIME_TYPE = "jms/map-message";

    private PropertyFieldTable _map;

    JMSMapMessage() throws JMSException
    {
        this(null);
    }

    JMSMapMessage(ByteBuffer data) throws JMSException
    {
        super(data); // this instantiates a content header
        _map = new PropertyFieldTable();
    }


    @Override
	public void clearBodyImpl() throws JMSException {
		super.clearBodyImpl();
		_map = new PropertyFieldTable();
	}

	JMSMapMessage(long messageNbr, ContentHeaderBody contentHeader, ByteBuffer data)
            throws AMQException
    {
        super(messageNbr, contentHeader, data);

        if (data != null)
        {

            long tableSize = EncodingUtils.readInteger(_data);
            _map = (PropertyFieldTable) FieldTableFactory.newFieldTable(_data, tableSize);

        }
        else
        {
            _map = (PropertyFieldTable) FieldTableFactory.newFieldTable();
        }
    }


    public String toBodyString() throws JMSException
    {
        return "MapSize:" + _map.getEncodedSize() + "\nMapData:\n" + _map.toString();
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
            if (_map.containsKey(string))
            {
                Object str = _map.getObject(string);

                if (str == null || !(str instanceof String))
                {
                    throw new MessageFormatException("getBoolean can't use " + string + " item.");
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

    public byte getByte(String string) throws JMSException
    {
        Byte b = _map.getByte(string);
        if (b == null)
        {
            if (_map.containsKey(string))
            {
                Object str = _map.getObject(string);

                if (str == null || !(str instanceof String))
                {
                    throw new MessageFormatException("getByte can't use " + string + " item.");
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

    public short getShort(String string) throws JMSException
    {
        {
            Short s = _map.getShort(string);

            if (s == null)
            {
                s = Short.valueOf(getByte(string));
            }

            return s;
        }
    }

    public char getChar(String string) throws JMSException
    {

        Character result = _map.getCharacter(string);

        if (result == null)
        {
            throw new NullPointerException("getChar couldn't find " + string + " item.");
        }
        else
        {
            return result;
        }
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
            if (_map.containsKey(string))
            {
                Object str = _map.getObject(string);

                if (str == null || !(str instanceof String))
                {
                    throw new MessageFormatException("getFloat can't use " + string + " item.");
                }
                else
                {
                    return Float.valueOf((String) str);
                }
            }
            else
            {
                f = Float.valueOf(null);
            }

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
            if (_map.containsKey(string))
            {
                Object o = _map.getObject(string);
                if (o instanceof byte[])
                {
                    throw new MessageFormatException("getObject couldn't find " + string + " item.");
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

    public byte[] getBytes(String string) throws JMSException
    {

        byte[] result = _map.getBytes(string);

        if (result == null)
        {
            throw new MessageFormatException("getBytes couldn't find " + string + " item.");
        }

        return result;

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
        //What if _data is null?
        _map.writeToBuffer(_data);
        return super.getData();
    }

}
