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

import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.berkeleydb.ReplicatedEnvironmentFacade;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

public class LocalReplicationNodeTest extends QpidTestCase
{

    private static final Object INVALID_VALUE = new Object();
    private UUID _id;
    private VirtualHost _virtualHost;
    private TaskExecutor _taskExecutor;
    private File _storePath;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = mock(TaskExecutor.class);
        _virtualHost = mock(VirtualHost.class);
        _storePath = new File(TMP_FOLDER, "store-" + System.currentTimeMillis());
    }

    @Override
    public void tearDown() throws Exception
    {
        FileUtils.delete(_storePath, true);
        super.tearDown();
    }

    public void testCreateLocalReplicationNodeWithoutDefaultParametersAndValidParameters()
    {
        Map<String, Object> attributes = createValidAttributes();

        LocalReplicationNode node = new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor);

        assertNodeAttributes(attributes, node);

        for (Map.Entry<String, Object> attributeEntry : LocalReplicationNode.DEFAULTS.entrySet())
        {
            assertEquals("Unexpected attribute value for attribute name " + attributeEntry.getKey(), attributeEntry.getValue(), node.getAttribute(attributeEntry.getKey()));
        }
    }

    public void testCreateLocalReplicationNodeWithoutDefaultParametersAndMissedParameters()
    {
        Map<String, Object> attributes = createValidAttributes();

        for (Map.Entry<String, Object> attributeEntry : attributes.entrySet())
        {
            String name = attributeEntry.getKey();
            Map<String, Object> incompleteAttributes = new HashMap<String, Object>(attributes);
            incompleteAttributes.remove(name);
            try
            {
                new LocalReplicationNode(_id, incompleteAttributes, _virtualHost, _taskExecutor);
                fail("Node creation should fails when attribute " + name + " is missed");
            }
            catch(IllegalConfigurationException e)
            {
                // pass
            }
        }
    }

    public void testCreateLocalReplicationNodeWithoutDefaultParametersAndInvalidParameters()
    {
 
        Map<String, Object> attributes = createValidAttributes();

        for (Map.Entry<String, Object> attributeEntry : attributes.entrySet())
        {
            String name = attributeEntry.getKey();
            Object value = attributeEntry.getValue();
            if (!(value instanceof String))
            {
                Map<String, Object> invalidAttributes = new HashMap<String, Object>(attributes);
                invalidAttributes.put(name, INVALID_VALUE);
                try
                {
                    new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor);
                    fail("Node creation should fails when attribute " + name + " is invalid");
                }
                catch(IllegalConfigurationException e)
                {
                    // pass
                }
            }
        }
    }

    public void testCreateLocalReplicationNodeWithOverriddenDefaultParameters()
    {
        Map<String, Object> attributes = createValidAttributes();
        attributes.put(ReplicationNode.DURABILITY, "SYNC,SYNC,NONE");
        attributes.put(ReplicationNode.COALESCING_SYNC, false);
        attributes.put(ReplicationNode.DESIGNATED_PRIMARY, true);

        LocalReplicationNode node = new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor);

        assertNodeAttributes(attributes, node);
    }

    public void testSetReplicatedEnvironmentFacade()
    {
        Map<String, Object> attributes = createValidAttributes();
        int port = findFreePort();
        String hostPort = "localhost:" + port;
        attributes.put(ReplicationNode.HOST_PORT, hostPort);
        attributes.put(ReplicationNode.HELPER_HOST_PORT, hostPort);
        attributes.put(ReplicationNode.STORE_PATH, hostPort);
        attributes.put(ReplicationNode.DESIGNATED_PRIMARY, true);
        LocalReplicationNode node = new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor);

        assertNull("Unexpected role attribute", node.getAttribute(ReplicationNode.ROLE));
        assertNull("Unexpected quorum override attribute", node.getAttribute(ReplicationNode.QUORUM_OVERRIDE));
        assertNull("Unexpected priority attribute", node.getAttribute(ReplicationNode.PRIORITY));
        assertNull("Unexpected join time attribute", node.getAttribute(ReplicationNode.JOIN_TIME));

        _storePath.mkdirs();
        String name = getTestName();

        ReplicatedEnvironmentFacade facade = null;
        try
        {
            facade = new ReplicatedEnvironmentFacade(name, _storePath.getAbsolutePath(), node, mock(RemoteReplicationNodeFactory.class));
            node.setReplicatedEnvironmentFacade(facade);
            assertEquals("Unexpected role attribute", "MASTER", node.getAttribute(ReplicationNode.ROLE));
            assertEquals("Unexpected quorum override attribute", 0, node.getAttribute(ReplicationNode.QUORUM_OVERRIDE));
            assertEquals("Unexpected priority attribute", 1, node.getAttribute(ReplicationNode.PRIORITY));
            assertNotNull("Unexpected join time attribute", node.getAttribute(ReplicationNode.JOIN_TIME));
            assertNodeAttributes(attributes, node);
        }
        finally
        {
            if (facade != null)
            {
                facade.close();
            }
        }
    }

    private Map<String, Object> createValidAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ReplicationNode.NAME, "testNode");
        attributes.put(ReplicationNode.GROUP_NAME, "testGroup");
        attributes.put(ReplicationNode.HOST_PORT, "localhost:5000");
        attributes.put(ReplicationNode.HELPER_HOST_PORT, "localhost:5001");
        return attributes;
    }

    private void assertNodeAttributes(Map<String, Object> expectedAttributes,
            LocalReplicationNode node)
    {
        for (Map.Entry<String, Object> attributeEntry : expectedAttributes.entrySet())
        {
            assertEquals("Unexpected attribute value for attribute name " + attributeEntry.getKey(), attributeEntry.getValue(), node.getAttribute(attributeEntry.getKey()));
        }
    }

}
