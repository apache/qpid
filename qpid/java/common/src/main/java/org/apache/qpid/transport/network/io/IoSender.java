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

import static org.apache.qpid.transport.util.Functions.mod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocket;

import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.SenderClosedException;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.util.SystemUtils;


public final class IoSender implements Runnable, ByteBufferSender
{

    private static final Logger log = Logger.get(IoSender.class);

    // by starting here, we ensure that we always test the wraparound
    // case, we should probably make this configurable somehow so that
    // we can test other cases as well
    private final static int START = Integer.MAX_VALUE - 10;

    private final long timeout;
    private final Socket socket;
    private final OutputStream out;

    private final byte[] buffer;
    private volatile int head = START;
    private volatile int tail = START;
    private volatile boolean idle = true;
    private final Object notFull = new Object();
    private final Object notEmpty = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread senderThread;
    private IoReceiver _receiver;
    private final String _remoteSocketAddress;
    private static final boolean shutdownBroken;

    static
    {
        shutdownBroken = SystemUtils.isWindows();
    }

    private volatile Throwable exception = null;

    public IoSender(Socket socket, int bufferSize, long timeout)
    {
        this.socket = socket;
        this.buffer = new byte[pof2(bufferSize)]; // buffer size must be a power of 2
        this.timeout = timeout;
        _remoteSocketAddress = socket.getRemoteSocketAddress().toString();

        try
        {
            out = socket.getOutputStream();
        }
        catch (IOException e)
        {
            throw new TransportException("Error getting output stream for socket", e);
        }

        try
        {
            //Create but deliberately don't start the thread.
            senderThread = Threading.getThreadFactory().createThread(this);
        }
        catch(Exception e)
        {
            throw new Error("Error creating IOSender thread",e);
        }

        senderThread.setDaemon(true);
        senderThread.setName(String.format("IoSender-%s", _remoteSocketAddress));
    }

    public void initiate()
    {
        senderThread.start();
    }

    private static final int pof2(int n)
    {
        int result = 1;
        while (result < n)
        {
            result *= 2;
        }
        return result;
    }

    public void send(ByteBuffer buf)
    {
        checkNotAlreadyClosed();

        if(!senderThread.isAlive())
        {
            throw new SenderException(String.format("sender thread for socket %s is not alive", _remoteSocketAddress));
        }

        final int size = buffer.length;
        int remaining = buf.remaining();

        while (remaining > 0)
        {
            final int hd = head;
            final int tl = tail;

            if (hd - tl >= size)
            {
                flush();
                synchronized (notFull)
                {
                    final long start = System.currentTimeMillis();
                    long elapsed = 0;
                    while (!closed.get() && head - tail >= size && elapsed < timeout)
                    {
                        try
                        {
                            notFull.wait(timeout - elapsed);
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                        elapsed = System.currentTimeMillis() - start;
                    }

                    checkNotAlreadyClosed();

                    if (head - tail >= size)
                    {
                        try
                        {
                            log.error("write timed out for socket %s: head %d, tail %d", _remoteSocketAddress, head, tail);
                            throw new SenderException(String.format("write timed out for socket %s: head %d, tail %d",  _remoteSocketAddress, head, tail));
                        }
                        finally
                        {
                            close(false, false);
                        }
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
            head += length;
            remaining -= length;
        }
    }

    public void flush()
    {
        if (idle)
        {
            synchronized (notEmpty)
            {
                notEmpty.notify();
            }
        }
    }

    public void close()
    {
        close(true, true);
    }

    private void close(boolean awaitSenderBeforeClose, boolean reportException)
    {
        if (!closed.getAndSet(true))
        {
            synchronized (notFull)
            {
                notFull.notify();
            }

            synchronized (notEmpty)
            {
                notEmpty.notify();
            }

            try
            {
                if (awaitSenderBeforeClose)
                {
                    awaitSenderThreadShutdown();
                }
            }
            finally
            {
                closeReceiver();
            }
            if (reportException && exception != null)
            {
                throw new SenderException(exception);
            }
        }
    }

    private void closeReceiver()
    {
        if(_receiver != null)
        {
            try
            {
                _receiver.close();
            }
            catch(RuntimeException e)
            {
                log.error(e, "Exception closing receiver for socket %s", _remoteSocketAddress);
                throw new SenderException(e.getMessage(), e);
            }
        }
    }

    public void run()
    {
        final int size = buffer.length;
        while (true)
        {
            final int hd = head;
            final int tl = tail;

            if (hd == tl)
            {
                if (closed.get())
                {
                    break;
                }

                idle = true;

                synchronized (notEmpty)
                {
                    while (head == tail && !closed.get())
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

                idle = false;

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
                log.info("Exception in thread sending to '" + _remoteSocketAddress + "': " + e);
                exception = e;
                close(false, false);
                break;
            }
            tail += length;
            if (head - tl >= size)
            {
                synchronized (notFull)
                {
                    notFull.notify();
                }
            }
        }

        if (!shutdownBroken && !(socket instanceof SSLSocket))
        {
            try
            {
                socket.shutdownOutput();
            }
            catch (IOException e)
            {
                //pass
            }
        }
    }

    public void setReceiver(IoReceiver receiver)
    {
        _receiver = receiver;
    }

    private void awaitSenderThreadShutdown()
    {
        if (Thread.currentThread() != senderThread)
        {
            try
            {
                senderThread.join(timeout);
                if (senderThread.isAlive())
                {
                    log.error("join timed out for socket %s to stop", _remoteSocketAddress);
                    throw new SenderException(String.format("join timed out for socket %s to stop", _remoteSocketAddress));
                }
            }
            catch (InterruptedException e)
            {
                log.error("interrupted whilst waiting for sender thread for socket %s to stop", _remoteSocketAddress);
                throw new SenderException(e);
            }
        }
    }

    private void checkNotAlreadyClosed()
    {
        if (closed.get())
        {
            throw new SenderClosedException(String.format("sender for socket %s is closed", _remoteSocketAddress), exception);
        }
    }
}
