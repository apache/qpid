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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;

public class ConfiguredObjectRecordConverter
{
    private final Model _model;

    private static interface NameToIdResolver
    {
        public boolean resolve(Map<UUID, ConfiguredObjectRecord> objectsById);
    }

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
            Collection<NameToIdResolver> unresolved =
                    loadChild(rootClass, data, parent.getCategoryClass(), parent.getId(), objectsById);

            Iterator<NameToIdResolver> iterator = unresolved.iterator();
            while(iterator.hasNext())
            {
                if(iterator.next().resolve(objectsById))
                {
                    iterator.remove();
                }
            }

            if(!unresolved.isEmpty())
            {
                throw new IllegalArgumentException("Initial configuration has unresolved references");
            }
        }
        return objectsById.values();
    }


    private Collection<NameToIdResolver> loadChild(final Class<? extends ConfiguredObject> clazz,
                                                   final Map<String, Object> data,
                                                   final Class<? extends ConfiguredObject> parentClass,
                                                   final UUID parentId,
                                                   final Map<UUID, ConfiguredObjectRecord> records)
    {
        String idStr = (String) data.remove("id");

        final UUID id = idStr == null ? UUID.randomUUID() : UUID.fromString(idStr);
        final String type = clazz.getSimpleName();
        Map<String,UUID> parentMap = new HashMap<>();

        Collection<Class<? extends ConfiguredObject>> childClasses = _model.getChildTypes(clazz);
        List<NameToIdResolver> requiringResolution = new ArrayList<>();
        for(Class<? extends ConfiguredObject> childClass : childClasses)
        {
            final String childType = childClass.getSimpleName();
            String singularName = childType.toLowerCase();
            String attrName = singularName + (singularName.endsWith("s") ? "es" : "s");
            Object children = data.remove(attrName);
            if(children != null)
            {
                if(children instanceof Collection)
                {
                    for(Object child : (Collection)children)
                    {
                        if(child instanceof Map)
                        {
                            requiringResolution.addAll(loadChild(childClass, (Map) child, clazz, id, records));
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
                            final String ancestorClassName =
                                    _model.getAncestorClassWithGivenDescendant(clazz, otherParent).getSimpleName();
                            final String parentName = (String) otherParentId;
                            final String parentType = otherParent.getSimpleName();

                            requiringResolution.add(new AncestorFindingResolver(id,
                                                                                parentType,
                                                                                parentName,
                                                                                ancestorClassName));
                        }
                    }
                }

            }
        }

        records.put(id, new ConfiguredObjectRecordImpl(id, type, data, parentMap));

        return requiringResolution;
    }


    private static class AncestorFindingResolver implements NameToIdResolver
    {
        private final String _parentType;
        private final String _parentName;
        private final String _commonAncestorType;
        private final UUID _id;

        public AncestorFindingResolver(final UUID id,
                                       final String parentType,
                                       final String parentName,
                                       final String commonAncestorType)
        {
            _id = id;
            _parentType = parentType;
            _parentName = parentName;
            _commonAncestorType = commonAncestorType;
        }

        @Override
        public boolean resolve(final Map<UUID, ConfiguredObjectRecord> objectsById)
        {

            ConfiguredObjectRecord record = objectsById.get(_id);
            Collection<ConfiguredObjectRecord> recordsWithMatchingName = new ArrayList<>();
            for(ConfiguredObjectRecord possibleParentRecord : objectsById.values())
            {
                if(possibleParentRecord.getType().equals(_parentType)
                   && _parentName.equals(possibleParentRecord.getAttributes().get(ConfiguredObject.NAME)))
                {
                    recordsWithMatchingName.add(possibleParentRecord);
                }
            }
            for(ConfiguredObjectRecord candidate : recordsWithMatchingName)
            {
                UUID candidateAncestor = findAncestor(candidate, _commonAncestorType, objectsById);
                UUID recordAncestor = findAncestor(record, _commonAncestorType, objectsById);
                if(recordAncestor.equals(candidateAncestor))
                {
                    HashMap<String, UUID> parents = new HashMap<>(record.getParents());
                    parents.put(_parentType, candidate.getId());
                    objectsById.put(_id, new ConfiguredObjectRecordImpl(_id, record.getType(), record.getAttributes(), parents));

                    return true;
                }
            }
            return false;
        }

        private UUID findAncestor(final ConfiguredObjectRecord record,
                                  final String commonAncestorType,
                                  final Map<UUID, ConfiguredObjectRecord> objectsById)
        {
            UUID id = record.getParents().get(commonAncestorType);
            if(id == null)
            {
                for(UUID parentId : record.getParents().values())
                {
                    ConfiguredObjectRecord parent = objectsById.get(parentId);
                    if(parent != null)
                    {
                        id = findAncestor(parent, commonAncestorType, objectsById);
                    }
                    if(id != null)
                    {
                        break;
                    }
                }
            }
            return id;
        }
    }
}
