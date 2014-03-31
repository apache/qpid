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
package org.apache.qpid.server.configuration.startup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;

@SuppressWarnings("serial")
public final class StoreUpgrader1_3 extends StoreUpgrader
{

    public static final String VERSION = "1.3";

    private Map<String, VirtualHostEntryUpgrader> _vhostUpgraderMap = new HashMap<String, VirtualHostEntryUpgrader>()
    {{
        put("BDB_HA", new BdbHaVirtualHostUpgrader());
        put("STANDARD", new StandardVirtualHostUpgrader());
    }};

    StoreUpgrader1_3(String version)
    {
        super(version);
    }

    @Override
    protected void doUpgrade(ConfigurationEntryStore store)
    {
        ConfigurationEntry root = store.getRootEntry();
        Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
        Collection<ConfigurationEntry> vhosts = children.get("VirtualHost");
        Collection<ConfigurationEntry> changed = new ArrayList<ConfigurationEntry>();

        for (ConfigurationEntry vhost : vhosts)
        {
            Map<String, Object> attributes = vhost.getAttributes();
            if (attributes.containsKey("configPath"))
            {
                throw new IllegalConfigurationException("Auto-upgrade of virtual host " + attributes.get("name") + " having XML configuration is not supported. Virtual host configuration file is " + attributes.get("configPath"));
            }

            String type = (String) attributes.get("type");
            VirtualHostEntryUpgrader vhostUpgrader = _vhostUpgraderMap.get(type);
            if (vhostUpgrader == null)
            {
                throw new IllegalConfigurationException("Don't know how to perform an upgrade from version " + VERSION
                        + " for virtualhost type " + type);
            }
            ConfigurationEntry newVirtualHostConfigurationEntry = vhostUpgrader.upgrade(store, vhost);
            changed.add(newVirtualHostConfigurationEntry);
        }

        Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
        attributes.put(Broker.MODEL_VERSION, "1.4");
        changed.add(new ConfigurationEntry(root.getId(), root.getType(), attributes, root.getChildrenIds(), store));
        store.save(changed.toArray(new ConfigurationEntry[changed.size()]));
    }

    private interface VirtualHostEntryUpgrader
    {
        ConfigurationEntry upgrade(ConfigurationEntryStore store, ConfigurationEntry vhost);
    }

    private class StandardVirtualHostUpgrader implements VirtualHostEntryUpgrader
    {
        Map<String, AttributesTransformer> _messageStoreAttributeTransformers = new HashMap<String, AttributesTransformer>()
        {{
            put("DERBY", new AttributesTransformer().
                    addAttributeTransformer("storePath", copyAttribute()).
                    addAttributeTransformer("storeUnderfullSize", copyAttribute()).
                    addAttributeTransformer("storeOverfullSize", copyAttribute()).
                    addAttributeTransformer("storeType", mutateAttributeValue("DERBY")));
            put("MEMORY",  new AttributesTransformer().
                    addAttributeTransformer("storeType", mutateAttributeValue("Memory")));
            put("BDB", new AttributesTransformer().
                    addAttributeTransformer("storePath", copyAttribute()).
                    addAttributeTransformer("storeUnderfullSize", copyAttribute()).
                    addAttributeTransformer("storeOverfullSize", copyAttribute()).
                    addAttributeTransformer("bdbEnvironmentConfig", copyAttribute()).
                    addAttributeTransformer("storeType", mutateAttributeValue("BDB")));
            put("JDBC", new AttributesTransformer().
                    addAttributeTransformer("storePath", mutateAttributeName("connectionURL")).
                    addAttributeTransformer("connectionURL", copyAttribute()).
                    addAttributeTransformer("connectionPool", copyAttribute()).
                    addAttributeTransformer("jdbcBigIntType", copyAttribute()).
                    addAttributeTransformer("jdbcBytesForBlob", copyAttribute()).
                    addAttributeTransformer("jdbcBlobType", copyAttribute()).
                    addAttributeTransformer("jdbcVarbinaryType", copyAttribute()).
                    addAttributeTransformer("partitionCount", copyAttribute()).
                    addAttributeTransformer("maxConnectionsPerPartition", copyAttribute()).
                    addAttributeTransformer("minConnectionsPerPartition", copyAttribute()).
                    addAttributeTransformer("storeType", mutateAttributeValue("JDBC")));
        }};

