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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

import com.sleepycat.je.rep.ReplicatedEnvironment;

public class LocalReplicationNodeTest extends QpidTestCase
{

    private static final Object INVALID_VALUE = new Object();
    private UUID _id;
    private VirtualHost _virtualHost;
    private TaskExecutor _taskExecutor;
    private ReplicatedEnvironmentFacade _facade;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _taskExecutor = mock(TaskExecutor.class);
        _virtualHost = mock(VirtualHost.class);
        _facade = mock(ReplicatedEnvironmentFacade.class);
    }

    @Override
    public void tearDown() throws Exception
    {
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

    public void testGetValuesFromReplicatedEnvironmentFacade()
    {
        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);

        assertNull("Unexpected role attribute", node.getAttribute(ReplicationNode.ROLE));
        assertNull("Unexpected join time attribute", node.getAttribute(ReplicationNode.JOIN_TIME));
        assertNull("Unexpected last transaction id", node.getAttribute(ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID));
        assertEquals("Unexpected priority attribute", LocalReplicationNode.DEFAULT_PRIORITY, node.getAttribute(ReplicationNode.PRIORITY));
        assertEquals("Unexpected quorum override attribute", LocalReplicationNode.DEFAULT_QUORUM_OVERRIDE, node.getAttribute(ReplicationNode.QUORUM_OVERRIDE));
        assertEquals("Unexpected designated primary attribute", LocalReplicationNode.DEFAULT_DESIGNATED_PRIMARY, node.getAttribute(ReplicationNode.DESIGNATED_PRIMARY));

        String masterState = "MASTER";
        long joinTime = System.currentTimeMillis();
        long lastKnowTransactionId = 1000l;
        boolean designatedPrimary = true;
        int priority = 2;
        int quorumOverride = 3;

        when(_facade.getNodeState()).thenReturn(masterState);
        when(_facade.getJoinTime()).thenReturn(joinTime);
        when(_facade.getLastKnownReplicationTransactionId()).thenReturn(lastKnowTransactionId);
        when(_facade.isDesignatedPrimary()).thenReturn(designatedPrimary);
        when(_facade.getPriority()).thenReturn(priority);
        when(_facade.getElectableGroupSizeOverride()).thenReturn(quorumOverride);

        node.setReplicatedEnvironmentFacade(_facade);
        assertEquals("Unexpected role attribute", masterState, node.getAttribute(ReplicationNode.ROLE));
        assertEquals("Unexpected join time attribute", joinTime, node.getAttribute(ReplicationNode.JOIN_TIME));
        assertEquals("Unexpected last transaction id attribute", lastKnowTransactionId, node.getAttribute(ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID));
        assertEquals("Unexpected priority attribute", priority, node.getAttribute(ReplicationNode.PRIORITY));
        assertEquals("Unexpected quorum override attribute", quorumOverride, node.getAttribute(ReplicationNode.QUORUM_OVERRIDE));
    }

    public void testSetDesignatedPrimary() throws Exception
    {
        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);
        node.setReplicatedEnvironmentFacade(_facade);

        node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.DESIGNATED_PRIMARY, true));

        verify(_facade).setDesignatedPrimary(true);

        node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.DESIGNATED_PRIMARY, false));
        verify(_facade).setDesignatedPrimary(false);
    }

    public void testSetPriority() throws Exception
    {
        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);
        node.setReplicatedEnvironmentFacade(_facade);
        node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.PRIORITY, 100));

        verify(_facade).setPriority(100);
    }

    public void testSetQuorumOverride() throws Exception
    {
        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);
        node.setReplicatedEnvironmentFacade(_facade);

        node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.QUORUM_OVERRIDE, 10));

        verify(_facade).setElectableGroupSizeOverride(10);
    }

    public void testSetRole() throws Exception
    {
        when(_facade.getNodeState()).thenReturn(ReplicatedEnvironment.State.REPLICA.name());

        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);
        node.setReplicatedEnvironmentFacade(_facade);

        node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.ROLE, ReplicatedEnvironment.State.MASTER.name()));

        verify(_facade).transferMasterToSelfAsynchronously();
    }

    public void testSetRoleToReplicaUnsupported() throws Exception
    {
        when(_facade.getNodeState()).thenReturn(ReplicatedEnvironment.State.REPLICA.name());

        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);
        node.setReplicatedEnvironmentFacade(_facade);

        try
        {
            node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.ROLE, ReplicatedEnvironment.State.REPLICA.name()));
            fail("Exception not thrown");
        }
        catch(IllegalConfigurationException e)
        {
            // PASS
        }
    }

    public void testSetRoleWhenCurrentRoleNotRepliaIsUnsupported() throws Exception
    {
        when(_facade.getNodeState()).thenReturn(ReplicatedEnvironment.State.MASTER.name());

        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);
        node.setReplicatedEnvironmentFacade(_facade);

        try
        {
            node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.ROLE, ReplicatedEnvironment.State.MASTER.name()));
            fail("Exception not thrown");
        }
        catch(IllegalConfigurationException e)
        {
            // PASS
        }
    }

    public void testSetImmutableAttributesThrowException() throws Exception
    {
        Map<String, Object> changeAttributeMap = new HashMap<String, Object>();
        changeAttributeMap.put(ReplicationNode.GROUP_NAME, "newGroupName");
        changeAttributeMap.put(ReplicationNode.HELPER_HOST_PORT, "newhost:1234");
        changeAttributeMap.put(ReplicationNode.HOST_PORT, "newhost:1234");
        changeAttributeMap.put(ReplicationNode.COALESCING_SYNC, Boolean.FALSE);
        changeAttributeMap.put(ReplicationNode.DURABILITY, "durability");
        changeAttributeMap.put(ReplicationNode.JOIN_TIME, 1000l);
        changeAttributeMap.put(ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID, 10001l);
        changeAttributeMap.put(ReplicationNode.NAME, "newName");
        changeAttributeMap.put(ReplicationNode.STORE_PATH, "/not/used");
        changeAttributeMap.put(ReplicationNode.PARAMETERS, Collections.emptyMap());
        changeAttributeMap.put(ReplicationNode.REPLICATION_PARAMETERS, Collections.emptyMap());

        for (Entry<String, Object> entry : changeAttributeMap.entrySet())
        {
            assertSetAttributesThrowsException(entry.getKey(), entry.getValue());
        }
    }

    private void assertSetAttributesThrowsException(String attributeName, Object attributeValue)
    {
        LocalReplicationNode node = new LocalReplicationNode(_id, createValidAttributes(), _virtualHost, _taskExecutor);

        try
        {
            node.setAttributes(Collections.<String, Object>singletonMap(attributeName, attributeValue));
            fail("Operation to change attribute '" + attributeName + "' should fail");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    private Map<String, Object> createValidAttributes()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ReplicationNode.NAME, "testNode");
        attributes.put(ReplicationNode.GROUP_NAME, "testGroup");
        attributes.put(ReplicationNode.HOST_PORT, "localhost:5000");
        attributes.put(ReplicationNode.HELPER_HOST_PORT, "localhost:5001");
        attributes.put(ReplicationNode.STORE_PATH, TMP_FOLDER + File.separator + getTestName());
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
