/* Licensed to the Apache Software Foundation (ASF) under one
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
 */

package org.apache.qpid.configuration;

import org.apache.qpid.configuration.Accessor.SystemPropertyAccessor;

abstract class QpidProperty<T>
{
    private T defValue;
    private String[] names;
    protected Accessor accessor;

    QpidProperty(T defValue, String... names)
    {
        this(new SystemPropertyAccessor(),defValue,names);
    }
    
    QpidProperty(Accessor accessor,T defValue, String... names)
    {
        this.accessor = accessor;
        this.defValue = defValue;
        this.names = names;
    }

    T get()
    {
        for (String name : names)
        {
            T obj = getByName(name);
            if (obj != null)
            {
                return obj;
            }
        }

        return defValue;
    }

    protected abstract T getByName(String name);

    public static QpidProperty<Boolean> booleanProperty(Boolean defaultValue,
            String... names)
    {
        return new QpidBooleanProperty(defaultValue, names);
    }

    public static QpidProperty<Boolean> booleanProperty(Accessor accessor,
            Boolean defaultValue,String... names)
    {
        return new QpidBooleanProperty(accessor,defaultValue, names);
    }
    
    public static QpidProperty<Integer> intProperty(Integer defaultValue,
            String... names)
    {
        return new QpidIntProperty(defaultValue, names);
    }

    public static QpidProperty<Integer> intProperty(Accessor accessor,
            Integer defaultValue, String... names)
    {
        return new QpidIntProperty(accessor,defaultValue, names);
    }
    
    public static QpidProperty<Long> longProperty(Long defaultValue,
            String... names)
    {
        return new QpidLongProperty(defaultValue, names);
    }

    public static QpidProperty<Long> longProperty(Accessor accessor,
            Long defaultValue, String... names)
    {
        return new QpidLongProperty(accessor,defaultValue, names);
    }
    
    public static QpidProperty<String> stringProperty(String defaultValue,
            String... names)
    {
        return new QpidStringProperty(defaultValue, names);
    }

    public static QpidProperty<String> stringProperty(Accessor accessor,
            String defaultValue,String... names)
    {
        return new QpidStringProperty(accessor,defaultValue, names);
    }
    
    static class QpidBooleanProperty extends QpidProperty<Boolean>
    {
        QpidBooleanProperty(Boolean defValue, String... names)
        {
            super(defValue, names);
        }
        
        QpidBooleanProperty(Accessor accessor,Boolean defValue, String... names)
        {
            super(accessor,defValue, names);
        }

        @Override
        protected Boolean getByName(String name)
        {
            return accessor.getBoolean(name);
        }
    }

    static class QpidIntProperty extends QpidProperty<Integer>
    {
        QpidIntProperty(Integer defValue, String... names)
        {
            super(defValue, names);
        }

        QpidIntProperty(Accessor accessor,Integer defValue, String... names)
        {
            super(accessor,defValue, names);
        }
        
        @Override
        protected Integer getByName(String name)
        {
            return accessor.getInt(name);
        }
    }

    static class QpidLongProperty extends QpidProperty<Long>
    {
        QpidLongProperty(Long defValue, String... names)
        {
            super(defValue, names);
        }

        QpidLongProperty(Accessor accessor,Long defValue, String... names)
        {
            super(accessor,defValue, names);
        }
        
        @Override
        protected Long getByName(String name)
        {
            return accessor.getLong(name);
        }
    }

    static class QpidStringProperty extends QpidProperty<String>
    {
        QpidStringProperty(String defValue, String... names)
        {
            super(defValue, names);
        }

        QpidStringProperty(Accessor accessor,String defValue, String... names)
        {
            super(accessor,defValue, names);
        }
        
        @Override
        protected String getByName(String name)
        {
            return accessor.getString(name);
        }
    }

}