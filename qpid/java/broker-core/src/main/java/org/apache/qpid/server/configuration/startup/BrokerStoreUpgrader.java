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

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreUpgrader;
import org.apache.qpid.server.store.NonNullUpgrader;

import java.util.*;

public class BrokerStoreUpgrader
{
    private static Logger LOGGER = Logger.getLogger(BrokerStoreUpgrader.class);

    private static Map<String, UpgraderPhaseFactory> _upgraders = new HashMap<String, UpgraderPhaseFactory>();
    private final SystemContext _systemContext;

    public BrokerStoreUpgrader(SystemContext systemContext)
    {
        _systemContext = systemContext;
    }

    private static abstract class UpgraderPhaseFactory
    {
        private final String _toVersion;

        protected UpgraderPhaseFactory(String fromVersion, String toVersion)
        {
            _upgraders.put(fromVersion, this);
            _toVersion = toVersion;
        }

        public String getToVersion()
        {
            return _toVersion;
        }

        public abstract BrokerStoreUpgraderPhase newInstance();
    }

    private static abstract class BrokerStoreUpgraderPhase extends NonNullUpgrader
    {
        private final String _toVersion;

        protected BrokerStoreUpgraderPhase(String toVersion)
        {
            _toVersion = toVersion;
        }


        protected ConfiguredObjectRecord upgradeBrokerRecord(ConfiguredObjectRecord record)
        {
            Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
            updatedAttributes.put(Broker.MODEL_VERSION, _toVersion);
            record = createModifiedRecord(record, updatedAttributes);
            getUpdateMap().put(record.getId(), record);
            return record;
        }
    }

    // Note: don't use externally defined constants in upgraders in case they change, the values here MUST stay the same
    // no matter what changes are made to the code in the future

    private final static UpgraderPhaseFactory UPGRADE_1_0 = new UpgraderPhaseFactory("1.0", "1.1")
    {
        @Override
        public BrokerStoreUpgraderPhase newInstance()
        {
            return new BrokerStoreUpgraderPhase(getToVersion())
            {
                @Override
                public void configuredObject(ConfiguredObjectRecord record)
                {
                    if (record.getType().equals("Broker"))
                    {
                        record = upgradeBrokerRecord(record);
                    }
                    else if (record.getType().equals("VirtualHost") && record.getAttributes().containsKey("storeType"))
                    {
                        Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                        updatedAttributes.put("type", "STANDARD");
                        record = createModifiedRecord(record, updatedAttributes);
                        getUpdateMap().put(record.getId(), record);
                    }

                    getNextUpgrader().configuredObject(record);
                }


                @Override
                public void complete()
                {
                    getNextUpgrader().complete();
                }
            };
        }


    };


    protected static ConfiguredObjectRecordImpl createModifiedRecord(final ConfiguredObjectRecord record,
                                                                     final Map<String, Object> updatedAttributes)
    {

        return new ConfiguredObjectRecordImpl(record.getId(), record.getType(), updatedAttributes, record.getParents());
    }

    private final static UpgraderPhaseFactory UPGRADE_1_1 = new UpgraderPhaseFactory("1.1", "1.2")
    {
        @Override
        public BrokerStoreUpgraderPhase newInstance()
        {
            return new BrokerStoreUpgraderPhase(getToVersion())
            {

                @Override
                public void configuredObject(ConfiguredObjectRecord record)
                {
                    if (record.getType().equals("Broker"))
                    {
                        record = upgradeBrokerRecord(record);
                    }

                    getNextUpgrader().configuredObject(record);

                }

                @Override
                public void complete()
                {
                    getNextUpgrader().complete();
                }
            };
        }
    };


    private final static UpgraderPhaseFactory UPGRADE_1_2 = new UpgraderPhaseFactory("1.2", "1.3")
    {
        @Override
        public BrokerStoreUpgraderPhase newInstance()
        {
            return new BrokerStoreUpgraderPhase(getToVersion())
            {

                @Override
                public void configuredObject(ConfiguredObjectRecord record)
                {
                    if (record.getType().equals("TrustStore") && record.getAttributes().containsKey("type"))
                    {
                        Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                        updatedAttributes.put("trustStoreType", updatedAttributes.remove("type"));
                        record = createModifiedRecord(record, updatedAttributes);
                        getUpdateMap().put(record.getId(), record);

                    }
                    else if (record.getType().equals("KeyStore") && record.getAttributes().containsKey("type"))
                    {
                        Map<String, Object> updatedAttributes = new HashMap<String, Object>(record.getAttributes());
                        updatedAttributes.put("keyStoreType", updatedAttributes.remove("type"));
                        record = createModifiedRecord(record, updatedAttributes);
                        getUpdateMap().put(record.getId(), record);

                    }
                    else if (record.getType().equals("Broker"))
                    {
                        record = upgradeBrokerRecord(record);
                    }

                    getNextUpgrader().configuredObject(record);

                }

                @Override
                public void complete()
                {
                    getNextUpgrader().complete();
                }
            };
        }
    };


