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
package org.apache.qpid.server.queue;

import java.util.Map;

import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;

@PluggableService
public class QueueFactory<X extends Queue<X>>  implements ConfiguredObjectTypeFactory<X>
{
    @Override
    public Class<? super X> getCategoryClass()
    {
        return Queue.class;
    }

    @Override
    public X create(final ConfiguredObjectFactory factory,
                    final Map<String, Object> attributes,
                    final ConfiguredObject<?>... parents)
    {
        return getQueueFactory(factory, attributes).create(factory, attributes, parents);
    }

    @Override
    public ListenableFuture<X> createAsync(final ConfiguredObjectFactory factory,
                                           final Map<String, Object> attributes,
                                           final ConfiguredObject<?>... parents)
    {
        return getQueueFactory(factory, attributes).createAsync(factory, attributes, parents);
    }

    @Override
    public UnresolvedConfiguredObject<X> recover(final ConfiguredObjectFactory factory,
                                                 final ConfiguredObjectRecord record,
                                                 final ConfiguredObject<?>... parents)
    {
        return getQueueFactory(factory, record.getAttributes()).recover(factory, record, parents);
    }

    private ConfiguredObjectTypeFactory<X> getQueueFactory(final ConfiguredObjectFactory factory,
                                                           Map<String, Object> attributes)
    {

        String type;

        if(attributes.containsKey(Port.TYPE))
        {
            type = (String) attributes.get(Port.TYPE);
        }
        else
        {
            if(attributes.containsKey(PriorityQueue.PRIORITIES))
            {
                type = "priority";
            }
            else if(attributes.containsKey(SortedQueue.SORT_KEY))
            {
                type = "sorted";
            }
            else if(attributes.containsKey(LastValueQueue.LVQ_KEY))
            {
                type = "lvq";
            }
            else
            {
                type = "standard";
            }
        }

        return factory.getConfiguredObjectTypeFactory(Queue.class.getSimpleName(), type);
    }

    @Override
    public String getType()
    {
        return null;
    }
}
