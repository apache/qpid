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
package org.apache.qpid.server.protocol;

import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.TestNetworkConnection;

public class MultiVersionProtocolEngineFactoryTest extends QpidTestCase
{
    private VirtualHost _virtualHost;
    private Broker _broker;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _broker = BrokerTestHelper.createBrokerMock();
        VirtualHostRegistry virtualHostRegistry = _broker.getVirtualHostRegistry();
        when(_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST)).thenReturn("default");

        // AMQP 1-0 connection needs default vhost to be present
        _virtualHost = BrokerTestHelper.createVirtualHost("default", virtualHostRegistry);
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            _virtualHost.close();
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    private static final byte[] AMQP_0_8_HEADER =
        new byte[] { (byte) 'A',
                     (byte) 'M',
                     (byte) 'Q',
                     (byte) 'P',
                     (byte) 1,
                     (byte) 1,
                     (byte) 8,
                     (byte) 0
        };

    private static final byte[] AMQP_0_9_HEADER =
        new byte[] { (byte) 'A',
                     (byte) 'M',
                     (byte) 'Q',
                     (byte) 'P',
                     (byte) 1,
                     (byte) 1,
                     (byte) 0,
                     (byte) 9
        };

    private static final byte[] AMQP_0_9_1_HEADER =
        new byte[] { (byte) 'A',
                     (byte) 'M',
                     (byte) 'Q',
                     (byte) 'P',
                     (byte) 0,
                     (byte) 0,
                     (byte) 9,
                     (byte) 1
        };


    private static final byte[] AMQP_0_10_HEADER =
        new byte[] { (byte) 'A',
                     (byte) 'M',
                     (byte) 'Q',
                     (byte) 'P',
                     (byte) 1,
                     (byte) 1,
                     (byte) 0,
                     (byte) 10
        };


    private static final byte[] AMQP_1_0_0_HEADER =
            new byte[] {
                    (byte)'A',
                    (byte)'M',
                    (byte)'Q',
                    (byte)'P',
                    (byte) 0,
                    (byte) 1,
                    (byte) 0,
                    (byte) 0
            };


    private byte[] getAmqpHeader(final AmqpProtocolVersion version)
    {
        switch(version)
        {
            case v0_8:
                return AMQP_0_8_HEADER;
            case v0_9:
                return AMQP_0_9_HEADER;
            case v0_9_1:
                return AMQP_0_9_1_HEADER;
            case v0_10:
                return AMQP_0_10_HEADER;
            case v1_0_0:
                return AMQP_1_0_0_HEADER;
            default:
                fail("unknown AMQP version, appropriate header must be added for new protocol version");
                return null;
        }
    }

    /**
     * Test to verify that connections established using a MultiVersionProtocolEngine are assigned
     * IDs from a common sequence, independent of the protocol version under use.
     */
    public void testDifferentProtocolVersionsShareCommonIDNumberingSequence()
    {
        Set<AmqpProtocolVersion> versions = EnumSet.allOf(AmqpProtocolVersion.class);

        MultiVersionProtocolEngineFactory factory =
            new MultiVersionProtocolEngineFactory(_broker, versions, null);

        //create a dummy to retrieve the 'current' ID number
        long previousId = factory.newProtocolEngine().getConnectionId();

        //create a protocol engine and send the AMQP header for all supported AMQP verisons,
        //ensuring the ID assigned increases as expected
        for(AmqpProtocolVersion version : versions)
        {
            long expectedID = previousId + 1;
            byte[] header = getAmqpHeader(version);
            assertNotNull("protocol header should not be null", header);

            ServerProtocolEngine engine = factory.newProtocolEngine();
            TestNetworkConnection conn = new TestNetworkConnection();
            engine.setNetworkConnection(conn, conn.getSender());
            assertEquals("ID did not increment as expected", expectedID, engine.getConnectionId());

            //actually feed in the AMQP header for this protocol version, and ensure the ID remains consistent
            engine.received(ByteBuffer.wrap(header));
            assertEquals("ID was not as expected following receipt of the AMQP version header", expectedID, engine.getConnectionId());

            previousId = expectedID;
            engine.closed();
        }
    }

    /**
     * Test to verify that when requesting a ProtocolEngineFactory to produce engines having a default reply to unsupported
     * version initiations, there is enforcement that the default reply is itself a supported protocol version.
     */
    public void testUnsupportedDefaultReplyCausesIllegalArgumentException()
    {
        Set<AmqpProtocolVersion> versions = EnumSet.allOf(AmqpProtocolVersion.class);
        versions.remove(AmqpProtocolVersion.v0_9);

        try
        {
            new MultiVersionProtocolEngineFactory(_broker, versions, AmqpProtocolVersion.v0_9);
            fail("should not have been allowed to create the factory");
        }
        catch(IllegalArgumentException iae)
        {
            //expected
        }
    }
}
