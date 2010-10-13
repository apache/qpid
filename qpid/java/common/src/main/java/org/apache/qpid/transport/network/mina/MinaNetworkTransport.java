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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.PooledByteBufferAllocator;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;
import org.apache.mina.transport.socket.nio.DatagramAcceptorConfig;
import org.apache.mina.transport.socket.nio.DatagramConnector;
import org.apache.mina.transport.socket.nio.DatagramSessionConfig;
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
import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.vm.VmBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinaNetworkTransport implements IncomingNetworkTransport, OutgoingNetworkTransport
{
    private static final Logger _log = LoggerFactory.getLogger(MinaNetworkTransport.class);
    
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;
    
    public static final List<String> SUPPORTED = Arrays.asList(Transport.SOCKET, Transport.TCP, Transport.UDP, Transport.VM);
    
    private int _processors;
    private Executor _executor;
    private ConnectionSettings _settings;    
    private SocketAddress _address;
    private IoConnector _connector;
    private IoSession _session;
    private IoAcceptor _acceptor;
    private Receiver<ByteBuffer> _receiver;
    
    public MinaNetworkTransport()
    {
        org.apache.mina.common.ByteBuffer.setUseDirectBuffers(Boolean.getBoolean("amqj.enableDirectBuffers"));

        // the default is to use the simple allocator
//        if (Boolean.getBoolean("amqj.enablePooledAllocator"))
//        {
            org.apache.mina.common.ByteBuffer.setAllocator(new PooledByteBufferAllocator());
//        }
//        else
//        {
//            org.apache.mina.common.ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
//        }
        
        _processors = Integer.parseInt(System.getProperty("amqj.processors", "4"));
        _executor = Executors.newCachedThreadPool(Threading.getThreadFactory());
    }
    
    public NetworkConnection connect(ConnectionSettings settings, Receiver<ByteBuffer> delegate, SSLContextFactory sslFactory)
    {
        _log.debug("Initialising MINA transport");
        
        _settings = settings;
        _receiver = delegate;
        
        if (_settings.getProtocol().equalsIgnoreCase(Transport.TCP))
        {
	        _address = new InetSocketAddress(_settings.getHost(), _settings.getPort());
	        _connector = new SocketConnector(_processors, _executor); // non-blocking connector
        }
        else if (_settings.getProtocol().equalsIgnoreCase(Transport.UDP))
        {
            _address = new InetSocketAddress(_settings.getHost(), _settings.getPort());
            _connector = new DatagramConnector(_executor);
        }
        else if (_settings.getProtocol().equalsIgnoreCase(Transport.VM))
        {
            if (Boolean.getBoolean("amqj.autoCreate"))
            {
                VmBroker.createVMBroker();
            }
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
            _connector = new ExistingSocketConnector(_processors, _executor);
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
            if (elt.getClassName().contains("Test"))
            {
                s += elt.getClassName();
                break;
            }
        }
        
        IoServiceConfig cfg = _connector.getDefaultConfig();
        cfg.setThreadModel(ExecutorThreadModel.getInstance("MINANetworkTransport(Client)" + s));

        // Socket based connection configuration only (TCP/SOCKET)
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
        
        return new MinaNetworkConnection(_session);
    }
    
    public void accept(ConnectionSettings settings, ReceiverFactory factory, SSLContextFactory sslFactory)
    {
        if (settings.getProtocol().equalsIgnoreCase(Transport.TCP))
        {
            _acceptor = new SocketAcceptor(_processors, _executor);
    
            SocketAcceptorConfig sconfig = (SocketAcceptorConfig) _acceptor.getDefaultConfig();
            SocketSessionConfig ssc = (SocketSessionConfig) sconfig.getSessionConfig();
            ssc.setReuseAddress(true);
            ssc.setKeepAlive(Boolean.getBoolean("amqj.keepAlive"));
            ssc.setTcpNoDelay(Boolean.getBoolean("amqj.tcpNoDelay"));
            Integer sendBufferSize = Integer.getInteger("amqj.sendBufferSize", DEFAULT_BUFFER_SIZE);
            Integer receiveBufferSize = Integer.getInteger("amqj.receiveBufferSize", DEFAULT_BUFFER_SIZE);
            ssc.setSendBufferSize(sendBufferSize);
            ssc.setReceiveBufferSize(receiveBufferSize);
            
            if (settings.getHost().equals("*"))
            {
	            _address = new InetSocketAddress(settings.getPort());
            }
            else
            {
	            _address = new InetSocketAddress(settings.getHost(), settings.getPort());
            }
        }
        else if (settings.getProtocol().equalsIgnoreCase(Transport.UDP))
        {
            _acceptor = new DatagramAcceptor(_executor);
    
            DatagramAcceptorConfig dconfig = (DatagramAcceptorConfig) _acceptor.getDefaultConfig();
            DatagramSessionConfig dsc = (DatagramSessionConfig) dconfig.getSessionConfig();
            dsc.setReuseAddress(true);
            Integer sendBufferSize = Integer.getInteger("amqj.sendBufferSize", DEFAULT_BUFFER_SIZE);
            Integer receiveBufferSize = Integer.getInteger("amqj.receiveBufferSize", DEFAULT_BUFFER_SIZE);
            dsc.setSendBufferSize(sendBufferSize);
            dsc.setReceiveBufferSize(receiveBufferSize);
            
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

        IoServiceConfig cfg = _acceptor.getDefaultConfig();
        cfg.setThreadModel(ExecutorThreadModel.getInstance("MINANetworkTransport(Broker)"));

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
        if (_acceptor != null)
        {
	        _acceptor.unbind(_address);
        }
        if (_receiver != null)
        {
            _receiver.closed();
        }
        if (_session != null && _session.isConnected())
        {
            _session.close();
        }
    }

    public boolean isCompatible(String protocol) {
        return SUPPORTED.contains(protocol);
    }
}
