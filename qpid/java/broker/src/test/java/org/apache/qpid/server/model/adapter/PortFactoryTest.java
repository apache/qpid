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
package org.apache.qpid.server.model.adapter;

import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.transport.AmqpPortAdapter;

public class PortFactoryTest extends TestCase
{
    private UUID _portId = UUID.randomUUID();
    private int _portNumber = 123;
    private Set<String> _tcpStringSet = Collections.singleton(Transport.TCP.name());
    private Set<Transport> _tcpTransportSet = Collections.singleton(Transport.TCP);

    private Map<String, Object> _attributes = new HashMap<String, Object>();

    private Broker _broker = mock(Broker.class);
    private PortFactory _portFactory = new PortFactory();

    @Override
    protected void setUp() throws Exception
    {
        _attributes.put(Port.PORT, _portNumber);
        _attributes.put(Port.TRANSPORTS, _tcpStringSet);

        _attributes.put(Port.TCP_NO_DELAY, "true");
        _attributes.put(Port.RECEIVE_BUFFER_SIZE, "1");
        _attributes.put(Port.SEND_BUFFER_SIZE, "2");
        _attributes.put(Port.NEED_CLIENT_AUTH, "true");
        _attributes.put(Port.WANT_CLIENT_AUTH, "true");
    }

    public void testCreateAmqpPort()
    {
        Set<Protocol> amqp010ProtocolSet = Collections.singleton(Protocol.AMQP_0_10);
        Set<String> amqp010StringSet = Collections.singleton(Protocol.AMQP_0_10.name());
        _attributes.put(Port.PROTOCOLS, amqp010StringSet);

        Port port = _portFactory.createPort(_portId, _broker, _attributes);

        assertNotNull(port);
        assertTrue(port instanceof AmqpPortAdapter);
        assertEquals(_portId, port.getId());
        assertEquals(_portNumber, port.getPort());
        assertEquals(_tcpTransportSet, port.getTransports());
        assertEquals(amqp010ProtocolSet, port.getProtocols());
    }

    public void testCreateNonAmqpPort()
    {
        Set<Protocol> nonAmqpProtocolSet = Collections.singleton(Protocol.JMX_RMI);
        Set<String> nonAmqpStringSet = Collections.singleton(Protocol.JMX_RMI.name());
        _attributes.put(Port.PROTOCOLS, nonAmqpStringSet);

        Port port = _portFactory.createPort(_portId, _broker, _attributes);

        assertNotNull(port);
        assertFalse("Port should be a PortAdapter, not its AMQP-specific subclass", port instanceof AmqpPortAdapter);
        assertEquals(_portId, port.getId());
        assertEquals(_portNumber, port.getPort());
        assertEquals(_tcpTransportSet, port.getTransports());
        assertEquals(nonAmqpProtocolSet, port.getProtocols());
    }

    public void testCreateMixedAmqpAndNonAmqpThrowsException()
    {
        Set<String> mixedProtocolSet = new HashSet<String>(Arrays.asList(Protocol.AMQP_0_10.name(), Protocol.JMX_RMI.name()));
        _attributes.put(Port.PROTOCOLS, mixedProtocolSet);

        try
        {
            _portFactory.createPort(_portId, _broker, _attributes);
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException e)
        {
            // pass
        }
    }
}
