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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.security.AccessControl;
import org.apache.qpid.server.util.urlstreamhandler.data.Handler;

public class ACLFileAccessControlProviderImpl
        extends AbstractConfiguredObject<ACLFileAccessControlProviderImpl>
        implements ACLFileAccessControlProvider<ACLFileAccessControlProviderImpl>
{
    private static final Logger LOGGER = Logger.getLogger(ACLFileAccessControlProviderImpl.class);

    static
    {
        Handler.register();
    }

    protected DefaultAccessControl _accessControl;
    protected final Broker _broker;

    @ManagedAttributeField( afterSet = "reloadAclFile")
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
    protected void validateOnCreate()
    {
        DefaultAccessControl accessControl = null;
        try
        {
            accessControl = new DefaultAccessControl(getPath(), _broker);
            accessControl.validate();
            accessControl.open();
        }
        catch(RuntimeException e)
        {
            throw new IllegalConfigurationException(e.getMessage(), e);
        }
        finally
        {
            if (accessControl != null)
            {
                accessControl.close();
            }
        }
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _accessControl = new DefaultAccessControl(getPath(), _broker);
    }

    @SuppressWarnings("unused")
    private void reloadAclFile()
    {
        try
        {
            DefaultAccessControl accessControl = new DefaultAccessControl(getPath(), _broker);
            accessControl.open();
            DefaultAccessControl oldAccessControl = _accessControl;
            _accessControl = accessControl;
            if(oldAccessControl != null)
            {
                oldAccessControl.close();
            }
        }
        catch(RuntimeException e)
        {
            throw new IllegalConfigurationException(e.getMessage(), e);
        }
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
        if (_accessControl != null)
        {
            _accessControl.close();
        }
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

    public AccessControl getAccessControl()
    {
        return _accessControl;
    }
}
