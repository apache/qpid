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
package org.apache.qpid.transport;

import org.apache.mina.util.AvailablePortFinder;

import org.apache.qpid.util.concurrent.Condition;

import org.apache.qpid.transport.network.ConnectionBinding;
import org.apache.qpid.transport.network.io.IoAcceptor;
import org.apache.qpid.transport.network.io.IoTransport;
import org.apache.qpid.transport.util.Logger;

import junit.framework.TestCase;

import java.util.Random;

/**
 * ConnectionTest
 */

public class ConnectionTest extends TestCase
{

    private static final Logger log = Logger.get(ConnectionTest.class);

    private int port;

    protected void setUp() throws Exception
    {
        super.setUp();

        port = AvailablePortFinder.getNextAvailable(12000);

        ConnectionDelegate server = new ServerDelegate() {
            @Override public void connectionOpen(Channel ch, ConnectionOpen open)
            {
                super.connectionOpen(ch, open);
                ch.getConnection().close();
            }
        };

        IoAcceptor ioa = new IoAcceptor
            ("localhost", port, ConnectionBinding.get(server));
        ioa.start();
    }

    private Connection connect(final Condition closed)
    {
        Connection conn = new Connection();
        conn.setConnectionListener(new ConnectionListener()
        {
            public void opened(Connection conn) {}
            public void exception(Connection conn, ConnectionException exc)
            {
                exc.printStackTrace();
            }
            public void closed(Connection conn)
            {
                if (closed != null)
                {
                    closed.set();
                }
            }
        });
        conn.connect("localhost", port, null, "guest", "guest");
        return conn;
    }

    public void testClosedNotificationAndWriteToClosed() throws Exception
    {
        Condition closed = new Condition();
        Connection conn = connect(closed);
        if (!closed.get(3000))
        {
            fail("never got notified of connection close");
        }

        Channel ch = conn.getChannel(0);
        Session ssn = new Session("test".getBytes());
        ssn.attach(ch);

        try
        {
            ssn.sessionAttach(ssn.getName());
            fail("writing to a closed socket succeeded");
        }
        catch (TransportException e)
        {
            // expected
        }
    }

}
