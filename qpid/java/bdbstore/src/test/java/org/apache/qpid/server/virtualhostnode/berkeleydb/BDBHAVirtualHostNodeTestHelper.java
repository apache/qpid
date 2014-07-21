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
package org.apache.qpid.server.virtualhostnode.berkeleydb;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sleepycat.je.rep.ReplicationConfig;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

/**
 * Helper class to make the tests of BDB HA Virtual Host Nodes simpler and more concise.
 */
public class BDBHAVirtualHostNodeTestHelper
{
    private final String _testName;
    private Broker<?> _broker;
    private File _bdbStorePath;
    private TaskExecutor _taskExecutor;
    private final ConfiguredObjectFactory _objectFactory = BrokerModel.getInstance().getObjectFactory();
    private final Set<BDBHAVirtualHostNode<?>> _nodes = new HashSet<>();

    public BDBHAVirtualHostNodeTestHelper(String testName) throws Exception
    {
        _testName = testName;
        _broker = BrokerTestHelper.createBrokerMock();

        _taskExecutor = new TaskExecutorImpl();
        _taskExecutor.start();
        when(_broker.getTaskExecutor()).thenReturn(_taskExecutor);

        _bdbStorePath = new File(QpidTestCase.TMP_FOLDER, _testName + "." + System.currentTimeMillis());
        _bdbStorePath.deleteOnExit();
    }

    public void tearDown() throws Exception
    {
        try
        {
            Exception firstException = null;
            for (VirtualHostNode<?> node : _nodes)
            {
                try
                {
                    node.delete();
                }
                catch(Exception e)
                {
                    if (firstException != null)
                    {
                        firstException = e;
                    }
                }
                if (firstException != null)
                {
                    throw firstException;
                }
            }
        }
        finally
        {
            if (_taskExecutor != null)
            {
                _taskExecutor.stopImmediately();
            }
            if (_bdbStorePath != null)
            {
                FileUtils.delete(_bdbStorePath, true);
            }
        }
    }

    public BDBHARemoteReplicationNode<?> findRemoteNode(BDBHAVirtualHostNode<?> node, String name)
    {
        for (RemoteReplicationNode<?> remoteNode : node.getRemoteReplicationNodes())
        {
            if (remoteNode.getName().equals(name))
            {
                return (BDBHARemoteReplicationNode<?>)remoteNode;
            }
        }
        return null;
    }

    public void awaitRemoteNodes(BDBHAVirtualHostNode<?> node, int expectedNodeNumber) throws InterruptedException
    {
        int counter = 0;

        @SuppressWarnings("rawtypes")
        Collection<? extends RemoteReplicationNode> remoteNodes = null;
        do
        {
            remoteNodes = node.getRemoteReplicationNodes();
            if (counter > 0)
            {
                Thread.sleep(100);
            }
            counter++;
        }
        // TODO: 30 seconds is quite a lot to wait, we need to reduce this limit
        while(remoteNodes.size() != expectedNodeNumber && counter<100);
        assertEquals("Unexpected node number", expectedNodeNumber, node.getRemoteReplicationNodes().size());
    }

    public void awaitForAttributeChange(ConfiguredObject<?> object, String name, Object expectedValue) throws InterruptedException
    {
        int awaitCounter = 0;
        while(!object.equals(object.getAttribute(name)) && awaitCounter < 50)
        {
            Thread.sleep(100);
            awaitCounter++;
        }
        assertEquals("Unexpected attribute " + name + " on " + object, expectedValue, object.getAttribute(name) );
    }

    public BDBHAVirtualHostNode<?> awaitAndFindNodeInRole(String role) throws InterruptedException
    {
        BDBHAVirtualHostNode<?> replica = null;
        int findReplicaCount = 0;
        while(replica == null)
        {
            replica = findNodeInRole(role);
            if (replica == null)
            {
                Thread.sleep(100);
            }
            if (findReplicaCount > 50)
            {
                fail("Could not find a node in replica role");
            }
            findReplicaCount++;
        }
        return replica;
    }

