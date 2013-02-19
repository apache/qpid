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
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.transport.ServerConnection;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;

public class MultiVersionProtocolEngine implements ServerProtocolEngine
{
    private static final Logger _logger = Logger.getLogger(MultiVersionProtocolEngine.class);

    private final long _id;

    private Set<AmqpProtocolVersion> _supported;
    private String _fqdn;
    private final Broker _broker;
    private NetworkConnection _network;
    private Sender<ByteBuffer> _sender;
    private final AmqpProtocolVersion _defaultSupportedReply;

    private volatile ServerProtocolEngine _delegate = new SelfDelegateProtocolEngine();

    public MultiVersionProtocolEngine(final Broker broker,
                                      final Set<AmqpProtocolVersion> supported,
                                      final AmqpProtocolVersion defaultSupportedReply,
                                      final long id,
                                      final NetworkConnection network)
    {
        this(broker, supported, defaultSupportedReply, id);
        setNetworkConnection(network);
    }

    public MultiVersionProtocolEngine(final Broker broker,
                                      final Set<AmqpProtocolVersion> supported,
                                      final AmqpProtocolVersion defaultSupportedReply,
                                      final long id)
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
            return new AMQProtocolEngine(_broker, _network, _id);
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
            return new AMQProtocolEngine(_broker, _network, _id);
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
            return new AMQProtocolEngine(_broker, _network, _id);
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
            return new ProtocolEngine_0_10( conn, _network);
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
            return new ProtocolEngine_1_0_0(_network, _broker, _id);
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
            return new ProtocolEngine_1_0_0_SASL(_network, _broker, _id);
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
    }
}
