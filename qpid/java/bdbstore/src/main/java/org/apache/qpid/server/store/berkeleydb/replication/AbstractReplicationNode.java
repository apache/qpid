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

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.NoStatistics;

import com.sleepycat.je.Durability;

public abstract class AbstractReplicationNode implements ReplicationNode
{

    private final UUID _id;
    private final String _groupName;
    private final String _nodeName;
    private final String _hostPort;
    private final VirtualHost _virtualHost;

    public AbstractReplicationNode(String groupName, String nodeName, String hostPort, VirtualHost virtualHost)
    {
        super();
        _id = UUIDGenerator.generateReplicationNodeId(groupName, nodeName);
        _groupName = groupName;
        _nodeName = nodeName;
        _hostPort = hostPort;
        _virtualHost = virtualHost;
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public String getName()
    {
        return _nodeName;
    }

    @Override
    public String setName(String currentName, String desiredName)
            throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getDesiredState()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State setDesiredState(State currentState, State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getActualState()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addChangeListener(ConfigurationChangeListener listener)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeChangeListener(ConfigurationChangeListener listener)
    {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ConfiguredObject> T getParent(Class<T> clazz)
    {
        if (clazz == VirtualHost.class)
        {
            return (T) _virtualHost;
        }
        throw new IllegalArgumentException();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
            LifetimePolicy desired) throws IllegalStateException,
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
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ReplicationNode.AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object getAttribute(String name)
    {
        if (ReplicationNode.ID.equals(name))
        {
            return getId();
        }
        else if (ReplicationNode.NAME.equals(name))
        {
            return getName();
        }
        else if (ReplicationNode.LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        else if (ReplicationNode.TIME_TO_LIVE.equals(name))
        {
            return 0L;
        }
        else if (ReplicationNode.CREATED.equals(name))
        {
            return 0L;
        }
        else if (ReplicationNode.UPDATED.equals(name))
        {
            return 0L;
        }
        else if (ReplicationNode.DURABLE.equals(name))
        {
            return isDurable();
        }
        else if (ReplicationNode.HOST_PORT.equals(name))
        {
            return _hostPort;
        }
        else if (ReplicationNode.GROUP_NAME.equals(name))
        {
            return _groupName;
        }
        else if (ReplicationNode.TYPE.equals(name))
        {
            // TODO
            return null;
        }
        else if (ReplicationNode.STATE.equals(name))
        {
            // TODO
            return State.UNAVAILABLE;
        }
        else if (ReplicationNode.ROLE.equals(name))
        {
            // TODO
            return null;
        }
        else if (ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID.equals(name))
        {
            // TODO
            return null;
        }
        else if (ReplicationNode.HELPER_HOST_PORT.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.DURABILITY.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.COALESCING_SYNC.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.DESIGNATED_PRIMARY.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.PRIORITY.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.QUORUM_OVERRIDE.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.JOIN_TIME.equals(name))
        {
            return 0l;
        }
        else if (ReplicationNode.PARAMETERS.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.REPLICATION_PARAMETERS.equals(name))
        {
            return null;
        }
        else if (ReplicationNode.STORE_PATH.equals(name))
        {
            return null;
        }
        throw new UnsupportedOperationException("Attribute " + name + " is not supported");
    }

    @Override
    public Map<String, Object> getActualAttributes()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException,
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
        throw new UnsupportedOperationException();
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass,
            Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttributes(Map<String, Object> attributes)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    protected VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() + " [id=" + _id + ", name=" + _nodeName + "]";
    }

}
