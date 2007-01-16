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

    private LinkedHashMap<String, AMQTypedValue> _properties;

    public FieldTable()
    {
        super();
        _properties = new LinkedHashMap<String, AMQTypedValue>();

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
        setFromBuffer(buffer, length);
    }



    public Boolean getBoolean(String string)
    {
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        AMQTypedValue value = _properties.get(string);
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
        checkPropertyName(string);
        return _properties.put(string, AMQType.BOOLEAN.asTypedValue(b));
    }

    public Object setByte(String string, byte b)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.BYTE.asTypedValue(b));
    }

    public Object setShort(String string, short i)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.SHORT.asTypedValue(i));
    }

    public Object setInteger(String string, int i)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.INT.asTypedValue(i));
    }

    public Object setLong(String string, long l)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.LONG.asTypedValue(l));
    }

    public Object setFloat(String string, float v)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.FLOAT.asTypedValue(v));
    }

    public Object setDouble(String string, double v)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.DOUBLE.asTypedValue(v));
    }

    public Object setString(String string, String value)
    {
        checkPropertyName(string);
        if (value == null)
        {
            return _properties.put(string, AMQType.VOID.asTypedValue(null));
        }
        else
        {
            //FIXME: determine string encoding and set either WIDE or ASCII string
//            if ()
            {
                return _properties.put(string, AMQType.WIDE_STRING.asTypedValue(value));
            }
//            else
//            {
//                return _properties.put(string, AMQType.ASCII_STRING.asTypedValue(value));
//            }
        }
    }

    public Object setChar(String string, char c)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.ASCII_CHARACTER.asTypedValue(c));
    }

    public Object setBytes(String string, byte[] bytes)
    {
        checkPropertyName(string);
        return _properties.put(string, AMQType.BINARY.asTypedValue(bytes));
    }

    public Object setBytes(String string, byte[] bytes, int start, int length)
    {
        checkPropertyName(string);
        byte[] newBytes = new byte[length];
        System.arraycopy(bytes,start,newBytes,0,length);
        return setBytes(string, bytes);
    }


    public Object setObject(String string, Object object)
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
        AMQTypedValue value = _properties.get(name);
        return (value != null) && (value.getType() == AMQType.VOID);
    }

    // ***** Methods

    public Enumeration getPropertyNames()
    {
        return Collections.enumeration(_properties.keySet());
    }

    public boolean propertyExists(String propertyName)
    {
        return itemExists(propertyName);
    }

    public boolean itemExists(String string)
    {
        return _properties.containsKey(string);
    }

    public String toString()
    {
        return _properties.toString();
    }



    private void checkPropertyName(String propertyName)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if ("".equals(propertyName))
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);
    }


    protected static void checkIdentiferFormat(String propertyName)
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
        int encodedSize = 0;
        for(Map.Entry<String,AMQTypedValue> e : _properties.entrySet())
        {
            encodedSize += EncodingUtils.encodedShortStringLength(e.getKey());
            encodedSize++; // the byte for the encoding Type
            encodedSize += e.getValue().getEncodingSize();

        }
        return encodedSize;
    }

    public void addAll(FieldTable fieldTable)
    {
        _properties.putAll(fieldTable._properties);
    }


    public static interface FieldTableElementProcessor
    {
        public boolean processElement(String propertyName, AMQTypedValue value);
        public Object getResult();
    }

    public Object processOverElements(FieldTableElementProcessor processor)
    {
        for(Map.Entry<String,AMQTypedValue> e : _properties.entrySet())
        {
            boolean result = processor.processElement(e.getKey(), e.getValue());
            if(!result)
            {
                break;
            }
        }
        return processor.getResult();
    }


    public int size()
    {
        return _properties.size();
    }

    public boolean isEmpty()
    {
        return _properties.isEmpty();
    }

    public boolean containsKey(String key)
    {
        return _properties.containsKey(key);
    }

    public Set<String> keys()
    {
        return _properties.keySet();
    }


    public Object get(Object key)
    {

        return getObject((String)key);
    }


    public Object put(Object key, Object value)
    {
        return setObject(key.toString(), value);
    }

    
    public Object remove(String key)
    {
        AMQTypedValue value = _properties.remove(key);
        return value == null ? null : value.getValue();
    }



    public void clear()
    {
        _properties.clear();
    }

    public Set keySet()
    {
        return _properties.keySet();
    }

    private void putDataInBuffer(ByteBuffer buffer)
    {

        final Iterator<Map.Entry<String,AMQTypedValue>> it = _properties.entrySet().iterator();

        //If there are values then write out the encoded Size... could check _encodedSize != 0
        // write out the total length, which we have kept up to date as data is added


        while (it.hasNext())
        {
            final Map.Entry<String,AMQTypedValue> me = it.next();
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


    public void setFromBuffer(ByteBuffer buffer, long length) throws AMQFrameDecodingException
    {
        final boolean trace = _logger.isTraceEnabled();

        int sizeRead = 0;
        while (sizeRead < length)
        {
            int sizeRemaining = buffer.remaining();
            final String key = EncodingUtils.readShortString(buffer);
            AMQTypedValue value = AMQTypedValue.readFromBuffer(buffer);
            sizeRead += (sizeRemaining - buffer.remaining());

            if (trace)
            {
                _logger.trace("FieldTable::PropFieldTable(buffer," + length + "): Read type '" + value.getType() + "', key '" + key + "', value '" + value.getValue() + "' (now read " + sizeRead + " of " + length + " encoded bytes)...");
            }

            _properties.put(key,value);
        }

        if (trace)
        {
            _logger.trace("FieldTable::FieldTable(buffer," + length + "): Done.");
        }
    }

}
