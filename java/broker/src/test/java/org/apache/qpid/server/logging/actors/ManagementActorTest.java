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
package org.apache.qpid.server.logging.actors;

import javax.security.auth.Subject;

import org.apache.qpid.server.security.auth.TestPrincipalUtils;

import java.security.PrivilegedAction;
import java.util.List;

public class ManagementActorTest extends BaseActorTestCase
{

    private static final String IP = "127.0.0.1";
    private static final String CONNECTION_ID = "1";
    private String _threadName;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        setAmqpActor(new ManagementActor(getRootLogger()));

        // Set the thread name to be the same as a RMI JMX Connection would use
        _threadName = Thread.currentThread().getName();
        Thread.currentThread().setName("RMI TCP Connection(" + CONNECTION_ID + ")-" + IP);
    }

    @Override
    public void tearDown() throws Exception
    {
        Thread.currentThread().setName(_threadName);
        super.tearDown();
    }

    /**
     * Test the AMQPActor logging as a Connection level.
     *
     * The test sends a message then verifies that it entered the logs.
     *
     * The log message should be fully replaced (no '{n}' values) and should
     * not contain any channel identification.
     */
    public void testConnection()
    {
        final String message = sendTestLogMessage(getAmqpActor());

        List<Object> logs = getRawLogger().getLogMessages();

        assertEquals("Message log size not as expected.", 1, logs.size());

        // Verify that the logged message is present in the output
        assertTrue("Message was not found in log message",
                   logs.get(0).toString().contains(message));

        // Verify that all the values were presented to the MessageFormatter
        // so we will not end up with '{n}' entries in the log.
        assertFalse("Verify that the string does not contain any '{'.",
                    logs.get(0).toString().contains("{"));

        // Verify that the message has the correct type
        assertTrue("Message does not contain the [mng: prefix",
                   logs.get(0).toString().contains("[mng:"));

        // Verify that the logged message does not contains the 'ch:' marker
        assertFalse("Message was logged with a channel identifier." + logs.get(0),
                    logs.get(0).toString().contains("/ch:"));

        // Verify that the message has the right values
        assertTrue("Message contains the [mng: prefix",
                   logs.get(0).toString().contains("[mng:N/A(" + IP + ")"));
    }

    /**
     * Tests appearance of principal name in log message
     */
    public void testSubjectPrincipalNameAppearance()
    {
        Subject subject = TestPrincipalUtils.createTestSubject("guest");

        final String message = Subject.doAs(subject, new PrivilegedAction<String>()
        {
            public String run()
            {
                return sendTestLogMessage(getAmqpActor());
            }
        });

        // Verify that the log message was created
        assertNotNull("Test log message is not created!", message);

        List<Object> logs = getRawLogger().getLogMessages();

        // Verify that at least one log message was added to log
        assertEquals("Message log size not as expected.", 1, logs.size());

        String logMessage = logs.get(0).toString();

        // Verify that the logged message is present in the output
        assertTrue("Message was not found in log message", logMessage.contains(message));

        // Verify that the message has the right principal value
        assertTrue("Message contains the [mng: prefix", logMessage.contains("[mng:guest(" + IP + ")"));
    }

    public void testGetLogMessageWithSubject()
    {
        assertLogMessageInRMIThreadWithPrincipal("RMI TCP Connection(" + CONNECTION_ID + ")-" + IP, "my_principal");
    }

    public void testGetLogMessageWithoutSubjectButWithActorPrincipal()
    {
        String principalName = "my_principal";
        setAmqpActor(new ManagementActor(getRootLogger(), principalName));
        String message = getAmqpActor().getLogMessage();
        assertEquals("Unexpected log message", "[mng:" + principalName + "(" + IP + ")] ", message);
    }

    /** It's necessary to test successive calls because ManagementActor caches its log message based on thread and principal name */
    public void testGetLogMessageCaching()
    {
        String originalThreadName = "RMI TCP Connection(1)-" + IP;
        assertLogMessageInRMIThreadWithoutPrincipal(originalThreadName);
        assertLogMessageInRMIThreadWithPrincipal(originalThreadName, "my_principal");
        assertLogMessageInRMIThreadWithPrincipal("RMI TCP Connection(2)-" + IP, "my_principal");
    }

    public void testGetLogMessageAfterRemovingSubject()
    {
        assertLogMessageInRMIThreadWithPrincipal("RMI TCP Connection(1)-" + IP, "my_principal");

        Thread.currentThread().setName("RMI TCP Connection(2)-" + IP );
        String message = getAmqpActor().getLogMessage();
        assertEquals("Unexpected log message", "[mng:N/A(" + IP + ")] ", message);

        assertLogMessageWithoutPrincipal("TEST");
    }

    private void assertLogMessageInRMIThreadWithoutPrincipal(String threadName)
    {
        Thread.currentThread().setName(threadName );
        String message = getAmqpActor().getLogMessage();
        assertEquals("Unexpected log message", "[mng:N/A(" + IP + ")] ", message);
    }

    private void assertLogMessageWithoutPrincipal(String threadName)
    {
        Thread.currentThread().setName(threadName );
        String message = getAmqpActor().getLogMessage();
        assertEquals("Unexpected log message", "[" + threadName +"] ", message);
    }

    private void assertLogMessageInRMIThreadWithPrincipal(String threadName, String principalName)
    {
        Thread.currentThread().setName(threadName);
        Subject subject = TestPrincipalUtils.createTestSubject(principalName);
        final String message = Subject.doAs(subject, new PrivilegedAction<String>()
        {
            public String run()
            {
                return getAmqpActor().getLogMessage();
            }
        });

        assertEquals("Unexpected log message", "[mng:" + principalName + "(" + IP + ")] ", message);
    }
}
