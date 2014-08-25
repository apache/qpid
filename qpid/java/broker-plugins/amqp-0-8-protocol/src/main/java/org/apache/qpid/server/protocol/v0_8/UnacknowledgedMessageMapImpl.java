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
package org.apache.qpid.server.protocol.v0_8;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.message.MessageInstance;

public class UnacknowledgedMessageMapImpl implements UnacknowledgedMessageMap
{
    private final Object _lock = new Object();

    private Map<Long, MessageInstance> _map;

    private final int _prefetchLimit;

    public UnacknowledgedMessageMapImpl(int prefetchLimit)
    {
        _prefetchLimit = prefetchLimit;
        _map = new LinkedHashMap<>(prefetchLimit);
    }

    public void collect(long deliveryTag, boolean multiple, Map<Long, MessageInstance> msgs)
    {
        if (multiple)
        {
            collect(deliveryTag, msgs);
        }
        else
        {
            final MessageInstance entry = get(deliveryTag);
            if(entry != null)
            {
                msgs.put(deliveryTag, entry);
            }
        }

    }

    public void remove(Map<Long,MessageInstance> msgs)
    {
        synchronized (_lock)
        {
            for (Long deliveryTag : msgs.keySet())
            {
                remove(deliveryTag);
            }
        }
    }

    public MessageInstance remove(long deliveryTag)
    {
        synchronized (_lock)
        {

            MessageInstance message = _map.remove(deliveryTag);
            return message;
        }
    }

    public void visit(Visitor visitor) throws AMQException
    {
        synchronized (_lock)
        {
            Set<Map.Entry<Long, MessageInstance>> currentEntries = _map.entrySet();
            for (Map.Entry<Long, MessageInstance> entry : currentEntries)
            {
                visitor.callback(entry.getKey().longValue(), entry.getValue());
            }
            visitor.visitComplete();
        }
    }

    public void add(long deliveryTag, MessageInstance message)
    {
        synchronized (_lock)
        {
            _map.put(deliveryTag, message);
        }
    }

    public Collection<MessageInstance> cancelAllMessages()
    {
        synchronized (_lock)
        {
            Collection<MessageInstance> currentEntries = _map.values();
            _map = new LinkedHashMap<>(_prefetchLimit);
            return currentEntries;
        }
    }

    public int size()
    {
        synchronized (_lock)
        {
            return _map.size();
        }
    }

    public void clear()
    {
        synchronized (_lock)
        {
            _map.clear();
        }
    }

    public MessageInstance get(long key)
    {
        synchronized (_lock)
        {
            return _map.get(key);
        }
    }

    public Set<Long> getDeliveryTags()
    {
        synchronized (_lock)
        {
            return _map.keySet();
        }
    }

    public Collection<MessageInstance> acknowledge(long deliveryTag, boolean multiple)
    {
        Map<Long, MessageInstance> ackedMessageMap = new LinkedHashMap<Long,MessageInstance>();
        collect(deliveryTag, multiple, ackedMessageMap);
        remove(ackedMessageMap);
        List<MessageInstance> acknowledged = new ArrayList<>();
        for(MessageInstance instance : ackedMessageMap.values())
        {
            if(instance.lockAcquisition())
            {
                acknowledged.add(instance);
            }
        }
        return acknowledged;
    }

    private void collect(long key, Map<Long, MessageInstance> msgs)
    {
        synchronized (_lock)
        {
            for (Map.Entry<Long, MessageInstance> entry : _map.entrySet())
            {
                msgs.put(entry.getKey(),entry.getValue());
                if (entry.getKey() == key)
                {
                    break;
                }
            }
        }
    }

}
