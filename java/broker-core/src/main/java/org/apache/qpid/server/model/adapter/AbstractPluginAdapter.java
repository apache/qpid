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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.access.Operation;

public abstract class AbstractPluginAdapter<X extends Plugin<X>> extends AbstractConfiguredObject<X> implements Plugin<X>
{
    private Broker _broker;

    protected AbstractPluginAdapter(UUID id, Map<String, Object> attributes, Broker broker)
    {
        super(parentsMap(broker),
              combineIdWithAttributes(id, attributes), broker.getTaskExecutor());
        _broker = broker;
    }


    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getState()
    {
        return null;
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(Plugin.class);
    }

    @Override
    public Object getAttribute(String name)
    {
        if (ID.equals(name))
        {
            return getId();
        }
        else if (STATE.equals(name))
        {
            return getState();
        }
        else if (DURABLE.equals(name))
        {
            return isDurable();
        }
        else if (LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        return super.getAttribute(name);
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Plugin.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of plugin is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Plugin.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of plugin attribute is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Plugin.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of plugin attributes is denied");
        }
    }

    protected Broker getBroker()
    {
        return _broker;
    }
}