        Map<String, AttributesTransformer> _configurationStoreAttributeTransformers = new HashMap<String, AttributesTransformer>()
        {{
            put("DERBY", new AttributesTransformer().
                    addAttributeTransformer("configStorePath", mutateAttributeName("storePath")).
                    addAttributeTransformer("configStoreType", mutateAttributeName("storeType"), mutateAttributeValue("DERBY")));
            put("MEMORY",  new AttributesTransformer().
                    addAttributeTransformer("configStoreType", mutateAttributeValue("Memory")));
            put("JSON", new AttributesTransformer().
                    addAttributeTransformer("configStorePath", mutateAttributeName("storePath")).
                    addAttributeTransformer("configStoreType", mutateAttributeName("storeType"), mutateAttributeValue("JSON")));
            put("BDB", new AttributesTransformer().
                    addAttributeTransformer("configStorePath", mutateAttributeName("storePath")).
                    addAttributeTransformer("bdbEnvironmentConfig", copyAttribute()).
                    addAttributeTransformer("configStoreType", mutateAttributeName("storeType"), mutateAttributeValue("BDB")));
            put("JDBC", new AttributesTransformer().
                    addAttributeTransformer("configStorePath", mutateAttributeName("connectionURL")).
                    addAttributeTransformer("configConnectionURL", mutateAttributeName("connectionURL")).
                    addAttributeTransformer("connectionPool", copyAttribute()).
                    addAttributeTransformer("jdbcBigIntType", copyAttribute()).
                    addAttributeTransformer("jdbcBytesForBlob", copyAttribute()).
                    addAttributeTransformer("jdbcBlobType", copyAttribute()).
                    addAttributeTransformer("jdbcVarbinaryType", copyAttribute()).
                    addAttributeTransformer("partitionCount", copyAttribute()).
                    addAttributeTransformer("maxConnectionsPerPartition", copyAttribute()).
                    addAttributeTransformer("minConnectionsPerPartition", copyAttribute()).
                    addAttributeTransformer("configStoreType", mutateAttributeName("storeType"), mutateAttributeValue("JDBC")));
        }};

        @Override
        public ConfigurationEntry upgrade(ConfigurationEntryStore store, ConfigurationEntry vhost)
        {
            Map<String, Object> attributes = vhost.getAttributes();
            Map<String, Object> newAttributes = new HashMap<String, Object>(attributes);

            String capitalisedStoreType = String.valueOf(attributes.get("storeType")).toUpperCase();
            AttributesTransformer vhAttrsToMessageStoreSettings = _messageStoreAttributeTransformers.get(capitalisedStoreType);
            Map<String, Object> messageStoreSettings = null;
            if (vhAttrsToMessageStoreSettings != null)
            {
                messageStoreSettings = vhAttrsToMessageStoreSettings.upgrade(attributes);
            }

            if (attributes.containsKey("configStoreType"))
            {
                String capitaliseConfigStoreType = ((String) attributes.get("configStoreType")).toUpperCase();
                AttributesTransformer vhAttrsToConfigurationStoreSettings = _configurationStoreAttributeTransformers
                        .get(capitaliseConfigStoreType);
                Map<String, Object> configurationStoreSettings = vhAttrsToConfigurationStoreSettings.upgrade(attributes);
                newAttributes.keySet().removeAll(vhAttrsToConfigurationStoreSettings.getNamesToBeDeleted());
                newAttributes.put("configurationStoreSettings", configurationStoreSettings);
            }

            if (vhAttrsToMessageStoreSettings != null)
            {
                newAttributes.keySet().removeAll(vhAttrsToMessageStoreSettings.getNamesToBeDeleted());
                newAttributes.put("messageStoreSettings", messageStoreSettings);
            }

            return new ConfigurationEntry(vhost.getId(), vhost.getType(), newAttributes, vhost.getChildrenIds(), store);
        }
    }

    private class BdbHaVirtualHostUpgrader implements VirtualHostEntryUpgrader
    {

        private final AttributesTransformer haAttributesTransformer =  new AttributesTransformer().
                addAttributeTransformer("storePath", copyAttribute()).
                addAttributeTransformer("storeUnderfullSize", copyAttribute()).
                addAttributeTransformer("storeOverfullSize", copyAttribute()).
                addAttributeTransformer("haNodeName", copyAttribute()).
                addAttributeTransformer("haGroupName", copyAttribute()).
                addAttributeTransformer("haHelperAddress", copyAttribute()).
                addAttributeTransformer("haCoalescingSync", copyAttribute()).
                addAttributeTransformer("haNodeAddress", copyAttribute()).
                addAttributeTransformer("haDurability", copyAttribute()).
                addAttributeTransformer("haDesignatedPrimary", copyAttribute()).
                addAttributeTransformer("haReplicationConfig", copyAttribute()).
                addAttributeTransformer("bdbEnvironmentConfig", copyAttribute()).
                addAttributeTransformer("storeType", removeAttribute());

