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
package org.apache.qpid.server.management.plugin.session;

import java.security.Principal;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;

/**
 * Logs {@link ManagementConsoleMessages#OPEN(String)} and {@link ManagementConsoleMessages#CLOSE(String)}
 * messages.  A single instance of this class must be placed in the {@link HttpSession} immediately after
 * the user has successfully logged-in, and removed (or the whole session invalidated) as the user logs out.
 */
public class LoginLogoutReporter implements HttpSessionBindingListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginLogoutReporter.class);
    private final Subject _subject;
    private final Principal _principal;
    private final EventLoggerProvider _eventLoggerProvider;

    public LoginLogoutReporter(Subject subject, EventLoggerProvider eventLoggerProvider)
    {
        super();
        _subject = subject;
        _principal = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(_subject);
        _eventLoggerProvider = eventLoggerProvider;
    }

    @Override
    public void valueBound(HttpSessionBindingEvent arg0)
    {
        reportLogin();
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent arg0)
    {
        reportLogout();
    }

    private void reportLogin()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("User logging in : " + _principal);
        }

        Subject.doAs(_subject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                getEventLogger().message(ManagementConsoleMessages.OPEN(_principal.getName()));
                return null;
            }
        });
    }

    private void reportLogout()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("User logging out : " + _principal);
        }

        Subject.doAs(_subject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                getEventLogger().message(ManagementConsoleMessages.CLOSE(_principal.getName()));
                return null;
            }
        });
    }

    public EventLogger getEventLogger()
    {
        return _eventLoggerProvider.getEventLogger();
    }
}
