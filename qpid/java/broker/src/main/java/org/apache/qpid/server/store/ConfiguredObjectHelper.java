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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.BindingRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.ExchangeRecoveryHandler;
import org.apache.qpid.server.store.ConfigurationRecoveryHandler.QueueRecoveryHandler;
import org.apache.qpid.server.util.MapJsonSerializer;

public class ConfiguredObjectHelper
{
    /**
     * Name of queue attribute to store queue creation arguments.
     * <p>
     * This attribute is not defined yet on Queue configured object interface.
     */
    private static final String QUEUE_ARGUMENTS = "ARGUMENTS";

    private MapJsonSerializer _serializer = new MapJsonSerializer();

    public void loadQueue(ConfiguredObjectRecord configuredObject, QueueRecoveryHandler qrh)
    {
        if (Queue.class.getName().equals(configuredObject.getType()))
        {
            Map<String, Object> attributeMap = _serializer.deserialize(configuredObject.getAttributes());
            String queueName = (String) attributeMap.get(Queue.NAME);
            String owner = (String) attributeMap.get(Queue.OWNER);
            boolean exclusive = (Boolean) attributeMap.get(Queue.EXCLUSIVE);
            @SuppressWarnings("unchecked")
            Map<String, Object> queueArgumentsMap = (Map<String, Object>) attributeMap.get(QUEUE_ARGUMENTS);
            FieldTable arguments = null;
            if (queueArgumentsMap != null)
            {
                arguments = FieldTable.convertToFieldTable(queueArgumentsMap);
            }
            qrh.queue(configuredObject.getId(), queueName, owner, exclusive, arguments);
        }
    }

    public ConfiguredObjectRecord updateQueueConfiguredObject(final AMQQueue queue, ConfiguredObjectRecord queueRecord)
    {
        Map<String, Object> attributesMap = _serializer.deserialize(queueRecord.getAttributes());
        attributesMap.put(Queue.NAME, queue.getName());
        attributesMap.put(Queue.EXCLUSIVE, queue.isExclusive());
        String newJson = _serializer.serialize(attributesMap);
        ConfiguredObjectRecord newQueueRecord = new ConfiguredObjectRecord(queue.getId(), queueRecord.getType(), newJson);
        return newQueueRecord;
    }

    public ConfiguredObjectRecord createQueueConfiguredObject(AMQQueue queue, FieldTable arguments)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Queue.NAME, queue.getName());
        attributesMap.put(Queue.OWNER, AMQShortString.toString(queue.getOwner()));
        attributesMap.put(Queue.EXCLUSIVE, queue.isExclusive());
        if (arguments != null)
        {
            attributesMap.put(QUEUE_ARGUMENTS, FieldTable.convertToMap(arguments));
        }
        String json = _serializer.serialize(attributesMap);
        ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecord(queue.getId(), Queue.class.getName(), json);
        return configuredObject;
    }

    public void loadExchange(ConfiguredObjectRecord configuredObject, ExchangeRecoveryHandler erh)
    {
        if (Exchange.class.getName().equals(configuredObject.getType()))
        {
            Map<String, Object> attributeMap = _serializer.deserialize(configuredObject.getAttributes());
            String exchangeName = (String) attributeMap.get(Exchange.NAME);
            String exchangeType = (String) attributeMap.get(Exchange.TYPE);
            String lifeTimePolicy = (String) attributeMap.get(Exchange.LIFETIME_POLICY);
            boolean autoDelete = lifeTimePolicy == null
                    || LifetimePolicy.valueOf(lifeTimePolicy) == LifetimePolicy.AUTO_DELETE;
            erh.exchange(configuredObject.getId(), exchangeName, exchangeType, autoDelete);
        }
    }

    public ConfiguredObjectRecord createExchangeConfiguredObject(org.apache.qpid.server.exchange.Exchange exchange)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Exchange.NAME, exchange.getName());
        attributesMap.put(Exchange.TYPE, AMQShortString.toString(exchange.getTypeShortString()));
        attributesMap.put(Exchange.LIFETIME_POLICY, exchange.isAutoDelete() ? LifetimePolicy.AUTO_DELETE.name()
                : LifetimePolicy.PERMANENT.name());
        String json = _serializer.serialize(attributesMap);
        ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecord(exchange.getId(), Exchange.class.getName(), json);
        return configuredObject;
    }

    public void loadQueueBinding(ConfiguredObjectRecord configuredObject, BindingRecoveryHandler brh)
    {
        if (Binding.class.getName().equals(configuredObject.getType()))
        {
            Map<String, Object> attributeMap = _serializer.deserialize(configuredObject.getAttributes());
            UUID exchangeId = UUID.fromString((String)attributeMap.get(Binding.EXCHANGE));
            UUID queueId = UUID.fromString((String) attributeMap.get(Binding.QUEUE));
            String bindingName = (String) attributeMap.get(Binding.NAME);

            @SuppressWarnings("unchecked")
            Map<String, Object> bindingArgumentsMap = (Map<String, Object>) attributeMap.get(Binding.ARGUMENTS);
            FieldTable arguments = null;
            if (bindingArgumentsMap != null)
            {
                arguments = FieldTable.convertToFieldTable(bindingArgumentsMap);
            }
            ByteBuffer argumentsBB = (arguments == null ? null : ByteBuffer.wrap(arguments.getDataAsBytes()));

            brh.binding(configuredObject.getId(), exchangeId, queueId, bindingName, argumentsBB);
        }
    }

    public ConfiguredObjectRecord createBindingConfiguredObject(org.apache.qpid.server.binding.Binding binding)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Binding.NAME, binding.getBindingKey());
        attributesMap.put(Binding.EXCHANGE, binding.getExchange().getId());
        attributesMap.put(Binding.QUEUE, binding.getQueue().getId());
        Map<String, Object> arguments = binding.getArguments();
        if (arguments != null)
        {
            attributesMap.put(Binding.ARGUMENTS, arguments);
        }
        String json = _serializer.serialize(attributesMap);
        ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecord(binding.getId(), Binding.class.getName(), json);
        return configuredObject;
    }

    public void recoverQueues(QueueRecoveryHandler qrh, List<ConfiguredObjectRecord> configuredObjects)
    {
        for (ConfiguredObjectRecord configuredObjectRecord : configuredObjects)
        {
            loadQueue(configuredObjectRecord, qrh);
        }
    }

    public void recoverExchanges(ExchangeRecoveryHandler erh, List<ConfiguredObjectRecord> configuredObjects)
    {
        for (ConfiguredObjectRecord configuredObjectRecord : configuredObjects)
        {
            loadExchange(configuredObjectRecord, erh);
        }
    }

    public void recoverBindings(BindingRecoveryHandler brh, List<ConfiguredObjectRecord> configuredObjects)
    {
        for (ConfiguredObjectRecord configuredObjectRecord : configuredObjects)
        {
            loadQueueBinding(configuredObjectRecord, brh);
        }
    }
}
