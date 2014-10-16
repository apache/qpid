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
package org.apache.qpid.amqp_1_0.client.websocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketClient;
import org.eclipse.jetty.websocket.WebSocketClientFactory;

import org.apache.qpid.amqp_1_0.client.ConnectionException;
import org.apache.qpid.amqp_1_0.client.SSLUtil;
import org.apache.qpid.amqp_1_0.client.TransportProvider;
import org.apache.qpid.amqp_1_0.codec.FrameWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.framing.ConnectionHandler;
import org.apache.qpid.amqp_1_0.framing.ExceptionHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;

class WebSocketProvider implements TransportProvider
{
    public static final String AMQP_WEBSOCKET_SUBPROTOCOL = "AMQPWSB10";

    private static final byte AMQP_HEADER_FRAME_TYPE = (byte) 222;
    private static int _connections;
    private static QueuedThreadPool _threadPool;
    private final String _transport;
    private static WebSocketClientFactory _factory;
    private WebSocket.Connection _connection;

    public WebSocketProvider(final String transport)
    {
        _transport = transport;
    }

    private static synchronized WebSocketClientFactory getWebSocketClientFactory(SSLContext context) throws Exception
    {
        if(_threadPool == null)
        {
            _threadPool = new QueuedThreadPool();
        }
        if(context != null)
        {
            WebSocketClientFactory factory = new WebSocketClientFactory(_threadPool);
            SslContextFactory sslContextFactory = factory.getSslContextFactory();


            sslContextFactory.setSslContext(context);
            sslContextFactory.addExcludeProtocols(SSLUtil.SSLV3_PROTOCOL);
            factory.start();

            return factory;
        }
        else
        {
            if(_factory == null)
            {
                _factory = new WebSocketClientFactory(_threadPool);
                _factory.start();
            }
            _connections++;
            return _factory;
        }
    }


    private static synchronized void removeClient(final WebSocketClientFactory factory) throws Exception
    {

        if(factory == _factory)
        {
            if(--_connections == 0)
            {
                _factory.stop();
                _factory = null;
            }
        }
        else
        {
            factory.stop();
        }
    }

