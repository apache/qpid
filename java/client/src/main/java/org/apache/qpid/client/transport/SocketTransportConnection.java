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
package org.apache.qpid.client.transport;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.transport.socket.nio.ExistingSocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;

import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.pool.ReadWriteThreadModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SocketTransportConnection implements ITransportConnection
{
    private static final Logger _logger = LoggerFactory.getLogger(SocketTransportConnection.class);
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;

    private SocketConnectorFactory _socketConnectorFactory;

    static interface SocketConnectorFactory
    {
        IoConnector newSocketConnector();
    }

    public SocketTransportConnection(SocketConnectorFactory socketConnectorFactory)
    {
        _socketConnectorFactory = socketConnectorFactory;
    }

    public void connect(AMQProtocolHandler protocolHandler, BrokerDetails brokerDetail) throws IOException
    {
        ByteBuffer.setUseDirectBuffers(Boolean.getBoolean("amqj.enableDirectBuffers"));

        // the MINA default is currently to use the pooled allocator although this may change in future
        // once more testing of the performance of the simple allocator has been done
        if (!Boolean.getBoolean("amqj.enablePooledAllocator"))
        {
            _logger.info("Using SimpleByteBufferAllocator");
            ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        }

        final IoConnector ioConnector = _socketConnectorFactory.newSocketConnector();
        SocketConnectorConfig cfg = (SocketConnectorConfig) ioConnector.getDefaultConfig();

        // if we do not use our own thread model we get the MINA default which is to use
        // its own leader-follower model
        boolean readWriteThreading = Boolean.getBoolean("amqj.shared_read_write_pool");
        if (readWriteThreading)
        {
            cfg.setThreadModel(ReadWriteThreadModel.getInstance());
        }

        SocketSessionConfig scfg = (SocketSessionConfig) cfg.getSessionConfig();
        scfg.setTcpNoDelay("true".equalsIgnoreCase(System.getProperty("amqj.tcpNoDelay", "true")));
        scfg.setSendBufferSize(Integer.getInteger("amqj.sendBufferSize", DEFAULT_BUFFER_SIZE));
        _logger.info("send-buffer-size = " + scfg.getSendBufferSize());
        scfg.setReceiveBufferSize(Integer.getInteger("amqj.receiveBufferSize", DEFAULT_BUFFER_SIZE));
        _logger.info("recv-buffer-size = " + scfg.getReceiveBufferSize());

        final InetSocketAddress address;

        if (brokerDetail.getTransport().equals(BrokerDetails.SOCKET))
        {
            address = null;

            Socket socket = TransportConnection.removeOpenSocket(brokerDetail.getHost());

            if (socket != null)
            {
                _logger.info("Using existing Socket:" + socket);

                ((ExistingSocketConnector) ioConnector).setOpenSocket(socket);
            }
            else
            {
                throw new IllegalArgumentException("Active Socket must be provided for broker " +
                                                   "with 'socket://<SocketID>' transport:" + brokerDetail);
            }
        }
        else
        {
            address = new InetSocketAddress(brokerDetail.getHost(), brokerDetail.getPort());
            _logger.info("Attempting connection to " + address);
        }


        ConnectFuture future = ioConnector.connect(address, protocolHandler);

        // wait for connection to complete
        if (future.join(brokerDetail.getTimeout()))
        {
            // we call getSession which throws an IOException if there has been an error connecting
            future.getSession();
        }
        else
        {
            throw new IOException("Timeout waiting for connection.");
        }
    }
}
