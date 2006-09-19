/*
 *   @(#) $Id: SocketConnector.java 389042 2006-03-27 07:49:41Z trustin $
 *
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.qpid.bio;

import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.support.BaseIoConnector;
import org.apache.mina.common.support.DefaultConnectFuture;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 */
public class SocketConnector extends BaseIoConnector
{
    /**
     * @noinspection StaticNonFinalField
     */
    private static final Sequence idSequence = new Sequence();

    private final Object lock = new Object();
    private final String threadName = "SocketConnector-" + idSequence.nextId();
    private final IoServiceConfig defaultConfig = new SocketConnectorConfig();
    private final Set managedSessions = Collections.synchronizedSet(new HashSet());

    /**
     * Create a connector with a single processing thread
     */
    public SocketConnector()
    {
    }

    public IoServiceConfig getDefaultConfig()
    {
        return defaultConfig;
    }

    public ConnectFuture connect(SocketAddress address, IoHandler handler, IoServiceConfig config)
    {
        return connect(address, null, handler, config);
    }

    public ConnectFuture connect(SocketAddress address, SocketAddress localAddress,
                                 IoHandler handler, IoServiceConfig config)
    {
        if (address == null)
        {
            throw new NullPointerException("address");
        }
        if (handler == null)
        {
            throw new NullPointerException("handler");
        }

        if (! (address instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected address type: " + address.getClass());
        }
        if (localAddress != null && !(localAddress instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected local address type: " + localAddress.getClass());
        }
        if (config == null)
        {
            config = getDefaultConfig();
        }

        DefaultConnectFuture future = new DefaultConnectFuture();
        try
        {

            //Socket socket = new Socket();
            //socket.connect(address);
            //SimpleSocketChannel channel = new SimpleSocketChannel(socket);
            //SocketAddress serviceAddress = socket.getRemoteSocketAddress();

            SocketChannel channel = SocketChannel.open(address);
            channel.configureBlocking(true);
            SocketAddress serviceAddress = channel.socket().getRemoteSocketAddress();


            SocketSessionImpl session = newSession(channel, handler, config, channel.socket().getRemoteSocketAddress());
            future.setSession(session);
        }
        catch (IOException e)
        {
            future.setException(e);
        }

        return future;
    }

    private SocketSessionImpl newSession(ByteChannel channel, IoHandler handler, IoServiceConfig config, SocketAddress serviceAddress)
            throws IOException
    {
        SocketSessionImpl session = new SocketSessionImpl(this,
                                                          (SocketSessionConfig) config.getSessionConfig(),
                                                          handler,
                                                          channel,
                                                          serviceAddress);
        try
        {
            getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getThreadModel().buildFilterChain(session.getFilterChain());
            ((SocketFilterChain) session.getFilterChain()).sessionCreated(session);

            session.start();
            //not sure if this will work... socket is already opened before the created callback is called...
            ((SocketFilterChain) session.getFilterChain()).sessionOpened(session);
        }
        catch (Throwable e)
        {
            throw (IOException) new IOException("Failed to create a session.").initCause(e);
        }

        //TODO: figure out how the managed session are used/ what they are etc.
        //session.getManagedSessions().add( session );


        return session;
    }
}
