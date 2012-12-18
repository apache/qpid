package org.apache.qpid.server.configuration.store;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class JsonConfigurationEntryStore implements ConfigurationEntryStore
{
    private static final String ID = "id";
    private static final String TYPE = "type";

    private ObjectMapper _objectMapper;
    private Map<UUID, ConfigurationEntry> _entries;
    private File _storeFile;
    private UUID _rootId;

    public JsonConfigurationEntryStore(String filePath)
    {
        _storeFile = new File(filePath);
        _objectMapper = new ObjectMapper();
        _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        _entries = new HashMap<UUID, ConfigurationEntry>();
        if (_storeFile.exists())
        {
            JsonNode node = load(_storeFile, _objectMapper);
            _rootId = getId(node);
            _entries.putAll(flattenTree(_rootId, node));
        }
        else
        {
            File parent = _storeFile.getParentFile();
            if (!parent.exists())
            {
                if (!parent.mkdirs())
                {
                    throw new IllegalConfigurationException("Cannot create folder(s) for the store at " + _storeFile);
                }
            }
            try
            {
                if (!_storeFile.createNewFile())
                {
                    throw new IllegalConfigurationException("Cannot create store file at " + _storeFile);
                }
            }
            catch (IOException e)
            {
                throw new IllegalConfigurationException("Cannot write into file at " + _storeFile, e);
            }
            ConfigurationEntry brokerEntry = new ConfigurationEntry(UUID.randomUUID(), Broker.class.getSimpleName(), null,
                    null, null);
            _rootId = brokerEntry.getId();
            save(brokerEntry);
        }
    }

    @Override
    public synchronized Collection<ConfigurationEntry> list()
    {
        return Collections.unmodifiableCollection(_entries.values());
    }

    @Override
    public synchronized void save(ConfigurationEntry entry)
    {
        ConfigurationEntry parent = null;
        ConfigurationEntry oldEntry = _entries.put(entry.getId(), entry);
        if (!entry.equals(oldEntry))
        {
            if (oldEntry == null && parent != null)
            {
                Set<UUID> parentChildren =  parent.getChildrenIds();
                parentChildren.add(entry.getId());
                _entries.put(parentId, new ConfigurationEntry(parent.getId(), parent.getType(), parent.getAttributes(), parent.getParentId(), parentChildren));
            }
            saveAsTree();
        }
    }

    @Override
    public synchronized void remove(UUID entryId)
    {
        if (removeInternal(entryId))
        {
            saveAsTree();
        }
    }

    @Override
    public synchronized void save(ConfigurationEntry[] entries)
    {
        boolean theSame = true;
        for (int i = 0; i < entries.length; i++)
        {
            ConfigurationEntry entry = entries[i];
            UUID parentId = entry.getParentId();
            if (parentId != null)
            {
                ConfigurationEntry parent = _entries.get(parentId);
                if (parent == null)
                {
                    throw new IllegalConfigurationException("Unknown parentId " + entry.getParentId());
                }
            }
        }
        for (int i = 0; i < entries.length; i++)
        {
            ConfigurationEntry entry = entries[i];
            ConfigurationEntry oldEntry = _entries.put(entry.getId(), entry);
            if (!entry.equals(oldEntry))
            {
                theSame = false;
                if (oldEntry == null)
                {
                    ConfigurationEntry parent = _entries.get(entry.getParentId());
                    if (parent != null)
                    {
                        Set<UUID> parentChildren =  parent.getChildrenIds();
                        parentChildren.add(entry.getId());
                        _entries.put(parent.getId(), new ConfigurationEntry(parent.getId(), parent.getType(), parent.getAttributes(), parent.getParentId(), parentChildren));
                    }
                }
            }
        }
        if (!theSame)
        {
            saveAsTree();
        }
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
            UUID parentId = oldEntry.getParentId();
            if (parentId != null)
            {
                ConfigurationEntry parent = _entries.get(parentId);
                if (parent != null)
                {
                    Set<UUID> parentChildren =  parent.getChildrenIds();
                    parentChildren.remove(oldEntry.getId());
                    _entries.put(parentId, new ConfigurationEntry(parent.getId(), parent.getType(), parent.getAttributes(), parent.getParentId(), parentChildren));
                }
            }
            return true;
        }
        return false;
    }

    private void saveAsTree()
    {
        saveAsTree(_rootId, _entries, _objectMapper, _storeFile);
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
                    Set<Map<String, Object>> children = (Set<Map<String, Object>>) tree.get(relationshipName);
                    if (children == null)
                    {
                        children = new TreeSet<Map<String, Object>>();
                        tree.put(relationshipName, children);
                    }
                    Map<String, Object> childAsMap = toTree(relationship, entries);
                    children.add(childAsMap);
                }
            }
        }
        return tree;
    }

    private JsonNode load(File file, ObjectMapper mapper)
    {
        JsonNode root = null;
        try
        {
            root = mapper.readTree(file);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalConfigurationException("Cannot parse file '" + file + "'!", e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot read file '" + file + "'!", e);
        }
        return root;
    }

    private Map<UUID, ConfigurationEntry> flattenTree(UUID id, JsonNode root)
    {
        String type = getType(root);
        UUID parentId = getParentId(root);

        Map<String, Object> attributes = new HashMap<String, Object>();
        Set<UUID> childrenIds = new TreeSet<UUID>();

        ConfigurationEntry entry = new ConfigurationEntry(id, type, attributes, childrenIds, this);
        Map<UUID, ConfigurationEntry> entries = new HashMap<UUID, ConfigurationEntry>();
        entries.put(id, entry);
        Iterator<String> fieldNames = root.getFieldNames();
        while (fieldNames.hasNext())
        {
            String name = fieldNames.next();
            JsonNode node = root.get(name);
            if (node.isValueNode())
            {
                if (node.isBoolean())
                {
                    attributes.put(name, node.asBoolean());
                }
                else if (node.isDouble())
                {
                    attributes.put(name, node.asDouble());
                }
                else if (node.isInt())
                {
                    attributes.put(name, node.asInt());
                }
                else if (node.isLong())
                {
                    attributes.put(name, node.asLong());
                }
                else if (node.isNull())
                {
                    attributes.put(name, null);
                }
                else
                {
                    String text = node.asText();
                    try
                    {
                        UUID referedId = UUID.fromString(text);
                        attributes.put(name, referedId);
                    }
                    catch (Exception e)
                    {
                        attributes.put(name, text);
                    }
                }
            }
            else if (node.isArray())
            {
                Iterator<JsonNode> elements = node.getElements();
                while (elements.hasNext())
                {
                    JsonNode element = elements.next();
                    UUID elementId = getId(element);
                    childrenIds.add(elementId);
                    Map<UUID, ConfigurationEntry> subEntries = flattenTree(elementId, element);
                    entries.putAll(subEntries);
                }
            }
            else
            {
                throw new IllegalConfigurationException("Unexpected node " + root + "!");
            }
        }

        return entries;
    }

    private String getType(JsonNode root)
    {
        JsonNode typeNode = root.get(TYPE);
        if (typeNode != null)
        {
            return typeNode.asText();
        }
        return null;
    }

    private UUID getId(JsonNode node)
    {
        JsonNode idNode = node.get(ID);
        if (idNode == null)
        {
            return UUID.randomUUID();
        }
        String id = idNode.asText();
        if (id == null)
        {
            return UUID.randomUUID();
        }
        try
        {
            return UUID.fromString(id);
        }
        catch (Exception e)
        {
            return UUID.nameUUIDFromBytes(id.getBytes());
        }
    }

    @Override
    public ConfigurationEntry getRootEntry()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConfigurationEntry getEntry(UUID id)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void remove(UUID... entryIds)
    {
        // TODO Auto-generated method stub
        
    }

}
