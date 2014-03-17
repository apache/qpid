package org.apache.qpid.server.configuration.startup;/*
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.model.Broker;

public abstract class StoreUpgrader
{

    private static Map<String, StoreUpgrader> _upgraders = new HashMap<String, StoreUpgrader>();

    // Note: don't use externally defined constants in upgraders in case they change, the values here MUST stay the same
    // no matter what changes are made to the code in the future

    private final static StoreUpgrader UPGRADE_1_0 = new StoreUpgrader("1.0")
    {
        @Override
        protected void doUpgrade(ConfigurationEntryStore store)
        {
            ConfigurationEntry root = store.getRootEntry();
            Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
            Collection<ConfigurationEntry> vhosts = children.get("VirtualHost");
            Collection<ConfigurationEntry> changed =  new HashSet<ConfigurationEntry>();
            for(ConfigurationEntry vhost : vhosts)
            {
                Map<String, Object> attributes = vhost.getAttributes();
                if(attributes.containsKey("storeType"))
                {
                    attributes = new HashMap<String, Object>(attributes);
                    attributes.put("type", "STANDARD");

                    changed.add(new ConfigurationEntry(vhost.getId(),vhost.getType(),attributes,vhost.getChildrenIds(),store));

                }

            }
            Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
            attributes.put(Broker.MODEL_VERSION, "1.1");
            changed.add(new ConfigurationEntry(root.getId(),root.getType(),attributes,root.getChildrenIds(),store));

            store.save(changed.toArray(new ConfigurationEntry[changed.size()]));

        }
    };

    private final static StoreUpgrader UPGRADE_1_1 = new StoreUpgrader("1.1")
    {
        @Override
        protected void doUpgrade(ConfigurationEntryStore store)
        {
            ConfigurationEntry root = store.getRootEntry();

            Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
            attributes.put(Broker.MODEL_VERSION, "1.2");
            ConfigurationEntry newRoot = new ConfigurationEntry(root.getId(),root.getType(),attributes,root.getChildrenIds(),store);

            store.save(newRoot);

        }
    };


    private final static StoreUpgrader UPGRADE_1_2 = new StoreUpgrader("1.2")
    {
        @Override
        protected void doUpgrade(ConfigurationEntryStore store)
        {
            ConfigurationEntry root = store.getRootEntry();
            Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
            Collection<ConfigurationEntry> changed =  new HashSet<ConfigurationEntry>();
            Collection<ConfigurationEntry> keyStores = children.get("KeyStore");
            if(keyStores != null)
            {
                for(ConfigurationEntry keyStore : keyStores)
                {
                    Map<String, Object> attributes = keyStore.getAttributes();
                    if(attributes.containsKey("type"))
                    {
                        attributes = new HashMap<String, Object>(attributes);
                        attributes.put("keyStoreType", attributes.remove("type"));

                        changed.add(new ConfigurationEntry(keyStore.getId(),keyStore.getType(),attributes,keyStore.getChildrenIds(),store));

                    }

                }
            }
            Collection<ConfigurationEntry> trustStores = children.get("TrustStore");
            if(trustStores != null)
            {
                for(ConfigurationEntry trustStore : trustStores)
                {
                    Map<String, Object> attributes = trustStore.getAttributes();
                    if(attributes.containsKey("type"))
                    {
                        attributes = new HashMap<String, Object>(attributes);
                        attributes.put("trustStoreType", attributes.remove("type"));

                        changed.add(new ConfigurationEntry(trustStore.getId(),trustStore.getType(),attributes,trustStore.getChildrenIds(),store));

                    }

                }
            }
            Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
            attributes.put(Broker.MODEL_VERSION, "1.3");
            changed.add(new ConfigurationEntry(root.getId(),root.getType(),attributes,root.getChildrenIds(),store));

            store.save(changed.toArray(new ConfigurationEntry[changed.size()]));

        }
    };

    final static StoreUpgrader UPGRADE_1_3 = new StoreUpgrader("1.3")
    {
        private final String[] HA_ATTRIBUTES =  {"haNodeName", "haGroupName", "haHelperAddress", "haCoalescingSync", "haNodeAddress","haDurability","haDesignatedPrimary","haReplicationConfig","bdbEnvironmentConfig"};
        private final String[] JDBC_ATTRIBUTES =  {"connectionPool", "jdbcBigIntType", "jdbcBytesForBlob", "jdbcVarbinaryType", "jdbcBlobType", "partitionCount", "maxConnectionsPerPartition", "minConnectionsPerPartition"};
        private final String[] STORE_TYPES = {"BDB", "BDB-HA", "JDBC", "Memory", "DERBY"};
        private final String[] CONFIGURATION_STORE_TYPES = {"BDB", "JSON", "JDBC", "Memory", "DERBY"};

        @Override
        protected void doUpgrade(ConfigurationEntryStore store)
        {
            ConfigurationEntry root = store.getRootEntry();
            Map<String, Collection<ConfigurationEntry>> children = root.getChildren();
            Collection<ConfigurationEntry> vhosts = children.get("VirtualHost");
            Collection<ConfigurationEntry> changed =  new ArrayList<ConfigurationEntry>();
            for(ConfigurationEntry vhost : vhosts)
            {
                Map<String, Object> attributes = vhost.getAttributes();
                Map<String, Object> newAttributes = new HashMap<String, Object>(attributes);
                Map<String, Object> messageStoreSettings = new HashMap<String, Object>();

                String storeType = (String) attributes.get("storeType");
                String realStoreType = storeType;
                for (String type : STORE_TYPES)
                {
                    if (type.equalsIgnoreCase(storeType))
                    {
                        realStoreType = type;
                        break;
                    }
                }
                if(attributes.containsKey("storeType"))
                {
                    newAttributes.remove("storeType");
                    messageStoreSettings.put("storeType", realStoreType);
                }
                if (attributes.containsKey("storePath"))
                {
                    messageStoreSettings.put("storePath", newAttributes.remove("storePath"));
                }
                if (attributes.containsKey("storeUnderfullSize"))
                {
                    messageStoreSettings.put("storeUnderfullSize", newAttributes.remove("storeUnderfullSize"));
                }
                if (attributes.containsKey("storeOverfullSize"))
                {
                    messageStoreSettings.put("storeOverfullSize", newAttributes.remove("storeOverfullSize"));
                }

                if ("BDB_HA".equals(attributes.get("type")))
                {
                    for (String haAttribute : HA_ATTRIBUTES)
                    {
                        if(attributes.containsKey(haAttribute))
                        {
                            messageStoreSettings.put(haAttribute, newAttributes.remove(haAttribute));
                        }
                    }
                    messageStoreSettings.remove("storeType");
                }
                else
                {
                    if ("JDBC".equalsIgnoreCase(realStoreType))
                    {
                        // storePath attribute might contain the connectionURL
                        if (messageStoreSettings.containsKey("storePath"))
                        {
                            messageStoreSettings.put("connectionURL", messageStoreSettings.remove("storePath"));
                        }

                        if (newAttributes.containsKey("connectionURL"))
                        {
                            messageStoreSettings.put("connectionURL", newAttributes.remove("connectionURL"));
                        }

                        copyJdbcStoreSettings(attributes, messageStoreSettings);
                    }
                    else if ("BDB".equals(realStoreType))
                    {
                        if(attributes.containsKey("bdbEnvironmentConfig"))
                        {
                            messageStoreSettings.put("bdbEnvironmentConfig", newAttributes.get("bdbEnvironmentConfig"));
                        }
                    }
                }

                //TODO: this might need throwing an exception if message store is not defined
                if (!messageStoreSettings.isEmpty())
                {
                    newAttributes.put("messageStoreSettings", messageStoreSettings);
                }

                Map<String, Object> configurationStoreSettings = new HashMap<String, Object>();
                String realConfigurationStoreType = copyConfigurationStoreSettings(newAttributes, configurationStoreSettings);

                if (!configurationStoreSettings.isEmpty())
                {
                    newAttributes.put("configurationStoreSettings", configurationStoreSettings);
                }

                if ("JDBC".equalsIgnoreCase(realStoreType) || "JDBC".equalsIgnoreCase(realConfigurationStoreType))
                {
                    for (String jdbcAttribute : JDBC_ATTRIBUTES)
                    {
                        if(newAttributes.containsKey(jdbcAttribute))
                        {
                            newAttributes.remove(jdbcAttribute);
                        }
                    }
                }

                if ("BDB".equalsIgnoreCase(realStoreType) || "BDB".equalsIgnoreCase(realConfigurationStoreType))
                {
                    if(newAttributes.containsKey("bdbEnvironmentConfig"))
                    {
                        newAttributes.remove("bdbEnvironmentConfig");
                    }
                }

                changed.add(new ConfigurationEntry(vhost.getId(), vhost.getType(), newAttributes, vhost.getChildrenIds(), store));
            }
            Map<String, Object> attributes = new HashMap<String, Object>(root.getAttributes());
            attributes.put(Broker.MODEL_VERSION, "1.4");
            changed.add(new ConfigurationEntry(root.getId(), root.getType(), attributes, root.getChildrenIds(),store));

            store.save(changed.toArray(new ConfigurationEntry[changed.size()]));

        }

        private String copyConfigurationStoreSettings(Map<String, Object> newAttributes,
                Map<String, Object> configurationStoreSettings)
        {
            String realConfigurationStoreType = null;
            if(newAttributes.containsKey("configStoreType"))
            {
                String configurationStoreType = (String) newAttributes.get("configStoreType");
                realConfigurationStoreType = configurationStoreType;
                for (String type : CONFIGURATION_STORE_TYPES)
                {
                    if (type.equalsIgnoreCase(configurationStoreType))
                    {
                        realConfigurationStoreType = type;
                        break;
                    }
                }
                newAttributes.remove("configStoreType");
                configurationStoreSettings.put("storeType", realConfigurationStoreType);
                if ("JDBC".equalsIgnoreCase(realConfigurationStoreType))
                {
                    // storePath attribute might contain the connectionURL
                    if (newAttributes.containsKey("configStorePath"))
                    {
                        configurationStoreSettings.put("connectionURL", newAttributes.remove("configStorePath"));
                    }
                    if (newAttributes.containsKey("configConnectionURL"))
                    {
                        configurationStoreSettings.put("connectionURL", newAttributes.remove("configConnectionURL"));
                    }
                    copyJdbcStoreSettings(newAttributes, configurationStoreSettings);
                }
                else if ("BDB".equals(realConfigurationStoreType))
                {
                    if(newAttributes.containsKey("bdbEnvironmentConfig"))
                    {
                        configurationStoreSettings.put("bdbEnvironmentConfig", newAttributes.get("bdbEnvironmentConfig"));
                    }
                }
            }

            if (newAttributes.containsKey("configStorePath"))
            {
                configurationStoreSettings.put("storePath", newAttributes.remove("configStorePath"));
            }
            return realConfigurationStoreType;
        }

        private void copyJdbcStoreSettings(Map<String, Object> attributes, Map<String, Object> messageStoreSettings)
        {
            for (String jdbcAttribute : JDBC_ATTRIBUTES)
            {
                if(attributes.containsKey(jdbcAttribute))
                {
                     messageStoreSettings.put(jdbcAttribute, attributes.get(jdbcAttribute));
                }
            }
        }
    };

    private StoreUpgrader(String version)
    {
        _upgraders.put(version, this);
    }

    public static void upgrade(ConfigurationEntryStore store)
    {
        StoreUpgrader upgrader = null;
        while ((upgrader = _upgraders.get(store.getRootEntry().getAttributes().get(Broker.MODEL_VERSION).toString())) != null)
        {
            upgrader.doUpgrade(store);
        }
    }

    protected abstract void doUpgrade(ConfigurationEntryStore store);


}
