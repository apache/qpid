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

package org.apache.qpid.server.configuration;

import java.util.UUID;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigStore
{
    private ConcurrentHashMap<ConfigObjectType, ConcurrentHashMap<UUID, ConfiguredObject>> _typeMap =
            new ConcurrentHashMap<ConfigObjectType, ConcurrentHashMap<UUID, ConfiguredObject>>();

    private ConcurrentHashMap<ConfigObjectType, CopyOnWriteArrayList<ConfigEventListener>> _listenerMap =
            new ConcurrentHashMap<ConfigObjectType, CopyOnWriteArrayList<ConfigEventListener>>();

    private AtomicReference<SystemConfig> _root = new AtomicReference<SystemConfig>(null);

    private final AtomicLong _objectIdSource = new AtomicLong(0l);


    public enum Event
    {
        CREATED, DELETED
    }

    public interface ConfigEventListener<T extends ConfigObjectType<T,C>, C extends ConfiguredObject<T, C>>
    {
        void onEvent(C object, Event evt);
    }

    private ConfigStore()
    {
    }

    public <T extends ConfigObjectType<T, C>, C extends ConfiguredObject<T, C>> ConfiguredObject<T, C> getConfiguredObject(ConfigObjectType<T,C> type, UUID id)
    {
        ConcurrentHashMap<UUID, ConfiguredObject> typeMap = _typeMap.get(type);
        if(typeMap != null)
        {
            return typeMap.get(id);
        }
        else
        {
            return null;
        }

    }

    public <T extends ConfigObjectType<T, C>, C extends ConfiguredObject<T, C>> Collection<? extends C> getConfiguredObjects(ConfigObjectType<T,C> type)
    {
        ConcurrentHashMap typeMap = _typeMap.get(type);
        if(typeMap != null)
        {
            return typeMap.values();
        }
        else
        {
            return Collections.EMPTY_LIST;
        }

    }

    public <T extends ConfigObjectType<T, C>, C extends ConfiguredObject<T, C>> void addConfiguredObject(ConfiguredObject<T, C> object)
    {
        ConcurrentHashMap typeMap = _typeMap.get(object.getConfigType());
        if(typeMap == null)
        {
            typeMap = new ConcurrentHashMap();
            ConcurrentHashMap oldMap = _typeMap.putIfAbsent(object.getConfigType(), typeMap);
            if(oldMap != null)
            {
                typeMap = oldMap;
            }

        }

        typeMap.put(object.getId(), object);
        sendEvent(Event.CREATED, object);
    }


    public <T extends ConfigObjectType<T, C>, C extends ConfiguredObject<T, C>> void removeConfiguredObject(ConfiguredObject<T, C> object)
    {
        ConcurrentHashMap typeMap = _typeMap.get(object.getConfigType());
        if(typeMap != null)
        {
            typeMap.remove(object.getId());
            sendEvent(Event.DELETED, object);
        }
    }

    public <T extends ConfigObjectType<T, C>, C extends ConfiguredObject<T, C>> void addConfigEventListener(T type, ConfigEventListener<T,C> listener)
    {
        CopyOnWriteArrayList listeners = _listenerMap.get(type);
        if(listeners == null)
        {
            listeners = new CopyOnWriteArrayList();
            CopyOnWriteArrayList oldListeners = _listenerMap.putIfAbsent(type, listeners);
            if(oldListeners != null)
            {
                listeners = oldListeners;
            }

        }

        listeners.add(listener);

    }

    public <T extends ConfigObjectType<T, C>, C extends ConfiguredObject<T, C>> void removeConfigEventListener(T type, ConfigEventListener<T,C> listener)
    {
        CopyOnWriteArrayList listeners = _listenerMap.get(type);
        if(listeners != null)
        {
            listeners.remove(listener);
        }
    }

    private void sendEvent(Event e, ConfiguredObject o)
    {
        CopyOnWriteArrayList<ConfigEventListener> listeners = _listenerMap.get(o.getConfigType());
        if(listeners != null)
        {
            for(ConfigEventListener listener : listeners)
            {
                listener.onEvent(o, e);
            }
        }
    }

    public boolean setRoot(SystemConfig object)
    {
        if(_root.compareAndSet(null,object))
        {
            addConfiguredObject(object);
            return true;
        }
        else
        {
            return false;
        }
    }

    public UUID createId()
    {
        return new UUID(0l, _objectIdSource.getAndIncrement());
    }


    public SystemConfig getRoot()
    {
        return _root.get();
    }

    public static ConfigStore newInstance()
    {
        return new ConfigStore();
    }

}
