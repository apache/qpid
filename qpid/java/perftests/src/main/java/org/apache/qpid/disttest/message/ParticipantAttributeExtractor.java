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
package org.apache.qpid.disttest.message;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;


public class ParticipantAttributeExtractor
{
    public static Map<ParticipantAttribute, Object> getAttributes(Object targetObject)
    {
        Map<ParticipantAttribute, Object> attributes = new HashMap<ParticipantAttribute, Object>();


        PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(targetObject);
        for (PropertyDescriptor propertyDescriptor : descriptors)
        {
            final Method readMethod = getPropertyReadMethod(targetObject, propertyDescriptor);

            for (Annotation annotation : readMethod.getDeclaredAnnotations())
            {
                if (annotation instanceof OutputAttribute)
                {
                    OutputAttribute outputAttribute = (OutputAttribute) annotation;

                    Object value = getPropertyValue(targetObject, propertyDescriptor.getName());
                    attributes.put(outputAttribute.attribute(), value);
                }
            }
        }

        return attributes;
    }

    public static Method getPropertyReadMethod(Object targetObject, PropertyDescriptor propertyDescriptor)
    {
        final Method readMethod = propertyDescriptor.getReadMethod();

        if (readMethod == null)
        {
            throw new RuntimeException("No read method for property " + propertyDescriptor.getName() + " on " + targetObject);
        }
        return readMethod;
    }

    public static Object getPropertyValue(Object targetObject, String propertyName)
    {
        try
        {
            return PropertyUtils.getProperty(targetObject, propertyName);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Couldn't get value of property " + propertyName + " from " + targetObject, e);
        }
        catch (InvocationTargetException e)
        {
            throw new RuntimeException("Couldn't get value of property " + propertyName + " from " + targetObject, e);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException("Couldn't get value of property " + propertyName + " from " + targetObject, e);
        }

    }
}
