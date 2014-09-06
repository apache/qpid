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
package org.apache.qpid.amqp_1_0.jms.impl.util;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.amqp_1_0.codec.DescribedType;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;

public class AnnotationDecoder
{
    private static final Map<String,Converter> CONVERTERS = new HashMap<>();


    private final JsonDecoder _decoder = new JsonDecoder();

    public Map<Symbol,Object> decode(String value) throws IOException
    {
        Map<String,Object> map = (Map<String,Object>) _decoder.decode(new StringReader(value));
        Map<Symbol,Object> convertedMap = new LinkedHashMap<>();
        for(Map.Entry<String,Object> entry : map.entrySet())
        {
            convertedMap.put(Symbol.valueOf(entry.getKey()), convertObject(entry.getValue()));
        }
        return convertedMap;
    }

    private Object convertObject(final Object value)
    {
        if(value == null || value instanceof String || value instanceof Boolean)
        {
            return value;
        }
        else if(value instanceof Number)
        {
            return ((Number)value).intValue();
        }
        else if(value instanceof Collection)
        {
            Collection<?> list = (Collection<?>)value;
            List<Object> convertedList = new ArrayList<>(list.size());
            for(Object o : list)
            {
                convertedList.add(convertObject(o));
            }
            return convertedList;
        }
        else if(value instanceof Map)
        {
            Map<String,Object> map = (Map<String,Object>)value;
            if(map.size() != 1)
            {
                throw new IllegalArgumentException("Cannot parse map " + map + " as a value");
            }
            Converter converter = CONVERTERS.get(map.keySet().iterator().next());
            return converter.convert(map.values().iterator().next(), this);
        }
        return null;
    }

    private static abstract class Converter
    {
        Converter(String name)
        {
            CONVERTERS.put(name, this);
        }

        abstract Object convert(Object value, final AnnotationDecoder decoder);
    }

    private static Converter LONG_CONVERTER = new Converter("long")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return ((Number)value).longValue();
        }
    };

    private static Converter SHORT_CONVERTER = new Converter("short")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return ((Number)value).shortValue();
        }
    };

    private static Converter BYTE_CONVERTER = new Converter("byte")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return ((Number)value).byteValue();
        }
    };

    private static Converter ULONG_CONVERTER = new Converter("ulong")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            Number number = (Number) value;
            return UnsignedLong.valueOf(number.toString());
        }
    };

    private static Converter UINT_CONVERTER = new Converter("uint")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return UnsignedInteger.valueOf(((Number) value).longValue());
        }
    };

    private static Converter USHORT_CONVERTER = new Converter("ushort")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            Number number = (Number) value;
            return UnsignedShort.valueOf(number.toString());
        }
    };

    private static Converter UBYTE_CONVERTER = new Converter("ubyte")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            Number number = (Number) value;

            return UnsignedByte.valueOf(value.toString());
        }
    };

    private static Converter FLOAT_CONVERTER = new Converter("float")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return ((Number) value).floatValue();
        }
    };

    private static Converter DOUBLE_CONVERTER = new Converter("double")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return ((Number) value).doubleValue();
        }
    };


    private static Converter SYMBOL_CONVERTER = new Converter("symbol")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return Symbol.valueOf((String) value);
        }
    };


    private static Converter CHAR_CONVERTER = new Converter("char")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            String stringValue = (String) value;
            if(stringValue.length() != 1)
            {
                throw new IllegalArgumentException("Cannot decode '"+stringValue+"' as a char");
            }
            return stringValue.charAt(0);
        }
    };


    private static Converter TIMESTAMP_CONVERTER = new Converter("timestamp")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            return new Date(((Number) value).longValue());
        }
    };


    private static Converter MAP_CONVERTER = new Converter("map")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            Map<?,?> map = (Map<?,?>) value;
            Map<Object,Object> convertedMap = new LinkedHashMap<>();
            for(Map.Entry<?,?> entry : map.entrySet())
            {
                convertedMap.put(decoder.convertObject(entry.getKey()), decoder.convertObject(entry.getValue()));
            }
            return convertedMap;
        }
    };


    private static Converter DESCRIBED_CONVERTER = new Converter("described")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            Map<?,?> map = (Map<?,?>) value;
            if(map.size() != 1)
            {
                throw new IllegalArgumentException("Cannot convert described type from: " + map);
            }
            Object descriptor = decoder.convertObject(map.keySet().iterator().next());
            Object described =  decoder.convertObject(map.values().iterator().next());
            return new DescribedType(descriptor, described);
        }
    };

    private static Converter BINARY_CONVERTER = new Converter("binary")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            String valueString = (String)value;
            byte[] bytes = DatatypeConverter.parseBase64Binary(valueString);
            return new Binary(bytes);
        }
    };

    private static Converter ARRAY_CONVERTER = new Converter("array")
    {
        @Override
        Object convert(final Object value, final AnnotationDecoder decoder)
        {
            Collection<?> list = (Collection<?>)value;
            List<Object> convertedList = new ArrayList<>(list.size());
            Set<Class> objClasses = new HashSet<>();
            for(Object o : list)
            {
                Object convertObject = decoder.convertObject(o);
                objClasses.add(convertObject == null ? Void.class : convertObject.getClass());
                convertedList.add(convertObject);
            }
            if(objClasses.size() != 1)
            {
                throw new IllegalArgumentException("Cannot convert object to an array: " + value);
            }
            Class objClass = objClasses.iterator().next();
            if(objClass == Void.class)
            {
                return new Void[convertedList.size()];
            }
            else if(objClass == Boolean.class)
            {
                boolean[] array = new boolean[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Boolean) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Byte.class)
            {
                byte[] array = new byte[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Byte) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Character.class)
            {
                char[] array = new char[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Character) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Short.class)
            {
                short[] array = new short[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Short) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Integer.class)
            {
                int[] array = new int[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Integer) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Long.class)
            {
                long[] array = new long[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Long) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Float.class)
            {
                float[] array = new float[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Float) convertedList.get(i);
                }
                return array;
            }
            else if(objClass == Double.class)
            {
                double[] array = new double[convertedList.size()];
                for(int i = 0; i < convertedList.size(); i++)
                {
                    array[i] = (Double) convertedList.get(i);
                }
                return array;
            }
            else
            {
                return convertedList.toArray((Object[])Array.newInstance(objClass, convertedList.size()));
            }
        }
    };


    public static void main(String[] args) throws Exception
    {
        Map<Symbol, Object> foo = new LinkedHashMap<>();
        foo.put(Symbol.valueOf("ARG_1"), 2);
        foo.put(Symbol.valueOf("ARG_2"), true);
        foo.put(Symbol.valueOf("ARG_3"), "wibble");
        foo.put(Symbol.valueOf("ARG_4"), Arrays.asList("this", "is", "a", "test"));
        foo.put(Symbol.valueOf("ARG_5"), Arrays.asList((Object)"this", 2l, Symbol.valueOf("a"), "test"));
        foo.put(Symbol.valueOf("ARG_6"), Collections.singletonMap("wibble",0.3));



        String encoded = new AnnotationEncoder().encode(foo);
        System.err.println(encoded);
        Object foo2 = new AnnotationDecoder().decode(encoded);

        System.out.println(foo2.equals(foo));
    }

}
