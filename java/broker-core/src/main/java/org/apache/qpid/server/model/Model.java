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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class Model
{

    <X extends ConfiguredObject<X>> Collection<X> getReachableObjects(final ConfiguredObject<?> object,
                                                                      final Class<X> clazz)
    {
        Class<? extends ConfiguredObject> category = ConfiguredObjectTypeRegistry.getCategory(object.getClass());
        Class<? extends ConfiguredObject> ancestorClass = getAncestorClassWithGivenDescendant(category, clazz);
        if(ancestorClass != null)
        {
            ConfiguredObject ancestor = getAncestor(ancestorClass, category, object);
            if(ancestor != null)
            {
                return getAllDescendants(ancestor, ancestorClass, clazz);
            }
        }
        return null;
    }

    <X extends ConfiguredObject<X>> Collection<X> getAllDescendants(final ConfiguredObject ancestor,
                                                                    final Class<? extends ConfiguredObject> ancestorClass,
                                                                    final Class<X> clazz)
    {
        Set<X> descendants = new HashSet<X>();
        for(Class<? extends ConfiguredObject> childClass : getChildTypes(ancestorClass))
        {
            Collection<? extends ConfiguredObject> children = ancestor.getChildren(childClass);
            if(childClass == clazz)
            {

                if(children != null)
                {
                    descendants.addAll((Collection<X>)children);
                }
            }
            else
            {
                if(children != null)
                {
                    for(ConfiguredObject child : children)
                    {
                        descendants.addAll(getAllDescendants(child, childClass, clazz));
                    }
                }
            }
        }
        return descendants;
    }

    <C extends ConfiguredObject> C getAncestor(final Class<C> ancestorClass,
                                               final Class<? extends ConfiguredObject> category,
                                               final ConfiguredObject<?> object)
    {
        if(ancestorClass.isInstance(object))
        {
            return (C) object;
        }
        else
        {
            for(Class<? extends ConfiguredObject> parentClass : object.getModel().getParentTypes(category))
            {
                ConfiguredObject<?> parent = object.getParent(parentClass);
                ConfiguredObject<?> ancestor = getAncestor(ancestorClass, parentClass, parent);
                if(ancestor != null)
                {
                    return (C) ancestor;
                }
            }
        }
        return null;
    }

    public Class<? extends ConfiguredObject> getAncestorClassWithGivenDescendant(
            final Class<? extends ConfiguredObject> category,
            final Class<? extends ConfiguredObject> descendantClass)
    {
        Collection<Class<? extends ConfiguredObject>> candidateClasses =
                Collections.<Class<? extends ConfiguredObject>>singleton(category);
        while(!candidateClasses.isEmpty())
        {
            for(Class<? extends ConfiguredObject> candidate : candidateClasses)
            {
                if(hasDescendant(candidate, descendantClass))
                {
                    return candidate;
                }
            }
            Set<Class<? extends ConfiguredObject>> previous = new HashSet<>(candidateClasses);
            candidateClasses = new HashSet<>();
            for(Class<? extends ConfiguredObject> prev : previous)
            {
                candidateClasses.addAll(getParentTypes(prev));
            }
        }
        return null;
    }

    private boolean hasDescendant(final Class<? extends ConfiguredObject> candidate,
                                  final Class<? extends ConfiguredObject> descendantClass)
    {
        int oldSize = 0;

        Set<Class<? extends ConfiguredObject>> allDescendants = new HashSet<>(getChildTypes(candidate));
        while(allDescendants.size() > oldSize)
        {
            oldSize = allDescendants.size();
            Set<Class<? extends ConfiguredObject>> prev = new HashSet<>(allDescendants);
            for(Class<? extends ConfiguredObject> clazz : prev)
            {
                allDescendants.addAll(getChildTypes(clazz));
            }
            if(allDescendants.contains(descendantClass))
            {
                break;
            }
        }
        return allDescendants.contains(descendantClass);
    }


    public abstract Collection<Class<? extends ConfiguredObject>> getSupportedCategories();
    public abstract Collection<Class<? extends ConfiguredObject>> getChildTypes(Class<? extends ConfiguredObject> parent);

    public abstract Class<? extends ConfiguredObject> getRootCategory();

    public abstract Collection<Class<? extends ConfiguredObject>> getParentTypes(Class<? extends ConfiguredObject> child);
    public abstract int getMajorVersion();
    public abstract int getMinorVersion();

    public abstract ConfiguredObjectFactory getObjectFactory();

    public abstract ConfiguredObjectTypeRegistry getTypeRegistry();

}
