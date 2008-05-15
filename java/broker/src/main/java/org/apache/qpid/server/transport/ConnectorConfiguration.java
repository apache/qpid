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
package org.apache.qpid.server.transport;

import org.apache.mina.common.IoAcceptor;
import org.apache.mina.util.NewThreadExecutor;
import org.apache.qpid.configuration.Configured;
import org.apache.log4j.Logger;

public class ConnectorConfiguration
{
    private static final Logger _logger = Logger.getLogger(ConnectorConfiguration.class);

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

    @Configured(path = "connector.ssl.enabled",
                defaultValue = "false")
    public boolean enableSSL;

    @Configured(path = "connector.ssl.sslOnly",
                defaultValue = "true")
    public boolean sslOnly;

    @Configured(path = "connector.ssl.port",
                defaultValue = SSL_PORT)
    public int sslPort;

    @Configured(path = "connector.ssl.keystorePath",
                defaultValue = "none")
    public String keystorePath;

    @Configured(path = "connector.ssl.keystorePassword",
                defaultValue = "none")
    public String keystorePassword;

    @Configured(path = "connector.ssl.certType",
                defaultValue = "SunX509")
    public String certType;

    @Configured(path = "connector.qpidnio",
                defaultValue = "false")
    public boolean _multiThreadNIO;

    @Configured(path = "advanced.useWriteBiasedPool",
                    defaultValue = "false")        
    public boolean useBiasedWrites;


    public IoAcceptor createAcceptor()
    {
        if (_multiThreadNIO)
        {
            _logger.warn("Using Qpid Multithreaded IO Processing");
            return new org.apache.mina.transport.socket.nio.MultiThreadSocketAcceptor(processors, new NewThreadExecutor());
        }
        else
        {
            _logger.warn("Using Mina IO Processing");
            return new org.apache.mina.transport.socket.nio.SocketAcceptor(processors, new NewThreadExecutor());
        }
    }
}
