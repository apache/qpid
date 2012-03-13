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
package org.apache.qpid.server.model.impl;

import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

abstract class AbstractConfiguredObject implements ConfiguredObject
{
    public static final Map<String, Object> EMPTY_ATTRIBUTE_MAP =
            Collections.<String, Object>emptyMap();
    public static final Map<Class<? extends ConfiguredObject>, ConfiguredObject> EMPTY_PARENT_MAP =
            Collections.<Class<? extends ConfiguredObject>, ConfiguredObject>emptyMap();

    private UUID _id;
    private String _name;
    private State _state;
    private boolean _isDurable;
    private LifetimePolicy _lifetimePolicy;
    private long _timeToLive;


    private final Map<String,Object> _attributes = new HashMap<String, Object>();
    private final Map<Class<? extends ConfiguredObject>, ConfiguredObject> _parents =
            new HashMap<Class<? extends ConfiguredObject>, ConfiguredObject>();

    private final Collection<ConfigurationChangeListener> _changeListeners =
            new ArrayList<ConfigurationChangeListener>();

    protected AbstractConfiguredObject(final UUID id,
                                       final String name,
                                       final State state,
                                       final boolean durable,
                                       final LifetimePolicy lifetimePolicy,
                                       final long timeToLive,
                                       final Map<String, Object> attributes,
                                       final Map<Class<? extends ConfiguredObject>, ConfiguredObject> parents)
    {
        _id = id;
        _name = name;
        _state = state;
        _isDurable = durable;
        _lifetimePolicy = lifetimePolicy;
        _timeToLive = timeToLive;
        _attributes.putAll(attributes);
        _parents.putAll(parents);
    }

    public UUID getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        if(desiredName == null)
        {
            throw new NullPointerException("The name may not be null");
        }

        synchronized (getLock())
        {
            if(_name.equals(currentName))
            {
                _name = desiredName;
            }
            return _name;
        }
    }

    public State getDesiredState()
    {
        return _state;
    }

    public State setDesiredState(final State currentState, final State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        synchronized (getLock())
        {
            if(_state == currentState && currentState != desiredState)
            {
                _state = desiredState;
                for(ConfigurationChangeListener listener : _changeListeners)
                {
                    listener.stateChanged(this, currentState, desiredState);
                }
            }
            return _state;
        }
    }

    public void addChangeListener(final ConfigurationChangeListener listener)
    {
        if(listener == null)
        {
            throw new NullPointerException("Cannot add a null listener");
        }
        synchronized (getLock())
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
        synchronized (getLock())
        {
            return _changeListeners.remove(listener);
        }
    }

    public boolean isDurable()
    {
        return _isDurable;
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        synchronized (getLock())
        {
            _isDurable = durable;
        }
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return _lifetimePolicy;
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        synchronized(getLock())
        {
            if((_lifetimePolicy == null && expected == null)
                || (_lifetimePolicy != null && _lifetimePolicy.equals(expected)))
            {
                _lifetimePolicy = desired;
            }

            return _lifetimePolicy;

        }
    }

    public long getTimeToLive()
    {
        return _timeToLive;
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        synchronized(getLock())
        {
            if(_timeToLive == expected)
            {
                _timeToLive = desired;
            }
            return _timeToLive;
        }
    }

    public Collection<String> getAttributeNames()
    {
        synchronized(_attributes)
        {
            return new ArrayList<String>(_attributes.keySet());
        }
    }

    public Object getAttribute(final String name)
    {
        synchronized (getLock())
        {
            return _attributes.get(name);
        }
    }

    public Object setAttribute(final String name, final Object expected, final Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        synchronized (getLock())
        {
            Object currentValue = _attributes.get(name);
            if ((currentValue == null && expected == null && desired != null)
                || (currentValue != null && currentValue.equals(expected) && !currentValue.equals(desired)))
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
        synchronized (getLock())
        {
            return (T) _parents.get(clazz);
        }
    }

    protected <T extends ConfiguredObject> void addParent(Class<T> clazz, T parent)
    {
        synchronized (getLock())
        {
            _parents.put(clazz, parent);
        }
    }

    protected  <T extends ConfiguredObject> void removeParent(Class<T> clazz)
    {
        synchronized (getLock())
        {
            _parents.remove(clazz);
        }
    }

    protected void notifyChildAddedListener(ConfiguredObject child)
    {
        for (ConfigurationChangeListener listener : _changeListeners)
        {
            listener.childAdded(this, child);
        }
    }

    protected void notifyChildRemovedListener(ConfiguredObject child)
    {
        synchronized (getLock())
        {
            for (ConfigurationChangeListener listener : _changeListeners)
            {
                listener.childRemoved(this, child);
            }
        }
    }

    abstract protected Object getLock();
}
