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
package org.apache.qpid.server.protocol;


import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.log4j.Logger;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.transport.ServerConnection;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.security.SSLStatus;
import org.apache.qpid.transport.network.security.ssl.SSLBufferingSender;
import org.apache.qpid.transport.network.security.ssl.SSLReceiver;

public class MultiVersionProtocolEngine implements ServerProtocolEngine
{
    private static final Logger _logger = Logger.getLogger(MultiVersionProtocolEngine.class);

    private final long _id;
    private final SSLContext _sslContext;
    private final boolean _wantClientAuth;
    private final boolean _needClientAuth;
    private final Port _port;
    private final Transport _transport;

    private Set<AmqpProtocolVersion> _supported;
    private String _fqdn;
    private final Broker _broker;
    private NetworkConnection _network;
    private Sender<ByteBuffer> _sender;
    private final AmqpProtocolVersion _defaultSupportedReply;

    private volatile ServerProtocolEngine _delegate = new SelfDelegateProtocolEngine();

    public MultiVersionProtocolEngine(final Broker broker,
                                      SSLContext sslContext, boolean wantClientAuth, boolean needClientAuth,
                                      final Set<AmqpProtocolVersion> supported,
                                      final AmqpProtocolVersion defaultSupportedReply,
                                      Port port, Transport transport, final long id)
    {
        if(defaultSupportedReply != null && !supported.contains(defaultSupportedReply))
        {
            throw new IllegalArgumentException("The configured default reply (" + defaultSupportedReply
                                             + ") to an unsupported protocol version initiation is itself not supported!");
        }

        _id = id;
        _broker = broker;
        _supported = supported;
        _defaultSupportedReply = defaultSupportedReply;
        _sslContext = sslContext;
        _wantClientAuth = wantClientAuth;
        _needClientAuth = needClientAuth;
        _port = port;
        _transport = transport;
    }


    public SocketAddress getRemoteAddress()
    {
        return _delegate.getRemoteAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _delegate.getLocalAddress();
    }

    public long getWrittenBytes()
    {
        return _delegate.getWrittenBytes();
    }

    public long getReadBytes()
    {
        return _delegate.getReadBytes();
    }

    public void closed()
    {
        _delegate.closed();
    }

    public void writerIdle()
    {
        _delegate.writerIdle();
    }

    public void readerIdle()
    {
        _delegate.readerIdle();
    }


    public void received(ByteBuffer msg)
    {
        _delegate.received(msg);
    }

    public void exception(Throwable t)
    {
        _delegate.exception(t);
    }

    public long getConnectionId()
    {
        return _delegate.getConnectionId();
    }

    private static final int MINIMUM_REQUIRED_HEADER_BYTES = 8;

