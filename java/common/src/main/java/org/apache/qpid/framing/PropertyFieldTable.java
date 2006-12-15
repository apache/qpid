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

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.HashMap;

//extends FieldTable
public class PropertyFieldTable implements FieldTable
{
    private static final Logger _logger = Logger.getLogger(PropertyFieldTable.class);

    private static final String BOOLEAN = "boolean";
    private static final String BYTE = "byte";
    private static final String BYTES = "bytes";
    private static final String SHORT = "short";
    private static final String INT = "int";
    private static final String LONG = "long";
    private static final String FLOAT = "float";
    private static final String DOUBLE = "double";
    private static final String STRING = "string";
    private static final String NULL_STRING = "nullstring";
    private static final String CHAR = "char";
    private static final String UNKNOWN = "unknown type";

    private static final String PROPERTY_FIELD_TABLE_CLOSE_XML = "</PropertyFieldTable>";
    private static final String PROPERTY_FIELD_TABLE_OPEN_XML = "<PropertyFieldTable>";
    private static final String BYTES_CLOSE_XML = "</" + BYTES + ">";
    private static final String BYTES_OPEN_XML_START = "<" + BYTES;

    private LinkedHashMap<String, Object> _properties;
    private LinkedHashMap<String, Prefix> _propertyNamesTypeMap;
    private long _encodedSize = 0;

    public static enum Prefix
    {
        //AMQP FieldTable Wire Types
        AMQP_DECIMAL_PROPERTY_PREFIX('D'),
        AMQP_UNSIGNED_SHORT_PROPERTY_PREFIX('S'),
        AMQP_UNSIGNED_INT_PROPERTY_PREFIX('I'),
        AMQP_UNSIGNED_LONG_PROPERTY_PREFIX('L'),
        AMQP_DOUBLE_EXTTENDED_PROPERTY_PREFIX('D'),

        AMQP_TIMESTAMP_PROPERTY_PREFIX('T'),
        AMQP_BINARY_PROPERTY_PREFIX('x'),

        //Strings
        AMQP_ASCII_STRING_PROPERTY_PREFIX('c'),
        AMQP_WIDE_STRING_PROPERTY_PREFIX('C'),
        AMQP_NULL_STRING_PROPERTY_PREFIX('n'),

        //Java Primative Types
        AMQP_BOOLEAN_PROPERTY_PREFIX('t'),
        AMQP_BYTE_PROPERTY_PREFIX('b'),
        AMQP_ASCII_CHARACTER_PROPERTY_PREFIX('k'),
        AMQP_SHORT_PROPERTY_PREFIX('s'),
        AMQP_INT_PROPERTY_PREFIX('i'),
        AMQP_LONG_PROPERTY_PREFIX('l'),
        AMQP_FLOAT_PROPERTY_PREFIX('f'),
        AMQP_DOUBLE_PROPERTY_PREFIX('d');

        private final char _identifier;

        Prefix(char identifier)
        {
            _identifier = identifier;
            _reverseTypeMap.put(identifier, this);
        }

        public final char identifier()
        {
            return _identifier;
        }

    }

    public static Map<Character, Prefix> _reverseTypeMap = new HashMap<Character, Prefix>();

    public PropertyFieldTable()
    {
        super();
        _properties = new LinkedHashMap<String, Object>();
        _propertyNamesTypeMap = new LinkedHashMap<String, Prefix>();
    }

    public PropertyFieldTable(String textFormat)
    {
        this();
        try
        {
            parsePropertyFieldTable(textFormat);
        }
        catch (Exception e)
        {
            _logger.warn("Unable to decode PropertyFieldTable format:" + textFormat, e);
            throw new IllegalArgumentException("Unable to decode PropertyFieldTable format:" + textFormat);
        }
    }

    /**
     * Construct a new field table.
     *
     * @param buffer the buffer from which to read data. The length byte must be read already
     * @param length the length of the field table. Must be > 0.
     * @throws AMQFrameDecodingException if there is an error decoding the table
     */
    public PropertyFieldTable(ByteBuffer buffer, long length) throws AMQFrameDecodingException
    {
        this();
        setFromBuffer(buffer, length);
    }

