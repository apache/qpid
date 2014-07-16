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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.HighAvailabilityMessages;
import org.apache.qpid.server.model.SystemContext;
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

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _helper = new BDBHAVirtualHostNodeTestHelper(getTestName());
        _eventLogger = mock(EventLogger.class);
        SystemContext<?> context = (SystemContext<?>) _helper.getBroker().getParent(SystemContext.class);
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
    }

    public void testCreate() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);

        _helper.assertNodeRole(node1, "MASTER");

        String expectedMessage = HighAvailabilityMessages.ADDED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ADDED_LOG_HIERARCHY)));

        expectedMessage = HighAvailabilityMessages.ATTACHED(node1.getName(), node1.getGroupName(), "UNKNOWN").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ATTACHED_LOG_HIERARCHY)));


        expectedMessage = HighAvailabilityMessages.STARTED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.STARTED_LOG_HIERARCHY)));

        expectedMessage = HighAvailabilityMessages.ROLE_CHANGED(node1.getName(), node1.getGroupName(), "UNKNOWN", "MASTER").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ROLE_CHANGED_LOG_HIERARCHY)));
    }

    public void testStop() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");
        reset(_eventLogger);

        node1.stop();

        String expectedMessage = HighAvailabilityMessages.DETACHED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DETACHED_LOG_HIERARCHY)));

        expectedMessage = HighAvailabilityMessages.STOPPED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.STOPPED_LOG_HIERARCHY)));
    }

    public void testClose() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        reset(_eventLogger);

        node1.close();

        String expectedMessage = HighAvailabilityMessages.DETACHED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DETACHED_LOG_HIERARCHY)));
    }

    public void testDelete() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        reset(_eventLogger);

        node1.delete();

        String expectedMessage = HighAvailabilityMessages.DETACHED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DETACHED_LOG_HIERARCHY)));

        expectedMessage = HighAvailabilityMessages.DELETED(node1.getName(), node1.getGroupName()).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DELETED_LOG_HIERARCHY)));
    }

    public void testSetPriority() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        reset(_eventLogger);

        node1.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.PRIORITY, 10));

        String expectedMessage = HighAvailabilityMessages.PRIORITY_CHANGED(node1.getName(), node1.getGroupName(), "10").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.PRIORITY_CHANGED_LOG_HIERARCHY)));
    }

    public void testSetQuorumOverride() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        reset(_eventLogger);

        node1.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.QUORUM_OVERRIDE, 1));

        String expectedMessage = HighAvailabilityMessages.QUORUM_OVERRIDE_CHANGED(node1.getName(), node1.getGroupName(), "1").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.QUORUM_OVERRIDE_CHANGED_LOG_HIERARCHY)));
    }

    public void testSetDesignatedPrimary() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        reset(_eventLogger);

        node1.setAttributes(Collections.<String, Object>singletonMap(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true));

        String expectedMessage = HighAvailabilityMessages.DESIGNATED_PRIMARY_CHANGED(node1.getName(), node1.getGroupName(), "true").toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DESIGNATED_PRIMARY_CHANGED_LOG_HIERARCHY)));
    }

    public void testRemoteNodeAdded() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        reset(_eventLogger);

        int node2PortNumber = getNextAvailable(node1PortNumber + 1);
        Map<String, Object> node2Attributes = _helper.createNodeAttributes("node2", groupName, "localhost:" + node2PortNumber, helperAddress);
        BDBHAVirtualHostNodeImpl node2 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node2Attributes);
        _helper.awaitRemoteNodes(node1, 1);

        // Verify ADDED message from node2 when its created
        String expectedMessage = HighAvailabilityMessages.ADDED(node2.getName(), groupName).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node2.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ADDED_LOG_HIERARCHY)));

        // Verify ADDED message from node1 when it discovers node2 has been added
        expectedMessage = HighAvailabilityMessages.ADDED(node2.getName(), groupName).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.ADDED_LOG_HIERARCHY)));
    }

    public void testRemoteNodeRemoved() throws Exception
    {
        int node1PortNumber = findFreePort();
        String helperAddress = "localhost:" + node1PortNumber;
        String groupName = "group";
        String nodeName = "node1";

        Map<String, Object> node1Attributes = _helper.createNodeAttributes(nodeName, groupName, helperAddress, helperAddress);
        node1Attributes.put(BDBHAVirtualHostNode.DESIGNATED_PRIMARY, true);
        BDBHAVirtualHostNodeImpl node1 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node1Attributes);
        _helper.assertNodeRole(node1, "MASTER");

        resetEventLogger();

        int node2PortNumber = getNextAvailable(node1PortNumber + 1);
        Map<String, Object> node2Attributes = _helper.createNodeAttributes("node2", groupName, "localhost:" + node2PortNumber, helperAddress);
        BDBHAVirtualHostNodeImpl node2 = (BDBHAVirtualHostNodeImpl)_helper.createHaVHN(node2Attributes);
        _helper.awaitRemoteNodes(node1, 1);

        reset(_eventLogger);

        node2.delete();
        _helper.awaitRemoteNodes(node1, 0);

        String expectedMessage = HighAvailabilityMessages.DELETED(node2.getName(), groupName).toString();
        verify(_eventLogger).message(argThat(new LogSubjectMatcher(node1.getVirtualHostNodeLogSubject())),
                argThat(new LogMessageMatcher(expectedMessage, HighAvailabilityMessages.DELETED_LOG_HIERARCHY)));
    }

    private EventLogger resetEventLogger()
    {
        EventLogger eventLogger = mock(EventLogger.class);
        SystemContext<?> context = (SystemContext<?>) _helper.getBroker().getParent(SystemContext.class);
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
