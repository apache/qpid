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
package org.apache.qpid.server.configuration.store;

import static org.apache.qpid.server.configuration.ConfigurationEntry.ATTRIBUTE_NAME;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.util.Strings;
import org.apache.qpid.util.Strings.ChainedResolver;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.node.ArrayNode;

public class MemoryConfigurationEntryStore implements ConfigurationEntryStore
{

    public static final String STORE_TYPE = "memory";

    private static final String DEFAULT_BROKER_NAME = "Broker";
    private static final String ID = "id";
    private static final String TYPE = "@type";

    static final int STORE_VERSION = 1;

    private final ObjectMapper _objectMapper;
    private final Map<UUID, ConfigurationEntry> _entries;
    private final Map<String, Class<? extends ConfiguredObject>> _brokerChildrenRelationshipMap;
    private final ConfigurationEntryStoreUtil _util = new ConfigurationEntryStoreUtil();

    private String _storeLocation;
    private UUID _rootId;

    private boolean _generatedObjectIdDuringLoad;

    private ChainedResolver _resolver;

    protected MemoryConfigurationEntryStore(Map<String, String> configProperties)
    {
        _objectMapper = new ObjectMapper();
        _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        _objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        _entries = new HashMap<UUID, ConfigurationEntry>();
        _brokerChildrenRelationshipMap = buildRelationshipClassMap();
        _resolver = new Strings.ChainedResolver(Strings.SYSTEM_RESOLVER,
                                                new Strings.MapResolver(configProperties));
    }

    MemoryConfigurationEntryStore(String json, Map<String, String> configProperties)
    {
        this(configProperties);
        if (json == null || "".equals(json))
        {
            createRootEntry();
        }
        else
        {
            loadFromJson(json);
        }
    }

    public MemoryConfigurationEntryStore(String initialStoreLocation, ConfigurationEntryStore initialStore, Map<String, String> configProperties)
    {
        this(configProperties);
        if (initialStore == null && (initialStoreLocation == null || "".equals(initialStoreLocation) ))
        {
            throw new IllegalConfigurationException("Cannot instantiate the memory broker store as neither initial store nor initial store location is provided");
        }

        if (initialStore != null)
        {
            if (initialStore instanceof MemoryConfigurationEntryStore)
            {
                _storeLocation = initialStore.getStoreLocation();
            }
            _rootId = initialStore.getRootEntry().getId();
            copyEntry(_rootId, initialStore, _entries);
        }
        else
        {
            _storeLocation = initialStoreLocation;
            load(_util.toURL(_storeLocation));
        }
    }

    @Override
    public synchronized UUID[] remove(UUID... entryIds)
    {
        List<UUID> removedIds = new ArrayList<UUID>();
        for (UUID uuid : entryIds)
        {
            if (_rootId.equals(uuid))
            {
                throw new IllegalConfigurationException("Cannot remove root entry");
            }
        }
        for (UUID uuid : entryIds)
        {
            if (removeInternal(uuid))
            {
                // remove references to the entry from parent entries
                for (ConfigurationEntry entry : _entries.values())
                {
                    if (entry.hasChild(uuid))
                    {
                        Set<UUID> children = new HashSet<UUID>(entry.getChildrenIds());
                        children.remove(uuid);
                        ConfigurationEntry referral = new ConfigurationEntry(entry.getId(), entry.getType(),
                                entry.getAttributes(), children, this);
                        _entries.put(entry.getId(), referral);
                    }
                }
                removedIds.add(uuid);
            }
        }

        return removedIds.toArray(new UUID[removedIds.size()]);
    }

    @Override
    public synchronized void save(ConfigurationEntry... entries)
    {
        replaceEntries(entries);
    }

    @Override
    public ConfigurationEntry getRootEntry()
    {
        return getEntry(_rootId);
    }

    @Override
    public synchronized ConfigurationEntry getEntry(UUID id)
    {
        return _entries.get(id);
    }

    /**
     * Copies the store into the given location
     *
     * @param target location to copy store into
     * @throws IllegalConfigurationException if store cannot be copied into given location
     */
    public void copyTo(String copyLocation)
    {
        File file = new File(copyLocation);
        if (!file.exists())
        {
            createFileIfNotExist(file);
        }
        saveAsTree(file);
    }

    @Override
    public String getStoreLocation()
    {
        return _storeLocation;
    }

    @Override
    public int getVersion()
    {
        return STORE_VERSION;
    }

    @Override
    public String getType()
    {
        return STORE_TYPE;
    }

    @Override
    public String toString()
    {
        return "MemoryConfigurationEntryStore [_rootId=" + _rootId + "]";
    }

