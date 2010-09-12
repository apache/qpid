package org.apache.qpid.configuration;
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


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public interface Accessor
{
    public Boolean getBoolean(String name);
    public Integer getInt(String name);
    public Long getLong(String name);
    public String getString(String name);
    
    static class SystemPropertyAccessor implements Accessor
    {
        public Boolean getBoolean(String name)
        {
            return Boolean.getBoolean(name);
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
    }
    
    static class MapAccessor implements Accessor
    {
        protected Map<Object,Object> source;
        
        public MapAccessor(Map<Object,Object> map)
        {
            source = map;
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
    }  
    
    static class PropertyFileAccessor extends MapAccessor
    {
        public PropertyFileAccessor(String fileName) throws FileNotFoundException, IOException
        {
            super(null);
            Properties props = new Properties();
            FileInputStream inStream = new FileInputStream(fileName);
            try
            {
                props.load(inStream);
            }
            finally
            {
                inStream.close();
            }
            source = props;
        }
    }
    
    static class CombinedAccessor implements Accessor
    {
        private List<Accessor> accessors;
        
        public CombinedAccessor(Accessor...accessors)
        {
            this.accessors = Arrays.asList(accessors);
        }
        
        public Boolean getBoolean(String name)
        {
            for (Accessor accessor: accessors)
            {
                if (accessor.getBoolean(name) != null)
                {
                    return accessor.getBoolean(name);
                }
            }
            return null;
        }
        
        public Integer getInt(String name)
        {
            for (Accessor accessor: accessors)
            {
                if (accessor.getBoolean(name) != null)
                {
                    return accessor.getInt(name);
                }
            }
            return null;
        }
        
        public Long getLong(String name)
        {
            for (Accessor accessor: accessors)
            {
                if (accessor.getBoolean(name) != null)
                {
                    return accessor.getLong(name);
                }
            }
            return null;
        }
        
        public String getString(String name)
        {
            for (Accessor accessor: accessors)
            {
                if (accessor.getBoolean(name) != null)
                {
                    return accessor.getString(name);
                }
            }
            return null;
        }
    }
    
    static class ValidationAccessor implements Accessor
    {   
        private List<Validator> validators;
        private Accessor delegate;
        
        public ValidationAccessor(Accessor delegate,Validator...validators)
        {
            this.validators = Arrays.asList(validators);
            this.delegate = delegate;
        }

        public Boolean getBoolean(String name)
        {
            // there is nothing to validate in a boolean
            return delegate.getBoolean(name);
        }
        
        public Integer getInt(String name)
        {
            Integer v = delegate.getInt(name);
            for (Validator validator: validators)
            {
                validator.validate(v);
            }
            return v;
        }
        
        public Long getLong(String name)
        {
            Long v = delegate.getLong(name);
            for (Validator validator: validators)
            {
                validator.validate(v);
            }
            return v;
        }
        
        public String getString(String name)
        {
            String v = delegate.getString(name);
            for (Validator validator: validators)
            {
                validator.validate(v);
            }
            return v;
        }
    }
}
