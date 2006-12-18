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
import org.apache.qpid.framing.JMSPropertyFieldTable;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.util.Enumeration;

public class JMSMapMessage extends JMSBytesMessage implements javax.jms.MapMessage
{
    private static final Logger _logger = Logger.getLogger(JMSMapMessage.class);


    public static final String MIME_TYPE = "jms/map-message";

    private JMSPropertyFieldTable _properties;

    JMSMapMessage() throws JMSException
    {
        this(null);
    }

    JMSMapMessage(ByteBuffer data) throws JMSException
    {
        super(data); // this instantiates a content header
        _properties = new JMSPropertyFieldTable();
    }

    JMSMapMessage(long messageNbr, ContentHeaderBody contentHeader, ByteBuffer data)
            throws AMQException
    {
        super(messageNbr, contentHeader, data);

        if (data != null)
        {

            long tableSize = EncodingUtils.readInteger(_data);
            try
            {
                _properties = new JMSPropertyFieldTable(_data, tableSize);
            }
            catch (JMSException e)
            {
                Exception error = e.getLinkedException();
                if (error instanceof AMQFrameDecodingException)
                {
                    throw(AMQFrameDecodingException) error;
                }
                else
                {
                    throw new AMQException(e.getMessage(), e);
                }
            }
        }
        else
        {
            _properties = new JMSPropertyFieldTable();
        }
    }


    public String toBodyString() throws JMSException
    {
        return _properties.toString();
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }


    public ByteBuffer getData()
    {
        //What if _data is null?
        _properties.writeToBuffer(_data);
        return super.getData();
    }

    @Override
    public void clearBodyImpl() throws JMSException
    {
        super.clearBodyImpl();
        _properties.clear();
    }

    public boolean getBoolean(String string) throws JMSException
    {
        return _properties.getBoolean(string);
    }

    public byte getByte(String string) throws JMSException
    {
        return _properties.getByte(string);
    }

    public short getShort(String string) throws JMSException
    {
        return _properties.getShort(string);
    }

    public char getChar(String string) throws JMSException
    {
    	Character result = _properties.getCharacter(string);

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
        return _properties.getInteger(string);
    }

    public long getLong(String string) throws JMSException
    {
        return _properties.getLong(string);
    }

    public float getFloat(String string) throws JMSException
    {
        return _properties.getFloat(string);
    }

    public double getDouble(String string) throws JMSException
    {
        return _properties.getDouble(string);
    }

    public String getString(String string) throws JMSException
    {
        return _properties.getString(string);
    }

    public byte[] getBytes(String string) throws JMSException
    {
        return _properties.getBytes(string);
    }

    public Object getObject(String string) throws JMSException
    {
        return _properties.getObject(string);
    }

    public Enumeration getMapNames() throws JMSException
    {
        return _properties.getMapNames();
    }


    public void setBoolean(String string, boolean b) throws JMSException
    {
        checkWritable();
        _properties.setBoolean(string, b);
    }

    public void setByte(String string, byte b) throws JMSException
    {
        checkWritable();
        _properties.setByte(string, b);
    }

    public void setShort(String string, short i) throws JMSException
    {
        checkWritable();
        _properties.setShort(string, i);
    }

    public void setChar(String string, char c) throws JMSException
    {
        checkWritable();
        _properties.setChar(string, c);
    }

    public void setInt(String string, int i) throws JMSException
    {
        checkWritable();
        _properties.setInteger(string, i);
    }

    public void setLong(String string, long l) throws JMSException
    {
        checkWritable();
        _properties.setLong(string, l);
    }

    public void setFloat(String string, float v) throws JMSException
    {
        checkWritable();
        _properties.setFloat(string, v);
    }

    public void setDouble(String string, double v) throws JMSException
    {
        checkWritable();
        _properties.setDouble(string, v);
    }

    public void setString(String string, String string1) throws JMSException
    {
        checkWritable();
        _properties.setString(string, string1);
    }

    public void setBytes(String string, byte[] bytes) throws JMSException
    {
        this.setBytes(string, bytes, 0, bytes.length);
    }

    public void setBytes(String string, byte[] bytes, int i, int i1) throws JMSException
    {
        checkWritable();
        _properties.setBytes(string, bytes, i, i1);
    }

    public void setObject(String string, Object object) throws JMSException
    {
        checkWritable();
        _properties.setObject(string, object);
    }

    public boolean itemExists(String string) throws JMSException
    {
        return _properties.itemExists(string);
    }

}
