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

import java.io.File;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;
import org.apache.qpid.server.virtualhost.StandardVirtualHost;

public abstract class AbstractStandardVirtualHostNode<X extends AbstractStandardVirtualHostNode<X>> extends AbstractConfiguredObject<X>
                implements VirtualHostNode<X>
{
    private static final Logger LOGGER = Logger.getLogger(AbstractStandardVirtualHostNode.class);

    private final Broker<?> _broker;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.INITIALISING);
    private final EventLogger _eventLogger;

    @ManagedAttributeField
    private boolean _messageStoreProvider;

    private MessageStoreLogSubject _configurationStoreLogSubject;
    private DurableConfigurationStore _durableConfigurationStore;

    @SuppressWarnings("rawtypes")
    public AbstractStandardVirtualHostNode(Broker<?> parent, Map<String, Object> attributes, TaskExecutor taskExecutor)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, parent),
              attributes, taskExecutor);
        _broker = parent;
        SystemContext systemContext = _broker.getParent(SystemContext.class);
        _eventLogger = systemContext.getEventLogger();
    }

    @Override
    public void validate()
    {
        super.validate();
        DurableConfigurationStoreFactory durableConfigurationStoreFactory = getDurableConfigurationStoreFactory();
        Map<String, Object> storeSettings = new HashMap<String, Object>(getActualAttributes());
        storeSettings.put(DurableConfigurationStore.STORE_TYPE, durableConfigurationStoreFactory.getType());
        durableConfigurationStoreFactory.validateConfigurationStoreSettings(storeSettings);
    }

    @Override
    public void onOpen()
    {
        super.onOpen();
        DurableConfigurationStoreFactory durableConfigurationStoreFactory = getDurableConfigurationStoreFactory();
        _durableConfigurationStore = durableConfigurationStoreFactory.createDurableConfigurationStore();
        _configurationStoreLogSubject = new MessageStoreLogSubject(getName(), _durableConfigurationStore.getClass().getSimpleName());

    }

    protected abstract DurableConfigurationStoreFactory getDurableConfigurationStoreFactory();

    protected Map<String, Object> getDefaultMessageStoreSettings()
    {
        // TODO perhaps look for the MS with the default annotation and associated default.
        Map<String, Object> settings = new HashMap<String, Object>();
        settings.put(MessageStore.STORE_TYPE, "DERBY");
        settings.put(MessageStore.STORE_PATH, "${qpid.work_dir}" + File.separator + "derbystore" + File.separator + getName());
        return settings;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents)
    {
        if(childClass == VirtualHost.class)
        {
            return (C) getObjectFactory().create(VirtualHost.class, attributes, this);
        }
        return super.addChild(childClass, attributes, otherParents);
    }



    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T extends ConfiguredObject> T getParent(Class<T> clazz)
    {
        if (clazz == Broker.class)
        {
            return (T) _broker;
        }
        return super.getParent(clazz);
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

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        State state = _state.get();
        if (desiredState == State.DELETED)
        {
            if (state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.ERRORED)
            {
                if( _state.compareAndSet(state, State.DELETED))
                {
                    delete();
                    return true;
                }
            }
            else
            {
                throw new IllegalStateException("Cannot delete virtual host node in " + state + " state");
            }
        }
        else if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.STOPPED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    activate();
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to make " + this + " active.", e);
                    }
                    else
                    {
                        throw e;
                    }
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot activate virtual host node in " + state + " state");
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                stop();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop virtual host node in " + state + " state");
            }
        }
        return false;
    }


    @Override
    public boolean isMessageStoreProvider()
    {
        return _messageStoreProvider;
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

    private void activate()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Activating virtualhost node " + this);
        }

        Map<String, Object> attributes = buildAttributesForStore();

        _durableConfigurationStore.openConfigurationStore(this, attributes);

        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.CREATED());

        if (this instanceof FileBasedVirtualHostNode)
        {
            @SuppressWarnings("rawtypes")
            FileBasedVirtualHostNode fileBasedVirtualHostNode = (FileBasedVirtualHostNode) this;
            _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.STORE_LOCATION(fileBasedVirtualHostNode.getStorePath()));
        }

        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.RECOVERY_START());

        VirtualHostStoreUpgraderAndRecoverer upgrader = new VirtualHostStoreUpgraderAndRecoverer(this, getObjectFactory());
        upgrader.perform(_durableConfigurationStore);

        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.RECOVERY_COMPLETE());

        VirtualHost<?,?,?>  host = getVirtualHost();

        if (host == null)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Creating new virtualhost with name : " +  getName());
            }
            Map<String, Object> hostAttributes = new HashMap<String, Object>();
            hostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
            hostAttributes.put(VirtualHost.NAME, getName());
            hostAttributes.put(VirtualHost.TYPE, StandardVirtualHost.TYPE);
            if (!isMessageStoreProvider())
            {
                hostAttributes.put(VirtualHost.MESSAGE_STORE_SETTINGS, getDefaultMessageStoreSettings());
            }
            host = createChild(VirtualHost.class, hostAttributes);
        }
        else
        {
            final VirtualHost<?,?,?> recoveredHost = host;
            Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    recoveredHost.open();
                    return null;
                }
            });
        }

        host.setDesiredState(host.getState(), State.ACTIVE);
    }

    private Map<String, Object> buildAttributesForStore()
    {
        final Map<String, Object> attributes = new HashMap<String, Object>();
        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                for (String attributeName : getAttributeNames())
                {
                    Object value = getAttribute(attributeName);
                    attributes.put(attributeName, value);
                }
                return null;
            }
        });

        return attributes;
    }

    private void delete()
    {
        VirtualHost<?, ?, ?> virtualHost = getVirtualHost();
        if (virtualHost != null)
        {
            virtualHost.setDesiredState(virtualHost.getState(), State.DELETED);
        }
        //TODO: this needs to be called from parent
        deleted();

        // TODO Split onDelete into deleteMessageStore/deleteConfigStore
        if (_durableConfigurationStore instanceof MessageStore)
        {
            ((MessageStore)_durableConfigurationStore).onDelete();
        }

    }

    private void stop()
    {
        VirtualHost<?, ?, ?> virtualHost = getVirtualHost();
        if (virtualHost != null)
        {
            virtualHost.setDesiredState(virtualHost.getState(), State.STOPPED);
        }
        _durableConfigurationStore.closeConfigurationStore();

        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.CLOSE());
    }


}
