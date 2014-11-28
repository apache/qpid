/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.server.security.auth.manager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.login.AccountNotFoundException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ExternalFileBasedAuthenticationManager;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.PreferencesSupportingAuthenticationProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.security.auth.AuthenticationResult.AuthenticationStatus;
import org.apache.qpid.server.security.auth.UsernamePrincipal;
import org.apache.qpid.server.security.auth.database.PrincipalDatabase;

public abstract class PrincipalDatabaseAuthenticationManager<T extends PrincipalDatabaseAuthenticationManager<T>>
        extends AbstractAuthenticationManager<T>
        implements ExternalFileBasedAuthenticationManager<T>, PreferencesSupportingAuthenticationProvider
{

    private static final Logger LOGGER = Logger.getLogger(PrincipalDatabaseAuthenticationManager.class);


    private final Map<Principal, PrincipalAdapter> _userMap = new ConcurrentHashMap<Principal, PrincipalAdapter>();

    private PrincipalDatabase _principalDatabase;
    @ManagedAttributeField
    private String _path;

    protected PrincipalDatabaseAuthenticationManager(final Map<String, Object> attributes, final Broker broker)
    {
        super(attributes, broker);
    }

    @Override
    protected void validateOnCreate()
    {
        super.validateOnCreate();
        File passwordFile = new File(_path);
        if (passwordFile.exists() && !passwordFile.canRead())
        {
            throw new IllegalConfigurationException(String.format("Cannot read password file '%s'. Please check permissions.", _path));
        }
    }

    @Override
    protected void onCreate()
    {
        super.onCreate();
        File passwordFile = new File(_path);
        if (!passwordFile.exists())
        {
            try
            {
                passwordFile.createNewFile();
            }
            catch (IOException e)
            {
                throw new IllegalConfigurationException(String.format("Cannot create password file at '%s'", _path), e);
            }
        }
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _principalDatabase = createDatabase();
        initialise();
        List<Principal> users = _principalDatabase == null ? Collections.<Principal>emptyList() : _principalDatabase.getUsers();
        for (Principal user : users)
        {
            PrincipalAdapter principalAdapter = new PrincipalAdapter(user);
            principalAdapter.registerWithParents();
            principalAdapter.open();
            _userMap.put(user, principalAdapter);
        }
    }

    protected abstract PrincipalDatabase createDatabase();


    @Override
    public String getPath()
    {
        return _path;
    }

    public void initialise()
    {
        try
        {
            _principalDatabase.open(new File(_path));
        }
        catch (FileNotFoundException e)
        {
            throw new IllegalConfigurationException("Exception opening password database: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot use password database at :" + _path, e);
        }
    }

    public List<String> getMechanisms()
    {
        return _principalDatabase.getMechanisms();
    }

    public SaslServer createSaslServer(String mechanism, String localFQDN, Principal externalPrincipal) throws SaslException
    {
        return _principalDatabase.createSaslServer(mechanism, localFQDN, externalPrincipal);
    }

    /**
     * @see org.apache.qpid.server.model.AuthenticationProvider#authenticate(SaslServer, byte[])
     */
    public AuthenticationResult authenticate(SaslServer server, byte[] response)
    {
        try
        {
            // Process response from the client
            byte[] challenge = server.evaluateResponse(response != null ? response : new byte[0]);

            if (server.isComplete())
            {
                final String userId = server.getAuthorizationID();
                return new AuthenticationResult(new UsernamePrincipal(userId));
            }
            else
            {
                return new AuthenticationResult(challenge, AuthenticationResult.AuthenticationStatus.CONTINUE);
            }
        }
        catch (SaslException e)
        {
            return new AuthenticationResult(AuthenticationResult.AuthenticationStatus.ERROR, e);
        }
    }

    /**
     * @see org.apache.qpid.server.model.AuthenticationProvider#authenticate(String, String)
     */
    public AuthenticationResult authenticate(final String username, final String password)
    {
        try
        {
            if (_principalDatabase.verifyPassword(username, password.toCharArray()))
            {
                return new AuthenticationResult(new UsernamePrincipal(username));
            }
            else
            {
                return new AuthenticationResult(AuthenticationStatus.CONTINUE);
            }
        }
        catch (AccountNotFoundException e)
        {
            return new AuthenticationResult(AuthenticationStatus.CONTINUE);
        }
    }

    public PrincipalDatabase getPrincipalDatabase()
    {
        return _principalDatabase;
    }


    @StateTransition( currentState = { State.ACTIVE, State.QUIESCED, State.ERRORED}, desiredState = State.DELETED)
    public void doDelete()
    {
        File file = new File(_path);
        if (file.exists() && file.isFile())
        {
            file.delete();
        }
        deleted();
        setState(State.DELETED);
    }

    @Override
    public boolean createUser(String username, String password, Map<String, String> attributes)
    {
        getSecurityManager().authoriseUserOperation(Operation.CREATE, username);
        Principal principal = new UsernamePrincipal(username);
        boolean created =
                getPrincipalDatabase().createPrincipal(principal, password.toCharArray());
        if(created)
        {
            principal = getPrincipalDatabase().getUser(username);

            PrincipalAdapter principalAdapter = new PrincipalAdapter(principal);
            principalAdapter.create();
            _userMap.put(principal, principalAdapter);
        }
        return created;

    }


    private void deleteUserFromDatabase(String username) throws AccountNotFoundException
    {
        getSecurityManager().authoriseUserOperation(Operation.DELETE, username);
        UsernamePrincipal principal = new UsernamePrincipal(username);
        getPrincipalDatabase().deletePrincipal(principal);
        _userMap.remove(principal);
    }

    @Override
    public void deleteUser(String username) throws AccountNotFoundException
    {
        UsernamePrincipal principal = new UsernamePrincipal(username);
        PrincipalAdapter user = _userMap.get(principal);
        if(user != null)
        {
            user.delete();
        }
        else
        {
            deleteUserFromDatabase(username);
        }
    }

    private org.apache.qpid.server.security.SecurityManager getSecurityManager()
    {
        return getBroker().getSecurityManager();
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
                return (C) _userMap.get(p);
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
        return super.getChildren(clazz);
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

    @Override
    protected void validateChange(final ConfiguredObject<?> updatedObject, final Set<String> changedAttributes)
    {
        super.validateChange(updatedObject, changedAttributes);

        ExternalFileBasedAuthenticationManager<?> updated = (ExternalFileBasedAuthenticationManager<?>) updatedObject;
        if (changedAttributes.contains(NAME) &&  !getName().equals(updated.getName()))
        {
            throw new IllegalConfigurationException("Changing the name of authentication provider is not supported");
        }
        if (changedAttributes.contains(TYPE) && !getType().equals(updated.getType()))
        {
            throw new IllegalConfigurationException("Changing the type of authentication provider is not supported");
        }
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        super.changeAttributes(attributes);
        if(getState() != State.DELETED && getDesiredState() != State.DELETED)
        {
            // TODO - this does not belong here!
            try
            {
                initialise();
                // if provider was previously in ERRORED state then set its state to ACTIVE
                setState(State.ACTIVE);
            }
            catch(RuntimeException e)
            {
                if(getState() != State.ERRORED)
                {
                    throw e;
                }
            }
        }


    }

    private class PrincipalAdapter extends AbstractConfiguredObject<PrincipalAdapter> implements User<PrincipalAdapter>
    {
        private final Principal _user;

        @ManagedAttributeField
        private String _password;

        public PrincipalAdapter(Principal user)
        {
            super(parentsMap(PrincipalDatabaseAuthenticationManager.this),createPrincipalAttributes(PrincipalDatabaseAuthenticationManager.this, user));
            _user = user;

        }

        @Override
        public void onValidate()
        {
            super.onValidate();
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

        @Override
        public String getPassword()
        {
            return _password;
        }

        @Override
        public void setPassword(String password)
        {
            try
            {
                PrincipalDatabaseAuthenticationManager.this.setPassword(_user.getName(), password);
            }
            catch (AccountNotFoundException e)
            {
                throw new IllegalStateException(e);
            }
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

        @StateTransition(currentState = {State.UNINITIALIZED,State.ERRORED}, desiredState = State.ACTIVE)
        private void activate()
        {
            setState(State.ACTIVE);
        }

        @StateTransition(currentState = State.ACTIVE, desiredState = State.DELETED)
        private void doDelete()
        {
            try
            {
                String userName = _user.getName();
                deleteUserFromDatabase(userName);
                PreferencesProvider preferencesProvider = getPreferencesProvider();
                if (preferencesProvider != null)
                {
                    preferencesProvider.deletePreferences(userName);
                }
                deleted();
                setState(State.DELETED);
            }
            catch (AccountNotFoundException e)
            {
                LOGGER.warn("Failed to delete user " + _user, e);
            }

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
            return PrincipalDatabaseAuthenticationManager.this.getPreferencesProvider();
        }

    }

    private static Map<String, Object> createPrincipalAttributes(PrincipalDatabaseAuthenticationManager manager, final Principal user)
    {
        final Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, UUID.randomUUID());
        attributes.put(NAME, user.getName());
        return attributes;
    }

}
