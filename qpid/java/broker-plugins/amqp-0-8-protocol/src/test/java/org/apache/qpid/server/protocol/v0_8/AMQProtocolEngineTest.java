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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
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
}
