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
package org.apache.qpid.server.protocol.v1_0;

import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.server.plugin.ProtocolEngineCreator;
import org.apache.qpid.transport.network.NetworkConnection;

public class ProtocolEngineCreator_1_0_0 implements ProtocolEngineCreator
{
    private static final byte[] AMQP_1_0_0_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 0,
                         (byte) 1,
                         (byte) 0,
                         (byte) 0
            };

    public ProtocolEngineCreator_1_0_0()
    {
    }

    public AmqpProtocolVersion getVersion()
    {
        return AmqpProtocolVersion.v1_0_0;
    }


    public byte[] getHeaderIdentifier()
    {
        return AMQP_1_0_0_HEADER;
    }

    public ServerProtocolEngine newProtocolEngine(Broker broker,
                                                  NetworkConnection network,
                                                  Port port,
                                                  Transport transport,
                                                  long id)
    {
        return new ProtocolEngine_1_0_0(network, broker, id, port, transport);
    }

    private static ProtocolEngineCreator INSTANCE = new ProtocolEngineCreator_1_0_0();

    public static ProtocolEngineCreator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getType()
    {
        return getVersion().toString() + "_NO_SASL";
    }
}
