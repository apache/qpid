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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Attribute;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.ChangeAttributesTask;
import org.apache.qpid.server.configuration.updater.ChangeStateTask;
import org.apache.qpid.server.configuration.updater.CreateChildTask;
import org.apache.qpid.server.configuration.updater.SetAttributeTask;
import org.apache.qpid.server.configuration.updater.TaskExecutor;

public abstract class AbstractConfiguredObject<X extends ConfiguredObject<X>> implements ConfiguredObject<X>
{
    private static final Object ID = "id";
    private final Map<String,Object> _attributes = new HashMap<String, Object>();
    private final Map<Class<? extends ConfiguredObject>, ConfiguredObject> _parents =
            new HashMap<Class<? extends ConfiguredObject>, ConfiguredObject>();
    private final Collection<ConfigurationChangeListener> _changeListeners =
            new ArrayList<ConfigurationChangeListener>();

    private final UUID _id;
    private final Map<String, Object> _defaultAttributes = new HashMap<String, Object>();
    private final TaskExecutor _taskExecutor;

    protected AbstractConfiguredObject(UUID id,
                                       Map<String, Object> defaults,
                                       Map<String, Object> attributes,
                                       TaskExecutor taskExecutor)
    {
        this(id, defaults, attributes, taskExecutor, true);
    }

    protected AbstractConfiguredObject(UUID id, Map<String, Object> defaults, Map<String, Object> attributes,
                                       TaskExecutor taskExecutor, boolean filterAttributes)

    {
        _taskExecutor = taskExecutor;
        _id = id;
        if (attributes != null)
        {
            Collection<String> names = getAttributeNames();
            if(filterAttributes)
            {
                for (String name : names)
                {
                    if (attributes.containsKey(name))
                    {
                        final Object value = attributes.get(name);
                        if(value != null)
                        {
                            _attributes.put(name, value);
                        }
                    }
                }
            }
            else
            {
                for(Map.Entry<String, Object> entry : attributes.entrySet())
                {
                    if(entry.getValue()!=null)
                    {
                        _attributes.put(entry.getKey(),entry.getValue());
                    }
                }
            }
        }
        if (defaults != null)
        {
            _defaultAttributes.putAll(defaults);
        }
    }

    protected AbstractConfiguredObject(UUID id, TaskExecutor taskExecutor)
    {
        this(id, null, null, taskExecutor);
    }

    public final UUID getId()
    {
        return _id;
    }

    public State getDesiredState()
    {
        return null;  //TODO
    }

    @Override
    public final State setDesiredState(final State currentState, final State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        if (_taskExecutor.isTaskExecutorThread())
        {
            authoriseSetDesiredState(currentState, desiredState);
            if (setState(currentState, desiredState))
            {
                notifyStateChanged(currentState, desiredState);
                return desiredState;
            }
            else
            {
                return getState();
            }
        }
        else
        {
            return _taskExecutor.submitAndWait(new ChangeStateTask(this, currentState, desiredState));
        }
    }

    /**
     * @return true when the state has been successfully updated to desiredState or false otherwise
     */
    protected abstract boolean setState(State currentState, State desiredState);

