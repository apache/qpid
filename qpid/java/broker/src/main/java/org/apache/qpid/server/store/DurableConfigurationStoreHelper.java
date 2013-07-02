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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;

public class DurableConfigurationStoreHelper
{

    public static void updateQueue(DurableConfigurationStore store, AMQQueue queue) throws AMQStoreException
    {
        Map<String, Object> attributesMap = new LinkedHashMap<String, Object>();
        attributesMap.put(Queue.NAME, queue.getName());
        attributesMap.put(Queue.OWNER, AMQShortString.toString(queue.getOwner()));
        attributesMap.put(Queue.EXCLUSIVE, queue.isExclusive());
        if (queue.getAlternateExchange() != null)
        {
            attributesMap.put(Queue.ALTERNATE_EXCHANGE, queue.getAlternateExchange().getId());
        }
        else
        {
            attributesMap.remove(Queue.ALTERNATE_EXCHANGE);
        }
        if (attributesMap.containsKey(Queue.ARGUMENTS))
        {
            // We wouldn't need this if createQueueConfiguredObject took only AMQQueue
            Map<String, Object> currentArgs = (Map<String, Object>) attributesMap.get(Queue.ARGUMENTS);
            currentArgs.putAll(queue.getArguments());
        }
        else
        {
            attributesMap.put(Queue.ARGUMENTS, queue.getArguments());
        }
        store.update(queue.getId(), Queue.class.getName(), attributesMap);
    }

    public static void createQueue(DurableConfigurationStore store, AMQQueue queue, FieldTable arguments)
            throws AMQStoreException
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Queue.NAME, queue.getName());
        attributesMap.put(Queue.OWNER, AMQShortString.toString(queue.getOwner()));
        attributesMap.put(Queue.EXCLUSIVE, queue.isExclusive());
        if (queue.getAlternateExchange() != null)
        {
            attributesMap.put(Queue.ALTERNATE_EXCHANGE, queue.getAlternateExchange().getId());
        }
        // TODO KW i think the arguments could come from the queue itself removing the need for the parameter arguments.
        // It would also do away with the need for the if/then/else within updateQueueConfiguredObject
        if (arguments != null)
        {
            attributesMap.put(Queue.ARGUMENTS, FieldTable.convertToMap(arguments));
        }
        store.create(queue.getId(),Queue.class.getName(),attributesMap);
    }

    public static void removeQueue(DurableConfigurationStore store, AMQQueue queue) throws AMQStoreException
    {
        store.remove(queue.getId(), Queue.class.getName());
    }

    public static void createExchange(DurableConfigurationStore store, org.apache.qpid.server.exchange.Exchange exchange)
            throws AMQStoreException
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Exchange.NAME, exchange.getName());
        attributesMap.put(Exchange.TYPE, AMQShortString.toString(exchange.getTypeShortString()));
        attributesMap.put(Exchange.LIFETIME_POLICY, exchange.isAutoDelete() ? LifetimePolicy.AUTO_DELETE.name()
                : LifetimePolicy.PERMANENT.name());
        store.create(exchange.getId(), Exchange.class.getName(), attributesMap);

    }


    public static void removeExchange(DurableConfigurationStore store, org.apache.qpid.server.exchange.Exchange exchange)
            throws AMQStoreException
    {
        store.remove(exchange.getId(),Exchange.class.getName());
    }

    public static void createBinding(DurableConfigurationStore store, org.apache.qpid.server.binding.Binding binding)
                throws AMQStoreException
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
        store.create(binding.getId(), Binding.class.getName(), attributesMap);
    }


    public static void removeBinding(DurableConfigurationStore store, org.apache.qpid.server.binding.Binding binding)
                throws AMQStoreException
    {
        store.remove(binding.getId(), Binding.class.getName());
    }

}