    @Override
    public void connect(final ConnectionEndpoint conn,
                        final String address,
                        final int port,
                        final SSLContext sslContext, final ExceptionHandler exceptionHandler) throws ConnectionException
    {

        try
        {
            final WebSocketClientFactory webSocketClientFactory = getWebSocketClientFactory(sslContext);
            WebSocketClient client = webSocketClientFactory.newWebSocketClient();
            // Configure the client
            client.setProtocol(AMQP_WEBSOCKET_SUBPROTOCOL);


            ConnectionHandler.FrameOutput<FrameBody> out = new ConnectionHandler.FrameOutput<FrameBody>(conn);

            final ConnectionHandler.FrameSource src;

            if(conn.requiresSASL())
            {
                ConnectionHandler.FrameOutput<SaslFrameBody> saslOut = new ConnectionHandler.FrameOutput<SaslFrameBody>(conn);

                src =  new ConnectionHandler.SequentialFrameSource(new HeaderFrameSource((byte)'A',
                                                                                               (byte)'M',
                                                                                               (byte)'Q',
                                                                                               (byte)'P',
                                                                                               (byte)3,
                                                                                               (byte)1,
                                                                                               (byte)0,
                                                                                               (byte)0),
                                                                   saslOut.asFrameSource(),
                                                                   new HeaderFrameSource((byte)'A',
                                                                                               (byte)'M',
                                                                                               (byte)'Q',
                                                                                               (byte)'P',
                                                                                               (byte)0,
                                                                                               (byte)1,
                                                                                               (byte)0,
                                                                                               (byte)0),
                                                                   out.asFrameSource());

                conn.setSaslFrameOutput(saslOut);
            }
            else
            {
                src =  new ConnectionHandler.SequentialFrameSource(new HeaderFrameSource((byte)'A',
                                                                                               (byte)'M',
                                                                                               (byte)'Q',
                                                                                               (byte)'P',
                                                                                               (byte)0,
                                                                                               (byte)1,
                                                                                               (byte)0,
                                                                                               (byte)0),
                                                               out.asFrameSource());
            }

            final ConnectionHandler handler = new ConnectionHandler(conn);
            conn.setFrameOutputHandler(out);
            final URI uri = new URI(_transport +"://"+ address+":"+ port +"/");
            _connection = client.open(uri, new WebSocket.OnBinaryMessage()
            {
                public void onOpen(Connection connection)
                {

                    Thread outputThread = new Thread(new FrameOutputThread(connection, src, conn, exceptionHandler, webSocketClientFactory));
                    outputThread.setDaemon(true);
                    outputThread.start();
                }

                public void onClose(int closeCode, String message)
                {
                    conn.inputClosed();
                }

                @Override
                public void onMessage(final byte[] data, final int offset, final int length)
                {
                    handler.parse(ByteBuffer.wrap(data,offset,length).slice());
                }
            }).get(5, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            throw new ConnectionException(e);
        }

    }

    @Override
    public void close()
    {
        _connection.close();
    }


    public static class HeaderFrameSource implements ConnectionHandler.FrameSource
    {

        private final ByteBuffer _buffer;
        private boolean _closed;

        public HeaderFrameSource(byte... headerBytes)
        {
            _buffer = ByteBuffer.wrap(headerBytes);
        }


        @Override
        public AMQFrame getNextFrame(final boolean wait)
        {
            if(_closed)
            {
                return null;
            }
            else
            {
                _closed = true;
                return new HeaderFrame(_buffer);
            }
        }

        public boolean closed()
        {
            return _closed;
        }

        @Override
        public void close()
        {
            _closed = true;
        }

    }


    private static class HeaderFrame extends AMQFrame
    {

        public HeaderFrame(final ByteBuffer buffer)
        {
            super(null,buffer);
        }

        @Override
        public short getChannel()
        {
            return 0;
        }

        @Override
        public byte getFrameType()
        {
            return AMQP_HEADER_FRAME_TYPE;
        }
    }

    private class FrameOutputThread implements Runnable
    {
        private final WebSocket.Connection _connection;
        private final ConnectionHandler.FrameSource _frameSource;
        private final ExceptionHandler _exceptionHandler;
        private final FrameWriter _frameWriter;
        private final byte[] _buffer;
        private final WebSocketClientFactory _factory;

        public FrameOutputThread(final WebSocket.Connection connection,
                                 final ConnectionHandler.FrameSource src,
                                 final ConnectionEndpoint conn,
                                 final ExceptionHandler exceptionHandler, final WebSocketClientFactory factory)
        {
            _connection = connection;
            _frameSource = src;
            _exceptionHandler = exceptionHandler;
            _frameWriter = new FrameWriter(conn.getDescribedTypeRegistry());
            _buffer = new byte[conn.getMaxFrameSize()];
            _factory = factory;
        }

        @Override
        public void run()
        {

            final FrameWriter frameWriter = _frameWriter;
            final ByteBuffer buffer = ByteBuffer.wrap(_buffer);
            try
            {

                while(_connection.isOpen() && !_frameSource.closed())
                {
                    AMQFrame frame = _frameSource.getNextFrame(true);
                    if(frame instanceof HeaderFrame)
                    {
                        _connection.sendMessage(frame.getPayload().array(),
                                                frame.getPayload().arrayOffset(),
                                                frame.getPayload().remaining());
                    }
                    else if(frame != null)
                    {
                        frameWriter.setValue(frame);
                        buffer.clear();
                        int length = frameWriter.writeToBuffer(buffer);
                        _connection.sendMessage(_buffer,0,length);
                    }
                }
                if(_frameSource.closed() && _connection.isOpen())
                {
                    _connection.close();
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                try
                {
                    removeClient(_factory);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
