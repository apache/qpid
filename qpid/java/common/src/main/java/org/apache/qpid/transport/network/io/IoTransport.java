/*
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
 */

package org.apache.qpid.transport.network.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.Assembler;
import org.apache.qpid.transport.network.Disassembler;
import org.apache.qpid.transport.network.InputHandler;
import org.apache.qpid.transport.network.OutputHandler;
import org.apache.qpid.transport.util.Logger;

/**
 * This class provides a socket based transport using the java.io
 * classes.
 *
 * The following params are configurable via JVM arguments
 * TCP_NO_DELAY - amqj.tcpNoDelay
 * SO_RCVBUF    - amqj.receiveBufferSize
 * SO_SNDBUF    - amqj.sendBufferSize
 */
public final class IoTransport
{

    private static final Logger log = Logger.get(IoTransport.class);

    private static int DEFAULT_READ_WRITE_BUFFER_SIZE = 64 * 1024;

    private IoReceiver receiver;
    private IoSender sender;
    private Socket socket;
    private int readBufferSize;
    private int writeBufferSize;
    private final long timeout = 60000;

    private IoTransport()
    {
        readBufferSize = Integer.getInteger("amqj.receiveBufferSize",DEFAULT_READ_WRITE_BUFFER_SIZE);
        writeBufferSize = Integer.getInteger("amqj.sendBufferSize",DEFAULT_READ_WRITE_BUFFER_SIZE);
    }

    public static final Connection connect(String host, int port,
            ConnectionDelegate delegate)
    {
        IoTransport handler = new IoTransport();
        return handler.connectInternal(host,port,delegate);
    }

    private Connection connectInternal(String host, int port,
            ConnectionDelegate delegate)
    {
        try
        {
            InetAddress address = InetAddress.getByName(host);
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.setTcpNoDelay(Boolean.getBoolean("amqj.tcpNoDelay"));

            log.debug("default-SO_RCVBUF : %s", socket.getReceiveBufferSize());
            log.debug("default-SO_SNDBUF : %s", socket.getSendBufferSize());

            socket.setSendBufferSize(writeBufferSize);
            socket.setReceiveBufferSize(readBufferSize);

            log.debug("new-SO_RCVBUF : %s", socket.getReceiveBufferSize());
            log.debug("new-SO_SNDBUF : %s", socket.getSendBufferSize());

            socket.connect(new InetSocketAddress(address, port));
        }
        catch (SocketException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }
        catch (IOException e)
        {
            throw new TransportException("Error connecting to broker", e);
        }

        sender = new IoSender(this, 2*writeBufferSize, timeout);
        Connection conn = new Connection
            (new Disassembler(new OutputHandler(sender), 64*1024 - 1),
             delegate);
        receiver = new IoReceiver(this, new InputHandler(new Assembler(conn)), 2*readBufferSize);

        return conn;
    }

    IoSender getSender()
    {
        return sender;
    }

    IoReceiver getReceiver()
    {
        return receiver;
    }

    Socket getSocket()
    {
        return socket;
    }

}
