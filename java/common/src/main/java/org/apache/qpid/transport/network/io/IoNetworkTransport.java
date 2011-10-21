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
package org.apache.qpid.transport.network.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkTransport;
import org.apache.qpid.transport.util.Logger;

public class IoNetworkTransport implements NetworkTransport, IoContext
{
    static
    {
        org.apache.mina.common.ByteBuffer.setAllocator
            (new org.apache.mina.common.SimpleByteBufferAllocator());
        org.apache.mina.common.ByteBuffer.setUseDirectBuffers
            (Boolean.getBoolean("amqj.enableDirectBuffers"));
    }

    private static final Logger log = Logger.get(IoNetworkTransport.class);

    private Socket socket;
    private Sender<ByteBuffer> sender;
    private IoReceiver receiver;
    private long timeout = 60000; 
    private ConnectionSettings settings;    
    
    public void init(ConnectionSettings settings)
    {
        try
        {
            this.settings = settings;
            InetAddress address = InetAddress.getByName(settings.getHost());
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.setTcpNoDelay(settings.isTcpNodelay());

            log.debug("default-SO_RCVBUF : %s", socket.getReceiveBufferSize());
            log.debug("default-SO_SNDBUF : %s", socket.getSendBufferSize());

            socket.setSendBufferSize(settings.getWriteBufferSize());
            socket.setReceiveBufferSize(settings.getReadBufferSize());

            log.debug("new-SO_RCVBUF : %s", socket.getReceiveBufferSize());
            log.debug("new-SO_SNDBUF : %s", socket.getSendBufferSize());

            socket.connect(new InetSocketAddress(address, settings.getPort()));
        }
        catch (SocketException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }
        catch (IOException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }
    }

    public void receiver(Receiver<ByteBuffer> delegate)
    {
        receiver = new IoReceiver(this, delegate,
                2*settings.getReadBufferSize() , timeout);
    }

    public Sender<ByteBuffer> sender()
    {
        return new IoSender(this, 2*settings.getWriteBufferSize(), timeout);
    }

    public void close()
    {
        
    }

    public Sender<ByteBuffer> getSender()
    {
        return sender;
    }

    public IoReceiver getReceiver()
    {
        return receiver;
    }

    public Socket getSocket()
    {
        return socket;
    }
}