    private final static UpgraderPhaseFactory UPGRADE_1_3 = new UpgraderPhaseFactory("1.3", "1.4")
    {
        @Override
        public BrokerStoreUpgraderPhase newInstance()
        {
            return new BrokerStoreUpgraderPhase(getToVersion())
            {

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
                        record = createModifiedRecord(record, updatedAttributes);
                        getUpdateMap().put(record.getId(), record);

                    }
                    else if (record.getType().equals("Broker"))
                    {
                        record = upgradeBrokerRecord(record);
                    }

                    getNextUpgrader().configuredObject(record);

                }

                @Override
                public void complete()
                {
                    getNextUpgrader().complete();
                }
            };
        }


    };

    private static interface VirtualHostEntryUpgrader
    {
        ConfiguredObjectRecord upgrade(ConfiguredObjectRecord vhost);
    }

    private static class StandardVirtualHostUpgrader implements VirtualHostEntryUpgrader
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




    public Broker upgrade(ConfigurationEntryStore store)
    {
        final BrokerStoreRecoveryHandler recoveryHandler = new BrokerStoreRecoveryHandler(_systemContext);
        store.openConfigurationStore(_systemContext, Collections.<String,Object>emptyMap());
        store.recoverConfigurationStore(recoveryHandler);

        return recoveryHandler.getBroker();
    }


    private static class BrokerStoreRecoveryHandler implements ConfigurationRecoveryHandler
    {
        private static Logger LOGGER = Logger.getLogger(ConfigurationRecoveryHandler.class);

        private DurableConfigurationStoreUpgrader _upgrader;
        private DurableConfigurationStore _store;
        private final Map<UUID, ConfiguredObjectRecord> _records = new HashMap<UUID, ConfiguredObjectRecord>();
        private int _version;
        private final SystemContext _systemContext;

        private BrokerStoreRecoveryHandler(final SystemContext systemContext)
        {
            _systemContext = systemContext;
        }


        @Override
        public void beginConfigurationRecovery(final DurableConfigurationStore store, final int configVersion)
        {
            _store = store;
            _version = configVersion;
        }

        @Override
        public void configuredObject(final ConfiguredObjectRecord object)
        {
            _records.put(object.getId(), object);
        }

        @Override
        public int completeConfigurationRecovery()
        {
            String version = getCurrentVersion();

            while(!Model.MODEL_VERSION.equals(version))
            {
                LOGGER.debug("Adding broker store upgrader from model version: " + version);
                final UpgraderPhaseFactory upgraderPhaseFactory = _upgraders.get(version);
                BrokerStoreUpgraderPhase upgrader = upgraderPhaseFactory.newInstance();
                if(_upgrader == null)
                {
                    _upgrader = upgrader;
                }
                else
                {
                    _upgrader.setNextUpgrader(upgrader);
                }
                version = upgraderPhaseFactory.getToVersion();
            }

            if(_upgrader == null)
            {
                _upgrader = new DurableConfigurationStoreUpgrader()
                {

                    @Override
                    public void configuredObject(final ConfiguredObjectRecord record)
                    {
                    }

                    @Override
                    public void complete()
                    {
                    }

                    @Override
                    public void setNextUpgrader(final DurableConfigurationStoreUpgrader upgrader)
                    {
                    }

                    @Override
                    public Map<UUID, ConfiguredObjectRecord> getUpdatedRecords()
                    {
                        return Collections.emptyMap();
                    }

                    @Override
                    public Map<UUID, ConfiguredObjectRecord> getDeletedRecords()
                    {
                        return Collections.emptyMap();
                    }
                };
            }
            else
            {
                _upgrader.setNextUpgrader(new DurableConfigurationStoreUpgrader()
                {
                    @Override
                    public void configuredObject(final ConfiguredObjectRecord record)
                    {
                    }

                    @Override
                    public void complete()
                    {

                    }

                    @Override
                    public void setNextUpgrader(final DurableConfigurationStoreUpgrader upgrader)
                    {
                    }

                    @Override
                    public Map<UUID, ConfiguredObjectRecord> getUpdatedRecords()
                    {
                        return Collections.emptyMap();
                    }

                    @Override
                    public Map<UUID, ConfiguredObjectRecord> getDeletedRecords()
                    {
                        return Collections.emptyMap();
                    }
                });
            }

            for(ConfiguredObjectRecord record : _records.values())
            {
                _upgrader.configuredObject(record);
            }

            Map<UUID, ConfiguredObjectRecord> deletedRecords = _upgrader.getDeletedRecords();
            Map<UUID, ConfiguredObjectRecord> updatedRecords = _upgrader.getUpdatedRecords();

            LOGGER.debug("Broker store upgrade: " + deletedRecords.size() + " records deleted");
            LOGGER.debug("Broker store upgrade: " + updatedRecords.size() + " records updated");
            LOGGER.debug("Broker store upgrade: " + _records.size() + " total records");

            _store.update(true, updatedRecords.values().toArray(new ConfiguredObjectRecord[updatedRecords.size()]));
            _store.remove(deletedRecords.values().toArray(new ConfiguredObjectRecord[deletedRecords.size()]));




            _records.keySet().removeAll(deletedRecords.keySet());
            _records.putAll(updatedRecords);

            _systemContext.resolveObjects(_records.values().toArray(new ConfiguredObjectRecord[_records.size()]));

            _systemContext.getBroker().addChangeListener(new StoreConfigurationChangeListener(_store));

            return _version;
        }

        private String getCurrentVersion()
        {
            for(ConfiguredObjectRecord record : _records.values())
            {
                if(record.getType().equals("Broker"))
                {
                    String version = (String) record.getAttributes().get(Broker.MODEL_VERSION);
                    if(version == null)
                    {
                        version = "1.0";
                    }
                    return version;
                }
            }
            return Model.MODEL_VERSION;
        }

        public Broker getBroker()
        {
            return _systemContext.getBroker();
        }
    }


}
