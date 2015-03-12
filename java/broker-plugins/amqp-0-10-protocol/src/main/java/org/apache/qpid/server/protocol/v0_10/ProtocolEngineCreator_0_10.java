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
package org.apache.qpid.server.protocol.v0_10;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.plugin.ProtocolEngineCreator;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.network.NetworkConnection;

@PluggableService
public class ProtocolEngineCreator_0_10 implements ProtocolEngineCreator
{

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


    public ProtocolEngineCreator_0_10()
    {
    }

    public Protocol getVersion()
    {
        return Protocol.AMQP_0_10;
    }


    public byte[] getHeaderIdentifier()
    {
        return AMQP_0_10_HEADER;
    }

    public ServerProtocolEngine newProtocolEngine(Broker<?> broker,
                                                  NetworkConnection network,
                                                  AmqpPort<?> port,
                                                  Transport transport,
                                                  long id)
    {
        String fqdn = null;
        SocketAddress address = network.getLocalAddress();
        if (address instanceof InetSocketAddress)
        {
            fqdn = ((InetSocketAddress) address).getHostName();
        }
        final ConnectionDelegate connDelegate = new ServerConnectionDelegate(broker,
                fqdn, broker.getSubjectCreator(address, transport.isSecure())
        );

        ServerConnection conn = new ServerConnection(id,broker, port, transport);

        conn.setConnectionDelegate(connDelegate);
        conn.setRemoteAddress(network.getRemoteAddress());
        conn.setLocalAddress(network.getLocalAddress());

        ProtocolEngine_0_10 protocolEngine = new ProtocolEngine_0_10(conn, network);
        conn.setProtocolEngine(protocolEngine);

        return protocolEngine;
    }


    private static ProtocolEngineCreator INSTANCE = new ProtocolEngineCreator_0_10();

    public static ProtocolEngineCreator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getType()
    {
        return getVersion().toString();
    }
}
