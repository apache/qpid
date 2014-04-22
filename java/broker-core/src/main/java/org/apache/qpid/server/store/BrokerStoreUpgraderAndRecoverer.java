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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.util.Action;

public class BrokerStoreUpgraderAndRecoverer
{
    private final SystemContext<?> _systemContext;
    private final Map<String, StoreUpgraderPhase> _upgraders = new HashMap<String, StoreUpgraderPhase>();

    // Note: don't use externally defined constants in upgraders in case they change, the values here MUST stay the same
    // no matter what changes are made to the code in the future
    public BrokerStoreUpgraderAndRecoverer(SystemContext<?> systemContext)
    {
        _systemContext = systemContext;

        register(new Upgrader_1_0_to_1_1());
        register(new Upgrader_1_1_to_1_2());
        register(new Upgrader_1_2_to_1_3());
        register(new Upgrader_1_3_to_1_4());
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

    private static final class Upgrader_1_3_to_1_4 extends StoreUpgraderPhase
    {
        private Upgrader_1_3_to_1_4()
        {
            super("modelVersion", "1.3", "1.4");
        }

        @SuppressWarnings("serial")
        private Map<String, VirtualHostEntryUpgrader> _vhostUpgraderMap = new HashMap<String, VirtualHostEntryUpgrader>()
        {{
            put("BDB_HA", new BdbHaVirtualHostUpgrader());
            put("STANDARD", new StandardVirtualHostUpgrader());
        }};

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

                String type = (String) attributes.get("type");
                VirtualHostEntryUpgrader vhostUpgrader = _vhostUpgraderMap.get(type);
                if (vhostUpgrader == null)
                {
                    throw new IllegalConfigurationException("Don't know how to perform an upgrade from version for virtualhost type " + type);
                }
                record = vhostUpgrader.upgrade(record);
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

    private static interface VirtualHostEntryUpgrader
    {
        ConfiguredObjectRecord upgrade(ConfiguredObjectRecord vhost);
    }

    private static class StandardVirtualHostUpgrader implements VirtualHostEntryUpgrader
    {
        @SuppressWarnings("serial")
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

        @SuppressWarnings("serial")
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
        public ConfiguredObjectRecord upgrade(ConfiguredObjectRecord vhost)
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

            return new ConfiguredObjectRecordImpl(vhost.getId(), vhost.getType(), newAttributes, vhost.getParents());
        }
    }

    private static class BdbHaVirtualHostUpgrader implements VirtualHostEntryUpgrader
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
        public ConfiguredObjectRecord upgrade(ConfiguredObjectRecord vhost)
        {
            Map<String, Object> attributes = vhost.getAttributes();

            Map<String, Object> messageStoreSettings = haAttributesTransformer.upgrade(attributes);

            Map<String, Object> newAttributes = new HashMap<String, Object>(attributes);
            newAttributes.keySet().removeAll(haAttributesTransformer.getNamesToBeDeleted());
            newAttributes.put("messageStoreSettings", messageStoreSettings);

            return new ConfiguredObjectRecordImpl(vhost.getId(), vhost.getType(), newAttributes, vhost.getParents());
        }
    }

    private static class AttributesTransformer
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

    private static AttributeTransformer copyAttribute()
    {
        return CopyAttribute.INSTANCE;
    }

    private static AttributeTransformer removeAttribute()
    {
        return RemoveAttribute.INSTANCE;
    }

    private static AttributeTransformer mutateAttributeValue(Object newValue)
    {
        return new MutateAttributeValue(newValue);
    }

    private static AttributeTransformer mutateAttributeName(String newName)
    {
        return new MutateAttributeName(newName);
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

    private static class RemoveAttribute implements AttributeTransformer
    {
        private static final RemoveAttribute INSTANCE = new RemoveAttribute();

        private RemoveAttribute()
        {
        }

        @Override
        public MutableEntry transform(MutableEntry entry)
        {
            return null;
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

    private static class MutateAttributeValue implements AttributeTransformer
    {
        private final Object _newValue;

        public MutateAttributeValue(Object newValue)
        {
            _newValue = newValue;
        }

        @Override
        public MutableEntry transform(MutableEntry entry)
        {
            entry.setValue(_newValue);
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

        public void setValue(Object value)
        {
            _value = value;
        }
    }

    public Broker<?> perform(final DurableConfigurationStore store)
    {
        final String brokerCategory = Broker.class.getSimpleName();
        final GenericStoreUpgrader upgrader = new GenericStoreUpgrader(brokerCategory, Broker.MODEL_VERSION, store, _upgraders);
        upgrader.upgrade();

        new GenericRecoverer(_systemContext, brokerCategory).recover(upgrader.getRecords());

        final StoreConfigurationChangeListener configChangeListener = new StoreConfigurationChangeListener(store);
        applyRecursively(_systemContext.getBroker(), new Action<ConfiguredObject<?>>()
        {
            @Override
            public void performAction(final ConfiguredObject<?> object)
            {
                 object.addChangeListener(configChangeListener);
            }
        });

        return _systemContext.getBroker();
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