    protected void notifyStateChanged(final State currentState, final State desiredState)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.stateChanged(this, currentState, desiredState);
            }
        }
    }

    public void addChangeListener(final ConfigurationChangeListener listener)
    {
        if(listener == null)
        {
            throw new NullPointerException("Cannot add a null listener");
        }
        synchronized (_changeListeners)
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
        synchronized (_changeListeners)
        {
            return _changeListeners.remove(listener);
        }
    }

    protected void childAdded(ConfiguredObject child)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.childAdded(this, child);
            }
        }
    }

    protected void childRemoved(ConfiguredObject child)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.childRemoved(this, child);
            }
        }
    }

    protected void attributeSet(String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        synchronized (_changeListeners)
        {
            List<ConfigurationChangeListener> copy = new ArrayList<ConfigurationChangeListener>(_changeListeners);
            for(ConfigurationChangeListener listener : copy)
            {
                listener.attributeSet(this, attributeName, oldAttributeValue, newAttributeValue);
            }
        }
    }

    private final Object getDefaultAttribute(String name)
    {
        return _defaultAttributes.get(name);
    }

    @Override
    public Object getAttribute(String name)
    {
        Object value = getActualAttribute(name);
        if (value == null)
        {
            value = getDefaultAttribute(name);
        }
        return value;
    }

    @Override
    public String getDescription()
    {
        return (String) getAttribute(DESCRIPTION);
    }

    @Override
    public <T> T getAttribute(final Attribute<? super X, T> attr)
    {
        return (T) getAttribute(attr.getName());
    }

    @Override
    public final Map<String, Object> getActualAttributes()
    {
        synchronized (_attributes)
        {
            return new HashMap<String, Object>(_attributes);
        }
    }

    private Object getActualAttribute(final String name)
    {
        synchronized (_attributes)
        {
            return _attributes.get(name);
        }
    }

    public Object setAttribute(final String name, final Object expected, final Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        if (_taskExecutor.isTaskExecutorThread())
        {
            authoriseSetAttribute(name, expected, desired);
            if (changeAttribute(name, expected, desired))
            {
                attributeSet(name, expected, desired);
                return desired;
            }
            else
            {
                return getAttribute(name);
            }
        }
        else
        {
            return _taskExecutor.submitAndWait(new SetAttributeTask(this, name, expected, desired));
        }
    }

    protected boolean changeAttribute(final String name, final Object expected, final Object desired)
    {
        synchronized (_attributes)
        {
            Object currentValue = getAttribute(name);
            if((currentValue == null && expected == null)
               || (currentValue != null && currentValue.equals(expected)))
            {
                //TODO: don't put nulls
                _attributes.put(name, desired);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    public <T extends ConfiguredObject> T getParent(final Class<T> clazz)
    {
        synchronized (_parents)
        {
            return (T) _parents.get(clazz);
        }
    }

    protected <T extends ConfiguredObject> void addParent(Class<T> clazz, T parent)
    {
        synchronized (_parents)
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

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + _id + ", name=" + getName() + "]";
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if (_taskExecutor.isTaskExecutorThread())
        {
            authoriseCreateChild(childClass, attributes, otherParents);
            C child = addChild(childClass, attributes, otherParents);
            if (child != null)
            {
                childAdded(child);
            }
            return child;
        }
        else
        {
            return (C)_taskExecutor.submitAndWait(new CreateChildTask(this, childClass, attributes, otherParents));
        }
    }

    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }


    protected TaskExecutor getTaskExecutor()
    {
        return _taskExecutor;
    }

    @Override
    public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        if (getTaskExecutor().isTaskExecutorThread())
        {
            authoriseSetAttributes(attributes);
            changeAttributes(attributes);
        }
        else
        {
            getTaskExecutor().submitAndWait(new ChangeAttributesTask(this, attributes));
        }
    }

    protected void changeAttributes(final Map<String, Object> attributes)
    {
        validateChangeAttributes(attributes);
        Collection<String> names = getAttributeNames();
        for (String name : names)
        {
            if (attributes.containsKey(name))
            {
                Object desired = attributes.get(name);
                Object expected = getAttribute(name);
                if (changeAttribute(name, expected, desired))
                {
                    attributeSet(name, expected, desired);
                }
            }
        }
    }

    protected void validateChangeAttributes(final Map<String, Object> attributes)
    {
        if (attributes.containsKey(ID))
        {
            UUID id = getId();
            Object idAttributeValue = attributes.get(ID);
            if (idAttributeValue != null && !idAttributeValue.equals(id.toString()))
            {
                throw new IllegalConfigurationException("Cannot change existing configured object id");
            }
        }
    }

    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        // allowed by default
    }

    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        // allowed by default
    }

    protected <C extends ConfiguredObject> void authoriseCreateChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents) throws AccessControlException
    {
        // allowed by default
    }

    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        // allowed by default
    }

    protected Map<String, Object> getDefaultAttributes()
    {
        return _defaultAttributes;
    }

    /**
     * Returns a map of effective attribute values that would result
     * if applying the supplied changes. Does not apply the changes.
     */
    protected Map<String, Object> generateEffectiveAttributes(Map<String,Object> changedValues)
    {
        //Build a new set of effective attributes that would be
        //the result of applying the attribute changes, so we
        //can validate the configuration that would result

        Map<String, Object> defaultValues = getDefaultAttributes();
        Map<String, Object> existingActualValues = getActualAttributes();

        //create a new merged map, starting with the defaults
        Map<String, Object> merged =  new HashMap<String, Object>(defaultValues);

        for(String name : getAttributeNames())
        {
            if(changedValues.containsKey(name))
            {
                Object changedValue = changedValues.get(name);
                if(changedValue != null)
                {
                    //use the new non-null value for the merged values
                    merged.put(name, changedValue);
                }
                else
                {
                    //we just use the default (if there was one) since the changed
                    //value is null and effectively clears any existing actual value
                }
            }
            else if(existingActualValues.get(name) != null)
            {
                //Use existing non-null actual value for the merge
                merged.put(name, existingActualValues.get(name));
            }
            else
            {
                //There was neither a change or an existing non-null actual
                //value, so just use the default value (if there was one).
            }
        }

        return merged;
    }

    @Override
    public String getLastUpdatedBy()
    {
        return null;
    }

    @Override
    public long getLastUpdatedTime()
    {
        return 0;
    }

    @Override
    public String getCreatedBy()
    {
        return null;
    }

    @Override
    public long getCreatedTime()
    {
        return 0;
    }

    @Override
    public String getType()
    {
        return (String)getAttribute(TYPE);
    }
}
