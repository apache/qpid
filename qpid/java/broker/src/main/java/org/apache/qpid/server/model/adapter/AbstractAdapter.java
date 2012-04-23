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
package org.apache.qpid.server.model.adapter;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.State;

abstract class AbstractAdapter implements ConfiguredObject
{
    private final Map<String,Object> _attributes = new HashMap<String, Object>();
    private final Map<Class<? extends ConfiguredObject>, ConfiguredObject> _parents =
            new HashMap<Class<? extends ConfiguredObject>, ConfiguredObject>();
    private final Collection<ConfigurationChangeListener> _changeListeners =
            new ArrayList<ConfigurationChangeListener>();

    private final UUID _id;

    protected AbstractAdapter(String... names)
    {
        StringBuilder sb = new StringBuilder();
        for(String name : names)
        {
            sb.append('/').append(name);
        }
        _id = UUID.nameUUIDFromBytes(sb.toString().getBytes());
    }

    protected AbstractAdapter()
    {
        _id = UUID.randomUUID();
    }

    static String getStringAttribute(String name, Map<String,Object> attributes, String defaultVal)
    {
        final Object value = attributes.get(name);
        return value == null ? defaultVal : String.valueOf(value);
    }

    static Map getMapAttribute(String name, Map<String,Object> attributes, Map defaultVal)
    {
        final Object value = attributes.get(name);
        if(value == null)
        {
            return defaultVal;
        }
        else if(value instanceof Map)
        {
            return (Map) value;
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Map");
        }
    }


    static <E extends Enum> E getEnumAttribute(Class<E> clazz, String name, Map<String,Object> attributes, E defaultVal)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultVal;
        }
        else if(clazz.isInstance(obj))
        {
            return (E) obj;
        }
        else if(obj instanceof String)
        {
            return (E) Enum.valueOf(clazz, (String)obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type " + clazz.getSimpleName());
        }
    }

    static Boolean getBooleanAttribute(String name, Map<String,Object> attributes, Boolean defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Boolean)
        {
            return (Boolean) obj;
        }
        else if(obj instanceof String)
        {
            return Boolean.parseBoolean((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Boolean");
        }
    }

    static Integer getIntegerAttribute(String name, Map<String,Object> attributes, Integer defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Number)
        {
            return ((Number) obj).intValue();
        }
        else if(obj instanceof String)
        {
            return Integer.valueOf((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Integer");
        }
    }

    static Long getLongAttribute(String name, Map<String,Object> attributes, Long defaultValue)
    {
        Object obj = attributes.get(name);
        if(obj == null)
        {
            return defaultValue;
        }
        else if(obj instanceof Number)
        {
            return ((Number) obj).longValue();
        }
        else if(obj instanceof String)
        {
            return Long.valueOf((String) obj);
        }
        else
        {
            throw new IllegalArgumentException("Value for attribute " + name + " is not of required type Long");
        }
    }

    public final UUID getId()
    {
        return _id;
    }

    public State getDesiredState()
    {
        return null;  //TODO
    }

    public State setDesiredState(final State currentState, final State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        return null;  //TODO
    }

    public void addChangeListener(final ConfigurationChangeListener listener)
    {
        if(listener == null)
        {
            throw new NullPointerException("Cannot add a null listener");
        }
        synchronized (this)
        {
            if(!_changeListeners.contains(listener))
            {
                _changeListeners.add(listener);
            }
        }
    }

    public boolean removeChangeListener(final ConfigurationChangeListener listener)
    {
        if(listener == null)
        {
            throw new NullPointerException("Cannot remove a null listener");
        }
        synchronized (this)
        {
            return _changeListeners.remove(listener);
        }
    }


    protected void childAdded(ConfiguredObject child)
    {
        synchronized (this)
        {
            for(ConfigurationChangeListener listener : _changeListeners)
            {
                listener.childAdded(this, child);
            }
        }
    }


    protected void childRemoved(ConfiguredObject child)
    {
        synchronized (this)
        {
            for(ConfigurationChangeListener listener : _changeListeners)
            {
                listener.childRemoved(this, child);
            }
        }
    }

    public Object getAttribute(final String name)
    {
        synchronized (this)
        {
            return _attributes.get(name);
        }
    }

    public Object setAttribute(final String name, final Object expected, final Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        synchronized (this)
        {
            Object currentValue = _attributes.get(name);
            if((currentValue == null && expected == null)
               || (currentValue != null && currentValue.equals(expected)))
            {
                _attributes.put(name, desired);
                return desired;
            }
            else
            {
                return currentValue;
            }
        }
    }

    public <T extends ConfiguredObject> T getParent(final Class<T> clazz)
    {
        synchronized (this)
        {
            return (T) _parents.get(clazz);
        }
    }

    protected <T extends ConfiguredObject> void addParent(Class<T> clazz, T parent)
    {
        synchronized (this)
        {
            _parents.put(clazz, parent);
        }
    }

    protected  <T extends ConfiguredObject> void removeParent(Class<T> clazz)
    {
        synchronized (this)
        {
            _parents.remove(clazz);
        }
    }

    public Collection<String> getAttributeNames()
    {
        synchronized(_attributes)
        {
            return new ArrayList<String>(_attributes.keySet());
        }
    }

}
