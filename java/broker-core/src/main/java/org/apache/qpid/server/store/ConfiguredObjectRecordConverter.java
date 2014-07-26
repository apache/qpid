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
package org.apache.qpid.server.store;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;

public class ConfiguredObjectRecordConverter
{
    private final Model _model;

    public ConfiguredObjectRecordConverter(final Model model)
    {
        _model = model;
    }

    public Collection<ConfiguredObjectRecord> readFromJson(final Class<? extends ConfiguredObject> rootClass,
                                                           final ConfiguredObject<?> parent, final Reader reader) throws IOException
    {
        Map<UUID, ConfiguredObjectRecord> objectsById = new HashMap<>();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        Map data = objectMapper.readValue(reader, Map.class);
        if(!data.isEmpty())
        {
            loadChild(rootClass, data, parent.getCategoryClass(), parent.getId(), objectsById);
        }
        return objectsById.values();
    }


    private void loadChild(final Class<? extends ConfiguredObject> clazz,
                           final Map<String, Object> data,
                           final Class<? extends ConfiguredObject> parentClass,
                           final UUID parentId, final Map<UUID, ConfiguredObjectRecord> records)
    {
        String idStr = (String) data.remove("id");

        final UUID id = idStr == null ? UUID.randomUUID() : UUID.fromString(idStr);
        final String type = clazz.getSimpleName();
        Map<String,UUID> parentMap = new HashMap<>();

        Collection<Class<? extends ConfiguredObject>> childClasses = _model.getChildTypes(clazz);
        for(Class<? extends ConfiguredObject> childClass : childClasses)
        {
            final String childType = childClass.getSimpleName();
            String attrName = childType.toLowerCase() + "s";
            Object children = data.remove(attrName);
            if(children != null)
            {
                if(children instanceof Collection)
                {
                    for(Object child : (Collection)children)
                    {
                        if(child instanceof Map)
                        {
                            loadChild(childClass, (Map)child, clazz, id, records);
                        }
                    }
                }
            }

        }
        if(parentId != null)
        {
            parentMap.put(parentClass.getSimpleName(),parentId);
            for(Class<? extends ConfiguredObject> otherParent : _model.getParentTypes(clazz))
            {
                if(otherParent != parentClass)
                {
                    final String otherParentAttr = otherParent.getSimpleName().toLowerCase();
                    Object otherParentId = data.remove(otherParentAttr);
                    if(otherParentId instanceof String)
                    {
                        try
                        {
                            parentMap.put(otherParent.getSimpleName(), UUID.fromString((String) otherParentId));
                        }
                        catch(IllegalArgumentException e)
                        {
                            // TODO
                        }
                    }
                }

            }
        }

        records.put(id, new ConfiguredObjectRecordImpl(id, type, data, parentMap));

    }


}
