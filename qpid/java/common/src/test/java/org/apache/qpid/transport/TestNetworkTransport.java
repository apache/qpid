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
package org.apache.qpid.transport;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.qpid.transport.network.NetworkTransport;
import org.apache.qpid.transport.network.io.IoNetworkTransport;

/**
 * Test implementation of {@link NetworkTransport}.
 * 
 * Exposes a {@link SocketAddress}, all other methods are as in {@link IoNetworkTransport}.
 */
public class TestNetworkTransport extends IoNetworkTransport
{
    private String _host = "127.0.0.1";
    private int _port = 1;
    private SocketAddress _address = null;
    
    public SocketAddress getAddress()
    {
        return (_address != null) ? _address : new InetSocketAddress(_host, _port);
    }

    public void setPort(int port)
    {
        _port = port;
    }

    public int getPort()
    {
        return _port;
    }

    public void setHost(String host)
    {
        _host = host;
    }

    public void setAddress(SocketAddress address)
    {
        _address = address;
    }
}
