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

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.HighAvailabilityMessages;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.store.berkeleydb.NoopConfigurationChangeListener;
import org.apache.qpid.test.utils.PortHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.hamcrest.Description;
import org.mockito.ArgumentMatcher;

/**
 * Class to test that specific VHN operations result in the expected Operational Log message(s) being performed.
 */
public class BDBHAVirtualHostNodeOperationalLoggingTest extends QpidTestCase
{
    private BDBHAVirtualHostNodeTestHelper _helper;
    private EventLogger _eventLogger;
    private PortHelper _portHelper = new PortHelper();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _helper = new BDBHAVirtualHostNodeTestHelper(getTestName());
        _eventLogger = mock(EventLogger.class);
        SystemConfig<?> context = (SystemConfig<?>) _helper.getBroker().getParent(SystemConfig.class);
        when(context.getEventLogger()).thenReturn(_eventLogger);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            _helper.tearDown();
        }
        finally
        {
            super.tearDown();
        }

        _portHelper.waitUntilAllocatedPortsAreFree();
    }

    public void testCreate() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);

        _helper.assertNodeRole(node1, NodeRole.MASTER);

        assertEquals("Unexpected VHN log subject", "[grp(/group)/vhn(/node1)] ", node1.getVirtualHostNodeLogSubject().getLogString());
        assertEquals("Unexpected group log subject", "[grp(/group)] ", node1.getGroupLogSubject().getLogString());

        String expectedMessage = HighAvailabilityMessages.CREATED().toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.CREATED_LOG_HIERARCHY)));

        expectedMessage = HighAvailabilityMessages.ROLE_CHANGED(node1.getName(), node1.getAddress(), NodeRole.WAITING.name(), NodeRole.MASTER.name()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getGroupLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ROLE_CHANGED_LOG_HIERARCHY)));
    }

    public void testDelete() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        reset(_eventLogger);

        node1.delete();

        String expectedMessage = HighAvailabilityMessages.DELETED().toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DELETED_LOG_HIERARCHY)));

    }

    public void testSetPriority() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        reset(_eventLogger);

        node1.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.PRIORITY, 10));

        // make sure that task executor thread finishes all scheduled tasks
        node1.stop();

        String expectedMessage = HighAvailabilityMessages.PRIORITY_CHANGED("10").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.PRIORITY_CHANGED_LOG_HIERARCHY)));
    }

    public void testSetQuorumOverride() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        reset(_eventLogger);

        node1.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.QUORUM_OVERRIDE, 1));

        // make sure that task executor thread finishes all scheduled tasks
        node1.stop();

        String expectedMessage = HighAvailabilityMessages.QUORUM_OVERRIDE_CHANGED("1").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY)));
    }

    public void testSetDesignatedPrimary() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        reset(_eventLogger);

        node1.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true));

        // make sure that task executor thread finishes all scheduled tasks
        node1.stop();

        String expectedMessage = HighAvailabilityMessages.DESIGNATED_PRIMARY_CHANGED("true").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY)));
    }

    public void testRemoteNodeAdded() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        int node2PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber, node2PortNumber);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        reset(_eventLogger);


        Map<String, Object> node2Attributes = _helper.createNodeAttributes("node2", groupName, "localhost:" + node2PortNumber, helperAddress, nodeName);
        BDBHAVirtualHostNodeImpl node2 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node2Attributes);
        _helper.awaitRemoteNodes(node1, 1);

        // make sure that task executor thread finishes all scheduled tasks
        node2.stop();

        // Verify ADDED message from node1 when it discovers node2 has been added
        String expectedMessage = HighAvailabilityMessages.ADDED(node2.getName(), node2.getAddress()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getGroupLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ADDED_LOG_HIERARCHY)));
    }

    public void testRemoteNodeRemoved() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        int node2PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber, node2PortNumber);
        node1Attributes.put(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        resetEventLogger();

        Map<String, Object> node2Attributes = _helper.createNodeAttributes("node2", groupName, "localhost:" + node2PortNumber, helperAddress, nodeName);
        BDBHAVirtualHostNodeImpl node2 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node2Attributes);
        _helper.awaitRemoteNodes(node1, 1);

        reset(_eventLogger);

        node2.delete();
        _helper.awaitRemoteNodes(node1, 0);

        // make sure that task executor thread finishes all scheduled tasks
        node1.stop();

        String expectedMessage = HighAvailabilityMessages.REMOVED(node2.getName(), node2.getAddress()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getGroupLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.REMOVED_LOG_HIERARCHY)));
    }

    public void testRemoteNodeDetached() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        int node2PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber, node2PortNumber);
        node1Attributes.put(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);

        final CountDownLatch remoteNodeAdded = new CountDownLatch(1);
        node1.addChangeListener(new NoopConfigurationChangeListener()
        {
            @Override
            public void childAdded(ConfiguredObject<?> object, ConfiguredObject<?> child)
            {
                if (child instanceof BDBHARemoteReplicationNode)
                {
                    remoteNodeAdded.countDown();
                }
            }
        });
        Map<String, Object> node2Attributes = _helper.createNodeAttributes("node2", groupName, "localhost:" + node2PortNumber, helperAddress, nodeName);
        BDBHAVirtualHostNodeImpl node2 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node2Attributes);

        assertTrue("Remote node was not added during expected period of time", remoteNodeAdded.await(10, TimeUnit.SECONDS));


        BDBHARemoteReplicationNodeImpl remoteNode = (BDBHARemoteReplicationNodeImpl)node1.getRemoteReplicationNodes().iterator().next();
        waitForRemoteNodeToAttainRole(remoteNode, EnumSet.of(NodeRole.REPLICA));


        reset(_eventLogger);

        // close remote node
        node2.close();


        waitForRemoteNodeToAttainRole(remoteNode, EnumSet.of(NodeRole.UNREACHABLE));

        // make sure that task executor thread finishes all scheduled tasks
        node1.stop();

        // verify that remaining node issues the DETACHED operational logging for remote node
        String expectedMessage = HighAvailabilityMessages.LEFT(node2.getName(), node2.getAddress()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getGroupLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.LEFT_LOG_HIERARCHY)));
    }


    public void testRemoteNodeReAttached() throws Exception
    {
        int node1PortNumber = _portHelper.getNextAvailable();
        int node2PortNumber = _portHelper.getNextAvailable();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress, nodeName, node1PortNumber, node2PortNumber);
        node1Attributes.put(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, NodeRole.MASTER);

        resetEventLogger();

        Map<String, Object> node2Attributes = _helper.createNodeAttributes("node2", groupName, "localhost:" + node2PortNumber, helperAddress, nodeName);
        BDBHAVirtualHostNodeImpl node2 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node2Attributes);
        _helper.awaitRemoteNodes(node1, 1);

        BDBHARemoteReplicationNodeImpl remoteNode = (BDBHARemoteReplicationNodeImpl)node1.getRemoteReplicationNodes().iterator().next();
        waitForRemoteNodeToAttainRole(remoteNode, EnumSet.of(NodeRole.REPLICA));

        node2.close();

        waitForRemoteNodeToAttainRole(remoteNode, EnumSet.of(NodeRole.UNREACHABLE));

        reset(_eventLogger);

        node2Attributes.put(BDBHAVirtualHostNode.PERMITTED_NODES,
                            node1Attributes.get(BDBHAVirtualHostNode.PERMITTED_NODES));
        node2 = (BDBHAVirtualHostNodeImpl)_helper.recoverHaVHN(node2.getId(), node2Attributes);
        _helper.assertNodeRole(node2, NodeRole.REPLICA, NodeRole.MASTER);
        waitForRemoteNodeToAttainRole(remoteNode, EnumSet.of(NodeRole.REPLICA, NodeRole.MASTER));

        // make sure that task executor thread finishes all scheduled tasks
        node1.stop();

        final String expectedMessage = HighAvailabilityMessages.JOINED(node2.getName(), node2.getAddress()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getGroupLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.JOINED_LOG_HIERARCHY)));
    }

    private void waitForRemoteNodeToAttainRole(BDBHARemoteReplicationNode remoteNode, EnumSet<NodeRole> desiredRoles) throws Exception
    {
        int counter = 0;
        while (!desiredRoles.contains(remoteNode.getRole())  && counter<50)
        {
            Thread.sleep(100);
            counter++;
        }
    }

    private EventLogger resetEventLogger()
    {
        EventLogger eventLogger = mock(EventLogger.class);
        SystemConfig<?> context = (SystemConfig<?>) _helper.getBroker().getParent(SystemConfig.class);
        when(context.getEventLogger()).thenReturn(eventLogger);
        return eventLogger;
    }

    class LogMessageMatcher extends ArgumentMatcher<LogMessage>
    {
        private String _expectedMessage;
        private String _expectedMessageFailureDescription = null;
        private String _expectedHierarchy;
        private String _expectedHierarchyFailureDescription = null;

        public LogMessageMatcher(String expectedMessage, String expectedHierarchy)
        {
            _expectedMessage = expectedMessage;
            _expectedHierarchy = expectedHierarchy;
        }

        @Override
        public boolean matches(Object argument)
        {
            LogMessage logMessage = (LogMessage)argument;

            boolean expectedMessageMatches = _expectedMessage.equals(logMessage.toString());
            if (!expectedMessageMatches)
            {
                _expectedMessageFailureDescription = "Expected message does not match. Expected: " + _expectedMessage + ", actual: " + logMessage.toString();
            }
            boolean expectedHierarchyMatches = _expectedHierarchy.equals(logMessage.getLogHierarchy());
            if (!expectedHierarchyMatches)
            {
                _expectedHierarchyFailureDescription = "Expected hierarchy does not match. Expected: " + _expectedHierarchy + ", actual: " + logMessage.getLogHierarchy();
            }

            return expectedMessageMatches && expectedHierarchyMatches;
        }

        @Override
        public void describeTo(Description description)
        {
            if (_expectedMessageFailureDescription != null)
            {
                description.appendText(_expectedMessageFailureDescription);
            }
            if (_expectedHierarchyFailureDescription != null)
            {
                description.appendText(_expectedHierarchyFailureDescription);
            }
        }
    }

    class LogSubjectMatcher extends ArgumentMatcher<LogSubject>
    {
        private LogSubject _logSubject;
        private String _failureDescription = null;

        public LogSubjectMatcher(LogSubject logSubject)
        {
            _logSubject = logSubject;
        }

        @Override
        public boolean matches(Object argument)
        {
            final LogSubject logSubject = (LogSubject)argument;
            final boolean foundAMatch = _logSubject.toLogString().equals(logSubject.toLogString());
            if (!foundAMatch)
            {
                _failureDescription = "LogSubject does not match. Expected: " + _logSubject.toLogString() + ", actual : " + logSubject.toLogString();
            }
            return foundAMatch;
        }

        @Override
        public void describeTo(Description description)
        {
            if (_failureDescription != null)
            {
                description.appendText(_failureDescription);
            }
        }
    }
}
