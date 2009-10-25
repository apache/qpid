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
package org.apache.qpid.server.logging.subjects;

import org.apache.qpid.server.protocol.AMQProtocolSession;

/** The Connection LogSubject */
public class ConnectionLogSubject extends AbstractLogSubject
{

    /**
     * LOG FORMAT for the ConnectionLogSubject,
     * Uses a MessageFormat call to insert the requried values according to
     * these indicies:
     *
     * 0 - Connection ID
     * 1 - User ID
     * 2 - IP
     * 3 - Virtualhost
     */
    public static final String CONNECTION_FORMAT = "con:{0}({1}@{2}/{3})";

    public ConnectionLogSubject(AMQProtocolSession session)
    {
        setLogStringWithFormat(CONNECTION_FORMAT, session.getSessionID(),
               session.getPrincipal().getName(),
               session.getRemoteAddress(),
               session.getVirtualHost().getName());
    }
}
