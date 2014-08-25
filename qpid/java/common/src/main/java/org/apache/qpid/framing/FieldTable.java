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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQPInvalidClassException;

// extends FieldTable
public class FieldTable
{
    private static final Logger _logger = LoggerFactory.getLogger(FieldTable.class);
    private static final String STRICT_AMQP_NAME = "STRICT_AMQP";
    private static final boolean STRICT_AMQP = Boolean.valueOf(System.getProperty(STRICT_AMQP_NAME, "false"));

    private byte[] _encodedForm;
    private int _encodedFormOffset;
    private LinkedHashMap<AMQShortString, AMQTypedValue> _properties = null;
    private long _encodedSize;
    private static final int INITIAL_HASHMAP_CAPACITY = 16;
    private static final int INITIAL_ENCODED_FORM_SIZE = 256;
    private final boolean _strictAMQP;

    public FieldTable()
    {
        this(STRICT_AMQP);
    }


    public FieldTable(boolean strictAMQP)
    {
        super();
        _strictAMQP = strictAMQP;
    }

    /**
     * Construct a new field table.
     *
     * @param buffer the buffer from which to read data. The length byte must be read already
     * @param length the length of the field table. Must be great than 0.
     * @throws IOException if there is an issue reading the buffer
     */
    public FieldTable(DataInput buffer, long length) throws IOException
    {
        this();
        _encodedForm = new byte[(int) length];
        buffer.readFully(_encodedForm);
        _encodedSize = length;
    }

    public FieldTable(byte[] encodedForm, int offset, int length)
    {
        this();
        _encodedForm = encodedForm;
        _encodedFormOffset = offset;
        _encodedSize = length;
    }


    public boolean isClean()
    {
        return _encodedForm != null;
    }

