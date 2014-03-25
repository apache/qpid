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

import static org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.filter.FilterSupport;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
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
    private final ExchangeRegistry _exchangeRegistry;
    private final VirtualHost _virtualHost;

    @SuppressWarnings("serial")
    private static final Map<String, String> DEFAULT_EXCHANGES = Collections.unmodifiableMap(new HashMap<String, String>()
    {{
        put("amq.direct", "direct");
        put("amq.topic", "topic");
        put("amq.fanout", "fanout");
        put("amq.match", "headers");
    }});

    private final Map<String, UUID> _defaultExchangeIds;

    public DefaultUpgraderProvider(final VirtualHost virtualHost,
                                   final ExchangeRegistry exchangeRegistry)
    {
        _virtualHost = virtualHost;
        _exchangeRegistry = exchangeRegistry;
        Map<String, UUID> defaultExchangeIds = new HashMap<String, UUID>();
        for (String exchangeName : DEFAULT_EXCHANGES.keySet())
        {
            UUID id = UUIDGenerator.generateExchangeUUID(exchangeName, _virtualHost.getName());
            defaultExchangeIds.put(exchangeName, id);
        }
        _defaultExchangeIds = Collections.unmodifiableMap(defaultExchangeIds);
    }

    public DurableConfigurationStoreUpgrader getUpgrader(final int configVersion, DurableConfigurationRecoverer recoverer)
    {
        DurableConfigurationStoreUpgrader currentUpgrader = null;
        switch(configVersion)
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
            case CURRENT_CONFIG_VERSION:
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
        public void configuredObject(final UUID id, final String type, Map<String, Object> attributes)
        {
            _records.put(id, new ConfiguredObjectRecord(id, type, attributes));
        }

        private void removeSelectorArguments(Map<String, Object> binding)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = new LinkedHashMap<String, Object>((Map<String,Object>)binding.get(Binding.ARGUMENTS));

            FilterSupport.removeFilters(arguments);
            binding.put(Binding.ARGUMENTS, arguments);
        }

        private boolean isTopicExchange(Map<String, Object> binding)
        {
            UUID exchangeId = UUID.fromString((String)binding.get(Binding.EXCHANGE));

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

                return _exchangeRegistry.getExchange(exchangeId) != null
                       && _exchangeRegistry.getExchange(exchangeId).getExchangeType() == TopicExchange.TYPE;
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
                if(type.equals(Binding.class.getName()) && hasSelectorArguments(attributes) && !isTopicExchange(attributes))
                {
                    attributes = new LinkedHashMap<String, Object>(attributes);
                    removeSelectorArguments(attributes);

                    record = new ConfiguredObjectRecord(id, type, attributes);
                    getUpdateMap().put(id, record);
                    entry.setValue(record);

                }
                getNextUpgrader().configuredObject(id, type, attributes);
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
        public void configuredObject(final UUID id, String type, final Map<String, Object> attributes)
        {
            type = type.substring(1+type.lastIndexOf('.'));
            getUpdateMap().put(id, new ConfiguredObjectRecord(id, type, attributes));

        }

        @Override
        public void complete()
        {
            for(Map.Entry<UUID, ConfiguredObjectRecord> entry : getUpdateMap().entrySet())
            {
                final ConfiguredObjectRecord record = entry.getValue();
                if(isBinding(record.getType()) && (unknownExchange((String) record.getAttributes().get(Binding.EXCHANGE))
                                                   || unknownQueue((String) record.getAttributes().get(Binding.QUEUE))))
                {
                    entry.setValue(null);
                }
                else
                {
                    getNextUpgrader().configuredObject(record.getId(), record.getType(), record.getAttributes());
                }
            }
            getNextUpgrader().complete();
        }

        private boolean unknownExchange(final String exchangeIdString)
        {
            UUID exchangeId = UUID.fromString(exchangeIdString);
            if (_defaultExchangeIds.containsValue(exchangeId))
            {
                return false;
            }
            ConfiguredObjectRecord localRecord = getUpdateMap().get(exchangeId);
            return !((localRecord != null && localRecord.getType().equals(Exchange.class.getSimpleName()))
                     || _exchangeRegistry.getExchange(exchangeId) != null);
        }

        private boolean unknownQueue(final String queueIdString)
        {
            UUID queueId = UUID.fromString(queueIdString);
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
            public void configuredObject(UUID id, String type, Map<String, Object> attributes)
            {
                if(Queue.class.getSimpleName().equals(type))
                {
                    Map<String, Object> newAttributes = new LinkedHashMap<String, Object>();
                    if(attributes.get(ARGUMENTS) instanceof Map)
                    {
                        newAttributes.putAll(QueueArgumentsConverter.convertWireArgsToModel((Map<String, Object>) attributes
                                .get(ARGUMENTS)));
                    }
                    newAttributes.putAll(attributes);
                    attributes = newAttributes;
                    getUpdateMap().put(id, new ConfiguredObjectRecord(id,type,attributes));
                }

                getNextUpgrader().configuredObject(id,type,attributes);
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
        public void configuredObject(UUID id, String type, Map<String, Object> attributes)
        {
            if(Queue.class.getSimpleName().equals(type))
            {
                Map<String, Object> newAttributes = new LinkedHashMap<String, Object>(attributes);
                if(attributes.get(EXCLUSIVE) instanceof Boolean)
                {
                    boolean isExclusive = (Boolean) attributes.get(EXCLUSIVE);
                    newAttributes.put(EXCLUSIVE, isExclusive ? "CONTAINER" : "NONE");
                    if(!isExclusive && attributes.containsKey("owner"))
                    {
                        newAttributes.remove("owner");
                    }
                }
                else
                {
                    newAttributes.remove("owner");
                }
                if(!attributes.containsKey("durable"))
                {
                    newAttributes.put("durable","true");
                }
                attributes = newAttributes;
                getUpdateMap().put(id, new ConfiguredObjectRecord(id,type,attributes));
            }

            getNextUpgrader().configuredObject(id,type,attributes);
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
        public void configuredObject(UUID id, String type, Map<String, Object> attributes)
        {
            if(Exchange.class.getSimpleName().equals(type))
            {
                String name = (String)attributes.get(NAME);
                _missingAmqpExchanges.remove(name);
            }

            getNextUpgrader().configuredObject(id,type,attributes);
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

                getUpdateMap().put(id, new ConfiguredObjectRecord(id, Exchange.class.getSimpleName(), attributes));

                getNextUpgrader().configuredObject(id, Exchange.class.getSimpleName(), attributes);

            }

            getNextUpgrader().complete();
        }
    }

}
