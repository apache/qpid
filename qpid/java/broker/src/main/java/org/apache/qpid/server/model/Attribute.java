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

public class Attribute<C extends ConfiguredObject, T>
{
    private final String _name;
    private final Class<T> _type;

    public Attribute(String name, Class<T> type)
    {
        _name = name;
        _type = type;
    }

    public String getName()
    {
        return _name;
    }

    public Class<T> getType()
    {
        return _type;
    }
    
    public T getValue(C configuredObject)
    {
        Object o = configuredObject.getAttribute(_name);
        if(_type.isInstance(o))
        {
            return (T) o;
        }
        return null;
    }
    
    public T setValue(T expected, T desired, C configuredObject)
    {
        return (T) configuredObject.setAttribute(_name, expected, desired);
    }

}
