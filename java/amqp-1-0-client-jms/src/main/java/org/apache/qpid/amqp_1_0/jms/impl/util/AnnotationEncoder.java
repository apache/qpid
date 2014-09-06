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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.amqp_1_0.codec.DescribedType;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;

public class AnnotationEncoder
{
    private final JsonEncoder _encoder = new JsonEncoder();

    public String encode(Map<Symbol,Object> annotations) throws IOException
    {
        Map<String,Object> convertedMap = convertMap(annotations);
        return _encoder.encode(convertedMap);
    }


    private Map<String, Object> convertMap(final Map<Symbol,Object> value)
    {
        Map<String,Object> converted = new LinkedHashMap<>();
        for(Map.Entry<Symbol,Object> entry : value.entrySet())
        {
            converted.put(entry.getKey().toString(), convert(entry.getValue()));
        }
        return converted;
    }

    private Object convert(final Object value)
    {
        if(value == null || value instanceof String || value instanceof Boolean || value instanceof Integer)
        {
            return value;
        }
        else if(value instanceof Long)
        {
            return Collections.singletonMap("long", value);
        }
        else if(value instanceof Short)
        {
            return Collections.singletonMap("short", value);
        }
        else if(value instanceof Byte)
        {
            return Collections.singletonMap("byte", value);
        }
        else if(value instanceof UnsignedLong)
        {
            return Collections.singletonMap("ulong", ((UnsignedLong)value).bigIntegerValue());
        }
        else if(value instanceof UnsignedInteger)
        {
            return Collections.singletonMap("uint", ((UnsignedInteger)value).longValue());
        }
        else if(value instanceof UnsignedShort)
        {
            return Collections.singletonMap("ushort", ((UnsignedShort)value).intValue());
        }
        else if(value instanceof UnsignedByte)
        {
            return Collections.singletonMap("ubyte", ((UnsignedByte)value).shortValue());
        }
        else if(value instanceof Character)
        {
            return Collections.singletonMap("char", value.toString());
        }
        else if(value instanceof Symbol)
        {
            return Collections.singletonMap("symbol", value.toString());
        }
        else if(value instanceof Date)
        {
            return Collections.singletonMap("timestamp", ((Date)value).getTime());
        }
        else if(value instanceof Float)
        {
            return Collections.singletonMap("float", value);
        }
        else if(value instanceof Double)
        {
            return Collections.singletonMap("double", value);
        }
        else if(value instanceof Binary)
        {
            Binary bin = (Binary) value;
            byte[] bytes;
            if(bin.getArrayOffset() != 0 || bin.getLength() != bin.getArray().length)
            {
                bytes = new byte[bin.getLength()];
                System.arraycopy(bin.getArray(), bin.getArrayOffset(),bytes, 0, bin.getLength());
            }
            else
            {
                bytes = bin.getArray();
            }
            return Collections.singletonMap("binary", DatatypeConverter.printBase64Binary(bytes));
        }
        else if(value instanceof List)
        {
            List<?> list = (List) value;
            List<Object> convertedList = new ArrayList<>(list.size());
            for(Object o : list)
            {
                convertedList.add(convert(o));
            }
            return convertedList;
        }
        else if(value instanceof Map)
        {
            Map<?,?> map = (Map<?,?>) value;
            Map<Object,Object> convertedMap = new LinkedHashMap<>();
            for(Map.Entry<?,?> entry : map.entrySet())
            {
                convertedMap.put(convert(entry.getKey()), convert(entry.getValue()));
            }
            return Collections.singletonMap("map", convertedMap);
        }
        else if(value instanceof Object[])
        {
            return Collections.singletonMap("array", convert(Arrays.asList((Object[])value)));
        }
        else if(value.getClass().isArray())
        {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for(int i = 0; i < length; i++)
            {
                list.add(Array.get(value, i));
            }
            return Collections.singletonMap("array", convert(list));
        }
        else if(value instanceof DescribedType)
        {
            DescribedType type = (DescribedType) value;
            return Collections.singletonMap("described", Collections.singletonMap(convert(type.getDescriptor()),
                                                                                  convert(type.getDescribed())));
        }
        throw new IllegalArgumentException("Cannot convert object of class: " + value.getClass().getName());
    }
}
