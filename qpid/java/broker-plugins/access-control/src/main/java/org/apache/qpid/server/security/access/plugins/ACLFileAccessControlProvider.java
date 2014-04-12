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
package org.apache.qpid.server.security.access.plugins;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.plugin.AccessControlProviderFactory;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.MapValueConverter;

@ManagedObject( category = false, type="AclFile" )
public class ACLFileAccessControlProvider
        extends AbstractConfiguredObject<ACLFileAccessControlProvider>
        implements AccessControlProvider<ACLFileAccessControlProvider>
{
    private static final Logger LOGGER = Logger.getLogger(ACLFileAccessControlProvider.class);

    protected DefaultAccessControl _accessControl;
    protected final Broker _broker;

    protected Map<String, AccessControlProviderFactory> _factories;
    private AtomicReference<State> _state;

    @ManagedAttributeField
    private String _path;

    public ACLFileAccessControlProvider(Broker broker,
                                        Map<String, Object> attributes)
    {
        super(parentsMap(broker),
              attributes, broker.getTaskExecutor());


        _broker = broker;

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);

    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _accessControl = new DefaultAccessControl(getPath(), _broker);
    }

    @ManagedAttribute( automate = true, mandatory = true )
    public String getPath()
    {
        return _path;
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
        return getAttributeNames(getClass());
    }

    @Override
    public Object getAttribute(String name)
    {
        if(DURABLE.equals(name))
        {
            return true;
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

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public boolean setState(State currentState, State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        State state = _state.get();

        if(desiredState == State.DELETED)
        {
            deleted();
            return _state.compareAndSet(state, State.DELETED);
        }
        else if (desiredState == State.QUIESCED)
        {
            return _state.compareAndSet(state, State.QUIESCED);
        }
        else if(desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    _accessControl.open();
                    return true;
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate ACL provider: " + getName(), e);
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Can't activate access control provider in " + state + " state");
            }
        }
        else if(desiredState == State.STOPPED)
        {
            if(_state.compareAndSet(state, State.STOPPED))
            {
                _accessControl.close();
                return true;
            }

            return false;
        }
        return false;
    }


    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        throw new UnsupportedOperationException("Changing attributes on AccessControlProvider is not supported");
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AccessControlProvider.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of AccessControlProvider is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AccessControlProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of AccessControlProvider attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), AccessControlProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of AccessControlProvider attributes is denied");
        }
    }
    
    public AccessControl getAccessControl()
    {
        return _accessControl;
    }
}
