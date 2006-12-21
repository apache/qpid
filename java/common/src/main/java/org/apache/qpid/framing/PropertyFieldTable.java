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

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

//extends FieldTable
public class PropertyFieldTable implements FieldTable, Map
{
    private static final Logger _logger = Logger.getLogger(PropertyFieldTable.class);

    public static final char AMQP_DECIMAL_PROPERTY_PREFIX = 'D';
    public static final char AMQP_UNSIGNEDINT_PROPERTY_PREFIX = 'I';
    public static final char AMQP_TIMESTAMP_PROPERTY_PREFIX = 'T';
    public static final char AMQP_STRING_PROPERTY_PREFIX = 'S';
    public static final char AMQP_ASCII_STRING_PROPERTY_PREFIX = 'c';
    public static final char AMQP_WIDE_STRING_PROPERTY_PREFIX = 'C';
    public static final char AMQP_BINARY_PROPERTY_PREFIX = 'x';

    public static final char BOOLEAN_PROPERTY_PREFIX = 't';
    public static final char BYTE_PROPERTY_PREFIX = 'b';
    public static final char SHORT_PROPERTY_PREFIX = 's';
    public static final char INT_PROPERTY_PREFIX = 'i';
    public static final char LONG_PROPERTY_PREFIX = 'l';
    public static final char FLOAT_PROPERTY_PREFIX = 'f';
    public static final char DOUBLE_PROPERTY_PREFIX = 'd';
    public static final char NULL_STRING_PROPERTY_PREFIX = 'n';

    public static final char STRING_PROPERTY_PREFIX = AMQP_STRING_PROPERTY_PREFIX;
    public static final char CHAR_PROPERTY_PREFIX = AMQP_ASCII_STRING_PROPERTY_PREFIX;
    public static final char BYTES_PROPERTY_PREFIX = AMQP_BINARY_PROPERTY_PREFIX;

    //Our custom prefix for encoding across the wire
    private static final char XML_PROPERTY_PREFIX = 'X';

    private static final String BOOLEAN = "boolean";
    private static final String BYTE = "byte";
    private static final String BYTES = "bytes";
    private static final String SHORT = "short";
    private static final String INT = "int";
    private static final String LONG = "long";
    private static final String FLOAT = "float";
    private static final String DOUBLE = "double";
    private static final String STRING = "string";
    private static final String CHAR = "char";
    private static final String UNKNOWN = "unknown type";

    private static final String PROPERTY_FIELD_TABLE_CLOSE_XML = "</PropertyFieldTable>";
    private static final String PROPERTY_FIELD_TABLE_OPEN_XML = "<PropertyFieldTable>";
    private static final String BYTES_CLOSE_XML = "</" + BYTES + ">";
    private static final String BYTES_OPEN_XML_START = "<" + BYTES;

    private LinkedHashMap<String, Object> _properties;
    private LinkedHashMap<String, String> _propertyNamesTypeMap;
    private long _encodedSize = 0;//EncodingUtils.unsignedIntegerLength();

