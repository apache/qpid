/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.messaging.internal;

import java.util.List;
import java.util.Map;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.TransportFailureException;

/**
 * An extended interface meant for API implementors.
 */
public interface ConnectionInternal extends Connection
{
    public void addConnectionEventListener(ConnectionEventListener l) throws ConnectionException;

    public void removeConnectionEventListener(ConnectionEventListener l) throws ConnectionException;

    public List<SessionInternal> getSessions() throws ConnectionException;

    public void exception(TransportFailureException e, long serialNumber);

    public void reconnect(String url, Map<String,Object> options) throws TransportFailureException;

    public void recreate() throws MessagingException;

    public void unregisterSession(SessionInternal sesion);

    /**
     *  The per connection lock that is used by the connection
     *  and it's child objects. A single lock is used to prevent
     *  deadlocks that could occur with having multiple locks,
     *  perhaps at the cost of a minor perf degradation.
     */
    public Object getConnectionLock();

    public String getConnectionURL();

    public Map<String,Object> getConnectionOptions();

    /**
     * Every time a protocol connection is established a new serial number
     * is assigned to the connection to distinguish itself from a previous
     * version. This is useful in avoiding the same connection exception being
     * notified by multiple sessions (and it's children), resulting in spurious failover calls.
     */
    public long getSerialNumber();
}
