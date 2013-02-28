package org.apache.qpid.server.configuration.store;

import static org.apache.qpid.server.configuration.ConfigurationEntry.ATTRIBUTE_NAME;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.Strings;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.node.ArrayNode;

public class JsonConfigurationEntryStore implements ConfigurationEntryStore
{
    public static final String STORE_TYPE = "json";
    public static final String IN_MEMORY = ":memory:";

    private static final String DEFAULT_BROKER_NAME = "Broker";
    private static final String ID = "id";
    private static final String TYPE = "@type";

    private ObjectMapper _objectMapper;
    private Map<UUID, ConfigurationEntry> _entries;
    private File _storeFile;
    private UUID _rootId;
    private Map<String, Class<? extends ConfiguredObject>> _relationshipClasses;

    public JsonConfigurationEntryStore()
    {
        _objectMapper = new ObjectMapper();
        _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        _objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        _entries = new HashMap<UUID, ConfigurationEntry>();
        _relationshipClasses = buildRelationshipClassMap();
    }

    @Override
    public void open(String storeLocation)
    {
        if (_rootId != null)
        {
            throw new IllegalConfigurationException("The store has been opened alread");
        }
        if (!IN_MEMORY.equals(storeLocation))
        {
            _storeFile = new File(storeLocation);
        }
        createOrLoadStore();
    }

    @Override
    public void open(String storeLocation, String initialStoreLocation)
    {
        if (_rootId != null)
        {
            throw new IllegalConfigurationException("The store has been opened already");
        }
        if (!IN_MEMORY.equals(storeLocation))
        {
            _storeFile = new File(storeLocation);
            if ((!_storeFile.exists() || _storeFile.length() == 0) && initialStoreLocation != null)
            {
                copyInitialStoreFile(initialStoreLocation);
            }
            createOrLoadStore();
        }
        else
        {
            if (initialStoreLocation == null)
            {
                createRootEntryIfNotExists();
            }
            else
            {
                load(toURL(initialStoreLocation));
            }
        }
    }

    @Override
    public void open(String storeLocation, ConfigurationEntryStore initialStore)
    {
        if (_rootId != null)
        {
            throw new IllegalConfigurationException("The store has been opened already");
        }
        boolean copyStore = false;
        if (IN_MEMORY.equals(storeLocation))
        {
            copyStore = initialStore != null;
        }
        else
        {
            _storeFile = new File(storeLocation);
            if ((!_storeFile.exists() || _storeFile.length() == 0) && initialStore != null)
            {
                createStoreFileIfNotExist(_storeFile);
                copyStore = true;
            }
        }
        if (copyStore)
        {
            ConfigurationEntry rootEntry = initialStore.getRootEntry();
            _rootId = rootEntry.getId();
            copyEntry(rootEntry.getId(), initialStore);
            saveAsTree();
        }
        else
        {
            createOrLoadStore();
        }
    }

