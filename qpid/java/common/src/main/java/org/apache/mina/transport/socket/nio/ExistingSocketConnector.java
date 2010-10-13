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
package org.apache.mina.transport.socket.nio;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.ExceptionMonitor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.support.AbstractIoFilterChain;
import org.apache.mina.common.support.DefaultConnectFuture;
import org.apache.mina.util.NewThreadExecutor;

/**
 * Extension of {@link SocketConnector} using an existing open socket.
 */
public class ExistingSocketConnector extends SocketConnector
{
    private static final Map<String, Socket> OPEN_SOCKET_REGISTER = new ConcurrentHashMap<String, Socket>();

    private static final AtomicInteger nextId = new AtomicInteger();
    private final int id = nextId.getAndIncrement();
    private final SocketIoProcessor[] ioProcessors;
    private final int processorCount;
    private int processorDistributor = 0;

    private Socket _openSocket = null;

    public static void registerOpenSocket(String socketID, Socket openSocket)
    {
        OPEN_SOCKET_REGISTER.put(socketID, openSocket);
    }

    public static Socket removeOpenSocket(String socketID)
    {
        return OPEN_SOCKET_REGISTER.remove(socketID);
    }

    public void setOpenSocket(Socket openSocket)
    {
        _openSocket = openSocket;
    }

    /**
     * Create a connector with a single processing thread using a NewThreadExecutor
     */
    public ExistingSocketConnector()
    {
        this(1, new NewThreadExecutor());
    }

    /**
     * Create a connector with the desired number of processing threads
     *
     * @param processorCount Number of processing threads
     * @param executor Executor to use for launching threads
     */
    public ExistingSocketConnector(int processorCount, Executor executor) {
        if (processorCount < 1)
        {
            throw new IllegalArgumentException("Must have at least one processor");
        }

        this.processorCount = processorCount;
        ioProcessors = new SocketIoProcessor[processorCount];

        for (int i = 0; i < processorCount; i++)
        {
            ioProcessors[i] = new SocketIoProcessor("SocketConnectorIoProcessor-" + id + "." + i, executor);
        }
    }

    /**
     * Changes here from the Mina OpenSocketConnector.
     * 
     * Ignoring all address as they are not needed.
     */
    public ConnectFuture connect(SocketAddress address, SocketAddress localAddress, IoHandler handler, IoServiceConfig config)
    {
        if (handler == null)
        {
            throw new NullPointerException("handler");
        }
        if (config == null)
        {
            config = getDefaultConfig();
        }
        if (_openSocket == null)
        {
            throw new IllegalArgumentException("Specifed Socket not active");
        }

        boolean success = false;

        try
        {
            DefaultConnectFuture future = new DefaultConnectFuture();
            newSession(_openSocket.getChannel(), handler, config, future);
            success = true;
            return future;
        }
        catch (IOException e)
        {
            return DefaultConnectFuture.newFailedFuture(e);
        }
        finally
        {
            if (!success && _openSocket != null)
            {
                try
                {
                    _openSocket.close();
                }
                catch (IOException e)
                {
                    ExceptionMonitor.getInstance().exceptionCaught(e);
                }
            }
        }
    }

    private void newSession(SocketChannel ch, IoHandler handler, IoServiceConfig config, ConnectFuture connectFuture)
            throws IOException
    {
        SocketSessionImpl session = new SocketSessionImpl(this,
                nextProcessor(), getListeners(), config, ch, handler,
                ch.socket().getRemoteSocketAddress());
        try
        {
            getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getThreadModel().buildFilterChain(session.getFilterChain());
        }
        catch (Throwable e)
        {
            throw (IOException) new IOException("Failed to create a session.").initCause(e);
        }

        // Set the ConnectFuture of the specified session, which will be
        // removed and notified by AbstractIoFilterChain eventually.
        session.setAttribute(AbstractIoFilterChain.CONNECT_FUTURE, connectFuture);

        // Forward the remaining process to the SocketIoProcessor.
        session.getIoProcessor().addNew(session);
    }

    private SocketIoProcessor nextProcessor()
    {
        if (processorDistributor == Integer.MAX_VALUE)
        {
            processorDistributor = Integer.MAX_VALUE % processorCount;
        }

        return ioProcessors[processorDistributor++ % processorCount];
    }
}