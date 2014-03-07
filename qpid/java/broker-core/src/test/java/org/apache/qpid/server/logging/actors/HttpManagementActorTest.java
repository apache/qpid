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

import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.SystemLog;
import org.apache.qpid.server.security.auth.ManagementConnectionPrincipal;
import org.apache.qpid.server.security.auth.TestPrincipalUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;

public class HttpManagementActorTest extends BaseActorTestCase
{
    public static final LogMessage EMPTY_MESSAGE = new LogMessage()
    {
        @Override
        public String getLogHierarchy()
        {
            return "";
        }

        public String toString()
        {
            return "";
        }
    };
    private static final String IP = "127.0.0.1";
    private static final int PORT = 1;
    private static final String SUFFIX = "(/" + IP + ":" + PORT + ")] ";
    private ManagementConnectionPrincipal _connectionPrincipal;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _connectionPrincipal = new ManagementConnectionPrincipal()
                                    {
                                        @Override
                                        public String getType()
                                        {
                                            return "HTTP";
                                        }

                                        @Override
                                        public SocketAddress getRemoteAddress()
                                        {
                                            return new InetSocketAddress(IP, PORT);
                                        }

                                        @Override
                                        public String getName()
                                        {
                                            return getRemoteAddress().toString();
                                        }
                                    };
    }

    public void testSubjectPrincipalNameAppearance()
    {
        Subject subject = TestPrincipalUtils.createTestSubject("guest");

        subject.getPrincipals().add(_connectionPrincipal);

        final String message = Subject.doAs(subject, new PrivilegedAction<String>()
        {
            public String run()
            {
                return sendTestLogMessage();
            }
        });

        assertNotNull("Test log message is not created!", message);

        List<Object> logs = getRawLogger().getLogMessages();
        assertEquals("Message log size not as expected.", 1, logs.size());

        String logMessage = logs.get(0).toString();
        assertTrue("Message was not found in log message", logMessage.contains(message));
        assertTrue("Message does not contain expected value: " + logMessage, logMessage.contains("[mng:guest" + SUFFIX));
    }

    /** It's necessary to test successive calls because HttpManagementActor caches
     *  its log message based on principal name */
    public void testGetLogMessageCaching()
    {
        assertLogMessageWithoutPrincipal();
        assertLogMessageWithPrincipal("my_principal");
        assertLogMessageWithPrincipal("my_principal2");
        assertLogMessageWithoutPrincipal();
    }

    private void assertLogMessageWithoutPrincipal()
    {
        getRawLogger().getLogMessages().clear();
        Subject subject = new Subject(false,
                                      Collections.singleton(_connectionPrincipal),
                                      Collections.emptySet(),
                                      Collections.emptySet());
        Subject.doAs(subject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                SystemLog.message(EMPTY_MESSAGE);
                List<Object> logs = getRawLogger().getLogMessages();
                assertEquals("Message log size not as expected.", 1, logs.size());

                String logMessage = logs.get(0).toString();
                assertEquals("Unexpected log message",
                             "[mng:" + "N/A" + SUFFIX,
                             logMessage);
                return null;
            }
        });
    }

    private void assertLogMessageWithPrincipal(String principalName)
    {
        getRawLogger().getLogMessages().clear();

        Subject subject = TestPrincipalUtils.createTestSubject(principalName);
        subject.getPrincipals().add(_connectionPrincipal);
        final String message = Subject.doAs(subject, new PrivilegedAction<String>()
        {
            public String run()
            {
                SystemLog.message(EMPTY_MESSAGE);
                List<Object> logs = getRawLogger().getLogMessages();
                assertEquals("Message log size not as expected.", 1, logs.size());

                String logMessage = logs.get(0).toString();

                return logMessage;
            }
        });

        assertEquals("Unexpected log message", "[mng:" + principalName + SUFFIX, message);
    }
}
