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
 * Provides support to generate message property values.
 */
public abstract class GeneratedPropertySupport implements GeneratedPropertyValue
{
    private Object _lastValue;

    public GeneratedPropertySupport()
    {
        super();
        _lastValue = null;
    }

    @Override
    public Object getValue()
    {
        Object result = nextValue();
        result = evaluate(result);
        synchronized(this)
        {
            _lastValue = result;
        }
        return result;
    }

    protected Object evaluate(Object result)
    {
        while (result instanceof PropertyValue)
        {
            result = ((PropertyValue)result).getValue();
        }
        return result;
    }

    public abstract Object nextValue();

    public synchronized Object getLastValue()
    {
        return _lastValue;
    }

    @Override
    public String toString()
    {
        return "GeneratedPropertyValue [value=" + getLastValue() + ", @def=" + getDefinition() + "]";
    }

}
