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

import org.apache.qpid.server.logging.RootMessageLogger;

import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.Set;

/**
 * NOTE: This actor is not thread safe.
 *
 * Sharing of a ManagementActor instance between threads may result in an
 * incorrect actor value being logged.
 *
 * This is due to the fact that calls to message will dynamically query the
 * thread name and use that to set the log format during each message() call.
 *
 * This is currently not an issue as each MBean operation creates a new Actor
 * that is unique for each operation.
 */
public class ManagementActor extends AbstractActor
{
    /**
     * Holds the principal name to display when principal subject is not available.
     * <p>
     * This is useful for cases when users invoke JMX operation over JConsole
     * attached to the local JVM.
     */
    private static final String UNKNOWN_PRINCIPAL = "N/A";

    String _lastThreadName = null;

    /**
     * LOG FORMAT for the ManagementActor,
     * Uses a MessageFormat call to insert the required values according to
     * these indices:
     *
     * 0 - User ID
     * 1 - IP
     */
    public static final String MANAGEMENT_FORMAT = "mng:{0}({1})";

    /**
     * The logString to be used for logging
     */
    private String _logString;

    /** @param rootLogger The RootLogger to use for this Actor */
    public ManagementActor(RootMessageLogger rootLogger)
    {
        super(rootLogger);
    }

    private void updateLogString()
    {
        String currentName = Thread.currentThread().getName();

        String actor;
        // Record the last thread name so we don't have to recreate the log string
        if (!currentName.equals(_lastThreadName))
        {
            _lastThreadName = currentName;

            // Management Thread names have this format.
            // RMI TCP Connection(2)-169.24.29.116
            // This is true for both LocalAPI and JMX Connections
            // However to be defensive lets test.

            String[] split = currentName.split("\\(");
            if (split.length == 2)
            {
                String ip = currentName.split("-")[1];
                String principalName = getPrincipalName();
                if (principalName == null)
                {
                    principalName = UNKNOWN_PRINCIPAL;
                }
                actor = MessageFormat.format(MANAGEMENT_FORMAT, principalName, ip);
            }
            else
            {
                // This is a precautionary path as it is not expected to occur
                // however rather than adjusting the thread name of the two
                // tests that will use this it is safer all round to do this.
                // it is also currently used by tests :
                // AMQBrokerManagerMBeanTest
                // ExchangeMBeanTest
                actor = currentName;
            }

            _logString = "[" + actor + "] ";

        }
    }

    /**
     * Returns current JMX principal name.
     *
     * @return principal name or null if principal can not be found
     */
    protected String getPrincipalName()
    {
        String identity = null;

        // retrieve Subject from current AccessControlContext
        final Subject subject = Subject.getSubject(AccessController.getContext());
        if (subject != null)
        {
            // retrieve JMXPrincipal from Subject
            final Set<JMXPrincipal> principals = subject.getPrincipals(JMXPrincipal.class);
            if (principals != null && !principals.isEmpty())
            {
                final Principal principal = principals.iterator().next();
                identity = principal.getName();
            }
        }
        return identity;
    }

    public String getLogMessage()
    {
        updateLogString();
        return _logString;
    }

}