    public AMQTypedValue getProperty(AMQShortString string)
    {
        checkPropertyName(string);

        synchronized (this)
        {
            if (_properties == null)
            {
                if (_encodedForm == null)
                {
                    return null;
                }
                else
                {
                    populateFromBuffer();
                }
            }
        }

        if (_properties == null)
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
            setFromBuffer();
        }
        catch (AMQFrameDecodingException e)
        {
            _logger.error("Error decoding FieldTable in deferred decoding mode ", e);
            throw new IllegalArgumentException(e);
        }
        catch (IOException e)
        {
            _logger.error("Unexpected IO exception decoding field table");
            throw new IllegalArgumentException(e);

        }
    }

    private AMQTypedValue setProperty(AMQShortString key, AMQTypedValue val)
    {
        checkPropertyName(key);
        initMapIfNecessary();
        if (_properties.containsKey(key))
        {
            _encodedForm = null;

            if (val == null)
            {
                return removeKey(key);
            }
        }
        else if ((_encodedForm != null) && (val != null))
        {
            // We have updated data to store in the buffer
            // So clear the _encodedForm to allow it to be rebuilt later
            // this is safer than simply appending to any existing buffer.
            _encodedForm = null;
        }
        else if (val == null)
        {
            return null;
        }

        AMQTypedValue oldVal = _properties.put(key, val);
        if (oldVal != null)
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
        synchronized (this)
        {
            if (_properties == null)
            {
                if ((_encodedForm == null) || (_encodedSize == 0))
                {
                    _properties = new LinkedHashMap<AMQShortString, AMQTypedValue>();
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
        return getBoolean(AMQShortString.valueOf(string));
    }

    public Boolean getBoolean(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.BOOLEAN))
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
        return getByte(AMQShortString.valueOf(string));
    }

    public Byte getByte(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.BYTE))
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
        return getShort(AMQShortString.valueOf(string));
    }

    public Short getShort(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.SHORT))
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
        return getInteger(AMQShortString.valueOf(string));
    }

    public Integer getInteger(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.INT))
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
        return getLong(AMQShortString.valueOf(string));
    }

    public Long getLong(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.LONG))
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
        return getFloat(AMQShortString.valueOf(string));
    }

    public Float getFloat(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.FLOAT))
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
        return getDouble(AMQShortString.valueOf(string));
    }

    public Double getDouble(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.DOUBLE))
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
        return getString(AMQShortString.valueOf(string));
    }

    public String getString(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && ((value.getType() == AMQType.WIDE_STRING) || (value.getType() == AMQType.ASCII_STRING)))
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
        return getCharacter(AMQShortString.valueOf(string));
    }

    public Character getCharacter(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.ASCII_CHARACTER))
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
        return getBytes(AMQShortString.valueOf(string));
    }

    public byte[] getBytes(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if ((value != null) && (value.getType() == AMQType.BINARY))
        {
            return (byte[]) value.getValue();
        }
        else
        {
            return null;
        }
    }

    /**
     * Extracts a value from the field table that is itself a FieldTable associated with the specified parameter name.
     *
     * @param string The name of the parameter to get the associated FieldTable value for.
     *
     * @return The associated FieldTable value, or <tt>null</tt> if the associated value is not of FieldTable type or
     *         not present in the field table at all.
     */
    public FieldTable getFieldTable(String string)
    {
        return getFieldTable(AMQShortString.valueOf(string));
    }

    /**
     * Extracts a value from the field table that is itself a FieldTable associated with the specified parameter name.
     *
     * @param string The name of the parameter to get the associated FieldTable value for.
     *
     * @return The associated FieldTable value, or <tt>null</tt> if the associated value is not of FieldTable type or
     *         not present in the field table at all.
     */
    public FieldTable getFieldTable(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);

        if ((value != null) && (value.getType() == AMQType.FIELD_TABLE))
        {
            return (FieldTable) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public Object getObject(String string)
    {
        return getObject(AMQShortString.valueOf(string));
    }

    public Object getObject(AMQShortString string)
    {
        AMQTypedValue value = getProperty(string);
        if (value != null)
        {
            return value.getValue();
        }
        else
        {
            return value;
        }

    }

    public Long getTimestamp(AMQShortString name)
    {
        AMQTypedValue value = getProperty(name);
        if ((value != null) && (value.getType() == AMQType.TIMESTAMP))
        {
            return (Long) value.getValue();
        }
        else
        {
            return null;
        }
    }

    public BigDecimal getDecimal(AMQShortString propertyName)
    {
        AMQTypedValue value = getProperty(propertyName);
        if ((value != null) && (value.getType() == AMQType.DECIMAL))
        {
            return (BigDecimal) value.getValue();
        }
        else
        {
            return null;
        }
    }

    // ************  Setters
    public Object setBoolean(String string, Boolean b)
    {
        return setBoolean(AMQShortString.valueOf(string), b);
    }

    public Object setBoolean(AMQShortString string, Boolean b)
    {
        return setProperty(string, AMQType.BOOLEAN.asTypedValue(b));
    }

    public Object setByte(String string, Byte b)
    {
        return setByte(AMQShortString.valueOf(string), b);
    }

    public Object setByte(AMQShortString string, Byte b)
    {
        return setProperty(string, AMQType.BYTE.asTypedValue(b));
    }

    public Object setShort(String string, Short i)
    {
        return setShort(AMQShortString.valueOf(string), i);
    }

    public Object setShort(AMQShortString string, Short i)
    {
        return setProperty(string, AMQType.SHORT.asTypedValue(i));
    }

    public Object setInteger(String string, int i)
    {
        return setInteger(AMQShortString.valueOf(string), i);
    }

    public Object setInteger(AMQShortString string, int i)
    {
        return setProperty(string, AMQTypedValue.createAMQTypedValue(i));
    }

    public Object setLong(String string, long l)
    {
        return setLong(AMQShortString.valueOf(string), l);
    }

    public Object setLong(AMQShortString string, long l)
    {
        return setProperty(string, AMQTypedValue.createAMQTypedValue(l));
    }

    public Object setFloat(String string, Float f)
    {
        return setFloat(AMQShortString.valueOf(string), f);
    }

    public Object setFloat(AMQShortString string, Float v)
    {
        return setProperty(string, AMQType.FLOAT.asTypedValue(v));
    }

    public Object setDouble(String string, Double d)
    {
        return setDouble(AMQShortString.valueOf(string), d);
    }

    public Object setDouble(AMQShortString string, Double v)
    {
        return setProperty(string, AMQType.DOUBLE.asTypedValue(v));
    }

    public Object setString(String string, String s)
    {
        return setString(AMQShortString.valueOf(string), s);
    }

    public Object setAsciiString(AMQShortString string, String value)
    {
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
        return setChar(AMQShortString.valueOf(string), c);
    }

    public Object setChar(AMQShortString string, char c)
    {
        return setProperty(string, AMQType.ASCII_CHARACTER.asTypedValue(c));
    }

    public Object setFieldArray(String string, Collection<?> collection)
    {
        return setFieldArray(AMQShortString.valueOf(string), collection);
    }
    public Object setFieldArray(AMQShortString string, Collection<?> collection)
    {
        return setProperty(string, AMQType.FIELD_ARRAY.asTypedValue(collection));
    }

    public Object setBytes(String string, byte[] b)
    {
        return setBytes(AMQShortString.valueOf(string), b);
    }

    public Object setBytes(AMQShortString string, byte[] bytes)
    {
        return setProperty(string, AMQType.BINARY.asTypedValue(bytes));
    }

    public Object setBytes(String string, byte[] bytes, int start, int length)
    {
        return setBytes(AMQShortString.valueOf(string), bytes, start, length);
    }

    public Object setBytes(AMQShortString string, byte[] bytes, int start, int length)
    {
        byte[] newBytes = new byte[length];
        System.arraycopy(bytes, start, newBytes, 0, length);

        return setBytes(string, bytes);
    }

    public Object setObject(String string, Object o)
    {
        return setObject(AMQShortString.valueOf(string), o);
    }

    public Object setTimestamp(AMQShortString string, long datetime)
    {
        return setProperty(string, AMQType.TIMESTAMP.asTypedValue(datetime));
    }

    public Object setDecimal(AMQShortString string, BigDecimal decimal)
    {
        if (decimal.longValue() > Integer.MAX_VALUE)
        {
            throw new UnsupportedOperationException("AMQP does not support decimals larger than " + Integer.MAX_VALUE);
        }

        if (decimal.scale() > Byte.MAX_VALUE)
        {
            throw new UnsupportedOperationException("AMQP does not support decimal scales larger than " + Byte.MAX_VALUE);
        }

        return setProperty(string, AMQType.DECIMAL.asTypedValue(decimal));
    }

    public Object setVoid(AMQShortString string)
    {
        return setProperty(string, AMQType.VOID.asTypedValue(null));
    }

    /**
     * Associates a nested field table with the specified parameter name.
     *
     * @param string  The name of the parameter to store in the table.
     * @param ftValue The field table value to associate with the parameter name.
     *
     * @return The stored value.
     */
    public Object setFieldTable(String string, FieldTable ftValue)
    {
        return setFieldTable(AMQShortString.valueOf(string), ftValue);
    }

    /**
     * Associates a nested field table with the specified parameter name.
     *
     * @param string  The name of the parameter to store in the table.
     * @param ftValue The field table value to associate with the parameter name.
     *
     * @return The stored value.
     */
    public Object setFieldTable(AMQShortString string, FieldTable ftValue)
    {
        return setProperty(string, AMQType.FIELD_TABLE.asTypedValue(ftValue));
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
        else if (object instanceof Collection)
        {
            return setFieldArray(string, (Collection)object);
        }
        else if (object instanceof FieldTable)
        {
            return setFieldTable(string, (FieldTable) object);
        }
        else if (object instanceof Map)
        {
            return setFieldTable(string, FieldTable.convertToFieldTable((Map) object));
        }
        else if (object instanceof Date)
        {
            return setTimestamp(string, ((Date) object).getTime());
        }
        else if (object instanceof BigDecimal)
        {
            return setDecimal(string, (BigDecimal) object);
        }
        else if (object instanceof byte[])
        {
            return setBytes(string, (byte[]) object);
        }

        throw new AMQPInvalidClassException(AMQPInvalidClassException.INVALID_OBJECT_MSG + (object == null ? "null" : object.getClass()));
    }

    public boolean isNullStringValue(String name)
    {
        AMQTypedValue value = getProperty(AMQShortString.valueOf(name));

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

    public boolean itemExists(AMQShortString propertyName)
    {
        checkPropertyName(propertyName);
        initMapIfNecessary();

        return _properties.containsKey(propertyName);
    }

    public boolean itemExists(String string)
    {
        return itemExists(AMQShortString.valueOf(string));
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
        else if (propertyName.length() == 0)
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        if (_strictAMQP)
        {
            checkIdentiferFormat(propertyName);
        }
    }

    protected static void checkIdentiferFormat(AMQShortString propertyName)
    {
        // AMQP Spec: 4.2.5.5 Field Tables
        // Guidelines for implementers:
        // * Field names MUST start with a letter, '$' or '#' and may continue with
        // letters, '$' or '#', digits, or underlines, to a maximum length of 128
        // characters.
        // * The server SHOULD validate field names and upon receiving an invalid
        // field name, it SHOULD signal a connection exception with reply code
        // 503 (syntax error). Conformance test: amq_wlp_table_01.
        // * A peer MUST handle duplicate fields by using only the first instance.

        // AMQP length limit
        if (propertyName.length() > 128)
        {
            throw new IllegalArgumentException("AMQP limits property names to 128 characters");
        }

        // AMQ start character
        if (!(Character.isLetter(propertyName.charAt(0)) || (propertyName.charAt(0) == '$')
                    || (propertyName.charAt(0) == '#') || (propertyName.charAt(0) == '_'))) // Not official AMQP added for JMS.
        {
            throw new IllegalArgumentException("Identifier '" + propertyName
                + "' does not start with a valid AMQP start character");
        }
    }

    // *************************  Byte Buffer Processing

    public void writeToBuffer(DataOutput buffer) throws IOException
    {
        final boolean trace = _logger.isDebugEnabled();

        if (trace)
        {
            _logger.debug("FieldTable::writeToBuffer: Writing encoded length of " + getEncodedSize() + "...");
            if (_properties != null)
            {
                _logger.debug(_properties.toString());
            }
        }

        EncodingUtils.writeUnsignedInteger(buffer, getEncodedSize());

        putDataInBuffer(buffer);
    }

    public byte[] getDataAsBytes()
    {
        if(_encodedForm == null)
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try
            {
                putDataInBuffer(new DataOutputStream(baos));
                return baos.toByteArray();
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("IO Exception should never be thrown here");
            }

        }
        else if(_encodedFormOffset == 0 && _encodedSize == _encodedForm.length)
        {
            return _encodedForm.clone();
        }
        else
        {
            byte[] encodedCopy = new byte[(int) _encodedSize];
            System.arraycopy(_encodedForm,_encodedFormOffset,encodedCopy,0,(int)_encodedSize);
            return encodedCopy;
        }

    }

    public long getEncodedSize()
    {
        return _encodedSize;
    }

    private void recalculateEncodedSize()
    {

        int encodedSize = 0;
        if (_properties != null)
        {
            for (Map.Entry<AMQShortString, AMQTypedValue> e : _properties.entrySet())
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
        fieldTable.initMapIfNecessary();
        if (fieldTable._properties != null)
        {
            _encodedForm = null;
            _properties.putAll(fieldTable._properties);
            recalculateEncodedSize();
        }
    }

    public static Map<String, Object> convertToMap(final FieldTable fieldTable)
    {
        final Map<String, Object> map = new HashMap<String, Object>();

        if(fieldTable != null)
        {
            fieldTable.processOverElements(
                        new FieldTableElementProcessor()
                    {

                        public boolean processElement(String propertyName, AMQTypedValue value)
                        {
                            Object val = value.getValue();
                            if(val instanceof AMQShortString)
                            {
                                val = val.toString();
                            }
                            map.put(propertyName, val);
                            return true;
                        }

                        public Object getResult()
                        {
                            return map;
                        }
                    });
        }
        return map;
    }


    public static interface FieldTableElementProcessor
    {
        public boolean processElement(String propertyName, AMQTypedValue value);

        public Object getResult();
    }

    public Object processOverElements(FieldTableElementProcessor processor)
    {
        initMapIfNecessary();
        if (_properties != null)
        {
            for (Map.Entry<AMQShortString, AMQTypedValue> e : _properties.entrySet())
            {
                boolean result = processor.processElement(e.getKey().toString(), e.getValue());
                if (!result)
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
        return size() == 0;
    }

    public boolean containsKey(AMQShortString key)
    {
        initMapIfNecessary();

        return _properties.containsKey(key);
    }

    public boolean containsKey(String key)
    {
        return containsKey(AMQShortString.valueOf(key));
    }

    public Set<String> keys()
    {
        initMapIfNecessary();
        Set<String> keys = new LinkedHashSet<String>();
        for (AMQShortString key : _properties.keySet())
        {
            keys.add(key.toString());
        }

        return keys;
    }

    public Iterator<Map.Entry<AMQShortString, AMQTypedValue>> iterator()
    {
        initMapIfNecessary();
        return _properties.entrySet().iterator();
    }

    public Object get(String key)
    {
        return get(AMQShortString.valueOf(key));
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

        return remove(AMQShortString.valueOf(key));

    }

    public Object remove(AMQShortString key)
    {
        AMQTypedValue val = removeKey(key);

        return (val == null) ? null : val.getValue();

    }

    public AMQTypedValue removeKey(AMQShortString key)
    {
        initMapIfNecessary();
        _encodedForm = null;
        AMQTypedValue value = _properties.remove(key);
        if (value == null)
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

    private void putDataInBuffer(DataOutput buffer) throws IOException
    {

        if (_encodedForm != null)
        {
            buffer.write(_encodedForm,_encodedFormOffset,(int)_encodedSize);
        }
        else if (_properties != null)
        {
            final Iterator<Map.Entry<AMQShortString, AMQTypedValue>> it = _properties.entrySet().iterator();

            // If there are values then write out the encoded Size... could check _encodedSize != 0
            // write out the total length, which we have kept up to date as data is added

            while (it.hasNext())
            {
                final Map.Entry<AMQShortString, AMQTypedValue> me = it.next();
                try
                {
                    // Write the actual parameter name
                    EncodingUtils.writeShortStringBytes(buffer, me.getKey());
                    me.getValue().writeToBuffer(buffer);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void setFromBuffer() throws AMQFrameDecodingException, IOException
    {

        ByteArrayDataInput baid = new ByteArrayDataInput(_encodedForm, _encodedFormOffset, (int)_encodedSize);

        if (_encodedSize > 0)
        {


            _properties = new LinkedHashMap<AMQShortString, AMQTypedValue>(INITIAL_HASHMAP_CAPACITY);

            do
            {

                final AMQShortString key = baid.readAMQShortString();
                AMQTypedValue value = AMQTypedValue.readFromBuffer(baid);
                _properties.put(key, value);

            }
            while (baid.available() > 0);

        }

    }

    public int hashCode()
    {
        initMapIfNecessary();

        return _properties.hashCode();
    }

    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (o == null)
        {
            return false;
        }

        if (!(o instanceof FieldTable))
        {
            return false;
        }

        initMapIfNecessary();

        FieldTable f = (FieldTable) o;
        f.initMapIfNecessary();

        return _properties.equals(f._properties);
    }

    public static FieldTable convertToFieldTable(Map<String, Object> map)
    {
        if (map != null)
        {
            FieldTable table = new FieldTable();
            for(Map.Entry<String,Object> entry : map.entrySet())
            {
                table.put(AMQShortString.valueOf(entry.getKey()), entry.getValue());
            }

            return table;
        }
        else
        {
            return null;
        }
    }


}
