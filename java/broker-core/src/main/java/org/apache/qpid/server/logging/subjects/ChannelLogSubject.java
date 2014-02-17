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

import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CHANNEL_FORMAT;

public class ChannelLogSubject extends AbstractLogSubject
{

    public ChannelLogSubject(AMQSessionModel session)
    {
        /**
         * LOG FORMAT used by the AMQPConnectorActor follows
         * ChannelLogSubject.CHANNEL_FORMAT : con:{0}({1}@{2}/{3})/ch:{4}.
         *
         * Uses a MessageFormat call to insert the required values according to
         * these indices:
         *
         * 0 - Connection ID
         * 1 - User ID
         * 2 - IP
         * 3 - Virtualhost
         * 4 - Channel ID
         */
        AMQConnectionModel connection = session.getConnectionModel();
        setLogStringWithFormat(CHANNEL_FORMAT,
                               connection == null ? -1L : connection.getConnectionId(),
                               (connection == null || connection.getAuthorizedPrincipal() == null) ? "?" : connection.getAuthorizedPrincipal().getName(),
                               (connection == null || connection.getRemoteAddressString() == null) ? "?" : connection.getRemoteAddressString(),
                               (connection == null || connection.getVirtualHostName() == null) ? "?" : connection.getVirtualHostName(),
                               session.getChannelId());

    }

}
