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

import java.net.Socket;

import org.apache.qpid.transport.Binding;
import org.apache.qpid.transport.ByteBufferSender;
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
public final class IoTransport<E>
{


    private static final Logger log = Logger.get(IoTransport.class);

    private static int DEFAULT_READ_WRITE_BUFFER_SIZE = 64 * 1024;
    private static int readBufferSize = Integer.getInteger
        ("amqj.receiveBufferSize", DEFAULT_READ_WRITE_BUFFER_SIZE);
    private static int writeBufferSize = Integer.getInteger
        ("amqj.sendBufferSize", DEFAULT_READ_WRITE_BUFFER_SIZE);

    private Socket socket;
    private ByteBufferSender sender;
    private E endpoint;
    private IoReceiver receiver;
    private long timeout = 60000;

    IoTransport(Socket socket, Binding<E> binding)
    {
        this.socket = socket;
        setupTransport(socket, binding);
    }

    private void setupTransport(Socket socket, Binding<E> binding)
    {
        IoSender ios = new IoSender(socket, 2*writeBufferSize, timeout);
        ios.initiate();

        this.sender = ios;
        this.endpoint = binding.endpoint(sender);
        this.receiver = new IoReceiver(socket, binding.receiver(endpoint),
                                       2*readBufferSize, timeout);
        this.receiver.initiate();

        ios.setReceiver(this.receiver);
    }

    public ByteBufferSender getSender()
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
