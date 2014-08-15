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

import java.net.InetSocketAddress;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import com.sleepycat.je.rep.util.ReplicationGroupAdmin;
import com.sleepycat.je.rep.utilint.HostPortPair;
import org.apache.log4j.Logger;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.Task;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.messages.HighAvailabilityMessages;
import org.apache.qpid.server.logging.subjects.BDBHAVirtualHostNodeLogSubject;
import org.apache.qpid.server.logging.subjects.GroupLogSubject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.VirtualHostStoreUpgraderAndRecoverer;
import org.apache.qpid.server.store.berkeleydb.BDBConfigurationStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicationGroupListener;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHostImpl;
import org.apache.qpid.server.virtualhostnode.AbstractVirtualHostNode;

@ManagedObject( category = false, type = BDBHAVirtualHostNodeImpl.VIRTUAL_HOST_NODE_TYPE )
public class BDBHAVirtualHostNodeImpl extends AbstractVirtualHostNode<BDBHAVirtualHostNodeImpl> implements
        BDBHAVirtualHostNode<BDBHAVirtualHostNodeImpl>
{
    public static final String VIRTUAL_HOST_NODE_TYPE = "BDB_HA";
    public static final String VIRTUAL_HOST_PRINCIPAL_NAME_FORMAT = "grp(/{0})/vhn(/{1})";

    /**
     * Length of time we synchronously await the a JE mutation to complete.  It is not considered an error if we exceed this timeout, although a
     * a warning will be logged.
     */
    private static final int MUTATE_JE_TIMEOUT_MS = 100;

    private static final Logger LOGGER = Logger.getLogger(BDBHAVirtualHostNodeImpl.class);

    private final AtomicReference<ReplicatedEnvironmentFacade> _environmentFacade = new AtomicReference<>();

    private final AtomicReference<ReplicatedEnvironment.State> _lastReplicatedEnvironmentState = new AtomicReference<>(ReplicatedEnvironment.State.UNKNOWN);
    private BDBHAVirtualHostNodeLogSubject _virtualHostNodeLogSubject;
    private GroupLogSubject _groupLogSubject;
    private String _virtualHostNodePrincipalName;

    @ManagedAttributeField
    private String _storePath;

    @ManagedAttributeField
    private String _groupName;

    @ManagedAttributeField
    private String _helperAddress;

    @ManagedAttributeField
    private String _address;

    @ManagedAttributeField(afterSet="postSetDesignatedPrimary")
    private boolean _designatedPrimary;

    @ManagedAttributeField(afterSet="postSetPriority")
    private int _priority;


    @ManagedAttributeField(afterSet="postSetQuorumOverride")
    private int _quorumOverride;

    @ManagedAttributeField(afterSet="postSetRole")
    private String _role;

    @ManagedAttributeField
    private String _helperNodeName;

    @ManagedObjectFactoryConstructor
    public BDBHAVirtualHostNodeImpl(Map<String, Object> attributes, Broker<?> broker)
    {
        super(broker, attributes);
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if (changedAttributes.contains(ROLE))
        {
            String currentRole = getRole();
            if (!ReplicatedEnvironment.State.REPLICA.name().equals(currentRole))
            {
                throw new IllegalStateException("Cannot transfer mastership when not a replica, current role is " + currentRole);
            }
            BDBHAVirtualHostNode<?> proposed = (BDBHAVirtualHostNode<?>)proxyForValidation;
            if (!ReplicatedEnvironment.State.MASTER.name().equals(proposed.getRole()))
            {
                throw new IllegalArgumentException("Changing role to other value then " + ReplicatedEnvironment.State.MASTER.name() + " is unsupported");
            }
        }
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
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
    public String getRole()
    {
        return _lastReplicatedEnvironmentState.get().name();
    }

    @Override
    public Long getLastKnownReplicationTransactionId()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null)
        {
            return environmentFacade.getLastKnownReplicationTransactionId();
        }
        return -1L;
    }

    @Override
    public Long getJoinTime()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null)
        {
            return environmentFacade.getJoinTime();
        }
        return -1L;
    }

    @Override
    public String getHelperNodeName()
    {
        return _helperNodeName;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Collection<? extends RemoteReplicationNode> getRemoteReplicationNodes()
    {
        Collection<RemoteReplicationNode> remoteNodes = getChildren(RemoteReplicationNode.class);
        return (Collection<? extends RemoteReplicationNode>)remoteNodes;
    }

    @Override
    public String toString()
    {
        return "BDBHAVirtualHostNodeImpl [id=" + getId() + ", name=" + getName() + ", storePath=" + _storePath + ", groupName=" + _groupName + ", address=" + _address
                + ", state=" + getState() + ", priority=" + _priority + ", designatedPrimary=" + _designatedPrimary + ", quorumOverride=" + _quorumOverride + "]";
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

    @Override
    public BDBConfigurationStore getConfigurationStore()
    {
        return (BDBConfigurationStore) super.getConfigurationStore();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        if (!isFirstNodeInAGroup())
        {
            try
            {
                ReplicatedEnvironmentFacade.connectToHelperNodeAndCheckPermittedHosts(getName(), getAddress(), getGroupName(), getHelperNodeName(), getHelperAddress());
            }
            catch(IllegalConfigurationException e)
            {
                deleted();
                throw e;
            }
        }

        getEventLogger().message(getVirtualHostNodeLogSubject(), HighAvailabilityMessages.CREATED());
    }

    protected ReplicatedEnvironmentFacade getReplicatedEnvironmentFacade()
    {
        return _environmentFacade.get();
    }

    @Override
    protected DurableConfigurationStore createConfigurationStore()
    {
        return new BDBConfigurationStore(new ReplicatedEnvironmentFacadeFactory(), VirtualHost.class);
    }

    @Override
    protected void activate()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Activating virtualhost node " + this);
        }

        getConfigurationStore().openConfigurationStore(this, false);

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CREATED());
        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.STORE_LOCATION(getStorePath()));

        ReplicatedEnvironmentFacade environmentFacade = (ReplicatedEnvironmentFacade) getConfigurationStore().getEnvironmentFacade();
        if (environmentFacade == null)
        {
            throw new IllegalStateException("Environment facade is not created");
        }

        if (_environmentFacade.compareAndSet(null, environmentFacade))
        {
            environmentFacade.setStateChangeListener(new EnvironmentStateChangeListener());
            environmentFacade.setReplicationGroupListener(new RemoteNodesDiscoverer());
        }
    }

    @StateTransition( currentState = { State.UNINITIALIZED, State.ACTIVE, State.ERRORED }, desiredState = State.STOPPED )
    protected void doStop()
    {
        try
        {
            super.doStop();
        }
        finally
        {
            closeEnvironment();

            // closing the environment does not cause a state change.  Adjust the role
            // so that our observers will see DETACHED rather than our previous role in the group.
            ReplicatedEnvironment.State detached = ReplicatedEnvironment.State.DETACHED;
            _lastReplicatedEnvironmentState.set(detached);
            attributeSet(ROLE, _role, detached);
        }
    }

    private void closeEnvironment()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null && _environmentFacade.compareAndSet(environmentFacade, null))
        {
            environmentFacade.close();
        }
    }

    @StateTransition( currentState = { State.ACTIVE, State.STOPPED, State.ERRORED}, desiredState = State.DELETED )
    protected void doDelete()
    {
        Set<InetSocketAddress> helpers = getRemoteNodeAddresses();
        super.doDelete();
        getEventLogger().message(getVirtualHostNodeLogSubject(), HighAvailabilityMessages.DELETED());
        if (getState() == State.DELETED && !helpers.isEmpty())
        {
            try
            {
                new ReplicationGroupAdmin(_groupName, helpers).removeMember(getName());
            }
            catch(DatabaseException e)
            {
                LOGGER.warn("The deletion of node " + this + " on remote nodes failed due to: " + e.getMessage()
                        + ". To finish deletion a removal of the node from any of remote nodes (" + helpers + ") is required.");
            }
        }
    }

    @Override
    protected void deleteVirtualHostIfExists()
    {
        ReplicatedEnvironmentFacade replicatedEnvironmentFacade = getReplicatedEnvironmentFacade();
        if (replicatedEnvironmentFacade != null && replicatedEnvironmentFacade.isMaster()
                && replicatedEnvironmentFacade.getNumberOfElectableGroupMembers() == 1)
        {
            super.deleteVirtualHostIfExists();
        }
        else
        {
            closeVirtualHostIfExist();
        }
    }

    private Set<InetSocketAddress> getRemoteNodeAddresses()
    {
        Set<InetSocketAddress> helpers = new HashSet<InetSocketAddress>();
        @SuppressWarnings("rawtypes")
        Collection<? extends RemoteReplicationNode> remoteNodes = getRemoteReplicationNodes();
        for (RemoteReplicationNode<?> node : remoteNodes)
        {
            BDBHARemoteReplicationNode<?> bdbHaRemoteReplicationNode = (BDBHARemoteReplicationNode<?>)node;
            String remoteNodeAddress = bdbHaRemoteReplicationNode.getAddress();
            helpers.add(HostPortPair.getSocket(remoteNodeAddress));
        }
        return helpers;
    }

    @Override
    protected void onClose()
    {
        try
        {
            super.onClose();
        }
        finally
        {
            closeEnvironment();
        }
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        _virtualHostNodeLogSubject =  new BDBHAVirtualHostNodeLogSubject(getGroupName(), getName());
        _groupLogSubject = new GroupLogSubject(getGroupName());
        _virtualHostNodePrincipalName = MessageFormat.format(VIRTUAL_HOST_PRINCIPAL_NAME_FORMAT, getGroupName(), getName());
    }

    private void onMaster()
    {
        try
        {
            closeVirtualHostIfExist();

            getConfigurationStore().upgradeStoreStructure();

            getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_START());
            VirtualHostStoreUpgraderAndRecoverer upgraderAndRecoverer = new VirtualHostStoreUpgraderAndRecoverer(this);
            upgraderAndRecoverer.perform(getConfigurationStore());
            getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.RECOVERY_COMPLETE());

            VirtualHost<?,?,?>  host = getVirtualHost();

            if (host == null)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Creating new virtualhost with name : " + getGroupName());
                }

                boolean hasBlueprint = getContextKeys(false).contains(VIRTUALHOST_BLUEPRINT_CONTEXT_VAR);
                boolean blueprintUtilised = getContext().containsKey(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR)
                                            && Boolean.parseBoolean(String.valueOf(getContext().get(
                        VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR)));

                Map<String, Object> hostAttributes = new HashMap<>();
                if (hasBlueprint && !blueprintUtilised)
                {
                    Map<String, Object> virtualhostBlueprint =
                            getContextValue(Map.class, VIRTUALHOST_BLUEPRINT_CONTEXT_VAR);

                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Using virtualhost blueprint " + virtualhostBlueprint);
                    }

                    hostAttributes.putAll(virtualhostBlueprint);


                }

                hostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
                hostAttributes.put(VirtualHost.NAME, getGroupName());
                hostAttributes.put(VirtualHost.TYPE, BDBHAVirtualHostImpl.VIRTUAL_HOST_TYPE);
                host = createChild(VirtualHost.class, hostAttributes);

                if (hasBlueprint && !blueprintUtilised)
                {
                    // Update the context with the utilised flag
                    Map<String, String> actualContext = (Map<String, String>) getActualAttributes().get(CONTEXT);
                    Map<String, String> context = new HashMap<>(actualContext);
                    context.put(VIRTUALHOST_BLUEPRINT_UTILISED_CONTEXT_VAR, Boolean.TRUE.toString());
                    setAttribute(CONTEXT, getContext(), context);
                }
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

        }
        catch (Exception e)
        {
            LOGGER.error("Failed to activate on hearing MASTER change event", e);
        }
    }

    private void onReplica()
    {
        createReplicaVirtualHost();
    }


    private void onDetached()
    {
        createReplicaVirtualHost();
    }

    private void createReplicaVirtualHost()
    {
        try
        {
            closeVirtualHostIfExist();

            Map<String, Object> hostAttributes = new HashMap<>();
            hostAttributes.put(VirtualHost.MODEL_VERSION, BrokerModel.MODEL_VERSION);
            hostAttributes.put(VirtualHost.NAME, getGroupName());
            hostAttributes.put(VirtualHost.TYPE, "BDB_HA_REPLICA");
            createChild(VirtualHost.class, hostAttributes);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to create a replica virtualhost", e);
        }
    }

    protected void closeVirtualHostIfExist()
    {
        VirtualHost<?,?,?> virtualHost = getVirtualHost();
        if (virtualHost!= null)
        {
            virtualHost.close();
            childRemoved(virtualHost);
        }
    }

    private class EnvironmentStateChangeListener implements StateChangeListener
    {
        @Override
        public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
        {
            com.sleepycat.je.rep.ReplicatedEnvironment.State state = stateChangeEvent.getState();

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Received BDB event indicating transition to state " + state);
            }
            String previousRole = getRole();
            try
            {
                switch (state)
                {
                    case MASTER:
                        onMaster();
                        break;
                    case REPLICA:
                        onReplica();
                        break;
                    case DETACHED:
                        onDetached();
                        break;
                    case UNKNOWN:
                        break;
                    default:
                        LOGGER.error("Unexpected state change: " + state);
                }
            }
            finally
            {
                _lastReplicatedEnvironmentState.set(state);
                attributeSet(ROLE, _role, state.name());
                getEventLogger().message(getGroupLogSubject(),
                        HighAvailabilityMessages.ROLE_CHANGED(getName(), getAddress(), previousRole, state.name()));
            }
        }
    }

    // used as post action by field _priority
    @SuppressWarnings("unused")
    private void postSetPriority()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null)
        {
            try
            {
                environmentFacade.setPriority(_priority).get(MUTATE_JE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                getEventLogger().message(getVirtualHostNodeLogSubject(),
                        HighAvailabilityMessages.PRIORITY_CHANGED(String.valueOf(_priority)));
            }
            catch (TimeoutException e)
            {
                LOGGER.warn("Change node priority did not complete within " + MUTATE_JE_TIMEOUT_MS + "ms. New value " + _priority + " will become effective once the JE task thread is free.");
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException e)
            {
                throw new ServerScopedRuntimeException("Failed to set priority node to value " + _priority + " on " + this, e);
            }
        }
    }

    // used as post action by field _designatedPrimary
    @SuppressWarnings("unused")
    private void postSetDesignatedPrimary()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null)
        {
            try
            {
                environmentFacade.setDesignatedPrimary(_designatedPrimary).get(MUTATE_JE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                getEventLogger().message(getVirtualHostNodeLogSubject(),
                        HighAvailabilityMessages.DESIGNATED_PRIMARY_CHANGED(String.valueOf(_designatedPrimary)));
            }
            catch (TimeoutException e)
            {
                LOGGER.warn("Change designated primary did not complete within " + MUTATE_JE_TIMEOUT_MS + "ms. New value " + _designatedPrimary + " will become effective once the JE task thread is free.");
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException e)
            {
                throw new ServerScopedRuntimeException("Failed to set designated primary to value " + _designatedPrimary + " on " + this, e);
            }
        }
    }

    // used as post action by field _quorumOverride
    @SuppressWarnings("unused")
    private void postSetQuorumOverride()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null)
        {
            try
            {
                environmentFacade.setElectableGroupSizeOverride(_quorumOverride).get(MUTATE_JE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                getEventLogger().message(getVirtualHostNodeLogSubject(),
                        HighAvailabilityMessages.QUORUM_OVERRIDE_CHANGED(String.valueOf(_quorumOverride)));
            }
            catch (TimeoutException e)
            {
                LOGGER.warn("Change quorum override did not complete within " + MUTATE_JE_TIMEOUT_MS + "ms. New value " + _quorumOverride + " will become effective once the JE task thread is free.");
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException e)
            {
                throw new ServerScopedRuntimeException("Failed to set quorum override to value " + _quorumOverride + " on " + this, e);
            }
        }
    }

    // used as post action by field _role
    @SuppressWarnings("unused")
    private void postSetRole()
    {
        ReplicatedEnvironmentFacade environmentFacade = getReplicatedEnvironmentFacade();
        if (environmentFacade != null)
        {
            try
            {
                getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.TRANSFER_MASTER(getName(), getAddress()));
                environmentFacade.transferMasterToSelfAsynchronously().get(MUTATE_JE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException e)
            {
                LOGGER.warn("Transfer master did not complete within " + MUTATE_JE_TIMEOUT_MS + "ms. Node may still be elected master at a later time.");
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            catch (ExecutionException e)
            {
                throw new ServerScopedRuntimeException("Failed to transfer master to " + this, e);
            }
        }
        else
        {
            // Ignored
        }
    }

    private boolean isFirstNodeInAGroup()
    {
        return getAddress().equals(getHelperAddress());
    }

    BDBHAVirtualHostNodeLogSubject getVirtualHostNodeLogSubject()
    {
        return _virtualHostNodeLogSubject;
    }

    GroupLogSubject getGroupLogSubject()
    {
        return _groupLogSubject;
    }

    private class RemoteNodesDiscoverer implements ReplicationGroupListener
    {
        @Override
        public void onReplicationNodeAddedToGroup(final ReplicationNode node)
        {
            getTaskExecutor().submit(new VirtualHostNodeGroupTask()
            {
                @Override
                public void perform()
                {
                    addRemoteReplicationNode(node);
                }
            });
        }

        private void addRemoteReplicationNode(ReplicationNode node)
        {
            BDBHARemoteReplicationNodeImpl remoteNode = new BDBHARemoteReplicationNodeImpl(BDBHAVirtualHostNodeImpl.this, nodeToAttributes(node), getReplicatedEnvironmentFacade());
            remoteNode.create();
            childAdded(remoteNode);
            getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.ADDED(remoteNode.getName(), remoteNode.getAddress()));
        }

        @Override
        public void onReplicationNodeRecovered(final ReplicationNode node)
        {
            getTaskExecutor().submit(new VirtualHostNodeGroupTask()
            {
                @Override
                public void perform()
                {
                    recoverRemoteReplicationNode(node);
                }
            });
        }

        private void recoverRemoteReplicationNode(ReplicationNode node)
        {
            BDBHARemoteReplicationNodeImpl remoteNode = new BDBHARemoteReplicationNodeImpl(BDBHAVirtualHostNodeImpl.this, nodeToAttributes(node), getReplicatedEnvironmentFacade());
            remoteNode.registerWithParents();
            remoteNode.open();

            getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.JOINED(remoteNode.getName(), remoteNode.getAddress()));
        }

        @Override
        public void onReplicationNodeRemovedFromGroup(final ReplicationNode node)
        {
            getTaskExecutor().submit(new VirtualHostNodeGroupTask()
            {
                @Override
                public void perform()
                {
                    removeRemoteReplicationNode(node);
                }
            });
        }

        private void removeRemoteReplicationNode(ReplicationNode node)
        {
            BDBHARemoteReplicationNodeImpl remoteNode = getChildByName(BDBHARemoteReplicationNodeImpl.class, node.getName());
            if (remoteNode != null)
            {
                remoteNode.deleted();
                getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.REMOVED(remoteNode.getName(), remoteNode.getAddress()));
            }
        }

        @Override
        public void onNodeState(final ReplicationNode node, final NodeState nodeState)
        {
            Subject.doAs(SecurityManager.getSystemTaskSubject(_virtualHostNodePrincipalName), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    processNodeState(node, nodeState);
                    return null;
                }
            });
        }

        private void processNodeState(ReplicationNode node, NodeState nodeState)
        {
            BDBHARemoteReplicationNodeImpl remoteNode = getChildByName(BDBHARemoteReplicationNodeImpl.class, node.getName());
            if (remoteNode != null)
            {
                String currentRole = remoteNode.getRole();
                if (nodeState == null)
                {
                    remoteNode.setRole(ReplicatedEnvironment.State.UNKNOWN.name());
                    remoteNode.setLastTransactionId(-1);
                    if (!remoteNode.isDetached())
                    {
                        getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.LEFT(remoteNode.getName(), remoteNode.getAddress()));
                        remoteNode.setDetached(true);
                    }
                }
                else
                {
                    remoteNode.setJoinTime(nodeState.getJoinTime());
                    remoteNode.setLastTransactionId(nodeState.getCurrentTxnEndVLSN());
                    remoteNode.setRole(nodeState.getNodeState().name());
                    if (remoteNode.isDetached())
                    {
                        getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.JOINED(remoteNode.getName(), remoteNode.getAddress()));
                        remoteNode.setDetached(false);
                    }
                }

                String newRole = remoteNode.getRole();
                if (!newRole.equals(currentRole))
                {
                    getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.ROLE_CHANGED(remoteNode.getName(), remoteNode.getAddress(), currentRole, newRole));
                }
            }
        }

        @Override
        public void onIntruderNode(final ReplicationNode node)
        {
            Subject.doAs(SecurityManager.getSystemTaskSubject(_virtualHostNodePrincipalName), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    processIntruderNode(node);
                    return null;
                }
            });
        }

        private void processIntruderNode(ReplicationNode node)
        {
            String hostAndPort = node.getHostName() + ":" + node.getPort();
            getEventLogger().message(getGroupLogSubject(), HighAvailabilityMessages.INTRUDER_DETECTED(node.getName(), hostAndPort));

            boolean inManagementMode = getParent(Broker.class).isManagementMode();
            if (inManagementMode)
            {
                BDBHARemoteReplicationNodeImpl remoteNode = getChildByName(BDBHARemoteReplicationNodeImpl.class, node.getName());
                if (remoteNode == null)
                {
                    addRemoteReplicationNode(node);
                }
            }
            else
            {
                LOGGER.error(String.format("Intruder node '%s' from '%s' is detected. Stopping down virtual host node '%s'",
                        node.getName(), hostAndPort, BDBHAVirtualHostNodeImpl.this.toString() ));

                getTaskExecutor().submit(new Task<Void>()
                {
                    @Override
                    public Void execute()
                    {
                        State state = getState();

                        if (state == State.ACTIVE)
                        {
                            try
                            {
                                stopAndSetStateTo(State.ERRORED);
                            }
                            finally
                            {
                                closeEnvironment();
                            }
                            notifyStateChanged(state, State.ERRORED);
                        }
                        return null;
                    }
                });
            }
        }

        @Override
        public void onNoMajority()
        {
            getEventLogger().message(getVirtualHostNodeLogSubject(), HighAvailabilityMessages.QUORUM_LOST());
        }

        private Map<String, Object> nodeToAttributes(ReplicationNode replicationNode)
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(ConfiguredObject.NAME, replicationNode.getName());
            attributes.put(ConfiguredObject.DURABLE, false);
            attributes.put(BDBHARemoteReplicationNode.ADDRESS, replicationNode.getHostName() + ":" + replicationNode.getPort());
            attributes.put(BDBHARemoteReplicationNode.MONITOR, replicationNode.getType() == NodeType.MONITOR);
            return attributes;
        }
    }

    private abstract class VirtualHostNodeGroupTask implements Task<Void>
    {
        @Override
        public Void execute()
        {
            return Subject.doAs(SecurityManager.getSystemTaskSubject(_virtualHostNodePrincipalName), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    perform();
                    return null;
                }
            });
        }

        abstract void perform();
    }

}
