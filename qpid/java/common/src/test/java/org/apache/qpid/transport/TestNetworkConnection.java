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
import java.nio.ByteBuffer;

import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.NetworkTransport;

/**
 * Test implementation of {@link NetworkTransport}.
 * 
 * Exposes a given {@link SocketAddress} as both local and remote addresses,
 * either by setting the address directly or by setting the host and port
 * separately. All other methods are empty.
 */
public class TestNetworkConnection implements NetworkConnection
{
    private String _host = "127.0.0.1";
    private int _port = 1;
    private SocketAddress _address = null;
    
    public SocketAddress getRemoteAddress()
    {
        return (_address != null) ? _address : new InetSocketAddress(_host, _port);
    }

    public SocketAddress getLocalAddress()
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

    public void close()
    {
    }

    public long getReadBytes()
    {
        return 0;
    }

    public Sender<ByteBuffer> getSender()
    {
        return null;
    }

    public long getWrittenBytes()
    {
        return 0;
    }
}
