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

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public abstract class ConfiguredObjectAttribute<C extends ConfiguredObject, T> extends ConfiguredObjectAttributeOrStatistic<C,T>
{
    ConfiguredObjectAttribute(Class<C> clazz,
                              final Method getter)
    {
        super(getter);
        if(getter.getParameterTypes().length != 0)
        {
            throw new IllegalArgumentException("ManagedAttribute annotation should only be added to no-arg getters");
        }
    }

    public abstract boolean isAutomated();

    public abstract boolean isDerived();

    public abstract boolean isSecure();

    public abstract boolean isPersisted();

    public abstract String getDescription();

    public T convert(final Object value, C object)
    {
        final AttributeValueConverter<T> converter = getConverter();
        try
        {
            return converter.convert(value, object);
        }
        catch (IllegalArgumentException iae)
        {
            Type returnType = getGetter().getGenericReturnType();
            String simpleName = returnType instanceof Class ? ((Class) returnType).getSimpleName() : returnType.toString();

            throw new IllegalArgumentException("Cannot convert '" + value
                                               + "' into a " + simpleName
                                               + " for attribute " + getName()
                                               + " (" + iae.getMessage() + ")", iae);
        }
    }
}
