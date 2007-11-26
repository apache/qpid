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
package org.apache.qpid.server.cluster;

import org.apache.log4j.Logger;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.RuntimeIOException;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ConnectionRedirectBody;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.framing.ProtocolVersion;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * A 'client stub' for a remote cluster peer, using MINA for IO Layer
 *
 */
public class MinaBrokerProxy extends Broker implements MethodHandler
{
    private static final Logger _logger = Logger.getLogger(MinaBrokerProxy.class);
    private final ConnectionStatusMonitor _connectionMonitor = new ConnectionStatusMonitor();
    private final ClientHandlerRegistry _legacyHandler;
    private final MinaBinding _binding = new MinaBinding();
    private final MemberHandle _local;
    private IoSession _session;
    private MethodHandler _handler;
    private Iterable<AMQMethodBody> _replay;

    MinaBrokerProxy(String host, int port, MemberHandle local)
    {
        super(host, port);
        _local = local;
        _legacyHandler = new ClientHandlerRegistry(local, null);
    }

    private void init(IoSession session)
    {
        _session = session;
        _handler = new ClientAdapter(session, _legacyHandler);
    }

    private ConnectFuture connectImpl()
    {
        _logger.info("Connecting to cluster peer: " + getDetails());
        SocketConnector ioConnector = new SocketConnector();
        SocketConnectorConfig cfg = (SocketConnectorConfig) ioConnector.getDefaultConfig();

        SocketSessionConfig scfg = (SocketSessionConfig) cfg.getSessionConfig();
        scfg.setTcpNoDelay(true);
        scfg.setSendBufferSize(32768);
        scfg.setReceiveBufferSize(32768);
        InetSocketAddress address = new InetSocketAddress(getHost(), getPort());
        return ioConnector.connect(address, _binding);
    }

    //extablish connection without handling redirect
    boolean connect() throws IOException, InterruptedException
    {
        ConnectFuture future = connectImpl();
        // wait for connection to complete
        future.join();
        // we call getSession which throws an IOException if there has been an error connecting
        try
        {
            future.getSession();
        }
        catch (RuntimeIOException e)
        {
            _connectionMonitor.failed(e);
            _logger.error(new LogMessage("Could not connect to {0}: {1}", this, e), e);
            throw e;
        }
        return _connectionMonitor.waitUntilOpen();
    }

    void connectAsynch(Iterable<AMQMethodBody> msgs)
    {
        _replay = msgs;
        connectImpl();
    }

    void replay(Iterable<AMQMethodBody> msgs)
    {
        _replay = msgs;
        if(_connectionMonitor.isOpened())
        {
            replay();
        }
    }

    //establish connection, handling redirect if required...
    Broker connectToCluster() throws IOException, InterruptedException
    {
        connect();
        //wait until the connection is open or get a redirection
        if (_connectionMonitor.waitUntilOpen())
        {
            return this;
        }
        else
        {
            Broker broker = new MinaBrokerProxy(_connectionMonitor.getHost(), _connectionMonitor.getPort(), _local);
            broker.connect();
            return broker;
        }
    }

    public void send(AMQDataBlock data) throws AMQConnectionWaitException
    {
        if (_session == null)
        {
            try
            {
                _connectionMonitor.waitUntilOpen();
            }
            catch (InterruptedException e)
            {
                throw new AMQConnectionWaitException("Failed to send " + data + ": " + e, e);
            }
        }
        _session.write(data);
    }

    private void replay()
    {
        if(_replay != null)
        {
            for(AMQMethodBody b : _replay)
            {
                _session.write(new AMQFrame(0, b));
            }
        }
    }

    public void handle(int channel, AMQMethodBody method) throws AMQException
    {
        _logger.info(new LogMessage("Handling method: {0} for channel {1}", method, channel));
        if (!handleResponse(channel, method))
        {
            _logger.warn(new LogMessage("Unhandled method: {0} for channel {1}", method, channel));
        }
    }

    private void handleMethod(int channel, AMQMethodBody method) throws AMQException
    {
        if (method instanceof ConnectionRedirectBody)
        {
            //signal redirection to waiting thread
            ConnectionRedirectBody redirect = (ConnectionRedirectBody) method;
            String[] parts = redirect.host.toString().split(":");
            _connectionMonitor.redirect(parts[0], Integer.parseInt(parts[1]));
        }
        else
        {
            _handler.handle(channel, method);
            if (AMQState.CONNECTION_OPEN.equals(_legacyHandler.getCurrentState()) && _handler != this)
            {
                _handler = this;
                _logger.info(new LogMessage("Connection opened, handler switched"));
                //replay any messages:
                replay();
                //signal waiting thread:
                _connectionMonitor.opened();
            }
        }
    }

    private void handleFrame(AMQFrame frame) throws AMQException
    {
        AMQBody body = frame.getBodyFrame();
        if (body instanceof AMQMethodBody)
        {
            handleMethod(frame.getChannel(), (AMQMethodBody) body);
        }
        else
        {
            throw new AMQUnexpectedBodyTypeException(AMQMethodBody.class, body);
        }
    }

    public String toString()
    {
        return "MinaBrokerProxy[" + (_session == null ? super.toString() : _session.getRemoteAddress()) + "]";
    }

    private class MinaBinding extends IoHandlerAdapter
    {
        public void sessionCreated(IoSession session) throws Exception
        {
            init(session);
            _logger.info(new LogMessage("{0}: created", MinaBrokerProxy.this));
            ProtocolCodecFilter pcf = new ProtocolCodecFilter(new AMQCodecFactory(false));
            session.getFilterChain().addLast("protocolFilter", pcf);
            
            /* Find last protocol version in protocol version list. Make sure last protocol version
            listed in the build file (build-module.xml) is the latest version which will be used
            here. */

            session.write(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion()));
        }

        public void sessionOpened(IoSession session) throws Exception
        {
            _logger.info(new LogMessage("{0}: opened", MinaBrokerProxy.this));
        }

        public void sessionClosed(IoSession session) throws Exception
        {
            _logger.info(new LogMessage("{0}: closed", MinaBrokerProxy.this));
        }

        public void exceptionCaught(IoSession session, Throwable throwable) throws Exception
        {
            _logger.error(new LogMessage("{0}: received {1}", MinaBrokerProxy.this, throwable), throwable);
            if (! (throwable instanceof IOException))
            {
                _session.close();
            }
            failed();
        }

        public void messageReceived(IoSession session, Object object) throws Exception
        {
            if (object instanceof AMQFrame)
            {
                handleFrame((AMQFrame) object);
            }
            else
            {
                throw new AMQUnexpectedFrameTypeException("Received message of unrecognised type: " + object);
            }
        }

        public void messageSent(IoSession session, Object object) throws Exception
        {
            _logger.debug(new LogMessage("{0}: sent {1}", MinaBrokerProxy.this, object));
        }
    }
}
