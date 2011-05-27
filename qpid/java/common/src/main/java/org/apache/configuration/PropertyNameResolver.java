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

package org.apache.configuration;

import java.util.HashMap;
import java.util.Map;

public class PropertyNameResolver
{
    public static interface Accessor
    {
        Object get(String name);
    }

    private static Map<Class<?>,Accessor> accessors = new HashMap<Class<?>,Accessor>();
    protected Map<String,QpidProperty> properties;

    private static class BooleanAccessor implements Accessor
    {
        public Boolean get(String name)
        {
            return Boolean.getBoolean(name);
        }
    }

    private static class IntegerAccessor implements Accessor
    {
        public Integer get(String name)
        {
            return Integer.getInteger(name);
        }
    }
    
    private static class LongAccessor implements Accessor
    {
        public Long get(String name)
        {
            return Long.getLong(name);
        }
    }
    
    private static class StringAccessor implements Accessor
    {
        public String get(String name)
        {
            return System.getProperty(name);
        }
    }

    static
    {
        accessors.put(Boolean.class, new BooleanAccessor());
        accessors.put(Integer.class, new IntegerAccessor());
        accessors.put(String.class, new StringAccessor());
        accessors.put(Long.class, new LongAccessor());
    }
    
   public Integer getIntegerValue(String propName)
   {
       return properties.get(propName).get(Integer.class);
   }

   public Long getLongValue(String propName)
   {
       return properties.get(propName).get(Long.class);
   }
   
   public String getStringValue(String propName)
   {
       return properties.get(propName).get(String.class);
   }

   public Boolean getBooleanValue(String propName)
   {
       return properties.get(propName).get(Boolean.class);
   }

   public <T> T get(String propName,Class<T> klass)
   {
       return properties.get(propName).get(klass);
   }
   
   static class QpidProperty
   {
       private Object defValue;
       private String[] names;

       QpidProperty(Object defValue, String ... names)
       {
           this.defValue = defValue;
           this.names = names;
       }

       <T> T get(Class<T> klass)
       {
           Accessor acc = accessors.get(klass);
           for (String name : names)
           {
               Object obj = acc.get(name);
               if (obj != null)
               {
                   return klass.cast(obj);
               }
           }

           return klass.cast(defValue);
       }
   }

}
