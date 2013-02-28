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

import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.rmi.RMIConnection;
import javax.management.remote.rmi.RMIJRMPServerImpl;
import javax.security.auth.Subject;

import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;

/**
 * An implementation of RMIJRMPServerImpl that caches the usernames of users as they log-on
 * and makes the same available via {@link UsernameAccessor#getUsernameForConnectionId(String)}.
 *
 * Caller is responsible for installing this object as a {@link NotificationListener} of the
 * {@link JMXConnectorServer} so the cache entries are removed as the clients disconnect.
 *
 */
public class UsernameCachingRMIJRMPServer extends RMIJRMPServerImpl implements NotificationListener, NotificationFilter, UsernameAccessor
{
    // ConnectionId is guaranteed to be unique per client connection, according to the JMX spec.
    private final Map<String, String> _connectionIdUsernameMap = new ConcurrentHashMap<String, String>();

    UsernameCachingRMIJRMPServer(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf,
            Map<String, ?> env) throws IOException
    {
        super(port, csf, ssf, env);
    }

    @Override
    protected RMIConnection makeClient(String connectionId, Subject subject) throws IOException
    {
        final RMIConnection makeClient = super.makeClient(connectionId, subject);
        final AuthenticatedPrincipal authenticatedPrincipalFromSubject = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(subject);
        _connectionIdUsernameMap.put(connectionId, authenticatedPrincipalFromSubject.getName());
        return makeClient;
    }

    @Override
    public String getUsernameForConnectionId(String connectionId)
    {
        return _connectionIdUsernameMap.get(connectionId);
    }

    @Override
    public void handleNotification(Notification notification, Object handback)
    {
        final String connectionId = ((JMXConnectionNotification) notification).getConnectionId();
        removeConnectionIdFromCache(connectionId);
    }

     @Override
    public boolean isNotificationEnabled(Notification notification)
    {
        return isClientDisconnectEvent(notification);
    }

     private void removeConnectionIdFromCache(String connectionId)
     {
         _connectionIdUsernameMap.remove(connectionId);
     }

    private boolean isClientDisconnectEvent(Notification notification)
    {
        final String type = notification.getType();
        return CLOSED.equals(type) || FAILED.equals(type);
    }

}