        @Override
        public ConfigurationEntry upgrade(ConfigurationEntryStore store, ConfigurationEntry vhost)
        {
            Map<String, Object> attributes = vhost.getAttributes();

            Map<String, Object> messageStoreSettings = haAttributesTransformer.upgrade(attributes);

            Map<String, Object> newAttributes = new HashMap<String, Object>(attributes);
            newAttributes.keySet().removeAll(haAttributesTransformer.getNamesToBeDeleted());
            newAttributes.put("messageStoreSettings", messageStoreSettings);

            return new ConfigurationEntry(vhost.getId(), vhost.getType(), newAttributes, vhost.getChildrenIds(), store);
        }
    }

    private class AttributesTransformer
    {
        private final Map<String, List<AttributeTransformer>> _transformers = new HashMap<String, List<AttributeTransformer>>();
        private Set<String> _namesToBeDeleted = new HashSet<String>();

        public AttributesTransformer addAttributeTransformer(String string, AttributeTransformer... attributeTransformers)
        {
            _transformers.put(string, Arrays.asList(attributeTransformers));
            return this;
        }

        public Map<String, Object> upgrade(Map<String, Object> attributes)
        {
            Map<String, Object> settings = new HashMap<String, Object>();
            for (Entry<String, List<AttributeTransformer>> entry : _transformers.entrySet())
            {
                String attributeName = entry.getKey();
                if (attributes.containsKey(attributeName))
                {
                    Object attributeValue = attributes.get(attributeName);
                    MutatableEntry newEntry = new MutatableEntry(attributeName, attributeValue);

                    List<AttributeTransformer> transformers = entry.getValue();
                    for (AttributeTransformer attributeTransformer : transformers)
                    {
                        newEntry = attributeTransformer.transform(newEntry);
                        if (newEntry == null)
                        {
                            break;
                        }
                    }
                    if (newEntry != null)
                    {
                        settings.put(newEntry.getKey(), newEntry.getValue());
                    }

                    _namesToBeDeleted.add(attributeName);
                }
            }
            return settings;
        }

        public Set<String> getNamesToBeDeleted()
        {
            return _namesToBeDeleted;
        }
    }

    private AttributeTransformer copyAttribute()
    {
        return CopyAttribute.INSTANCE;
    }

    private AttributeTransformer removeAttribute()
    {
        return RemoveAttribute.INSTANCE;
    }

    private AttributeTransformer mutateAttributeValue(Object newValue)
    {
        return new MutateAttributeValue(newValue);
    }

    private AttributeTransformer mutateAttributeName(String newName)
    {
        return new MutateAttributeName(newName);
    }

    private interface AttributeTransformer
    {
        MutatableEntry transform(MutatableEntry entry);
    }

    private static class CopyAttribute implements AttributeTransformer
    {
        private static final CopyAttribute INSTANCE = new CopyAttribute();

        private CopyAttribute()
        {
        }

        @Override
        public MutatableEntry transform(MutatableEntry entry)
        {
            return entry;
        }
    }

    private static class RemoveAttribute implements AttributeTransformer
    {
        private static final RemoveAttribute INSTANCE = new RemoveAttribute();

        private RemoveAttribute()
        {
        }

        @Override
        public MutatableEntry transform(MutatableEntry entry)
        {
            return null;
        }
    }

    private class MutateAttributeName implements AttributeTransformer
    {
        private final String _newName;

        public MutateAttributeName(String newName)
        {
            _newName = newName;
        }

        @Override
        public MutatableEntry transform(MutatableEntry entry)
        {
            entry.setKey(_newName);
            return entry;
        }
    }

    private static class MutateAttributeValue implements AttributeTransformer
    {
        private final Object _newValue;

        public MutateAttributeValue(Object newValue)
        {
            _newValue = newValue;
        }

        @Override
        public MutatableEntry transform(MutatableEntry entry)
        {
            entry.setValue(_newValue);
            return entry;
        }
    }

    private static class MutatableEntry
    {
        private String _key;
        private Object _value;

        public MutatableEntry(String key, Object value)
        {
            _key = key;
            _value = value;
        }

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            _key = key;
        }

        public Object getValue()
        {
            return _value;
        }

        public void setValue(Object value)
        {
            _value = value;
        }
    }

}