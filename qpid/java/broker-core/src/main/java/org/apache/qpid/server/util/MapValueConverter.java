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
package org.apache.qpid.server.util;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapValueConverter
{

    public static String getStringAttribute(String name, Map<String,Object> attributes, String defaultVal)
    {
        final Object value = attributes.get(name);
        return toString(value, defaultVal);
    }

    public static String toString(final Object value)
    {
        return toString(value, null);
    }

    public static String toString(final Object value, String defaultVal)
    {
        if (value == null)
        {
            return defaultVal;
        }
        else if (value instanceof String)
        {
            return (String)value;
        }
        return String.valueOf(value);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <E extends Enum> E getEnumAttribute(Class<E> clazz, String name, Map<String,Object> attributes, E defaultVal)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultVal;
        }
        else if(clazz.isInstance(obj))
        {
            return (E) obj;
        }
        else if(obj instanceof String)
        {
            return (E) Enum.valueOf(clazz, (String)obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type " + clazz.getSimpleName());
        }
    }

    @SuppressWarnings({ "unchecked" })
    public static <T extends Enum<T>> T toEnum(String name, Object rawValue, Class<T> enumType)
    {
        if (enumType.isInstance(rawValue))
        {
            return (T) rawValue;
        }
        else if (rawValue instanceof String)
        {
            final String stringValue = (String) rawValue;

            return "null".equals(stringValue) ? null : (T) Enum.valueOf(enumType, stringValue);
        }
        else if(rawValue == null)
        {
            return null;
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type "
                    + enumType.getSimpleName());
        }
    }

    public static Boolean getBooleanAttribute(String name, Map<String,Object> attributes, Boolean defaultValue)
    {
        Object obj = attributes.get(name);
        return toBoolean(name, obj, defaultValue);
    }

    public static Boolean toBoolean(String name, Object obj)
    {
        return toBoolean(name, obj, null);
    }

    public static Boolean toBoolean(String name, Object obj, Boolean defaultValue)
    {
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Boolean)
        {
            return (Boolean) obj;
        }
        else if(obj instanceof String)
        {
            return Boolean.parseBoolean((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Boolean");
        }
    }

    public static Integer getIntegerAttribute(String name, Map<String,Object> attributes, Integer defaultValue)
    {
        Object obj = attributes.get(name);
        return toInteger(name, obj, defaultValue);
    }

    public static Integer toInteger(String name, Object obj)
    {
        return toInteger(name, obj, null);
    }

    public static Integer toInteger(String name, Object obj, Integer defaultValue)
    {
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Number)
        {
            return ((Number) obj).intValue();
        }
        else if(obj instanceof String)
        {
            return Integer.valueOf((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Integer");
        }
    }

    public static Long toLong(String name, Object obj)
    {
        return toLong(name, obj, null);
    }

    public static Long toLong(String name, Object obj, Long defaultValue)
    {
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Number)
        {
            return ((Number) obj).longValue();
        }
        else if(obj instanceof String)
        {
            return Long.valueOf((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Long");
        }
    }
    public static <T extends Enum<T>> Set<T> getEnumSetAttribute(String name, Map<String, Object> attributes, Class<T> clazz)
    {
        Object obj = attributes.get(name);
        if (obj == null)
        {
            return null;
        }
        else
        {
            return toSet(obj, clazz, name);
        }
    }

    public static <T> Set<T> toSet(Object rawValue, Class<T> setItemClass, String attributeName)
    {
        if (rawValue == null)
        {
            return null;
        }
        HashSet<T> set = new HashSet<T>();
        if (rawValue instanceof Iterable)
        {
            Iterable<?> iterable = (Iterable<?>)rawValue;
            for (Object object : iterable)
            {
                T converted = convert(object, setItemClass, attributeName);
                set.add(converted);
            }
        }
        else if (rawValue.getClass().isArray())
        {
            int length = Array.getLength(rawValue);
            for (int i = 0; i < length; i ++)
            {
                Object arrayElement = Array.get(rawValue, i);
                T converted = convert(arrayElement, setItemClass, attributeName);
                set.add(converted);
            }
        }
        else
        {
            throw new IllegalArgumentException("Cannot convert '" + rawValue.getClass() + "' into Set<" + setItemClass.getSimpleName() + "> for attribute " + attributeName);
        }
        return set;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T convert(Object rawValue, Class<T> classObject, String attributeName)
    {
        Object value;
        if (classObject == Long.class || classObject == long.class)
        {
            value = toLong(attributeName, rawValue);
        }
        else if (classObject == Integer.class || classObject == int.class)
        {
            value = toInteger(attributeName, rawValue);
        }
        else if (classObject == Boolean.class || classObject == boolean.class)
        {
            value = toBoolean(attributeName, rawValue);
        }
        else if (classObject == String.class)
        {
            value = toString(rawValue);
        }
        else if (Enum.class.isAssignableFrom(classObject))
        {
            value = toEnum(attributeName, rawValue, (Class<Enum>) classObject);
        }
        else if (classObject == Object.class)
        {
            value = rawValue;
        }
        else
        {
            throw new IllegalArgumentException("Cannot convert '" + rawValue + "' of type '" + rawValue.getClass()
                    + "' into type " + classObject + " for attribute " + attributeName);
        }
        return (T) value;
    }

}
