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
package org.apache.qpid.framing;

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQPInvalidClassException;

import java.util.*;

//extends FieldTable
public class FieldTable
{
    private static final Logger _logger = Logger.getLogger(FieldTable.class);

    private ByteBuffer _encodedForm;
    private LinkedHashMap<AMQShortString, AMQTypedValue> _properties;
    private long _encodedSize;
    private static final int INITIAL_HASHMAP_CAPACITY = 16;

    public FieldTable()
    {
        super();
    }

    /**
     * Construct a new field table.
     *
     * @param buffer the buffer from which to read data. The length byte must be read already
     * @param length the length of the field table. Must be > 0.
     * @throws AMQFrameDecodingException if there is an error decoding the table
     */
    public FieldTable(ByteBuffer buffer, long length) throws AMQFrameDecodingException
    {
        this();
        _encodedForm = buffer.slice();
        _encodedForm.limit((int)length);
        _encodedSize = length;
        buffer.skip((int)length);
    }



    private AMQTypedValue getProperty(AMQShortString string)
    {
        synchronized(this)
        {
            if(_properties == null)
            {
                if(_encodedForm == null)
                {
                    return null;
                }
                else
                {
                    populateFromBuffer();
                }
            }
        }

        if(_properties == null)
        {
            return null;
        }
        else
        {
            return _properties.get(string);
        }
    }

    private void populateFromBuffer()
    {
        try
        {
            setFromBuffer(_encodedForm, _encodedSize);
        }
        catch (AMQFrameDecodingException e)
        {
            _logger.error("Error decoding FieldTable in deferred decoding mode ", e);
            throw new IllegalArgumentException(e);
        }
    }


    private AMQTypedValue setProperty(AMQShortString key, AMQTypedValue val)
    {
        initMapIfNecessary();
        _encodedForm = null;
        if(val == null)
        {
            return removeKey(key);
        }
        AMQTypedValue oldVal = _properties.put(key,val);
        if(oldVal != null)
        {
            _encodedSize -= oldVal.getEncodingSize();
        }
        else
        {
            _encodedSize += EncodingUtils.encodedShortStringLength(key) + 1;
        }
        _encodedSize += val.getEncodingSize();

        return oldVal;
    }

    private void initMapIfNecessary()
    {
        synchronized(this)
        {
            if(_properties == null)
            {
                if(_encodedForm == null)
                {
                    _properties = new LinkedHashMap<AMQShortString,AMQTypedValue>();
                }
                else
                {
                    populateFromBuffer();
                }
            }

        }
    }


    public Boolean getBoolean(String string)
    {
        return getBoolean(new AMQShortString(string));
    }

