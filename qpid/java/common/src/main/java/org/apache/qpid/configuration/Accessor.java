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
package org.apache.qpid.configuration;

import java.util.Map;

public interface Accessor
{
    public Boolean getBoolean(String name);
    public Integer getInt(String name);
    public Long getLong(String name);
    public String getString(String name);
    public Float getFloat(String name);
    
    static class SystemPropertyAccessor implements Accessor
    {
        public Boolean getBoolean(String name)
        {
            return System.getProperty(name) == null ? null : Boolean.getBoolean(name);
        }
        
        public Integer getInt(String name)
        {
            return Integer.getInteger(name);
        }
        
        public Long getLong(String name)
        {
            return Long.getLong(name);
        }
        
        public String getString(String name)
        {
            return System.getProperty(name);
        }

        public Float getFloat(String name)
        {
            return System.getProperty(name) == null ? null : Float.parseFloat(System.getProperty(name));
        }
    }
    
    static class MapAccessor implements Accessor
    {
        private Map<Object,Object> source;
        
        public MapAccessor(Map<Object,Object> map)
        {
            source = map;
        }

        protected void setSource(Map<Object, Object> source)
        {
            this.source = source;
        }

        public Boolean getBoolean(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Boolean)
                {
                    return (Boolean)source.get(name);
                }
                else
                {
                    return Boolean.parseBoolean((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public Integer getInt(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Integer)
                {
                    return (Integer)source.get(name);
                }
                else
                {
                    return Integer.parseInt((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public Long getLong(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Long)
                {
                    return (Long)source.get(name);
                }
                else
                {
                    return Long.parseLong((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public String getString(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof String)
                {
                    return (String)source.get(name);
                }
                else
                {
                    return String.valueOf(source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
        
        public Float getFloat(String name)
        {
            if (source != null && source.containsKey(name))
            {
                if (source.get(name) instanceof Float)
                {
                    return (Float)source.get(name);
                }
                else
                {
                    return Float.parseFloat((String)source.get(name));
                }
            }
            else
            {
                return null;
            }
        }
    }
}
