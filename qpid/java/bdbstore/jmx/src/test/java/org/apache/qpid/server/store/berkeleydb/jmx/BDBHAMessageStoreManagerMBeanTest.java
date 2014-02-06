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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.jar.JarException;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;

import junit.framework.TestCase;

import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

public class BDBHAMessageStoreManagerMBeanTest extends TestCase
{
    private static final String TEST_GROUP_NAME = "testGroupName";
    private static final String TEST_NODE_NAME = "testNodeName";
    private static final String TEST_NODE_HOST_PORT = "host:1234";
    private static final String TEST_HELPER_HOST_PORT = "host:5678";
    private static final String TEST_DURABILITY = "sync,sync,all";
    private static final String TEST_NODE_STATE = "MASTER";
    private static final boolean TEST_DESIGNATED_PRIMARY_FLAG = false;
    private static final String TEST_VHOST_NAME = "test";

    private BDBHAMessageStoreManagerMBean _mBean;
    private VirtualHost _virtualHost;
    private ReplicationNode _localReplicationNode;
    private AMQManagedObject _mBeanParent;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));
        _localReplicationNode = mock(ReplicationNode.class);
        _virtualHost = mock(VirtualHost.class);
        _mBeanParent = mock(AMQManagedObject.class);
        when(_mBeanParent.getRegistry()).thenReturn(mock(ManagedObjectRegistry.class));
        when(_localReplicationNode.getParent(VirtualHost.class)).thenReturn(_virtualHost);

        when(_localReplicationNode.getName()).thenReturn(TEST_NODE_NAME);
        when(_virtualHost.getName()).thenReturn(TEST_VHOST_NAME);

        _mBean = new BDBHAMessageStoreManagerMBean(_localReplicationNode, _mBeanParent);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        CurrentActor.remove();
    }

    public void testObjectName() throws Exception
    {
        String expectedObjectName = "org.apache.qpid:type=BDBHAMessageStore,name=" + ObjectName.quote(TEST_VHOST_NAME);
        assertEquals(expectedObjectName, _mBean.getObjectName().toString());
    }

    public void testGroupName() throws Exception
    {
        when(_localReplicationNode.getAttribute(GROUP_NAME)).thenReturn(TEST_GROUP_NAME);

        assertEquals(TEST_GROUP_NAME, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_GROUP_NAME));
    }

    public void testNodeName() throws Exception
    {
        assertEquals(TEST_NODE_NAME, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_NAME));
    }

    public void testNodeHostPort() throws Exception
    {
        when(_localReplicationNode.getAttribute(HOST_PORT)).thenReturn(TEST_NODE_HOST_PORT);

        assertEquals(TEST_NODE_HOST_PORT, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_HOST_PORT));
    }

    public void testHelperHostPort() throws Exception
    {
        when(_localReplicationNode.getAttribute(HELPER_HOST_PORT)).thenReturn(TEST_HELPER_HOST_PORT);

        assertEquals(TEST_HELPER_HOST_PORT, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_HELPER_HOST_PORT));
    }

    public void testDurability() throws Exception
    {
        when(_localReplicationNode.getAttribute(DURABILITY)).thenReturn(TEST_DURABILITY);

        assertEquals(TEST_DURABILITY, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_DURABILITY));
    }

    public void testCoalescingSync() throws Exception
    {
        when(_localReplicationNode.getAttribute(COALESCING_SYNC)).thenReturn(true);

        assertEquals(true, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_COALESCING_SYNC));
    }

    public void testNodeState() throws Exception
    {
        when(_localReplicationNode.getAttribute(ROLE)).thenReturn(TEST_NODE_STATE);

        assertEquals(TEST_NODE_STATE, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_STATE));
    }

    public void testDesignatedPrimaryFlag() throws Exception
    {
        when(_localReplicationNode.getAttribute(DESIGNATED_PRIMARY)).thenReturn(TEST_DESIGNATED_PRIMARY_FLAG);

        assertEquals(TEST_DESIGNATED_PRIMARY_FLAG, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_DESIGNATED_PRIMARY));
    }

    public void testGroupMembersForGroupWithOneNode() throws Exception
    {
        ReplicationNode remoteNode = mock(ReplicationNode.class);
        when(remoteNode.getName()).thenReturn("remotenode");
        when(remoteNode.getAttribute(HOST_PORT)).thenReturn("remotehost:port");

        when(_localReplicationNode.getAttribute(HOST_PORT)).thenReturn(TEST_NODE_HOST_PORT);

        Collection<ReplicationNode> nodes = new ArrayList<ReplicationNode>();
        nodes.add(_localReplicationNode);
        nodes.add(remoteNode);
        when(_virtualHost.getChildren(ReplicationNode.class)).thenReturn(nodes);

        final TabularData resultsTable = _mBean.getAllNodesInGroup();

        assertTableHasHeadingsNamed(resultsTable, BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_NAME, BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_HOST_PORT);

        final int numberOfDataRows = resultsTable.size();
        assertEquals("Unexpected number of data rows", 2 ,numberOfDataRows);
        Iterator<?> iterator = resultsTable.values().iterator();

        final CompositeData firstRow = (CompositeData) iterator.next();
        assertEquals(TEST_NODE_NAME, firstRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_NAME));
        assertEquals(TEST_NODE_HOST_PORT, firstRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_HOST_PORT));

        final CompositeData secondRow = (CompositeData) iterator.next();
        assertEquals("remotenode", secondRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_NAME));
        assertEquals("remotehost:port", secondRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_HOST_PORT));

    }

    public void testRemoveNodeFromReplicationGroup() throws Exception
    {
        Collection<ReplicationNode> nodes = new ArrayList<ReplicationNode>();
        nodes.add(_localReplicationNode);
        when(_virtualHost.getChildren(ReplicationNode.class)).thenReturn(nodes);
        when(_localReplicationNode.getActualState()).thenReturn(State.ACTIVE);
        when(_localReplicationNode.setDesiredState(State.ACTIVE, State.DELETED)).thenReturn(State.DELETED);

        _mBean.removeNodeFromGroup(TEST_NODE_NAME);

        verify(_localReplicationNode).setDesiredState(State.ACTIVE, State.DELETED);
    }

    public void testRemoveNodeFromReplicationGroupOnIllegalStateTransitionException() throws Exception
    {
        Collection<ReplicationNode> nodes = new ArrayList<ReplicationNode>();
        nodes.add(_localReplicationNode);
        when(_virtualHost.getChildren(ReplicationNode.class)).thenReturn(nodes);
        when(_localReplicationNode.getActualState()).thenReturn(State.ACTIVE);
        when(_localReplicationNode.setDesiredState(State.ACTIVE, State.DELETED)).thenThrow(new IllegalStateTransitionException());

        try
        {
            _mBean.removeNodeFromGroup(TEST_NODE_NAME);
            fail("Should throw JM Exception on IllegalStateTransitionException");
        }
        catch(JMException e)
        {
            //pass
        }
    }

    public void testSetAsDesignatedPrimary() throws Exception
    {
        _mBean.setDesignatedPrimary(true);

        verify(_localReplicationNode).setAttribute(DESIGNATED_PRIMARY, null, true);
    }

    private void assertTableHasHeadingsNamed(final TabularData resultsTable, String... headingNames)
    {
        CompositeType headingsRow = resultsTable.getTabularType().getRowType();
        for (final String headingName : headingNames)
        {
            assertTrue("Table should have column with heading " + headingName, headingsRow.containsKey(headingName));
        }
    }
}
