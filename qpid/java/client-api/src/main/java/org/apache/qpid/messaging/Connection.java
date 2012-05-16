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
package org.apache.qpid.messaging;

/**
 * A connection represents a network connection to a remote endpoint.
 */
public interface Connection
{
    /**
     * Creates a network connection to the peer and negotiates with the peer to establish a protocol level connection.
     * When this method returns the connection is ready to be used.
     */
    public void open();

    /**
     * Returns true if the connection is open.
     */
    public boolean isOpen();

    /**
     * Close the connection and any sessions associated with this connection.
     */
    public void close();

    /**
     * Creates a session with the given name.The name should be unique.
     * It is advised to use a UUID for generating the name.
     * @param name Unique identifier for the session.
     * @return Session
     */
    public Session createSession(String name);

    /**
     * Creates a transactional session with the given name.
     * Messages sent or received through this session, will only be settled once commit is called on this session.
     * The name should be unique. It is advised to use a UUID for generating the name.
     * @param name Unique identifier for the session.
     * @return Session
     */
    public Session createTransactionalSession(String name);

    /**
     * Returns the authenticated username for this connection.
     * For the simple username/password case, this just returns the same username.
     * For EXTERNAL The username will be constructed from the subject distinguished name.
     * For KERBEROR the username will be the kerberos username.
     * @return The authenticated username.
     */
    public String getAuthenticatedUsername();
}
