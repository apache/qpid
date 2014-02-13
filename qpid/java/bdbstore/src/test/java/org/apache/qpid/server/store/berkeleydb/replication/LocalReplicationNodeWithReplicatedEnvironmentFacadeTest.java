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

import static org.apache.qpid.server.model.ReplicationNode.REPLICATION_PARAMETERS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class LocalReplicationNodeWithReplicatedEnvironmentFacadeTest extends QpidTestCase
{
   private UUID _id;
   private VirtualHost _virtualHost;
   private TaskExecutor _taskExecutor;
   private String _evironmentWorkingFolder;
   private LocalReplicationNode _node;

   @Override
   public void setUp() throws Exception
   {
       super.setUp();
       _taskExecutor = mock(TaskExecutor.class);
       when(_taskExecutor.isTaskExecutorThread()).thenReturn(true);
       _virtualHost = mock(VirtualHost.class);
       when(_virtualHost.getAttribute(VirtualHost.REMOTE_REPLICATION_NODE_MONITOR_INTERVAL)).thenReturn(100l);
       when(_virtualHost.getTaskExecutor()).thenReturn(_taskExecutor);
       _evironmentWorkingFolder = TMP_FOLDER + File.separator + getTestName();
   }

   @Override
   public void tearDown() throws Exception
   {
       try
       {
           destroyNode(_node);
       }
       finally
       {
           super.tearDown();
       }
   }

   public void testAttainDesiredState() throws Exception
   {
       int port = findFreePort();
       Map<String, Object> attributes = createValidAttributes(port, port);

       _node = new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor, new NodeReplicatedEnvironmentFacadeFactory());

       assertEquals("Unexpected state", State.INITIALISING, _node.getAttribute(ReplicationNode.STATE));

       _node.attainDesiredState();

       assertEquals("Unexpected state after attaining desired state", State.ACTIVE,
               _node.getAttribute(ReplicationNode.STATE));
       CountDownLatch latch = createMasterStateChangeAwaiter(_node);
       assertTrue("Transistion to master did not happen", latch.await(5, TimeUnit.SECONDS));

       assertEquals("Unexpected role attribute", "MASTER", _node.getAttribute(ReplicationNode.ROLE));
   }

   public void testSetDesiredState() throws Exception
   {
       int port = findFreePort();
       _node = createMasterNode(port);

       assertEquals("Unexpected state after attaining the desired state", State.ACTIVE,
               _node.getAttribute(ReplicationNode.STATE));
       _node.setDesiredState(State.ACTIVE, State.STOPPED);

       assertEquals("Unexpected state after stop", State.STOPPED,
               _node.getAttribute(ReplicationNode.STATE));

       _node.setDesiredState(State.STOPPED, State.ACTIVE);

       assertEquals("Unexpected state after activation after stop", State.ACTIVE,
               _node.getAttribute(ReplicationNode.STATE));

       _node.setDesiredState(State.ACTIVE, State.DELETED);

       assertEquals("Unexpected state after deletion", State.DELETED,
               _node.getAttribute(ReplicationNode.STATE));

       assertEquals("Unexpected facade state after node deletion",
               org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade.State.CLOSED,
               _node.getReplicatedEnvironmentFacade().getFacadeState());
   }

   public void testGetValuesFromReplicatedEnvironmentFacade() throws Exception
   {
       int port = findFreePort();
       Map<String, Object> attributes = createValidAttributes(port, port);

       _node = new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor, new NodeReplicatedEnvironmentFacadeFactory());

       assertNull("Unexpected role attribute", _node.getAttribute(ReplicationNode.ROLE));
       assertNull("Unexpected join time attribute", _node.getAttribute(ReplicationNode.JOIN_TIME));
       assertNull("Unexpected last transaction id",
               _node.getAttribute(ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID));
       assertEquals("Unexpected priority attribute", LocalReplicationNode.DEFAULT_PRIORITY,
               _node.getAttribute(ReplicationNode.PRIORITY));
       assertEquals("Unexpected quorum override attribute", LocalReplicationNode.DEFAULT_QUORUM_OVERRIDE,
               _node.getAttribute(ReplicationNode.QUORUM_OVERRIDE));
       assertEquals("Unexpected designated primary attribute", LocalReplicationNode.DEFAULT_DESIGNATED_PRIMARY,
               _node.getAttribute(ReplicationNode.DESIGNATED_PRIMARY));
       assertNull("Unexpected environment facade value", _node.getReplicatedEnvironmentFacade());

       _node.attainDesiredState();

       CountDownLatch latch = createMasterStateChangeAwaiter(_node);
       assertTrue("Transistion to master did not happen", latch.await(5, TimeUnit.SECONDS));

       assertEquals("Unexpected role attribute", "MASTER", _node.getAttribute(ReplicationNode.ROLE));

       boolean designatedPrimary = true;
       int priority = 2;
       int quorumOverride = 3;

       ReplicatedEnvironmentFacade facade = _node.getReplicatedEnvironmentFacade();
       facade.setDesignatedPrimaryInternal(designatedPrimary);
       facade.setElectableGroupSizeOverrideInternal(quorumOverride);
       facade.setPriorityInternal(priority);

       assertEquals("Unexpected priority attribute", priority, _node.getAttribute(ReplicationNode.PRIORITY));
       assertEquals("Unexpected quorum override attribute", quorumOverride,
               _node.getAttribute(ReplicationNode.QUORUM_OVERRIDE));
       long lastKnowTransactionId = ((Number) _node.getAttribute(ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID)).longValue();
       assertTrue("Unexpected last transaction id attribute: " + lastKnowTransactionId, lastKnowTransactionId > 0);
       long joinTime = ((Number) _node.getAttribute(ReplicationNode.JOIN_TIME)).longValue();
       assertTrue("Unexpected join time attribute:" + joinTime, joinTime > 0);
   }

   public void testSetRole() throws Exception
   {
       int port = findFreePort();
       _node = createMasterNode(port);

       int replicaPort = getNextAvailable(port + 1);
       Map<String, Object> replicaAttributes = createValidAttributes(replicaPort, port);
       String replicaEnvironmentFolder = _evironmentWorkingFolder + "-replica";
       replicaAttributes.put(ReplicationNode.STORE_PATH, replicaEnvironmentFolder);
       replicaAttributes.put(ReplicationNode.NAME, "testNode2");
       replicaAttributes.put(ReplicationNode.DESIGNATED_PRIMARY, true);
       LocalReplicationNode node = new LocalReplicationNode(_id, replicaAttributes, _virtualHost, _taskExecutor, new NodeReplicatedEnvironmentFacadeFactory());
       node.attainDesiredState();
       try
       {
           CountDownLatch replicaLatch = createMasterStateChangeAwaiter(node);
           node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.ROLE, ReplicatedEnvironment.State.MASTER.name()));

           assertTrue("Transistion to master did not happen", replicaLatch.await(10, TimeUnit.SECONDS));
       }
       finally
       {
           destroyNode(node);
       }
   }

   public void testSetRoleToReplicaUnsupported() throws Exception
   {
       int port = findFreePort();
       _node = createMasterNode(port);

       try
       {
           _node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.ROLE, ReplicatedEnvironment.State.REPLICA.name()));
           fail("Exception not thrown");
       }
       catch(IllegalConfigurationException e)
       {
           // PASS
       }
   }

   public void testSetRoleWhenCurrentRoleNotRepliaIsUnsupported() throws Exception
   {
       int port = findFreePort();
       _node = createMasterNode(port);

       try
       {
           _node.setAttributes(Collections.<String, Object>singletonMap(ReplicationNode.ROLE, ReplicatedEnvironment.State.MASTER.name()));
           fail("Exception not thrown");
       }
       catch(IllegalConfigurationException e)
       {
           // PASS
       }
   }

   private Map<String, Object> createValidAttributes(int port, int helperPort)
   {
       Map<String, Object> attributes = new HashMap<String, Object>();
       attributes.put(ReplicationNode.NAME, "testNode");
       attributes.put(ReplicationNode.GROUP_NAME, "testGroup");
       attributes.put(ReplicationNode.HOST_PORT, "localhost:" + port);
       attributes.put(ReplicationNode.HELPER_HOST_PORT, "localhost:" + helperPort);
       attributes.put(ReplicationNode.STORE_PATH, _evironmentWorkingFolder);
       Map<String, String> repConfig = new HashMap<String, String>();
       repConfig.put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "1 s");
       repConfig.put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "1 s");
       attributes.put(REPLICATION_PARAMETERS, repConfig);
       return attributes;
   }

   private void destroyNode(LocalReplicationNode node)
   {
       if (node != null)
       {
           try
           {
               node.setDesiredState(node.getActualState(), State.DELETED);
           }
           finally
           {
               FileUtils.delete(new File((String) node.getAttribute(ReplicationNode.STORE_PATH)), true);
           }
       }
   }

   private CountDownLatch createMasterStateChangeAwaiter(LocalReplicationNode node)
   {
       ReplicatedEnvironmentFacade facade = node.getReplicatedEnvironmentFacade();
       final CountDownLatch latch = new CountDownLatch(1);
       facade.setStateChangeListener(new StateChangeListener()
       {
           @Override
           public void stateChange(StateChangeEvent stateEvent) throws RuntimeException
           {
               if (stateEvent.getState() == com.sleepycat.je.rep.ReplicatedEnvironment.State.MASTER)
               {
                   latch.countDown();
               }
           }
       });
       return latch;
   }

   private LocalReplicationNode createMasterNode(int port) throws InterruptedException
   {
       Map<String, Object> attributes = createValidAttributes(port, port);
       LocalReplicationNode node = new LocalReplicationNode(_id, attributes, _virtualHost, _taskExecutor, new NodeReplicatedEnvironmentFacadeFactory());
       node.attainDesiredState();

       assertEquals("Unexpected state after attaining desired state", State.ACTIVE,
               node.getAttribute(ReplicationNode.STATE));
       CountDownLatch latch = createMasterStateChangeAwaiter(node);
       assertTrue("Transistion to master did not happen", latch.await(5, TimeUnit.SECONDS));
       assertEquals("Unexpected role attribute", "MASTER", node.getAttribute(ReplicationNode.ROLE));
       return  node;
   }
}