    protected boolean replaceEntries(ConfigurationEntry... entries)
    {
        boolean anySaved = false;
        for (ConfigurationEntry entry : entries)
        {
            ConfigurationEntry oldEntry = _entries.put(entry.getId(), entry);
            if (!entry.equals(oldEntry))
            {
                anySaved = true;
            }
        }
        return anySaved;
    }

    protected ObjectMapper getObjectMapper()
    {
        return _objectMapper;
    }

    protected void saveAsTree(File file)
    {
        saveAsTree(_rootId, _entries, _objectMapper, file, STORE_VERSION);
    }

    protected void saveAsTree(UUID rootId, Map<UUID, ConfigurationEntry> entries, ObjectMapper mapper, File file, int version)
    {
        Map<String, Object> tree = toTree(rootId, entries);
        tree.put(Broker.STORE_VERSION, version);
        try
        {
            mapper.writeValue(file, tree);
        }
        catch (JsonGenerationException e)
        {
            throw new IllegalConfigurationException("Cannot generate json!", e);
        }
        catch (JsonMappingException e)
        {
            throw new IllegalConfigurationException("Cannot map objects for json serialization!", e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot save configuration into " + file + "!", e);
        }
    }

    protected void load(URL url)
    {
        InputStream is = null;
        try
        {
            is = url.openStream();
            JsonNode node = loadJsonNodes(is, _objectMapper);

            int storeVersion = 0;
            JsonNode storeVersionNode = node.get(Broker.STORE_VERSION);
            if (storeVersionNode == null || storeVersionNode.isNull())
            {
                throw new IllegalConfigurationException("Broker " + Broker.STORE_VERSION + " attribute must be specified");
            }
            else
            {
                storeVersion = storeVersionNode.getIntValue();
            }

            if (storeVersion != STORE_VERSION)
            {
                throw new IllegalConfigurationException("The data of version " + storeVersion
                        + " can not be loaded by store of version " + STORE_VERSION);
            }

            ConfigurationEntry brokerEntry = toEntry(node, Broker.class, _entries);
            _rootId = brokerEntry.getId();
        }
        catch (IOException e)
        {
           throw new IllegalConfigurationException("Cannot load store from: " + url, e);
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot close input stream for: " + url, e);
                }

            }
        }
    }

    protected void createFileIfNotExist(File file)
    {
        File parent = file.getParentFile();
        if (!parent.exists())
        {
            if (!parent.mkdirs())
            {
                throw new IllegalConfigurationException("Cannot create folders " + parent);
            }
        }
        try
        {
            file.createNewFile();
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot create file " + file, e);
        }
    }

    protected void copyEntry(UUID entryId, ConfigurationEntryStore initialStore, Map<UUID,ConfigurationEntry> entries)
    {
        ConfigurationEntry entry = initialStore.getEntry(entryId);
        if (entry != null)
        {
            if (entries.containsKey(entryId))
            {
                throw new IllegalConfigurationException("Duplicate id is found: " + entryId
                        + "! The following configuration entries have the same id: " + entries.get(entryId) + ", " + entry);
            }

            Set<UUID> children = entry.getChildrenIds();
            Set<UUID> childrenCopy = children == null? null : new HashSet<UUID>(children);
            ConfigurationEntry copy = new ConfigurationEntry(entryId, entry.getType(), new HashMap<String, Object>(entry.getAttributes()), childrenCopy, this);
            entries.put(entryId, copy);
            if (children != null)
            {
                for (UUID uuid : children)
                {
                    copyEntry(uuid, initialStore, entries);
                }
            }
        }
    }

    private void loadFromJson(String json)
    {
        ByteArrayInputStream bais = null;
        try
        {
            byte[] bytes = json.getBytes("UTF-8");
            bais = new ByteArrayInputStream(bytes);
            JsonNode node = loadJsonNodes(bais, _objectMapper);
            ConfigurationEntry brokerEntry = toEntry(node, Broker.class, _entries);
            _rootId = brokerEntry.getId();
        }
        catch(Exception e)
        {
            throw new IllegalConfigurationException("Cannot create store from json:" + json);
        }
        finally
        {
            if (bais != null)
            {
                try
                {
                    bais.close();
                }
                catch (IOException e)
                {
                    // ByteArrayInputStream#close() is an empty method
                }
            }
        }
    }

    private void createRootEntry()
    {
        ConfigurationEntry brokerEntry = new ConfigurationEntry(UUIDGenerator.generateRandomUUID(),
                Broker.class.getSimpleName(), Collections.<String, Object> emptyMap(), Collections.<UUID> emptySet(), this);
        _rootId = brokerEntry.getId();
        _entries.put(_rootId, brokerEntry);
    }

    private Map<String, Object> toTree(UUID rootId, Map<UUID, ConfigurationEntry> entries)
    {
        ConfigurationEntry entry = entries.get(rootId);
        if (entry == null || !entry.getId().equals(rootId))
        {
            throw new IllegalConfigurationException("Cannot find entry with id " + rootId + "!");
        }
        Map<String, Object> tree = new TreeMap<String, Object>();
        Map<String, Object> attributes = entry.getAttributes();
        if (attributes != null)
        {
            tree.putAll(attributes);
        }
        tree.put(ID, entry.getId());
        Set<UUID> childrenIds = entry.getChildrenIds();
        if (childrenIds != null && !childrenIds.isEmpty())
        {
            for (UUID relationship : childrenIds)
            {
                ConfigurationEntry child = entries.get(relationship);
                if (child != null)
                {
                    String relationshipName = child.getType().toLowerCase() + "s";

                    @SuppressWarnings("unchecked")
                    Collection<Map<String, Object>> children = (Collection<Map<String, Object>>) tree.get(relationshipName);
                    if (children == null)
                    {
                        children = new ArrayList<Map<String, Object>>();
                        tree.put(relationshipName, children);
                    }
                    Map<String, Object> childAsMap = toTree(relationship, entries);
                    children.add(childAsMap);
                }
            }
        }
        return tree;
    }

    private Map<String, Class<? extends ConfiguredObject>> buildRelationshipClassMap()
    {
        Map<String, Class<? extends ConfiguredObject>> relationships = new HashMap<String, Class<? extends ConfiguredObject>>();

        Collection<Class<? extends ConfiguredObject>> children = Model.getInstance().getChildTypes(Broker.class);
        for (Class<? extends ConfiguredObject> childClass : children)
        {
            String name = childClass.getSimpleName().toLowerCase();
            String relationshipName = name + (name.endsWith("s") ? "es" : "s");
            relationships.put(relationshipName, childClass);
        }
        return relationships;
    }

    private boolean removeInternal(UUID entryId)
    {
        ConfigurationEntry oldEntry = _entries.remove(entryId);
        if (oldEntry != null)
        {
            Set<UUID> children = oldEntry.getChildrenIds();
            if (children != null && !children.isEmpty())
            {
                for (UUID childId : children)
                {
                    removeInternal(childId);
                }
            }
            return true;
        }
        return false;
    }

    private JsonNode loadJsonNodes(InputStream is, ObjectMapper mapper)
    {
        JsonNode root = null;
        try
        {
            root = mapper.readTree(is);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalConfigurationException("Cannot parse json", e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot read json", e);
        }
        return root;
    }

    private ConfigurationEntry toEntry(JsonNode parent, Class<? extends ConfiguredObject> expectedConfiguredObjectClass, Map<UUID, ConfigurationEntry> entries)
    {
        Map<String, Object> attributes = null;
        Set<UUID> childrenIds = new TreeSet<UUID>();
        Iterator<String> fieldNames = parent.getFieldNames();
        String type = null;
        String idAsString = null;
        while (fieldNames.hasNext())
        {
            String fieldName = fieldNames.next();
            JsonNode fieldNode = parent.get(fieldName);
            if (fieldName.equals(ID))
            {
                idAsString = fieldNode.asText();
            }
            else if (fieldName.equals(TYPE))
            {
                type = fieldNode.asText();
            }
            else if (fieldNode.isArray())
            {
                // array containing either broker children or attribute values
                Iterator<JsonNode> elements = fieldNode.getElements();
                List<Object> fieldValues = null;
                while (elements.hasNext())
                {
                    JsonNode element = elements.next();
                    if (element.isObject())
                    {
                        Class<? extends ConfiguredObject> expectedChildConfiguredObjectClass = findExpectedChildConfiguredObjectClass(
                                fieldName, expectedConfiguredObjectClass);

                        // assuming it is a child node
                        ConfigurationEntry entry = toEntry(element, expectedChildConfiguredObjectClass, entries);
                        childrenIds.add(entry.getId());
                    }
                    else
                    {
                        if (fieldValues == null)
                        {
                            fieldValues = new ArrayList<Object>();
                        }
                        fieldValues.add(toObject(element));
                    }
                }
                if (fieldValues != null)
                {
                    Object[] array = fieldValues.toArray(new Object[fieldValues.size()]);
                    if (attributes == null)
                    {
                        attributes = new HashMap<String, Object>();
                    }
                    attributes.put(fieldName, array);
                }
            }
            else if (fieldNode.isObject())
            {
                if (attributes == null)
                {
                    attributes = new HashMap<String, Object>();
                }
                attributes.put(fieldName, toObject(fieldNode) );
            }
            else
            {
                // primitive attribute
                Object value = toObject(fieldNode);
                if (attributes == null)
                {
                    attributes = new HashMap<String, Object>();
                }
                attributes.put(fieldName, value);
            }
        }

        if (type == null)
        {
            if (expectedConfiguredObjectClass == null)
            {
                throw new IllegalConfigurationException("Type attribute is not provided for configuration entry " + parent);
            }
            else
            {
                type = expectedConfiguredObjectClass.getSimpleName();
            }
        }
        String name = null;
        if (attributes != null)
        {
            name = (String) attributes.get(ATTRIBUTE_NAME);
        }
        if ((name == null || "".equals(name)))
        {
            if (expectedConfiguredObjectClass == Broker.class)
            {
                name = DEFAULT_BROKER_NAME;
            }
            else
            {
                throw new IllegalConfigurationException("Name attribute is not provided for configuration entry " + parent);
            }
        }
        UUID id = null;
        if (idAsString == null)
        {
            id = UUIDGenerator.generateRandomUUID();

            _generatedObjectIdDuringLoad = true;
        }
        else
        {
            try
            {
                id = UUID.fromString(idAsString);
            }
            catch (Exception e)
            {
                throw new IllegalConfigurationException(
                        "ID attribute value does not conform to UUID format for configuration entry " + parent);
            }
        }
        ConfigurationEntry entry = new ConfigurationEntry(id, type, attributes, childrenIds, this);
        if (entries.containsKey(id))
        {
            throw new IllegalConfigurationException("Duplicate id is found: " + id
                    + "! The following configuration entries have the same id: " + entries.get(id) + ", " + entry);
        }
        entries.put(id, entry);
        return entry;
    }

    private Class<? extends ConfiguredObject> findExpectedChildConfiguredObjectClass(String parentFieldName,
            Class<? extends ConfiguredObject> parentConfiguredObjectClass)
    {
        if (parentConfiguredObjectClass == Broker.class)
        {
            return _brokerChildrenRelationshipMap.get(parentFieldName);
        }

        // for non-broker parent classes
        // try to determine the child class from the model by iterating through the children classes
        // for the parent configured object class
        if (parentConfiguredObjectClass != null)
        {
            Collection<Class<? extends ConfiguredObject>> childTypes = Model.getInstance().getChildTypes(parentConfiguredObjectClass);
            for (Class<? extends ConfiguredObject> childType : childTypes)
            {
                String relationship = childType.getSimpleName().toLowerCase();
                relationship += relationship.endsWith("s") ? "es": "s";
                if (parentFieldName.equals(relationship))
                {
                    return childType;
                }
            }
        }

        return null;
    }

    private Object toObject(JsonNode node)
    {
        if (node.isValueNode())
        {
            if (node.isBoolean())
            {
                return node.asBoolean();
            }
            else if (node.isDouble())
            {
                return node.asDouble();
            }
            else if (node.isInt())
            {
                return node.asInt();
            }
            else if (node.isLong())
            {
                return node.asLong();
            }
            else if (node.isNull())
            {
                return null;
            }
            else
            {
                return Strings.expand(node.asText(), _resolver);
            }
        }
        else if (node.isArray())
        {
            return toArray(node);
        }
        else if (node.isObject())
        {
            return toMap(node);
        }
        else
        {
            throw new IllegalConfigurationException("Unexpected node: " + node);
        }
    }

    private Map<String, Object> toMap(JsonNode node)
    {
        Map<String, Object> object = new TreeMap<String, Object>();
        Iterator<String> fieldNames = node.getFieldNames();
        while (fieldNames.hasNext())
        {
            String name = fieldNames.next();
            Object value = toObject(node.get(name));
            object.put(name, value);
        }
        return object;
    }

    private Object toArray(JsonNode node)
    {
        ArrayNode arrayNode = (ArrayNode) node;
        Object[] array = new Object[arrayNode.size()];
        Iterator<JsonNode> elements = arrayNode.getElements();
        for (int i = 0; i < array.length; i++)
        {
            array[i] = toObject(elements.next());
        }
        return array;
    }

    protected boolean isGeneratedObjectIdDuringLoad()
    {
        return _generatedObjectIdDuringLoad;
    }

    protected ConfigurationEntryStoreUtil getConfigurationEntryStoreUtil()
    {
        return _util;
    }
}