    // ************  Getters
    private Object get(String propertyName, Prefix prefix)
    {
        //Retrieve the type associated with this name
        Prefix type = _propertyNamesTypeMap.get(propertyName);

        if (type == null)
        {
            return null;
        }

        if (type.equals(prefix))
        {
            return _properties.get(propertyName);
        }
        else
        {
            return null;
        }
    }

    public Boolean getBoolean(String string)
    {
        Object o = get(string, Prefix.AMQP_BOOLEAN_PROPERTY_PREFIX);
        if (o != null && o instanceof Boolean)
        {
            return (Boolean) o;
        }
        else
        {
            return null;
        }
    }

    public Byte getByte(String string)
    {
        Object o = get(string, Prefix.AMQP_BYTE_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Byte) o;
        }
        else
        {
            return null;
        }
    }

    public Short getShort(String string)
    {
        Object o = get(string, Prefix.AMQP_SHORT_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Short) o;
        }
        else
        {
            return null;
        }
    }

    public Integer getInteger(String string)
    {
        Object o = get(string, Prefix.AMQP_INT_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Integer) o;
        }
        else
        {
            return null;
        }
    }

    public Long getLong(String string)
    {
        Object o = get(string, Prefix.AMQP_LONG_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Long) o;
        }
        else
        {
            return null;
        }
    }

    public Float getFloat(String string)
    {
        Object o = get(string, Prefix.AMQP_FLOAT_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Float) o;
        }
        else
        {
            return null;
        }
    }

    public Double getDouble(String string)
    {
        Object o = get(string, Prefix.AMQP_DOUBLE_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Double) o;
        }
        else
        {
            return null;
        }
    }

    public String getString(String string)
    {
        Object o = get(string, Prefix.AMQP_ASCII_STRING_PROPERTY_PREFIX);
        if (o != null)
        {
            return (String) o;
        }
        else
        {
            o = get(string, Prefix.AMQP_WIDE_STRING_PROPERTY_PREFIX);
            if (o != null)
            {
                return (String) o;
            }
            else
            {

                Prefix type = _propertyNamesTypeMap.get(string);

                if (type == null || type.equals(Prefix.AMQP_NULL_STRING_PROPERTY_PREFIX))
                {
                    return null;
                }
                else
                {
                    switch (type)
                    {
                        case AMQP_ASCII_STRING_PROPERTY_PREFIX:
                        case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                        case AMQP_BINARY_PROPERTY_PREFIX:
                            return null;
                        default:
                        case AMQP_BYTE_PROPERTY_PREFIX:
                        case AMQP_BOOLEAN_PROPERTY_PREFIX:
                        case AMQP_SHORT_PROPERTY_PREFIX:
                        case AMQP_INT_PROPERTY_PREFIX:
                        case AMQP_LONG_PROPERTY_PREFIX:
                        case AMQP_FLOAT_PROPERTY_PREFIX:
                        case AMQP_DOUBLE_PROPERTY_PREFIX:
                            return String.valueOf(_properties.get(string));
                        case AMQP_ASCII_CHARACTER_PROPERTY_PREFIX:
                            Object value = _properties.get(string);
                            if (value == null)
                            {
                                throw new NullPointerException("null char cannot be converted to String");
                            }
                            else
                            {
                                return String.valueOf(value);
                            }
                    }
                }
            }
        }
    }

    public Character getCharacter(String string)
    {
        Object o = get(string, Prefix.AMQP_ASCII_CHARACTER_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Character) o;
        }
        else
        {
            return null;
        }
    }

    public byte[] getBytes(String string)
    {
        Object o = get(string, Prefix.AMQP_BINARY_PROPERTY_PREFIX);
        if (o != null)
        {
            return (byte[]) o;
        }
        else
        {
            return null;
        }
    }

    public Object getObject(String string)
    {
        return _properties.get(string);
    }

    // ************  Setters

    public Object setBoolean(String string, boolean b)
    {
        return put(Prefix.AMQP_BOOLEAN_PROPERTY_PREFIX, string, b);
    }

    public Object setByte(String string, byte b)
    {
        return put(Prefix.AMQP_BYTE_PROPERTY_PREFIX, string, b);
    }

    public Object setShort(String string, short i)
    {
        return put(Prefix.AMQP_SHORT_PROPERTY_PREFIX, string, i);
    }

    public Object setInteger(String string, int i)
    {
        return put(Prefix.AMQP_INT_PROPERTY_PREFIX, string, i);
    }

    public Object setLong(String string, long l)
    {
        return put(Prefix.AMQP_LONG_PROPERTY_PREFIX, string, l);
    }

    public Object setFloat(String string, float v)
    {
        return put(Prefix.AMQP_FLOAT_PROPERTY_PREFIX, string, v);
    }

    public Object setDouble(String string, double v)
    {
        return put(Prefix.AMQP_DOUBLE_PROPERTY_PREFIX, string, v);
    }

    public Object setString(String string, String string1)
    {
        if (string1 == null)
        {
            return put(Prefix.AMQP_NULL_STRING_PROPERTY_PREFIX, string, null);
        }
        else
        {
            //FIXME: determine string encoding and set either WIDE or ASCII string
//            if ()
            {
                return put(Prefix.AMQP_WIDE_STRING_PROPERTY_PREFIX, string, string1);
            }
//            else
//            {
//                return put(Prefix.AMQP_ASCII_STRING_PROPERTY_PREFIX, string, string1);
//            }
        }
    }

    public Object setChar(String string, char c)
    {
        return put(Prefix.AMQP_ASCII_CHARACTER_PROPERTY_PREFIX, string, c);
    }

    public Object setBytes(String string, byte[] bytes)
    {
        return setBytes(string, bytes, 0, bytes.length);
    }

    public Object setBytes(String string, byte[] bytes, int start, int length)
    {
        return put(Prefix.AMQP_BINARY_PROPERTY_PREFIX, string, sizeByteArray(bytes, start, length));
    }

    private byte[] sizeByteArray(byte[] bytes, int start, int length)
    {
        byte[] resized = new byte[length];
        int newIndex = 0;
        for (int oldIndex = start; oldIndex < length; oldIndex++)
        {
            resized[newIndex] = bytes[oldIndex];
            newIndex++;
        }

        return resized;
    }


    public Object setObject(String string, Object object)
    {
        if (object instanceof Boolean)
        {
            return setBoolean(string, (Boolean) object);
        }
        else
        {
            if (object instanceof Byte)
            {
                return setByte(string, (Byte) object);
            }
            else
            {
                if (object instanceof Short)
                {
                    return setShort(string, (Short) object);
                }
                else
                {
                    if (object instanceof Integer)
                    {
                        return setInteger(string, (Integer) object);
                    }
                    else
                    {
                        if (object instanceof Long)
                        {
                            return setLong(string, (Long) object);
                        }
                        else
                        {
                            if (object instanceof Float)
                            {
                                return setFloat(string, (Float) object);
                            }
                            else
                            {
                                if (object instanceof Double)
                                {
                                    return setDouble(string, (Double) object);
                                }
                                else
                                {
                                    if (object instanceof String)
                                    {
                                        return setString(string, (String) object);
                                    }
                                    else
                                    {
                                        if (object instanceof Character)
                                        {
                                            return setChar(string, (Character) object);
                                        }
                                        else
                                        {
                                            if (object instanceof byte[])
                                            {
                                                return setBytes(string, (byte[]) object);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        throw new AMQPInvalidClassException("Only Primatives objects allowed Object is:" + object.getClass());
    }

    // ***** Methods

    public Enumeration getPropertyNames()
    {
        Vector<String> names = new Vector<String>();

        Iterator keys = _properties.keySet().iterator();

        while (keys.hasNext())
        {
            String key = (String) keys.next();

            names.add(key);
        }

        return names.elements();
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
        return valueOf(this);
    }

    public static String valueOf(PropertyFieldTable table)
    {
        StringBuffer buf = new StringBuffer(PROPERTY_FIELD_TABLE_OPEN_XML);

        final Iterator it = table._properties.entrySet().iterator();

        while (it.hasNext())
        {
            final Map.Entry entry = (Map.Entry) it.next();
            final String propertyName = (String) entry.getKey();

            buf.append('\n');
            buf.append(valueAsXML(table._propertyNamesTypeMap.get(propertyName), propertyName, entry.getValue()));
        }
        buf.append("\n");
        buf.append(PROPERTY_FIELD_TABLE_CLOSE_XML);

        return buf.toString();
    }

    private static String valueAsXML(Prefix type, String propertyName, Object value)
    {
        StringBuffer buf = new StringBuffer();
        // Start Tag
        buf.append(propertyXML(type, propertyName, true));

        // Value
        if (type.equals(Prefix.AMQP_BINARY_PROPERTY_PREFIX))
        {
            //remove '>'
            buf.deleteCharAt(buf.length() - 1);

            byte[] bytes = (byte[]) value;
            buf.append(" length='").append(bytes.length).append("'>");

            buf.append(byteArrayToXML(propertyName, bytes));
        }
        else
        {
            if (!type.equals(Prefix.AMQP_NULL_STRING_PROPERTY_PREFIX))
            {
                buf.append(String.valueOf(value));
            }
        }
        //End Tag
        buf.append(propertyXML(type, propertyName, false));

        return buf.toString();
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
              || propertyName.charAt(0) == '#'))
        {
            throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid AMQP start character");
        }
    }

    private static String propertyXML(Prefix type, String propertyName, boolean start)
    {
        StringBuffer buf = new StringBuffer();

        if (start)
        {
            buf.append("<");
        }
        else
        {
            buf.append("</");
        }

        switch (type)
        {
            case AMQP_BOOLEAN_PROPERTY_PREFIX:
                buf.append(BOOLEAN);
                break;
            case AMQP_BYTE_PROPERTY_PREFIX:
                buf.append(BYTE);
                break;
            case AMQP_BINARY_PROPERTY_PREFIX:
                buf.append(BYTES);
                break;
            case AMQP_SHORT_PROPERTY_PREFIX:
                buf.append(SHORT);
                break;
            case AMQP_INT_PROPERTY_PREFIX:
                buf.append(INT);
                break;
            case AMQP_LONG_PROPERTY_PREFIX:
                buf.append(LONG);
                break;
            case AMQP_FLOAT_PROPERTY_PREFIX:
                buf.append(FLOAT);
                break;
            case AMQP_DOUBLE_PROPERTY_PREFIX:
                buf.append(DOUBLE);
                break;
            case AMQP_NULL_STRING_PROPERTY_PREFIX:
                buf.append(NULL_STRING);
                break;
            case AMQP_ASCII_STRING_PROPERTY_PREFIX:
            case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                buf.append(STRING);
                break;
            case AMQP_ASCII_CHARACTER_PROPERTY_PREFIX:
                buf.append(CHAR);
                break;
            default:
                buf.append(UNKNOWN + " (identifier ").append(type.identifier()).append(")");
                break;
        }

        if (start)
        {
            buf.append(" name='").append(propertyName).append("'");
        }

        buf.append(">");

        return buf.toString();
    }

    private static String byteArrayToXML(String propertyName, byte[] bytes)
    {
        StringBuffer buf = new StringBuffer();

        for (int index = 0; index < bytes.length; index++)
        {
            buf.append("\n");
            buf.append(propertyXML(Prefix.AMQP_BYTE_PROPERTY_PREFIX, propertyName + "[" + index + "]", true));
            buf.append(bytes[index]);
            buf.append(propertyXML(Prefix.AMQP_BYTE_PROPERTY_PREFIX, propertyName + "[" + index + "]", false));
        }
        buf.append("\n");
        return buf.toString();
    }

    private void processBytesXMLLine(String xmlline)
    {
        String propertyName = xmlline.substring(xmlline.indexOf('\'') + 1,
                                                xmlline.indexOf('\'', xmlline.indexOf('\'') + 1));
        String value = xmlline.substring(xmlline.indexOf(">") + 1,
                                         xmlline.indexOf("</"));

        Integer index = Integer.parseInt(propertyName.substring(propertyName.lastIndexOf("[") + 1,
                                                                propertyName.lastIndexOf("]")));
        propertyName = propertyName.substring(0, propertyName.lastIndexOf("["));

        getBytes(propertyName)[index] = Byte.parseByte(value);
    }

    private void parsePropertyFieldTable(String textFormat)
    {
        StringTokenizer tokenizer = new StringTokenizer(textFormat, "\n");

        boolean finished = false;
        boolean processing = false;

        boolean processing_bytes = false;

        if (!tokenizer.hasMoreTokens())
        {
            throw new IllegalArgumentException("XML has no tokens to parse.");
        }

        while (tokenizer.hasMoreTokens())
        {
            String token = tokenizer.nextToken();

            if (token.equals(PROPERTY_FIELD_TABLE_CLOSE_XML))
            {
                processing = false;
                finished = true;
            }
            if (token.equals(BYTES_CLOSE_XML))
            {
                processing = false;
            }

            if (token.equals(BYTES_CLOSE_XML))
            {
                processing_bytes = false;
            }

            if (processing)
            {
                processXMLLine(token);
            }
            else if (processing_bytes)
            {
                processBytesXMLLine(token);
            }

            if (token.startsWith(BYTES_OPEN_XML_START))
            {
                processing_bytes = true;
                processing = false;
            }

            if (token.equals(PROPERTY_FIELD_TABLE_OPEN_XML) ||
                token.equals(BYTES_CLOSE_XML))
            {
                processing = true;
            }
        }

        if (!finished)
        {
            throw new IllegalArgumentException("XML was not in a valid format.");
        }

    }

    private void processXMLLine(String xmlline)
    {
        // <<type> name='<property>'><value></<type>>
        // <string name='message' >Message 99</string >

        String type = xmlline.substring(1, xmlline.indexOf(" "));

        String propertyName = xmlline.substring(xmlline.indexOf('\'') + 1,
                                                xmlline.indexOf('\'', xmlline.indexOf('\'') + 1));

        String value = "";

        if (!type.equals(BYTES))
        {
            value = xmlline.substring(xmlline.indexOf(">") + 1,
                                      xmlline.indexOf("</"));
        }

        if (type.equals(BOOLEAN))
        {
            setBoolean(propertyName, Boolean.parseBoolean(value));
        }
        if (type.equals(BYTE))
        {
            setByte(propertyName, Byte.parseByte(value));
        }
        if (type.equals(BYTES))
        {
            int headerEnd = xmlline.indexOf('>');
            String bytesHeader = xmlline.substring(0, headerEnd);

            //Extract length value
            Integer length = Integer.parseInt(bytesHeader.substring(
                    bytesHeader.lastIndexOf("=") + 2
                    , bytesHeader.lastIndexOf("'")));


            byte[] bytes = new byte[length];
            setBytes(propertyName, bytes);

            //Check if the line contains all the byte values
            // This is needed as the XMLLine sent across the wire is the bytes value

            int byteStart = xmlline.indexOf('<', headerEnd);

            //Don't think this is required.
            if (byteStart > 0)
            {
                while (!xmlline.startsWith(BYTES_CLOSE_XML, byteStart))
                {
                    //This should be the next byte line
                    int bytePrefixEnd = xmlline.indexOf('>', byteStart) + 1;
                    int byteEnd = xmlline.indexOf('>', bytePrefixEnd) + 1;

                    String byteline = xmlline.substring(byteStart, byteEnd);

                    processBytesXMLLine(byteline);

                    byteStart = xmlline.indexOf('<', byteEnd);
                }
            }

        }
        if (type.equals(SHORT))
        {
            setShort(propertyName, Short.parseShort(value));
        }
        if (type.equals(INT))
        {
            setInteger(propertyName, Integer.parseInt(value));
        }
        if (type.equals(LONG))
        {
            setLong(propertyName, Long.parseLong(value));
        }
        if (type.equals(FLOAT))
        {
            setFloat(propertyName, Float.parseFloat(value));
        }
        if (type.equals(DOUBLE))
        {
            setDouble(propertyName, Double.parseDouble(value));
        }
        if (type.equals(STRING) || type.equals(NULL_STRING))
        {
            if (type.equals(NULL_STRING))
            {
                value = null;
            }
            setString(propertyName, value);
        }
        if (type.equals(CHAR))
        {
            setChar(propertyName, value.charAt(0));
        }
        if (type.equals(UNKNOWN))
        {
            _logger.warn("Ignoring unknown property value:" + xmlline);
        }
    }

    // *************************  Byte Buffer Processing

    public void writeToBuffer(ByteBuffer buffer)
    {
        final boolean trace = _logger.isTraceEnabled();

        if (trace)
        {
            _logger.trace("FieldTable::writeToBuffer: Writing encoded size of " + _encodedSize + "...");
        }

        EncodingUtils.writeUnsignedInteger(buffer, _encodedSize);

        putDataInBuffer(buffer);
    }

    public byte[] getDataAsBytes()
    {
        final ByteBuffer buffer = ByteBuffer.allocate((int) _encodedSize); // FIXME XXX: Is cast a problem?

        putDataInBuffer(buffer);

        final byte[] result = new byte[(int) _encodedSize];
        buffer.flip();
        buffer.get(result);
        buffer.release();
        return result;
    }


    public int size()
    {
        return _properties.size();
    }

    public boolean isEmpty()
    {
        return _properties.isEmpty();
    }

    public boolean containsKey(Object key)
    {
        return _properties.containsKey(key);
    }

    public boolean containsValue(Object value)
    {
        return _properties.containsValue(value);
    }

    public Object get(Object key)
    {
        return _properties.get(key);
    }


    public Object put(Object key, Object value)
    {
        return setObject(key.toString(), value);
    }

    protected Object put(Prefix type, String propertyName, Object value)
    {
        checkPropertyName(propertyName);

        //remove the previous value
        Object previous = remove(propertyName);


        if (_logger.isTraceEnabled())
        {
            int valueSize = 0;
            if (value != null)
            {
                valueSize = getEncodingSize(type, value);
            }
            _logger.trace("Put:" + propertyName +
                          " encoding size Now:" + _encodedSize +
                          " name size= " + EncodingUtils.encodedShortStringLength(propertyName) +
                          " value size= " + valueSize);
        }

        //Add the size of the propertyName plus one for the type identifier
        _encodedSize += EncodingUtils.encodedShortStringLength(propertyName) + 1;

        if (value != null)
        {
            //Add the size of the content
            _encodedSize += getEncodingSize(type, value);
        }

        //Store new values
        _propertyNamesTypeMap.put(propertyName, type);
        _properties.put(propertyName, value);

        return previous;
    }

    public Object remove(Object key)
    {
        if (_properties.containsKey(key))
        {
            final Object value = _properties.remove(key);
            Prefix type = _propertyNamesTypeMap.remove(key);
            // plus one for the type
            _encodedSize -= EncodingUtils.encodedShortStringLength(((String) key)) + 1;

            // This check is, for now, unnecessary (we don't store null values).
            if (value != null)
            {
                _encodedSize -= getEncodingSize(type, value);
            }
            return value;
        }
        else
        {
            return null;
        }
    }

    public void putAll(Map t)
    {
        Iterator it = t.keySet().iterator();

        while (it.hasNext())
        {
            Object key = it.next();
            put(key, t.get(key));
        }
    }

    public void clear()
    {
        _encodedSize = 0;
        _properties.clear();
        _propertyNamesTypeMap.clear();
    }

    public Set keySet()
    {
        return _properties.keySet();
    }

    public Collection values()
    {
        return _properties.values();
    }

    public Set entrySet()
    {
        return _properties.entrySet();
    }

    public long getEncodedSize()
    {
        return _encodedSize;
    }


    private void putDataInBuffer(ByteBuffer buffer)
    {

        final Iterator it = _properties.entrySet().iterator();

        //If there are values then write out the encoded Size... could check _encodedSize != 0
        // write out the total length, which we have kept up to date as data is added


        while (it.hasNext())
        {
            Map.Entry me = (Map.Entry) it.next();
            String propertyName = (String) me.getKey();

            //The type value
            Prefix type = _propertyNamesTypeMap.get(propertyName);

            Object value = me.getValue();
            try
            {
                if (_logger.isTraceEnabled())
                {
                    _logger.trace("Writing Property:" + propertyName +
                                  " Type:" + type +
                                  " Value:" + value);
                    _logger.trace("Buffer Position:" + buffer.position() +
                                  " Remaining:" + buffer.remaining());
                }

                //Write the actual parameter name
                EncodingUtils.writeShortStringBytes(buffer, propertyName);

                switch (type)
                {
                    case AMQP_BOOLEAN_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_BOOLEAN_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeBoolean(buffer, (Boolean) value);
                        break;
                    case AMQP_BYTE_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_BYTE_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeByte(buffer, (Byte) value);
                        break;
                    case AMQP_SHORT_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_SHORT_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeShort(buffer, (Short) value);
                        break;
                    case AMQP_INT_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_INT_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeInteger(buffer, (Integer) value);
                        break;
                    case AMQP_UNSIGNED_INT_PROPERTY_PREFIX: // Currently we don't create these
                        buffer.put((byte) Prefix.AMQP_UNSIGNED_INT_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeUnsignedInteger(buffer, (Long) value);
                        break;
                    case AMQP_LONG_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_LONG_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeLong(buffer, (Long) value);
                        break;
                    case AMQP_FLOAT_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_FLOAT_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeFloat(buffer, (Float) value);
                        break;
                    case AMQP_DOUBLE_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_DOUBLE_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeDouble(buffer, (Double) value);
                        break;
                    case AMQP_NULL_STRING_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_NULL_STRING_PROPERTY_PREFIX.identifier());
                        break;
                    case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_WIDE_STRING_PROPERTY_PREFIX.identifier());
                        // FIXME: use proper charset encoder
                        EncodingUtils.writeLongStringBytes(buffer, (String) value);
                        break;
                    case AMQP_ASCII_STRING_PROPERTY_PREFIX:
                        //This is a simple ASCII string
                        buffer.put((byte) Prefix.AMQP_ASCII_STRING_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeLongStringBytes(buffer, (String) value);
                        break;
                    case AMQP_ASCII_CHARACTER_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_ASCII_CHARACTER_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeChar(buffer, (Character) value);
                        break;
                    case AMQP_BINARY_PROPERTY_PREFIX:
                        buffer.put((byte) Prefix.AMQP_BINARY_PROPERTY_PREFIX.identifier());
                        EncodingUtils.writeBytes(buffer, (byte[]) value);
                        break;
                    default:
                    {
                        // Should never get here
                        throw new IllegalArgumentException("Key '" + propertyName + "': Unsupported type in field table, type: " + ((value == null) ? "null-object" : value.getClass()));
                    }
                }
            }
            catch (Exception e)
            {
                if (_logger.isTraceEnabled())
                {
                    _logger.trace("Exception thrown:" + e);
                    _logger.trace("Writing Property:" + propertyName +
                                  " Type:" + type +
                                  " Value:" + value);
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

            byte iType = buffer.get();

            Prefix type = _reverseTypeMap.get((char) iType);

            Object value;

            switch (type)
            {
                case AMQP_BOOLEAN_PROPERTY_PREFIX:
                    value = EncodingUtils.readBoolean(buffer);
                    break;
                case AMQP_BYTE_PROPERTY_PREFIX:
                    value = EncodingUtils.readByte(buffer);
                    break;
                case AMQP_SHORT_PROPERTY_PREFIX:
                    value = EncodingUtils.readShort(buffer);
                    break;
                case AMQP_INT_PROPERTY_PREFIX:
                    value = EncodingUtils.readInteger(buffer);
                    break;
                case AMQP_UNSIGNED_INT_PROPERTY_PREFIX:// This will only fit in a long
                    //Change this type for java lookups
                    type = Prefix.AMQP_LONG_PROPERTY_PREFIX;
                case AMQP_LONG_PROPERTY_PREFIX:
                    value = EncodingUtils.readLong(buffer);
                    break;
                case AMQP_FLOAT_PROPERTY_PREFIX:
                    value = EncodingUtils.readFloat(buffer);
                    break;
                case AMQP_DOUBLE_PROPERTY_PREFIX:
                    value = EncodingUtils.readDouble(buffer);
                    break;
                case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                    // FIXME: use proper charset encoder
                case AMQP_ASCII_STRING_PROPERTY_PREFIX:
                    value = EncodingUtils.readLongString(buffer);
                    break;
                case AMQP_NULL_STRING_PROPERTY_PREFIX:
                    value = null;
                    break;
                case AMQP_ASCII_CHARACTER_PROPERTY_PREFIX:
                    value = EncodingUtils.readChar((buffer));
                    break;
                case AMQP_BINARY_PROPERTY_PREFIX:
                    value = EncodingUtils.readBytes(buffer);
                    break;
                default:
                    String msg = "Field '" + key + "' - unsupported field table type: " + type + ".";
                    //some extra trace information...
                    msg += " (" + iType + "), length=" + length + ", sizeRead=" + sizeRead + ", sizeRemaining=" + sizeRemaining;
                    throw new AMQFrameDecodingException(msg);
            }

            sizeRead += (sizeRemaining - buffer.remaining());

            if (trace)
            {
                _logger.trace("FieldTable::PropFieldTable(buffer," + length + "): Read type '" + type + "', key '" + key + "', value '" + value + "' (now read " + sizeRead + " of " + length + " encoded bytes)...");
            }

            put(type, key, value);
        }

        if (trace)
        {
            _logger.trace("FieldTable::FieldTable(buffer," + length + "): Done.");
        }
    }

    /**
     * @param type  the type to calucluate encoding for
     * @param value the property value
     * @return integer
     */
    private static int getEncodingSize(Prefix type, Object value)
    {
        int encodingSize = 0;

        switch (type)
        {
            case AMQP_BOOLEAN_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedBooleanLength();
                break;
            case AMQP_BYTE_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedByteLength();
                break;
            case AMQP_SHORT_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedShortLength();
                break;
            case AMQP_INT_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedIntegerLength();
                break;
            case AMQP_LONG_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedLongLength();
                break;
            case AMQP_FLOAT_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedFloatLength();
                break;
            case AMQP_DOUBLE_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedDoubleLength();
                break;
            case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                // FIXME: use proper charset encoder
            case AMQP_ASCII_STRING_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedLongStringLength((String) value);
                break;
//            This is not required as this method is never called if the value is null
//            case AMQP_NULL_STRING_PROPERTY_PREFIX:
//                // There is no need for additional size beyond the prefix
//                break;
            case AMQP_ASCII_CHARACTER_PROPERTY_PREFIX:
                encodingSize = EncodingUtils.encodedCharLength();
                break;
            case AMQP_BINARY_PROPERTY_PREFIX:
                encodingSize = 1 + ((byte[]) value).length;
                break;
            default:
                throw new IllegalArgumentException("Unsupported type in field table: " + value.getClass());
        }

        // the extra byte for the type indicator is calculated in the name
        return encodingSize;
    }
}
