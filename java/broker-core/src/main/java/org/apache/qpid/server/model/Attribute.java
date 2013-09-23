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

package org.apache.qpid.server.model;

public abstract class Attribute<C extends ConfiguredObject, T>
{
    private final String _name;
    public Attribute(String name)
    {
        _name = name;
    }

    public String getName()
    {
        return _name;
    }

    abstract public Class<T> getType();
    
    public T getValue(C configuredObject)
    {
        Object o = configuredObject.getAttribute(_name);
        if(getType().isInstance(o))            
        {
            return (T) o;
        }
        return null;
    }
    
    public T setValue(T expected, T desired, C configuredObject)
    {
        return (T) configuredObject.setAttribute(_name, expected, desired);
    }

    abstract public T setValue(String stringValue, C configuredObject);

    static class StringAttribute<C extends ConfiguredObject> extends Attribute<C, String>
    {

        public StringAttribute(String name)
        {
            super(name);
        }

        @Override
        public Class<String> getType()
        {
            return String.class;
        }

        @Override
        public String setValue(String stringValue, C configuredObject)
        {
            return setValue(getValue(configuredObject), stringValue, configuredObject);
        }

    }
    
    static class IntegerAttribute<C extends ConfiguredObject> extends Attribute<C, Integer>
    {

        public IntegerAttribute(String name)
        {
            super(name);
        }

        @Override
        public Class<Integer> getType()
        {
            return Integer.class;
        }

        @Override
        public Integer setValue(String stringValue, C configuredObject)
        {
            try
            {
                Integer val = Integer.valueOf(stringValue);
                return setValue(getValue(configuredObject), val, configuredObject);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
    }


    static class LongAttribute<C extends ConfiguredObject> extends Attribute<C, Long>
    {

        public LongAttribute(String name)
        {
            super(name);
        }

        @Override
        public Class<Long> getType()
        {
            return Long.class;
        }

        @Override
        public Long setValue(String stringValue, C configuredObject)
        {
            try
            {
                Long val = Long.valueOf(stringValue);
                return setValue(getValue(configuredObject), val, configuredObject);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
    }


    static class DoubleAttribute<C extends ConfiguredObject> extends Attribute<C, Double>
    {

        public DoubleAttribute(String name)
        {
            super(name);
        }

        @Override
        public Class<Double> getType()
        {
            return Double.class;
        }

        @Override
        public Double setValue(String stringValue, C configuredObject)
        {
            try
            {
                Double val = Double.valueOf(stringValue);
                return setValue(getValue(configuredObject), val, configuredObject);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
    }


    static class FloatAttribute<C extends ConfiguredObject> extends Attribute<C, Float>
    {

        public FloatAttribute(String name)
        {
            super(name);
        }

        @Override
        public Class<Float> getType()
        {
            return Float.class;
        }

        @Override
        public Float setValue(String stringValue, C configuredObject)
        {
            try
            {
                Float val = Float.valueOf(stringValue);
                return setValue(getValue(configuredObject), val, configuredObject);
            }
            catch (NumberFormatException e)
            {
                throw new IllegalArgumentException(e);
            }
        }
    }



}
