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
package org.apache.qpid.server.protocol.v0_8;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.network.NetworkConnection;

public class AMQProtocolEngineTest extends QpidTestCase
{
    private Broker<?> _broker;
    private AmqpPort<?> _port;
    private NetworkConnection _network;
    private Transport _transport;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _broker = BrokerTestHelper.createBrokerMock();
        when(_broker.getConnection_closeWhenNoRoute()).thenReturn(true);

        _port = mock(AmqpPort.class);
        when(_port.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(AmqpPort.DEFAULT_MAX_MESSAGE_SIZE);

        _network = mock(NetworkConnection.class);
        _transport = Transport.TCP;
    }

    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            BrokerTestHelper.tearDown();
        }
    }

    public void testSetClientPropertiesForNoRouteProvidedAsString()
    {
        AMQProtocolEngine engine = new AMQProtocolEngine(_broker, _network, 0, _port, _transport);
        assertTrue("Unexpected closeWhenNoRoute before client properties set", engine.isCloseWhenNoRoute());

        Map<String, Object> clientProperties = new HashMap<String, Object>();
        clientProperties.put(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE, Boolean.FALSE.toString());
        engine.setClientProperties(FieldTable.convertToFieldTable(clientProperties));

        assertFalse("Unexpected closeWhenNoRoute after client properties set", engine.isCloseWhenNoRoute());
    }

    public void testSetClientPropertiesForNoRouteProvidedAsBoolean()
    {
        AMQProtocolEngine engine = new AMQProtocolEngine(_broker, _network, 0, _port, _transport);
        assertTrue("Unexpected closeWhenNoRoute before client properties set", engine.isCloseWhenNoRoute());

        Map<String, Object> clientProperties = new HashMap<String, Object>();
        clientProperties.put(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE, Boolean.FALSE);
        engine.setClientProperties(FieldTable.convertToFieldTable(clientProperties));

        assertFalse("Unexpected closeWhenNoRoute after client properties set", engine.isCloseWhenNoRoute());
    }

    public void testThrownExceptionOnSendingResponseFromExceptionHandler()
    {
        ByteBufferSender sender = mock(ByteBufferSender.class);
        when(_network.getSender()).thenReturn(sender);
        doThrow(new SenderException("exception on close")).when(sender).close();
        doThrow(new SenderException("exception on send")).when(sender).send(any(ByteBuffer.class));

        AMQProtocolEngine engine = new AMQProtocolEngine(_broker, _network, 0, _port, _transport);

        try
        {
            engine.exception(new ConnectionScopedRuntimeException("test"));
        }
        catch (Exception e)
        {
            fail("Unexpected exception is thrown " + e);
        }

        doThrow(new NullPointerException("unexpected exception")).when(sender).send(any(ByteBuffer.class));
        try
        {
            engine.exception(new ConnectionScopedRuntimeException("test"));
            fail("Unexpected exception should be reported");
        }
        catch (NullPointerException e)
        {
            // pass
        }

    }

    public void testExceptionHandling()
    {
        ByteBufferSender sender = mock(ByteBufferSender.class);
        when(_network.getSender()).thenReturn(sender);

        AMQProtocolEngine engine = new AMQProtocolEngine(_broker, _network, 0, _port, _transport);

        try
        {
            engine.exception(new ConnectionScopedRuntimeException("test"));
        }
        catch (Exception e)
        {
            fail("Unexpected exception is thrown " + e);
        }


        try
        {
            engine.exception(new SenderException("test"));
        }
        catch (NullPointerException e)
        {
            fail("Unexpected exception should be reported");
        }

        try
        {
            engine.exception(new NullPointerException("test"));
            fail("NullPointerException should be re-thrown");
        }
        catch (NullPointerException e)
        {
            //pass
        }

        try
        {
            engine.exception(new ServerScopedRuntimeException("test"));
            fail("ServerScopedRuntimeException should be re-thrown");
        }
        catch (ServerScopedRuntimeException e)
        {
            //pass
        }

        try
        {
            engine.exception(new AMQException(AMQConstant.INTERNAL_ERROR, "test"));
            fail("AMQException should be re-thrown as ServerScopedRuntimeException");
        }
        catch (ServerScopedRuntimeException e)
        {
            //pass
        }

        try
        {
            engine.exception(new Error("test"));
            fail("Error should be re-thrown");
        }
        catch (Error e)
        {
            //pass
        }
    }

}
