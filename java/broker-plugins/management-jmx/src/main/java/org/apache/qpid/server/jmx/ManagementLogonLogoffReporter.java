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

import static javax.management.remote.JMXConnectionNotification.CLOSED;
import static javax.management.remote.JMXConnectionNotification.FAILED;
import static javax.management.remote.JMXConnectionNotification.OPENED;

import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.security.PrivilegedAction;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.EventLoggerProvider;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.jmx.JMXConnectionPrincipal;

public class ManagementLogonLogoffReporter implements  NotificationListener, NotificationFilter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementLogonLogoffReporter.class);
    private final EventLoggerProvider _eventLoggerProvider;
    private final UsernameAccessor _usernameAccessor;

    public ManagementLogonLogoffReporter(EventLoggerProvider eventLoggerProvider, UsernameAccessor usernameAccessor)
    {
        _eventLoggerProvider = eventLoggerProvider;
        _usernameAccessor = usernameAccessor;
    }

    @Override
    public void handleNotification(final Notification notification, final Object handback)
    {
        final String connectionId = ((JMXConnectionNotification) notification).getConnectionId();
        final String type = notification.getType();

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Notification connectionId : " + connectionId + " type : " + type);
        }

        Subject subject = _usernameAccessor.getSubjectConnectionId(connectionId);
        if(subject == null)
        {
            subject = new Subject();
        }
        AuthenticatedPrincipal authenticatedPrincipal =
                AuthenticatedPrincipal.getOptionalAuthenticatedPrincipalFromSubject(subject);

        String user;

        if(authenticatedPrincipal != null)
        {
            user = authenticatedPrincipal.getName();
        }
        else
        {
            // If user is still null, fallback to an unordered list of Principals from the connection id.
            final String[] splitConnectionId = connectionId.split(" ");
            user = splitConnectionId[1];
        }


        if(subject.getPrincipals(JMXConnectionPrincipal.class).isEmpty())
        {
            try
            {
                String clientHost = RemoteServer.getClientHost();
                subject = new Subject(false,
                                      subject.getPrincipals(),
                                      subject.getPublicCredentials(),
                                      subject.getPrivateCredentials());
                subject.getPrincipals().add(new JMXConnectionPrincipal(clientHost));
                subject.setReadOnly();
            }
            catch(ServerNotActiveException e)
            {
            }
        }

        final String username = user;
        Subject.doAs(subject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                if (JMXConnectionNotification.OPENED.equals(type))
                {
                    getEventLogger().message(ManagementConsoleMessages.OPEN(username));
                }
                else if (JMXConnectionNotification.CLOSED.equals(type) ||
                         JMXConnectionNotification.FAILED.equals(type))
                {
                    getEventLogger().message(ManagementConsoleMessages.CLOSE(username));
                }
                return null;
            }
        });
    }

    @Override
    public boolean isNotificationEnabled(Notification notification)
    {
        return notification instanceof JMXConnectionNotification && isLogonTypeEvent(notification);
    }

    private boolean isLogonTypeEvent(Notification notification)
    {
        final String type = notification.getType();
        return CLOSED.equals(type) || FAILED.equals(type) || OPENED.equals(type);
    }

    public EventLogger getEventLogger()
    {
        return _eventLoggerProvider.getEventLogger();
    }
}