    public PropertyFieldTable()
    {
        super();
        _properties = new LinkedHashMap<String, Object>();
        _propertyNamesTypeMap = new LinkedHashMap<String, String>();
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
            _logger.error("Unable to decode PropertyFieldTable format:" + textFormat, e);
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

    private Object get(String propertyName, char prefix)
    {
        String type = _propertyNamesTypeMap.get(propertyName);

        if (type == null)
        {
            return null;
        }

        if (type.equals("" + prefix))
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
        Object o = get(string, BOOLEAN_PROPERTY_PREFIX);
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
        Object o = get(string, BYTE_PROPERTY_PREFIX);
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
        Object o = get(string, SHORT_PROPERTY_PREFIX);
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
        Object o = get(string, INT_PROPERTY_PREFIX);
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
        Object o = get(string, LONG_PROPERTY_PREFIX);
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
        Object o = get(string, FLOAT_PROPERTY_PREFIX);
        if (o != null)
        {
            return (Float) o;
        }
        else
        {
            return null;     //Float.valueOf(null); ???
        }
    }

    public Double getDouble(String string)
    {
        Object o = get(string, DOUBLE_PROPERTY_PREFIX);
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
        Object o = get(string, STRING_PROPERTY_PREFIX);
        if (o != null)
        {
            return (String) o;
        }
        else
        {


            String type = _propertyNamesTypeMap.get(string);

            if (type == null || type.equals("" + NULL_STRING_PROPERTY_PREFIX))
            {
                return null;
            }
            else
            {
                char itype = type.charAt(0);

                Object value = _properties.get(string);

                switch (itype)
                {
                    case STRING_PROPERTY_PREFIX:
                    case BYTES_PROPERTY_PREFIX:
                        return null;
                    default:
                    case BYTE_PROPERTY_PREFIX:
                    case BOOLEAN_PROPERTY_PREFIX:
                    case SHORT_PROPERTY_PREFIX:
                    case INT_PROPERTY_PREFIX:
                    case LONG_PROPERTY_PREFIX:
                    case FLOAT_PROPERTY_PREFIX:
                    case DOUBLE_PROPERTY_PREFIX:
                        return String.valueOf(value);
                    case CHAR_PROPERTY_PREFIX:
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

    public Character getCharacter(String string)
    {
        Object o = get(string, CHAR_PROPERTY_PREFIX);
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
        Object o = get(string, BYTES_PROPERTY_PREFIX);
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
        return put(BOOLEAN_PROPERTY_PREFIX + string, b);
    }

    public Object setByte(String string, byte b)
    {
        return put(BYTE_PROPERTY_PREFIX + string, b);
    }

    public Object setShort(String string, short i)
    {
        return put(SHORT_PROPERTY_PREFIX + string, i);
    }

    public Object setInteger(String string, int i)
    {
        return put(INT_PROPERTY_PREFIX + string, i);
    }

    public Object setLong(String string, long l)
    {
        return put(LONG_PROPERTY_PREFIX + string, l);
    }

    public Object setFloat(String string, float v)
    {
        return put(FLOAT_PROPERTY_PREFIX + string, v);
    }

    public Object setDouble(String string, double v)
    {
        return put(DOUBLE_PROPERTY_PREFIX + string, v);
    }

    public Object setString(String string, String string1)
    {
        if (string1 == null)
        {
            return put(NULL_STRING_PROPERTY_PREFIX + string, null);
        }
        else
        {
            return put(STRING_PROPERTY_PREFIX + string, string1);
        }
    }

    public Object setChar(String string, char c)
    {
        return put(CHAR_PROPERTY_PREFIX + string, c);
    }

    public Object setBytes(String string, byte[] bytes)
    {
        return setBytes(string, bytes, 0, bytes.length);
    }

    public Object setBytes(String string, byte[] bytes, int start, int length)
    {
        return put(BYTES_PROPERTY_PREFIX + string, sizeByteArray(bytes, start, length));
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
        return null;
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
        return _propertyNamesTypeMap.containsKey(propertyName);
    }

    public boolean itemExists(String string)
    {
        Iterator keys = _properties.keySet().iterator();

        while (keys.hasNext())
        {
            String key = (String) keys.next();

            if (key.endsWith(string))
            {
                return true;
            }
        }
        return false;
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
            if (propertyName == null)
            {
                buf.append("\nInternal error: Property with NULL key defined");
            }
            else
            {
                buf.append('\n');

                buf.append(valueAsXML(table._propertyNamesTypeMap.get(propertyName) + propertyName, entry.getValue()));
            }
        }
        buf.append("\n");
        buf.append(PROPERTY_FIELD_TABLE_CLOSE_XML);

        return buf.toString();
    }

    private static String valueAsXML(String name, Object value)
    {
        char propertyPrefix = name.charAt(0);
        String propertyName = name.substring(1);


        StringBuffer buf = new StringBuffer();
        // Start Tag
        buf.append(propertyXML(name, true));

        // Value
        if (propertyPrefix == BYTES_PROPERTY_PREFIX)
        {
            //remove '>'
            buf.deleteCharAt(buf.length() - 1);

            byte[] bytes = (byte[]) value;
            buf.append(" length='").append(bytes.length).append("'>");

            buf.append(byteArrayToXML(propertyName, bytes));
        }
        else
        {
            buf.append(String.valueOf(value));
        }

        //End Tag
        buf.append(propertyXML(name, false));

        return buf.toString();
    }

    private Object checkPropertyName(String name)
    {
        String propertyName = name.substring(1);
        char propertyPrefix = name.charAt(0);

        Object previous = null;

        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if ("".equals(propertyName))
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        checkIdentiferFormat(propertyName);

        String currentValue = _propertyNamesTypeMap.get(propertyName);

        if (currentValue != null)
        {
            previous = _properties.remove(currentValue + propertyName);

            // If we are in effect deleting the value (see comment on null values being deleted
            // below) then we also need to remove the name from the encoding length.
            if (previous == null)
            {
                _encodedSize -= EncodingUtils.encodedShortStringLength(propertyName);
            }

            // FIXME: Should be able to short-cut this process if the old and new values are
            // the same object and/or type and size...
            _encodedSize -= getEncodingSize(currentValue + propertyName, previous);
        }

        _propertyNamesTypeMap.put(propertyName, "" + propertyPrefix);

        return previous;
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

//        JMS requirements 3.5.1 Property Names
//        Identifiers:
//        - An identifier is an unlimited-length character sequence that must begin
//          with a Java identifier start character; all following characters must be Java
//          identifier part characters. An identifier start character is any character for
//          which the method Character.isJavaIdentifierStart returns true. This includes
//          '_' and '$'. An identifier part character is any character for which the
//          method Character.isJavaIdentifierPart returns true.
//        - Identifiers cannot be the names NULL, TRUE, or FALSE.
//        – Identifiers cannot be NOT, AND, OR, BETWEEN, LIKE, IN, IS, or
//          ESCAPE.
//        – Identifiers are either header field references or property references. The
//          type of a property value in a message selector corresponds to the type
//          used to set the property. If a property that does not exist in a message is
//          referenced, its value is NULL. The semantics of evaluating NULL values
//          in a selector are described in Section 3.8.1.2, “Null Values.”
//        – The conversions that apply to the get methods for properties do not
//          apply when a property is used in a message selector expression. For
//          example, suppose you set a property as a string value, as in the
//          following:
//              myMessage.setStringProperty("NumberOfOrders", "2");
//          The following expression in a message selector would evaluate to false,
//          because a string cannot be used in an arithmetic expression:
//          "NumberOfOrders > 1"
//        – Identifiers are case sensitive.
//        – Message header field references are restricted to JMSDeliveryMode,
//          JMSPriority, JMSMessageID, JMSTimestamp, JMSCorrelationID, and
//          JMSType. JMSMessageID, JMSCorrelationID, and JMSType values may be
//          null and if so are treated as a NULL value.


        if (Boolean.getBoolean("strict-jms"))
        {
            // JMS start character
            if (!(Character.isJavaIdentifierStart(propertyName.charAt(0))))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' does not start with a valid JMS identifier start character");
            }

            // JMS part character
            int length = propertyName.length();
            for (int c = 1; c < length; c++)
            {
                if (!(Character.isJavaIdentifierPart(propertyName.charAt(c))))
                {
                    throw new IllegalArgumentException("Identifier '" + propertyName + "' contains an invalid JMS identifier character");
                }
            }

            // JMS invalid names
            if (!(propertyName.equals("NULL")
                  || propertyName.equals("TRUE")
                  || propertyName.equals("FALSE")
                  || propertyName.equals("NOT")
                  || propertyName.equals("AND")
                  || propertyName.equals("OR")
                  || propertyName.equals("BETWEEN")
                  || propertyName.equals("LIKE")
                  || propertyName.equals("IN")
                  || propertyName.equals("IS")
                  || propertyName.equals("ESCAPE")))
            {
                throw new IllegalArgumentException("Identifier '" + propertyName + "' is not allowed in JMS");
            }

        }
        else
        {
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

    }

    private static String propertyXML(String name, boolean start)
    {
        char propertyPrefix = name.charAt(0);
        String propertyName = name.substring(1);

        StringBuffer buf = new StringBuffer();

        if (start)
        {
            buf.append("<");
        }
        else
        {
            buf.append("</");
        }

        switch (propertyPrefix)
        {
            case BOOLEAN_PROPERTY_PREFIX:
                buf.append(BOOLEAN);
                break;
            case BYTE_PROPERTY_PREFIX:
                buf.append(BYTE);
                break;
            case BYTES_PROPERTY_PREFIX:
                buf.append(BYTES);
                break;
            case SHORT_PROPERTY_PREFIX:
                buf.append(SHORT);
                break;
            case INT_PROPERTY_PREFIX:
                buf.append(INT);
                break;
            case LONG_PROPERTY_PREFIX:
                buf.append(LONG);
                break;
            case FLOAT_PROPERTY_PREFIX:
                buf.append(FLOAT);
                break;
            case DOUBLE_PROPERTY_PREFIX:
                buf.append(DOUBLE);
                break;
            case STRING_PROPERTY_PREFIX:
                buf.append(STRING);
                break;
            case CHAR_PROPERTY_PREFIX:
                buf.append(CHAR);
                break;
            default:
                buf.append(UNKNOWN + " (identifier ").append(propertyPrefix).append(")");
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
            buf.append(propertyXML(BYTE_PROPERTY_PREFIX + propertyName + "[" + index + "]", true));
            buf.append(bytes[index]);
            buf.append(propertyXML(BYTE_PROPERTY_PREFIX + propertyName + "[" + index + "]", false));
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

        boolean processing = false;

        boolean processing_bytes = false;

        while (tokenizer.hasMoreTokens())
        {
            String token = tokenizer.nextToken();

            if (token.equals(PROPERTY_FIELD_TABLE_CLOSE_XML)
                || token.equals(BYTES_CLOSE_XML))
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
        if (type.equals(STRING))
        {
            setString(propertyName, value);
        }
        if (type.equals(CHAR))
        {
            setChar(propertyName, value.charAt(0));
        }
        if (type.equals(UNKNOWN))
        {
            _logger.error("Ignoring unknown property value:" + xmlline);
        }
    }

    // *************************  Byte Buffer Processing

    public void writeToBuffer(ByteBuffer buffer)
    {
        final boolean debug = _logger.isDebugEnabled();

        if (debug)
        {
            _logger.debug("FieldTable::writeToBuffer: Writing encoded size of " + _encodedSize + "...");
        }

        EncodingUtils.writeUnsignedInteger(buffer, _encodedSize);
        //EncodingUtils.writeLong(buffer, _encodedSize);


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

    protected Object put(String key, Object value)
    {
        Object previous = checkPropertyName(key);


        String propertyName = key.substring(1);
        char propertyPrefix = _propertyNamesTypeMap.get(propertyName).charAt(0);

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Put:" + propertyName +
                          " encoding Now:" + _encodedSize +
                          " name size= " + EncodingUtils.encodedShortStringLength(propertyName) +
                          " value size= " + getEncodingSize(key, value));
        }

        // This prevents the item from being sent.
        // JMS needs these propertyNames for lookups.
        //if (value != null)
        {
            //Add the size of the propertyName
            _encodedSize += EncodingUtils.encodedShortStringLength(propertyName);

            // For now: Setting a null value is the equivalent of deleting it.
            // This is ambiguous in the JMS spec and needs thrashing out and potentially
            // testing against other implementations.

            //Add the size of the content
            _encodedSize += getEncodingSize(key, value);
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Put: new encodingSize " + _encodedSize);
        }

        _properties.put((String) propertyName, value);

        return previous;
    }

    public Object remove(Object key)
    {
        if (key instanceof String)
        {
            throw new IllegalArgumentException("Property key be a string");
        }

        char propertyPrefix = ((String) key).charAt(0);

        if (_properties.containsKey(key))
        {
            final Object value = _properties.remove(key);
            // plus one for the type
            _encodedSize -= EncodingUtils.encodedShortStringLength(((String) key));

            // This check is, for now, unnecessary (we don't store null values).
            if (value != null)
            {
                _encodedSize -= getEncodingSize(propertyPrefix + (String) key, value);
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
            char propertyPrefix = _propertyNamesTypeMap.get(propertyName).charAt(0);

            Object value = me.getValue();
            try
            {

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Writing Property:" + propertyName +
                                  " Type:" + propertyPrefix +
                                  " Value:" + value);
                    _logger.debug("Buffer Position:" + buffer.position() +
                                  " Remaining:" + buffer.remaining());
                }

                //The actual param name skipping type
                EncodingUtils.writeShortStringBytes(buffer, propertyName);


                switch (propertyPrefix)
                {

                    case BOOLEAN_PROPERTY_PREFIX:
                        buffer.put((byte) BOOLEAN_PROPERTY_PREFIX);
                        EncodingUtils.writeBoolean(buffer, (Boolean) value);
                        break;
                    case BYTE_PROPERTY_PREFIX:
                        buffer.put((byte) BYTE_PROPERTY_PREFIX);
                        EncodingUtils.writeByte(buffer, (Byte) value);
                        break;
                    case SHORT_PROPERTY_PREFIX:
                        buffer.put((byte) SHORT_PROPERTY_PREFIX);
                        EncodingUtils.writeShort(buffer, (Short) value);
                        break;
                    case INT_PROPERTY_PREFIX:
                        buffer.put((byte) INT_PROPERTY_PREFIX);
                        EncodingUtils.writeInteger(buffer, (Integer) value);
                        break;
                    case AMQP_UNSIGNEDINT_PROPERTY_PREFIX: // Currently we don't create these
                        buffer.put((byte) AMQP_UNSIGNEDINT_PROPERTY_PREFIX);
                        EncodingUtils.writeUnsignedInteger(buffer, (Long) value);
                        break;
                    case LONG_PROPERTY_PREFIX:
                        buffer.put((byte) LONG_PROPERTY_PREFIX);
                        EncodingUtils.writeLong(buffer, (Long) value);
                        break;
                    case FLOAT_PROPERTY_PREFIX:
                        buffer.put((byte) FLOAT_PROPERTY_PREFIX);
                        EncodingUtils.writeFloat(buffer, (Float) value);
                        break;
                    case DOUBLE_PROPERTY_PREFIX:
                        buffer.put((byte) DOUBLE_PROPERTY_PREFIX);
                        EncodingUtils.writeDouble(buffer, (Double) value);
                        break;
                    case NULL_STRING_PROPERTY_PREFIX:
                        buffer.put((byte) NULL_STRING_PROPERTY_PREFIX);
                        break;
                    case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                        //case AMQP_STRING_PROPERTY_PREFIX:
                    case STRING_PROPERTY_PREFIX:
                        // TODO: look at using proper charset encoder
                        buffer.put((byte) STRING_PROPERTY_PREFIX);
                        EncodingUtils.writeLongStringBytes(buffer, (String) value);
                        break;

                        //case AMQP_ASCII_STRING_PROPERTY_PREFIX:
                    case CHAR_PROPERTY_PREFIX:
                        // TODO: look at using proper charset encoder
                        buffer.put((byte) CHAR_PROPERTY_PREFIX);
                        EncodingUtils.writeShortStringBytes(buffer, "" + (Character) value);
                        break;

                    case BYTES_PROPERTY_PREFIX:
                        buffer.put((byte) BYTES_PROPERTY_PREFIX);
                        EncodingUtils.writeBytes(buffer, (byte[]) value);
                        break;

                    case XML_PROPERTY_PREFIX:
                        // Encode as XML
                        buffer.put((byte) XML_PROPERTY_PREFIX);
                        EncodingUtils.writeLongStringBytes(buffer, valueAsXML(propertyPrefix + propertyName, value));
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
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Exception thrown:" + e);
                    _logger.debug("Writing Property:" + propertyName +
                                  " Type:" + propertyPrefix +
                                  " Value:" + value);
                    _logger.debug("Buffer Position:" + buffer.position() +
                                  " Remaining:" + buffer.remaining());
                }
                throw new RuntimeException(e);
            }
        }

    }


    public void setFromBuffer(ByteBuffer buffer, long length) throws AMQFrameDecodingException
    {
        final boolean debug = _logger.isDebugEnabled();

        int sizeRead = 0;
        while (sizeRead < length)
        {
            int sizeRemaining = buffer.remaining();
            final String key = EncodingUtils.readShortString(buffer);
            // TODO: use proper charset decoder
            byte iType = buffer.get();
            final char type = (char) iType;
            Object value = null;

            switch (type)
            {
                case BOOLEAN_PROPERTY_PREFIX:
                    value = EncodingUtils.readBoolean(buffer);
                    break;
                case BYTE_PROPERTY_PREFIX:
                    value = EncodingUtils.readByte(buffer);
                    break;
                case SHORT_PROPERTY_PREFIX:
                    value = EncodingUtils.readShort(buffer);
                    break;
                case INT_PROPERTY_PREFIX:
                    value = EncodingUtils.readInteger(buffer);
                    break;
                case AMQP_UNSIGNEDINT_PROPERTY_PREFIX:// This will only fit in a long
                case LONG_PROPERTY_PREFIX:
                    value = EncodingUtils.readLong(buffer);
                    break;
                case FLOAT_PROPERTY_PREFIX:
                    value = EncodingUtils.readFloat(buffer);
                    break;
                case DOUBLE_PROPERTY_PREFIX:
                    value = EncodingUtils.readDouble(buffer);
                    break;

                    // TODO: use proper charset decoder
                case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                    //case AMQP_STRING_PROPERTY_PREFIX:
                case STRING_PROPERTY_PREFIX:
                    value = EncodingUtils.readLongString(buffer);
                    break;
                case NULL_STRING_PROPERTY_PREFIX:
                    value = null;
                    break;
                    //case AMQP_ASCII_STRING_PROPERTY_PREFIX:
                case CHAR_PROPERTY_PREFIX:
                    value = EncodingUtils.readShortString(buffer).charAt(0);
                    break;
                case BYTES_PROPERTY_PREFIX:
                    value = EncodingUtils.readBytes(buffer);
                    break;
                case XML_PROPERTY_PREFIX:
                    processXMLLine(EncodingUtils.readLongString(buffer));
                    break;
                default:
                    String msg = "Field '" + key + "' - unsupported field table type: " + type + ".";
                    //some extra debug information...
                    msg += " (" + iType + "), length=" + length + ", sizeRead=" + sizeRead + ", sizeRemaining=" + sizeRemaining;
                    throw new AMQFrameDecodingException(msg);
            }

            sizeRead += (sizeRemaining - buffer.remaining());

            if (debug)
            {
                _logger.debug("FieldTable::PropFieldTable(buffer," + length + "): Read type '" + type + "', key '" + key + "', value '" + value + "' (now read " + sizeRead + " of " + length + " encoded bytes)...");
            }

            if (type != XML_PROPERTY_PREFIX)
            {
                setObject(key, value);
                if (value == null)
                {
                    _logger.debug("setFromBuffer: value is null for key:" + key);
                    _propertyNamesTypeMap.put(key, "" + type);
                    _properties.put(key, null);
                }
            }
        }

        if (debug)
        {
            _logger.debug("FieldTable::FieldTable(buffer," + length + "): Done.");
        }
    }


    /**
     * @param name  the property name with type prefix
     * @param value the property value
     * @return integer
     */
    private static int getEncodingSize(String name, Object value)
    {
        int encodingSize = 1; // Initialy 1 to cover the char prefix

        char propertyPrefix = name.charAt(0);

        switch (propertyPrefix)
        {
            case BOOLEAN_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedBooleanLength();
                break;
            case BYTE_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedByteLength();
                break;
            case SHORT_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedShortLength();
                break;
            case INT_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedIntegerLength();
                break;
            case LONG_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedLongLength();
                break;
            case FLOAT_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedFloatLength();
                break;
            case DOUBLE_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedDoubleLength();
                break;
            case AMQP_WIDE_STRING_PROPERTY_PREFIX:
                //case AMQP_STRING_PROPERTY_PREFIX:
            case STRING_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedLongStringLength((String) value);
                break;
            case NULL_STRING_PROPERTY_PREFIX:
                // There is no need for additiona size beyond the prefix 
                break;
                //case AMQP_ASCII_STRING_PROPERTY_PREFIX:
            case CHAR_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedShortStringLength("" + (Character) value);
                break;
            case BYTES_PROPERTY_PREFIX:
                encodingSize += 1 + ((byte[]) value).length;
                break;
            case XML_PROPERTY_PREFIX:
                encodingSize += EncodingUtils.encodedLongStringLength(valueAsXML(name, value));
                break;
            default:
                //encodingSize = 1 + EncodingUtils.encodedLongStringLength(String.valueOf(value));
                //  We are using XML String encoding
                throw new IllegalArgumentException("Unsupported type in field table: " + value.getClass());
        }

// the extra byte for the type indicator is calculated in the name
        return encodingSize;
    }


}
