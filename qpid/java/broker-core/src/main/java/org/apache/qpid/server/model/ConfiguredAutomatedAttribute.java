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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.log4j.Logger;

public class ConfiguredAutomatedAttribute<C extends ConfiguredObject, T>  extends ConfiguredObjectAttribute<C,T>
{
    private static final Logger LOGGER = Logger.getLogger(ConfiguredAutomatedAttribute.class);

    private final ManagedAttribute _annotation;
    private final Method _validValuesMethod;

    ConfiguredAutomatedAttribute(final Class<C> clazz,
                                 final Method getter,
                                 final ManagedAttribute annotation)
    {
        super(clazz, getter);
        _annotation = annotation;
        Method validValuesMethod = null;

        if(_annotation.validValues().length == 1)
        {
            String validValue = _annotation.validValues()[0];

            validValuesMethod = getValidValuesMethod(validValue, clazz);
        }
        _validValuesMethod = validValuesMethod;
    }

    private Method getValidValuesMethod(final String validValue, final Class<C> clazz)
    {
        if(validValue.matches("([\\w][\\w\\d_]+\\.)+[\\w][\\w\\d_\\$]*#[\\w\\d_]+\\s*\\(\\s*\\)"))
        {
            String function = validValue;
            try
            {
                String className = function.split("#")[0].trim();
                String methodName = function.split("#")[1].split("\\(")[0].trim();
                Class<?> validValueCalculatingClass = Class.forName(className);
                Method method = validValueCalculatingClass.getMethod(methodName);
                if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
                {
                    if (Collection.class.isAssignableFrom(method.getReturnType()))
                    {
                        if (method.getGenericReturnType() instanceof ParameterizedType)
                        {
                            Type parameterizedType =
                                    ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
                            if (parameterizedType == String.class)
                            {
                                return method;
                            }
                        }
                    }
                }

            }
            catch (ClassNotFoundException | NoSuchMethodException e)
            {
                LOGGER.warn("The validValues of the " + getName() + " attribute in class " + clazz.getSimpleName()
                            + " has value '" + validValue + "' which looks like it should be a method,"
                            + " but no such method could be used.", e );
            }
        }
        return null;
    }

    public boolean isAutomated()
    {
        return true;
    }

    public boolean isDerived()
    {
        return false;
    }

    public String defaultValue()
    {
        return _annotation.defaultValue();
    }

    public boolean isSecure()
    {
        return _annotation.secure();
    }

    public boolean isMandatory()
    {
        return _annotation.mandatory();
    }

    public boolean isPersisted()
    {
        return _annotation.persist();
    }

    @Override
    public boolean isOversized()
    {
        return _annotation.oversize();
    }

    @Override
    public String getOversizedAltText()
    {
        return _annotation.oversizedAltText();
    }

    public String getDescription()
    {
        return _annotation.description();
    }

    public Collection<String> validValues()
    {
        if(_validValuesMethod != null)
        {
            try
            {
                return (Collection<String>) _validValuesMethod.invoke(null);
            }
            catch (InvocationTargetException | IllegalAccessException e)
            {
                LOGGER.warn("Could not execute the validValues generation method " + _validValuesMethod.getName(), e);
                return Collections.emptySet();
            }
        }
        else
        {
            return Arrays.asList(_annotation.validValues());
        }
    }

    /** Returns true iff this attribute has valid values defined */
    public boolean hasValidValues()
    {
        return validValues() != null && validValues().size() > 0;
    }
}
