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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractAdapter;
import org.apache.qpid.server.model.adapter.NoStatistics;

import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.util.DbPing;
import com.sleepycat.je.rep.utilint.ServiceDispatcher.ServiceConnectFailedException;

/**
 * Represents a remote replication node in a BDB group.
 */
public class RemoteReplicationNode extends AbstractAdapter implements ReplicationNode
{
    private static final Logger LOGGER = Logger.getLogger(RemoteReplicationNode.class);

    private final com.sleepycat.je.rep.ReplicationNode _replicationNode;
    private final VirtualHost _virtualHost;
    private final String _hostPort;
    private final String _groupName;

    private volatile String _role;
    private volatile long _joinTime;
    private volatile long _lastTransactionId;

    public RemoteReplicationNode(com.sleepycat.je.rep.ReplicationNode replicationNode, String groupName, VirtualHost virtualHost, TaskExecutor taskExecutor)
    {
        super(UUIDGenerator.generateReplicationNodeId(groupName, replicationNode.getName()), null, null, taskExecutor);
        addParent(VirtualHost.class, virtualHost);
        _groupName = groupName;
        _hostPort = replicationNode.getHostName() + ":" + replicationNode.getPort();
        _replicationNode = replicationNode;
        _virtualHost = virtualHost;
    }

    @Override
    public boolean isLocal()
    {
        return false;
    }

    @Override
    public String getName()
    {
        return (String)getAttribute(NAME);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getActualState()
    {
        return State.UNAVAILABLE;
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
    public long getTimeToLive()
    {
        return 0;
    }

    @Override
    public long setTimeToLive(long expected, long desired) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.STOPPED)
        {
            return true;
        }
        else
        {
            return false;
        }
    }


    @Override
    public Object getAttribute(String name)
    {
        if (ReplicationNode.ID.equals(name))
        {
            return getId();
        }
        else if (ReplicationNode.LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        else if (ReplicationNode.DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(STATE.equals(name))
        {
            return getActualState();
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            return getLifetimePolicy();
        }
        else if (ROLE.equals(name))
        {
            return _role;
        }
        else if (JOIN_TIME.equals(name))
        {
            return _joinTime;
        }
        else if (LAST_KNOWN_REPLICATION_TRANSACTION_ID.equals(name))
        {
            return _lastTransactionId;
        }
        else if (NAME.equals(name))
        {
            return _replicationNode.getName();
        }
        else if (GROUP_NAME.equals(name))
        {
            return _groupName;
        }
        else if (HOST_PORT.equals(name))
        {
            return _hostPort;
        }
        return super.getAttribute(name);
    }

    public void updateNodeState()
    {
        Long monitorTimeout = (Long)_virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_TIMEOUT);
        DbPing ping = new DbPing(_replicationNode, _groupName, monitorTimeout.intValue());
        String oldRole = _role;
        long oldJoinTime = _joinTime;
        long oldTransactionId = _lastTransactionId;

        try
        {
            NodeState state = ping.getNodeState();
            _role = state.getNodeState().name();
            _joinTime = state.getJoinTime();
            _lastTransactionId = state.getCurrentTxnEndVLSN();
        }
        catch (IOException e)
        {
            _role = com.sleepycat.je.rep.ReplicatedEnvironment.State.UNKNOWN.name();
            //LOGGER.warn("Cannot connect to node " + _replicationNode.getName() + " from " + _groupName, e);
        }
        catch (ServiceConnectFailedException e)
        {
            _role = com.sleepycat.je.rep.ReplicatedEnvironment.State.UNKNOWN.name();
            //LOGGER.warn("Cannot retrieve the node details for node " + _replicationNode.getName() + " from " + _groupName, e);
        }

        if (!_role.equals(oldRole))
        {
            attributeSet(ROLE, oldRole, _role);
        }

        if (_joinTime != oldJoinTime)
        {
            attributeSet(JOIN_TIME, oldJoinTime, _joinTime);
        }

        if (_lastTransactionId != oldTransactionId)
        {
            attributeSet(LAST_KNOWN_REPLICATION_TRANSACTION_ID, oldTransactionId, _lastTransactionId);
        }
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ReplicationNode.AVAILABLE_ATTRIBUTES;
    }
}
