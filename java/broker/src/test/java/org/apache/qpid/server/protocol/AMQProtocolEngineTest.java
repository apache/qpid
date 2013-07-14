package org.apache.qpid.server.protocol;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolEngine;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.network.NetworkConnection;

public class AMQProtocolEngineTest extends QpidTestCase
{
    private Broker _broker;
    private Port _port;
    private NetworkConnection _network;
    private Transport _transport;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _broker = BrokerTestHelper.createBrokerMock();
        when(_broker.getAttribute(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE)).thenReturn(true);

        _port = mock(Port.class);
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
