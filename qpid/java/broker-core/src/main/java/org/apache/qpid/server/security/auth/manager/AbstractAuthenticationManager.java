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
package org.apache.qpid.server.security.auth.manager;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.model.adapter.AbstractConfiguredObject;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.MapValueConverter;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractAuthenticationManager<T extends AbstractAuthenticationManager<T>>
    extends AbstractConfiguredObject<T>
    implements AuthenticationProvider<T>, AuthenticationManager
{
    private static final Logger LOGGER = Logger.getLogger(AbstractAuthenticationManager.class);

    private final Broker _broker;
    private PreferencesProvider _preferencesProvider;
    private AtomicReference<State> _state = new AtomicReference<State>(State.INITIALISING);

    protected AbstractAuthenticationManager(final Broker broker,
                                            final Map<String, Object> defaults,
                                            final Map<String, Object> attributes)
    {
        super(Collections.<Class<? extends ConfiguredObject>, ConfiguredObject<?>>singletonMap(Broker.class, broker),
              defaults, attributes, broker.getTaskExecutor());
        _broker = broker;
    }

    protected final Broker getBroker()
    {
        return _broker;
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostPortBindings()
    {
        return null;
    }

    @Override
    public SubjectCreator getSubjectCreator()
    {
        return new SubjectCreator(this, _broker.getGroupProviders());
    }

    @Override
    public PreferencesProvider getPreferencesProvider()
    {
        return _preferencesProvider;
    }

    @Override
    public void setPreferencesProvider(final PreferencesProvider preferencesProvider)
    {
        _preferencesProvider = preferencesProvider;
    }




    @Override
    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {

    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if(childClass == PreferencesProvider.class)
        {
            String name = MapValueConverter.getStringAttribute(PreferencesProvider.NAME, attributes);
            String type = MapValueConverter.getStringAttribute(PreferencesProvider.TYPE, attributes);
            PreferencesProviderFactory factory = PreferencesProviderFactory.FACTORIES.get(type);
            UUID id = UUIDGenerator.generatePreferencesProviderUUID(name, getName());
            PreferencesProvider pp = factory.createInstance(id, attributes, this);
            pp.setDesiredState(State.INITIALISING, State.ACTIVE);
            _preferencesProvider = pp;
            return (C)pp;
        }
        throw new IllegalArgumentException("Cannot create child of class " + childClass.getSimpleName());
    }


    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of authentication provider is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of authentication provider attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of authentication provider attributes is denied");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if (clazz == PreferencesProvider.class && _preferencesProvider != null)
        {
            return (Collection<C>) Collections.<PreferencesProvider>singleton(_preferencesProvider);
        }
        return Collections.emptySet();
    }

    @Override
    public boolean setState(State currentState, State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        State state = _state.get();
        if(desiredState == State.DELETED)
        {
            String providerName = getName();

            // verify that provider is not in use
            Collection<Port> ports = new ArrayList<Port>(_broker.getPorts());
            for (Port port : ports)
            {
                if (providerName.equals(port.getAttribute(Port.AUTHENTICATION_PROVIDER)))
                {
                    throw new IntegrityViolationException("Authentication provider '" + providerName + "' is set on port " + port.getName());
                }
            }

            if ((state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED  || state == State.ERRORED)
                && _state.compareAndSet(state, State.DELETED))
            {
                close();
                delete();
                if (_preferencesProvider != null)
                {
                    _preferencesProvider.setDesiredState(_preferencesProvider.getState(), State.DELETED);
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot delete authentication provider in state: " + state);
            }
        }
        else if(desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED || state == State.STOPPED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    initialise();
                    if (_preferencesProvider != null)
                    {
                        _preferencesProvider.setDesiredState(_preferencesProvider.getState(), State.ACTIVE);
                    }
                    return true;
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate authentication provider: " + getName(), e);
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Cannot activate authentication provider in state: " + state);
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                return true;
            }
        }
        else if(desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                close();
                if (_preferencesProvider != null)
                {
                    _preferencesProvider.setDesiredState(_preferencesProvider.getState(), State.STOPPED);
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop authentication provider in state: " + state);
            }
        }

        return false;
    }


    protected boolean updateState(State from, State to)
    {
        return _state.compareAndSet(from, to);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(getClass());
    }

    @Override
    public Object getAttribute(final String name)
    {
        if(STATE.equals(name))
        {
            return getState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        return super.getAttribute(name);
    }
}
