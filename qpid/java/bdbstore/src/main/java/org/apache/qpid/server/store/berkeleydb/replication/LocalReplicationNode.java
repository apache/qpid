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

import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractAdapter;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ParameterizedTypeImpl;

import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;

public class LocalReplicationNode extends AbstractAdapter implements ReplicationNode
{

    private static final Durability DEFAULT_DURABILITY = new Durability(SyncPolicy.NO_SYNC, SyncPolicy.NO_SYNC,
            ReplicaAckPolicy.SIMPLE_MAJORITY);

    @SuppressWarnings("serial")
    static final Map<String, Object> DEFAULTS = new HashMap<String, Object>()
    {{
        put(DURABILITY, DEFAULT_DURABILITY.toString());
        put(COALESCING_SYNC, true);
        put(DESIGNATED_PRIMARY, false);
        //TODO: add defaults for parameters and replicatedParameters
    }};

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = new HashMap<String, Type>()
    {{
        put(ID, UUID.class);
        put(NAME, String.class);
        put(GROUP_NAME, String.class);
        put(HOST_PORT, String.class);
        put(HELPER_HOST_PORT, String.class);
        put(DURABILITY, String.class);
        put(COALESCING_SYNC, Boolean.class);
        put(DESIGNATED_PRIMARY, Boolean.class);
        put(PRIORITY, Integer.class);
        put(QUORUM_OVERRIDE, Integer.class);
        put(ROLE, String.class);
        put(JOIN_TIME, Long.class);
        put(PARAMETERS, new ParameterizedTypeImpl(Map.class, String.class, String.class));
        put(REPLICATION_PARAMETERS, new ParameterizedTypeImpl(Map.class, String.class, String.class));
        put(STORE_PATH, String.class);
    }};

    private final VirtualHost _virtualHost;

    //TODO: add state management
    public LocalReplicationNode(UUID id, Map<String, Object> attributes, VirtualHost virtualHost, TaskExecutor taskExecutor)
    {
        super(id, DEFAULTS, validateAttributes(MapValueConverter.convert(attributes, ATTRIBUTE_TYPES)), taskExecutor);
        _virtualHost = virtualHost;
        addParent(VirtualHost.class, virtualHost);
        validateAttributes(attributes);
    }

    private static Map<String, Object> validateAttributes(Map<String, Object> attributes)
    {
        if (attributes.get(NAME) == null)
        {
            throw new IllegalConfigurationException("Name is not specified");
        }
        if (attributes.get(GROUP_NAME) == null)
        {
            throw new IllegalConfigurationException("Group name is not specified");
        }
        if (attributes.get(HOST_PORT) == null)
        {
            throw new IllegalConfigurationException("Host and port attribute is not specified");
        }
        if (attributes.get(HELPER_HOST_PORT) == null)
        {
            throw new IllegalConfigurationException("Helper host and port attribute is not specified");
        }
        return attributes;
    }

    @Override
    public String getName()
    {
        return (String)getAttribute(NAME);
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
    public Object getAttribute(String attributeName)
    {
        if (ReplicationNode.ID.equals(attributeName))
        {
            return getId();
        }
        else if (ReplicationNode.LIFETIME_POLICY.equals(attributeName))
        {
            return getLifetimePolicy();
        }
        else if (ReplicationNode.DURABLE.equals(attributeName))
        {
            return isDurable();
        }
        return super.getAttribute(attributeName);
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
        throw new UnsupportedOperationException();
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
    protected boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE || desiredState == State.STOPPED)
        {
            return true;
        }
        return false;
    }

    @Override
    public boolean isLocal()
    {
        return true;
    }

}
