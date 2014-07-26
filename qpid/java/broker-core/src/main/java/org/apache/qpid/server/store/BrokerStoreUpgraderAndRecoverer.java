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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.util.Action;

public class BrokerStoreUpgraderAndRecoverer
{
    private final SystemConfig<?> _systemConfig;
    private final Map<String, StoreUpgraderPhase> _upgraders = new HashMap<String, StoreUpgraderPhase>();

    // Note: don't use externally defined constants in upgraders in case they change, the values here MUST stay the same
    // no matter what changes are made to the code in the future
    public BrokerStoreUpgraderAndRecoverer(SystemConfig<?> systemConfig)
    {
        _systemConfig = systemConfig;

        register(new Upgrader_1_0_to_1_1());
        register(new Upgrader_1_1_to_1_2());
        register(new Upgrader_1_2_to_1_3());
        register(new Upgrader_1_3_to_2_0());
    }

    private void register(StoreUpgraderPhase upgrader)
    {
        _upgraders.put(upgrader.getFromVersion(), upgrader);
    }

    private static final class Upgrader_1_0_to_1_1 extends StoreUpgraderPhase
    {
        private Upgrader_1_0_to_1_1()
        {
            super("modelVersion", "1.0", "1.1");
        }

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {
            if (record.getType().equals("Broker"))
            {
                record = upgradeRootRecord(record);
            }
            else if (record.getType().equals("VirtualHost") && record.getAttributes().containsKey("storeType"))
            {
                Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                updatedAttributes.put("type", "STANDARD");
                record = new ConfiguredObjectRecordImpl(record.getId(), record.getType(), updatedAttributes, record.getParents());
                getUpdateMap().put(record.getId(), record);
            }

            getNextUpgrader().configuredObject(record);
        }

        @Override
        public void complete()
        {
            getNextUpgrader().complete();
        }

    }

    private static final class Upgrader_1_1_to_1_2 extends StoreUpgraderPhase
    {
        private Upgrader_1_1_to_1_2()
        {
            super("modelVersion", "1.1", "1.2");
        }

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {
            if (record.getType().equals("Broker"))
            {
                record = upgradeRootRecord(record);
            }

            getNextUpgrader().configuredObject(record);

        }

        @Override
        public void complete()
        {
            getNextUpgrader().complete();
        }

    }

    private static final class Upgrader_1_2_to_1_3 extends StoreUpgraderPhase
    {
        private Upgrader_1_2_to_1_3()
        {
            super("modelVersion", "1.2", "1.3");
        }

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {
            if (record.getType().equals("TrustStore") && record.getAttributes().containsKey("type"))
            {
                Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                updatedAttributes.put("trustStoreType", updatedAttributes.remove("type"));
                record = new ConfiguredObjectRecordImpl(record.getId(), record.getType(), updatedAttributes, record.getParents());
                getUpdateMap().put(record.getId(), record);

            }
            else if (record.getType().equals("KeyStore") && record.getAttributes().containsKey("type"))
            {
                Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                updatedAttributes.put("keyStoreType", updatedAttributes.remove("type"));
                record = new ConfiguredObjectRecordImpl(record.getId(), record.getType(), updatedAttributes, record.getParents());
                getUpdateMap().put(record.getId(), record);

            }
            else if (record.getType().equals("Broker"))
            {
                record = upgradeRootRecord(record);
            }

            getNextUpgrader().configuredObject(record);

        }

        @Override
        public void complete()
        {
            getNextUpgrader().complete();
        }

    }

    private static final class Upgrader_1_3_to_2_0 extends StoreUpgraderPhase
    {
        private final VirtualHostEntryUpgrader _virtualHostUpgrader;

