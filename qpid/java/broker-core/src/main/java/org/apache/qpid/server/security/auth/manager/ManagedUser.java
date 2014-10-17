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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.configuration.updater.VoidTask;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.access.Operation;

@ManagedObject( category = false, type = ManagedUser.MANAGED_USER_TYPE)
class ManagedUser extends AbstractConfiguredObject<ManagedUser> implements User<ManagedUser>
{
    public static final String MANAGED_USER_TYPE = "managed";

    private ConfigModelPasswordManagingAuthenticationProvider<?> _authenticationManager;
    @ManagedAttributeField
    private String _password;

    @ManagedObjectFactoryConstructor
    ManagedUser(final Map<String, Object> attributes, ConfigModelPasswordManagingAuthenticationProvider<?> parent)
    {
        super(parentsMap(parent), attributes);
        _authenticationManager = parent;

        setState(State.ACTIVE);
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _authenticationManager.getUserMap().put(getName(), this);
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        _authenticationManager.validateUser(this);
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
    protected void authoriseSetDesiredState(final State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            _authenticationManager.getSecurityManager().authoriseUserOperation(Operation.DELETE, getName());
        }

    }

    @StateTransition(currentState = {State.ACTIVE}, desiredState = State.DELETED)
    private void doDelete()
    {
        _authenticationManager.getUserMap().remove(getName());
        deleted();
    }


    @Override
    public void setAttributes(final Map<String, Object> attributes)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        runTask(new VoidTask()
        {

            @Override
            public void execute()
            {
                Map<String, Object> modifiedAttributes = new HashMap<String, Object>(attributes);
                final String newPassword = (String) attributes.get(User.PASSWORD);
                if (attributes.containsKey(User.PASSWORD)
                    && !newPassword.equals(getActualAttributes().get(User.PASSWORD)))
                {
                    modifiedAttributes.put(User.PASSWORD,
                                           _authenticationManager.createStoredPassword(newPassword));

                }
                ManagedUser.super.setAttributes(modifiedAttributes);
            }
        });


    }

    @Override
    public String getPassword()
    {
        return _password;
    }

    @Override
    public void setPassword(final String password)
    {
        _authenticationManager.getSecurityManager().authoriseUserOperation(Operation.UPDATE, getName());

        changeAttribute(User.PASSWORD, getAttribute(User.PASSWORD),
                        _authenticationManager.createStoredPassword(password));

    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(final Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Object> getPreferences()
    {
        PreferencesProvider<?> preferencesProvider = _authenticationManager.getPreferencesProvider();
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
        PreferencesProvider<?> preferencesProvider = _authenticationManager.getPreferencesProvider();
        if (preferencesProvider == null)
        {
            return null;
        }
        return preferencesProvider.setPreferences(this.getName(), preferences);
    }

    @Override
    public boolean deletePreferences()
    {
        PreferencesProvider preferencesProvider = _authenticationManager.getPreferencesProvider();
        if (preferencesProvider == null)
        {
            return false;
        }
        String[] deleted = preferencesProvider.deletePreferences(this.getName());
        return deleted.length == 1;
    }

}
