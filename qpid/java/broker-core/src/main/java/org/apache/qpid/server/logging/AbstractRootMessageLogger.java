/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.logging;


import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.connection.SessionPrincipal;
import org.apache.qpid.server.logging.subjects.LogSubjectFormat;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.ManagementConnectionPrincipal;
import org.apache.qpid.server.security.auth.TaskPrincipal;

import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.Set;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CHANNEL_FORMAT;
import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CONNECTION_FORMAT;
import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SOCKET_FORMAT;
import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.USER_FORMAT;

public abstract class AbstractRootMessageLogger implements RootMessageLogger
{
    public static final String DEFAULT_LOG_HIERARCHY_PREFIX = "qpid.message.";

    private final String _msgPrefix = System.getProperty("qpid.logging.prefix","");

    private boolean _enabled = true;

    public AbstractRootMessageLogger()
    {

    }
    
    public AbstractRootMessageLogger(boolean statusUpdatesEnabled)
    {
        _enabled = statusUpdatesEnabled;
    }
    
    public boolean isEnabled()
    {
        return _enabled;
    }

    public boolean isMessageEnabled(String logHierarchy)
    {
        return _enabled;
    }

    public void message(LogMessage message)
    {
        if (isMessageEnabled(message.getLogHierarchy()))
        {
            rawMessage(_msgPrefix + getActor() + message, message.getLogHierarchy());
        }
    }

    public void message(LogSubject subject, LogMessage message)
    {
        if (isMessageEnabled(message.getLogHierarchy()))
        {
            rawMessage(_msgPrefix + getActor() + subject.toLogString() + message,
                       message.getLogHierarchy());
        }
    }
    abstract void rawMessage(String message, String logHierarchy);

    abstract void rawMessage(String message, Throwable throwable, String logHierarchy);


    protected String getActor()
    {
        Subject subject = Subject.getSubject(AccessController.getContext());

        SessionPrincipal sessionPrincipal = getPrincipal(subject, SessionPrincipal.class);
        String message;
        if(sessionPrincipal != null)
        {
            message =  generateSessionMessage(sessionPrincipal.getSession());
        }
        else
        {
            ConnectionPrincipal connPrincipal = getPrincipal(subject, ConnectionPrincipal.class);

            if(connPrincipal != null)
            {
                message = generateConnectionMessage(connPrincipal.getConnection());
            }
            else
            {
                TaskPrincipal taskPrincipal = getPrincipal(subject, TaskPrincipal.class);
                if(taskPrincipal != null)
                {
                    message = generateTaskMessage(taskPrincipal);
                }
                else
                {
                    ManagementConnectionPrincipal managementConnection = getPrincipal(subject,ManagementConnectionPrincipal.class);
                    if(managementConnection != null)
                    {
                        message = generateManagementConnectionMessage(managementConnection, getPrincipal(subject, AuthenticatedPrincipal.class));
                    }
                    else
                    {
                        message = "<<UNKNOWN>> ";
                    }
                }
            }
        }
        return message;
    }

    private String generateManagementConnectionMessage(final ManagementConnectionPrincipal managementConnection,
                                                       final AuthenticatedPrincipal userPrincipal)
    {
        String remoteAddress = managementConnection.getRemoteAddress().toString();
        String user = userPrincipal == null ? "N/A" : userPrincipal.getName();
        return "[" + MessageFormat.format(LogSubjectFormat.MANAGEMENT_FORMAT, user, remoteAddress) + "] ";
    }

    private String generateTaskMessage(final TaskPrincipal taskPrincipal)
    {
        return "["+taskPrincipal.getName()+"] ";
    }

    protected String generateConnectionMessage(final AMQConnectionModel connection)
    {
        if (connection.getAuthorizedPrincipal() != null)
        {
            if (connection.getVirtualHostName() != null)
            {
                /**
                 * LOG FORMAT used by the AMQPConnectorActor follows
                 * ConnectionLogSubject.CONNECTION_FORMAT :
                 * con:{0}({1}@{2}/{3})
                 *
                 * Uses a MessageFormat call to insert the required values
                 * according to these indices:
                 *
                 * 0 - Connection ID 1 - User ID 2 - IP 3 - Virtualhost
                 */
                return "[" + MessageFormat.format(CONNECTION_FORMAT,
                                                  connection.getConnectionId(),
                                                  connection.getAuthorizedPrincipal().getName(),
                                                  connection.getRemoteAddressString(),
                                                  connection.getVirtualHostName())
                       + "] ";

            }
            else
            {
                return"[" + MessageFormat.format(USER_FORMAT,
                                                 connection.getConnectionId(),
                                                 connection.getAuthorizedPrincipal().getName(),
                                                 connection.getRemoteAddressString())
                      + "] ";

            }
        }
        else
        {
            return "[" + MessageFormat.format(SOCKET_FORMAT,
                                              connection.getConnectionId(),
                                              connection.getRemoteAddressString())
                   + "] ";
        }
    }

    protected String generateSessionMessage(final AMQSessionModel session)
    {
        AMQConnectionModel connection = session.getConnectionModel();
        return "[" + MessageFormat.format(CHANNEL_FORMAT, connection == null ? -1L : connection.getConnectionId(),
                                          (connection == null || connection.getAuthorizedPrincipal() == null)
                                                  ? "?"
                                                  : connection.getAuthorizedPrincipal().getName(),
                                          (connection == null || connection.getRemoteAddressString() == null)
                                                  ? "?"
                                                  : connection.getRemoteAddressString(),
                                          (connection == null || connection.getVirtualHostName() == null)
                                                  ? "?"
                                                  : connection.getVirtualHostName(),
                                          session.getChannelId())
               + "] ";
    }

    private <P extends Principal> P getPrincipal(Subject subject, Class<P> clazz)
    {
        if(subject != null)
        {
            Set<P> principals = subject.getPrincipals(clazz);
            if(principals != null && !principals.isEmpty())
            {
                return principals.iterator().next();
            }
        }
        return null;
    }


}