        private Upgrader_1_3_to_2_0()
        {
            super("modelVersion", "1.3", "2.0");
            _virtualHostUpgrader = new VirtualHostEntryUpgrader();
        }

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {
            if (record.getType().equals("VirtualHost"))
            {
                Map<String, Object> attributes = record.getAttributes();
                if (attributes.containsKey("configPath"))
                {
                    throw new IllegalConfigurationException("Auto-upgrade of virtual host " + attributes.get("name") + " having XML configuration is not supported. Virtual host configuration file is " + attributes.get("configPath"));
                }

                record = _virtualHostUpgrader.upgrade(record);
                getUpdateMap().put(record.getId(), record);
            }
            else if (record.getType().equals("Plugin") && record.getAttributes().containsKey("pluginType"))
            {
                Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                updatedAttributes.put("type", updatedAttributes.remove("pluginType"));
                record = new ConfiguredObjectRecordImpl(record.getId(), record.getType(), updatedAttributes, record.getParents());
                getUpdateMap().put(record.getId(), record);

            }
            else if (record.getType().equals("Broker"))
            {
                record = upgradeRootRecord(record);
            }

            getNextUpgrader().configuredObject(record);

        }

        @Override
        public void complete()
        {
            getNextUpgrader().complete();
        }

    }

    private static class VirtualHostEntryUpgrader
    {
        @SuppressWarnings("serial")
        Map<String, AttributesTransformer> _messageStoreToNodeTransformers = new HashMap<String, AttributesTransformer>()
        {{
                put("DERBY", new AttributesTransformer().
                        addAttributeTransformer("id", copyAttribute()).
                        addAttributeTransformer("name", copyAttribute()).
                        addAttributeTransformer("createdTime", copyAttribute()).
                        addAttributeTransformer("createdBy", copyAttribute()).
                        addAttributeTransformer("storePath", copyAttribute()).
                        addAttributeTransformer("storeUnderfullSize", copyAttribute()).
                        addAttributeTransformer("storeOverfullSize", copyAttribute()));
                put("Memory",  new AttributesTransformer().
                        addAttributeTransformer("id", copyAttribute()).
                        addAttributeTransformer("name", copyAttribute()).
                        addAttributeTransformer("createdTime", copyAttribute()).
                        addAttributeTransformer("createdBy", copyAttribute()));
                put("BDB", new AttributesTransformer().
                        addAttributeTransformer("id", copyAttribute()).
                        addAttributeTransformer("name", copyAttribute()).
                        addAttributeTransformer("createdTime", copyAttribute()).
                        addAttributeTransformer("createdBy", copyAttribute()).
                        addAttributeTransformer("storePath", copyAttribute()).
                        addAttributeTransformer("storeUnderfullSize", copyAttribute()).
                        addAttributeTransformer("storeOverfullSize", copyAttribute()).
                        addAttributeTransformer("bdbEnvironmentConfig", mutateAttributeName("context")));
                put("JDBC", new AttributesTransformer().
                        addAttributeTransformer("id", copyAttribute()).
                        addAttributeTransformer("name", copyAttribute()).
                        addAttributeTransformer("createdTime", copyAttribute()).
                        addAttributeTransformer("createdBy", copyAttribute()).
                        addAttributeTransformer("storePath", mutateAttributeName("connectionURL")).
                        addAttributeTransformer("connectionURL", mutateAttributeName("connectionUrl")).
                        addAttributeTransformer("connectionPool", new AttributeTransformer()
                        {
                            @Override
                            public MutableEntry transform(MutableEntry entry)
                            {
                               Object value = entry.getValue();
                                if ("DEFAULT".equals(value))
                                {
                                    value = "NONE";
                                }
                                return new MutableEntry("connectionPoolType", value);
                            }
                        }).
                        addAttributeTransformer("jdbcBigIntType", addContextVar("qpid.jdbcstore.bigIntType")).
                        addAttributeTransformer("jdbcBytesForBlob", addContextVar("qpid.jdbcstore.useBytesForBlob")).
                        addAttributeTransformer("jdbcBlobType", addContextVar("qpid.jdbcstore.blobType")).
                        addAttributeTransformer("jdbcVarbinaryType", addContextVar("qpid.jdbcstore.varBinaryType")).
                        addAttributeTransformer("partitionCount", addContextVar("qpid.jdbcstore.bonecp.partitionCount")).
                        addAttributeTransformer("maxConnectionsPerPartition", addContextVar("qpid.jdbcstore.bonecp.maxConnectionsPerPartition")).
                        addAttributeTransformer("minConnectionsPerPartition", addContextVar("qpid.jdbcstore.bonecp.minConnectionsPerPartition")));
                put("BDB_HA", new AttributesTransformer().
                        addAttributeTransformer("id", copyAttribute()).
                        addAttributeTransformer("createdTime", copyAttribute()).
                        addAttributeTransformer("createdBy", copyAttribute()).
                        addAttributeTransformer("storePath", copyAttribute()).
                        addAttributeTransformer("storeUnderfullSize", copyAttribute()).
                        addAttributeTransformer("storeOverfullSize", copyAttribute()).
                        addAttributeTransformer("haNodeName", mutateAttributeName("name")).
                        addAttributeTransformer("haGroupName", mutateAttributeName("groupName")).
                        addAttributeTransformer("haHelperAddress", mutateAttributeName("helperAddress")).
                        addAttributeTransformer("haNodeAddress", mutateAttributeName("address")).
                        addAttributeTransformer("haDesignatedPrimary", mutateAttributeName("designatedPrimary")).
                        addAttributeTransformer("haReplicationConfig", mutateAttributeName("context")).
                        addAttributeTransformer("bdbEnvironmentConfig", mutateAttributeName("context")));
            }};

