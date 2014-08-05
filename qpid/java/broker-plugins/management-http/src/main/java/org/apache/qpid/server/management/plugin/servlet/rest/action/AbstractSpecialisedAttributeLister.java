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
package org.apache.qpid.server.management.plugin.servlet.rest.action;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.qpid.server.management.plugin.servlet.rest.Action;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectAttribute;
import org.apache.qpid.server.model.ConfiguredObjectTypeRegistry;

abstract class AbstractSpecialisedAttributeLister<T extends ConfiguredObject>  implements Action
{


    private static final String ATTRIBUTES = "attributes";
    private static final String DESCRIPTIONS = "descriptions";

    @Override
    final public Object perform(Map<String, Object> request, Broker broker)
    {
        ConfiguredObjectTypeRegistry typeRegistry = broker.getModel().getTypeRegistry();
        Collection<Class<? extends ConfiguredObject>> groupProviderTypes =
                typeRegistry.getTypeSpecialisations(getCategoryClass());

        Map<String, Object> attributes = new TreeMap<String, Object>();

        for (Class<? extends ConfiguredObject> groupProviderType : groupProviderTypes)
        {
            Collection<ConfiguredObjectAttribute<?, ?>> typeSpecificAttributes =
                    typeRegistry.getTypeSpecificAttributes(groupProviderType);

            Map<String, Object> data = new HashMap<String, Object>();

            Collection<String> attributeNames = new TreeSet<>();
            Map<String,String> descriptions = new HashMap<>();
            for(ConfiguredObjectAttribute<?, ?> attr : typeSpecificAttributes)
            {
                attributeNames.add(attr.getName());
                if(!"".equals(attr.getDescription()))
                {
                    descriptions.put(attr.getName(), attr.getDescription());
                }
            }
            data.put(ATTRIBUTES, attributeNames);
            data.put(DESCRIPTIONS, descriptions);

            attributes.put(ConfiguredObjectTypeRegistry.getType(groupProviderType), data);
        }
        return attributes;
    }

    abstract Class<T> getCategoryClass();

}
