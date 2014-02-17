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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractAdapter;
import org.apache.qpid.server.model.adapter.NoStatistics;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ParameterizedTypeImpl;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.rep.ReplicatedEnvironment;

public class LocalReplicationNode extends AbstractAdapter implements ReplicationNode, ReplicatedEnvironmentConfiguration
{
    private static final Logger LOGGER = Logger.getLogger(LocalReplicationNode.class);

    private static final Durability DEFAULT_DURABILITY = new Durability(SyncPolicy.NO_SYNC, SyncPolicy.NO_SYNC,
            ReplicaAckPolicy.SIMPLE_MAJORITY);
    static final boolean DEFAULT_DESIGNATED_PRIMARY = false;
    static final int DEFAULT_PRIORITY = 1;
    static final int DEFAULT_QUORUM_OVERRIDE = 0;
    static final boolean DEFAULT_COALESCING_SYNC = true;

    @SuppressWarnings("serial")
    static final Map<String, Object> DEFAULTS = new HashMap<String, Object>()
    {{
        put(DURABILITY, DEFAULT_DURABILITY.toString());
        put(COALESCING_SYNC, DEFAULT_COALESCING_SYNC);
        put(DESIGNATED_PRIMARY, DEFAULT_DESIGNATED_PRIMARY);
        put(PRIORITY, DEFAULT_PRIORITY);
        put(QUORUM_OVERRIDE, DEFAULT_QUORUM_OVERRIDE);
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
        put(LAST_KNOWN_REPLICATION_TRANSACTION_ID, Long.class);
        put(DESIRED_STATE, State.class);
    }};

    static final String[] IMMUTABLE_ATTRIBUTES = {ReplicationNode.GROUP_NAME, ReplicationNode.HELPER_HOST_PORT,
        ReplicationNode.HOST_PORT, ReplicationNode.COALESCING_SYNC, ReplicationNode.DURABILITY,
        ReplicationNode.JOIN_TIME, ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID, ReplicationNode.NAME,
        ReplicationNode.STORE_PATH, ReplicationNode.PARAMETERS, ReplicationNode.REPLICATION_PARAMETERS};

    private final VirtualHost _virtualHost;
    private final AtomicReference<State> _state;
    private final NodeReplicatedEnvironmentFacadeFactory _factory;
    private volatile ReplicatedEnvironmentFacade _replicatedEnvironmentFacade;

