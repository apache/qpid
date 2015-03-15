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
package org.apache.qpid.server.exchange.topic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.queue.AMQQueue;

public final class TopicExchangeResult implements TopicMatcherResult
{
    private final List<BindingImpl> _bindings = new CopyOnWriteArrayList<BindingImpl>();
    private final Map<AMQQueue, Integer> _unfilteredQueues = new ConcurrentHashMap<AMQQueue, Integer>();
    private final ConcurrentMap<AMQQueue, Map<FilterManager,Integer>> _filteredQueues = new ConcurrentHashMap<>();
    private volatile ArrayList<AMQQueue> _unfilteredQueueList = new ArrayList<AMQQueue>(0);

    public void addUnfilteredQueue(AMQQueue queue)
    {
        Integer instances = _unfilteredQueues.get(queue);
        if(instances == null)
        {
            _unfilteredQueues.put(queue, 1);
            ArrayList<AMQQueue> newList = new ArrayList<AMQQueue>(_unfilteredQueueList);
            newList.add(queue);
            _unfilteredQueueList = newList;
        }
        else
        {
            _unfilteredQueues.put(queue, instances + 1);
        }
    }

    public void removeUnfilteredQueue(AMQQueue queue)
    {
        Integer instances = _unfilteredQueues.get(queue);
        if(instances == 1)
        {
            _unfilteredQueues.remove(queue);
            ArrayList<AMQQueue> newList = new ArrayList<AMQQueue>(_unfilteredQueueList);
            newList.remove(queue);
            _unfilteredQueueList = newList;

        }
        else
        {
            _unfilteredQueues.put(queue,instances - 1);
        }

    }

    public Collection<AMQQueue> getUnfilteredQueues()
    {
        return _unfilteredQueues.keySet();
    }

    public void addBinding(BindingImpl binding)
    {
        _bindings.add(binding);
    }
    
    public void removeBinding(BindingImpl binding)
    {
        _bindings.remove(binding);
    }
    
    public List<BindingImpl> getBindings()
    {
        return new ArrayList<>(_bindings);
    }

    public void addFilteredQueue(AMQQueue queue, FilterManager filter)
    {
        Map<FilterManager,Integer> filters = _filteredQueues.get(queue);
        if(filters == null)
        {
            filters = new ConcurrentHashMap<>();
            _filteredQueues.put(queue, filters);
        }
        Integer instances = filters.get(filter);
        if(instances == null)
        {
            filters.put(filter,1);
        }
        else
        {
            filters.put(filter, instances + 1);
        }

    }

    public void removeFilteredQueue(AMQQueue queue, FilterManager filter)
    {
        Map<FilterManager,Integer> filters = _filteredQueues.get(queue);
        if(filters != null)
        {
            Integer instances = filters.get(filter);
            if(instances != null)
            {
                if(instances == 1)
                {
                    filters.remove(filter);
                    if(filters.isEmpty())
                    {
                        _filteredQueues.remove(queue);
                    }
                }
                else
                {
                    filters.put(filter, instances - 1);
                }
            }

        }

    }

    public void replaceQueueFilter(AMQQueue queue,
                                   FilterManager oldFilter,
                                   FilterManager newFilter)
    {
        Map<FilterManager,Integer> filters = _filteredQueues.get(queue);
        Map<FilterManager,Integer> newFilters = new ConcurrentHashMap<>(filters);
        Integer oldFilterInstances = filters.get(oldFilter);
        if(oldFilterInstances == 1)
        {
            newFilters.remove(oldFilter);
        }
        else
        {
            newFilters.put(oldFilter, oldFilterInstances-1);
        }
        Integer newFilterInstances = filters.get(newFilter);
        if(newFilterInstances == null)
        {
            newFilters.put(newFilter, 1);
        }
        else
        {
            newFilters.put(newFilter, newFilterInstances+1);
        }
        _filteredQueues.put(queue,newFilters);
    }

    public Collection<AMQQueue> processMessage(Filterable msg, Collection<AMQQueue> queues)
    {
        if(queues == null)
        {
            if(_filteredQueues.isEmpty())
            {
                return _unfilteredQueueList;
            }
            else
            {
                queues = new HashSet<AMQQueue>();
            }
        }
        else if(!(queues instanceof Set))
        {
            queues = new HashSet<AMQQueue>(queues);
        }

        queues.addAll(_unfilteredQueues.keySet());
        if(!_filteredQueues.isEmpty())
        {
            for(Map.Entry<AMQQueue, Map<FilterManager, Integer>> entry : _filteredQueues.entrySet())
            {
                if(!queues.contains(entry.getKey()))
                {
                    for(FilterManager filter : entry.getValue().keySet())
                    {
                        if(filter.allAllow(msg))
                        {
                            queues.add(entry.getKey());
                        }
                    }
                }
            }
        }
        return queues;
    }

}
