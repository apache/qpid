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
package org.apache.qpid.server.transport;

import org.apache.qpid.configuration.Configured;
import org.apache.mina.common.IoAcceptor;

public class ConnectorConfiguration
{
    public static final String DEFAULT_PORT = "5672";

    public static final String SSL_PORT = "8672";

    @Configured(path = "connector.processors",
                defaultValue = "4")
    public int processors;

    @Configured(path = "connector.port",
                defaultValue = DEFAULT_PORT)
    public int port;

    @Configured(path = "connector.bind",
                defaultValue = "wildcard")
    public String bindAddress;

    @Configured(path = "connector.sslport",
                defaultValue = SSL_PORT)
    public int sslPort;

    @Configured(path = "connector.socketReceiveBuffer",
                defaultValue = "32767")
    public int socketReceiveBufferSize;

    @Configured(path = "connector.socketWriteBuffer",
                defaultValue = "32767")
    public int socketWriteBuferSize;

    @Configured(path = "connector.tcpNoDelay",
                defaultValue = "true")
    public boolean tcpNoDelay;

    @Configured(path = "advanced.filterchain[@enableExecutorPool]",
                defaultValue = "false")
    public boolean enableExecutorPool;

    @Configured(path = "advanced.enablePooledAllocator",
                defaultValue = "false")
    public boolean enablePooledAllocator;

    @Configured(path = "advanced.enableDirectBuffers",
                defaultValue = "false")
    public boolean enableDirectBuffers;

    @Configured(path = "connector.ssl",
                defaultValue = "false")
    public boolean enableSSL;

    @Configured(path = "connector.nonssl",
                defaultValue = "true")
    public boolean enableNonSSL;

    @Configured(path = "advanced.useBlockingIo",
                defaultValue = "false")
    public boolean useBlockingIo;

    public IoAcceptor createAcceptor()
    {
        if(useBlockingIo)
        {
            System.out.println("Using blocking io");
            return new org.apache.qpid.bio.SocketAcceptor();
        }
        else
        {
            return new org.apache.mina.transport.socket.nio.SocketAcceptor(processors);
        }
    }
}
