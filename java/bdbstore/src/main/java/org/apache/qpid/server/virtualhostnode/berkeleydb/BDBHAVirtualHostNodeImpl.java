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
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;

import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import org.apache.log4j.Logger;

import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;
import org.apache.qpid.server.store.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.store.berkeleydb.BDBMessageStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.server.virtualhost.VirtualHostState;
import org.apache.qpid.server.virtualhostnode.AbstractVirtualHostNode;

@ManagedObject( category = false, type = "BDB_HA" )
public class BDBHAVirtualHostNodeImpl extends AbstractVirtualHostNode<BDBHAVirtualHostNodeImpl> implements BDBHAVirtualHostNode<BDBHAVirtualHostNodeImpl>
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

    @ManagedObjectFactoryConstructor
    public BDBHAVirtualHostNodeImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(broker, attributes);
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
    public String toString()
    {
        return "BDBHAVirtualHostNodeImpl [id=" + getId() + ", name=" + getName() + ", storePath=" + _storePath + ", groupName=" + _groupName + ", address=" + _address
                + ", state=" + getState() + "]";
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents)
    {
        if(childClass == VirtualHost.class)
        {
            if ("MASTER".equals(((ReplicatedEnvironmentFacade)getConfigurationStore().getEnvironmentFacade()).getNodeState()))
            {
                ConfiguredObjectTypeFactory<? extends ConfiguredObject> factory =
                        getObjectFactory().getConfiguredObjectTypeFactory(VirtualHost.class.getSimpleName(), "BDB_HA");
                return (C) factory.create(getObjectFactory(), attributes, this);
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

    @Override
    public BDBMessageStore getConfigurationStore()
    {
        return (BDBMessageStore) super.getConfigurationStore();
    }

    protected DurableConfigurationStore createConfigurationStore()
    {
        return new BDBMessageStore(new ReplicatedEnvironmentFacadeFactory());
    }

    @Override
    protected void activate()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Activating virtualhost node " + this);
        }

        Map<String, Object> attributes = buildAttributesForStore();

        getConfigurationStore().openConfigurationStore(this, attributes);

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CREATED());
        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.STORE_LOCATION(getStorePath()));


        ReplicatedEnvironmentFacade environmentFacade = (ReplicatedEnvironmentFacade) getConfigurationStore().getEnvironmentFacade();
        environmentFacade.setStateChangeListener(new BDBHAMessageStoreStateChangeListener());
    }

    private void onMaster()
    {
        try
        {
            destroyVirtualHostIfExist();
            getConfigurationStore().getEnvironmentFacade().getEnvironment().flushLog(true);

            getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_START());
            VirtualHostStoreUpgraderAndRecoverer upgraderAndRecoverer = new VirtualHostStoreUpgraderAndRecoverer(this);
            upgraderAndRecoverer.perform(getConfigurationStore());
            getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_COMPLETE());

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