    public LocalReplicationNode(UUID id, Map<String, Object> attributes, VirtualHost virtualHost, TaskExecutor taskExecutor, NodeReplicatedEnvironmentFacadeFactory factory)
    {
        super(id, DEFAULTS, validateAttributes(MapValueConverter.convert(attributes, ATTRIBUTE_TYPES)), taskExecutor);
        _virtualHost = virtualHost;
        addParent(VirtualHost.class, virtualHost);
        validateAttributes(attributes);
        _state = new AtomicReference<State>(State.INITIALISING);
        _factory = factory;
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
        Object storePath = attributes.get(STORE_PATH);
        if (storePath == null || storePath.equals(""))
        {
            throw new IllegalConfigurationException("Store path is not specified for the replication node");
        }
        String durability = (String)MapValueConverter.getStringAttribute(ReplicationNode.DURABILITY, attributes, DEFAULT_DURABILITY.toString());
        Boolean coalescingSync = MapValueConverter.getBooleanAttribute(ReplicationNode.COALESCING_SYNC, attributes, DEFAULT_COALESCING_SYNC);

        if (coalescingSync && Durability.parse(durability).getLocalSync() == SyncPolicy.SYNC)
        {
            throw new IllegalConfigurationException("Coalescing sync cannot be used with master sync policy " + SyncPolicy.SYNC
                    + "! Please set highAvailability.coalescingSync to false in store configuration.");
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
    public State getActualState()
    {
        return _state.get();
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
        else if(STATE.equals(attributeName))
        {
            return getActualState();
        }
        else if(TIME_TO_LIVE.equals(attributeName))
        {
            return getLifetimePolicy();
        }
        if (_replicatedEnvironmentFacade != null)
        {
            try
            {
                if(ROLE.equals(attributeName))
                {
                    return _replicatedEnvironmentFacade.getNodeState();
                }
                else if(JOIN_TIME.equals(attributeName))
                {
                    return _replicatedEnvironmentFacade.getJoinTime();
                }
                else if(LAST_KNOWN_REPLICATION_TRANSACTION_ID.equals(attributeName))
                {
                    return _replicatedEnvironmentFacade.getLastKnownReplicationTransactionId();
                }
                else if(QUORUM_OVERRIDE.equals(attributeName))
                {
                    return _replicatedEnvironmentFacade.getElectableGroupSizeOverride();
                }
                else if(DESIGNATED_PRIMARY.equals(attributeName))
                {
                    return _replicatedEnvironmentFacade.isDesignatedPrimary();
                }
                else if(PRIORITY.equals(attributeName))
                {
                    return _replicatedEnvironmentFacade.getPriority();
                }
            }
            catch(IllegalStateException e)
            {
                // ignore, as attribute value will be returned from actual/default attribute maps if present
                LOGGER.debug("Exception on retrieving attribute from environment", e);
            }
            catch(DatabaseException e)
            {
                // ignore, as attribute value will be returned from actual/default attribute maps if present
                LOGGER.debug("Exception on retrieving attribute from environment", e);
            }
        }
        return super.getAttribute(attributeName);
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
    public boolean changeAttribute(final String name, final Object expected, final Object desired)
    {
        updateReplicatedEnvironmentFacade(name, desired);
        if (ROLE.equals(name))
        {
            return true;
        }
        else if (DESIRED_STATE.equals(name))
        {
            return changeDesiredStateAttribute(expected, desired);
        }
        else
        {
            return super.changeAttribute(name, expected, desired);
        }
    }

    private boolean changeDesiredStateAttribute(Object expected, Object desired)
    {
        State result = setDesiredState((State)expected, (State)desired);
        if (result != desired)
        {
            throw new IllegalStateException("State has not been changed from " + expected + " to " + desired);
        }
        return super.changeAttribute(DESIRED_STATE, expected, desired);
    }

    @Override
    public void changeAttributes(Map<String, Object> attributes)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        Map<String, Object> convertedAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);

        checkWhetherImmutableAttributeChanged(convertedAttributes);

        super.changeAttributes(convertedAttributes);
    }

    private void updateReplicatedEnvironmentFacade(String attributeName, Object attributeValue)
    {
        if (_replicatedEnvironmentFacade != null)
        {
            if (PRIORITY.equals(attributeName))
            {
                int priority = (Integer)attributeValue;
                try
                {
                    _replicatedEnvironmentFacade.setPriority(priority);
                }
                catch(Exception e)
                {
                    throw new IllegalConfigurationException("Cannot set attribute " + PRIORITY + " to " + priority, e);
                }
            }

            if (DESIGNATED_PRIMARY.equals(attributeName))
            {
                boolean designatedPrimary = (Boolean)attributeValue;
                try
                {
                    _replicatedEnvironmentFacade.setDesignatedPrimary(designatedPrimary);
                }
                catch(Exception e)
                {
                    throw new IllegalConfigurationException("Cannot set attribute '" + DESIGNATED_PRIMARY + "' to " + designatedPrimary, e);
                }
            }

            if (QUORUM_OVERRIDE.equals(attributeName))
            {
                int quorumOverride = (Integer)attributeValue;
                try
                {
                    _replicatedEnvironmentFacade.setElectableGroupSizeOverride(quorumOverride);
                }
                catch(Exception e)
                {
                    throw new IllegalConfigurationException("Cannot set attribute '" + QUORUM_OVERRIDE + "' to " + quorumOverride, e);
                }
            }
        }

        if (ROLE.equals(attributeName))
        {
            String currentRole = (String)getAttribute(ROLE);
            if (!ReplicatedEnvironment.State.REPLICA.name().equals(currentRole))
            {
                throw new IllegalConfigurationException("Cannot transfer mastership when not a replica");
            }

            // we do not want to write role into the store
            String role  = (String)attributeValue;

            if (ReplicatedEnvironment.State.MASTER.name().equals(role) )
            {
                try
                {
                    _replicatedEnvironmentFacade.transferMasterToSelfAsynchronously();
                }
                catch(Exception e)
                {
                    throw new IllegalConfigurationException("Cannot transfer mastership", e);
                }
            }
            else
            {
                throw new IllegalConfigurationException("Changing role to other value then " + ReplicatedEnvironment.State.MASTER.name() + " is unsupported");
            }
        }
    }

    private void checkWhetherImmutableAttributeChanged(Map<String, Object> convertedAttributes)
    {
        for (int i = 0; i < IMMUTABLE_ATTRIBUTES.length; i++)
        {
            String attributeName = IMMUTABLE_ATTRIBUTES[i];
            if (convertedAttributes.containsKey(attributeName))
            {
                Object newValue = convertedAttributes.get(attributeName);
                Object currentValue = getAttribute(attributeName);
                if (currentValue == null)
                {
                    if (newValue != null)
                    {
                        throw new IllegalConfigurationException("Cannot change value of immutable attribute " + attributeName);
                    }
                }
                else
                {
                    if (!currentValue.equals(newValue))
                    {
                        throw new IllegalConfigurationException("Cannot change value of immutable attribute " + attributeName);
                    }
                }
            }
        }
    }

    protected VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        switch (desiredState)
        {
        case ACTIVE:
            if (_state.compareAndSet(State.INITIALISING, State.ACTIVE) || _state.compareAndSet(State.STOPPED, State.ACTIVE))
            {
                _replicatedEnvironmentFacade = _factory.createReplicatedEnvironmentFacade(this, new DefaultRemoteReplicationNodeFactory(_virtualHost));
                return true;
            }
        //TODO: Should we use UNAVAILABLE state instead of STOPPED to to stop the node
        //      When node is stopped the corresponding remote node will have UNAVAILABLE state...
        //      Alternatively, on DBPing failure, we can display the remote node state as STOPPED
        case STOPPED:
            if (_state.compareAndSet(State.ACTIVE, State.STOPPED))
            {
                if (_replicatedEnvironmentFacade !=null)
                {
                    _replicatedEnvironmentFacade.close();
                }
                return true;
            }
        case DELETED:
            if (getActualState() == State.ACTIVE)
            {
                setDesiredState(State.ACTIVE, State.STOPPED);
            }

            if (_state.compareAndSet(State.INITIALISING, State.DELETED) || _state.compareAndSet(State.ERRORED, State.DELETED)
                    || _state.compareAndSet(State.STOPPED, State.DELETED))
            {
                return true;
            }
        case INITIALISING:
        case UNAVAILABLE:
        case ERRORED:
        case QUIESCED:
        default:
            if (getActualState() == desiredState)
            {
                return false;
            }
            else
            {
                throw new IllegalStateTransitionException("Cannot transit into desired state " + desiredState + " from "
                        + currentState);
            }

        }
    }

