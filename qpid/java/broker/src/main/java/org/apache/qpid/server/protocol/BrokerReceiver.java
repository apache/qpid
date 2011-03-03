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

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.protocol.BrokerReceiverFactory.VERSION;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.transport.ServerConnection;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.NetworkTransport;

public class BrokerReceiver implements Receiver<java.nio.ByteBuffer>
{
    private static final Logger _logger = Logger.getLogger(BrokerReceiver.class);

    private static final AtomicLong _idGenerator = new AtomicLong(0);

    private NetworkConnection _network;
    private NetworkTransport _transport;
    private Sender<ByteBuffer> _sender;
    private Set<VERSION> _supported;
    private String _fqdn;
    private IApplicationRegistry _appRegistry;
    private long _conenctionId;

    private volatile Receiver<java.nio.ByteBuffer> _delegate = new SelfDelegateProtocolEngine();

    public BrokerReceiver(IApplicationRegistry appRegistry,
                                      String fqdn,
                                      Set<VERSION> supported,
                                      NetworkTransport transport,
                                      NetworkConnection network)
    {
        _appRegistry = appRegistry;
        _fqdn = fqdn;
        _supported = supported;
        _transport = transport;
        _network = network;
        _sender = _network.getSender();
        _conenctionId = _idGenerator.incrementAndGet();
        
        CurrentActor.get().message(ConnectionMessages.OPEN(null, null, false, false));
    }

    public void closed()
    {
        _delegate.closed();
        _network.close();
    }

    public void received(ByteBuffer msg)
    {
        _delegate.received(msg);
    }

    public void exception(Throwable t)
    {
        _delegate.exception(t);
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

    private static interface DelegateCreator
    {
        VERSION getVersion();
        byte[] getHeaderIdentifier();
        Receiver<java.nio.ByteBuffer> getProtocolEngine();
    }

    private DelegateCreator creator_0_8 = new DelegateCreator()
    {
        public VERSION getVersion()
        {
            return VERSION.v0_8;
        }

        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_8_HEADER;
        }

        public Receiver<java.nio.ByteBuffer> getProtocolEngine()
        {
            return new AMQProtocolEngine(_appRegistry.getVirtualHostRegistry(), _transport, _network, _sender, _conenctionId);
        }
    };

    private DelegateCreator creator_0_9 = new DelegateCreator()
    {
        public VERSION getVersion()
        {
            return VERSION.v0_9;
        }

        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_9_HEADER;
        }

        public Receiver<java.nio.ByteBuffer> getProtocolEngine()
        {
            return new AMQProtocolEngine(_appRegistry.getVirtualHostRegistry(), _transport, _network, _sender, _conenctionId);
        }
    };

    private DelegateCreator creator_0_9_1 = new DelegateCreator()
    {
        public VERSION getVersion()
        {
            return VERSION.v0_9_1;
        }

        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_9_1_HEADER;
        }

        public Receiver<java.nio.ByteBuffer> getProtocolEngine()
        {
            return new AMQProtocolEngine(_appRegistry.getVirtualHostRegistry(), _transport, _network, _sender, _conenctionId);
        }
    };

    private DelegateCreator creator_0_10 = new DelegateCreator()
    {
        public VERSION getVersion()
        {
            return VERSION.v0_10;
        }

        public byte[] getHeaderIdentifier()
        {
            return AMQP_0_10_HEADER;
        }

        public Receiver<java.nio.ByteBuffer> getProtocolEngine()
        {
            final ConnectionDelegate connDelegate = new org.apache.qpid.server.transport.ServerConnectionDelegate(_appRegistry, _fqdn);

            ServerConnection conn = new ServerConnection(_conenctionId);
            conn.setConnectionDelegate(connDelegate);

            return new ProtocolEngine_0_10(conn, _appRegistry, _network);
        }
    };

    private final DelegateCreator[] _creators = new DelegateCreator[] { creator_0_8, creator_0_9, creator_0_9_1, creator_0_10 };


    private class ClosedDelegateProtocolEngine implements Receiver<java.nio.ByteBuffer>
    {
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
    }

    private class SelfDelegateProtocolEngine implements Receiver<java.nio.ByteBuffer>
    {
        private final ByteBuffer _header = ByteBuffer.allocate(MINIMUM_REQUIRED_HEADER_BYTES);

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


                Receiver<java.nio.ByteBuffer> newDelegate = null;
                byte[] newestSupported = null;

                for(int i = 0; newDelegate == null && i < _creators.length; i++)
                {

                    if(_supported.contains(_creators[i].getVersion()))
                    {
                        newestSupported = _creators[i].getHeaderIdentifier();
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
                }

                // If no delegate is found then send back the most recent support protocol version id
                if(newDelegate == null)
                {
                    _sender.send(ByteBuffer.wrap(newestSupported));
                    _sender.close();
                    _delegate = new ClosedDelegateProtocolEngine();
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

        public void exception(Throwable t)
        {
            _logger.error("Error establishing session", t);
        }

        public void closed()
        {

        }
    }
}
