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
package org.apache.qpid.server.virtualhostnode.berkeleydb;

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
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;
import org.apache.qpid.server.store.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.store.berkeleydb.BDBHAVirtualHostFactory;
import org.apache.qpid.server.store.berkeleydb.BDBMessageStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.server.virtualhost.VirtualHostState;

import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

@ManagedObject( category = false, type = "BDB_HA" )
public class BDBHAVirtualHostNodeImpl extends AbstractConfiguredObject<BDBHAVirtualHostNodeImpl> implements BDBHAVirtualHostNode<BDBHAVirtualHostNodeImpl>
{
    private static final Logger LOGGER = Logger.getLogger(BDBHAVirtualHostNodeImpl.class);

    @ManagedAttributeField
    private Map<String, String> _environmentConfiguration;

    @ManagedAttributeField
    private String _storePath;

    @ManagedAttributeField
    private String _groupName;

    @ManagedAttributeField
    private String _helperAddress;

    @ManagedAttributeField
    private String _address;

    @ManagedAttributeField
    private String _durability;

    @ManagedAttributeField
    private boolean _coalescingSync;

    @ManagedAttributeField
    private boolean _designatedPrimary;

    @ManagedAttributeField
    private int _priority;

    @ManagedAttributeField
    private int _quorumOverride;

    @ManagedAttributeField
    private Map<String, String> _replicatedEnvironmentConfiguration;

    //TODO: remove this field
    @ManagedAttributeField
    private boolean _messageStoreProvider;

    private final AtomicReference<State> _state = new AtomicReference<State>(State.INITIALISING);
    private final Broker<?> _broker;
    private final ConfiguredObjectFactory _objectFactory;
    private final EventLogger _eventLogger;

    private MessageStoreLogSubject _configurationStoreLogSubject;
    private BDBMessageStore _durableConfigurationStore;

