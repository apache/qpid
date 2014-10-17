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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.queue.QueueArgumentsConverter;

public class VirtualHostStoreUpgraderAndRecoverer
{
    private final VirtualHostNode<?> _virtualHostNode;
    private Map<String, StoreUpgraderPhase> _upgraders = new HashMap<String, StoreUpgraderPhase>();

    @SuppressWarnings("serial")
    private static final Map<String, String> DEFAULT_EXCHANGES = Collections.unmodifiableMap(new HashMap<String, String>()
    {{
        put("amq.direct", "direct");
        put("amq.topic", "topic");
        put("amq.fanout", "fanout");
        put("amq.match", "headers");
    }});

    private final Map<String, UUID> _defaultExchangeIds;

    public VirtualHostStoreUpgraderAndRecoverer(VirtualHostNode<?> virtualHostNode)
    {
        _virtualHostNode = virtualHostNode;
        register(new Upgrader_0_0_to_0_1());
        register(new Upgrader_0_1_to_0_2());
        register(new Upgrader_0_2_to_0_3());
        register(new Upgrader_0_3_to_0_4());
        register(new Upgrader_0_4_to_2_0());
        register(new Upgrader_2_0_to_2_1());

        Map<String, UUID> defaultExchangeIds = new HashMap<String, UUID>();
        for (String exchangeName : DEFAULT_EXCHANGES.keySet())
        {
            UUID id = UUIDGenerator.generateExchangeUUID(exchangeName, virtualHostNode.getName());
            defaultExchangeIds.put(exchangeName, id);
        }
        _defaultExchangeIds = Collections.unmodifiableMap(defaultExchangeIds);
    }

    private void register(StoreUpgraderPhase upgrader)
    {
        _upgraders.put(upgrader.getFromVersion(), upgrader);
    }

    /*
     * Removes filters from queue bindings to exchanges other than topic exchanges.  In older versions of the broker
     * such bindings would have been ignored, starting from the point at which the config version changed, these
     * arguments would actually cause selectors to be enforced, thus changing which messages would reach a queue.
     */
    private class Upgrader_0_0_to_0_1  extends StoreUpgraderPhase
    {
        private final Map<UUID, ConfiguredObjectRecord> _records = new HashMap<UUID, ConfiguredObjectRecord>();

        public Upgrader_0_0_to_0_1()
        {
            super("modelVersion", "0.0", "0.1");
        }

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
            UUID exchangeId = entry.getParents().get("Exchange");
            if (exchangeId == null)
            {
                return false;
            }

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

    }

    /*
     * Change the type string from org.apache.qpid.server.model.Foo to Foo (in line with the practice in the broker
     * configuration store).  Also remove bindings which reference nonexistent queues or exchanges.
     */
    private class Upgrader_0_1_to_0_2 extends StoreUpgraderPhase
    {
        public Upgrader_0_1_to_0_2()
        {
            super("modelVersion", "0.1", "0.2");
        }

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
                final UUID exchangeParent = record.getParents().get(Exchange.class.getSimpleName());
                final UUID queueParent = record.getParents().get(Queue.class.getSimpleName());
                if(isBinding(record.getType()) && (exchangeParent == null || unknownExchange(exchangeParent)
                                                   || queueParent == null || unknownQueue(queueParent)))
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
    }


    /*
     * Convert the storage of queue attributes to remove the separate "ARGUMENT" attribute, and flatten the
     * attributes into the map using the model attribute names rather than the wire attribute names
     */
    private class Upgrader_0_2_to_0_3 extends StoreUpgraderPhase
    {
        private static final String ARGUMENTS = "arguments";

        public Upgrader_0_2_to_0_3()
        {
            super("modelVersion", "0.2", "0.3");
        }

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

    }

    /*
     * Convert the storage of queue attribute exclusive to change exclusive from a boolean to an enum
     * where exclusive was false it will now be "NONE", and where true it will now be "CONTAINER"
     * ensure OWNER is null unless the exclusivity policy is CONTAINER
     */
    private class Upgrader_0_3_to_0_4 extends StoreUpgraderPhase
    {
        private static final String EXCLUSIVE = "exclusive";

        public Upgrader_0_3_to_0_4()
        {
            super("modelVersion", "0.3", "0.4");
        }


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

    }

    private class Upgrader_0_4_to_2_0 extends StoreUpgraderPhase
    {
        private Map<String, String> _missingAmqpExchanges = new HashMap<String, String>(DEFAULT_EXCHANGES);
        private ConfiguredObjectRecord _virtualHostRecord;

        public Upgrader_0_4_to_2_0()
        {
            super("modelVersion", "0.4", "2.0");
        }

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {
            if("VirtualHost".equals(record.getType()))
            {
                record = upgradeRootRecord(record);
                Map<String, Object> virtualHostAttributes = new HashMap<String, Object>(record.getAttributes());
                virtualHostAttributes.put("name", _virtualHostNode.getName());
                virtualHostAttributes.put("modelVersion", getToVersion());
                record = new ConfiguredObjectRecordImpl(record.getId(), "VirtualHost", virtualHostAttributes, Collections.<String, UUID>emptyMap());
                _virtualHostRecord = record;
            }
            else if("Exchange".equals(record.getType()))
            {
                Map<String, Object> attributes = record.getAttributes();
                String name = (String)attributes.get("name");
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
                attributes.put("name", name);
                attributes.put("type", type);
                attributes.put("lifetimePolicy", "PERMANENT");

                ConfiguredObjectRecord record = new ConfiguredObjectRecordImpl(id, Exchange.class.getSimpleName(), attributes, Collections.singletonMap(_virtualHostRecord.getType(), _virtualHostRecord.getId()));
                getUpdateMap().put(id, record);

                getNextUpgrader().configuredObject(record);

            }

            getNextUpgrader().complete();
        }

    }

    private class Upgrader_2_0_to_2_1 extends StoreUpgraderPhase
    {
        public Upgrader_2_0_to_2_1()
        {
            super("modelVersion", "2.0", "2.1");
        }

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {

            if("VirtualHost".equals(record.getType()))
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

    public void perform(DurableConfigurationStore durableConfigurationStore)
    {
        String virtualHostCategory = VirtualHost.class.getSimpleName();
        GenericStoreUpgrader upgraderHandler = new GenericStoreUpgrader(virtualHostCategory, VirtualHost.MODEL_VERSION, durableConfigurationStore, _upgraders);
        upgraderHandler.upgrade();

        new GenericRecoverer(_virtualHostNode).recover(upgraderHandler.getRecords());
    }
}
