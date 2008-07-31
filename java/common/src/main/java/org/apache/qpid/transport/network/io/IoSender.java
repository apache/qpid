/*
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
 */
package org.apache.qpid.transport.network.io;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.util.Logger;

import static org.apache.qpid.transport.util.Functions.*;


final class IoSender extends Thread implements Sender<ByteBuffer>
{

    private static final Logger log = Logger.get(IoSender.class);

    // by starting here, we ensure that we always test the wraparound
    // case, we should probably make this configurable somehow so that
    // we can test other cases as well
    private final static int START = Integer.MAX_VALUE - 10;

    private final IoTransport transport;
    private final long timeout;
    private final Socket socket;
    private final OutputStream out;

    private final byte[] buffer;
    private final AtomicInteger head = new AtomicInteger(START);
    private final AtomicInteger tail = new AtomicInteger(START);
    private final Object notFull = new Object();
    private final Object notEmpty = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile Throwable exception = null;


    public IoSender(IoTransport transport, int bufferSize, long timeout)
    {
        this.transport = transport;
        this.socket = transport.getSocket();
        this.buffer = new byte[bufferSize];
        this.timeout = timeout;

        try
        {
            out = socket.getOutputStream();
        }
        catch (IOException e)
        {
            throw new TransportException("Error getting output stream for socket", e);
        }

        setDaemon(true);
        setName(String.format("IoSender - %s", socket.getRemoteSocketAddress()));
        start();
    }

    private static final int mod(int n, int m)
    {
        int r = n % m;
        return r < 0 ? m + r : r;
    }

    public void send(ByteBuffer buf)
    {
        if (closed.get())
        {
            throw new TransportException("sender is closed", exception);
        }

        final int size = buffer.length;
        int remaining = buf.remaining();

        while (remaining > 0)
        {
            final int hd = head.get();
            final int tl = tail.get();

            if (hd - tl >= size)
            {
                synchronized (notFull)
                {
                    long start = System.currentTimeMillis();
                    long elapsed = 0;
                    while (head.get() - tail.get() >= size && elapsed < timeout)
                    {
                        try
                        {
                            notFull.wait(timeout - elapsed);
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                        elapsed += System.currentTimeMillis() - start;
                    }

                    if (head.get() - tail.get() >= size)
                    {
                        throw new TransportException(String.format("write timed out: %s, %s", head.get(), tail.get()));
                    }
                }
                continue;
            }

            final int hd_idx = mod(hd, size);
            final int tl_idx = mod(tl, size);
            final int length;

            if (tl_idx > hd_idx)
            {
                length = Math.min(tl_idx - hd_idx, remaining);
            }
            else
            {
                length = Math.min(size - hd_idx, remaining);
            }

            buf.get(buffer, hd_idx, length);
            head.getAndAdd(length);
            if (hd == tail.get())
            {
                synchronized (notEmpty)
                {
                    notEmpty.notify();
                }
            }
            remaining -= length;
        }
    }

    public void flush()
    {
        // pass
    }

    public void close()
    {
        close(true);
    }

    void close(boolean reportException)
    {
        if (!closed.getAndSet(true))
        {
            synchronized (notEmpty)
            {
                notEmpty.notify();
            }

            try
            {
                if (Thread.currentThread() != this)
                {
                    join(timeout);
                    if (isAlive())
                    {
                        throw new TransportException("join timed out");
                    }
                }
                transport.getReceiver().close();
                socket.close();
            }
            catch (InterruptedException e)
            {
                throw new TransportException(e);
            }
            catch (IOException e)
            {
                throw new TransportException(e);
            }

            if (reportException && exception != null)
            {
                throw new TransportException(exception);
            }
        }
    }

    public void run()
    {
        final int size = buffer.length;

        while (true)
        {
            final int hd = head.get();
            final int tl = tail.get();

            if (hd == tl)
            {
                if (closed.get())
                {
                    break;
                }

                synchronized (notEmpty)
                {
                    while (head.get() == tail.get() && !closed.get())
                    {
                        try
                        {
                            notEmpty.wait();
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                    }
                }

                continue;
            }

            final int hd_idx = mod(hd, size);
            final int tl_idx = mod(tl, size);

            final int length;
            if (tl_idx < hd_idx)
            {
                length = hd_idx - tl_idx;
            }
            else
            {
                length = size - tl_idx;
            }

            try
            {
                out.write(buffer, tl_idx, length);
            }
            catch (IOException e)
            {
                log.error(e, "error in write thread");
                exception = e;
                close(false);
                break;
            }
            tail.getAndAdd(length);
            if (head.get() - tl >= size)
            {
                synchronized (notFull)
                {
                    notFull.notify();
                }
            }
        }
    }

}
