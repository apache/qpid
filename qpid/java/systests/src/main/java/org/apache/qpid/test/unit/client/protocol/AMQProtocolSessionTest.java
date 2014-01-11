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
package org.apache.qpid.test.unit.client.protocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.Principal;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;

public class AMQProtocolSessionTest extends QpidBrokerTestCase
{
    private static class TestProtocolSession extends AMQProtocolSession
    {

        public TestProtocolSession(AMQProtocolHandler protocolHandler, AMQConnection connection)
        {
            super(protocolHandler,connection);
        }

        public TestNetworkConnection getNetworkConnection()
        {
            return (TestNetworkConnection) getProtocolHandler().getNetworkConnection();
        }

        public AMQShortString genQueueName()
        {
            return generateQueueName();
        }
    }

    private TestProtocolSession _testSession;

    protected void setUp() throws Exception
    {
        super.setUp();

        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQProtocolHandler protocolHandler = new AMQProtocolHandler(con);
        protocolHandler.setNetworkConnection(new TestNetworkConnection());

        //don't care about the values set here apart from the dummy IoSession
        _testSession = new TestProtocolSession(protocolHandler , con);
    }
    
    public void testTemporaryQueueWildcard() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(1234), "tmp_0_0_0_0_0_0_0_0_1234_1");
    }
    
    public void testTemporaryQueueLocalhostAddr() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 1234), "tmp_127_0_0_1_1234_1");
    }
    
    public void testTemporaryQueueLocalhostName() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("localhost"), 1234), "tmp_localhost_127_0_0_1_1234_1");
    }
    
    public void testTemporaryQueueInet4() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("192.168.1.2"), 1234), "tmp_192_168_1_2_1234_1");
    }
    
    public void testTemporaryQueueInet6() throws UnknownHostException
    {
        checkTempQueueName(new InetSocketAddress(InetAddress.getByName("1080:0:0:0:8:800:200C:417A"), 1234), "tmp_1080_0_0_0_8_800_200c_417a_1234_1");
    }
    
    private void checkTempQueueName(SocketAddress address, String queueName)
    {
        _testSession.getNetworkConnection().setLocalAddress(address);
        assertEquals("Wrong queue name", queueName, _testSession.genQueueName().asString());
    }

    private static class TestNetworkConnection implements NetworkConnection
    {
        private String _remoteHost = "127.0.0.1";
        private String _localHost = "127.0.0.1";
        private int _port = 1;
        private SocketAddress _localAddress = null;
        private final Sender<ByteBuffer> _sender;

        public TestNetworkConnection()
        {
            _sender = new Sender<ByteBuffer>()
            {

                public void setIdleTimeout(int i)
                {

                }

                public void send(ByteBuffer msg)
                {

                }

                public void flush()
                {

                }

                public void close()
                {

                }
            };
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return (_localAddress != null) ? _localAddress : new InetSocketAddress(_localHost, _port);
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return new InetSocketAddress(_remoteHost, _port);
        }

        @Override
        public void setMaxReadIdle(int idleTime)
        {
        }

        @Override
        public Principal getPeerPrincipal()
        {
            return null;
        }

        @Override
        public int getMaxReadIdle()
        {
            return 0;
        }

        @Override
        public int getMaxWriteIdle()
        {
            return 0;
        }

        @Override
        public void setMaxWriteIdle(int idleTime)
        {
        }

        @Override
        public void close()
        {
        }

        public void setLocalAddress(SocketAddress address)
        {
            _localAddress = address;
        }

        public Sender<ByteBuffer> getSender()
        {
            return _sender;
        }

        public void start()
        {
        }
    }
}
