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

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.store.ConfiguredObjectDependency;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;

abstract public class AbstractConfiguredObjectTypeFactory<X extends AbstractConfiguredObject<X>> implements ConfiguredObjectTypeFactory<X>
{
    private final Class<X> _clazz;

    public AbstractConfiguredObjectTypeFactory(final Class<X> clazz)
    {
        _clazz = clazz;
    }

    @Override
    public final String getType()
    {
        return ConfiguredObjectTypeRegistry.getType(_clazz);
    }

    @Override
    public final Class<? super X> getCategoryClass()
    {
        return (Class<? super X>) ConfiguredObjectTypeRegistry.getCategory(_clazz);
    }

    @Override
    public X create(final ConfiguredObjectFactory factory,
                    final Map<String, Object> attributes,
                    final ConfiguredObject<?>... parents)
    {
        X instance = createInstance(attributes, parents);
        instance.create();
        return instance;
    }

    protected abstract X createInstance(Map<String, Object> attributes, ConfiguredObject<?>... parents);

    public final <C extends ConfiguredObject<?>> C getParent(Class<C> parentClass, ConfiguredObject<?>... parents)
    {

        if(!parents[0].getModel().getParentTypes((Class<? extends ConfiguredObject>) getCategoryClass()).contains(
                ConfiguredObjectTypeRegistry.getCategory(parentClass)))
        {
            throw new IllegalArgumentException(parentClass.getSimpleName() + " is not a parent of " + _clazz.getSimpleName());
        }

        for(ConfiguredObject<?> parent : parents)
        {
            if(parentClass.isInstance(parent))
            {
                return (C) parent;
            }
        }
        throw new IllegalArgumentException("No parent of class " + parentClass.getSimpleName() + " found.");
    }

    @Override
    public UnresolvedConfiguredObject<X> recover(final ConfiguredObjectFactory factory,
                                                 final ConfiguredObjectRecord record,
                                                 final ConfiguredObject<?>... parents)
    {
        return new GenericUnresolvedConfiguredObject( record, parents );
    }


    private class GenericUnresolvedConfiguredObject extends AbstractUnresolvedObject<X>
    {
        public GenericUnresolvedConfiguredObject(
                final ConfiguredObjectRecord record, final ConfiguredObject<?>[] parents)
        {
            super(_clazz, record, parents);
        }

        @Override
        protected <C extends ConfiguredObject<C>> void resolved(final ConfiguredObjectDependency<C> dependency,
                                                                 final C value)
        {

        }

        @Override
        public X resolve()
        {
            Map<String,Object> attributesWithId = new HashMap<String, Object>(getRecord().getAttributes());
            attributesWithId.put(ConfiguredObject.ID, getRecord().getId());
            X instance = createInstance(attributesWithId, getParents());
            instance.registerWithParents();
            return instance;
        }
    }
}
