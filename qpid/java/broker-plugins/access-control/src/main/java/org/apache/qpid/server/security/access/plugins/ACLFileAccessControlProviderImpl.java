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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.server.security.access.Operation;

public class ACLFileAccessControlProviderImpl
        extends AbstractConfiguredObject<ACLFileAccessControlProviderImpl>
        implements ACLFileAccessControlProvider<ACLFileAccessControlProviderImpl>
{
    private static final Logger LOGGER = Logger.getLogger(ACLFileAccessControlProviderImpl.class);

    protected DefaultAccessControl _accessControl;
    protected final Broker _broker;

    @ManagedAttributeField
    private String _path;

    @ManagedObjectFactoryConstructor
    public ACLFileAccessControlProviderImpl(Map<String, Object> attributes, Broker broker)
    {
        super(parentsMap(broker), attributes);


        _broker = broker;

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
    protected void onOpen()
    {
        super.onOpen();
        _accessControl = new DefaultAccessControl(getPath(), _broker);
    }

    @Override
    public String getPath()
    {
        return _path;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }


    @StateTransition(currentState = {State.UNINITIALIZED, State.QUIESCED, State.ERRORED}, desiredState = State.ACTIVE)
    private void activate()
    {
        if(_broker.isManagementMode())
        {

            setState(_accessControl.validate() ? State.QUIESCED : State.ERRORED);
        }
        else
        {
            try
            {
                _accessControl.open();
                setState(State.ACTIVE);
            }
            catch (RuntimeException e)
            {
                setState(State.ERRORED);
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
    }

    @Override
    protected void onClose()
    {
        super.onClose();
        _accessControl.close();
    }

    @StateTransition(currentState = State.UNINITIALIZED, desiredState = State.QUIESCED)
    private void startQuiesced()
    {
        setState(State.QUIESCED);
    }

    @StateTransition(currentState = {State.ACTIVE, State.QUIESCED, State.ERRORED}, desiredState = State.DELETED)
    private void doDelete()
    {
        close();
        setState(State.DELETED);
        deleted();
    }

    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
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
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
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
