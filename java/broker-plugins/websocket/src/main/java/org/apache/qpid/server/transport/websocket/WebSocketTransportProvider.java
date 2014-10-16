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
package org.apache.qpid.server.transport.websocket;

import java.util.Set;

import javax.net.ssl.SSLContext;

import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.transport.AcceptingTransport;
import org.apache.qpid.server.transport.TransportProvider;

class WebSocketTransportProvider implements TransportProvider
{
    public WebSocketTransportProvider()
    {
    }

    @Override
    public AcceptingTransport createTransport(final Set<Transport> transports,
                                              final SSLContext sslContext,
                                              final AmqpPort<?> port,
                                              final Set<Protocol> supported,
                                              final Protocol defaultSupportedProtocolReply)
    {
        return new WebSocketProvider(transports.iterator().next(),
                                     sslContext,
                                     port,
                                     supported,
                                     defaultSupportedProtocolReply);
    }
}
