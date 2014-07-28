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
package org.apache.qpid.server.message;

import java.util.EnumMap;
import java.util.Map;

public interface InstanceProperties
{

    enum Property {
        REDELIVERED,
        PERSISTENT,
        MANDATORY,
        IMMEDIATE,
        EXPIRATION
    }

    public Object getProperty(Property prop);

    InstanceProperties EMPTY = new InstanceProperties()
        {
            @Override
            public Object getProperty(final Property prop)
            {
                return null;
            }
        };

    class Factory
    {
        public static InstanceProperties fromMap(Map<Property, Object> map)
        {
            final Map<Property,Object> props = new EnumMap<Property,Object>(map);
            return new MapInstanceProperties(props);
        }

        public static Map<Property, Object> asMap(InstanceProperties props)
        {
            EnumMap<Property, Object> map = new EnumMap<Property,Object>(Property.class);

            for(Property prop : Property.values())
            {
                Object value = props.getProperty(prop);
                if(value != null)
                {
                    map.put(prop,value);
                }
            }

            return map;
        }

        public static InstanceProperties copy(InstanceProperties from)
        {
            final Map<Property,Object> props = asMap(from);

            return new MapInstanceProperties(props);

        }

        private static class MapInstanceProperties implements InstanceProperties
        {
            private final Map<Property, Object> _props;

            private MapInstanceProperties(final Map<Property, Object> props)
            {
                _props = props;
            }

            @Override
            public Object getProperty(final Property prop)
            {
                return _props.get(prop);
            }
        }


    }
}