    @SuppressWarnings("rawtypes")
    protected BDBHAVirtualHostNodeImpl(Broker<?> broker, Map<String, Object> attributes, TaskExecutor taskExecutor)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, broker), attributes, taskExecutor);
        _broker = broker;
        _objectFactory = _broker.getParent(SystemContext.class).getObjectFactory();
        SystemContext systemContext = _broker.getParent(SystemContext.class);
        _eventLogger = systemContext.getEventLogger();

    }

    @Override
    public Map<String, String> getEnvironmentConfiguration()
    {
        return _environmentConfiguration;
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public boolean isMessageStoreProvider()
    {
        return true;
    }

    @Override
    public VirtualHost<?,?,?> getVirtualHost()
    {
        @SuppressWarnings("rawtypes")
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
    public String getGroupName()
    {
        return _groupName;
    }

    @Override
    public String getHelperAddress()
    {
        return _helperAddress;
    }

    @Override
    public String getAddress()
    {
        return _address;
    }

    @Override
    public String getDurability()
    {
        return _durability;
    }

    @Override
    public boolean isCoalescingSync()
    {
        return _coalescingSync;
    }

    @Override
    public boolean isDesignatedPrimary()
    {
        return _designatedPrimary;
    }

    @Override
    public int getPriority()
    {
        return _priority;
    }

    @Override
    public int getQuorumOverride()
    {
        return _quorumOverride;
    }

    @Override
    public Map<String, String> getReplicatedEnvironmentConfiguration()
    {
        return _replicatedEnvironmentConfiguration;
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        State  state = getState();

        if (desiredState == State.DELETED)
        {
            if (state == State.ACTIVE)
            {
                setDesiredState(state, State.STOPPED);
            }
            if (state == State.INITIALISING || state == State.STOPPED || state == State.ERRORED)
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
    public String toString()
    {
        return "BDBHAVirtualHostNodeImpl [name=" + getName() + ", storePath=" + _storePath + ", groupName=" + _groupName + ", address=" + _address
                + ", state=" + _state.get() + "]";
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents)
    {
        if(childClass == VirtualHost.class)
        {
            if ("MASTER".equals(((ReplicatedEnvironmentFacade)_durableConfigurationStore.getEnvironmentFacade()).getNodeState()))
            {
                BDBHAVirtualHostFactory virtualHostFactory = new BDBHAVirtualHostFactory();
                return (C) virtualHostFactory.create(getObjectFactory(), attributes,this);
            }
            else
            {
                ReplicaVirtualHost host = new ReplicaVirtualHost(attributes, this);
                host.create();
                return (C) host;
            }
        }
        return super.addChild(childClass, attributes, otherParents);
    }

    private void activate()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Activating virtualhost node " + this);
        }

        _durableConfigurationStore = new BDBMessageStore(new ReplicatedEnvironmentFacadeFactory());
        _configurationStoreLogSubject = new MessageStoreLogSubject(getName(), BDBMessageStore.class.getSimpleName());

        Map<String, Object> attributes = buildAttributesForStore();

        _durableConfigurationStore.openConfigurationStore(this, attributes);

        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.CREATED());
        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.STORE_LOCATION(getStorePath()));


        ReplicatedEnvironmentFacade environmentFacade = (ReplicatedEnvironmentFacade) _durableConfigurationStore.getEnvironmentFacade();
        environmentFacade.setStateChangeListener(new BDBHAMessageStoreStateChangeListener());
    }

    private void stop()
    {
        destroyVirtualHostIfExist();
        _durableConfigurationStore.closeConfigurationStore();
        _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.CLOSE());
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

        _durableConfigurationStore.onDelete();

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

        attributes.put(IS_MESSAGE_STORE_PROVIDER, true);
        return attributes;
    }

    private void onMaster()
    {
        try
        {
            destroyVirtualHostIfExist();
            _durableConfigurationStore.getEnvironmentFacade().getEnvironment().flushLog(true);

            _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.RECOVERY_START());
            VirtualHostStoreUpgraderAndRecoverer upgraderAndRecoverer = new VirtualHostStoreUpgraderAndRecoverer(this, _objectFactory);
            upgraderAndRecoverer.perform(_durableConfigurationStore);
            _eventLogger.message(_configurationStoreLogSubject, ConfigStoreMessages.RECOVERY_COMPLETE());

            VirtualHost<?,?,?>  host = getVirtualHost();

            if (host == null)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Creating new virtualhost with name : " +  getGroupName());
                }

                Map<String, Object> hostAttributes = new HashMap<String, Object>();
                hostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
                hostAttributes.put(VirtualHost.NAME, getGroupName());
                hostAttributes.put(VirtualHost.TYPE, "BDB_HA");
                host = createChild(VirtualHost.class, hostAttributes);
            }
            else
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Recovered virtualhost with name : " +  getGroupName());
                }

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
        catch (Exception e)
        {
            LOGGER.error("Failed to activate on hearing MASTER change event", e);
        }
    }

    private void onReplica()
    {
        try
        {
            destroyVirtualHostIfExist();

            Map<String, Object> hostAttributes = new HashMap<String, Object>();
            hostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
            hostAttributes.put(VirtualHost.NAME, getGroupName());
            hostAttributes.put(VirtualHost.TYPE, "BDB_HA");
            createChild(VirtualHost.class, hostAttributes);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to create a replica host", e);
        }
    }

    private void onDetached()
    {
        destroyVirtualHostIfExist();
    }

    protected void destroyVirtualHostIfExist()
    {
        VirtualHost<?,?,?> virtualHost = getVirtualHost();
        if (virtualHost!= null)
        {
            virtualHost.setDesiredState(virtualHost.getState(), State.STOPPED);
        }
    }

    private class BDBHAMessageStoreStateChangeListener implements StateChangeListener
    {
        @Override
        public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
        {
            com.sleepycat.je.rep.ReplicatedEnvironment.State state = stateChangeEvent.getState();

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Received BDB event indicating transition to state " + state);
            }

            switch (state)
            {
            case MASTER:
                onMaster();
                break;
            case REPLICA:
                onReplica();
                break;
            case DETACHED:
                LOGGER.error("BDB replicated node in detached state, therefore passivating.");
                onDetached();
                break;
            case UNKNOWN:
                LOGGER.warn("BDB replicated node in unknown state (hopefully temporarily)");
                break;
            default:
                LOGGER.error("Unexpected state change: " + state);
                throw new IllegalStateException("Unexpected state change: " + state);
            }
        }
    }

    private class ReplicaVirtualHost extends BDBHAVirtualHost
    {

        ReplicaVirtualHost(Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
        {
            super(attributes, virtualHostNode);
            setState(VirtualHostState.PASSIVE);
        }

        @Override
        public void onCreate()
        {
        }

        @Override
        public boolean setState(State currentState, State desiredState)
        {
            if (desiredState != State.STOPPED)
            {
                throw new IllegalArgumentException("Unsupported state " + desiredState);
            }
            return super.setState(currentState, desiredState);
        }
    }
}
