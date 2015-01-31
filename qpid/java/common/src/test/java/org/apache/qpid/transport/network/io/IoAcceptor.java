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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

import org.apache.log4j.Logger;

import org.apache.qpid.transport.Binding;


/**
 * IoAcceptor
 *
 */

public class IoAcceptor<E> extends Thread
{
    private static final Logger _logger = Logger.getLogger(IoAcceptor.class);

    private volatile boolean _closed = false;

    private ServerSocket socket;
    private Binding<E> binding;

    public IoAcceptor(SocketAddress address, Binding<E> binding)
        throws IOException
    {
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address);
        this.binding = binding;

        setName(String.format("IoAcceptor - %s", socket.getInetAddress()));
    }

    /**
        Close the underlying ServerSocket if it has not already been closed.
     */
    public void close() throws IOException
    {
        _closed = true;

        if (!socket.isClosed())
        {
            socket.close();
        }
    }

    public IoAcceptor(String host, int port, Binding<E> binding)
        throws IOException
    {
        this(new InetSocketAddress(host, port), binding);
    }

    public void run()
    {
        while (!_closed)
        {
            try
            {
                Socket sock = socket.accept();
                IoTransport<E> transport = new IoTransport<E>(sock, binding);
            }
            catch (IOException e)
            {
                if (!_closed)
                {
                    _logger.error("Error in IoAcceptor thread", e);
                    closeSocketIfNecessary(socket);
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException ie)
                    {
                        _logger.debug("Stopping io acceptor due to interrupt request");
                        _closed = true;
                    }
                }
            }
        }
    }

    private void closeSocketIfNecessary(final ServerSocket socket)
    {
        if(socket != null)
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                _logger.debug("Exception while closing socket", e);
            }
        }
    }
}
