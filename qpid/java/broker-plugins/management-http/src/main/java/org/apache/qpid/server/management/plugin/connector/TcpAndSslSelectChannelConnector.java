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
package org.apache.qpid.server.management.plugin.connector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

import org.eclipse.jetty.http.HttpSchemes;
import org.eclipse.jetty.io.AsyncEndPoint;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.nio.AsyncConnection;
import org.eclipse.jetty.io.nio.IndirectNIOBuffer;
import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.io.nio.SelectorManager;
import org.eclipse.jetty.io.nio.SslConnection;
import org.eclipse.jetty.server.AsyncHttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslCertificates;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class TcpAndSslSelectChannelConnector extends SelectChannelConnector
{

    private static final Logger LOG = Log.getLogger(TcpAndSslSelectChannelConnector.class);

    private final SslContextFactory _sslContextFactory;

    public TcpAndSslSelectChannelConnector(SslContextFactory factory)
    {
        _sslContextFactory = factory;
        addBean(_sslContextFactory);
        setUseDirectBuffers(false);
        setSoLingerTime(30000);
    }


    @Override
    public void customize(EndPoint endpoint, Request request) throws IOException
    {
        if(endpoint instanceof SslConnection.SslEndPoint)
        {
            request.setScheme(HttpSchemes.HTTPS);
        }

        super.customize(endpoint,request);

        if(endpoint instanceof SslConnection.SslEndPoint)
        {
            SslConnection.SslEndPoint sslEndpoint = (SslConnection.SslEndPoint) endpoint;
            SSLEngine sslEngine = sslEndpoint.getSslEngine();
            SSLSession sslSession = sslEngine.getSession();

            SslCertificates.customize(sslSession, endpoint, request);
        }
    }

    @Override
    protected AsyncConnection newConnection(SocketChannel channel, AsyncEndPoint endpoint)
    {
        return new ProtocolIdentifyingConnection((ProtocolIdentifyingEndpoint) endpoint);
    }

    @Override
    protected SelectChannelEndPoint newEndPoint(final SocketChannel channel,
                                                final SelectorManager.SelectSet selectSet,
                                                final SelectionKey key) throws IOException
    {

        ProtocolIdentifyingEndpoint endpoint = new ProtocolIdentifyingEndpoint(channel,selectSet,key, getMaxIdleTime());
        endpoint.setConnection(selectSet.getManager().newConnection(channel, endpoint, key.attachment()));
        return endpoint;
    }

    protected SSLEngine createSSLEngine(SocketChannel channel) throws IOException
    {
        SSLEngine engine;
        if (channel != null)
        {
            String peerHost = channel.socket().getInetAddress().getHostAddress();
            int peerPort = channel.socket().getPort();
            engine = _sslContextFactory.newSslEngine(peerHost, peerPort);
        }
        else
        {
            engine = _sslContextFactory.newSslEngine();
        }

        engine.setUseClientMode(false);
        return engine;
    }

    @Override
    protected void doStart() throws Exception
    {
        _sslContextFactory.checkKeyStore();
        _sslContextFactory.start();

        SSLEngine sslEngine = _sslContextFactory.newSslEngine();

        sslEngine.setUseClientMode(false);

        SSLSession sslSession = sslEngine.getSession();

        if (getRequestHeaderSize()<sslSession.getApplicationBufferSize())
            setRequestHeaderSize(sslSession.getApplicationBufferSize());
        if (getRequestBufferSize()<sslSession.getApplicationBufferSize())
            setRequestBufferSize(sslSession.getApplicationBufferSize());

        super.doStart();
    }

    @Override
    public boolean isConfidential(final Request request)
    {
        if(request.getScheme().equals(HttpSchemes.HTTPS))
        {
            final int confidentialPort=getConfidentialPort();
            return confidentialPort==0||confidentialPort==request.getServerPort();
        }
        return super.isConfidential(request);
    }

    enum Protocol { UNKNOWN, TCP , SSL }

    private class ProtocolIdentifyingEndpoint extends SelectChannelEndPoint
    {

        private Protocol _protocol = Protocol.UNKNOWN;
        private Buffer _preBuffer = new IndirectNIOBuffer(6);

        public ProtocolIdentifyingEndpoint(final SocketChannel channel,
                                           final SelectorManager.SelectSet selectSet,
                                           final SelectionKey key, final int maxIdleTime) throws IOException
        {
            super(channel, selectSet, key, maxIdleTime);
        }

        public Protocol getProtocol() throws IOException
        {
            if(_protocol == Protocol.UNKNOWN)
            {
                if(_preBuffer.space() != 0)
                {
                    super.fill(_preBuffer);
                    _protocol = identifyFromPreBuffer();
                }
            }
            return _protocol;
        }

        public SocketChannel getSocketChannel()
        {
            return (SocketChannel) getChannel();
        }

        private Protocol identifyFromPreBuffer()
        {
            if(_preBuffer.space() == 0)
            {
                byte[] helloBytes = _preBuffer.array();
                if (looksLikeSSLv2ClientHello(helloBytes) || looksLikeSSLv3ClientHello(helloBytes))
                {
                    return Protocol.SSL;
                }
                else
                {
                    return Protocol.TCP;
                }
            }
            return Protocol.UNKNOWN;
        }

        private boolean looksLikeSSLv3ClientHello(byte[] headerBytes)
        {
            return headerBytes[0] == 22 && // SSL Handshake
                   (headerBytes[1] == 3 && // SSL 3.0 / TLS 1.x
                    (headerBytes[2] == 0 || // SSL 3.0
                     headerBytes[2] == 1 || // TLS 1.0
                     headerBytes[2] == 2 || // TLS 1.1
                     headerBytes[2] == 3)) && // TLS1.2
                   (headerBytes[5] == 1); // client_hello
        }

        private boolean looksLikeSSLv2ClientHello(byte[] headerBytes)
        {
            return headerBytes[0] == -128 &&
                   headerBytes[3] == 3 && // SSL 3.0 / TLS 1.x
                   (headerBytes[4] == 0 || // SSL 3.0
                    headerBytes[4] == 1 || // TLS 1.0
                    headerBytes[4] == 2 || // TLS 1.1
                    headerBytes[4] == 3);
        }

        @Override
        public int fill(final Buffer buffer) throws IOException
        {
            int size = 0;

            if(getProtocol() != Protocol.UNKNOWN)
            {
                if (_preBuffer.hasContent())
                {
                    size = buffer.put(_preBuffer);
                    _preBuffer.skip(size);
                }
                if (buffer.space() != 0)
                {
                    size += super.fill(buffer);
                }
            }
            return size;
        }
    }

    private class ProtocolIdentifyingConnection implements AsyncConnection
    {
        private final ProtocolIdentifyingEndpoint _endpoint;
        private AsyncConnection _delegate;
        private final long _timestamp;
        private IOException _exception;

        private ProtocolIdentifyingConnection(final ProtocolIdentifyingEndpoint endpoint)
        {
            _endpoint = endpoint;
            _timestamp = System.currentTimeMillis();
        }

        @Override
        public void onInputShutdown() throws IOException
        {
            if (_delegate == null)
            {
                createDelegate(true);
            }
            _delegate.onInputShutdown();
        }

        private boolean createDelegate(boolean createPlainWhenUnknown) throws IOException
        {
            if(_exception != null)
            {
                throw _exception;
            }
            Protocol protocol = _endpoint.getProtocol();
            if(protocol == Protocol.TCP || (createPlainWhenUnknown && protocol == Protocol.UNKNOWN))
            {
                _delegate = new AsyncHttpConnection(TcpAndSslSelectChannelConnector.this, _endpoint, getServer());
                return true;
            }
            else if(protocol == Protocol.SSL)
            {
                SocketChannel channel = _endpoint.getSocketChannel();
                SSLEngine engine = createSSLEngine(channel);
                SslConnection connection = new SslConnection(engine, _endpoint);
                AsyncConnection delegate = new AsyncHttpConnection(TcpAndSslSelectChannelConnector.this,
                                                                   connection.getSslEndPoint(),
                                                                   getServer());
                connection.getSslEndPoint().setConnection(delegate);
                connection.setAllowRenegotiate(_sslContextFactory.isAllowRenegotiate());

                _delegate = connection;
                return true;
            }
            return false;
        }

        private boolean createDelegateNoException()
        {
            try
            {
                return createDelegate(false);
            }
            catch (IOException e)
            {
                _exception = e;
                return false;
            }
        }

        @Override
        public Connection handle() throws IOException
        {
            if(_delegate != null || createDelegate(false))
            {
                return _delegate.handle();
            }
            return this;
        }

        @Override
        public long getTimeStamp()
        {
            return _timestamp;
        }

        @Override
        public boolean isIdle()
        {
            if(_delegate != null || createDelegateNoException())
            {
                return _delegate.isIdle();
            }
            return false;
        }

        @Override
        public boolean isSuspended()
        {
            if(_delegate != null || createDelegateNoException())
            {
                return _delegate.isSuspended();
            }
            return false;
        }

        @Override
        public void onClose()
        {
            if(_delegate != null)
            {
                _delegate.onClose();
            }
        }

        @Override
        public void onIdleExpired(final long idleForMs)
        {
            try
            {
                if(_delegate != null || createDelegate(true))
                {
                    _delegate.onIdleExpired(idleForMs);
                }
            }
            catch (IOException e)
            {
                LOG.ignore(e);

                try
                {
                    _endpoint.close();
                }
                catch(IOException e2)
                {
                    LOG.ignore(e2);
                }
            }
        }
    }

}