        public ConfiguredObjectRecord upgrade(ConfiguredObjectRecord vhost)
        {
            Map<String, Object> attributes = vhost.getAttributes();
            String type = (String) attributes.get("type");
            AttributesTransformer nodeAttributeTransformer = null;
            if ("STANDARD".equalsIgnoreCase(type))
            {
                if (attributes.containsKey("configStoreType"))
                {
                    throw new IllegalConfigurationException("Auto-upgrade of virtual host " + attributes.get("name")
                            + " with split configuration and message store is not supported."
                            + " Configuration store type is " + attributes.get("configStoreType") + " and message store type is "
                            + attributes.get("storeType"));
                }
                else
                {
                    type = (String) attributes.get("storeType");
                }
            }

            if (type == null)
            {
                throw new IllegalConfigurationException("Cannot auto-upgrade virtual host with attributes: " + attributes);
            }

            type = getVirtualHostNodeType(type);
            nodeAttributeTransformer = _messageStoreToNodeTransformers.get(type);

            if (nodeAttributeTransformer == null)
            {
                throw new IllegalConfigurationException("Don't know how to perform an upgrade from version for virtualhost type " + type);
            }

            Map<String, Object> nodeAttributes = nodeAttributeTransformer.upgrade(attributes);
            nodeAttributes.put("type", type);
            return new ConfiguredObjectRecordImpl(vhost.getId(), "VirtualHostNode", nodeAttributes, vhost.getParents());
        }

        private String getVirtualHostNodeType(String type)
        {
            for (String t : _messageStoreToNodeTransformers.keySet())
            {
                if (type.equalsIgnoreCase(t))
                {
                    return t;
                }
            }
            return null;
        }
    }

    private static class AttributesTransformer
    {
        private final Map<String, List<AttributeTransformer>> _transformers = new HashMap<String, List<AttributeTransformer>>();

        public AttributesTransformer addAttributeTransformer(String string, AttributeTransformer... attributeTransformers)
        {
            _transformers.put(string, Arrays.asList(attributeTransformers));
            return this;
        }

