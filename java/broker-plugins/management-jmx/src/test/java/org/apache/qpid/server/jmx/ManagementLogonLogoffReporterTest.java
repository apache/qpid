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
package org.apache.qpid.server.jmx;

import static javax.management.remote.JMXConnectionNotification.OPENED;
import static javax.management.remote.JMXConnectionNotification.CLOSED;
import static javax.management.remote.JMXConnectionNotification.FAILED;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import javax.management.remote.JMXConnectionNotification;
import javax.security.auth.Subject;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.MessageLogger;

import junit.framework.TestCase;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.mockito.ArgumentMatcher;

import java.util.Collections;

public class ManagementLogonLogoffReporterTest extends TestCase
{
    private static final String TEST_JMX_UNIQUE_CONNECTION_ID = "jmxconnectionid1 jmxuser,group";
    private static final Subject TEST_USER = new Subject(false, Collections.singleton(new AuthenticatedPrincipal("jmxuser")), Collections.emptySet(), Collections.emptySet());

    private ManagementLogonLogoffReporter _reporter;
    private UsernameAccessor _usernameAccessor;
    private MessageLogger _messageLogger;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _usernameAccessor = mock(UsernameAccessor.class);
        _messageLogger = mock(MessageLogger.class);
        // Enable messaging so we can valid the generated strings
        when(_messageLogger.isMessageEnabled(anyString())).thenReturn(true);
        EventLogger eventLogger = new EventLogger(_messageLogger);
        _reporter = new ManagementLogonLogoffReporter(eventLogger, _usernameAccessor);
    }

    public void testOpenedNotification()
    {
        when(_usernameAccessor.getSubjectConnectionId(TEST_JMX_UNIQUE_CONNECTION_ID)).thenReturn(TEST_USER);
        JMXConnectionNotification openNotification = createMockNotification(TEST_JMX_UNIQUE_CONNECTION_ID, OPENED);

        _reporter.handleNotification(openNotification, null);

        verify(_messageLogger).message(messageMatch("MNG-1007 : Open : User jmxuser",
                                                        "qpid.message.managementconsole.open"));
    }

    private LogMessage messageMatch(final String message, final String hierarchy)
    {
        return argThat(new ArgumentMatcher<LogMessage>()
        {
            @Override
            public boolean matches(final Object argument)
            {
                LogMessage actual = (LogMessage) argument;
                return actual.getLogHierarchy().equals(hierarchy) &&  actual.toString().equals(message);
            }
        });
    }

    public void testClosedNotification()
    {
        when(_usernameAccessor.getSubjectConnectionId(TEST_JMX_UNIQUE_CONNECTION_ID)).thenReturn(TEST_USER);
        JMXConnectionNotification closeNotification = createMockNotification(TEST_JMX_UNIQUE_CONNECTION_ID, CLOSED);

        _reporter.handleNotification(closeNotification, null);

        verify(_messageLogger).message(messageMatch("MNG-1008 : Close : User jmxuser", "qpid.message.managementconsole.close"));
    }

    public void tesNotifiedForLogOnTypeEvents()
    {
        JMXConnectionNotification openNotification = createMockNotification(TEST_JMX_UNIQUE_CONNECTION_ID, OPENED);
        JMXConnectionNotification closeNotification = createMockNotification(TEST_JMX_UNIQUE_CONNECTION_ID, CLOSED);
        JMXConnectionNotification failedNotification = createMockNotification(TEST_JMX_UNIQUE_CONNECTION_ID, FAILED);

        assertTrue(_reporter.isNotificationEnabled(openNotification));
        assertTrue(_reporter.isNotificationEnabled(closeNotification));
        assertTrue(_reporter.isNotificationEnabled(failedNotification));

        JMXConnectionNotification otherNotification = createMockNotification(TEST_JMX_UNIQUE_CONNECTION_ID, "other");
        assertFalse(_reporter.isNotificationEnabled(otherNotification));
    }

    private JMXConnectionNotification createMockNotification(String connectionId, String notificationType)
    {
        JMXConnectionNotification notification = mock(JMXConnectionNotification.class);
        when(notification.getConnectionId()).thenReturn(connectionId);
        when(notification.getType()).thenReturn(notificationType);
        return notification;
    }
}
