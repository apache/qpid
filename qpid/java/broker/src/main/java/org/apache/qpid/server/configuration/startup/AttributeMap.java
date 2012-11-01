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
package org.apache.qpid.server.configuration.startup;

import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.util.MapValueConverter;

public class AttributeMap
{
    private Map<String, Object> _source;

    public AttributeMap(Map<String, Object> source)
    {
        _source = source;
    }

    public String getStringAttribute(String name, String defaultVal)
    {
        return MapValueConverter.getStringAttribute(name, _source, defaultVal);
    }

    public Map<String, Object> getMapAttribute(String name, Map<String, Object> defaultVal)
    {
        return MapValueConverter.getMapAttribute(name, _source, defaultVal);
    }

    public <E extends Enum<?>> E getEnumAttribute(Class<E> clazz, String name, E defaultVal)
    {
        return MapValueConverter.getEnumAttribute(clazz, name, _source, defaultVal);
    }

    public <E extends Enum<?>> E getEnumAttribute(Class<E> clazz, String name)
    {
        return MapValueConverter.getEnumAttribute(clazz, name, _source);
    }

    public Boolean getBooleanAttribute(String name, Boolean defaultValue)
    {
        return MapValueConverter.getBooleanAttribute(name, _source, defaultValue);
    }

    public Integer getIntegerAttribute(String name, Integer defaultValue)
    {
        return MapValueConverter.getIntegerAttribute(name, _source, defaultValue);
    }

    public Long getLongAttribute(String name, Long defaultValue)
    {
        return MapValueConverter.getLongAttribute(name, _source, defaultValue);
    }

    public <T> Set<T> getSetAttribute(String name)
    {
        return MapValueConverter.getSetAttribute(name, _source);
    }

    public <T> Set<T> getSetAttribute(String name, Set<T> defaultValue)
    {
        return MapValueConverter.getSetAttribute(name, _source, defaultValue);
    }

    public String getStringAttribute(String name)
    {
        String value = getStringAttribute(name, null);
        if (value == null)
        {
            throw new IllegalArgumentException("Attribute with name '" + name + "' is not found!");
        }
        return value;
    }

}