    public BDBHAVirtualHostNode<?> findNodeInRole(String role)
    {
        for (BDBHAVirtualHostNode<?> node : _nodes)
        {
            if (role.equals(node.getRole()))
            {
                return node;
            }
        }
        return null;
    }

    public BDBHAVirtualHostNode<?> createHaVHN(Map<String, Object> attributes)
    {
        @SuppressWarnings("unchecked")
        BDBHAVirtualHostNode<?> node = (BDBHAVirtualHostNode<?>) _objectFactory.create(VirtualHostNode.class, attributes, _broker);
        _nodes.add(node);
        return node;
    }

    public BDBHAVirtualHostNode<?> recoverHaVHN(UUID id, Map<String, Object> attributes)
    {
        Map<String,ConfiguredObjectRecord> parents = new HashMap<>();
        parents.put(Broker.class.getSimpleName(),new ConfiguredObjectRecordImpl(_broker.getId(), Broker.class.getSimpleName(), Collections.<String, Object>singletonMap(Broker.NAME, _broker.getName()) ));
        ConfiguredObjectRecordImpl record = new ConfiguredObjectRecordImpl(id, VirtualHostNode.class.getSimpleName(), attributes, parents );

        @SuppressWarnings("unchecked")
        UnresolvedConfiguredObject<BDBHAVirtualHostNodeImpl> unresolved =  _objectFactory.recover(record, _broker);
        BDBHAVirtualHostNode<?> node = unresolved.resolve();
        node.open();
        _nodes.add(node);
        return node;
    }

    public void assertNodeRole(BDBHAVirtualHostNode<?> node, String... roleName) throws InterruptedException
    {
        int iterationCounter = 0;
        boolean inRole =false;
        do
        {
            for (String role : roleName)
            {
                if (role.equals(node.getRole()))
                {
                    inRole = true;
                    break;
                }
            }
            if (!inRole)
            {
                Thread.sleep(50);
            }
            iterationCounter++;
        }
        while(!inRole && iterationCounter<100);
        assertTrue("Node " + node.getName() + " did not transit into role " + Arrays.toString(roleName), inRole);
    }

    public BDBHAVirtualHostNode<?> createAndStartHaVHN(Map<String, Object> attributes)  throws InterruptedException
    {
        BDBHAVirtualHostNode<?> node = createHaVHN(attributes);
        return startNodeAndWait(node);
    }

    public BDBHAVirtualHostNode<?> startNodeAndWait(BDBHAVirtualHostNode<?> node) throws InterruptedException
    {
        node.start();
        assertNodeRole(node, "MASTER", "REPLICA");
        assertEquals("Unexpected node state", State.ACTIVE, node.getState());
        return node;
    }

    public String getMessageStorePath()
    {
        return _bdbStorePath.getAbsolutePath();
    }

    public Broker getBroker()
    {
        return _broker;
    }

    Map<String, Object> createNodeAttributes(String nodeName, String groupName, String address, String helperAddress)
    {
        Map<String, Object> node1Attributes = new HashMap<String, Object>();
        node1Attributes.put(BDBHAVirtualHostNode.ID, UUID.randomUUID());
        node1Attributes.put(BDBHAVirtualHostNode.TYPE, "BDB_HA");
        node1Attributes.put(BDBHAVirtualHostNode.NAME, nodeName);
        node1Attributes.put(BDBHAVirtualHostNode.GROUP_NAME, groupName);
        node1Attributes.put(BDBHAVirtualHostNode.ADDRESS, address);
        node1Attributes.put(BDBHAVirtualHostNode.HELPER_ADDRESS, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.STORE_PATH, getMessageStorePath() + File.separator + nodeName);

        Map<String, String> repConfig = new HashMap<String, String>();
        repConfig.put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "2 s");
        repConfig.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "2 s");

        node1Attributes.put(BDBHAVirtualHostNode.CONTEXT, repConfig);

        return node1Attributes;
    }
}
