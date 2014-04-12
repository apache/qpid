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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class ConfiguredObjectFactory
{
    private final Map<String, String> _defaultTypes = new HashMap<String, String>();
    private final Map<String, Map<String, ConfiguredObjectTypeFactory>> _allFactories =
            new HashMap<String, Map<String, ConfiguredObjectTypeFactory>>();
    private final Map<String, Collection<String>> _supportedTypes = new HashMap<String, Collection<String>>();

    private final Model _model;

    public ConfiguredObjectFactory(Model model)
    {
        _model = model;

        QpidServiceLoader<ConfiguredObjectTypeFactory> serviceLoader = new QpidServiceLoader<ConfiguredObjectTypeFactory>();
        Iterable<ConfiguredObjectTypeFactory> allFactories = serviceLoader.instancesOf(ConfiguredObjectTypeFactory.class);
        for(ConfiguredObjectTypeFactory factory : allFactories)
        {
            final Class<? extends ConfiguredObject> categoryClass = factory.getCategoryClass();
            final String categoryName = categoryClass.getSimpleName();

            Map<String, ConfiguredObjectTypeFactory> categoryFactories = _allFactories.get(categoryName);
            if(categoryFactories == null)
            {
                categoryFactories = new HashMap<String, ConfiguredObjectTypeFactory>();
                _allFactories.put(categoryName, categoryFactories);
                _supportedTypes.put(categoryName, new ArrayList<String>());
                ManagedObject annotation = categoryClass.getAnnotation(ManagedObject.class);
                if(annotation != null && !"".equals(annotation.defaultType()))
                {
                    _defaultTypes.put(categoryName, annotation.defaultType());
                }

            }
            if(categoryFactories.put(factory.getType(),factory) != null)
            {
                throw new ServerScopedRuntimeException("Misconfiguration - there is more than one factory defined for class " + categoryName
                                                       + " with type " + factory.getType());
            }
            if(factory.getType() != null)
            {
                _supportedTypes.get(categoryName).add(factory.getType());
            }
        }
    }

    public <X extends ConfiguredObject<X>> UnresolvedConfiguredObject<X> recover(ConfiguredObjectRecord record,
                                                                                 ConfiguredObject<?>... parents)
    {
        String category = record.getType();


        String type = (String) record.getAttributes().get(ConfiguredObject.TYPE);

        ConfiguredObjectTypeFactory<X> factory = getConfiguredObjectTypeFactory(category, type);

        if(factory == null)
        {
            throw new ServerScopedRuntimeException("No factory defined for ConfiguredObject of category " + category + " and type " + type);
        }

        return factory.recover(record, parents);
    }

    public <X extends ConfiguredObject<X>> ConfiguredObjectTypeFactory<X> getConfiguredObjectTypeFactory(final Class<X> categoryClass, Map<String,Object> attributes)
    {
        final String category = categoryClass.getSimpleName();
        Map<String, ConfiguredObjectTypeFactory> categoryFactories = _allFactories.get(category);
        if(categoryFactories == null)
        {
            throw new ServerScopedRuntimeException("No factory defined for ConfiguredObject of category " + category);
        }
        String type = (String) attributes.get(ConfiguredObject.TYPE);

        ConfiguredObjectTypeFactory<X> factory;

        if(type != null)
        {
            factory = getConfiguredObjectTypeFactory(category, type);
        }
        else
        {
            factory = getConfiguredObjectTypeFactory(category, null);
            if(factory == null)
            {
                ManagedObject annotation = categoryClass.getAnnotation(ManagedObject.class);
                factory = getConfiguredObjectTypeFactory(category, annotation.defaultType());
            }
        }
        return factory;
    }

    public <X extends ConfiguredObject<X>> ConfiguredObjectTypeFactory<X> getConfiguredObjectTypeFactory(final String category, final String type)
    {
        Map<String, ConfiguredObjectTypeFactory> categoryFactories = _allFactories.get(category);
        if(categoryFactories == null)
        {
            throw new ServerScopedRuntimeException("No factory defined for ConfiguredObject of category " + category);
        }
        ConfiguredObjectTypeFactory factory = categoryFactories.get(type);
        if(factory == null)
        {
            factory = categoryFactories.get(_defaultTypes.get(category));
        }
        return factory;
    }

    public Collection<String> getSupportedTypes(Class<? extends ConfiguredObject> category)
    {
        return Collections.unmodifiableCollection(_supportedTypes.get(category.getSimpleName()));
    }


    public Model getModel()
    {
        return _model;
    }

}
