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
package org.apache.qpidity.transport;

import org.apache.mina.util.AvailablePortFinder;

import org.apache.qpid.util.concurrent.Condition;

import org.apache.qpidity.transport.network.mina.MinaHandler;
import org.apache.qpidity.transport.util.Logger;

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

        ConnectionDelegate server = new ConnectionDelegate() {
            public void init(Channel ch, ProtocolHeader hdr) {
                ch.getConnection().close();
            }

            public SessionDelegate getSessionDelegate() {
                return new SessionDelegate() {};
            }
            public void exception(Throwable t) {
                log.error(t, "exception caught");
            }
            public void closed() {}
        };

        MinaHandler.accept("0.0.0.0", port, server);
    }

    private Connection connect(final Condition closed)
    {
        Connection conn = MinaHandler.connect("0.0.0.0", port, new ConnectionDelegate()
        {
            public SessionDelegate getSessionDelegate()
            {
                return new SessionDelegate() {};
            }
            public void exception(Throwable t)
            {
                t.printStackTrace();
            }
            public void closed()
            {
                if (closed != null)
                {
                    closed.set();
                }
            }
        });

        conn.send(new ConnectionEvent(0, new ProtocolHeader(1, 0, 10)));
        return conn;
    }

    public void testWriteToClosed() throws Exception
    {
        Connection conn = connect(null);
        Channel ch = conn.getChannel(0);
        Session ssn = new Session();
        ssn.attach(ch);

        try
        {
            ssn.sessionOpen(1234);
            fail("writing to a closed socket succeeded");
        }
        catch (TransportException e)
        {
            // expected
        }
    }

    public void testClosedNotification() throws Exception
    {
        Condition closed = new Condition();
        Connection conn = connect(closed);
        if (!closed.get(3000))
        {
            fail("never got notified of connection close");
        }
    }

}
