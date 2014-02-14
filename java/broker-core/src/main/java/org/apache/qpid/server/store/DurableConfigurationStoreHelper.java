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
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.Set;

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class DurableConfigurationStoreHelper
{

    private static final String BINDING = Binding.class.getSimpleName();
    private static final String EXCHANGE = Exchange.class.getSimpleName();
    private static final String QUEUE = Queue.class.getSimpleName();
    private static final Set<String> QUEUE_ARGUMENTS_EXCLUDES = new HashSet<String>(Arrays.asList(Queue.NAME,
                                                                                                  Queue.OWNER,
                                                                                                  Queue.EXCLUSIVE,
                                                                                                  Queue.ALTERNATE_EXCHANGE));

    public static void updateQueue(DurableConfigurationStore store, AMQQueue<?,?,?> queue)
    {
        Map<String, Object> attributesMap = new LinkedHashMap<String, Object>();
        attributesMap.put(Queue.NAME, queue.getName());
        attributesMap.put(Queue.OWNER, queue.getOwner());
        attributesMap.put(Queue.EXCLUSIVE, queue.isExclusive());

        if (queue.getAlternateExchange() != null)
        {
            attributesMap.put(Queue.ALTERNATE_EXCHANGE, queue.getAlternateExchange().getId());
        }

        Collection<String> availableAttrs = queue.getAvailableAttributes();

        for(String attrName : availableAttrs)
        {
            if(!QUEUE_ARGUMENTS_EXCLUDES.contains(attrName))
            {
                attributesMap.put(attrName, queue.getAttribute(attrName));
            }
        }

        try
        {
            store.update(queue.getId(), QUEUE, attributesMap);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }
    }

    public static void createQueue(DurableConfigurationStore store, AMQQueue<?,?,?> queue)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Queue.NAME, queue.getName());
        attributesMap.put(Queue.OWNER, queue.getOwner());
        attributesMap.put(Queue.EXCLUSIVE, queue.isExclusive());
        if (queue.getAlternateExchange() != null)
        {
            attributesMap.put(Queue.ALTERNATE_EXCHANGE, queue.getAlternateExchange().getId());
        }

        for(String attrName : queue.getAvailableAttributes())
        {
            if(!QUEUE_ARGUMENTS_EXCLUDES.contains(attrName))
            {
                attributesMap.put(attrName, queue.getAttribute(attrName));
            }
        }
        try
        {
            store.create(queue.getId(), QUEUE, attributesMap);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }
    }

    public static void removeQueue(DurableConfigurationStore store, AMQQueue queue)
    {
        try
        {
            store.remove(queue.getId(), QUEUE);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }
    }

    public static void createExchange(DurableConfigurationStore store, org.apache.qpid.server.exchange.Exchange exchange)
    {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(Exchange.NAME, exchange.getName());
        attributesMap.put(Exchange.TYPE, exchange.getTypeName());
        attributesMap.put(Exchange.LIFETIME_POLICY, exchange.isAutoDelete() ? LifetimePolicy.AUTO_DELETE.name()
                : LifetimePolicy.PERMANENT.name());
        try
        {
            store.create(exchange.getId(), EXCHANGE, attributesMap);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }

    }


    public static void removeExchange(DurableConfigurationStore store, org.apache.qpid.server.exchange.Exchange exchange)
    {
        try
        {
            store.remove(exchange.getId(), EXCHANGE);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }
    }

    public static void createBinding(DurableConfigurationStore store, org.apache.qpid.server.binding.Binding binding)
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
        try
        {
            store.create(binding.getId(), BINDING, attributesMap);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }
    }


    public static void removeBinding(DurableConfigurationStore store, org.apache.qpid.server.binding.Binding binding)
    {
        try
        {
            store.remove(binding.getId(), BINDING);
        }
        catch (AMQStoreException e)
        {
            throw new ServerScopedRuntimeException("A problem was encountered with the configuration store ", e);
        }
    }

}
