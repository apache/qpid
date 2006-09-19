/*
 *   @(#) $Id: SocketSessionImpl.java 398039 2006-04-28 23:36:27Z proyal $
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

import org.apache.mina.common.IoFilter.WriteRequest;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoService;
import org.apache.mina.common.IoSessionConfig;
import org.apache.mina.common.RuntimeIOException;
import org.apache.mina.common.TransportType;
import org.apache.mina.common.support.BaseIoSession;
import org.apache.mina.common.support.BaseIoSessionConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfigImpl;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 */
class SocketSessionImpl extends BaseIoSession
{
    private final IoService manager;
    private final SocketSessionConfig config;
    private final SocketFilterChain filterChain;
    private final IoHandler handler;
    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;
    private final SocketAddress serviceAddress;
    private final Socket socket;
    private final ByteChannel channel;
    private final Reader reader;
    private Thread runner;
    private int readBufferSize;

    /**
     * Creates a new instance.
     */
    SocketSessionImpl(IoService manager,
                      SocketSessionConfig config,
                      IoHandler handler,
                      ByteChannel channel,
                      SocketAddress serviceAddress) throws IOException
    {
        this.manager = manager;
        this.filterChain = new SocketFilterChain(this);
        this.handler = handler;
        this.channel = channel;
        if(channel instanceof SocketChannel)
        {
            socket = ((SocketChannel) channel).socket();
        }
        else if(channel instanceof SimpleSocketChannel)
        {
            socket = ((SimpleSocketChannel) channel).socket();
        }
        else
        {
            throw new IllegalArgumentException("Unrecognised channel type: " + channel.getClass());
        }

        this.remoteAddress = socket.getRemoteSocketAddress();
        this.localAddress = socket.getLocalSocketAddress();
        this.serviceAddress = serviceAddress;

        this.config = new SessionConfigImpl(config);

        reader = new Reader(handler, this);
    }

    void start()
    {
        //create & start thread for this...
        runner = new Thread(reader);
        runner.start();
    }

    void shutdown() throws IOException
    {
        filterChain.sessionClosed( this );
        reader.stop();
        channel.close();
    }

    ByteChannel getChannel()
    {
        return channel;
    }

    protected void write0(WriteRequest writeRequest)
    {
        filterChain.filterWrite(this, writeRequest);
    }

    protected void close0()
    {
        filterChain.filterClose(this);
        super.close0();
    }

    protected void updateTrafficMask()
    {
        //TODO
    }

    public IoService getService()
    {
        return manager;
    }

    public IoSessionConfig getConfig()
    {
        return config;
    }

    public IoFilterChain getFilterChain()
    {
        return filterChain;
    }

    public IoHandler getHandler()
    {
        return handler;
    }

    public int getScheduledWriteRequests()
    {
        return 0;
    }

    public int getScheduledWriteBytes()
    {
        return 0;
    }

    public TransportType getTransportType()
    {
        return TransportType.SOCKET;
    }

    public SocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public SocketAddress getLocalAddress()
    {
        return localAddress;
    }

    public SocketAddress getServiceAddress()
    {
        return serviceAddress;
    }

    int getReadBufferSize()
    {
        return readBufferSize;
    }

    private class SessionConfigImpl extends BaseIoSessionConfig implements SocketSessionConfig
    {
        SessionConfigImpl()
        {
        }

        SessionConfigImpl(SocketSessionConfig cfg)
        {
            setKeepAlive(cfg.isKeepAlive());
            setOobInline(cfg.isOobInline());
            setReceiveBufferSize(cfg.getReceiveBufferSize());
            readBufferSize = cfg.getReceiveBufferSize();
            setReuseAddress(cfg.isReuseAddress());
            setSendBufferSize(cfg.getSendBufferSize());
            setSoLinger(cfg.getSoLinger());
            setTcpNoDelay(cfg.isTcpNoDelay());
            if (getTrafficClass() != cfg.getTrafficClass())
            {
                setTrafficClass(cfg.getTrafficClass());
            }
        }


        public boolean isKeepAlive()
        {
            try
            {
                return socket.getKeepAlive();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setKeepAlive(boolean on)
        {
            try
            {
                socket.setKeepAlive(on);
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public boolean isOobInline()
        {
            try
            {
                return socket.getOOBInline();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setOobInline(boolean on)
        {
            try
            {
                socket.setOOBInline(on);
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public boolean isReuseAddress()
        {
            try
            {
                return socket.getReuseAddress();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setReuseAddress(boolean on)
        {
            try
            {
                socket.setReuseAddress(on);
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public int getSoLinger()
        {
            try
            {
                return socket.getSoLinger();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setSoLinger(int linger)
        {
            try
            {
                if (linger < 0)
                {
                    socket.setSoLinger(false, 0);
                }
                else
                {
                    socket.setSoLinger(true, linger);
                }
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public boolean isTcpNoDelay()
        {
            try
            {
                return socket.getTcpNoDelay();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setTcpNoDelay(boolean on)
        {
            try
            {
                socket.setTcpNoDelay(on);
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public int getTrafficClass()
        {
            if (SocketSessionConfigImpl.isGetTrafficClassAvailable())
            {
                try
                {
                    return socket.getTrafficClass();
                }
                catch (SocketException e)
                {
                    throw new RuntimeIOException(e);
                }
            }
            else
            {
                return 0;
            }
        }

        public void setTrafficClass(int tc)
        {
            if (SocketSessionConfigImpl.isSetTrafficClassAvailable())
            {
                try
                {
                    socket.setTrafficClass(tc);
                }
                catch (SocketException e)
                {
                    throw new RuntimeIOException(e);
                }
            }
        }

        public int getSendBufferSize()
        {
            try
            {
                return socket.getSendBufferSize();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setSendBufferSize(int size)
        {
            if (SocketSessionConfigImpl.isSetSendBufferSizeAvailable())
            {
                try
                {
                    socket.setSendBufferSize(size);
                }
                catch (SocketException e)
                {
                    throw new RuntimeIOException(e);
                }
            }
        }

        public int getReceiveBufferSize()
        {
            try
            {
                return socket.getReceiveBufferSize();
            }
            catch (SocketException e)
            {
                throw new RuntimeIOException(e);
            }
        }

        public void setReceiveBufferSize(int size)
        {
            if (SocketSessionConfigImpl.isSetReceiveBufferSizeAvailable())
            {
                try
                {
                    socket.setReceiveBufferSize(size);
                    SocketSessionImpl.this.readBufferSize = size;
                }
                catch (SocketException e)
                {
                    throw new RuntimeIOException(e);
                }
            }
        }
    }
}
