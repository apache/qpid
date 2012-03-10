/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.qpid.server.configuration;

import java.net.InetSocketAddress;
import org.apache.qpid.transport.NetworkTransportConfiguration;

public class ServerNetworkTransportConfiguration implements NetworkTransportConfiguration
{
    private final ServerConfiguration _serverConfig;
    private final String _transport;
    private InetSocketAddress _address;

    public ServerNetworkTransportConfiguration(final ServerConfiguration serverConfig, 
                                               final InetSocketAddress address,
                                               final String transport)
    {
        _serverConfig = serverConfig;
        _address = address;
        _transport = transport;
    }

    public Boolean getTcpNoDelay()
    {
        return _serverConfig.getTcpNoDelay();
    }

    public Integer getSendBufferSize()
    {
        return _serverConfig.getWriteBufferSize();
    }

    public Integer getReceiveBufferSize()
    {
        return _serverConfig.getReceiveBufferSize();
    }

    public Integer getPort()
    {
        return _address.getPort();
    }

    public String getHost()
    {
        return _address.getHostName();
    }

    public String getTransport()
    {
        return _transport;
    }

    public Integer getConnectorProcessors()
    {
        return _serverConfig.getConnectorProcessors();
    }

    public InetSocketAddress getAddress()
    {
        return _address;
    }
}
