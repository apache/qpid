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
package org.apache.qpid.server.virtualhost;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStoreUpgrader;
import org.apache.qpid.server.store.NonNullUpgrader;
import org.apache.qpid.server.store.NullUpgrader;
import org.apache.qpid.server.store.UpgraderProvider;

public class DefaultUpgraderProvider implements UpgraderProvider
{
    private static final Logger LOGGER = Logger.getLogger(DefaultUpgraderProvider.class);

    public static final String EXCLUSIVE = "exclusive";
    public static final String NAME = "name";
    private final VirtualHostImpl _virtualHost;

    @SuppressWarnings("serial")
    private static final Map<String, String> DEFAULT_EXCHANGES = Collections.unmodifiableMap(new HashMap<String, String>()
    {{
        put("amq.direct", "direct");
        put("amq.topic", "topic");
        put("amq.fanout", "fanout");
        put("amq.match", "headers");
    }});

    private final Map<String, UUID> _defaultExchangeIds;

    public DefaultUpgraderProvider(final VirtualHostImpl virtualHost)
    {
        _virtualHost = virtualHost;
        Map<String, UUID> defaultExchangeIds = new HashMap<String, UUID>();
        for (String exchangeName : DEFAULT_EXCHANGES.keySet())
        {
            UUID id = UUIDGenerator.generateExchangeUUID(exchangeName, _virtualHost.getName());
            defaultExchangeIds.put(exchangeName, id);
        }
        _defaultExchangeIds = Collections.unmodifiableMap(defaultExchangeIds);
    }

    public DurableConfigurationStoreUpgrader getUpgrader(final String configVersion, DurableConfigurationRecoverer recoverer)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Getting upgrader for configVersion:  " + configVersion);
        }
        DurableConfigurationStoreUpgrader currentUpgrader = null;

        int conigVersionAsInteger = Integer.parseInt(configVersion.replace(".", ""));
        switch(conigVersionAsInteger)
        {
            case 0:
                currentUpgrader = addUpgrader(currentUpgrader, new Version0Upgrader());
            case 1:
                currentUpgrader = addUpgrader(currentUpgrader, new Version1Upgrader());
            case 2:
                currentUpgrader = addUpgrader(currentUpgrader, new Version2Upgrader());
            case 3:
                currentUpgrader = addUpgrader(currentUpgrader, new Version3Upgrader());
            case 4:
                currentUpgrader = addUpgrader(currentUpgrader, new Version4Upgrader());
            case (BrokerModel.MODEL_MAJOR_VERSION * 10) + BrokerModel.MODEL_MINOR_VERSION:
                currentUpgrader = addUpgrader(currentUpgrader, new NullUpgrader(recoverer));
                break;

            default:
                throw new IllegalStateException("Unknown configuration model version: " + configVersion
                                                + ". Attempting to run an older instance against an upgraded configuration?");
        }
        return currentUpgrader;
    }

    private DurableConfigurationStoreUpgrader addUpgrader(DurableConfigurationStoreUpgrader currentUpgrader,
                                                          final DurableConfigurationStoreUpgrader nextUpgrader)
    {
        if(currentUpgrader == null)
        {
            currentUpgrader = nextUpgrader;
        }
        else
        {
            currentUpgrader.setNextUpgrader(nextUpgrader);
        }
        return currentUpgrader;
    }

    /*
     * Removes filters from queue bindings to exchanges other than topic exchanges.  In older versions of the broker
     * such bindings would have been ignored, starting from the point at which the config version changed, these
     * arguments would actually cause selectors to be enforced, thus changing which messages would reach a queue.
     */
    private class Version0Upgrader extends NonNullUpgrader
    {
        private final Map<UUID, ConfiguredObjectRecord> _records = new HashMap<UUID, ConfiguredObjectRecord>();

        public Version0Upgrader()
        {
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

                return _virtualHost.getExchange(exchangeId) != null
                       && _virtualHost.getExchange(exchangeId).getExchangeType() == TopicExchange.TYPE;
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
                if(type.equals(Binding.class.getName()) && hasSelectorArguments(attributes) && !isTopicExchange(record))
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
    private class Version1Upgrader extends NonNullUpgrader
    {


        @Override
        public void configuredObject(final ConfiguredObjectRecord record)
        {
            String type = record.getType().substring(1 + record.getType().lastIndexOf('.'));
            getUpdateMap().put(record.getId(),
                               new ConfiguredObjectRecordImpl(record.getId(), type, record.getAttributes(), record.getParents()));
        }

        @Override
        public void complete()
        {
            for(Map.Entry<UUID, ConfiguredObjectRecord> entry : getUpdateMap().entrySet())
            {
                final ConfiguredObjectRecord record = entry.getValue();
                final ConfiguredObjectRecord exchangeParent = record.getParents().get(Exchange.class.getSimpleName());
                final ConfiguredObjectRecord queueParent = record.getParents().get(Queue.class.getSimpleName());
                if(isBinding(record.getType()) && (exchangeParent == null || unknownExchange(exchangeParent.getId())
                                                   || queueParent == null || unknownQueue(queueParent.getId())))
                {
                    getDeleteMap().put(entry.getKey(), entry.getValue());
                    entry.setValue(null);
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
            return !((localRecord != null && localRecord.getType().equals(Exchange.class.getSimpleName()))
                     || _virtualHost.getExchange(exchangeId) != null);
        }

        private boolean unknownQueue(final UUID queueId)
        {
            ConfiguredObjectRecord localRecord = getUpdateMap().get(queueId);
            return !((localRecord != null  && localRecord.getType().equals(Queue.class.getSimpleName()))
                     || _virtualHost.getQueue(queueId) != null);
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
        private class Version2Upgrader extends NonNullUpgrader
        {

            private static final String ARGUMENTS = "arguments";

            @Override
            public void configuredObject(ConfiguredObjectRecord record)
            {

                if(Queue.class.getSimpleName().equals(record.getType()))
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
    private class Version3Upgrader extends NonNullUpgrader
    {

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {

            if(Queue.class.getSimpleName().equals(record.getType()))
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

    private class Version4Upgrader extends NonNullUpgrader
    {
        private Map<String, String> _missingAmqpExchanges = new HashMap<String, String>(DEFAULT_EXCHANGES);

        @Override
        public void configuredObject(ConfiguredObjectRecord record)
        {
            if(Exchange.class.getSimpleName().equals(record.getType()))
            {
                Map<String, Object> attributes = record.getAttributes();
                String name = (String)attributes.get(NAME);
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

                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Creating amqp exchange " + name + " with id " + id);
                }

                Map<String, Object> attributes = new HashMap<String, Object>();
                attributes.put(org.apache.qpid.server.model.Exchange.NAME, name);
                attributes.put(org.apache.qpid.server.model.Exchange.TYPE, type);

                attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, true);

                ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(_virtualHost.getId(), org.apache.qpid.server.model.VirtualHost.class.getSimpleName(), Collections.<String, Object>emptyMap());
                ConfiguredObjectRecord record = new ConfiguredObjectRecordImpl(id, Exchange.class.getSimpleName(), attributes, Collections.singletonMap(virtualHostRecord.getType(), virtualHostRecord));
                getUpdateMap().put(id, record);

                getNextUpgrader().configuredObject(record);

            }

            getNextUpgrader().complete();
        }
    }

}
