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
package org.apache.qpid.server.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ParameterizedTypeImpl implements ParameterizedType
{
    private Class<?> _rawType;
    private Type[] _typeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Class<?>... typeArguments)
    {
        _rawType = rawType;
        _typeArguments = typeArguments;
    }
    @Override
    public Type[] getActualTypeArguments()
    {
        return _typeArguments;
    }

    @Override
    public Type getRawType()
    {
        return _rawType;
    }

    @Override
    public Type getOwnerType()
    {
        return _rawType.getDeclaringClass();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(_rawType.getName());
        if (_typeArguments != null)
        {
            sb.append("<");
            for (int i = 0; i < _typeArguments.length; i++)
            {
                sb.append(_typeArguments[i].getClass().getName());
                if (i < _typeArguments.length - 1)
                {
                    sb.append(",");
                }
            }
            sb.append(">");
        }
        return sb.toString();
    }
}
