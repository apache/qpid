/*
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
package org.apache.qpid.server.store.berkeleydb.jmx;

import static org.apache.qpid.server.model.ReplicationNode.COALESCING_SYNC;
import static org.apache.qpid.server.model.ReplicationNode.DESIGNATED_PRIMARY;
import static org.apache.qpid.server.model.ReplicationNode.DURABILITY;
import static org.apache.qpid.server.model.ReplicationNode.GROUP_NAME;
import static org.apache.qpid.server.model.ReplicationNode.HELPER_HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.ROLE;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.log4j.Logger;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.ConfiguredObjectFinder;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

/**
 * Management mbean for BDB HA.
 */
public class BDBHAMessageStoreManagerMBean extends AMQManagedObject implements ManagedBDBHAMessageStore
{
    private static final Logger LOGGER = Logger.getLogger(BDBHAMessageStoreManagerMBean.class);

    private static  final TabularType GROUP_MEMBERS_TABLE;
    private static final CompositeType GROUP_MEMBER_ROW;
    private static final OpenType<?>[] GROUP_MEMBER_ATTRIBUTE_TYPES;


    static
    {
        try
        {
            GROUP_MEMBER_ATTRIBUTE_TYPES = new OpenType<?>[] {SimpleType.STRING, SimpleType.STRING};
            final String[] itemNames = new String[] {GRP_MEM_COL_NODE_NAME, GRP_MEM_COL_NODE_HOST_PORT};
            final String[] itemDescriptions = new String[] {"Unique node name", "Node host / port "};
            GROUP_MEMBER_ROW = new CompositeType("GroupMember", "Replication group member",
                                                itemNames,
                                                itemDescriptions,
                                                GROUP_MEMBER_ATTRIBUTE_TYPES );
            GROUP_MEMBERS_TABLE = new TabularType("GroupMembers", "Replication group memebers",
                                                GROUP_MEMBER_ROW,
                                                new String[] {GRP_MEM_COL_NODE_NAME});
        }
        catch (final OpenDataException ode)
        {
            throw new ExceptionInInitializerError(ode);
        }
    }

    private final ReplicationNode _localReplicationNode;
    private final String _objectName;
    private final VirtualHost _parent;
    private final String _virtualHostName;

    public BDBHAMessageStoreManagerMBean(ReplicationNode localReplicationNode, ManagedObject parent) throws JMException
    {
        super(ManagedBDBHAMessageStore.class, ManagedBDBHAMessageStore.TYPE, ((AMQManagedObject)parent).getRegistry());

        _localReplicationNode = localReplicationNode;
        _virtualHostName = localReplicationNode.getParent(VirtualHost.class).getName();
        _objectName = ObjectName.quote(_virtualHostName);
        _parent = _localReplicationNode.getParent(VirtualHost.class);

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Creating BDBHAMessageStoreManagerMBean for " + _localReplicationNode.getName());
        }
        register();
    }

    @Override
    public String getObjectInstanceName()
    {
        return _objectName;
    }

    @Override
    public String getGroupName()
    {
        return (String) _localReplicationNode.getAttribute(GROUP_NAME);
    }

    @Override
    public String getNodeName()
    {
        return (String) _localReplicationNode.getName();
    }

    @Override
    public String getNodeHostPort()
    {
        return (String) _localReplicationNode.getAttribute(HOST_PORT);
    }

    @Override
    public String getHelperHostPort()
    {
        return (String) _localReplicationNode.getAttribute(HELPER_HOST_PORT);
    }

    @Override
    public String getDurability() throws IOException, JMException
    {
        return (String) _localReplicationNode.getAttribute(DURABILITY);
    }


    @Override
    public boolean getCoalescingSync() throws IOException, JMException
    {
        return (Boolean)_localReplicationNode.getAttribute(COALESCING_SYNC);
    }

    @Override
    public String getNodeState() throws IOException, JMException
    {
        return (String)_localReplicationNode.getAttribute(ROLE);
    }

    @Override
    public boolean getDesignatedPrimary() throws IOException, JMException
    {
        try
        {
            return (Boolean)_localReplicationNode.getAttribute(DESIGNATED_PRIMARY);
        }
        catch (RuntimeException e)
        {
            LOGGER.debug("Failed query designated primary", e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public TabularData getAllNodesInGroup() throws IOException, JMException
    {
        Collection<ReplicationNode> allNodes = _parent.getChildren(ReplicationNode.class);

        final TabularDataSupport data = new TabularDataSupport(GROUP_MEMBERS_TABLE);
        for (ReplicationNode replicationNode : allNodes)
        {
            Map<String, String> nodeMap = new HashMap<String, String>();
            nodeMap.put(GRP_MEM_COL_NODE_NAME, replicationNode.getName());
            nodeMap.put(GRP_MEM_COL_NODE_HOST_PORT, (String)replicationNode.getAttribute(HOST_PORT));

            CompositeData memberData = new CompositeDataSupport(GROUP_MEMBER_ROW, nodeMap);
            data.put(memberData);
        }
        return data;
    }

    @Override
    public void removeNodeFromGroup(String nodeName) throws JMException
    {
        // find the replication node object, set the desired state
        Collection<ReplicationNode> allNodes = _parent.getChildren(ReplicationNode.class);
        ReplicationNode targetNode = ConfiguredObjectFinder.findConfiguredObjectByName(allNodes, nodeName);

        if (targetNode == null)
        {
            throw new JMException("Failed to find replication node with name '" + nodeName + "'.");
        }
        try
        {
            State newState = targetNode.setDesiredState(targetNode.getActualState(), State.DELETED);
            if (newState != State.DELETED)
            {
                throw new JMException("Failed to delete replication node with name '" + nodeName + "'. New unexpectedly state is " + newState);
            }
        }
        catch(IllegalStateTransitionException e)
        {
            LOGGER.error("Cannot remove node '" + nodeName + "' from the group", e);
            throw new JMException("Cannot remove node '" + nodeName + "' from the group:" + e.getMessage());
        }
    }

    @Override
    public void setDesignatedPrimary(boolean primary) throws JMException
    {
        try
        {
            _localReplicationNode.setAttribute(DESIGNATED_PRIMARY, _localReplicationNode.getAttribute(DESIGNATED_PRIMARY), primary);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to set node " + _localReplicationNode.getName() + " to designated primary : " + primary, e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public void updateAddress(String nodeName, String newHostName, int newPort) throws JMException
    {
        throw new UnsupportedOperationException("Unsupported operation.  Delete the node then add a new node in its place.");
    }

    @Override
    public ManagedObject getParentObject()
    {
        return null;
    }

}
