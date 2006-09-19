/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.client.transport;

import org.apache.mina.common.IoConnector;
import org.apache.mina.transport.socket.nio.SocketConnector;

/**
 * The TransportConnection is a helper class responsible for connecting to an AMQ server. It sets up
 * the underlying connector, which currently always uses TCP/IP sockets. It creates the
 * "protocol handler" which deals with MINA protocol events.
 *
 * Could be extended in future to support different transport types by turning this into concrete class/interface
 * combo.
 */
public class TransportConnection
{
    private static ITransportConnection _instance;

    static
    {
        if (Boolean.getBoolean("amqj.useBlockingIo"))
        {
            _instance = new SocketTransportConnection(new SocketTransportConnection.SocketConnectorFactory() {
                public IoConnector newSocketConnector() {
                    return new org.apache.qpid.bio.SocketConnector(); // blocking connector
                }
            });
        }
        else
        {
            _instance = new SocketTransportConnection(new SocketTransportConnection.SocketConnectorFactory() {
                public IoConnector newSocketConnector() {
                    SocketConnector result = new SocketConnector(); // non-blocking connector

                    // Don't have the connector's worker thread wait around for other connections (we only use
                    // one SocketConnector per connection at the moment anyway). This allows short-running
                    // clients (like unit tests) to complete quickly.
                    result.setWorkerTimeout(0L);

                    return result;
                }
            });
        }
    }

    public static void setInstance(ITransportConnection transport)
    {
        _instance = transport;
    }

    public static ITransportConnection getInstance()
    {
        return _instance;
    }
}
