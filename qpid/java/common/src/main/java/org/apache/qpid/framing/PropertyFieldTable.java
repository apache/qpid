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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

//extends FieldTable
public class PropertyFieldTable
{

    private static final Logger _logger = Logger.getLogger(PropertyFieldTable.class);

    public static final char BOOLEAN_PROPERTY_PREFIX = 'B';
    public static final char BYTE_PROPERTY_PREFIX = 'b';
    public static final char SHORT_PROPERTY_PREFIX = 's';
    public static final char INT_PROPERTY_PREFIX = 'i';
    public static final char LONG_PROPERTY_PREFIX = 'l';
    public static final char FLOAT_PROPERTY_PREFIX = 'f';
    public static final char DOUBLE_PROPERTY_PREFIX = 'd';
    public static final char STRING_PROPERTY_PREFIX = 'S';
    public static final char CHAR_PROPERTY_PREFIX = 'c';
    public static final char BYTES_PROPERTY_PREFIX = 'y';


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
            System.out.println(textFormat);
            e.printStackTrace();
        }

    }

    // ************  Getters

    public Boolean getBoolean(String string)
    {
        return (Boolean) _properties.get(BOOLEAN_PROPERTY_PREFIX + string);
    }

    public Byte getByte(String string)
    {
        return (Byte) _properties.get(BYTE_PROPERTY_PREFIX + string);
    }

    public Short getShort(String string)
    {
        return (Short) _properties.get(SHORT_PROPERTY_PREFIX + string);
    }

    public Integer getInteger(String string)
    {
        return (Integer) _properties.get(INT_PROPERTY_PREFIX + string);
    }

    public Long getLong(String string)
    {
        return (Long) _properties.get(LONG_PROPERTY_PREFIX + string);
    }

    public Float getFloat(String string)
    {
        return (Float) _properties.get(FLOAT_PROPERTY_PREFIX + string);
    }

    public Double getDouble(String string)
    {
        return (Double) _properties.get(DOUBLE_PROPERTY_PREFIX + string);
    }

    public String getString(String string)
    {
        return (String) _properties.get(STRING_PROPERTY_PREFIX + string);
    }

    public Character getCharacter(String string)
    {
        return (Character) _properties.get(CHAR_PROPERTY_PREFIX + string);
    }

    public byte[] getBytes(String string)
    {
        return (byte[]) _properties.get(BYTES_PROPERTY_PREFIX + string);
    }

    public Object getObject(String string)
    {
        String typestring = _propertyNamesTypeMap.get(string);

        if (typestring != null && !typestring.equals(""))
        {
            char type = typestring.charAt(0);

            return _properties.get(type + string);
        }
        else
        {
            return null;
        }
    }

    // ************  Setters


    public void setBoolean(String string, boolean b)
    {
        checkPropertyName(string, BOOLEAN_PROPERTY_PREFIX);


        _propertyNamesTypeMap.put(string, "" + BOOLEAN_PROPERTY_PREFIX);
        _properties.put(BOOLEAN_PROPERTY_PREFIX + string, b);// ? new Long(1) : new Long(0));
    }

    public void setByte(String string, byte b)
    {
        checkPropertyName(string, BYTE_PROPERTY_PREFIX);


        _properties.put(BYTE_PROPERTY_PREFIX + string, b);
    }

    public void setShort(String string, short i)
    {
        checkPropertyName(string, SHORT_PROPERTY_PREFIX);


        _properties.put(SHORT_PROPERTY_PREFIX + string, i);
    }

    public void setInteger(String string, int i)
    {
        checkPropertyName(string, INT_PROPERTY_PREFIX);


        _properties.put(INT_PROPERTY_PREFIX + string, i);
    }

    public void setLong(String string, long l)
    {
        checkPropertyName(string, LONG_PROPERTY_PREFIX);


        _properties.put(LONG_PROPERTY_PREFIX + string, l);
    }

    public void setFloat(String string, float v)
    {
        checkPropertyName(string, FLOAT_PROPERTY_PREFIX);


        _properties.put(FLOAT_PROPERTY_PREFIX + string, v);
    }

    public void setDouble(String string, double v)
    {
        checkPropertyName(string, DOUBLE_PROPERTY_PREFIX);


        _properties.put(DOUBLE_PROPERTY_PREFIX + string, v);
    }

    public void setString(String string, String string1)
    {
        checkPropertyName(string, STRING_PROPERTY_PREFIX);


        _properties.put(STRING_PROPERTY_PREFIX + string, string1);
    }

    public void setChar(String string, char c)
    {
        checkPropertyName(string, CHAR_PROPERTY_PREFIX);

        _properties.put(CHAR_PROPERTY_PREFIX + string, c);
    }

    public void setBytes(String string, byte[] bytes)
    {
        setBytes(string, bytes, 0, bytes.length);
    }

    public void setBytes(String string, byte[] bytes, int start, int length)
    {
        checkPropertyName(string, BYTES_PROPERTY_PREFIX);

        _properties.put(BYTES_PROPERTY_PREFIX + string, sizeByteArray(bytes, start, length));
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


    public void setObject(String string, Object object)
    {
        if (object instanceof Boolean)
        {
            setBoolean(string, (Boolean) object);
        }
        else
        {
            if (object instanceof Byte)
            {
                setByte(string, (Byte) object);
            }
            else
            {
                if (object instanceof Short)
                {
                    setShort(string, (Short) object);
                }
                else
                {
                    if (object instanceof Integer)
                    {
                        setInteger(string, (Integer) object);
                    }
                    else
                    {
                        if (object instanceof Long)
                        {
                            setLong(string, (Long) object);
                        }
                        else
                        {
                            if (object instanceof Float)
                            {
                                setFloat(string, (Float) object);
                            }
                            else
                            {
                                if (object instanceof Double)
                                {
                                    setDouble(string, (Double) object);
                                }
                                else
                                {
                                    if (object instanceof String)
                                    {
                                        setString(string, (String) object);
                                    }
                                    else
                                    {
                                        if (object instanceof Character)
                                        {
                                            setChar(string, (Character) object);
                                        }
                                        else
                                        {
                                            if (object instanceof byte[])
                                            {
                                                setBytes(string, (byte[]) object);
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


    }

    // ***** Methods

    public Enumeration getPropertyNames()
    {
        Vector<String> names = new Vector<String>();

        Iterator keys = _properties.keySet().iterator();

        while (keys.hasNext())
        {
            String key = (String) keys.next();

            names.add(key.substring(1));
        }

        return names.elements();
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
                buf.append(propertyXML(propertyName, true));

                if (propertyName.charAt(0) == BYTES_PROPERTY_PREFIX)
                {
                    //remove '>'
                    buf.deleteCharAt(buf.length() - 1);

                    byte[] bytes = (byte[]) entry.getValue();
                    buf.append(" length='").append(bytes.length).append("'>");

                    buf.append(byteArrayToXML(propertyName.substring(1), bytes));
                }
                else
                {

                    buf.append(String.valueOf(entry.getValue()));
                }
                buf.append(propertyXML(propertyName, false));

            }
        }
        buf.append("\n");
        buf.append(PROPERTY_FIELD_TABLE_CLOSE_XML);

        return buf.toString();
    }

    private void checkPropertyName(String propertyName, char propertyPrefix)
    {
        if (propertyName == null)
        {
            throw new IllegalArgumentException("Property name must not be null");
        }
        else if ("".equals(propertyName))
        {
            throw new IllegalArgumentException("Property name must not be the empty string");
        }

        String currentValue = _propertyNamesTypeMap.get(propertyName);

        if (currentValue != null)
        {
            _properties.remove(currentValue + propertyName);
        }

        _propertyNamesTypeMap.put(propertyName, "" + propertyPrefix);
    }

    private static String propertyXML(String propertyName, boolean start)
    {
        char typeIdentifier = propertyName.charAt(0);

        StringBuffer buf = new StringBuffer();

        if (start)
        {
            buf.append("<");
        }
        else
        {
            buf.append("</");
        }


        switch (typeIdentifier)
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
                buf.append(UNKNOWN + " (identifier ").append(typeIdentifier).append(")");
                break;
        }


        if (start)
        {
            buf.append(" name='").append(propertyName.substring(1)).append("'");
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
        String type = xmlline.substring(1, xmlline.indexOf(" "));

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
            Integer length = Integer.parseInt(xmlline.substring(
                    xmlline.lastIndexOf("=") + 2
                    , xmlline.lastIndexOf("'")));
            byte[] bytes = new byte[length];
            setBytes(propertyName, bytes);
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


}