    private static final byte[] AMQP_0_8_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 1,
                         (byte) 1,
                         (byte) 8,
                         (byte) 0
            };

    private static final byte[] AMQP_0_9_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 1,
                         (byte) 1,
                         (byte) 0,
                         (byte) 9
            };

    private static final byte[] AMQP_0_9_1_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 0,
                         (byte) 0,
                         (byte) 9,
                         (byte) 1
            };


    private static final byte[] AMQP_0_10_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 1,
                         (byte) 1,
                         (byte) 0,
                         (byte) 10
            };

    private static final byte[] AMQP_1_0_0_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 0,
                         (byte) 1,
                         (byte) 0,
                         (byte) 0
            };

    private static final byte[] AMQP_SASL_1_0_0_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 3,
                         (byte) 1,
                         (byte) 0,
                         (byte) 0
            };

    public void setNetworkConnection(NetworkConnection networkConnection)
    {
        setNetworkConnection(networkConnection, networkConnection.getSender());
    }

    public void setNetworkConnection(NetworkConnection network, Sender<ByteBuffer> sender)
    {
        _network = network;
        SocketAddress address = _network.getLocalAddress();
        if (address instanceof InetSocketAddress)
        {
            _fqdn = ((InetSocketAddress) address).getHostName();
        }
        else
        {
            throw new IllegalArgumentException("Unsupported socket address class: " + address);
        }
        _sender = sender;
    }

    @Override
    public long getLastReadTime()
    {
        return _delegate.getLastReadTime();
    }

    @Override
    public long getLastWriteTime()
    {
        return _delegate.getLastWriteTime();
    }


    private static interface DelegateCreator
    {
        AmqpProtocolVersion getVersion();
        byte[] getHeaderIdentifier();
        ServerProtocolEngine getProtocolEngine();
    }

    private DelegateCreator creator_0_8 = new DelegateCreator()
    {

        public AmqpProtocolVersion getVersion()
        {
            return AmqpProtocolVersion.v0_8;
        }

        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_8_HEADER;
        }

        public ServerProtocolEngine getProtocolEngine()
        {
            return new AMQProtocolEngine(_broker, _network, _id, _port, _transport);
        }
    };

    private DelegateCreator creator_0_9 = new DelegateCreator()
    {

        public AmqpProtocolVersion getVersion()
        {
            return AmqpProtocolVersion.v0_9;
        }


        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_9_HEADER;
        }

        public ServerProtocolEngine getProtocolEngine()
        {
            return new AMQProtocolEngine(_broker, _network, _id, _port, _transport);
        }
    };

    private DelegateCreator creator_0_9_1 = new DelegateCreator()
    {

        public AmqpProtocolVersion getVersion()
        {
            return AmqpProtocolVersion.v0_9_1;
        }


        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_9_1_HEADER;
        }

        public ServerProtocolEngine getProtocolEngine()
        {
            return new AMQProtocolEngine(_broker, _network, _id, _port, _transport);
        }
    };


    private DelegateCreator creator_0_10 = new DelegateCreator()
    {

        public AmqpProtocolVersion getVersion()
        {
            return AmqpProtocolVersion.v0_10;
        }


        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_10_HEADER;
        }

        public ServerProtocolEngine getProtocolEngine()
        {
            final ConnectionDelegate connDelegate = new org.apache.qpid.server.transport.ServerConnectionDelegate(_broker,
                    _fqdn, _broker.getSubjectCreator(getLocalAddress()));

            ServerConnection conn = new ServerConnection(_id);

            conn.setConnectionDelegate(connDelegate);
            conn.setRemoteAddress(_network.getRemoteAddress());
            conn.setLocalAddress(_network.getLocalAddress());
            return new ProtocolEngine_0_10( conn, _network, _port, _transport);
        }
    };

    private DelegateCreator creator_1_0_0 = new DelegateCreator()
    {

        public AmqpProtocolVersion getVersion()
        {
            return AmqpProtocolVersion.v1_0_0;
        }


        public byte[] getHeaderIdentifier()
        {
            return AMQP_1_0_0_HEADER;
        }

        public ServerProtocolEngine getProtocolEngine()
        {
            return new ProtocolEngine_1_0_0(_network, _broker, _id, _port, _transport);
        }
    };

    private DelegateCreator creator_1_0_0_SASL = new DelegateCreator()
    {

        public AmqpProtocolVersion getVersion()
        {
            return AmqpProtocolVersion.v1_0_0;
        }


        public byte[] getHeaderIdentifier()
        {
            return AMQP_SASL_1_0_0_HEADER;
        }

        public ServerProtocolEngine getProtocolEngine()
        {
            return new ProtocolEngine_1_0_0_SASL(_network, _broker, _id, _port, _transport);
        }
    };

    private final DelegateCreator[] _creators =
            new DelegateCreator[] { creator_0_8, creator_0_9, creator_0_9_1, creator_0_10, creator_1_0_0_SASL, creator_1_0_0 };


    private class ClosedDelegateProtocolEngine implements ServerProtocolEngine
    {
        public SocketAddress getRemoteAddress()
        {
            return _network.getRemoteAddress();
        }

        public SocketAddress getLocalAddress()
        {
            return _network.getLocalAddress();
        }

        public long getWrittenBytes()
        {
            return 0;
        }

        public long getReadBytes()
        {
            return 0;
        }

        public void received(ByteBuffer msg)
        {
            _logger.error("Error processing incoming data, could not negotiate a common protocol");
        }

        public void exception(Throwable t)
        {
            _logger.error("Error establishing session", t);
        }

        public void closed()
        {

        }

        public void writerIdle()
        {

        }

        public void readerIdle()
        {

        }

        public void setNetworkConnection(NetworkConnection network, Sender<ByteBuffer> sender)
        {

        }

        @Override
        public long getLastReadTime()
        {
            return 0;
        }

        @Override
        public long getLastWriteTime()
        {
            return 0;
        }

        public long getConnectionId()
        {
            return _id;
        }
    }

    private class SelfDelegateProtocolEngine implements ServerProtocolEngine
    {
        private final ByteBuffer _header = ByteBuffer.allocate(MINIMUM_REQUIRED_HEADER_BYTES);
        private long _lastReadTime;

        public SocketAddress getRemoteAddress()
        {
            return _network.getRemoteAddress();
        }

        public SocketAddress getLocalAddress()
        {
            return _network.getLocalAddress();
        }

        public long getWrittenBytes()
        {
            return 0;
        }

        public long getReadBytes()
        {
            return 0;
        }

        public void received(ByteBuffer msg)
        {

            _lastReadTime = System.currentTimeMillis();
            ByteBuffer msgheader = msg.duplicate();
            if(_header.remaining() > msgheader.limit())
            {
                msg.position(msg.limit());
            }
            else
            {
                msgheader.limit(_header.remaining());
                msg.position(_header.remaining());
            }

            _header.put(msgheader);

            if(!_header.hasRemaining())
            {
                _header.flip();
                byte[] headerBytes = new byte[MINIMUM_REQUIRED_HEADER_BYTES];
                _header.get(headerBytes);


                ServerProtocolEngine newDelegate = null;
                byte[] supportedReplyBytes = null;
                byte[] defaultSupportedReplyBytes = null;
                AmqpProtocolVersion supportedReplyVersion = null;

                //Check the supported versions for a header match, and if there is one save the
                //delegate. Also save most recent supported version and associated reply header bytes
                for(int i = 0; newDelegate == null && i < _creators.length; i++)
                {
                    if(_supported.contains(_creators[i].getVersion()))
                    {
                        supportedReplyBytes = _creators[i].getHeaderIdentifier();
                        supportedReplyVersion = _creators[i].getVersion();
                        byte[] compareBytes = _creators[i].getHeaderIdentifier();
                        boolean equal = true;
                        for(int j = 0; equal && j<compareBytes.length; j++)
                        {
                            equal = headerBytes[j] == compareBytes[j];
                        }
                        if(equal)
                        {
                            newDelegate = _creators[i].getProtocolEngine();
                        }
                    }

                    //If there is a configured default reply to an unsupported version initiation,
                    //then save the associated reply header bytes when we encounter them
                    if(_defaultSupportedReply != null && _creators[i].getVersion() == _defaultSupportedReply)
                    {
                        defaultSupportedReplyBytes = _creators[i].getHeaderIdentifier();
                    }
                }

                if(newDelegate == null && looksLikeSSL(headerBytes))
                {
                    if(_sslContext !=  null)
                    {
                        newDelegate = new SslDelegateProtocolEngine();
                    }
                }

                // If no delegate is found then send back a supported protocol version id
                if(newDelegate == null)
                {
                    //if a default reply was specified use its reply header instead of the most recent supported version
                    if(_defaultSupportedReply != null && !(_defaultSupportedReply == supportedReplyVersion))
                    {
                        if(_logger.isDebugEnabled())
                        {
                            _logger.debug("Default reply to unsupported protocol version was configured, changing reply from "
                                    + supportedReplyVersion + " to " + _defaultSupportedReply);
                        }

                        supportedReplyBytes = defaultSupportedReplyBytes;
                        supportedReplyVersion = _defaultSupportedReply;
                    }
                    if(_logger.isDebugEnabled())
                    {
                        _logger.debug("Unsupported protocol version requested, replying with: " + supportedReplyVersion);
                    }
                    _sender.send(ByteBuffer.wrap(supportedReplyBytes));
                    _sender.flush();

                    _delegate = new ClosedDelegateProtocolEngine();

                    _network.close();

                }
                else
                {
                    _delegate = newDelegate;

                    _header.flip();
                    _delegate.received(_header);
                    if(msg.hasRemaining())
                    {
                        _delegate.received(msg);
                    }
                }

            }

        }

        public long getConnectionId()
        {
            return _id;
        }

        public void exception(Throwable t)
        {
            _logger.error("Error establishing session", t);
        }

        public void closed()
        {
            try
            {
                _delegate = new ClosedDelegateProtocolEngine();
                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Connection from  " + getRemoteAddress() + " was closed before any protocol version was established.");
                }
            }
            catch(Exception e)
            {
                //ignore
            }
            finally
            {
                try
                {
                    _network.close();
                }
                catch(Exception e)
                {
                    //ignore
                }
            }
        }

        public void writerIdle()
        {

        }

        public void readerIdle()
        {
            CurrentActor.get().message(ConnectionMessages.IDLE_CLOSE());
            _network.close();
        }

        public void setNetworkConnection(NetworkConnection network, Sender<ByteBuffer> sender)
        {

        }

        @Override
        public long getLastReadTime()
        {
            return _lastReadTime;
        }

        @Override
        public long getLastWriteTime()
        {
            return 0;
        }
    }

    private class SslDelegateProtocolEngine implements ServerProtocolEngine
    {
        private final MultiVersionProtocolEngine _decryptEngine;
        private final SSLEngine _engine;
        private final SSLReceiver _sslReceiver;
        private final SSLBufferingSender _sslSender;
        private long _lastReadTime;

        private SslDelegateProtocolEngine()
        {

            _decryptEngine = new MultiVersionProtocolEngine(_broker, null, false, false, _supported,
                                                            _defaultSupportedReply, _port, Transport.SSL, _id);

            _engine = _sslContext.createSSLEngine();
            _engine.setUseClientMode(false);

            if(_needClientAuth)
            {
                _engine.setNeedClientAuth(_needClientAuth);
            }
            else if(_wantClientAuth)
            {
                _engine.setWantClientAuth(_wantClientAuth);
            }

            SSLStatus sslStatus = new SSLStatus();
            _sslReceiver = new SSLReceiver(_engine,_decryptEngine,sslStatus);
            _sslSender = new SSLBufferingSender(_engine,_sender,sslStatus);
            _decryptEngine.setNetworkConnection(new SSLNetworkConnection(_engine,_network, _sslSender));
        }

        @Override
        public void received(ByteBuffer msg)
        {
            _lastReadTime = System.currentTimeMillis();
            _sslReceiver.received(msg);
            _sslSender.send();
            _sslSender.flush();
        }

        @Override
        public void setNetworkConnection(NetworkConnection network, Sender<ByteBuffer> sender)
        {
            //TODO - Implement
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return _decryptEngine.getRemoteAddress();
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return _decryptEngine.getLocalAddress();
        }

        @Override
        public long getWrittenBytes()
        {
            return _decryptEngine.getWrittenBytes();
        }

        @Override
        public long getReadBytes()
        {
            return _decryptEngine.getReadBytes();
        }

        @Override
        public void closed()
        {
            _decryptEngine.closed();
        }

        @Override
        public void writerIdle()
        {
            _decryptEngine.writerIdle();
        }

        @Override
        public void readerIdle()
        {
            _decryptEngine.readerIdle();
        }

        @Override
        public void exception(Throwable t)
        {
            _decryptEngine.exception(t);
        }

        @Override
        public long getConnectionId()
        {
            return _decryptEngine.getConnectionId();
        }

        @Override
        public long getLastReadTime()
        {
            return _lastReadTime;
        }

        @Override
        public long getLastWriteTime()
        {
            return _decryptEngine.getLastWriteTime();
        }
    }

    private boolean looksLikeSSL(byte[] headerBytes)
    {
        return looksLikeSSLv3ClientHello(headerBytes) || looksLikeSSLv2ClientHello(headerBytes);
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


    private static class SSLNetworkConnection implements NetworkConnection
    {
        private final NetworkConnection _network;
        private final SSLBufferingSender _sslSender;
        private final SSLEngine _engine;

        public SSLNetworkConnection(SSLEngine engine, NetworkConnection network,
                                    SSLBufferingSender sslSender)
        {
            _engine = engine;
            _network = network;
            _sslSender = sslSender;

        }

        @Override
        public Sender<ByteBuffer> getSender()
        {
            return _sslSender;
        }

        @Override
        public void start()
        {
            _network.start();
        }

        @Override
        public void close()
        {
            _sslSender.close();

            _network.close();
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return _network.getRemoteAddress();
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return _network.getLocalAddress();
        }

        @Override
        public void setMaxWriteIdle(int sec)
        {
            _network.setMaxWriteIdle(sec);
        }

        @Override
        public void setMaxReadIdle(int sec)
        {
            _network.setMaxReadIdle(sec);
        }

        @Override
        public void setPeerPrincipal(Principal principal)
        {
            _network.setPeerPrincipal(principal);
        }

        @Override
        public Principal getPeerPrincipal()
        {
            try
            {
                return _engine.getSession().getPeerPrincipal();
            }
            catch (SSLPeerUnverifiedException e)
            {
                return null;
            }
        }

        @Override
        public int getMaxReadIdle()
        {
            return _network.getMaxReadIdle();
        }

        @Override
        public int getMaxWriteIdle()
        {
            return _network.getMaxWriteIdle();
        }
    }
}
