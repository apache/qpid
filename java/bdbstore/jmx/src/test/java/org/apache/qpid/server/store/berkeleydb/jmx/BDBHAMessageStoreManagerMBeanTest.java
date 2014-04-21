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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;

import junit.framework.TestCase;

import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;

public class BDBHAMessageStoreManagerMBeanTest extends TestCase
{
    private static final String TEST_GROUP_NAME = "testGroupName";
    private static final String TEST_NODE_NAME = "testNodeName";
    private static final String TEST_NODE_HOST_PORT = "host:1234";
    private static final String TEST_HELPER_HOST_PORT = "host:5678";
    private static final String TEST_DURABILITY = "sync,sync,all";
    private static final String TEST_NODE_STATE = "MASTER";
    private static final String TEST_STORE_NAME = "testStoreName";
    private static final boolean TEST_DESIGNATED_PRIMARY_FLAG = false;

    private ReplicatedEnvironmentFacade _replicatedEnvironmentFacade;
    private BDBHAMessageStoreManagerMBean _mBean;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _replicatedEnvironmentFacade = mock(ReplicatedEnvironmentFacade.class);
        ManagedObjectRegistry registry = mock(ManagedObjectRegistry.class);
        _mBean = new BDBHAMessageStoreManagerMBean(TEST_STORE_NAME, _replicatedEnvironmentFacade, registry);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testObjectName() throws Exception
    {
        String expectedObjectName = "org.apache.qpid:type=BDBHAMessageStore,name=" + ObjectName.quote(TEST_STORE_NAME);
        assertEquals(expectedObjectName, _mBean.getObjectName().toString());
    }

    public void testGroupName() throws Exception
    {
        when(_replicatedEnvironmentFacade.getGroupName()).thenReturn(TEST_GROUP_NAME);

        assertEquals(TEST_GROUP_NAME, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_GROUP_NAME));
    }

    public void testNodeName() throws Exception
    {
        when(_replicatedEnvironmentFacade.getNodeName()).thenReturn(TEST_NODE_NAME);

        assertEquals(TEST_NODE_NAME, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_NAME));
    }

    public void testNodeHostPort() throws Exception
    {
        when(_replicatedEnvironmentFacade.getHostPort()).thenReturn(TEST_NODE_HOST_PORT);

        assertEquals(TEST_NODE_HOST_PORT, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_HOST_PORT));
    }

    public void testHelperHostPort() throws Exception
    {
        when(_replicatedEnvironmentFacade.getHelperHostPort()).thenReturn(TEST_HELPER_HOST_PORT);

        assertEquals(TEST_HELPER_HOST_PORT, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_HELPER_HOST_PORT));
    }

    public void testDurability() throws Exception
    {
        when(_replicatedEnvironmentFacade.getDurability()).thenReturn(TEST_DURABILITY);

        assertEquals(TEST_DURABILITY, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_DURABILITY));
    }

    public void testCoalescingSync() throws Exception
    {
        when(_replicatedEnvironmentFacade.isCoalescingSync()).thenReturn(true);

        assertEquals(true, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_COALESCING_SYNC));
    }

    public void testNodeState() throws Exception
    {
        when(_replicatedEnvironmentFacade.getNodeState()).thenReturn(TEST_NODE_STATE);

        assertEquals(TEST_NODE_STATE, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_NODE_STATE));
    }

    public void testDesignatedPrimaryFlag() throws Exception
    {
        when(_replicatedEnvironmentFacade.isDesignatedPrimary()).thenReturn(TEST_DESIGNATED_PRIMARY_FLAG);

        assertEquals(TEST_DESIGNATED_PRIMARY_FLAG, _mBean.getAttribute(ManagedBDBHAMessageStore.ATTR_DESIGNATED_PRIMARY));
    }

    public void testGroupMembersForGroupWithOneNode() throws Exception
    {
        List<Map<String, String>> members = Collections.singletonList(createTestNodeResult());
        when(_replicatedEnvironmentFacade.getGroupMembers()).thenReturn(members);

        final TabularData resultsTable = _mBean.getAllNodesInGroup();

        assertTableHasHeadingsNamed(resultsTable, ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME, ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_HOST_PORT);

        final int numberOfDataRows = resultsTable.size();
        assertEquals("Unexpected number of data rows", 1 ,numberOfDataRows);
        final CompositeData row = (CompositeData) resultsTable.values().iterator().next();
        assertEquals(TEST_NODE_NAME, row.get(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME));
        assertEquals(TEST_NODE_HOST_PORT, row.get(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_HOST_PORT));
    }

    public void testRemoveNodeFromReplicationGroup() throws Exception
    {
        _mBean.removeNodeFromGroup(TEST_NODE_NAME);

        verify(_replicatedEnvironmentFacade).removeNodeFromGroup(TEST_NODE_NAME);
    }

    public void testRemoveNodeFromReplicationGroupWithError() throws Exception
    {
        doThrow(new RuntimeException("mocked exception")).when(_replicatedEnvironmentFacade).removeNodeFromGroup(TEST_NODE_NAME);

        try
        {
            _mBean.removeNodeFromGroup(TEST_NODE_NAME);
            fail("Exception not thrown");
        }
        catch (JMException je)
        {
            // PASS
        }
    }

    public void testSetAsDesignatedPrimary() throws Exception
    {
        _mBean.setDesignatedPrimary(true);

        verify(_replicatedEnvironmentFacade).setDesignatedPrimary(true);
    }

    public void testSetAsDesignatedPrimaryWithError() throws Exception
    {
        doThrow(new RuntimeException("mocked exception")).when(_replicatedEnvironmentFacade).setDesignatedPrimary(true);

        try
        {
            _mBean.setDesignatedPrimary(true);
            fail("Exception not thrown");
        }
        catch (JMException je)
        {
            // PASS
        }
    }

    public void testUpdateAddress() throws Exception
    {
        String newHostName = "newHostName";
        int newPort = 1967;

        _mBean.updateAddress(TEST_NODE_NAME, newHostName, newPort);

        verify(_replicatedEnvironmentFacade).updateAddress(TEST_NODE_NAME, newHostName, newPort);
    }

    private void assertTableHasHeadingsNamed(final TabularData resultsTable, String... headingNames)
    {
        CompositeType headingsRow = resultsTable.getTabularType().getRowType();
        for (final String headingName : headingNames)
        {
            assertTrue("Table should have column with heading " + headingName, headingsRow.containsKey(headingName));
        }
    }

    private Map<String, String> createTestNodeResult()
    {
        Map<String, String> items = new HashMap<String, String>();
        items.put(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME, TEST_NODE_NAME);
        items.put(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_HOST_PORT, TEST_NODE_HOST_PORT);
        return items;
    }
}