    public Boolean getBoolean(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.BOOLEAN))
        {
            return (Boolean) value.getValue();
        }
        else
        {
            return null;
        }
    }


    public Byte getByte(String string)
    {
        return getByte(new AMQShortString(string));
    }

    public Byte getByte(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.BYTE))
        {
            return (Byte) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Short getShort(String string)
    {
        return getShort(new AMQShortString(string));
    }

    public Short getShort(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.SHORT))
        {
            return (Short) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Integer getInteger(String string)
    {
        return getInteger(new AMQShortString(string));
    }

    public Integer getInteger(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.INT))
        {
            return (Integer) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Long getLong(String string)
    {
        return getLong(new AMQShortString(string));
    }

    public Long getLong(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.LONG))
        {
            return (Long) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Float getFloat(String string)
    {
        return getFloat(new AMQShortString(string));
    }

    public Float getFloat(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.FLOAT))
        {
            return (Float) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Double getDouble(String string)
    {
        return getDouble(new AMQShortString(string));
    }

    public Double getDouble(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.DOUBLE))
        {
            return (Double) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public String getString(String string)
    {
        return getString(new AMQShortString(string));
    }

    public String getString(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && ((value.getType() == AMQType.WIDE_STRING) ||
                                (value.getType() == AMQType.ASCII_STRING)))
        {
            return (String) value.getValue();
        }

        else if ((value != null) && (value.getValue() != null) && !(value.getValue() instanceof byte[]))
        {
            return String.valueOf(value.getValue());
        }
        else
        {
            return null;
        }

    }

    public Character getCharacter(String string)
    {
        return getCharacter(new AMQShortString(string));
    }

    public Character getCharacter(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.ASCII_CHARACTER))
        {
            return (Character) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public byte[] getBytes(String string)
    {
        return getBytes(new AMQShortString(string));
    }

    public byte[] getBytes(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null && (value.getType() == AMQType.BINARY))
        {
            return (byte[]) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Object getObject(String string)
    {
        return getObject(new AMQShortString(string));
    }

    public Object getObject(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if(value != null)
        {
            return value.getValue();
        }
        else
        {
            return value;
        }

    }

    // ************  Setters
    public Object setBoolean(String string, boolean b)
    {
        return setBoolean(new AMQShortString(string), b);
    }

    public Object setBoolean(AMQShortString string, boolean b)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.BOOLEAN.asTypedValue(b));
    }

    public Object setByte(String string, byte b)
    {
        return setByte(new AMQShortString(string), b);
    }    

    public Object setByte(AMQShortString string, byte b)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.BYTE.asTypedValue(b));
    }

    public Object setShort(String string, short i)
    {
        return setShort(new AMQShortString(string), i);
    }

    public Object setShort(AMQShortString string, short i)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.SHORT.asTypedValue(i));
    }


    public Object setInteger(String string, int i)
    {
        return setInteger(new AMQShortString(string), i);
    }

    public Object setInteger(AMQShortString string, int i)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.INT.asTypedValue(i));
    }


    public Object setLong(String string, long l)
    {
        return setLong(new AMQShortString(string), l);
    }

    public Object setLong(AMQShortString string, long l)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.LONG.asTypedValue(l));
    }


    public Object setFloat(String string, float f)
    {
        return setFloat(new AMQShortString(string), f);
    }

    public Object setFloat(AMQShortString string, float v)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.FLOAT.asTypedValue(v));
    }

    public Object setDouble(String string, double d)
    {
        return setDouble(new AMQShortString(string), d);
    }


    public Object setDouble(AMQShortString string, double v)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.DOUBLE.asTypedValue(v));
    }


    public Object setString(String string, String s)
    {
        return setString(new AMQShortString(string), s);
    }

    public Object setAsciiString(AMQShortString string, String value)
    {
        checkPropertyName(string);
        if (value == null)
        {
            return setProperty(string, AMQType.VOID.asTypedValue(null));
        }
        else
        {
            return setProperty(string, AMQType.ASCII_STRING.asTypedValue(value));
        }
    }

    public Object setString(AMQShortString string, String value)
    {
        checkPropertyName(string);
        if (value == null)
        {
            return setProperty(string, AMQType.VOID.asTypedValue(null));
        }
        else
        {
            return setProperty(string, AMQType.LONG_STRING.asTypedValue(value));
        }
    }


    public Object setChar(String string, char c)
    {
        return setChar(new AMQShortString(string), c);
    }


    public Object setChar(AMQShortString string, char c)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.ASCII_CHARACTER.asTypedValue(c));
    }


    public Object setBytes(String string, byte[] b)
    {
        return setBytes(new AMQShortString(string), b);
    }

    public Object setBytes(AMQShortString string, byte[] bytes)
    {
        checkPropertyName(string);
        return setProperty(string, AMQType.BINARY.asTypedValue(bytes));
    }

    public Object setBytes(String string, byte[] bytes, int start, int length)
    {
        return setBytes(new AMQShortString(string), bytes,start,length);
    }

    public Object setBytes(AMQShortString string, byte[] bytes, int start, int length)
    {
        checkPropertyName(string);
        byte[] newBytes = new byte[length];
        System.arraycopy(bytes,start,newBytes,0,length);
        return setBytes(string, bytes);
    }

    public Object setObject(String string, Object o)
    {
        return setObject(new AMQShortString(string), o);
    }

    public Object setObject(AMQShortString string, Object object)
    {
        if (object instanceof Boolean)
        {
            return setBoolean(string, (Boolean) object);
        }
        else if (object instanceof Byte)
        {
            return setByte(string, (Byte) object);
        }
        else if (object instanceof Short)
        {
            return setShort(string, (Short) object);
        }
        else if (object instanceof Integer)
        {
            return setInteger(string, (Integer) object);
        }
        else if (object instanceof Long)
        {
            return setLong(string, (Long) object);
        }
        else if (object instanceof Float)
        {
            return setFloat(string, (Float) object);
        }
        else if (object instanceof Double)
        {
            return setDouble(string, (Double) object);
        }
        else if (object instanceof String)
        {
            return setString(string, (String) object);
        }
        else if (object instanceof Character)
        {
            return setChar(string, (Character) object);
        }
        else if (object instanceof byte[])
        {
            return setBytes(string, (byte[]) object);
        }

        throw new AMQPInvalidClassException("Only Primatives objects allowed Object is:" + object.getClass());
    }


    public boolean isNullStringValue(String name)
    {
        AMQTypedValue value = getProperty(new AMQShortString(name));
        return (value != null) && (value.getType() == AMQType.VOID);
    }

    // ***** Methods

    public Enumeration getPropertyNames()
    {
        return Collections.enumeration(keys());
    }

    public boolean propertyExists(AMQShortString propertyName)
    {
        return itemExists(propertyName);
    }

    public boolean propertyExists(String propertyName)
    {
        return itemExists(propertyName);
    }

    public boolean itemExists(AMQShortString string)
    {
        initMapIfNecessary();
        return _properties.containsKey(string);
    }

    public boolean itemExists(String string)
    {
        return itemExists(new AMQShortString(string));
    }

    public String toString()
    {
        initMapIfNecessary();
        return _properties.toString();
    }



    private void checkPropertyName(AMQShortString propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if (propertyName.length()==0)
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);
    }


    protected static void checkIdentiferFormat(AMQShortString propertyName)
    {
//        AMQP Spec: 4.2.5.5 Field Tables
//        Guidelines for implementers:
//           * Field names MUST start with a letter, '$' or '#' and may continue with
//             letters, '$' or '#', digits, or underlines, to a maximum length of 128
//             characters.
//           * The server SHOULD validate field names and upon receiving an invalid
//             field name, it SHOULD signal a connection exception with reply code
//             503 (syntax error). Conformance test: amq_wlp_table_01.
//           * A peer MUST handle duplicate fields by using only the first instance.


        // AMQP length limit
        if (propertyName.length() > 128)
        {
            throw new IllegalArgumentException("AMQP limits property names to 128 characters");
        }

        // AMQ start character
        if (!(Character.isLetter(propertyName.charAt(0))
              || propertyName.charAt(0) == '$'
              || propertyName.charAt(0) == '#'
              || propertyName.charAt(0) == '_')) // Not official AMQP added for JMS.
        {
            throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid AMQP start character");
        }
    }

 
    // *************************  Byte Buffer Processing

    public void writeToBuffer(ByteBuffer buffer)
    {
        final boolean trace = _logger.isTraceEnabled();

        if (trace)
        {
            _logger.trace("FieldTable::writeToBuffer: Writing encoded length of " + getEncodedSize() + "...");
        }

        EncodingUtils.writeUnsignedInteger(buffer, getEncodedSize());

        putDataInBuffer(buffer);
    }

    public byte[] getDataAsBytes()
    {
        final int encodedSize = (int) getEncodedSize();
        final ByteBuffer buffer = ByteBuffer.allocate(encodedSize); // FIXME XXX: Is cast a problem?

        putDataInBuffer(buffer);

        final byte[] result = new byte[encodedSize];
        buffer.flip();
        buffer.get(result);
        buffer.release();
        return result;
    }

    public long getEncodedSize()
    {
        return _encodedSize;
    }

    private void recalculateEncodedSize()
    {

        int encodedSize = 0;
        if(_properties != null)
        {
            for(Map.Entry<AMQShortString,AMQTypedValue> e : _properties.entrySet())
            {
                encodedSize += EncodingUtils.encodedShortStringLength(e.getKey());
                encodedSize++; // the byte for the encoding Type
                encodedSize += e.getValue().getEncodingSize();

            }
        }
        _encodedSize = encodedSize;
    }

    public void addAll(FieldTable fieldTable)
    {
        initMapIfNecessary();
        _properties.putAll(fieldTable._properties);
        recalculateEncodedSize();
    }


    public static interface FieldTableElementProcessor
    {
        public boolean processElement(String propertyName, AMQTypedValue value);
        public Object getResult();
    }

    public Object processOverElements(FieldTableElementProcessor processor)
    {
        initMapIfNecessary();
        if(_properties != null)
        {
            for(Map.Entry<AMQShortString,AMQTypedValue> e : _properties.entrySet())
            {
                boolean result = processor.processElement(e.getKey().toString(), e.getValue());
                if(!result)
                {
                    break;
                }
            }
        }
        return processor.getResult();


    }


    public int size()
    {
        initMapIfNecessary();
        return _properties.size();

    }

    public boolean isEmpty()
    {
        return size() ==0;
    }

    public boolean containsKey(AMQShortString key)
    {
        initMapIfNecessary();
        return _properties.containsKey(key);
    }

    public boolean containsKey(String key)
    {
        return containsKey(new AMQShortString(key));
    }

    public Set<String> keys()
    {
        initMapIfNecessary();
        Set<String> keys = new LinkedHashSet<String>();
        for(AMQShortString key : _properties.keySet())
        {
            keys.add(key.toString());
        }
        return keys;
    }


    public Object get(AMQShortString key)
    {

        return getObject(key);
    }



    public Object put(AMQShortString key, Object value)
    {
        return setObject(key, value);
    }


    public Object remove(String key)
    {

        return remove(new AMQShortString(key));

    }

    public Object remove(AMQShortString key)
    {
        AMQTypedValue val = removeKey(key);
        return val == null ? null : val.getValue();

    }


    public AMQTypedValue removeKey(AMQShortString key)
    {
        initMapIfNecessary();
        _encodedForm = null;
        AMQTypedValue value = _properties.remove(key);
        if(value == null)
        {
            return null;
        }
        else
        {
            _encodedSize -= EncodingUtils.encodedShortStringLength(key);
            _encodedSize--;
            _encodedSize -= value.getEncodingSize();
            return value;
        }

    }



    public void clear()
    {
        initMapIfNecessary();
        _encodedForm = null;        
        _properties.clear();
        _encodedSize = 0;
    }

    public Set<AMQShortString> keySet()
    {
        initMapIfNecessary();
        return _properties.keySet();
    }

    private void putDataInBuffer(ByteBuffer buffer)
    {

        if(_encodedForm != null)
        {
             buffer.put(_encodedForm);
        }
        else if(_properties != null)
        {
            final Iterator<Map.Entry<AMQShortString,AMQTypedValue>> it = _properties.entrySet().iterator();

            //If there are values then write out the encoded Size... could check _encodedSize != 0
            // write out the total length, which we have kept up to date as data is added


            while (it.hasNext())
            {
                final Map.Entry<AMQShortString,AMQTypedValue> me = it.next();
                try
                {
                    if (_logger.isTraceEnabled())
                    {
                        _logger.trace("Writing Property:" + me.getKey() +
                                      " Type:" + me.getValue().getType() +
                                      " Value:" + me.getValue().getValue());
                        _logger.trace("Buffer Position:" + buffer.position() +
                                      " Remaining:" + buffer.remaining());
                    }



                    //Write the actual parameter name
                    EncodingUtils.writeShortStringBytes(buffer, me.getKey());
                    me.getValue().writeToBuffer(buffer);
                }
                catch (Exception e)
                {
                    if (_logger.isTraceEnabled())
                    {
                        _logger.trace("Exception thrown:" + e);
                        _logger.trace("Writing Property:" + me.getKey() +
                                      " Type:" + me.getValue().getType() +
                                      " Value:" + me.getValue().getValue());
                        _logger.trace("Buffer Position:" + buffer.position() +
                                      " Remaining:" + buffer.remaining());
                    }
                    throw new RuntimeException(e);
                }
            }
        }
    }


    private void setFromBuffer(ByteBuffer buffer, long length) throws AMQFrameDecodingException
    {

        final boolean trace = _logger.isTraceEnabled();
        if(length > 0)
        {

            final int expectedRemaining = buffer.remaining()-(int)length;

            _properties = new LinkedHashMap<AMQShortString,AMQTypedValue>(INITIAL_HASHMAP_CAPACITY);

            do
            {

                final AMQShortString key = EncodingUtils.readAMQShortString(buffer);
                AMQTypedValue value = AMQTypedValue.readFromBuffer(buffer);

                if (trace)
                {
                    _logger.trace("FieldTable::PropFieldTable(buffer," + length + "): Read type '" + value.getType() + "', key '" + key + "', value '" + value.getValue() + "'");
                }



                _properties.put(key,value);


            
            }
            while (buffer.remaining() > expectedRemaining);

        }
        _encodedSize = length;

        if (trace)
        {
            _logger.trace("FieldTable::FieldTable(buffer," + length + "): Done.");
        }
    }

}
