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

import org.apache.qpid.amqp_1_0.framing.ConnectionHandler;
import org.apache.qpid.amqp_1_0.framing.ExceptionHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

class TCPTransportProvier implements TransportProvider
{
    private final String _transport;

    public TCPTransportProvier(final String transport)
    {
        _transport = transport;
    }

    @Override
    public void connect(final ConnectionEndpoint conn,
                        final String address,
                        final int port,
                        final boolean ssl,
                        final ExceptionHandler exceptionHandler) throws ConnectionException
    {
        try
        {
            final Socket s;
            if(ssl)
            {
                s = SSLSocketFactory.getDefault().createSocket(address, port);
            }
            else
            {
                s = new Socket(address, port);
            }

            conn.setRemoteAddress(s.getRemoteSocketAddress());


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
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(saslOut,conn.getDescribedTypeRegistry()),
                                                                   new ConnectionHandler.HeaderBytesSource(conn, (byte)'A',
                                                                                                           (byte)'M',
                                                                                                           (byte)'Q',
                                                                                                           (byte)'P',
                                                                                                           (byte)0,
                                                                                                           (byte)1,
                                                                                                           (byte)0,
                                                                                                           (byte)0),
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(out,conn.getDescribedTypeRegistry())
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
                                                                   new ConnectionHandler.FrameToBytesSourceAdapter(out,conn.getDescribedTypeRegistry())
                );
            }


            final OutputStream outputStream = s.getOutputStream();
            ConnectionHandler.BytesOutputHandler outputHandler =
                    new ConnectionHandler.BytesOutputHandler(outputStream, src, conn, exceptionHandler);
            Thread outputThread = new Thread(outputHandler);
            outputThread.setDaemon(true);
            outputThread.start();
            conn.setFrameOutputHandler(out);


            final ConnectionHandler handler = new ConnectionHandler(conn);
            final InputStream inputStream = s.getInputStream();

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
                            try
                            {
                                synchronized (outputStream)
                                {
                                    s.close();
                                }
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                            }
                        }
                    }
                }
            });

            inputThread.setDaemon(true);
            inputThread.start();

        }
        catch (IOException e)
        {
            throw new ConnectionException(e);
        }
    }
    private void doRead(final ConnectionEndpoint conn, final ConnectionHandler handler, final InputStream inputStream)
    {
        byte[] buf = new byte[2<<15];


        try
        {
            int read;
            boolean done = false;
            while(!handler.isDone() && (read = inputStream.read(buf)) != -1)
            {
                ByteBuffer bbuf = ByteBuffer.wrap(buf, 0, read);
                while(bbuf.hasRemaining() && !handler.isDone())
                {
                    handler.parse(bbuf);
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
