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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.store.ConfiguredObjectDependency;
import org.apache.qpid.server.store.ConfiguredObjectIdDependency;
import org.apache.qpid.server.store.ConfiguredObjectNameDependency;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;

public abstract class AbstractUnresolvedObject<C extends ConfiguredObject<C>> implements UnresolvedConfiguredObject<C>
{
    private final Class<C> _clazz;
    private final Collection<ConfiguredObjectDependency<?>> _unresolvedObjects = new ArrayList<ConfiguredObjectDependency<?>>();
    private final ConfiguredObjectRecord _record;
    private final ConfiguredObject<?>[] _parents;

    protected AbstractUnresolvedObject(Class<C> clazz,
                                       ConfiguredObjectRecord record,
                                       ConfiguredObject<?>... parents)
    {
        _clazz = clazz;
        _record = record;
        _parents = parents;

        Collection<ConfiguredObjectAttribute<? super C, ?>> attributes =
                parents[0].getModel().getTypeRegistry().getAttributes(clazz);
        for(ConfiguredObjectAttribute<? super C, ?> attribute : attributes)
        {
            if(attribute.isPersisted())
            {
                final Class<?> attributeType = attribute.getType();
                if (ConfiguredObject.class.isAssignableFrom(attributeType))
                {
                    addUnresolvedObject((Class<? extends ConfiguredObject>) attributeType,
                                        attribute.getName(),
                                        attribute.isAutomated() && ((ConfiguredAutomatedAttribute<? super C,?>)attribute).isMandatory());
                }
                else if (Collection.class.isAssignableFrom(attributeType))
                {
                    Type returnType = attribute.getGetter().getGenericReturnType();
                    Class<? extends ConfiguredObject> attrClass = getMemberType(returnType);
                    if (attrClass != null)
                    {
                        Object attrValue = _record.getAttributes().get(attribute.getName());
                        if (attrValue != null)
                        {
                            if (attrValue instanceof Collection)
                            {
                                for (Object val : (Collection) attrValue)
                                {
                                    addUnresolvedObject(attrClass, attribute.getName(), val);
                                }
                            }
                            else if (attrValue instanceof Object[])
                            {
                                for (Object val : (Object[]) attrValue)
                                {
                                    addUnresolvedObject(attrClass, attribute.getName(), val);
                                }
                            }
                            else
                            {
                                addUnresolvedObject(attrClass, attribute.getName(), attrValue);
                            }
                        }
                    }


                }
            }
        }
    }

    private Class<? extends ConfiguredObject> getMemberType(Type returnType)
    {
        Class<? extends ConfiguredObject> categoryClass = null;

        if (returnType instanceof ParameterizedType)
        {
            Type type = ((ParameterizedType) returnType).getActualTypeArguments()[0];
            if (type instanceof Class && ConfiguredObject.class.isAssignableFrom((Class)type))
            {
                categoryClass = (Class<? extends ConfiguredObject>) type;
            }
            else if (type instanceof ParameterizedType)
            {
                Type rawType = ((ParameterizedType) type).getRawType();
                if (rawType instanceof Class && ConfiguredObject.class.isAssignableFrom((Class)rawType))
                {
                    categoryClass = (Class<? extends ConfiguredObject>) rawType;
                }
            }
            else if (type instanceof TypeVariable)
            {
                Type[] bounds = ((TypeVariable) type).getBounds();
                for(Type boundType : bounds)
                {
                    categoryClass = getMemberType(boundType);
                    if(categoryClass != null)
                    {
                        break;
                    }
                }
            }
        }
        return categoryClass;
    }


    public ConfiguredObjectRecord getRecord()
    {
        return _record;
    }

    public ConfiguredObject<?>[] getParents()
    {
        return _parents;
    }

    private void addUnresolvedObject(final Class<? extends ConfiguredObject> clazz,
                                     final String attributeName,
                                     boolean mandatory)
    {
        Object attrValue = _record.getAttributes().get(attributeName);
        if(attrValue != null)
        {
            addUnresolvedObject(clazz, attributeName, attrValue);
        }
        else if(mandatory)
        {
            throw new IllegalConfigurationException("Missing attribute " + attributeName + " has no value");
        }
    }

    private void addUnresolvedObject(final Class<? extends ConfiguredObject> clazz,
                                     final String attributeName,
                                     final Object attrValue)
    {
        if(attrValue instanceof UUID)
        {
            _unresolvedObjects.add(new IdDependency(clazz, attributeName, (UUID) attrValue));
        }
        else if(attrValue instanceof String)
        {
            try
            {
                _unresolvedObjects.add(new IdDependency(clazz, attributeName, UUID.fromString((String) attrValue)));
            }
            catch(IllegalArgumentException e)
            {
                _unresolvedObjects.add(new NameDependency(clazz, attributeName, (String) attrValue));
            }
        }
        else if(!clazz.isInstance(attrValue))
        {
            throw new IllegalArgumentException("Cannot convert from type " + attrValue.getClass() + " to a configured object dependency");
        }
    }


    protected abstract  <X extends ConfiguredObject<X>>  void resolved(ConfiguredObjectDependency<X> dependency, X value);

    @Override
    public Collection<ConfiguredObjectDependency<?>> getUnresolvedDependencies()
    {
        return _unresolvedObjects;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{" +
               "class=" + _clazz.getSimpleName() +
               ", unresolvedObjects=" + _unresolvedObjects +
               '}';
    }

    private abstract class Dependency<X extends ConfiguredObject<X>> implements ConfiguredObjectDependency<X>
    {
        private final Class<X> _clazz;
        private final String _attributeName;

        public Dependency(final Class<X> clazz,
                          final String attributeName)
        {
            _clazz = clazz;
            _attributeName = attributeName;
        }

        @Override
        public final Class<X> getCategoryClass()
        {
            return _clazz;
        }

        @Override
        public final void resolve(final X object)
        {
            _unresolvedObjects.remove(this);
            resolved(this, object);
        }

        public final String getAttributeName()
        {
            return _attributeName;
        }

    }

    private class IdDependency<X extends ConfiguredObject<X>> extends Dependency<X> implements ConfiguredObjectIdDependency<X>
    {
        private final UUID _id;

        public IdDependency(final Class<X> clazz,
                            final String attributeName,
                            final UUID id)
        {
            super(clazz, attributeName);
            _id = id;
        }

        @Override
        public UUID getId()
        {
            return _id;
        }

        @Override
        public String toString()
        {
            return "IdDependency{" + getCategoryClass().getSimpleName() + ", " + _id + " }";
        }
    }

    private class NameDependency<X extends ConfiguredObject<X>> extends Dependency<X> implements ConfiguredObjectNameDependency<X>
    {

        private final String _name;

        public NameDependency(final Class<X> clazz,
                              final String attributeName,
                              final String attrValue)
        {
            super(clazz, attributeName);
            _name = attrValue;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public String toString()
        {
            return "NameDependency{" + getCategoryClass().getSimpleName() + ", \"" + _name + "\" }";
        }
    }
}


