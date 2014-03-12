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

import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.Set;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.*;

import javax.security.auth.Subject;

public class DurableConfigurationStoreHelper
{

    private static final String BINDING = Binding.class.getSimpleName();
    private static final String EXCHANGE = Exchange.class.getSimpleName();
    private static final String QUEUE = Queue.class.getSimpleName();
    private static final Set<String> QUEUE_ARGUMENTS_EXCLUDES = new HashSet<String>(Arrays.asList(Queue.ALTERNATE_EXCHANGE));

    public static void updateQueue(DurableConfigurationStore store, AMQQueue queue)
    {
        Map<String, Object> attributesMap = queue.getActualAttributes();
        attributesMap.remove(ConfiguredObject.ID);

        store.update(queue.getId(), QUEUE, attributesMap);
    }

    public static void createQueue(DurableConfigurationStore store, AMQQueue<?> queue)
    {

        Map<String, Object> attributesMap = queue.getActualAttributes();
        attributesMap.remove(ConfiguredObject.ID);

        store.create(queue.getId(), QUEUE, attributesMap);
    }

    public static void removeQueue(DurableConfigurationStore store, AMQQueue queue)
    {
        store.remove(queue.getId(), QUEUE);
    }

    public static void createExchange(DurableConfigurationStore store, ExchangeImpl exchange)
    {
        Map<String, Object> attributesMap = exchange.getActualAttributes();
        attributesMap.remove(ConfiguredObject.ID);

        store.create(exchange.getId(), EXCHANGE, attributesMap);

    }


    public static void removeExchange(DurableConfigurationStore store, ExchangeImpl exchange)
    {
        store.remove(exchange.getId(), EXCHANGE);
    }

    public static void createBinding(DurableConfigurationStore store, final BindingImpl binding)
    {
        Map<String, Object> attributesMap = binding.getActualAttributes();
        attributesMap.remove(ConfiguredObject.ID);
        if(!attributesMap.containsKey(Binding.EXCHANGE))
        {
            attributesMap.put(Binding.EXCHANGE, binding.getExchange());
        }
        if(!attributesMap.containsKey(Binding.QUEUE))
        {
            attributesMap.put(Binding.QUEUE, binding.getQueue());
        }

        store.create(binding.getId(), BINDING, attributesMap);
    }


    public static void removeBinding(DurableConfigurationStore store, BindingImpl binding)
    {
        store.remove(binding.getId(), BINDING);
    }

}
