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

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.ManagementActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;

public class ManagementLogonLogoffReporter implements  NotificationListener, NotificationFilter
{
    private static final Logger LOGGER = Logger.getLogger(ManagementLogonLogoffReporter.class);
    private final RootMessageLogger _rootMessageLogger;
    private final UsernameAccessor _usernameAccessor;

    public ManagementLogonLogoffReporter(RootMessageLogger rootMessageLogger, UsernameAccessor usernameAccessor)
    {
        _rootMessageLogger = rootMessageLogger;
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

        String user = _usernameAccessor.getUsernameForConnectionId(connectionId);

        // If user is still null, fallback to an unordered list of Principals from the connection id.
        if (user == null)
        {
            final String[] splitConnectionId = connectionId.split(" ");
            user = splitConnectionId[1];
        }

        // use a separate instance of actor as subject is not set on connect/disconnect
        // we need to pass principal name explicitly into log actor
        LogActor logActor = new ManagementActor(_rootMessageLogger, user);
        if (JMXConnectionNotification.OPENED.equals(type))
        {
            logActor.message(ManagementConsoleMessages.OPEN(user));
        }
        else if (JMXConnectionNotification.CLOSED.equals(type) ||
                 JMXConnectionNotification.FAILED.equals(type))
        {
            logActor.message(ManagementConsoleMessages.CLOSE(user));
        }
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

}