    @Override
    public boolean isLocal()
    {
        return true;
    }

    private Object getActualAttribute(String attributeName)
    {
        return super.getAttribute(attributeName);
    }

    ReplicatedEnvironmentFacade getReplicatedEnvironmentFacade()
    {
        return _replicatedEnvironmentFacade;
    }

    @Override
    public String getGroupName()
    {
        return (String)getAttribute(GROUP_NAME);
    }

    @Override
    public String getHostPort()
    {
        return (String)getAttribute(HOST_PORT);
    }

    @Override
    public String getHelperHostPort()
    {
        return (String)getAttribute(HELPER_HOST_PORT);
    }

    @Override
    public String getDurability()
    {
        return (String)getActualAttribute(DURABILITY);
    }

    @Override
    public boolean isCoalescingSync()
    {
        return (Boolean)getActualAttribute(COALESCING_SYNC);
    }

    @Override
    public boolean isDesignatedPrimary()
    {
        return (Boolean)getActualAttribute(DESIGNATED_PRIMARY);
    }

    @Override
    public int getPriority()
    {
        return (Integer)getActualAttribute(PRIORITY);
    }

    @Override
    public int getQuorumOverride()
    {
        return (Integer)getActualAttribute(QUORUM_OVERRIDE);
    }

    @Override
    public String getStorePath()
    {
        return (String)getActualAttribute(STORE_PATH);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getParameters()
    {
        return (Map<String, String>)getActualAttribute(PARAMETERS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getReplicationParameters()
    {
        return (Map<String, String>)getActualAttribute(REPLICATION_PARAMETERS);
    }

}
