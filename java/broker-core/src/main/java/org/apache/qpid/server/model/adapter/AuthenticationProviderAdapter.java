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

import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.login.AccountNotFoundException;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.MapValueConverter;

public abstract class AuthenticationProviderAdapter<X extends AuthenticationProvider<X>, T extends AuthenticationManager> extends
                                                                                                                          AbstractConfiguredObject<X>
        implements AuthenticationProvider<X>
{
    private static final Logger LOGGER = Logger.getLogger(AuthenticationProviderAdapter.class);

    protected T _authManager;
    protected final Broker _broker;

    protected Collection<String> _supportedAttributes;
    protected Map<String, AuthenticationManagerFactory> _factories;
    private final AtomicReference<State> _state;
    private PreferencesProvider _preferencesProvider;

    private AuthenticationProviderAdapter(UUID id, Broker broker, final T authManager, Map<String, Object> attributes, Collection<String> attributeNames)
    {
        super(createAttributes(id, attributes), Collections.<String,Object>emptyMap(), broker.getTaskExecutor());
        _authManager = authManager;
        _broker = broker;
        _supportedAttributes = createSupportedAttributes(attributeNames);
        _factories = getAuthenticationManagerFactories();

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
        addParent(Broker.class, broker);

        // set attributes now after all attribute names are known
        if (attributes != null)
        {
            for (String name : _supportedAttributes)
            {
                if (attributes.containsKey(name))
                {
                    changeAttribute(name, null, attributes.get(name));
                }
            }
        }
    }

    private static Map<String, Object> createAttributes(final UUID id, final Map<String, Object> attributes)
    {
        Map<String, Object> initialAttributes = new HashMap<String, Object>();
        initialAttributes.put(ID, id);
        initialAttributes.put(NAME, attributes.get(NAME));
        return initialAttributes;
    }

    T getAuthManager()
    {
        return _authManager;
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostPortBindings()
    {
        return Collections.emptyList();
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
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
    public void setDurable(boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return _supportedAttributes;
    }

    @Override
    public Object getAttribute(String name)
    {
        if(DURABLE.equals(name))
        {
            return true;
        }
        else if(ID.equals(name))
        {
            return getId();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(STATE.equals(name))
        {
            return getState();
        }
        return super.getAttribute(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if (clazz == PreferencesProvider.class && _preferencesProvider != null)
        {
            return (Collection<C>)Collections.<PreferencesProvider>singleton(_preferencesProvider);
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
                _authManager.close();
                _authManager.onDelete();
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
                    _authManager.initialise();
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
                _authManager.close();
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

    @Override
    public SubjectCreator getSubjectCreator()
    {
        return new SubjectCreator(_authManager, _broker.getGroupProviders());
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> effectiveAttributes = super.generateEffectiveAttributes(attributes);
        AuthenticationManager manager = validateAttributes(effectiveAttributes);
        manager.initialise();
        super.changeAttributes(attributes);
        _authManager = (T)manager;

        // if provider was previously in ERRORED state then set its state to ACTIVE
        _state.compareAndSet(State.ERRORED, State.ACTIVE);
    }

    private Map<String, AuthenticationManagerFactory> getAuthenticationManagerFactories()
    {
        QpidServiceLoader<AuthenticationManagerFactory> loader = new QpidServiceLoader<AuthenticationManagerFactory>();
        Iterable<AuthenticationManagerFactory> factories = loader.atLeastOneInstanceOf(AuthenticationManagerFactory.class);
        Map<String, AuthenticationManagerFactory> factoryMap = new HashMap<String, AuthenticationManagerFactory>();
        for (AuthenticationManagerFactory factory : factories)
        {
            factoryMap.put(factory.getType(), factory);
        }
        return factoryMap;
    }

    protected Collection<String> createSupportedAttributes(Collection<String> factoryAttributes)
    {
        List<String> attributesNames = new ArrayList<String>(getAttributeNames(AuthenticationProvider.class));
        if (factoryAttributes != null)
        {
            attributesNames.addAll(factoryAttributes);
        }
        return Collections.unmodifiableCollection(attributesNames);
    }

    protected AuthenticationManager validateAttributes(Map<String, Object> attributes)
    {
        super.validateChangeAttributes(attributes);

        String newName = (String)attributes.get(NAME);
        String currentName = getName();
        if (!currentName.equals(newName))
        {
            throw new IllegalConfigurationException("Changing the name of authentication provider is not supported");
        }
        String newType = (String)attributes.get(AuthenticationManagerFactory.ATTRIBUTE_TYPE);
        String currentType = (String)getAttribute(AuthenticationManagerFactory.ATTRIBUTE_TYPE);
        if (!currentType.equals(newType))
        {
            throw new IllegalConfigurationException("Changing the type of authentication provider is not supported");
        }
        AuthenticationManagerFactory managerFactory = _factories.get(newType);
        if (managerFactory == null)
        {
            throw new IllegalConfigurationException("Cannot find authentication provider factory for type " + newType);
        }
        AuthenticationManager manager = managerFactory.createInstance(_broker, attributes);
        if (manager == null)
        {
            throw new IllegalConfigurationException("Cannot change authentication provider " + newName + " of type " + newType + " with the given attributes");
        }
        return manager;
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

    public PreferencesProvider getPreferencesProvider()
    {
        return _preferencesProvider;
    }

    public void setPreferencesProvider(PreferencesProvider provider)
    {
        if (AnonymousAuthenticationManagerFactory.PROVIDER_TYPE.equals(getAttribute(TYPE)))
        {
            throw new IllegalConfigurationException("Cannot set preferences provider for anonymous authentication provider");
        }
        _preferencesProvider = provider;
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

    public static class SimpleAuthenticationProviderAdapter extends AuthenticationProviderAdapter<SimpleAuthenticationProviderAdapter,AuthenticationManager>
    {

        public SimpleAuthenticationProviderAdapter(
                UUID id, Broker broker, AuthenticationManager authManager, Map<String, Object> attributes, Collection<String> attributeNames)
        {
            super(id, broker,authManager, attributes, attributeNames);
        }
    }

    public static class PrincipalDatabaseAuthenticationManagerAdapter
            extends AuthenticationProviderAdapter<PrincipalDatabaseAuthenticationManagerAdapter, PrincipalDatabaseAuthenticationManager>
            implements PasswordCredentialManagingAuthenticationProvider<PrincipalDatabaseAuthenticationManagerAdapter>
    {
        public PrincipalDatabaseAuthenticationManagerAdapter(
                UUID id, Broker broker, PrincipalDatabaseAuthenticationManager authManager, Map<String, Object> attributes, Collection<String> attributeNames)
        {
            super(id, broker, authManager, attributes, attributeNames);
        }

        @Override
        public boolean createUser(String username, String password, Map<String, String> attributes)
        {
            getSecurityManager().authoriseUserOperation(Operation.CREATE, username);
            return getPrincipalDatabase().createPrincipal(new UsernamePrincipal(username), password.toCharArray());

        }

        @Override
        public void deleteUser(String username) throws AccountNotFoundException
        {
            getSecurityManager().authoriseUserOperation(Operation.DELETE, username);
            getPrincipalDatabase().deletePrincipal(new UsernamePrincipal(username));

        }

        private SecurityManager getSecurityManager()
        {
            return _broker.getSecurityManager();
        }

        private PrincipalDatabase getPrincipalDatabase()
        {
            return getAuthManager().getPrincipalDatabase();
        }

        @Override
        public void setPassword(String username, String password) throws AccountNotFoundException
        {
            getSecurityManager().authoriseUserOperation(Operation.UPDATE, username);

            getPrincipalDatabase().updatePassword(new UsernamePrincipal(username), password.toCharArray());

        }

        @Override
        public Map<String, Map<String, String>> getUsers()
        {

            Map<String, Map<String,String>> users = new HashMap<String, Map<String, String>>();
            for(Principal principal : getPrincipalDatabase().getUsers())
            {
                users.put(principal.getName(), Collections.<String, String>emptyMap());
            }
            return users;
        }

        public void reload() throws IOException
        {
            getPrincipalDatabase().reload();
        }

        @Override
        public <C extends ConfiguredObject> C addChild(Class<C> childClass,
                                                          Map<String, Object> attributes,
                                                          ConfiguredObject... otherParents)
        {
            if(childClass == User.class)
            {
                String username = (String) attributes.get("name");
                String password = (String) attributes.get("password");
                Principal p = new UsernamePrincipal(username);

                if(createUser(username, password,null))
                {
                    @SuppressWarnings("unchecked")
                    C principalAdapter = (C) new PrincipalAdapter(p);
                    return principalAdapter;
                }
                else
                {
                    LOGGER.info("Failed to create user " + username + ". User already exists?");
                    return null;

                }
            }

            return super.addChild(childClass, attributes, otherParents);
        }

        @Override
        public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
        {
            if(clazz == User.class)
            {
                List<Principal> users = getPrincipalDatabase().getUsers();
                Collection<User> principals = new ArrayList<User>(users.size());
                for(Principal user : users)
                {
                    principals.add(new PrincipalAdapter(user));
                }
                @SuppressWarnings("unchecked")
                Collection<C> unmodifiablePrincipals = (Collection<C>) Collections.unmodifiableCollection(principals);
                return unmodifiablePrincipals;
            }
            else
            {
                return super.getChildren(clazz);
            }
        }

        @Override
        protected void childAdded(ConfiguredObject child)
        {
            if (child instanceof User)
            {
                // no-op, prevent storing users in the broker store
                return;
            }
            super.childAdded(child);
        }

        @Override
        protected void childRemoved(ConfiguredObject child)
        {
            if (child instanceof User)
            {
                // no-op, as per above, users are not in the store
                return;
            }
            super.childRemoved(child);
        }

        private class PrincipalAdapter extends AbstractConfiguredObject<PrincipalAdapter> implements User<PrincipalAdapter>
        {
            private final Principal _user;

            public PrincipalAdapter(Principal user)
            {
                super(Collections.<String,Object>emptyMap(), createPrincipalAttributes(PrincipalDatabaseAuthenticationManagerAdapter.this, user),
                        PrincipalDatabaseAuthenticationManagerAdapter.this.getTaskExecutor());
                _user = user;

            }

            @Override
            public String getPassword()
            {
                return (String)getAttribute(PASSWORD);
            }

            @Override
            public void setPassword(String password)
            {
                try
                {
                    PrincipalDatabaseAuthenticationManagerAdapter.this.setPassword(_user.getName(), password);
                }
                catch (AccountNotFoundException e)
                {
                    throw new IllegalStateException(e);
                }
            }


            @Override
            public String setName(String currentName, String desiredName)
                    throws IllegalStateException, AccessControlException
            {
                throw new IllegalStateException("Names cannot be updated");
            }

            @Override
            public State getState()
            {
                return State.ACTIVE;
            }

            @Override
            public boolean isDurable()
            {
                return true;
            }

            @Override
            public void setDurable(boolean durable)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("Durability cannot be updated");
            }

            @Override
            public LifetimePolicy getLifetimePolicy()
            {
                return LifetimePolicy.PERMANENT;
            }

            @Override
            public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                throw new IllegalStateException("LifetimePolicy cannot be updated");
            }

            @Override
            public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
            {
                return null;
            }

            @Override
            public <C extends ConfiguredObject> C createChild(Class<C> childClass,
                                                              Map<String, Object> attributes,
                                                              ConfiguredObject... otherParents)
            {
                return null;
            }

            @Override
            public Collection<String> getAttributeNames()
            {
                return getAttributeNames(User.class);
            }

            @Override
            public Object getAttribute(String name)
            {
                if(ID.equals(name))
                {
                    return getId();
                }
                else if(PASSWORD.equals(name))
                {
                    return null; // for security reasons we don't expose the password
                }
                else if(NAME.equals(name))
                {
                    return getName();
                }
                return super.getAttribute(name);
            }

            @Override
            public boolean changeAttribute(String name, Object expected, Object desired)
                    throws IllegalStateException, AccessControlException, IllegalArgumentException
            {
                if(name.equals(PASSWORD))
                {
                    setPassword((String)desired);
                    return true;
                }
                return super.changeAttribute(name, expected, desired);
            }

            @Override
            protected boolean setState(State currentState, State desiredState)
                    throws IllegalStateTransitionException, AccessControlException
            {
                if(desiredState == State.DELETED)
                {
                    try
                    {
                        String userName = _user.getName();
                        deleteUser(userName);
                        PreferencesProvider preferencesProvider = getPreferencesProvider();
                        if (preferencesProvider != null)
                        {
                            preferencesProvider.deletePreferences(userName);
                        }
                    }
                    catch (AccountNotFoundException e)
                    {
                        LOGGER.warn("Failed to delete user " + _user, e);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public Map<String, Object> getPreferences()
            {
                PreferencesProvider preferencesProvider = getPreferencesProvider();
                if (preferencesProvider == null)
                {
                    return null;
                }
                return preferencesProvider.getPreferences(this.getName());
            }

            @Override
            public Object getPreference(String name)
            {
                Map<String, Object> preferences = getPreferences();
                if (preferences == null)
                {
                    return null;
                }
                return preferences.get(name);
            }

            @Override
            public Map<String, Object> setPreferences(Map<String, Object> preferences)
            {
                PreferencesProvider preferencesProvider = getPreferencesProvider();
                if (preferencesProvider == null)
                {
                    return null;
                }
                return preferencesProvider.setPreferences(this.getName(), preferences);
            }

            @Override
            public boolean deletePreferences()
            {
                PreferencesProvider preferencesProvider = getPreferencesProvider();
                if (preferencesProvider == null)
                {
                    return false;
                }
                String[] deleted = preferencesProvider.deletePreferences(this.getName());
                return deleted.length == 1;
            }

            private PreferencesProvider getPreferencesProvider()
            {
                return PrincipalDatabaseAuthenticationManagerAdapter.this.getPreferencesProvider();
            }

        }

        private static Map<String, Object> createPrincipalAttributes(PrincipalDatabaseAuthenticationManagerAdapter manager, final Principal user)
        {
            final Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(ID, UUIDGenerator.generateUserUUID(manager.getName(), user.getName()));
            attributes.put(NAME, user.getName());
            return attributes;
        }

    }

}
