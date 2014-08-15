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
package org.apache.qpid.server.virtualhostnode;

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.store.DurableConfigurationStore;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractVirtualHostNode<X extends AbstractVirtualHostNode<X>> extends AbstractConfiguredObject<X> implements VirtualHostNode<X>
{

    public static final String VIRTUALHOST_BLUEPRINT_CONTEXT_VAR = "virtualhostBlueprint";
    public static final String VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR = "virtualhostBlueprintUtilised";
    private static final Logger LOGGER = Logger.getLogger(AbstractVirtualHostNode.class);

    private final Broker<?> _broker;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.UNINITIALIZED);
    private final EventLogger _eventLogger;

    private DurableConfigurationStore _durableConfigurationStore;

    private MessageStoreLogSubject _configurationStoreLogSubject;

    public AbstractVirtualHostNode(Broker<?> parent, Map<String, Object> attributes)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, parent),
              attributes);
        _broker = parent;
        SystemConfig<?> systemConfig = _broker.getParent(SystemConfig.class);
        _eventLogger = systemConfig.getEventLogger();
    }


    @Override
    public void onOpen()
    {
        super.onOpen();
        _durableConfigurationStore = createConfigurationStore();
        _configurationStoreLogSubject = new MessageStoreLogSubject(getName(), _durableConfigurationStore.getClass().getSimpleName());

    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }


    @StateTransition( currentState = {State.UNINITIALIZED, State.STOPPED, State.ERRORED }, desiredState = State.ACTIVE )
    protected void doActivate()
    {
        try
        {
            activate();
            _state.set(State.ACTIVE);
        }
        catch(RuntimeException e)
        {
            _state.set(State.ERRORED);
            if (_broker.isManagementMode())
            {
                LOGGER.warn("Failed to make " + this + " active.", e);
            }
            else
            {
                throw e;
            }
        }
    }

    @Override
    public VirtualHost<?,?,?> getVirtualHost()
    {
        Collection<VirtualHost> children = getChildren(VirtualHost.class);
        if (children.size() == 0)
        {
            return null;
        }
        else if (children.size() == 1)
        {
            return children.iterator().next();
        }
        else
        {
            throw new IllegalStateException(this + " has an unexpected number of virtualhost children, size " + children.size());
        }
    }

    @Override
    public DurableConfigurationStore getConfigurationStore()
    {
        return _durableConfigurationStore;
    }

    protected Broker<?> getBroker()
    {
        return _broker;
    }

    protected EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    protected DurableConfigurationStore getDurableConfigurationStore()
    {
        return _durableConfigurationStore;
    }

    protected MessageStoreLogSubject getConfigurationStoreLogSubject()
    {
        return _configurationStoreLogSubject;
    }

    @StateTransition( currentState = { State.ACTIVE, State.STOPPED, State.ERRORED}, desiredState = State.DELETED )
    protected void doDelete()
    {
        _state.set(State.DELETED);
        deleteVirtualHostIfExists();
        close();
        deleted();
        getConfigurationStore().onDelete();
    }

    protected void deleteVirtualHostIfExists()
    {
        VirtualHost<?, ?, ?> virtualHost = getVirtualHost();
        if (virtualHost != null)
        {
            virtualHost.delete();
        }
    }

    @StateTransition( currentState = { State.ACTIVE, State.ERRORED, State.UNINITIALIZED }, desiredState = State.STOPPED )
    protected void doStop()
    {
        stopAndSetStateTo(State.STOPPED);
    }

    protected void stopAndSetStateTo(State stoppedState)
    {
        closeChildren();
        closeConfigurationStore();
        _state.set(stoppedState);
    }

    @Override
    protected void onClose()
    {
        closeConfigurationStore();
    }

    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            _broker.getSecurityManager().authoriseVirtualHostNode(getName(), Operation.DELETE);
        }
        else
        {
            _broker.getSecurityManager().authoriseVirtualHostNode(getName(), Operation.UPDATE);
        }
    }

    @Override
    protected <C extends ConfiguredObject> void authoriseCreateChild(final Class<C> childClass,
                                                                     final Map<String, Object> attributes,
                                                                     final ConfiguredObject... otherParents)
            throws AccessControlException
    {
        if (childClass == VirtualHost.class)
        {
            _broker.getSecurityManager().authoriseVirtualHost(String.valueOf(attributes.get(VirtualHost.NAME)),
                                                              Operation.CREATE);

        }
        else
        {
            super.authoriseCreateChild(childClass, attributes, otherParents);
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        _broker.getSecurityManager().authoriseVirtualHostNode(getName(), Operation.UPDATE);
    }

    private void closeConfigurationStore()
    {
        DurableConfigurationStore configurationStore = getConfigurationStore();
        if (configurationStore != null)
        {
            configurationStore.closeConfigurationStore();
            getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CLOSE());
        }
    }

    protected abstract DurableConfigurationStore createConfigurationStore();

    protected abstract void activate();

}
