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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;

import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.MessageEOFException;
import java.lang.NumberFormatException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AMQPEncodedListMessage extends JMSStreamMessage implements
        org.apache.qpid.jms.ListMessage, javax.jms.MapMessage
{
    private static final Logger _logger = LoggerFactory
            .getLogger(AMQPEncodedListMessage.class);

    public static final String MIME_TYPE = "amqp/list";

    private List<Object> _list = new ArrayList<Object>();

    public AMQPEncodedListMessage(AMQMessageDelegateFactory delegateFactory) throws JMSException
    {
        super(delegateFactory);
        currentIndex = 0;
    }

    AMQPEncodedListMessage(AMQMessageDelegate delegate, ByteBuffer data)
            throws AMQException
    {
        super(delegate, data);
        if (data != null)
        {
            try
            {
                populateListFromData(data);
            }
            catch (JMSException je)
            {
                throw new AMQException(null,
                        "Error populating ListMessage from ByteBuffer", je);
            }
        }
        currentIndex = 0;
    }

    public String toBodyString() throws JMSException
    {
        return _list == null ? "" : _list.toString();
    }

    protected String getMimeType()
    {
        return MIME_TYPE;
    }

    /* ListMessage Implementation. */
    public boolean add(Object a) throws JMSException
    {
        checkWritable();
        checkAllowedValue(a);
        try
        {
            return _list.add(a);
        }
        catch (Exception e)
        {
            MessageFormatException ex = new MessageFormatException("Error adding to ListMessage");
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;

        }
    }

    public void add(int index, Object element) throws JMSException
    {
        checkWritable();
        checkAllowedValue(element);
        try
        {
            _list.add(index, element);
        }
        catch (Exception e)
        {
            MessageFormatException ex = new MessageFormatException("Error adding to ListMessage at "
                    + index);
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
    }

    public boolean contains(Object o) throws JMSException
    {
        try
        {
            return _list.contains(o);
        }
        catch (Exception e)
        {
            JMSException ex = new JMSException("Error when looking up object");
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
    }

    public Object get(int index) throws JMSException
    {
        try
        {
            return _list.get(index);
        }
        catch (IndexOutOfBoundsException e)
        {
            MessageFormatException ex = new MessageFormatException(
                    "Error getting ListMessage element at " + index);
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
    }

    public int indexOf(Object o)
    {
        return _list.indexOf(o);
    }

    public Iterator iterator()
    {
        return _list.iterator();
    }

    public Object remove(int index) throws JMSException
    {
        checkWritable();
        try
        {
            return _list.remove(index);
        }
        catch (IndexOutOfBoundsException e)
        {
            MessageFormatException ex = new MessageFormatException(
                    "Error removing ListMessage element at " + index);
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
    }

    public boolean remove(Object o) throws JMSException
    {
        checkWritable();
        return _list.remove(o);
    }

    public Object set(int index, Object element) throws JMSException
    {
        checkWritable();
        checkAllowedValue(element);
        try
        {
            return _list.set(index, element);
        }
        catch (Exception e)
        {
            MessageFormatException ex = new MessageFormatException(
                    "Error setting ListMessage element at " + index);
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
    }

    public int size()
    {
        return _list.size();
    }

    public Object[] toArray()
    {
        return _list.toArray();
    }

    /* MapMessage Implementation */
    private boolean isValidIndex(int index)
    {
        if (index >= 0 && index < size())
            return true;

        return false;
    }

    private int getValidIndex(String indexStr) throws JMSException
    {
        if ((indexStr == null) || indexStr.equals(""))
        {
            throw new IllegalArgumentException(
                    "Property name cannot be null, or the empty String.");
        }

        int index = 0;
        try
        {
            index = Integer.parseInt(indexStr);
        }
        catch (NumberFormatException e)
        {
            JMSException ex = new JMSException("Invalid index string");
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
        if (isValidIndex(index))
            return index;

        throw new MessageFormatException("Property " + indexStr
                + " should be a valid index into the list of size " + size());
    }

    private void setGenericForMap(String propName, Object o)
            throws JMSException
    {
        checkWritable();
        int index = 0;
        try
        {
            index = Integer.parseInt(propName);
        }
        catch (NumberFormatException e)
        {
            JMSException ex = new JMSException("The property name should be a valid index");
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }

        if (isValidIndex(index))
            remove(index);
        add(index, o);
    }

    public boolean getBoolean(String propName) throws JMSException
    {
        return getBooleanImpl(getValidIndex(propName));
    }

    public byte getByte(String propName) throws JMSException
    {
        return getByteImpl(getValidIndex(propName));
    }

    public short getShort(String propName) throws JMSException
    {
        return getShortImpl(getValidIndex(propName));
    }

    public int getInt(String propName) throws JMSException
    {
        return getIntImpl(getValidIndex(propName));
    }

    public long getLong(String propName) throws JMSException
    {
        return getLongImpl(getValidIndex(propName));
    }

    public char getChar(String propName) throws JMSException
    {
        return getCharImpl(getValidIndex(propName));

    }

    public float getFloat(String propName) throws JMSException
    {
        return getFloatImpl(getValidIndex(propName));
    }

    public double getDouble(String propName) throws JMSException
    {
        return getDoubleImpl(getValidIndex(propName));
    }

    public String getString(String propName) throws JMSException
    {
        return getStringImpl(getValidIndex(propName));
    }

    public byte[] getBytes(String propName) throws JMSException
    {
        return getBytesImpl(getValidIndex(propName));
    }

    public Object getObject(String propName) throws JMSException
    {
        return get(getValidIndex(propName));
    }

    public Enumeration getMapNames() throws JMSException
    {
        List<String> names = new ArrayList<String>();
        int i = 0;

        while (i < size())
            names.add(Integer.toString(i++));

        return Collections.enumeration(names);
    }

    public void setBoolean(String propName, boolean b) throws JMSException
    {
        setGenericForMap(propName, b);
    }

    public void setByte(String propName, byte b) throws JMSException
    {
        setGenericForMap(propName, b);
    }

    public void setShort(String propName, short i) throws JMSException
    {
        setGenericForMap(propName, i);
    }

    public void setChar(String propName, char c) throws JMSException
    {
        setGenericForMap(propName, c);
    }

    public void setInt(String propName, int i) throws JMSException
    {
        setGenericForMap(propName, i);
    }

    public void setLong(String propName, long l) throws JMSException
    {
        setGenericForMap(propName, l);
    }

    public void setFloat(String propName, float v) throws JMSException
    {
        setGenericForMap(propName, v);
    }

    public void setDouble(String propName, double v) throws JMSException
    {
        setGenericForMap(propName, v);
    }

    public void setString(String propName, String string1) throws JMSException
    {
        setGenericForMap(propName, string1);
    }

    public void setBytes(String propName, byte[] bytes) throws JMSException
    {
        setGenericForMap(propName, bytes);
    }

    public void setBytes(String propName, byte[] bytes, int offset, int length)
            throws JMSException
    {
        if ((offset == 0) && (length == bytes.length))
        {
            setBytes(propName, bytes);
        }
        else
        {
            byte[] newBytes = new byte[length];
            System.arraycopy(bytes, offset, newBytes, 0, length);
            setBytes(propName, newBytes);
        }
    }

    public void setObject(String propName, Object value) throws JMSException
    {
        checkAllowedValue(value);
        setGenericForMap(propName, value);
    }

    public boolean itemExists(String propName) throws JMSException
    {
        return isValidIndex(Integer.parseInt(propName));
    }

    // StreamMessage methods

    private int currentIndex;

    private static final String MESSAGE_EOF_EXCEPTION = "End of Stream (ListMessage) at index: ";

    private void setGenericForStream(Object o) throws JMSException
    {
        checkWritable();
        add(o);
        currentIndex++;
    }

    @Override
    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getBooleanImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public byte readByte() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getByteImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public int readBytes(byte[] value) throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
        {
            ByteBuffer res = ByteBuffer.wrap(getBytesImpl(currentIndex++));
            res.get(value);
            return value.length;
        }

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public char readChar() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getCharImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public double readDouble() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getDoubleImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public float readFloat() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getFloatImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public int readInt() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getIntImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public long readLong() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getLongImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public Object readObject() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return get(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public short readShort() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getShortImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public String readString() throws JMSException
    {
        checkReadable();
        if (isValidIndex(currentIndex))
            return getStringImpl(currentIndex++);

        throw new MessageEOFException(MESSAGE_EOF_EXCEPTION + currentIndex);
    }

    @Override
    public void writeBoolean(boolean value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeByte(byte value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeBytes(byte[] value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeBytes(byte[] value, int offset, int length)
            throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeChar(char value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeDouble(double value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeFloat(float value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeInt(int value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeLong(long value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeObject(Object value) throws JMSException
    {
        checkAllowedValue(value);
        setGenericForStream(value);
    }

    @Override
    public void writeShort(short value) throws JMSException
    {
        setGenericForStream(value);
    }

    @Override
    public void writeString(String value) throws JMSException
    {
        setGenericForStream(value);
    }

    // Common methods

    private void checkAllowedValue(Object value) throws MessageFormatException
    {
        if (((value instanceof Boolean) || (value instanceof Byte)
                || (value instanceof Short) || (value instanceof Integer)
                || (value instanceof Long) || (value instanceof Character)
                || (value instanceof Float) || (value instanceof Double)
                || (value instanceof String) || (value instanceof byte[])
                || (value instanceof List) || (value instanceof Map)
                || (value instanceof UUID) || (value == null)) == false)
        {
            throw new MessageFormatException("Invalid value " + value
                    + "of type " + value.getClass().getName() + ".");
        }
    }

    @Override
    public void reset()
    {
        currentIndex = 0;
        setReadable(true);
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _list.clear();
        currentIndex = 0;
        setReadable(false);
    }

    private boolean getBooleanImpl(int index) throws JMSException
    {
        Object value = get(index);

        if (value instanceof Boolean)
        {
            return ((Boolean) value).booleanValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            try
            {
                return Boolean.valueOf((String) value);
            }
            catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName()
                + " cannot be converted to boolean.");
    }

    private byte getByteImpl(int index) throws JMSException
    {
        Object value = get(index);

        if (value instanceof Byte)
        {
            return ((Byte) value).byteValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            try
            {
                return Byte.valueOf((String) value).byteValue();
            } catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName() + " cannot be converted to byte.");
    }

    private short getShortImpl(int index) throws JMSException
    {
        Object value = get(index);

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
            try
            {
                return Short.valueOf((String) value).shortValue();
            } catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName() + " cannot be converted to short.");
    }

    private int getIntImpl(int index) throws JMSException
    {
        Object value = get(index);

        if (value instanceof Integer)
        {
            return ((Integer) value).intValue();
        }

        if (value instanceof Short)
        {
            return ((Short) value).intValue();
        }

        if (value instanceof Byte)
        {
            return ((Byte) value).intValue();
        }

        if ((value instanceof String) || (value == null))
        {
            try
            {
                return Integer.valueOf((String) value).intValue();
            }
            catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName() + " cannot be converted to int.");
    }

    private long getLongImpl(int index) throws JMSException
    {
        Object value = get(index);

        if (value instanceof Long)
        {
            return ((Long) value).longValue();
        } else if (value instanceof Integer)
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
        } else if ((value instanceof String) || (value == null))
        {
            try
            {
                return Long.valueOf((String) value).longValue();
            } catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName() + " cannot be converted to long.");
    }

    private char getCharImpl(int index) throws JMSException
    {
        Object value = get(index);

        if (value instanceof Character)
        {
            return ((Character) value).charValue();
        } else if (value == null)
        {
            throw new NullPointerException("Property at " + index
                    + " has null value and therefore cannot "
                    + "be converted to char.");
        } else
        {
            throw new MessageFormatException("Property at " + index
                    + " of type " + value.getClass().getName()
                    + " cannot be converted to a char.");
        }
    }

    private float getFloatImpl(int index) throws JMSException
    {
        Object value = get(index);

        if (value instanceof Float)
        {
            return ((Float) value).floatValue();
        }
        else if ((value instanceof String) || (value == null))
        {
            try
            {
                return Float.valueOf((String) value).floatValue();
            }
            catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName() + " cannot be converted to float.");
    }

    private double getDoubleImpl(int index) throws JMSException
    {
        Object value = get(index);

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
            try
            {
                return Double.valueOf((String) value).doubleValue();
            }
            catch (NumberFormatException e)
            {
                // FALLTHROUGH to exception
            }
        }

        throw new MessageFormatException("Property at " + index + " of type "
                + value.getClass().getName()
                + " cannot be converted to double.");
    }

    private String getStringImpl(int index) throws JMSException
    {
        Object value = get(index);

        if ((value instanceof String) || (value == null))
        {
            return (String) value;
        } else if (value instanceof byte[])
        {
            throw new MessageFormatException("Property at " + index
                    + " of type byte[] " + "cannot be converted to String.");
        } else
        {
            return value.toString();
        }
    }

    private byte[] getBytesImpl(int index) throws JMSException
    {
        Object value = get(index);

        if ((value instanceof byte[]) || (value == null))
        {
            return (byte[]) value;
        }
        else
        {
            throw new MessageFormatException("Property at " + index
                    + " of type " + value.getClass().getName()
                    + " cannot be converted to byte[].");
        }
    }

    protected void populateListFromData(ByteBuffer data) throws JMSException
    {
        if (data != null)
        {
            data.rewind();
            BBDecoder decoder = new BBDecoder();
            decoder.init(data);
            _list = decoder.readList();
        }
        else
        {
            _list.clear();
        }
    }

    public ByteBuffer getData() throws JMSException
    {
        BBEncoder encoder = new BBEncoder(1024);
        encoder.writeList(_list);
        return encoder.segment();
    }

    public void setList(List<Object> l)
    {
        _list = l;
    }

    public List<Object> asList()
    {
        return _list;
    }
}
