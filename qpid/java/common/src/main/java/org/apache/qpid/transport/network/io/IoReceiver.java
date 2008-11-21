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
package org.apache.qpid.transport.network.io;

import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.util.Logger;

import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IoReceiver
 *
 */

final class IoReceiver extends Thread
{

    private static final Logger log = Logger.get(IoReceiver.class);

    private final IoTransport transport;
    private final Receiver<ByteBuffer> receiver;
    private final int bufferSize;
    private final Socket socket;
    private final long timeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public IoReceiver(IoTransport transport, Receiver<ByteBuffer> receiver,
                      int bufferSize, long timeout)
    {
        this.transport = transport;
        this.receiver = receiver;
        this.bufferSize = bufferSize;
        this.socket = transport.getSocket();
        this.timeout = timeout;

        setDaemon(true);
        setName(String.format("IoReceiver - %s", socket.getRemoteSocketAddress()));
        start();
    }

    void close(boolean block)
    {
        if (!closed.getAndSet(true))
        {
            try
            {
                if (((String) System.getProperties().get("os.name")).matches("(?i).*windows.*"))
                {
                   socket.close();
                }
                else
                {
                    socket.shutdownInput();
                }
                if (block && Thread.currentThread() != this)
                {
                    join(timeout);
                    if (isAlive())
                    {
                        throw new TransportException("join timed out");
                    }
                }
            }
            catch (InterruptedException e)
            {
                throw new TransportException(e);
            }
            catch (IOException e)
            {
                throw new TransportException(e);
            }
        }
    }

    public void run()
    {
        final int threshold = bufferSize / 2;

        // I set the read buffer size simillar to SO_RCVBUF
        // Haven't tested with a lower value to see if it's better or worse
        byte[] buffer = new byte[bufferSize];
        try
        {
            InputStream in = socket.getInputStream();
            int read = 0;
            int offset = 0;
            while ((read = in.read(buffer, offset, bufferSize-offset)) != -1)
            {
                if (read > 0)
                {
                    ByteBuffer b = ByteBuffer.wrap(buffer,offset,read);
                    receiver.received(b);
                    offset+=read;
                    if (offset > threshold)
                    {
                        offset = 0;
                        buffer = new byte[bufferSize];
                    }
                }
            }
            socket.close();
        }
        catch (Throwable t)
        {
            receiver.exception(t);
        }
        finally
        {
            receiver.closed();
        }
    }

}
