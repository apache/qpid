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
package org.apache.qpid.server.security;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.util.MapValueConverter;

public abstract class AbstractKeyStoreAdapter<X extends ConfiguredObject<X>> extends AbstractConfiguredObject<X>
{
    public static final String DUMMY_PASSWORD_MASK = "********";
    public static final String DEFAULT_KEYSTORE_TYPE = java.security.KeyStore.getDefaultType();

    @ManagedAttributeField
    private String _password;


    protected AbstractKeyStoreAdapter(UUID id, Broker broker, Map<String, Object> defaults,
                                      Map<String, Object> attributes)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, broker),
              defaults,
              combineIdWithAttributes(id, attributes),
              broker.getTaskExecutor());

        MapValueConverter.assertMandatoryAttribute(KeyStore.PATH, attributes);
    }


    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
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
    public void setDurable(boolean durable) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public Object getAttribute(String name)
    {
        if(KeyStore.STATE.equals(name))
        {
            return getState();
        }
        else if(KeyStore.DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(KeyStore.LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }

        return super.getAttribute(name);
    }

    public String getPassword()
    {
        return _password;
    }

    public void setPassword(String password)
    {
        _password = password;
    }
}
