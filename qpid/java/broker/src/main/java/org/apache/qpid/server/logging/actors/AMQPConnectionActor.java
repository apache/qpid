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
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.subjects.ConnectionLogSubject;
import org.apache.qpid.server.protocol.AMQProtocolSession;

import java.text.MessageFormat;

/**
 * An AMQPConnectionActor represtents a connectionthrough the AMQP port.
 * <p/>
 * This is responsible for correctly formatting the LogActor String in the log
 * <p/>
 * [ con:1(user@127.0.0.1/) ]
 * <p/>
 * To do this it requires access to the IO Layers.
 */
public class AMQPConnectionActor extends AbstractActor
{
    /**
     * 0 - Connection ID
     * 1 - Remote Address
     */
    public static String SOCKET_FORMAT = "con:{0}({1})";

    /**
     * LOG FORMAT for the ConnectionLogSubject,
     * Uses a MessageFormat call to insert the requried values according to
     * these indicies:
     *
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     */
    public static final String USER_FORMAT = "con:{0}({1}@{2})";

    public AMQPConnectionActor(AMQProtocolSession session, RootMessageLogger rootLogger)
    {
        super(rootLogger);

        _logString = "[" + MessageFormat.format(SOCKET_FORMAT,
                                                session.getSessionID(),
                                                session.getRemoteAddress())

                     + "] ";
    }

    /**
     * Call when the connection has been authorized so that the logString
     * can be updated with the new user identity.
     *
     * @param session the authorized session
     */
    public void connectionAuthorized(AMQProtocolSession session)
    {
        _logString = "[" + MessageFormat.format(USER_FORMAT,
                                                session.getSessionID(),
                                                session.getPrincipal().getName(),
                                                session.getRemoteAddress())
                     + "] ";

    }

    /**
     * Called once the user has been authenticated and they are now selecting
     * the virtual host they wish to use.
     *
     * @param session the session that now has a virtualhost associated with it.
     */
    public void virtualHostSelected(AMQProtocolSession session)
    {

        /**
         * LOG FORMAT used by the AMQPConnectorActor follows
         * ConnectionLogSubject.CONNECTION_FORMAT :
         * con:{0}({1}@{2}/{3})
         *
         * Uses a MessageFormat call to insert the requried values according to
         * these indicies:
         *
         * 0 - Connection ID
         * 1 - User ID
         * 2 - IP
         * 3 - Virtualhost
         */
        _logString = "[" + MessageFormat.format(ConnectionLogSubject.CONNECTION_FORMAT,
                                                session.getSessionID(),
                                                session.getPrincipal().getName(),
                                                session.getRemoteAddress(),
                                                session.getVirtualHost().getName())
                     + "] ";

    }
}

