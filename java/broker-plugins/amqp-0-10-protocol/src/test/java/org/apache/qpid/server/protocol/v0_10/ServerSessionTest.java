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
package org.apache.qpid.server.protocol.v0_10;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.ExecutionException;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Method;

public class ServerSessionTest extends QpidTestCase
{

    private VirtualHostImpl _virtualHost;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _virtualHost = BrokerTestHelper.createVirtualHost(getName());
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_virtualHost != null)
            {
                _virtualHost.close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public void testCompareTo() throws Exception
    {
        final Broker<?> broker = mock(Broker.class);
        when(broker.getContextValue(eq(Long.class), eq(Broker.CHANNEL_FLOW_CONTROL_ENFORCEMENT_TIMEOUT))).thenReturn(0l);

        AmqpPort amqpPort = mock(AmqpPort.class);
        when(amqpPort.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(AmqpPort.DEFAULT_MAX_MESSAGE_SIZE);

        ServerConnection connection = new ServerConnection(1, broker, amqpPort, Transport.TCP);
        connection.setVirtualHost(_virtualHost);
        ServerSession session1 = new ServerSession(connection, new ServerSessionDelegate(),
                new Binary(getName().getBytes()), 0);

        // create a session with the same name but on a different connection
        ServerConnection connection2 = new ServerConnection(2, broker, amqpPort, Transport.TCP);
        connection2.setVirtualHost(_virtualHost);
        ServerSession session2 = new ServerSession(connection2, new ServerSessionDelegate(),
                new Binary(getName().getBytes()), 0);

        assertFalse("Unexpected compare result", session1.compareTo(session2) == 0);
        assertEquals("Unexpected compare result", 0, session1.compareTo(session1));
    }

    public void testOverlargeMessageTest() throws Exception
    {
        final Broker<?> broker = mock(Broker.class);
        when(broker.getContextValue(eq(Long.class), eq(Broker.CHANNEL_FLOW_CONTROL_ENFORCEMENT_TIMEOUT))).thenReturn(0l);

        AmqpPort port = mock(AmqpPort.class);
        when(port.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(1024);
        ServerConnection connection = new ServerConnection(1, broker, port, Transport.TCP);
        connection.setVirtualHost(_virtualHost);
        final List<Method> invokedMethods = new ArrayList<>();
        ServerSession session = new ServerSession(connection, new ServerSessionDelegate(),
                                                   new Binary(getName().getBytes()), 0)
        {
            @Override
            public void invoke(final Method m)
            {
                invokedMethods.add(m);
            }
        };

        ServerSessionDelegate delegate = new ServerSessionDelegate();

        MessageTransfer xfr = new MessageTransfer();
        xfr.setBody(new byte[2048]);
        delegate.messageTransfer(session, xfr);

        assertFalse("No methods invoked - expecting at least 1", invokedMethods.isEmpty());
        Method firstInvoked = invokedMethods.get(0);
        assertTrue("First invoked method not execution error", firstInvoked instanceof ExecutionException);
        assertEquals(ExecutionErrorCode.RESOURCE_LIMIT_EXCEEDED, ((ExecutionException)firstInvoked).getErrorCode());

        invokedMethods.clear();

        // test the boundary condition

        xfr.setBody(new byte[1024]);
        delegate.messageTransfer(session, xfr);

        assertTrue("Methods invoked when not expecting any", invokedMethods.isEmpty());
    }


}
