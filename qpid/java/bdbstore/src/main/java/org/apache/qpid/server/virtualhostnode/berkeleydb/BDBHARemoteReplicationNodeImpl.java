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

import static com.sleepycat.je.rep.ReplicatedEnvironment.State.MASTER;
import static com.sleepycat.je.rep.ReplicatedEnvironment.State.REPLICA;

import java.security.AccessControlException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.je.rep.MasterStateException;
import com.sleepycat.je.rep.ReplicatedEnvironment;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.HighAvailabilityMessages;
import org.apache.qpid.server.logging.subjects.BDBHAVirtualHostNodeLogSubject;
import org.apache.qpid.server.logging.subjects.GroupLogSubject;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;

public class BDBHARemoteReplicationNodeImpl extends AbstractConfiguredObject<BDBHARemoteReplicationNodeImpl> implements BDBHARemoteReplicationNode<BDBHARemoteReplicationNodeImpl>
{
    private static final Logger LOGGER = Logger.getLogger(BDBHARemoteReplicationNodeImpl.class);

    private final ReplicatedEnvironmentFacade _replicatedEnvironmentFacade;
    private final String _address;
    private final Broker _broker;

    private volatile long _joinTime;
    private volatile long _lastTransactionId;
    private volatile String _lastReplicatedEnvironmentState = ReplicatedEnvironment.State.UNKNOWN.name();

    @ManagedAttributeField(afterSet="afterSetRole")
    private volatile String _role = ReplicatedEnvironment.State.UNKNOWN.name();

    private final AtomicReference<State> _state;
    private final boolean _isMonitor;
    private boolean _detached;
    private BDBHAVirtualHostNodeLogSubject _virtualHostNodeLogSubject;
    private GroupLogSubject _groupLogSubject;

    public BDBHARemoteReplicationNodeImpl(BDBHAVirtualHostNode<?> virtualHostNode, Map<String, Object> attributes, ReplicatedEnvironmentFacade replicatedEnvironmentFacade)
    {
        super(parentsMap(virtualHostNode), attributes);
        _broker = virtualHostNode.getParent(Broker.class);
        _address = (String)attributes.get(ADDRESS);
        _replicatedEnvironmentFacade = replicatedEnvironmentFacade;
        _state = new AtomicReference<State>(State.ACTIVE);
        _isMonitor = (Boolean)attributes.get(MONITOR);
    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public String getGroupName()
    {
        return _replicatedEnvironmentFacade.getGroupName();
    }

    @Override
    public String getAddress()
    {
        return _address;
    }

    @Override
    public String getRole()
    {
        return _lastReplicatedEnvironmentState;
    }

    @Override
    public long getJoinTime()
    {
        return _joinTime;
    }

    @Override
    public long getLastKnownReplicationTransactionId()
    {
        return _lastTransactionId;
    }

    @Override
    public boolean isMonitor()
    {
        return _isMonitor;
    }

    @Override
    public void deleted()
    {
        super.deleted();
    }


    @Override
    protected void authoriseSetAttributes(final ConfiguredObject<?> proxyForValidation,
                                          final Set<String> modifiedAttributes)
    {
        _broker.getSecurityManager().authoriseVirtualHostNode(getName(), Operation.UPDATE);
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
    public String toString()
    {
        return getClass().getSimpleName() + "[id=" + getId() + ", name=" + getName() + ", address=" + getAddress()
               + ", state=" + getState() + ", role=" + getRole() + "]";
    }

    @StateTransition(currentState = {State.ACTIVE, State.UNAVAILABLE}, desiredState = State.DELETED)
    private void doDelete()
    {
        String nodeName = getName();

        getEventLogger().message(_virtualHostNodeLogSubject, HighAvailabilityMessages.DELETED());

        try
        {
            _replicatedEnvironmentFacade.removeNodeFromGroup(nodeName);
            _state.set(State.DELETED);
            deleted();
        }
        catch(MasterStateException e)
        {
            throw new IllegalStateTransitionException("Node '" + nodeName + "' cannot be deleted when role is a master");
        }
        catch (Exception e)
        {
            throw new IllegalStateTransitionException("Unexpected exception on node '" + nodeName + "' deletion", e);
        }
    }

    protected void afterSetRole()
    {
        try
        {
            String nodeName = getName();
            getEventLogger().message(_groupLogSubject, HighAvailabilityMessages.TRANSFER_MASTER(getName(), getAddress()));

            _replicatedEnvironmentFacade.transferMasterAsynchronously(nodeName);
        }
        catch (Exception e)
        {
            throw new IllegalConfigurationException("Cannot transfer mastership to '" + getName() + "'", e);
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if (changedAttributes.contains(ROLE))
        {
            String currentRole = getRole();
            if (!REPLICA.name().equals(currentRole))
            {
                throw new IllegalArgumentException("Cannot transfer mastership when not in replica role."
                                                 + " Current role " + currentRole);
            }
            if (!MASTER.name().equals(((BDBHARemoteReplicationNode<?>)proxyForValidation).getRole()))
            {
                throw new IllegalArgumentException("Changing role to other value then " + MASTER.name() + " is unsupported");
            }
        }

        if (changedAttributes.contains(JOIN_TIME))
        {
            throw new IllegalArgumentException("Cannot change derived attribute " + JOIN_TIME);
        }

        if (changedAttributes.contains(LAST_KNOWN_REPLICATION_TRANSACTION_ID))
        {
            throw new IllegalArgumentException("Cannot change derived attribute " + LAST_KNOWN_REPLICATION_TRANSACTION_ID);
        }
    }

    void setRole(String role)
    {
        _lastReplicatedEnvironmentState = role;
        _role = role;
        updateModelStateFromRole(role);
    }

    void setJoinTime(long joinTime)
    {
        _joinTime = joinTime;
    }

    void setLastTransactionId(long lastTransactionId)
    {
        _lastTransactionId = lastTransactionId;
    }

    private void updateModelStateFromRole(final String role)
    {
        State currentState = _state.get();
        if (currentState == State.DELETED)
        {
            return;
        }

        boolean isActive = MASTER.name().equals(role) || REPLICA.name().equals(role);
        _state.compareAndSet(currentState, isActive ? State.ACTIVE : State.UNAVAILABLE);
    }

    public boolean isDetached()
    {
        return _detached;
    }

    public void setDetached(boolean detached)
    {
        this._detached = detached;
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        _virtualHostNodeLogSubject =  new BDBHAVirtualHostNodeLogSubject(getGroupName(), getName());
        _groupLogSubject = new GroupLogSubject(getGroupName());
    }

    private EventLogger getEventLogger()
    {
        return ((SystemConfig)getParent(VirtualHostNode.class).getParent(Broker.class).getParent(SystemConfig.class)).getEventLogger();
    }
}
