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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;

import junit.framework.TestCase;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBHAVirtualHost;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHARemoteReplicationNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBHAVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.berkeleydb.NodeRole;

public class BDBHAMessageStoreManagerMBeanTest extends TestCase
{
    private static final String TEST_VHOST_NAME = "test";
    private static final String TEST_GROUP_NAME = TEST_VHOST_NAME;
    private static final String TEST_NODE_NAME = "testNodeName";
    private static final String TEST_NODE_HOST_PORT = "host:1234";
    private static final String TEST_HELPER_HOST_PORT = "host:5678";
    private static final String TEST_DURABILITY = "sync,sync,all";
    private static final NodeRole TEST_NODE_ROLE = NodeRole.MASTER;
    private static final boolean TEST_DESIGNATED_PRIMARY_FLAG = false;

    private BDBHAVirtualHostNode<?> _virtualHostNode;
    private BDBHAMessageStoreManagerMBean _mBean;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _virtualHostNode = mock(BDBHAVirtualHostNode.class);
        when(_virtualHostNode.getName()).thenReturn(TEST_NODE_NAME);
        when(_virtualHostNode.getGroupName()).thenReturn(TEST_GROUP_NAME);
        when(_virtualHostNode.getAddress()).thenReturn(TEST_NODE_HOST_PORT);

        ManagedObjectRegistry registry = mock(ManagedObjectRegistry.class);
        _mBean = new BDBHAMessageStoreManagerMBean(_virtualHostNode, registry);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testObjectName() throws Exception
    {
        String expectedObjectName = "org.apache.qpid:type=BDBHAMessageStore,name=" + ObjectName.quote(TEST_VHOST_NAME);
        assertEquals(expectedObjectName, _mBean.getObjectName().toString());
    }

    public void testGroupName() throws Exception
    {
        when(_virtualHostNode.getGroupName()).thenReturn(TEST_GROUP_NAME);

        assertEquals(TEST_GROUP_NAME, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_GROUP_NAME));
    }

    public void testNodeName() throws Exception
    {
        when(_virtualHostNode.getName()).thenReturn(TEST_NODE_NAME);

        assertEquals(TEST_NODE_NAME, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_NAME));
    }

    public void testNodeHostPort() throws Exception
    {
        when(_virtualHostNode.getAddress()).thenReturn(TEST_NODE_HOST_PORT);

        assertEquals(TEST_NODE_HOST_PORT, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_HOST_PORT));
    }

    public void testHelperHostPort() throws Exception
    {
        when(_virtualHostNode.getHelperAddress()).thenReturn(TEST_HELPER_HOST_PORT);

        assertEquals(TEST_HELPER_HOST_PORT, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_HELPER_HOST_PORT));
    }

    public void testDurability() throws Exception
    {
        BDBHAVirtualHost virtualHost = mock(BDBHAVirtualHost.class);
        when(_virtualHostNode.getVirtualHost()).thenReturn(virtualHost);
        when(virtualHost.getDurability()).thenReturn(TEST_DURABILITY);

        assertEquals(TEST_DURABILITY, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_DURABILITY));
    }

    public void testIsCoalescingSync() throws Exception
    {
        BDBHAVirtualHost virtualHost = mock(BDBHAVirtualHost.class);
        when(_virtualHostNode.getVirtualHost()).thenReturn(virtualHost);
        when(virtualHost.isCoalescingSync()).thenReturn(true);

        assertEquals(true, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_COALESCING_SYNC));
    }

    public void testNodeState() throws Exception
    {
        when(_virtualHostNode.getRole()).thenReturn(TEST_NODE_ROLE);

        assertEquals(TEST_NODE_ROLE.name(), _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_STATE));
    }

    public void testDesignatedPrimaryFlag() throws Exception
    {
        when(_virtualHostNode.isDesignatedPrimary()).thenReturn(TEST_DESIGNATED_PRIMARY_FLAG);

        assertEquals(TEST_DESIGNATED_PRIMARY_FLAG, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_DESIGNATED_PRIMARY));
    }

    public void testGroupMembersForGroupWithOneNode() throws Exception
    {
        BDBHARemoteReplicationNode<?> node = mockRemoteNode();

        final TabularData resultsTable = _mBean.getAllNodesInGroup();

        assertTableHasHeadingsNamed(resultsTable, BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_NAME,
                BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_HOST_PORT);

        final int numberOfDataRows = resultsTable.size();
        assertEquals("Unexpected number of data rows", 2, numberOfDataRows);
        Iterator<?> iterator = resultsTable.values().iterator();

        final CompositeData firstRow = (CompositeData) iterator.next();
        assertEquals(TEST_NODE_NAME, firstRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_NAME));
        assertEquals(TEST_NODE_HOST_PORT, firstRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_HOST_PORT));

        final CompositeData secondRow = (CompositeData) iterator.next();
        assertEquals(node.getName(), secondRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_NAME));
        assertEquals(node.getAddress(), secondRow.get(BDBHAMessageStoreManagerMBean.GRP_MEM_COL_NODE_HOST_PORT));
    }

    public void testRemoveNodeFromReplicationGroup() throws Exception
    {
        BDBHARemoteReplicationNode<?> node = mockRemoteNode();

        _mBean.removeNodeFromGroup(node.getName());

        verify(node).delete();
    }

    public void testRemoveNodeFromReplicationGroupOnIllegalStateTransitionException() throws Exception
    {
        BDBHARemoteReplicationNode<?> node = mockRemoteNode();
         doThrow(new IllegalStateTransitionException("test")).when(node).delete();

         try
        {
            _mBean.removeNodeFromGroup("remotenode");
            fail("Exception not thrown");
        }
        catch (JMException je)
        {
            // PASS#
        }
    }

    public void testSetAsDesignatedPrimary() throws Exception
    {
        _mBean.setDesignatedPrimary(true);

        verify(_virtualHostNode).setAttributes(
                eq(Collections.<String, Object> singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true)));
    }

    private void assertTableHasHeadingsNamed(final TabularData resultsTable, String... headingNames)
    {
        CompositeType headingsRow = resultsTable.getTabularType().getRowType();
        for (final String headingName : headingNames)
        {
            assertTrue("Table should have column with heading " + headingName, headingsRow.containsKey(headingName));
        }
    }

    private BDBHARemoteReplicationNode<?> mockRemoteNode()
    {
        BDBHARemoteReplicationNode<?> remoteNode = mock(BDBHARemoteReplicationNode.class);
        when(remoteNode.getName()).thenReturn("remotenode");
        when(remoteNode.getAddress()).thenReturn("remotehost:port");

        @SuppressWarnings("rawtypes")
        Collection<? extends RemoteReplicationNode> remoteNodes = Collections.singletonList(remoteNode);
        doReturn(remoteNodes).when(_virtualHostNode).getRemoteReplicationNodes();

        return remoteNode;
    }
}
