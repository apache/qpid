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
package org.apache.qpid.transport.network.mina;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.mina.util.NewThreadExecutor;
import org.apache.mina.transport.vmpipe.QpidVmPipeConnector;
import org.apache.mina.transport.vmpipe.VmPipeAddress;

import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.thread.QpidThreadExecutor;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.SocketConnectorFactory;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.network.VMBrokerMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinaNetworkTransport implements OutgoingNetworkTransport, IncomingNetworkTransport
{
    private static final int UNKNOWN = -1;
    private static final int TCP = 0;
    private static final int VM = 1;

    public NetworkConnection _connection;
    private SocketAcceptor _acceptor;
    private InetSocketAddress _address;

    public NetworkConnection connect(ConnectionSettings settings,
            Receiver<java.nio.ByteBuffer> delegate, SSLContextFactory sslFactory)
    {
        int transport = getTransport(settings.getProtocol());
        
        IoConnectorCreator stc;
        switch(transport)
        {
            case TCP:
                stc = new IoConnectorCreator(new SocketConnectorFactory()
                {
                    public IoConnector newConnector()
                    {
                        return new SocketConnector(1, new QpidThreadExecutor()); // non-blocking connector
                    }
                });
                _connection = stc.connect(delegate, settings, sslFactory);
                break;
            case VM:
                stc = new IoConnectorCreator(new SocketConnectorFactory()
                {
                    public IoConnector newConnector()
                    {
                        return new QpidVmPipeConnector();
                    }
                });
                _connection = stc.connect(delegate, settings, sslFactory);
                break;
            case UNKNOWN:
            default:
                    throw new TransportException("Unknown protocol: " + settings.getProtocol());
        }

        return _connection;
    }

    private static int getTransport(String transport)
    {
        if (transport.equals(Transport.TCP))
        {
            return TCP;
        }

        if (transport.equals(Transport.VM))
        {
            return VM;
        }

        return -1;
    }

    public void close()
    {
        if(_connection != null)
        {
            _connection.close();
        }
        if (_acceptor != null)
        {
            _acceptor.unbindAll();
        }
    }

    public NetworkConnection getConnection()
    {
        return _connection;
    }

    public void accept(final NetworkTransportConfiguration config, final ProtocolEngineFactory factory,
            final SSLContextFactory sslFactory)
    {
        int processors = config.getConnectorProcessors();
        
        if (Transport.TCP.equalsIgnoreCase(config.getTransport()))
        {
            _acceptor = new SocketAcceptor(processors, new NewThreadExecutor());
    
            SocketAcceptorConfig sconfig = (SocketAcceptorConfig) _acceptor.getDefaultConfig();
            sconfig.setThreadModel(ExecutorThreadModel.getInstance("MinaNetworkTransport(Acceptor)"));
            SocketSessionConfig sc = (SocketSessionConfig) sconfig.getSessionConfig();
            sc.setTcpNoDelay(config.getTcpNoDelay());
            sc.setSendBufferSize(config.getSendBufferSize());
            sc.setReceiveBufferSize(config.getReceiveBufferSize());

            if (config.getHost().equals(WILDCARD_ADDRESS))
            {
                _address = new InetSocketAddress(config.getPort());
            }
            else
            {
                _address = new InetSocketAddress(config.getHost(), config.getPort());
            }
        }
        else
        {
            throw new TransportException("Unknown transport: " + config.getTransport());
        }

        try
        {
            _acceptor.bind(_address, new MinaNetworkHandler(sslFactory, factory));
        }
        catch (IOException e)
        {
            throw new TransportException("Could not bind to " + _address, e);
        }
    }


    private static class IoConnectorCreator
    {
        private static final Logger LOGGER = LoggerFactory.getLogger(IoConnectorCreator.class);
        
        private static final int CLIENT_DEFAULT_BUFFER_SIZE = 32 * 1024;

        private SocketConnectorFactory _ioConnectorFactory;
        
        public IoConnectorCreator(SocketConnectorFactory socketConnectorFactory)
        {
            _ioConnectorFactory = socketConnectorFactory;
        }
        
        public NetworkConnection connect(Receiver<java.nio.ByteBuffer> receiver, ConnectionSettings settings, SSLContextFactory sslFactory)
        {
            final IoConnector ioConnector = _ioConnectorFactory.newConnector();
            final SocketAddress address;
            final String protocol = settings.getProtocol();
            final int port = settings.getPort();

            if (Transport.TCP.equalsIgnoreCase(protocol))
            {
                address = new InetSocketAddress(settings.getHost(), port);
            }
            else if(Transport.VM.equalsIgnoreCase(protocol))
            {
                synchronized (VMBrokerMap.class)
                {
                    if(!VMBrokerMap.contains(port))
                    {
                        throw new TransportException("VM broker on port " + port + " does not exist.");
                    }
                }

                address = new VmPipeAddress(port);
            }
            else
            {
                throw new TransportException("Unknown transport: " + protocol);
            }

            LOGGER.info("Attempting connection to " + address);

            if (ioConnector instanceof SocketConnector)
            {
                SocketConnectorConfig cfg = (SocketConnectorConfig) ioConnector.getDefaultConfig();
                cfg.setThreadModel(ExecutorThreadModel.getInstance("MinaNetworkTransport(Client)"));

                SocketSessionConfig scfg = (SocketSessionConfig) cfg.getSessionConfig();
                scfg.setTcpNoDelay(true);
                scfg.setSendBufferSize(CLIENT_DEFAULT_BUFFER_SIZE);
                scfg.setReceiveBufferSize(CLIENT_DEFAULT_BUFFER_SIZE);

                // Don't have the connector's worker thread wait around for other
                // connections (we only use one SocketConnector per connection
                // at the moment anyway). This allows short-running
                // clients (like unit tests) to complete quickly.
                ((SocketConnector) ioConnector).setWorkerTimeout(0);
            }

            ConnectFuture future = ioConnector.connect(address, new MinaNetworkHandler(sslFactory), ioConnector.getDefaultConfig());
            future.join();
            if (!future.isConnected())
            {
                throw new TransportException("Could not open connection");
            }

            IoSession session = future.getSession();
            session.setAttachment(receiver);

            return new MinaNetworkConnection(session);
        }
    }
}
