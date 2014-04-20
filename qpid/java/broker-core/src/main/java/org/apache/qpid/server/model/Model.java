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

import java.util.Collection;

public abstract class Model
{

    public static Class<? extends ConfiguredObject> getCategory(final Class<?> clazz)
    {
        ManagedObject annotation = clazz.getAnnotation(ManagedObject.class);
        if(annotation != null && annotation.category())
        {
            return (Class<? extends ConfiguredObject>) clazz;
        }
        for(Class<?> iface : clazz.getInterfaces() )
        {
            Class<? extends ConfiguredObject> cat = getCategory(iface);
            if(cat != null)
            {
                return cat;
            }
        }
        if(clazz.getSuperclass() != null)
        {
            return getCategory(clazz.getSuperclass());
        }
        return null;
    }

    public static String getType(final Class<? extends ConfiguredObject> clazz)
    {
        String type = getActualType(clazz);

        if("".equals(type))
        {
            Class<? extends ConfiguredObject> category = getCategory(clazz);
            if (category == null)
            {
                throw new IllegalArgumentException("No category for " + clazz.getSimpleName());
            }
            ManagedObject annotation = category.getAnnotation(ManagedObject.class);
            if (annotation == null)
            {
                throw new NullPointerException("No definition found for category " + category.getSimpleName());
            }
            if (!"".equals(annotation.defaultType()))
            {
                type = annotation.defaultType();
            }
            else
            {
                type = category.getSimpleName();
            }
        }
        return type;
    }

    private static String getActualType(final Class<? extends ConfiguredObject> clazz)
    {
        ManagedObject annotation = clazz.getAnnotation(ManagedObject.class);
        if(annotation != null)
        {
            if(!"".equals(annotation.type()))
            {
                return annotation.type();
            }
        }

        for(Class<?> iface : clazz.getInterfaces() )
        {
            if(ConfiguredObject.class.isAssignableFrom(iface))
            {
                String type = getActualType((Class<? extends ConfiguredObject>) iface);
                if(!"".equals(type))
                {
                    return type;
                }
            }
        }

        if(clazz.getSuperclass() != null && ConfiguredObject.class.isAssignableFrom(clazz.getSuperclass()))
        {
            String type = getActualType((Class<? extends ConfiguredObject>) clazz.getSuperclass());
            if(!"".equals(type))
            {
                return type;
            }
        }

        return "";
    }

    public abstract Collection<Class<? extends ConfiguredObject>> getSupportedCategories();
    public abstract Collection<Class<? extends ConfiguredObject>> getChildTypes(Class<? extends ConfiguredObject> parent);

    public abstract Class<? extends ConfiguredObject> getRootCategory();

    public abstract Collection<Class<? extends ConfiguredObject>> getParentTypes(Class<? extends ConfiguredObject> child);
    public abstract int getMajorVersion();
    public abstract int getMinorVersion();

}
