/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.ack;

import java.util.List;
import java.util.Map;

public class UnacknowledgedMessageMapImpl implements UnacknowledgedMessageMap
{
    private final Object _lock;
    private Map<Long, UnacknowledgedMessage> _map;

    public UnacknowledgedMessageMapImpl(Object lock, Map<Long, UnacknowledgedMessage> map)
    {
        _lock = lock;
        _map = map;
    }

    public void collect(long deliveryTag, boolean multiple, List<UnacknowledgedMessage> msgs)
    {
        if (multiple)
        {
            collect(deliveryTag, msgs);
        }
        else
        {
            msgs.add(get(deliveryTag));
        }

    }

    public void remove(List<UnacknowledgedMessage> msgs)
    {
        synchronized(_lock)
        {
            for(UnacknowledgedMessage msg : msgs)
            {
                _map.remove(msg.deliveryTag);
            }            
        }
    }

    private UnacknowledgedMessage get(long key)
    {
        synchronized(_lock)
        {
            return _map.get(key);
        }
    }

    private void collect(long key, List<UnacknowledgedMessage> msgs)
    {
        synchronized(_lock)
        {
            for(Map.Entry<Long, UnacknowledgedMessage> entry : _map.entrySet())
            {
                msgs.add(entry.getValue());
                if (entry.getKey() == key)
                {
                    break;
                }                        
            }
        }
    }
}

