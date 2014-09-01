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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
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
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;

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

    private final BDBHAVirtualHostNode<?> _virtualHostNode;
    private final String _objectName;

    protected BDBHAMessageStoreManagerMBean(BDBHAVirtualHostNode<?> virtualHostNode, ManagedObjectRegistry registry) throws JMException
    {
        super(ManagedBDBHAMessageStore.class, ManagedBDBHAMessageStore.TYPE, registry);
        LOGGER.debug("Creating BDBHAMessageStoreManagerMBean for " + virtualHostNode.getName());
        _virtualHostNode = virtualHostNode;
        _objectName = ObjectName.quote( virtualHostNode.getGroupName());
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
        return _virtualHostNode.getGroupName();
    }

    @Override
    public String getNodeName()
    {
        return _virtualHostNode.getName();
    }

    @Override
    public String getNodeHostPort()
    {
        return _virtualHostNode.getAddress();
    }

    @Override
    public String getHelperHostPort()
    {
        return _virtualHostNode.getHelperAddress();
    }

    @Override
    public String getDurability() throws IOException, JMException
    {
        BDBHAVirtualHost<?> host = (BDBHAVirtualHost<?>)_virtualHostNode.getVirtualHost();
        if (host != null)
        {
            return host.getDurability();
        }
        return null;
    }


    @Override
    public boolean getCoalescingSync() throws IOException, JMException
    {
        BDBHAVirtualHost<?> host = (BDBHAVirtualHost<?>)_virtualHostNode.getVirtualHost();
        if (host != null)
        {
            return host.isCoalescingSync();
        }
        return false;
    }

    @Override
    public String getNodeState() throws IOException, JMException
    {
        try
        {
            return _virtualHostNode.getRole().name();
        }
        catch (RuntimeException e)
        {
            LOGGER.debug("Failed query node role", e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public boolean getDesignatedPrimary() throws IOException, JMException
    {
        return _virtualHostNode.isDesignatedPrimary();
    }

    @Override
    public TabularData getAllNodesInGroup() throws IOException, JMException
    {
        final TabularDataSupport data = new TabularDataSupport(GROUP_MEMBERS_TABLE);

        Map<String, String> localNodeMap = new HashMap<String, String>();
        localNodeMap.put(GRP_MEM_COL_NODE_NAME, _virtualHostNode.getName());
        localNodeMap.put(GRP_MEM_COL_NODE_HOST_PORT, _virtualHostNode.getAddress());
        CompositeData localNodeData = new CompositeDataSupport(GROUP_MEMBER_ROW, localNodeMap);
        data.put(localNodeData);

        @SuppressWarnings("rawtypes")
        final Collection<? extends RemoteReplicationNode> members = _virtualHostNode.getRemoteReplicationNodes();
        for (RemoteReplicationNode<?> remoteNode : members)
        {
            BDBHARemoteReplicationNode<?> haReplicationNode = (BDBHARemoteReplicationNode<?>)remoteNode;
            Map<String, String> nodeMap = new HashMap<String, String>();
            nodeMap.put(GRP_MEM_COL_NODE_NAME, haReplicationNode.getName());
            nodeMap.put(GRP_MEM_COL_NODE_HOST_PORT, haReplicationNode.getAddress());

            CompositeData memberData = new CompositeDataSupport(GROUP_MEMBER_ROW, nodeMap);
            data.put(memberData);
        }
        return data;
    }

    @Override
    public void removeNodeFromGroup(String nodeName) throws JMException
    {
        if (getNodeName().equals(nodeName))
        {
            _virtualHostNode.delete();
        }
        else
        {
            @SuppressWarnings("rawtypes")
            Collection<? extends RemoteReplicationNode> remoteNodes = _virtualHostNode.getRemoteReplicationNodes();
            for (RemoteReplicationNode<?> remoteNode : remoteNodes)
            {
                if (remoteNode.getName().equals(nodeName))
                {
                    try
                    {
                        remoteNode.delete();
                        return;
                    }
                    catch(IllegalStateTransitionException e)
                    {
                        LOGGER.error("Cannot remove node '" + nodeName + "' from the group", e);
                        throw new JMException("Cannot remove node '" + nodeName + "' from the group:" + e.getMessage());
                    }
                }
            }

            throw new JMException("Failed to find replication node with name '" + nodeName + "'.");
        }
    }

    @Override
    public void setDesignatedPrimary(boolean primary) throws JMException
    {
        try
        {
            _virtualHostNode.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, primary));
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Failed to set node " + _virtualHostNode.getName() + " as designated primary", e);
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