        public Map<String, Object> upgrade(Map<String, Object> attributes)
        {
            Map<String, Object> settings = new HashMap<>();
            for (Map.Entry<String, List<AttributeTransformer>> entry : _transformers.entrySet())
            {
                String attributeName = entry.getKey();
                if (attributes.containsKey(attributeName))
                {
                    Object attributeValue = attributes.get(attributeName);
                    MutableEntry newEntry = new MutableEntry(attributeName, attributeValue);

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
                        if (settings.get(newEntry.getKey()) instanceof Map && newEntry.getValue() instanceof Map)
                        {
                            final Map newMap = (Map)newEntry.getValue();
                            final Map mergedMap = new HashMap((Map) settings.get(newEntry.getKey()));
                            mergedMap.putAll(newMap);
                            settings.put(newEntry.getKey(), mergedMap);
                        }
                        else
                        {
                            settings.put(newEntry.getKey(), newEntry.getValue());
                        }
                    }
                }
            }
            return settings;
        }
    }

    private static AttributeTransformer copyAttribute()
    {
        return CopyAttribute.INSTANCE;
    }

    private static AttributeTransformer mutateAttributeName(String newName)
    {
        return new MutateAttributeName(newName);
    }

    private static AttributeTransformer addContextVar(String newName)
    {
        return new AddContextVar(newName);
    }

    private static interface AttributeTransformer
    {
        MutableEntry transform(MutableEntry entry);
    }

    private static class CopyAttribute implements AttributeTransformer
    {
        private static final CopyAttribute INSTANCE = new CopyAttribute();

        private CopyAttribute()
        {
        }

        @Override
        public MutableEntry transform(MutableEntry entry)
        {
            return entry;
        }
    }

    private static class AddContextVar implements AttributeTransformer
    {
        private final String _newName;

        public AddContextVar(String newName)
        {
            _newName = newName;
        }

        @Override
        public MutableEntry transform(MutableEntry entry)
        {
            return new MutableEntry("context", Collections.singletonMap(_newName, entry.getValue()));
        }
    }

    private static class MutateAttributeName implements AttributeTransformer
    {
        private final String _newName;

        public MutateAttributeName(String newName)
        {
            _newName = newName;
        }

        @Override
        public MutableEntry transform(MutableEntry entry)
        {
            entry.setKey(_newName);
            return entry;
        }
    }

    private static class MutableEntry
    {
        private String _key;
        private Object _value;

        public MutableEntry(String key, Object value)
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
    }

    public Broker<?> perform(final DurableConfigurationStore store)
    {
        List<ConfiguredObjectRecord> upgradedRecords = upgrade(store);
        new GenericRecoverer(_systemConfig, Broker.class.getSimpleName()).recover(upgradedRecords);

        final StoreConfigurationChangeListener configChangeListener = new StoreConfigurationChangeListener(store);
        applyRecursively(_systemConfig.getBroker(), new Action<ConfiguredObject<?>>()
        {
            @Override
            public void performAction(final ConfiguredObject<?> object)
            {
                 object.addChangeListener(configChangeListener);
            }
        });

        return _systemConfig.getBroker();
    }

    List<ConfiguredObjectRecord> upgrade(final DurableConfigurationStore store)
    {
        GenericStoreUpgrader upgrader = new GenericStoreUpgrader(Broker.class.getSimpleName(), Broker.MODEL_VERSION, store, _upgraders);
        upgrader.upgrade();
        return upgrader.getRecords();
    }

    private void applyRecursively(final ConfiguredObject<?> object, final Action<ConfiguredObject<?>> action)
    {
        applyRecursively(object, action, new HashSet<ConfiguredObject<?>>());
    }

    private void applyRecursively(final ConfiguredObject<?> object,
                                  final Action<ConfiguredObject<?>> action,
                                  final HashSet<ConfiguredObject<?>> visited)
    {
        if(!visited.contains(object))
        {
            visited.add(object);
            action.performAction(object);
            for(Class<? extends ConfiguredObject> childClass : object.getModel().getChildTypes(object.getCategoryClass()))
            {
                Collection<? extends ConfiguredObject> children = object.getChildren(childClass);
                if(children != null)
                {
                    for(ConfiguredObject<?> child : children)
                    {
                        applyRecursively(child, action, visited);
                    }
                }
            }
        }
    }

}
