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
import java.util.List;
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
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;

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
            final String[] itemNames = new String[] {ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME, ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_HOST_PORT};
            final String[] itemDescriptions = new String[] {"Unique node name", "Node host / port "};
            GROUP_MEMBER_ROW = new CompositeType("GroupMember", "Replication group member",
                                                itemNames,
                                                itemDescriptions,
                                                GROUP_MEMBER_ATTRIBUTE_TYPES );
            GROUP_MEMBERS_TABLE = new TabularType("GroupMembers", "Replication group memebers",
                                                GROUP_MEMBER_ROW,
                                                new String[] {ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME});
        }
        catch (final OpenDataException ode)
        {
            throw new ExceptionInInitializerError(ode);
        }
    }

    private final ReplicatedEnvironmentFacade _replicatedEnvironmentFacade;
    private final String _objectName;

    protected BDBHAMessageStoreManagerMBean(String virtualHostName, ReplicatedEnvironmentFacade replicatedEnvironmentFacade, ManagedObject parent) throws JMException
    {
        super(ManagedBDBHAMessageStore.class, ManagedBDBHAMessageStore.TYPE, ((AMQManagedObject)parent).getRegistry());
        LOGGER.debug("Creating BDBHAMessageStoreManagerMBean for " + virtualHostName);
        _replicatedEnvironmentFacade = replicatedEnvironmentFacade;
        _objectName = ObjectName.quote(virtualHostName);
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
        return _replicatedEnvironmentFacade.getGroupName();
    }

    @Override
    public String getNodeName()
    {
        return _replicatedEnvironmentFacade.getNodeName();
    }

    @Override
    public String getNodeHostPort()
    {
        return _replicatedEnvironmentFacade.getHostPort();
    }

    @Override
    public String getHelperHostPort()
    {
        return _replicatedEnvironmentFacade.getHelperHostPort();
    }

    @Override
    public String getDurability() throws IOException, JMException
    {
        try
        {
            return _replicatedEnvironmentFacade.getDurability();
        }
        catch (RuntimeException e)
        {
            LOGGER.debug("Failed query replication policy", e);
            throw new JMException(e.getMessage());
        }
    }


    @Override
    public boolean getCoalescingSync() throws IOException, JMException
    {
        return _replicatedEnvironmentFacade.isCoalescingSync();
    }

    @Override
    public String getNodeState() throws IOException, JMException
    {
        try
        {
            return _replicatedEnvironmentFacade.getNodeState();
        }
        catch (RuntimeException e)
        {
            LOGGER.debug("Failed query node state", e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public boolean getDesignatedPrimary() throws IOException, JMException
    {
        try
        {
            return _replicatedEnvironmentFacade.isDesignatedPrimary();
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
        final TabularDataSupport data = new TabularDataSupport(GROUP_MEMBERS_TABLE);
        final List<Map<String, String>> members = _replicatedEnvironmentFacade.getGroupMembers();

        for (Map<String, String> map : members)
        {
            CompositeData memberData = new CompositeDataSupport(GROUP_MEMBER_ROW, map);
            data.put(memberData);
        }
        return data;
    }

    @Override
    public void removeNodeFromGroup(String nodeName) throws JMException
    {
        try
        {
            _replicatedEnvironmentFacade.removeNodeFromGroup(nodeName);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Failed to remove node " + nodeName + " from group", e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public void setDesignatedPrimary(boolean primary) throws JMException
    {
        try
        {
            _replicatedEnvironmentFacade.setDesignatedPrimary(primary);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Failed to set node " + _replicatedEnvironmentFacade.getNodeName() + " as designated primary", e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public void updateAddress(String nodeName, String newHostName, int newPort) throws JMException
    {
        try
        {
            _replicatedEnvironmentFacade.updateAddress(nodeName, newHostName, newPort);
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Failed to update address for node " + nodeName + " to " + newHostName + ":" + newPort, e);
            throw new JMException(e.getMessage());
        }
    }

    @Override
    public ManagedObject getParentObject()
    {
        return null;
    }

}
