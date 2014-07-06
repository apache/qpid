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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IntegrityViolationException;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.port.AbstractPortWithAuthProvider;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;

public abstract class AbstractAuthenticationManager<T extends AbstractAuthenticationManager<T>>
    extends AbstractConfiguredObject<T>
    implements AuthenticationProvider<T>, AuthenticationManager
{
    private static final Logger LOGGER = Logger.getLogger(AbstractAuthenticationManager.class);

    private final Broker _broker;
    private PreferencesProvider _preferencesProvider;
    private AtomicReference<State> _state = new AtomicReference<State>(State.UNINITIALIZED);

    protected AbstractAuthenticationManager(final Map<String, Object> attributes, final Broker broker)
    {
        super(parentsMap(broker), attributes);
        _broker = broker;
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        Collection<PreferencesProvider> prefsProviders = getChildren(PreferencesProvider.class);
        if(prefsProviders != null && prefsProviders.size() > 1)
        {
            throw new IllegalConfigurationException("Only one preference provider can be configured for an authentication provider");
        }

        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    protected final Broker getBroker()
    {
        return _broker;
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        Collection<PreferencesProvider> prefsProviders = getChildren(PreferencesProvider.class);
        if(prefsProviders != null && !prefsProviders.isEmpty())
        {
            _preferencesProvider = prefsProviders.iterator().next();
        }
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
    public void recoverUser(final User user)
    {
        throw new IllegalConfigurationException("Cannot associate  " + user + " with authentication provider " + this);
    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if(childClass == PreferencesProvider.class)
        {
            attributes = new HashMap<String, Object>(attributes);
            PreferencesProvider pp = getObjectFactory().create(PreferencesProvider.class, attributes, this);

            _preferencesProvider = pp;
            return (C)pp;
        }
        throw new IllegalArgumentException("Cannot create child of class " + childClass.getSimpleName());
    }


    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
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
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AuthenticationProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of authentication provider attributes is denied");
        }
    }

    @StateTransition( currentState = State.UNINITIALIZED, desiredState = State.QUIESCED )
    protected void startQuiesced()
    {
        _state.set(State.QUIESCED);
    }

    @StateTransition( currentState = { State.UNINITIALIZED, State.QUIESCED, State.QUIESCED }, desiredState = State.ACTIVE )
    protected void activate()
    {
        try
        {
            _state.set(State.ACTIVE);
        }
        catch(RuntimeException e)
        {
            _state.set(State.ERRORED);
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

    @StateTransition( currentState = { State.ACTIVE, State.QUIESCED, State.ERRORED}, desiredState = State.DELETED)
    protected void doDelete()
    {

        String providerName = getName();

        // verify that provider is not in use
        Collection<Port> ports = new ArrayList<Port>(_broker.getPorts());
        for (Port port : ports)
        {
            if(port instanceof AbstractPortWithAuthProvider
               && ((AbstractPortWithAuthProvider<?>)port).getAuthenticationProvider() == this)
            {
                throw new IntegrityViolationException("Authentication provider '" + providerName + "' is set on port " + port.getName());
            }
        }

        close();
        if (_preferencesProvider != null)
        {
            _preferencesProvider.delete();
        }
        deleted();

        _state.set(State.DELETED);

    }


    protected boolean updateState(State from, State to)
    {
        return _state.compareAndSet(from, to);
    }

    @Override
    public Object getAttribute(final String name)
    {
        if(STATE.equals(name))
        {
            return getState();
        }
        return super.getAttribute(name);
    }
}
