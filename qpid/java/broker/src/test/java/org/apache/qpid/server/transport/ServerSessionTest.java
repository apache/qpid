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
 */
package org.apache.qpid.server.transport;

import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Binary;

public class ServerSessionTest extends InternalBrokerBaseCase
{

    private VirtualHost _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _virtualHost = ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHosts().iterator().next();
    }

    public void testCompareTo() throws Exception
    {
        ServerConnection connection = new ServerConnection(1);
        connection.setVirtualHost(_virtualHost);
        ServerSession session1 = new ServerSession(connection, new ServerSessionDelegate(),
                new Binary(getName().getBytes()), 0);

        // create a session with the same name but on a different connection
        ServerConnection connection2 = new ServerConnection(2);
        connection2.setVirtualHost(_virtualHost);
        ServerSession session2 = new ServerSession(connection2, new ServerSessionDelegate(),
                new Binary(getName().getBytes()), 0);

        assertFalse("Unexpected compare result", session1.compareTo(session2) == 0);
        assertEquals("Unexpected compare result", 0, session1.compareTo(session1));
    }


}
