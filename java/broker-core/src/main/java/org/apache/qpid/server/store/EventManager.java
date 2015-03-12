/*
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManager
{
    private Map<Event, List<EventListener>> _listeners = new EnumMap<Event, List<EventListener>> (Event.class);
    private static final Logger _LOGGER = LoggerFactory.getLogger(EventManager.class);

    public synchronized void addEventListener(EventListener listener, Event... events)
    {
        for(Event event : events)
        {
            List<EventListener> list = _listeners.get(event);
            if(list == null)
            {
                list = new ArrayList<EventListener>();
                _listeners.put(event,list);
            }
            list.add(listener);
        }
    }

    public synchronized void notifyEvent(Event event)
    {
        if (_listeners.containsKey(event))
        {
            if(_LOGGER.isDebugEnabled())
            {
                _LOGGER.debug("Received event " + event);
            }

            for (EventListener listener : _listeners.get(event))
            {
                listener.event(event);
            }
        }
    }

    public synchronized boolean hasListeners(Event event)
    {
        return _listeners.containsKey(event);
    }
}
