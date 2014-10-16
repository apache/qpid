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
package org.apache.qpid.amqp_1_0.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.qpid.amqp_1_0.framing.ConnectionHandler;
import org.apache.qpid.amqp_1_0.framing.ExceptionHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;

class TCPTransportProvier implements TransportProvider
{
    private static final Logger RAW_LOGGER = Logger.getLogger("RAW");

    private Socket _socket;
    private final String _transport;
    
    // Defines read socket timeout in milliseconds.  A value of 0 means that the socket
    // read will block forever.  Default value is set to 10000, which is 10 seconds.
    private int _readTimeout = Integer.getInteger("qpid.connection_read_timeout", 10000);
        
    // Defines the max idle read timeout in milliseconds before the connection is closed down in 
    // the event of a SocketTimeoutException.  A value of -1L will disable idle read timeout checking.
    // Default value is set to -1L, which means disable idle read checks.
    private long _readIdleTimeout = Long.getLong("qpid.connection_read_idle_timeout", -1L);
    private final AtomicLong _threadNameIndex = new AtomicLong();

    public TCPTransportProvier(final String transport)
    {
        _transport = transport;
    }

    @Override
    public void connect(final ConnectionEndpoint conn,
                        final String address,
                        final int port,
                        final SSLContext sslContext,
                        final ExceptionHandler exceptionHandler) throws ConnectionException
    {
        try
        {
            if(sslContext != null)
            {
                final SSLSocketFactory socketFactory = sslContext.getSocketFactory();
                SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(address, port);
                SSLUtil.removeSSLv3Support(sslSocket);
                sslSocket.startHandshake();
                conn.setExternalPrincipal(sslSocket.getSession().getLocalPrincipal());
                _socket=sslSocket;
            }
            else
            {
                _socket = new Socket(address, port);
            }
            // set socket read timeout
            _socket.setSoTimeout(_readTimeout);

            conn.setRemoteAddress(_socket.getRemoteSocketAddress());

            ConnectionHandler.FrameOutput<FrameBody> out = new ConnectionHandler.FrameOutput<FrameBody>(conn);

            ConnectionHandler.BytesSource src;

            if(conn.requiresSASL())
            {
                ConnectionHandler.FrameOutput<SaslFrameBody> saslOut = new ConnectionHandler.FrameOutput<SaslFrameBody>(conn);

                src =  new ConnectionHandler.SequentialBytesSource(new ConnectionHandler.HeaderBytesSource(conn, (byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)3,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(saslOut.asFrameSource(),conn.getDescribedTypeRegistry()),
                                                                   new ConnectionHandler.HeaderBytesSource(conn, (byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)0,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(out.asFrameSource(),conn.getDescribedTypeRegistry())
                );

                conn.setSaslFrameOutput(saslOut);
            }
            else
            {
                src =  new ConnectionHandler.SequentialBytesSource(new ConnectionHandler.HeaderBytesSource(conn,(byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)0,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(out.asFrameSource(),conn.getDescribedTypeRegistry())
                );
            }


            final OutputStream outputStream = _socket.getOutputStream();
            ConnectionHandler.BytesOutputHandler outputHandler =
                    new ConnectionHandler.BytesOutputHandler(outputStream, src, conn, exceptionHandler);
            long threadIndex = _threadNameIndex.getAndIncrement();
            Thread outputThread = new Thread(outputHandler, "QpidConnectionOutputThread-"+threadIndex);

            outputThread.setDaemon(true);
            outputThread.start();
            conn.setFrameOutputHandler(out);


            final ConnectionHandler handler = new ConnectionHandler(conn);
            final InputStream inputStream = _socket.getInputStream();

            Thread inputThread = new Thread(new Runnable()
            {

                public void run()
                {
                    try
                    {
                        doRead(conn, handler, inputStream);
                    }
                    finally
                    {
                        if(conn.closedForInput() && conn.closedForOutput())
                        {
                            close();
                        }
                    }
                }
            },"QpidConnectionInputThread-"+threadIndex);

            inputThread.setDaemon(true);
            inputThread.start();

        }
        catch (IOException e)
        {
            throw new ConnectionException(e);
        }
    }

    @Override
    public void close()
    {
        try
        {
            _socket.close();
        }
        catch (IOException e)
        {
            RAW_LOGGER.log(Level.WARNING, "Unexpected Error during TCPTransportProvider socket close", e);
        }
    }

    private void doRead(final ConnectionEndpoint conn, final ConnectionHandler handler, final InputStream inputStream)
    {
        byte[] buf = new byte[2<<15];


        try
        {
            int read;
            boolean done = false;
            long lastReadTime = System.currentTimeMillis();
            while(!handler.isDone())
            {
                try
                {
                    read = inputStream.read(buf);
                    if(read == -1)
                    {
                      break;
                    }
                    lastReadTime = System.currentTimeMillis();
                     
                    ByteBuffer bbuf = ByteBuffer.wrap(buf, 0, read);
                    while(bbuf.hasRemaining() && !handler.isDone())
                    {
                        handler.parse(bbuf);
                    }
                    
                }
                catch(SocketTimeoutException e)
                {
                	// Note that a SocketTimeoutException could only occur if _readTimeout > 0.
                	// Only perform idle read timeout checking if _readIdleTimeout is greater than -1
                	if(_readIdleTimeout > -1 && (System.currentTimeMillis() - lastReadTime >= _readIdleTimeout)){
                		// break out of while loop and close down connection
                		break;
                	}
                }
            }
            if(!handler.isDone())
            {
                conn.inputClosed();
                if(conn.getConnectionEventListener() != null)
                {
                    conn.getConnectionEventListener().closeReceived();
                }
            }
        }
        catch (IOException e)
        {
            conn.inputClosed();
            e.printStackTrace();
        }
    }
}
