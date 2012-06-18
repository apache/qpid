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

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Session;

/**
 * An extended interface meant for API implementors.
 */
public interface ConnectionInternal extends Connection
{
    public void addConnectionStateListener(ConnectionStateListener l) throws ConnectionException;

    public void removeConnectionStateListener(ConnectionStateListener l) throws ConnectionException;

    public List<SessionInternal> getSessions() throws ConnectionException;

    public void exception(ConnectionException e);

    public void recreate() throws MessagingException;

    /**
     *  The per connection lock that is used by the connection
     *  and it's child objects. A single lock is used to prevent
     *  deadlocks that could occur with having multiple locks,
     *  perhaps at the cost of a minor perf degradation.
     */
    public Object getConnectionLock();
}
