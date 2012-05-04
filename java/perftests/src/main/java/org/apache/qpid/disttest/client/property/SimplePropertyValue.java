/*
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
 */
package org.apache.qpid.disttest.client.property;

/**
 * Simple property value holder for a constant properties.
 */
public class SimplePropertyValue implements PropertyValue
{
    private Object _value;

    public SimplePropertyValue()
    {
        super();
    }

    public SimplePropertyValue(Object value)
    {
        super();
        this._value = value;
    }

    @Override
    public Object getValue()
    {
        return _value;
    }

    @Override
    public String toString()
    {
        return "SimplePropertyValue [value=" + _value + "]";
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_value == null) ? 0 : _value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || getClass() != obj.getClass())
        {
            return false;
        }
        SimplePropertyValue other = (SimplePropertyValue) obj;
        if (_value == null && other._value != null)
        {
             return false;
        }
        return _value.equals(other._value);
    }

}
