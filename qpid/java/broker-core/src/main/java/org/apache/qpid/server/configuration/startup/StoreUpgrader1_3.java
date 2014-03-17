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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    public interface VirtualHostEntryUpgrader
    {
        ConfigurationEntry upgrade(ConfigurationEntryStore store, ConfigurationEntry vhost);
    }

    public class BdbHaVirtualHostUpgrader implements VirtualHostEntryUpgrader
    {
        private final String[] HA_ATTRIBUTES =
            { "storePath", "haNodeName", "haGroupName", "haHelperAddress", "haCoalescingSync", "haNodeAddress", "haDurability",
              "haDesignatedPrimary", "haReplicationConfig", "bdbEnvironmentConfig" };

        @Override
        public ConfigurationEntry upgrade(ConfigurationEntryStore store, ConfigurationEntry vhost)
        {
            Map<String, Object> attributes = vhost.getAttributes();
            Map<String, Object> newAttributes = new HashMap<String, Object>(attributes);
            Map<String, Object> messageStoreSettings = new HashMap<String, Object>();

            for (String haAttribute : HA_ATTRIBUTES)
            {
                if (attributes.containsKey(haAttribute))
                {
                    messageStoreSettings.put(haAttribute, newAttributes.remove(haAttribute));
                }
            }

            if (attributes.containsKey("storeUnderfullSize"))
            {
                messageStoreSettings.put("storeUnderfullSize", newAttributes.remove("storeUnderfullSize"));
            }
            if (attributes.containsKey("storeOverfullSize"))
            {
                messageStoreSettings.put("storeOverfullSize", newAttributes.remove("storeOverfullSize"));
            }
            newAttributes.remove("storeType");
            newAttributes.put("messageStoreSettings", messageStoreSettings);
            return new ConfigurationEntry(vhost.getId(), vhost.getType(), newAttributes, vhost.getChildrenIds(), store);
        }

    }

    public interface StoreEntryUpgrader
    {
        Map<String, Object> upgrade(Map<String, Object> attributes);

        Set<String> getNamesToBeDeleted();
    }

    public class GenericMessageStoreEntryUpgrader implements StoreEntryUpgrader
    {
        private Map<String, String> _oldToNewNamesMap;
        private String _storeType;

        public GenericMessageStoreEntryUpgrader(String storeType, Map<String, String> oldToNewNamesMap)
        {
            _oldToNewNamesMap = oldToNewNamesMap;
            _storeType = storeType;
        }

        @Override
        public Map<String, Object> upgrade(Map<String, Object> attributes)
        {
            Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
            for (Map.Entry<String, String> nameMapEntry : _oldToNewNamesMap.entrySet())
            {
                String attributeName = nameMapEntry.getKey();
                if (attributes.containsKey(attributeName))
                {
                    messageStoreSettings.put(nameMapEntry.getValue(), attributes.get(attributeName));
                }
            }
            messageStoreSettings.put("storeType", _storeType);
            return messageStoreSettings;
        }

        @Override
        public Set<String> getNamesToBeDeleted()
        {
            Set<String> names = new HashSet<String>(_oldToNewNamesMap.keySet());
            names.add("storeType");
            return names;
        }

    }

    public class JDBCMessageStoreEntryUpgrader implements StoreEntryUpgrader
    {
        private final String[] JDBC_ATTRIBUTES =
            { "connectionURL", "connectionPool", "jdbcBigIntType", "jdbcBytesForBlob", "jdbcVarbinaryType", "jdbcBlobType",
              "partitionCount", "maxConnectionsPerPartition", "minConnectionsPerPartition" };

        @Override
        public Map<String, Object> upgrade(Map<String, Object> attributes)
        {
            Map<String, Object> messageStoreSettings = new HashMap<String, Object>();

            if (attributes.containsKey("storePath"))
            {
                messageStoreSettings.put("connectionURL", attributes.get("storePath"));
            }

            copyJdbcStoreSettings(attributes, messageStoreSettings);

            messageStoreSettings.put("storeType", "JDBC");
            return messageStoreSettings;
        }

        @Override
        public Set<String> getNamesToBeDeleted()
        {
            Set<String> names = new HashSet<String>();
            names.addAll(Arrays.asList(JDBC_ATTRIBUTES));
            names.add("storePath");
            names.add("storeType");
            return names;
        }

        private void copyJdbcStoreSettings(Map<String, Object> attributes, Map<String, Object> messageStoreSettings)
        {
            for (String jdbcAttribute : JDBC_ATTRIBUTES)
            {
                if (attributes.containsKey(jdbcAttribute))
                {
                    messageStoreSettings.put(jdbcAttribute, attributes.get(jdbcAttribute));
                }
            }
        }

    }

    public class JDBCConfigurationStoreEntryUpgrader implements StoreEntryUpgrader
    {

        private final String[] JDBC_ATTRIBUTES =
            { "connectionPool", "jdbcBigIntType", "jdbcBytesForBlob", "jdbcVarbinaryType", "jdbcBlobType", "partitionCount",
              "maxConnectionsPerPartition", "minConnectionsPerPartition" };

        @Override
        public Map<String, Object> upgrade(Map<String, Object> attributes)
        {
            Map<String, Object> messageStoreSettings = new HashMap<String, Object>();

            if (attributes.containsKey("configStorePath"))
            {
                messageStoreSettings.put("connectionURL", attributes.get("configStorePath"));
            }

            if (attributes.containsKey("configConnectionURL"))
            {
                messageStoreSettings.put("connectionURL", attributes.get("configConnectionURL"));
            }

            copyJdbcStoreSettings(attributes, messageStoreSettings);

            messageStoreSettings.put("storeType", "JDBC");
            return messageStoreSettings;
        }

        @Override
        public Set<String> getNamesToBeDeleted()
        {
            Set<String> names = new HashSet<String>();
            names.addAll(Arrays.asList(JDBC_ATTRIBUTES));
            names.add("configStorePath");
            names.add("configStoreType");
            names.add("configConnectionURL");
            return names;
        }

        private void copyJdbcStoreSettings(Map<String, Object> attributes, Map<String, Object> messageStoreSettings)
        {
            for (String jdbcAttribute : JDBC_ATTRIBUTES)
            {
                if (attributes.containsKey(jdbcAttribute))
                {
                    messageStoreSettings.put(jdbcAttribute, attributes.get(jdbcAttribute));
                }
            }
        }
    }

    public class StandardVirtualHostUpgrader implements VirtualHostEntryUpgrader
    {
        Map<String, StoreEntryUpgrader> _messageStoreEntryUpgrader = new HashMap<String, StoreEntryUpgrader>()
        {{
            put("JDBC", new JDBCMessageStoreEntryUpgrader());
            put("BDB", new GenericMessageStoreEntryUpgrader("BDB", new HashMap<String, String>()
            {{
                put("storePath", "storePath");
                put("bdbEnvironmentConfig", "bdbEnvironmentConfig");
                put("storeUnderfullSize", "storeUnderfullSize");
                put("storeOverfullSize", "storeOverfullSize");
            }}));
            put("DERBY", new GenericMessageStoreEntryUpgrader("DERBY", new HashMap<String, String>()
            {{
                put("storePath", "storePath");
                put("storeUnderfullSize", "storeUnderfullSize");
                put("storeOverfullSize", "storeOverfullSize");
            }}));
            put("MEMORY", new GenericMessageStoreEntryUpgrader("Memory", Collections.<String, String> emptyMap()));
        }};
        Map<String, StoreEntryUpgrader> _configurationStoreEntryUpgrader = new HashMap<String, StoreEntryUpgrader>()
        {{
            put("JDBC", new JDBCConfigurationStoreEntryUpgrader());
            put("DERBY", new GenericMessageStoreEntryUpgrader("DERBY", new HashMap<String, String>()
            {{
                put("configStorePath", "storePath");
                put("configStoreType", "storeType");
            }}));
            put("BDB", new GenericMessageStoreEntryUpgrader("BDB", new HashMap<String, String>()
            {{
                put("configStoreType", "storeType");
                put("configStorePath", "storePath");
                put("bdbEnvironmentConfig", "bdbEnvironmentConfig");
            }}));
            put("MEMORY", new GenericMessageStoreEntryUpgrader("Memory",
                                            Collections.<String, String> singletonMap("configStoreType", "storeType")));
            put("JSON", new GenericMessageStoreEntryUpgrader("JSON", new HashMap<String, String>()
            {{
                put("configStorePath", "storePath");
                put("configStoreType", "storeType");
            }}));
        }};

        @Override
        public ConfigurationEntry upgrade(ConfigurationEntryStore store, ConfigurationEntry vhost)
        {
            Map<String, Object> attributes = vhost.getAttributes();
            Map<String, Object> newAttributes = new HashMap<String, Object>(attributes);

            String capitalisedStoreType = String.valueOf(attributes.get("storeType")).toUpperCase();
            StoreEntryUpgrader messageStoreSettingsUpgrader = _messageStoreEntryUpgrader.get(capitalisedStoreType);
            Map<String, Object> messageStoreSettings = null;
            if (messageStoreSettingsUpgrader != null)
            {
                messageStoreSettings = messageStoreSettingsUpgrader.upgrade(attributes);
            }

            if (attributes.containsKey("configStoreType"))
            {
                String capitaliseConfigStoreType = ((String) attributes.get("configStoreType")).toUpperCase();
                StoreEntryUpgrader configurationStoreSettingsUpgrader = _configurationStoreEntryUpgrader
                        .get(capitaliseConfigStoreType);
                Map<String, Object> configurationStoreSettings = configurationStoreSettingsUpgrader.upgrade(attributes);
                newAttributes.keySet().removeAll(configurationStoreSettingsUpgrader.getNamesToBeDeleted());
                newAttributes.put("configurationStoreSettings", configurationStoreSettings);
            }

            if (messageStoreSettingsUpgrader != null)
            {
                newAttributes.keySet().removeAll(messageStoreSettingsUpgrader.getNamesToBeDeleted());
                newAttributes.put("messageStoreSettings", messageStoreSettings);
            }

            return new ConfigurationEntry(vhost.getId(), vhost.getType(), newAttributes, vhost.getChildrenIds(), store);
        }

    }

}