    @Override
    public synchronized UUID[] remove(UUID... entryIds)
    {
        List<UUID> removedIds = new ArrayList<UUID>();
        boolean anyRemoved = false;
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
                anyRemoved = true;

                // remove references to the entry from parent entries
                for (ConfigurationEntry entry : _entries.values())
                {
                    if (entry.hasChild(uuid))
                    {
                        Set<UUID> children = new HashSet<UUID>(entry.getChildrenIds());
                        children.remove(uuid);
                        ConfigurationEntry referal = new ConfigurationEntry(entry.getId(), entry.getType(),
                                entry.getAttributes(), children, this);
                        _entries.put(entry.getId(), referal);
                    }
                }
                removedIds.add(uuid);
            }
        }
        if (anyRemoved)
        {
            saveAsTree();
        }
        return removedIds.toArray(new UUID[removedIds.size()]);
    }

    @Override
    public synchronized void save(ConfigurationEntry... entries)
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
        if (anySaved)
        {
            saveAsTree();
        }
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

    @Override
    public void copyTo(String copyLocation)
    {
        if (_rootId == null)
        {
            throw new IllegalConfigurationException("The store has not been opened");
        }
        File file = new File(copyLocation);
        if (!file.exists())
        {
            createStoreFileIfNotExist(file);
        }
        saveAsTree(_rootId, _entries, _objectMapper, file);
    }

    @Override
    public String toString()
    {
        return "JsonConfigurationEntryStore [_storeFile=" + _storeFile + ", _rootId=" + _rootId + "]";
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

    private void createOrLoadStore()
    {
        if (_storeFile != null)
        {
            if (!_storeFile.exists() || _storeFile.length() == 0)
            {
                createStoreFileIfNotExist(_storeFile);
            }
            else
            {
                load(fileToURL(_storeFile));
            }
        }

        createRootEntryIfNotExists();
    }

    private void createRootEntryIfNotExists()
    {
        if (_rootId == null)
        {
            // create a root entry for an empty store
            ConfigurationEntry brokerEntry = new ConfigurationEntry(UUIDGenerator.generateRandomUUID(),
                    Broker.class.getSimpleName(), Collections.<String, Object> emptyMap(), Collections.<UUID> emptySet(), this);
            _rootId = brokerEntry.getId();
            _entries.put(_rootId, brokerEntry);
        }
    }

    private void load(URL url)
    {
        InputStream is = null;
        try
        {
            is = url.openStream();
            JsonNode node = loadJsonNodes(is, _objectMapper);
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
    }

    private void copyInitialStoreFile(String initialStoreLocation)
    {
        createStoreFileIfNotExist(_storeFile);
        URL initialStoreURL = toURL(initialStoreLocation);
        InputStream in =  null;
        try
        {
            in = initialStoreURL.openStream();
            FileUtils.copy(in, _storeFile);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot create store file " + _storeFile + " by copying initial store from " + initialStoreLocation , e);
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot close initial store input stream: " + initialStoreLocation , e);
                }
            }
        }
    }

    private URL fileToURL(File storeFile)
    {
        URL storeURL = null;
        try
        {
            storeURL = storeFile.toURI().toURL();
        }
        catch (MalformedURLException e)
        {
            throw new IllegalConfigurationException("Cannot create URL for file " + storeFile, e);
        }
        return storeURL;
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

    private void saveAsTree()
    {
        if (_storeFile != null)
        {
            saveAsTree(_rootId, _entries, _objectMapper, _storeFile);
        }
    }

    private void saveAsTree(UUID rootId, Map<UUID, ConfigurationEntry> entries, ObjectMapper mapper, File file)
    {
        Map<String, Object> tree = toTree(rootId, entries);
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
        tree.put(TYPE, entry.getType());
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
                        Class<? extends ConfiguredObject> expectedChildConfiguredObjectClass = _relationshipClasses.get(fieldName);
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
                    attributes.put(fieldName, array);
                }
            }
            else if (fieldNode.isObject())
            {
                // ignore, in-line objects are not supported yet
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
            if (expectedConfiguredObjectClass == Broker.class)
            {
                id = UUIDGenerator.generateRandomUUID();
            }
            else
            {
                id = UUIDGenerator.generateBrokerChildUUID(type, name);
            }
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
                return Strings.expand(node.asText());
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

    /*
     * Initial store location can be URL or absolute path
     */
    private URL toURL(String location)
    {
        URL url = null;
        try
        {
            url = new URL(location);
        }
        catch (MalformedURLException e)
        {
            File locationFile = new File(location);
            url = fileToURL(locationFile);
        }
        return url;
    }

    private void createStoreFileIfNotExist(File file)
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

    private void copyEntry(UUID entryId, ConfigurationEntryStore initialStore)
    {
        ConfigurationEntry entry = initialStore.getEntry(entryId);
        if (entry != null)
        {
            if (_entries.containsKey(entryId))
            {
                throw new IllegalConfigurationException("Duplicate id is found: " + entryId
                        + "! The following configuration entries have the same id: " + _entries.get(entryId) + ", " + entry);
            }
            _entries.put(entryId, entry);
            Set<UUID> children = entry.getChildrenIds();
            if (children != null)
            {
                for (UUID uuid : children)
                {
                    copyEntry(uuid, initialStore);
                }
            }
        }
    }
}
