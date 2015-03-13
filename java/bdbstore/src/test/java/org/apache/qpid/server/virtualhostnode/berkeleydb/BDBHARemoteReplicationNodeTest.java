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
 */

package org.apache.qpid.server.virtualhostnode.berkeleydb;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.AccessControlException;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

public class BDBHARemoteReplicationNodeTest extends QpidTestCase
{
    private final org.apache.qpid.server.security.SecurityManager _mockSecurityManager = mock(SecurityManager.class);

    private Broker _broker;
    private TaskExecutor _taskExecutor;
    private BDBHAVirtualHostNode<?> _virtualHostNode;
    private DurableConfigurationStore _configStore;
    private ReplicatedEnvironmentFacade _facade;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _facade = mock(ReplicatedEnvironmentFacade.class);

        _broker = BrokerTestHelper.createBrokerMock();

        _taskExecutor = new CurrentThreadTaskExecutor();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_broker.getChildExecutor()).thenReturn(_taskExecutor);

        _virtualHostNode = mock(BDBHAVirtualHostNode.class);
        _configStore = mock(DurableConfigurationStore.class);
        when(_virtualHostNode.getConfigurationStore()).thenReturn(_configStore);

        // Virtualhost needs the EventLogger from the SystemContext.
        when(_virtualHostNode.getParent(Broker.class)).thenReturn(_broker);
        doReturn(VirtualHostNode.class).when(_virtualHostNode).getCategoryClass();
        ConfiguredObjectFactory objectFactory = _broker.getObjectFactory();
        when(_virtualHostNode.getModel()).thenReturn(objectFactory.getModel());
        when(_virtualHostNode.getTaskExecutor()).thenReturn(_taskExecutor);
        when(_virtualHostNode.getChildExecutor()).thenReturn(_taskExecutor);

    }

    public void testUpdateRole()
    {
        String remoteReplicationName = getName();
        BDBHARemoteReplicationNode remoteReplicationNode = createRemoteReplicationNode(remoteReplicationName);

        remoteReplicationNode.setAttribute(BDBHARemoteReplicationNode.ROLE, remoteReplicationNode.getRole(), NodeRole.MASTER);

        verify(_facade).transferMasterAsynchronously(remoteReplicationName);
    }

    public void testDelete()
    {
        String remoteReplicationName = getName();
        BDBHARemoteReplicationNode remoteReplicationNode = createRemoteReplicationNode(remoteReplicationName);

        remoteReplicationNode.delete();

        verify(_facade).removeNodeFromGroup(remoteReplicationName);
    }

    // ***************  ReplicationNode Access Control Tests  ***************

    public void testUpdateDeniedByACL()
    {
        when(_broker.getSecurityManager()).thenReturn(_mockSecurityManager);

        String remoteReplicationName = getName();
        BDBHARemoteReplicationNode remoteReplicationNode = createRemoteReplicationNode(remoteReplicationName);

        doThrow(new AccessControlException("mocked ACL exception")).when(_mockSecurityManager).authoriseUpdate(remoteReplicationNode);

        assertNull(remoteReplicationNode.getDescription());

        try
        {
            remoteReplicationNode.setAttribute(VirtualHost.DESCRIPTION, null, "My description");
            fail("Exception not thrown");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }
    }

    public void testDeleteDeniedByACL()
    {
        when(_broker.getSecurityManager()).thenReturn(_mockSecurityManager);

        String remoteReplicationName = getName();
        BDBHARemoteReplicationNode remoteReplicationNode = createRemoteReplicationNode(remoteReplicationName);

        doThrow(new AccessControlException("mocked ACL exception")).when(_mockSecurityManager).authoriseDelete(remoteReplicationNode);

        assertNull(remoteReplicationNode.getDescription());

        try
        {
            remoteReplicationNode.delete();
            fail("Exception not thrown");
        }
        catch (AccessControlException ace)
        {
            // PASS
        }
    }

    private BDBHARemoteReplicationNode createRemoteReplicationNode(final String replicationNodeName)
    {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(BDBHARemoteReplicationNode.NAME, replicationNodeName);
        attributes.put(BDBHARemoteReplicationNode.MONITOR, Boolean.FALSE);

        BDBHARemoteReplicationNodeImpl node = new BDBHARemoteReplicationNodeImpl(_virtualHostNode, attributes, _facade);
        node.create();
        return node;
    }


}
