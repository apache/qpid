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
package org.apache.qpid.transport.network;


import java.util.Set;

import javax.net.ssl.SSLContext;

import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.ByteBufferReceiver;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.io.IoNetworkTransport;

public class TransportTest extends QpidTestCase
{



    public void testDefaultGetOutgoingTransportForv0_8() throws Exception
    {
        final OutgoingNetworkTransport networkTransport = Transport.getOutgoingTransportInstance(ProtocolVersion.v8_0);
        assertNotNull(networkTransport);
        assertTrue(networkTransport instanceof IoNetworkTransport);
    }

    public void testGloballyOverriddenOutgoingTransportForv0_8() throws Exception
    {
        setTestSystemProperty(Transport.QPID_TRANSPORT_PROPNAME, TestOutgoingNetworkTransport.class.getName());

        final OutgoingNetworkTransport networkTransport = Transport.getOutgoingTransportInstance(ProtocolVersion.v8_0);
        assertNotNull(networkTransport);
        assertTrue(networkTransport instanceof TestOutgoingNetworkTransport);
    }

    public void testProtocolSpecificOverriddenOutgoingTransportForv0_8() throws Exception
    {
        setTestSystemProperty(Transport.QPID_TRANSPORT_V0_8_PROPNAME, TestOutgoingNetworkTransport.class.getName());

        final OutgoingNetworkTransport networkTransport = Transport.getOutgoingTransportInstance(ProtocolVersion.v8_0);
        assertNotNull(networkTransport);
        assertTrue(networkTransport instanceof TestOutgoingNetworkTransport);
    }

    public void testDefaultGetOutgoingTransportForv0_10() throws Exception
    {
        final OutgoingNetworkTransport networkTransport = Transport.getOutgoingTransportInstance(ProtocolVersion.v0_10);
        assertNotNull(networkTransport);
        assertTrue(networkTransport instanceof IoNetworkTransport);
    }

    public void testDefaultGetIncomingTransport() throws Exception
    {
        final IncomingNetworkTransport networkTransport = Transport.getIncomingTransportInstance();
        assertNotNull(networkTransport);
        assertTrue(networkTransport instanceof IoNetworkTransport);
    }

    public void testOverriddenGetIncomingTransport() throws Exception
    {
        setTestSystemProperty(Transport.QPID_BROKER_TRANSPORT_PROPNAME, TestIncomingNetworkTransport.class.getName());

        final IncomingNetworkTransport networkTransport = Transport.getIncomingTransportInstance();
        assertNotNull(networkTransport);
        assertTrue(networkTransport instanceof TestIncomingNetworkTransport);
    }

    public void testInvalidOutgoingTransportClassName() throws Exception
    {
        setTestSystemProperty(Transport.QPID_TRANSPORT_PROPNAME, "invalid");

        try
        {
            Transport.getOutgoingTransportInstance(ProtocolVersion.v0_10);
            fail("Should have failed to load the invalid class");
        }
        catch(TransportException te)
        {
            //expected, ignore
        }
    }

    public void testInvalidOutgoingTransportProtocolVersion() throws Exception
    {
        try
        {
            Transport.getOutgoingTransportInstance(new ProtocolVersion((byte)0, (byte)0));
            fail("Should have failed to load the transport for invalid protocol version");
        }
        catch(IllegalArgumentException iae)
        {
            //expected, ignore
        }
    }

    public static class TestOutgoingNetworkTransport implements OutgoingNetworkTransport
    {

        public void close()
        {
            throw new UnsupportedOperationException();
        }

        public NetworkConnection getConnection()
        {
            throw new UnsupportedOperationException();
        }

        public NetworkConnection connect(ConnectionSettings settings,
                                         ByteBufferReceiver delegate,
                                         TransportActivity transportActivity)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestIncomingNetworkTransport implements IncomingNetworkTransport
    {

        public void close()
        {
            throw new UnsupportedOperationException();
        }

        public NetworkConnection getConnection()
        {
            throw new UnsupportedOperationException();
        }

        public void accept(NetworkTransportConfiguration config,
                           ProtocolEngineFactory factory,
                           SSLContext sslContext,
                           final Set<TransportEncryption> encryptionSet)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getAcceptingPort()
        {
            return -1;
        }
    }
}
