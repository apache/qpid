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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultQueueRegistry implements QueueRegistry
{
    private ConcurrentMap<AMQShortString, AMQQueue> _queueMap = new ConcurrentHashMap<AMQShortString, AMQQueue>();

    private final VirtualHost _virtualHost;
    private final Collection<RegistryChangeListener> _listeners =
            new ArrayList<RegistryChangeListener>();

    public DefaultQueueRegistry(VirtualHost virtualHost)
    {
        _virtualHost = virtualHost;
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public void registerQueue(AMQQueue queue)
    {
        _queueMap.put(queue.getNameShortString(), queue);
        synchronized (_listeners)
        {
            for(RegistryChangeListener listener : _listeners)
            {
                listener.queueRegistered(queue);
            }
        }
    }

    public void unregisterQueue(AMQShortString name)
    {
        AMQQueue q = _queueMap.remove(name);
        if(q != null)
        {
            synchronized (_listeners)
            {
                for(RegistryChangeListener listener : _listeners)
                {
                    listener.queueUnregistered(q);
                }
            }
        }
    }

    public AMQQueue getQueue(AMQShortString name)
    {
        return _queueMap.get(name);
    }

    public Collection<AMQShortString> getQueueNames()
    {
        return _queueMap.keySet();
    }

    public Collection<AMQQueue> getQueues()
    {
        return _queueMap.values();
    }

    public AMQQueue getQueue(String queue)
    {
        return getQueue(new AMQShortString(queue));
    }

    public void addRegistryChangeListener(RegistryChangeListener listener)
    {
        synchronized(_listeners)
        {
            _listeners.add(listener);
        }
    }
}
