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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.startup.StoreUpgraderPhase;
import org.apache.qpid.server.configuration.startup.UpgraderPhaseFactory;
import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class VirtualHostStoreUpgraderAndRecoverer
{
    private final ConfiguredObjectFactory _objectFactory;
    private final VirtualHostNode<?> _virtualHostNode;
    private Map<String, UpgraderPhaseFactory> _upgraders = new HashMap<String, UpgraderPhaseFactory>();

    @SuppressWarnings("serial")
    private static final Map<String, String> DEFAULT_EXCHANGES = Collections.unmodifiableMap(new HashMap<String, String>()
    {{
        put("amq.direct", "direct");
        put("amq.topic", "topic");
        put("amq.fanout", "fanout");
        put("amq.match", "headers");
    }});

    private final Map<String, UUID> _defaultExchangeIds;

    public VirtualHostStoreUpgraderAndRecoverer(VirtualHostNode<?> virtualHostNode, ConfiguredObjectFactory objectFactory)
    {
        _virtualHostNode = virtualHostNode;
        _objectFactory = objectFactory;
        register(new UpgraderFactory_0_0());
        register(new UpgraderFactory_0_1());
        register(new UpgraderFactory_0_2());
        register(new UpgraderFactory_0_3());
        register(new UpgraderFactory_0_4());

        Map<String, UUID> defaultExchangeIds = new HashMap<String, UUID>();
        for (String exchangeName : DEFAULT_EXCHANGES.keySet())
        {
            UUID id = UUIDGenerator.generateExchangeUUID(exchangeName, virtualHostNode.getName());
            defaultExchangeIds.put(exchangeName, id);
        }
        _defaultExchangeIds = Collections.unmodifiableMap(defaultExchangeIds);
    }

    private void register(UpgraderPhaseFactory factory)
    {
        _upgraders.put(factory.getFromVersion(), factory);
    }

    /*
     * Removes filters from queue bindings to exchanges other than topic exchanges.  In older versions of the broker
     * such bindings would have been ignored, starting from the point at which the config version changed, these
     * arguments would actually cause selectors to be enforced, thus changing which messages would reach a queue.
     */
    private class UpgraderFactory_0_0  extends UpgraderPhaseFactory
    {
        private final Map<UUID, ConfiguredObjectRecord> _records = new HashMap<UUID, ConfiguredObjectRecord>();

        public UpgraderFactory_0_0()
        {
            super("0.0", "0.1");
        }


        @Override
        public StoreUpgraderPhase newInstance()
        {
            return new StoreUpgraderPhase("modelVersion", getToVersion())
            {

                @Override
                public void configuredObject(final ConfiguredObjectRecord record)
                {
                    _records.put(record.getId(), record);
                }

                private void removeSelectorArguments(Map<String, Object> binding)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = new LinkedHashMap<String, Object>((Map<String,Object>)binding.get(Binding.ARGUMENTS));

                    FilterSupport.removeFilters(arguments);
                    binding.put(Binding.ARGUMENTS, arguments);
                }

                private boolean isTopicExchange(ConfiguredObjectRecord entry)
                {
                    ConfiguredObjectRecord exchangeRecord = entry.getParents().get("Exchange");
                    if (exchangeRecord == null)
                    {
                        return false;
                    }
                    UUID exchangeId = exchangeRecord.getId();

                    if(_records.containsKey(exchangeId))
                    {
                        return "topic".equals(_records.get(exchangeId)
                                .getAttributes()
                                .get(org.apache.qpid.server.model.Exchange.TYPE));
                    }
                    else
                    {
                        if (_defaultExchangeIds.get("amq.topic").equals(exchangeId))
                        {
                            return true;
                        }

                        return false;
                    }

                }

                private boolean hasSelectorArguments(Map<String, Object> binding)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) binding.get(Binding.ARGUMENTS);
                    return (arguments != null) && FilterSupport.argumentsContainFilter(arguments);
                }

                @Override
                public void complete()
                {
                    for(Map.Entry<UUID,ConfiguredObjectRecord> entry : _records.entrySet())
                    {
                        ConfiguredObjectRecord record = entry.getValue();
                        String type = record.getType();
                        Map<String, Object> attributes = record.getAttributes();
                        UUID id = record.getId();
                        if ("org.apache.qpid.server.model.VirtualHost".equals(type))
                        {
                            record = upgradeRootRecord(record);
                        }
                        else if(type.equals(Binding.class.getName()) && hasSelectorArguments(attributes) && !isTopicExchange(record))
                        {
                            attributes = new LinkedHashMap<String, Object>(attributes);
                            removeSelectorArguments(attributes);

                            record = new ConfiguredObjectRecordImpl(id, type, attributes, record.getParents());
                            getUpdateMap().put(id, record);
                            entry.setValue(record);

                        }
                        getNextUpgrader().configuredObject(record);
                    }

                    getNextUpgrader().complete();
                }
            };
        }

    }

    /*
     * Change the type string from org.apache.qpid.server.model.Foo to Foo (in line with the practice in the broker
     * configuration store).  Also remove bindings which reference nonexistent queues or exchanges.
     */
    private class UpgraderFactory_0_1 extends UpgraderPhaseFactory
    {
        protected UpgraderFactory_0_1()
        {
            super("0.1", "0.2");
        }

        @Override
        public StoreUpgraderPhase newInstance()
        {
            return new StoreUpgraderPhase("modelVersion", getToVersion())
            {

                @Override
                public void configuredObject(final ConfiguredObjectRecord record)
                {
                    String type = record.getType().substring(1 + record.getType().lastIndexOf('.'));
                    ConfiguredObjectRecord newRecord = new ConfiguredObjectRecordImpl(record.getId(), type, record.getAttributes(), record.getParents());
                    getUpdateMap().put(record.getId(), newRecord);

                    if ("VirtualHost".equals(type))
                    {
                        newRecord = upgradeRootRecord(newRecord);
                    }
                }

                @Override
                public void complete()
                {
                    for (Iterator<Map.Entry<UUID, ConfiguredObjectRecord>> iterator = getUpdateMap().entrySet().iterator(); iterator.hasNext();)
                    {
                        Map.Entry<UUID, ConfiguredObjectRecord> entry = iterator.next();
                        final ConfiguredObjectRecord record = entry.getValue();
                        final ConfiguredObjectRecord exchangeParent = record.getParents().get(Exchange.class.getSimpleName());
                        final ConfiguredObjectRecord queueParent = record.getParents().get(Queue.class.getSimpleName());
                        if(isBinding(record.getType()) && (exchangeParent == null || unknownExchange(exchangeParent.getId())
                                                           || queueParent == null || unknownQueue(queueParent.getId())))
                        {
                            getDeleteMap().put(entry.getKey(), entry.getValue());
                            iterator.remove();
                        }
                        else
                        {
                            getNextUpgrader().configuredObject(record);
                        }
                    }
                    getNextUpgrader().complete();
                }

                private boolean unknownExchange(final UUID exchangeId)
                {
                    if (_defaultExchangeIds.containsValue(exchangeId))
                    {
                        return false;
                    }
                    ConfiguredObjectRecord localRecord = getUpdateMap().get(exchangeId);
                    return !(localRecord != null && localRecord.getType().equals(Exchange.class.getSimpleName()));
                }

                private boolean unknownQueue(final UUID queueId)
                {
                    ConfiguredObjectRecord localRecord = getUpdateMap().get(queueId);
                    return !(localRecord != null  && localRecord.getType().equals(Queue.class.getSimpleName()));
                }

                private boolean isBinding(final String type)
                {
                    return Binding.class.getSimpleName().equals(type);
                }
            };
        }
    }


    /*
     * Convert the storage of queue attributes to remove the separate "ARGUMENT" attribute, and flatten the
     * attributes into the map using the model attribute names rather than the wire attribute names
     */
    private class UpgraderFactory_0_2 extends UpgraderPhaseFactory
    {
        protected UpgraderFactory_0_2()
        {
            super("0.2", "0.3");
        }

        @Override
        public StoreUpgraderPhase newInstance()
        {
            return new StoreUpgraderPhase("modelVersion", getToVersion())
            {
                private static final String ARGUMENTS = "arguments";

                @SuppressWarnings("unchecked")
                @Override
                public void configuredObject(ConfiguredObjectRecord record)
                {
                    if("VirtualHost".equals(record.getType()))
                    {
                        record = upgradeRootRecord(record);
                    }
                    else if("Queue".equals(record.getType()))
                    {
                        Map<String, Object> newAttributes = new LinkedHashMap<String, Object>();
                        if(record.getAttributes().get(ARGUMENTS) instanceof Map)
                        {
                            newAttributes.putAll(QueueArgumentsConverter.convertWireArgsToModel((Map<String, Object>) record.getAttributes()
                                    .get(ARGUMENTS)));
                        }
                        newAttributes.putAll(record.getAttributes());

                        record = new ConfiguredObjectRecordImpl(record.getId(), record.getType(), newAttributes, record.getParents());
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
    }

    /*
     * Convert the storage of queue attribute exclusive to change exclusive from a boolean to an enum
     * where exclusive was false it will now be "NONE", and where true it will now be "CONTAINER"
     * ensure OWNER is null unless the exclusivity policy is CONTAINER
     */
    private class UpgraderFactory_0_3 extends UpgraderPhaseFactory
    {
        protected UpgraderFactory_0_3()
        {
            super("0.3", "0.4");
        }

        @Override
        public StoreUpgraderPhase newInstance()
        {
            return new StoreUpgraderPhase("modelVersion", getToVersion())
            {
                private static final String EXCLUSIVE = "exclusive";

                @Override
                public void configuredObject(ConfiguredObjectRecord record)
                {
                    if("VirtualHost".equals(record.getType()))
                    {
                        record = upgradeRootRecord(record);
                    }
                    else if(Queue.class.getSimpleName().equals(record.getType()))
                    {
                        Map<String, Object> newAttributes = new LinkedHashMap<String, Object>(record.getAttributes());
                        if(record.getAttributes().get(EXCLUSIVE) instanceof Boolean)
                        {
                            boolean isExclusive = (Boolean) record.getAttributes().get(EXCLUSIVE);
                            newAttributes.put(EXCLUSIVE, isExclusive ? "CONTAINER" : "NONE");
                            if(!isExclusive && record.getAttributes().containsKey("owner"))
                            {
                                newAttributes.remove("owner");
                            }
                        }
                        else
                        {
                            newAttributes.remove("owner");
                        }
                        if(!record.getAttributes().containsKey("durable"))
                        {
                            newAttributes.put("durable","true");
                        }

                        record = new ConfiguredObjectRecordImpl(record.getId(),record.getType(),newAttributes, record.getParents());
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
    }

    private class UpgraderFactory_0_4 extends UpgraderPhaseFactory
    {
        protected UpgraderFactory_0_4()
        {
            super("0.4", "2.0");
        }

        @Override
        public StoreUpgraderPhase newInstance()
        {
            return new StoreUpgraderPhase("modelVersion", getToVersion())
            {
                private Map<String, String> _missingAmqpExchanges = new HashMap<String, String>(DEFAULT_EXCHANGES);
                private static final String EXCHANGE_NAME = "name";
                private static final String EXCHANGE_TYPE = "type";
                private static final String EXCHANGE_DURABLE = "durable";
                private ConfiguredObjectRecord _virtualHostRecord;

                @Override
                public void configuredObject(ConfiguredObjectRecord record)
                {
                    if("VirtualHost".equals(record.getType()))
                    {
                        record = upgradeRootRecord(record);
                        Map<String, Object> virtualHostAttributes = new HashMap<String, Object>(record.getAttributes());
                        virtualHostAttributes.put("name", _virtualHostNode.getName());
                        virtualHostAttributes.put("modelVersion", getToVersion());
                        record = new ConfiguredObjectRecordImpl(record.getId(), "VirtualHost", virtualHostAttributes, Collections.<String, ConfiguredObjectRecord>emptyMap());
                        _virtualHostRecord = record;
                    }
                    else if("Exchange".equals(record.getType()))
                    {
                        Map<String, Object> attributes = record.getAttributes();
                        String name = (String)attributes.get(EXCHANGE_NAME);
                        _missingAmqpExchanges.remove(name);
                    }
                    getNextUpgrader().configuredObject(record);
                }

                @Override
                public void complete()
                {
                    for (Entry<String, String> entry : _missingAmqpExchanges.entrySet())
                    {
                        String name = entry.getKey();
                        String type = entry.getValue();
                        UUID id = _defaultExchangeIds.get(name);

                        Map<String, Object> attributes = new HashMap<String, Object>();
                        attributes.put(EXCHANGE_NAME, name);
                        attributes.put(EXCHANGE_TYPE, type);
                        attributes.put(EXCHANGE_DURABLE, true);

                        ConfiguredObjectRecord record = new ConfiguredObjectRecordImpl(id, Exchange.class.getSimpleName(), attributes, Collections.singletonMap(_virtualHostRecord.getType(), _virtualHostRecord));
                        getUpdateMap().put(id, record);

                        getNextUpgrader().configuredObject(record);

                    }

                    getNextUpgrader().complete();
                }
            };
        }

    }

    public void perform(DurableConfigurationStore durableConfigurationStore)
    {
        UpgradeAndRecoveryHandler vhrh = new UpgradeAndRecoveryHandler(_virtualHostNode, _objectFactory, durableConfigurationStore, _upgraders);
        durableConfigurationStore.visitConfiguredObjectRecords(vhrh);
    }

    //TODO: generalize this class
    private static class  UpgradeAndRecoveryHandler implements ConfiguredObjectRecordHandler
    {
        private static Logger LOGGER = Logger.getLogger(UpgradeAndRecoveryHandler.class);

        private final Map<UUID, ConfiguredObjectRecord> _records = new LinkedHashMap<UUID, ConfiguredObjectRecord>();
        private Map<String, UpgraderPhaseFactory> _upgraders;

        private final VirtualHostNode<?> _parent;
        private final ConfiguredObjectFactory _configuredObjectFactory;
        private final DurableConfigurationStore _store;

        public UpgradeAndRecoveryHandler(VirtualHostNode<?> parent, ConfiguredObjectFactory configuredObjectFactory, DurableConfigurationStore durableConfigurationStore, Map<String, UpgraderPhaseFactory> upgraders)
        {
            super();
            _parent = parent;
            _configuredObjectFactory = configuredObjectFactory;
            _upgraders = upgraders;
            _store = durableConfigurationStore;
        }

        @Override
        public void begin()
        {
        }

        @Override
        public boolean handle(final ConfiguredObjectRecord record)
        {
            _records.put(record.getId(), record);
            return true;
        }

        @Override
        public void end()
        {
            String version = getCurrentVersion();

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Store has model version " + version + ". Number of record(s) " + _records.size());
            }

            DurableConfigurationStoreUpgrader upgrader = buildUpgraderChain(version);

            for(ConfiguredObjectRecord record : _records.values())
            {
                upgrader.configuredObject(record);
            }

            upgrader.complete();

            Map<UUID, ConfiguredObjectRecord> deletedRecords = upgrader.getDeletedRecords();
            Map<UUID, ConfiguredObjectRecord> updatedRecords = upgrader.getUpdatedRecords();

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("VirtualHost store upgrade: " + deletedRecords.size() + " record(s) deleted");
                LOGGER.debug("VirtualHost store upgrade: " + updatedRecords.size() + " record(s) updated");
                LOGGER.debug("VirtualHost store upgrade: " + _records.size() + " total record(s)");
            }

            _store.update(true, updatedRecords.values().toArray(new ConfiguredObjectRecord[updatedRecords.size()]));
            _store.remove(deletedRecords.values().toArray(new ConfiguredObjectRecord[deletedRecords.size()]));

            _records.keySet().removeAll(deletedRecords.keySet());
            _records.putAll(updatedRecords);

            ConfiguredObjectRecord virtualHostRecord = null;
            for (ConfiguredObjectRecord record : _records.values())
            {
                LOGGER.debug("Found type " +  record.getType());
                if ("VirtualHost".equals(record.getType()))
                {
                    virtualHostRecord = record;
                    break;
                }
            }

            if (virtualHostRecord != null)
            {
                String parentCategory = _parent.getCategoryClass().getSimpleName();
                ConfiguredObjectRecord parentRecord = new ConfiguredObjectRecordImpl(_parent.getId(), parentCategory, Collections.<String, Object>emptyMap());
                Map<String, ConfiguredObjectRecord> rootParents = Collections.<String, ConfiguredObjectRecord>singletonMap(parentCategory, parentRecord);
                _records.put(virtualHostRecord.getId(), new ConfiguredObjectRecordImpl(virtualHostRecord.getId(), VirtualHost.class.getSimpleName(), virtualHostRecord.getAttributes(), rootParents));
                Collection<ConfiguredObjectRecord> records = _records.values();
                resolveObjects(_configuredObjectFactory, _parent, records.toArray(new ConfiguredObjectRecord[records.size()]));
            }
        }

        private DurableConfigurationStoreUpgrader buildUpgraderChain(String version)
        {
            DurableConfigurationStoreUpgrader head = null;
            while(!BrokerModel.MODEL_VERSION.equals(version))
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Adding virtual host store upgrader from model version: " + version);
                }
                final UpgraderPhaseFactory upgraderPhaseFactory = _upgraders.get(version);
                StoreUpgraderPhase upgrader = upgraderPhaseFactory.newInstance();
                if(head == null)
                {
                    head = upgrader;
                }
                else
                {
                    head.setNextUpgrader(upgrader);
                }
                version = upgraderPhaseFactory.getToVersion();
            }

            if(head == null)
            {
                head = new NullUpgrader();
            }
            else
            {
                head.setNextUpgrader(new NullUpgrader());
            }

            return head;
        }

        private String getCurrentVersion()
        {
            for(ConfiguredObjectRecord record : _records.values())
            {
                if(record.getType().equals("VirtualHost"))
                {
                    return (String) record.getAttributes().get(VirtualHost.MODEL_VERSION);
                }
            }
            return BrokerModel.MODEL_VERSION;
        }


        public void resolveObjects(ConfiguredObjectFactory factory, ConfiguredObject<?> root, ConfiguredObjectRecord... records)
        {
            Map<UUID, ConfiguredObject<?>> resolvedObjects = new HashMap<UUID, ConfiguredObject<?>>();
            resolvedObjects.put(root.getId(), root);

            Collection<ConfiguredObjectRecord> recordsWithUnresolvedParents = new ArrayList<ConfiguredObjectRecord>(Arrays.asList(records));
            Collection<UnresolvedConfiguredObject<? extends ConfiguredObject>> recordsWithUnresolvedDependencies =
                    new ArrayList<UnresolvedConfiguredObject<? extends ConfiguredObject>>();

            boolean updatesMade;

            do
            {
                updatesMade = false;
                Iterator<ConfiguredObjectRecord> iter = recordsWithUnresolvedParents.iterator();
                while (iter.hasNext())
                {
                    ConfiguredObjectRecord record = iter.next();
                    Collection<ConfiguredObject<?>> parents = new ArrayList<ConfiguredObject<?>>();
                    boolean foundParents = true;
                    for (ConfiguredObjectRecord parent : record.getParents().values())
                    {
                        if (!resolvedObjects.containsKey(parent.getId()))
                        {
                            foundParents = false;
                            break;
                        }
                        else
                        {
                            parents.add(resolvedObjects.get(parent.getId()));
                        }
                    }
                    if (foundParents)
                    {
                        iter.remove();
                        UnresolvedConfiguredObject<? extends ConfiguredObject> recovered =
                                factory.recover(record, parents.toArray(new ConfiguredObject<?>[parents.size()]));
                        Collection<ConfiguredObjectDependency<?>> dependencies =
                                recovered.getUnresolvedDependencies();
                        if (dependencies.isEmpty())
                        {
                            updatesMade = true;
                            ConfiguredObject<?> resolved = recovered.resolve();
                            resolvedObjects.put(resolved.getId(), resolved);
                        }
                        else
                        {
                            recordsWithUnresolvedDependencies.add(recovered);
                        }
                    }

                }

                Iterator<UnresolvedConfiguredObject<? extends ConfiguredObject>> unresolvedIter =
                        recordsWithUnresolvedDependencies.iterator();

                while(unresolvedIter.hasNext())
                {
                    UnresolvedConfiguredObject<? extends ConfiguredObject> unresolvedObject = unresolvedIter.next();
                    Collection<ConfiguredObjectDependency<?>> dependencies =
                            new ArrayList<ConfiguredObjectDependency<?>>(unresolvedObject.getUnresolvedDependencies());

                    for(ConfiguredObjectDependency dependency : dependencies)
                    {
                        if(dependency instanceof ConfiguredObjectIdDependency)
                        {
                            UUID id = ((ConfiguredObjectIdDependency)dependency).getId();
                            if(resolvedObjects.containsKey(id))
                            {
                                dependency.resolve(resolvedObjects.get(id));
                            }
                        }
                        else if(dependency instanceof ConfiguredObjectNameDependency)
                        {
                            ConfiguredObject<?> dependentObject = null;
                            for(ConfiguredObject<?> parent : unresolvedObject.getParents())
                            {
                                dependentObject = parent.findConfiguredObject(dependency.getCategoryClass(), ((ConfiguredObjectNameDependency)dependency).getName());
                                if(dependentObject != null)
                                {
                                    break;
                                }
                            }
                            if(dependentObject != null)
                            {
                                dependency.resolve(dependentObject);
                            }
                        }
                        else
                        {
                            throw new ServerScopedRuntimeException("Unknown dependency type " + dependency.getClass().getSimpleName());
                        }
                    }
                    if(unresolvedObject.getUnresolvedDependencies().isEmpty())
                    {
                        updatesMade = true;
                        unresolvedIter.remove();
                        ConfiguredObject<?> resolved = unresolvedObject.resolve();
                        resolvedObjects.put(resolved.getId(), resolved);
                    }
                }

            } while(updatesMade && !(recordsWithUnresolvedDependencies.isEmpty() && recordsWithUnresolvedParents.isEmpty()));

            if(!recordsWithUnresolvedDependencies.isEmpty())
            {
                throw new IllegalArgumentException("Cannot resolve some objects: " + recordsWithUnresolvedDependencies);
            }
            if(!recordsWithUnresolvedParents.isEmpty())
            {
                throw new IllegalArgumentException("Cannot resolve object because their parents cannot be found" + recordsWithUnresolvedParents);
            }
        }

    }
}
