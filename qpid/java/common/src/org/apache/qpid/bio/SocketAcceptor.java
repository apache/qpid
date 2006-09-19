/*
 *   @(#) $Id: SocketAcceptor.java 389042 2006-03-27 07:49:41Z trustin $
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

import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.support.BaseIoAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.*;

/**
 */
public class SocketAcceptor extends BaseIoAcceptor
{
    private static final Sequence acceptorSeq = new Sequence();

    private final int id = acceptorSeq.nextId();
    private final String threadName = "SocketAcceptor-" + id;
    private final IoServiceConfig defaultConfig = new SocketAcceptorConfig();
    private final Map services = new HashMap();//SocketAddress => SocketBinding

    public SocketAcceptor()
    {
    }

    /**
     * Binds to the specified <code>address</code> and handles incoming connections with the specified
     * <code>handler</code>.  Backlog value is configured to the value of <code>backlog</code> property.
     *
     * @throws IOException if failed to bind
     */
    public void bind(SocketAddress address, IoHandler handler, IoServiceConfig config) throws IOException
    {
        if (address == null)
        {
            throw new NullPointerException("address");
        }

        if (handler == null)
        {
            throw new NullPointerException("handler");
        }

        if (!(address instanceof InetSocketAddress))
        {
            throw new IllegalArgumentException("Unexpected address type: " + address.getClass());
        }

        if (((InetSocketAddress) address).getPort() == 0)
        {
            throw new IllegalArgumentException("Unsupported port number: 0");
        }

        if (config == null)
        {
            config = getDefaultConfig();
        }

        SocketBinding service = new SocketBinding(address, handler, config);
        synchronized (services)
        {
            services.put(address, service);
        }
        service.start();
    }

    public Set getManagedSessions(SocketAddress address)
    {
        if (address == null)
        {
            throw new NullPointerException("address");
        }

        SocketBinding service = (SocketBinding) services.get(address);

        if (service == null)
        {
            throw new IllegalArgumentException("Address not bound: " + address);
        }

        return Collections.unmodifiableSet(new HashSet(service.sessions));
    }

    public void unbind(SocketAddress address)
    {
        if (address == null)
        {
            throw new NullPointerException("address");
        }

        SocketBinding service;
        synchronized (services)
        {
            service = (SocketBinding) services.remove(address);
        }

        if (service == null)
        {
            throw new IllegalArgumentException("Address not bound: " + address);
        }

        try
        {
            service.unbind();
        }
        catch (IOException e)
        {
            //TODO: handle properly
            e.printStackTrace();
        }
    }

    public void unbindAll()
    {
        synchronized (services)
        {
            for (Iterator i = services.entrySet().iterator(); i.hasNext();)
            {
                SocketBinding service = (SocketBinding) i.next();
                try
                {
                    service.unbind();
                }
                catch (IOException e)
                {
                    //TODO: handle properly
                    e.printStackTrace();
                }
                i.remove();
            }
        }
    }

    public boolean isBound(SocketAddress address)
    {
        synchronized (services)
        {
            return services.containsKey(address);
        }
    }

    public Set getBoundAddresses()
    {
        throw new UnsupportedOperationException("getBoundAddresses() not supported by blocking IO Acceptor");
    }

    public IoServiceConfig getDefaultConfig()
    {
        return defaultConfig;
    }

    private class SocketBinding implements Runnable
    {
        private final SocketAddress address;
        private final ServerSocketChannel service;
        //private final ServerSocket service;
        private final IoServiceConfig config;
        private final IoHandler handler;
        private final List sessions = new ArrayList();
        private volatile boolean stopped = false;
        private Thread runner;

        SocketBinding(SocketAddress address, IoHandler handler, IoServiceConfig config) throws IOException
        {
            this.address = address;
            this.handler = handler;
            this.config = config;

            service = ServerSocketChannel.open();
            service.socket().bind(address);

            //service = new ServerSocket();
            //service.bind(address);
        }

        void unbind() throws IOException
        {
            stopped = true;
            //shutdown all sessions
            for (Iterator i = sessions.iterator(); i.hasNext();)
            {
                ((SocketSessionImpl) i.next()).close();
                i.remove();
            }

            //close server socket
            service.close();
            if (runner != null)
            {
                try
                {
                    runner.join();
                }
                catch (InterruptedException e)
                {
                    //ignore and return
                    System.err.println("Warning: interrupted on unbind(" + address + ")");
                }
            }
        }

        void start()
        {
            runner = new Thread(this);
            runner.start();
        }

        public void run()
        {
            while (!stopped)
            {
                try
                {
                    accept();
                }
                catch (Exception e)
                {
                    //handle this better...
                    e.printStackTrace();
                }
            }
        }

        private void accept() throws Exception
        {
            //accept(new SimpleSocketChannel(service.accept()));
            accept(service.accept());
        }

        private void accept(ByteChannel channel) throws Exception
        {
            //SocketChannel channel;
            //start session
            SocketSessionImpl session = new SocketSessionImpl(SocketAcceptor.this,
                                                              (SocketSessionConfig) defaultConfig.getSessionConfig(),
                                                              handler,
                                                              channel,
                                                              address);
            //signal start etc...
            sessions.add(session);

            //TODO
            //need to set up filter chains somehow... (this is copied from connector...)
            getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getFilterChainBuilder().buildFilterChain(session.getFilterChain());
            config.getThreadModel().buildFilterChain(session.getFilterChain());
            ((SocketFilterChain) session.getFilterChain()).sessionCreated(session);

            session.start();
            //not sure if this will work... socket is already opened before the created callback is called...
            ((SocketFilterChain) session.getFilterChain()).sessionOpened(session);
        }
    }
}
