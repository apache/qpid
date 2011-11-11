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

import org.apache.qpid.common.Closeable;
import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocket;

/**
 * IoReceiver
 *
 */

final class IoReceiver implements Runnable, Closeable
{

    private static final Logger log = Logger.get(IoReceiver.class);

    private final Receiver<ByteBuffer> receiver;
    private final int bufferSize;
    private final Socket socket;
    private final long timeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread receiverThread;
    private static final boolean shutdownBroken;
    static
    {
        String osName = System.getProperty("os.name");
        shutdownBroken = osName == null ? false : osName.matches("(?i).*windows.*");
    }

    public IoReceiver(Socket socket, Receiver<ByteBuffer> receiver, int bufferSize, long timeout)
    {
        this.receiver = receiver;
        this.bufferSize = bufferSize;
        this.socket = socket;
        this.timeout = timeout;

        try
        {
            //Create but deliberately don't start the thread.
            receiverThread = Threading.getThreadFactory().createThread(this);
        }
        catch(Exception e)
        {
            throw new RuntimeException("Error creating IOReceiver thread",e);
        }
        receiverThread.setDaemon(true);
        receiverThread.setName(String.format("IoReceiver - %s", socket.getRemoteSocketAddress()));
    }

    public void initiate()
    {
        receiverThread.start();
    }

    public void close()
    {
        close(false);
    }

    void close(boolean block)
    {
        if (!closed.getAndSet(true))
        {
            try
            {
                try
                {
                    if (shutdownBroken || socket instanceof SSLSocket)
                    {
                       socket.close();
                    }
                    else
                    {
                        socket.shutdownInput();
                    }
                }
                catch(SocketException se)
                {
                    if(!socket.isClosed() && !socket.isInputShutdown())
                    {
                        throw se;
                    }
                }
                if (block && Thread.currentThread() != receiverThread)
                {
                    receiverThread.join(timeout);
                    if (receiverThread.isAlive())
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
        }
        catch (Throwable t)
        {
            if (!(shutdownBroken &&
                  t instanceof SocketException &&
                  t.getMessage().equalsIgnoreCase("socket closed") &&
                  closed.get()))
            {
                receiver.exception(t);
            }
        }
        finally
        {
            receiver.closed();
            try
            {
                socket.close();
            }
            catch(Exception e)
            {
                log.warn(e, "Error closing socket");
            }
        }
    }

}
