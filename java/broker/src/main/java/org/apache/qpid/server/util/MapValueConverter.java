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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
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

    public static String getStringAttribute(String name, Map<String, Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getStringAttribute(name, attributes, null);
    }

    private static void assertMandatoryAttribute(String name, Map<String, Object> attributes)
    {
        if (!attributes.containsKey(name))
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not found");
        }
    }

    public static Map<String,Object> getMapAttribute(String name, Map<String,Object> attributes, Map<String,Object> defaultVal)
    {
        final Object value = attributes.get(name);
        if(value == null)
        {
            return defaultVal;
        }
        else if(value instanceof Map)
        {
            @SuppressWarnings("unchecked")
            Map<String,Object> retVal = (Map<String,Object>) value;
            return retVal;
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Map");
        }
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

    public static <E extends Enum<?>> E getEnumAttribute(Class<E> clazz, String name, Map<String,Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getEnumAttribute(clazz, name, attributes, null);
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
            return (T) Enum.valueOf(enumType, (String) rawValue);
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


    public static boolean getBooleanAttribute(String name, Map<String, Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getBooleanAttribute(name, attributes, null);
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

    public static Integer getIntegerAttribute(String name, Map<String,Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getIntegerAttribute(name, attributes, null);
    }

    public static Long getLongAttribute(String name, Map<String,Object> attributes, Long defaultValue)
    {
        Object obj = attributes.get(name);
        return toLong(name, obj, defaultValue);
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

    public static <T> Set<T> getSetAttribute(String name, Map<String,Object> attributes)
    {
        assertMandatoryAttribute(name, attributes);
        return getSetAttribute(name, attributes, Collections.<T>emptySet());
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> getSetAttribute(String name, Map<String,Object> attributes, Set<T> defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Set)
        {
            return (Set<T>) obj;
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Set");
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

    public static Map<String, Object> convert(Map<String, Object> configurationAttributes, Map<String, Type> attributeTypes)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        for (Map.Entry<String, Type> attributeEntry : attributeTypes.entrySet())
        {
            String attributeName = attributeEntry.getKey();
            if (configurationAttributes.containsKey(attributeName))
            {
                Type typeObject = attributeEntry.getValue();
                Object rawValue = configurationAttributes.get(attributeName);
                Object value = null;
                if (typeObject instanceof Class)
                {
                    Class<?> classObject = (Class<?>)typeObject;
                    value =  convert(rawValue, classObject, attributeName);
                }
                else if (typeObject instanceof ParameterizedType)
                {
                    ParameterizedType parameterizedType= (ParameterizedType)typeObject;
                    Type type = parameterizedType.getRawType();
                    if (type == Set.class)
                    {
                        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                        if (actualTypeArguments.length != 1)
                        {
                            throw new IllegalArgumentException("Set type argument is not specified");
                        }
                        Class<?> classObject = (Class<?>)actualTypeArguments[0];
                        value = toSet(rawValue, classObject, attributeName);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Convertion into " + parameterizedType + " is not yet supported");
                    }
                }
                else
                {
                    throw new IllegalArgumentException("Convertion into " + typeObject + " is not yet supported");
                }
                attributes.put(attributeName, value);
            }
        }
        return attributes;
    }

    public static <T> Set<T> toSet(Object rawValue, Class<T> setItemClass, String attributeName)
    {
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
        else
        {
            throw new IllegalArgumentException("Cannot convert '" + rawValue + "' of type '" + rawValue.getClass()
                    + "' into type " + classObject + " for attribute " + attributeName);
        }
        return (T) value;
    }

}
