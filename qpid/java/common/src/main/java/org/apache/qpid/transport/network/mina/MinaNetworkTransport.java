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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import org.apache.mina.common.CloseFuture;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.transport.socket.nio.ExistingSocketConnector;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;
import org.apache.qpid.protocol.ReceiverFactory;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.network.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.emory.mathcs.backport.java.util.concurrent.ExecutorService;
import edu.emory.mathcs.backport.java.util.concurrent.Executors;

public class MinaNetworkTransport implements IncomingNetworkTransport, OutgoingNetworkTransport
{
    private static final Logger _log = LoggerFactory.getLogger(MinaNetworkTransport.class);
    
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
    
    private MinaNetworkConnection _connection;
    private ConnectionSettings _settings;    
    private SocketAddress _address;
    private IoConnector _connector;
    private IoSession _session;
    private IoAcceptor _acceptor;
    private Receiver<ByteBuffer> _receiver;
    private ExecutorService _executor;
    private int _processors = 4;
    
    static
    {
        org.apache.mina.common.ByteBuffer.setUseDirectBuffers(Boolean.getBoolean("amqj.enableDirectBuffers"));
        org.apache.mina.common.ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
    }
 
    public NetworkConnection connect(ConnectionSettings settings, Receiver<ByteBuffer> delegate, SSLContextFactory sslFactory)
    {
        _log.debug("Initialising MINA transport");
        
        _settings = settings;
        _receiver = delegate;
        _processors = Runtime.getRuntime().availableProcessors();
        
        if (_settings.getProtocol().equalsIgnoreCase(Transport.TCP))
        {
	        _address = new InetSocketAddress(_settings.getHost(), _settings.getPort());
	        _executor = Executors.newFixedThreadPool(_processors);
	        _connector = new SocketConnector(1, _executor);
        }
        else if (_settings.getProtocol().equalsIgnoreCase(Transport.VM))
        {
	        _address = new VmPipeAddress(_settings.getPort());
	        _connector = new VmPipeConnector();
        }
        else if (_settings.getProtocol().equalsIgnoreCase(Transport.SOCKET))
        {
            Socket socket = ExistingSocketConnector.removeOpenSocket(_settings.getHost());
            if (socket == null)
            {
                throw new IllegalArgumentException("Active Socket must be provided for broker " +
                                                    "with 'socket://<SocketID>' transport");
            }
	        _address = socket.getRemoteSocketAddress();
	        _executor = Executors.newFixedThreadPool(_processors);
            _connector = new ExistingSocketConnector(1, _executor);
            ((ExistingSocketConnector) _connector).setOpenSocket(socket);
        }
        else
        {
            throw new TransportException("Unknown protocol: " + _settings.getProtocol());
        }
        _log.info("Connecting to broker on: " + _address);

        String s = "-";
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (StackTraceElement elt : trace)
        {
            if (elt.getClassName().endsWith("Test"))
            {
                s = "-" + elt.getClassName();
            }
        }
        
        IoServiceConfig cfg = _connector.getDefaultConfig();
        cfg.setThreadModel(ExecutorThreadModel.getInstance("MINANetworkTransport(Client)" + s));

        // Socket based connection configuration only
        if (_connector instanceof SocketConnector)
        {
            SocketSessionConfig scfg = (SocketSessionConfig) cfg.getSessionConfig();
            scfg.setTcpNoDelay(Boolean.getBoolean("amqj.tcpNoDelay"));
            Integer sendBufferSize = Integer.getInteger("amqj.sendBufferSize", DEFAULT_BUFFER_SIZE);
            Integer receiveBufferSize = Integer.getInteger("amqj.receiveBufferSize", DEFAULT_BUFFER_SIZE);
            scfg.setSendBufferSize(sendBufferSize);
            scfg.setReceiveBufferSize(receiveBufferSize);

            // Don't have the connector's worker thread wait around for other connections (we only use
            // one SocketConnector per connection at the moment anyway). This allows short-running
            // clients (like unit tests) to complete quickly.
            ((SocketConnector) _connector).setWorkerTimeout(0);
        }

        // Connect to the broker
        ConnectFuture future = _connector.connect(_address, new MinaNetworkHandler(this, sslFactory), cfg);
        future.join();
        if (!future.isConnected())
        {
            throw new TransportException("Could not open connection");
        }
        _session = future.getSession();
        _session.setAttachment(_receiver);
        
        _connection = new MinaNetworkConnection(_session);
        return _connection;
    }
    
    public void accept(ConnectionSettings settings, ReceiverFactory factory, SSLContextFactory sslFactory)
    {
        _processors = Runtime.getRuntime().availableProcessors();
        
        if (settings.getProtocol().equalsIgnoreCase(Transport.TCP))
        {
	        _executor = Executors.newFixedThreadPool(_processors);
            _acceptor = new SocketAcceptor(_processors, _executor);
    
            SocketAcceptorConfig sconfig = (SocketAcceptorConfig) _acceptor.getDefaultConfig();
            sconfig.setThreadModel(ExecutorThreadModel.getInstance("MINANetworkTransport(Acceptor)"));
            SocketSessionConfig sc = (SocketSessionConfig) sconfig.getSessionConfig();
            sc.setTcpNoDelay(Boolean.getBoolean("amqj.tcpNoDelay"));
            Integer sendBufferSize = Integer.getInteger("amqj.sendBufferSize", DEFAULT_BUFFER_SIZE);
            Integer receiveBufferSize = Integer.getInteger("amqj.receiveBufferSize", DEFAULT_BUFFER_SIZE);
            sc.setSendBufferSize(sendBufferSize);
            sc.setReceiveBufferSize(receiveBufferSize);
            
            if (settings.getHost().equals("*"))
            {
	            _address = new InetSocketAddress(settings.getPort());
            }
            else
            {
	            _address = new InetSocketAddress(settings.getHost(), settings.getPort());
            }
        }
        else if (settings.getProtocol().equalsIgnoreCase(Transport.VM))
        {
             _acceptor = new VmPipeAcceptor();
             _address = new VmPipeAddress(settings.getPort());
        }
        else
        {
            throw new TransportException("Unknown protocol: " + settings.getProtocol());
        }

        try
        {
            _acceptor.bind(_address, new MinaNetworkHandler(this, sslFactory, factory));
        }
        catch (IOException e)
        {
            throw new TransportException("Could not bind to " + _address, e);
        }
    }

    public SocketAddress getAddress()
    {
        return _address;
    }
    
    public void close()
    {
        if (_connection != null)
        {
            _connection.close();
        }
        if (_acceptor != null)
        {
            _acceptor.unbindAll();
            _acceptor = null;
        }
        if (_session != null && _session.isConnected())
        {
            CloseFuture closed = _session.close();
            closed.join();
            _session = null;
        }
        if (_executor != null)
        {
            _executor.shutdownNow();
            _executor = null;
        }
    }

    public boolean isCompatible(String protocol)
    {
        return (protocol.equalsIgnoreCase(Transport.TCP) ||
                 protocol.equalsIgnoreCase(Transport.SOCKET) ||
                 protocol.equalsIgnoreCase(Transport.VM));
    }
}
