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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.FilterSupport;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.DurableConfigurationRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStoreUpgrader;
import org.apache.qpid.server.store.NonNullUpgrader;
import org.apache.qpid.server.store.NullUpgrader;
import org.apache.qpid.server.store.UpgraderProvider;

import static org.apache.qpid.server.model.VirtualHost.CURRENT_CONFIG_VERSION;

public class DefaultUpgraderProvider implements UpgraderProvider
{
    private final ExchangeRegistry _exchangeRegistry;
    private final VirtualHost _virtualHost;

    public DefaultUpgraderProvider(final VirtualHost virtualHost,
                                   final ExchangeRegistry exchangeRegistry)
    {
        _virtualHost = virtualHost;
        _exchangeRegistry = exchangeRegistry;
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
                return _exchangeRegistry.getExchange(exchangeId) != null
                       && _exchangeRegistry.getExchange(exchangeId).getType() == TopicExchange.TYPE;
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
     * configuration store).  Also remove bindings which reference non-existant queues or exchanges.
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
            ConfiguredObjectRecord localRecord = getUpdateMap().get(exchangeId);
            return !((localRecord != null && localRecord.getType().equals(Exchange.class.getSimpleName()))
                     || _exchangeRegistry.getExchange(exchangeId) != null);
        }

        private boolean unknownQueue(final String queueIdString)
        {
            UUID queueId = UUID.fromString(queueIdString);
            ConfiguredObjectRecord localRecord = getUpdateMap().get(queueId);
            return !((localRecord != null  && localRecord.getType().equals(Queue.class.getSimpleName()))
                     || _virtualHost.getQueueRegistry().getQueue(queueId) != null);
        }

        private boolean isBinding(final String type)
        {
            return Binding.class.getSimpleName().equals(type);
        }


    }

}
