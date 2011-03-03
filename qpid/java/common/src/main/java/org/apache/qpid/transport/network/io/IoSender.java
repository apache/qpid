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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.util.Logger;

public final class IoSender implements Runnable, Sender<ByteBuffer>
{
    private static final Logger _log = Logger.get(IoSender.class);

    // by starting here, we ensure that we always test the wraparound
    // case, we should probably make this configurable somehow so that
    // we can test other cases as well
    private static final int START = Integer.MAX_VALUE - 10;
    private static final AtomicLong _id = new AtomicLong(0);

    private final long _timeout;
    private final Socket _socket;
    private final OutputStream _out;

    private final byte[] _buffer;
    private volatile int _head = START;
    private volatile int _tail = START;
    private volatile boolean _idle = true;
    private final Object _notFull = new Object();
    private final Object _notEmpty = new Object();
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final Thread _senderThread;
          
    private volatile Throwable _exception = null;

    public IoSender(Socket socket, int bufferSize, long timeout)
    {
        _socket = socket;
        _buffer = new byte[pof2(bufferSize)]; // buffer size must be a power of 2
        _timeout = timeout;

        try
        {
            _out = socket.getOutputStream();
        }
        catch (IOException e)
        {
            throw new TransportException("Error getting output stream for socket", e);
        }

        try
        {
            _senderThread = Threading.getThreadFactory().createThread(this);                      
        }
        catch(Exception e)
        {
            throw new Error("Error creating IOSender thread",e);
        }
        
        _senderThread.setDaemon(true);
        _senderThread.setName(String.format("IoSender-%d-%s", _id.getAndIncrement(), socket.getRemoteSocketAddress()));
        _senderThread.start();
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
        if (_closed.get())
        {
            throw new SenderException("sender is closed", _exception);
        }

        final int size = _buffer.length;
        int remaining = buf.remaining();

        while (remaining > 0)
        {
            final int hd = _head;
            final int tl = _tail;

            if (hd - tl >= size)
            {
                flush();
                synchronized (_notFull)
                {
                    long start = System.currentTimeMillis();
                    long elapsed = 0;
                    while (!_closed.get() && _head - _tail >= size && elapsed < _timeout)
                    {
                        try
                        {
                            _notFull.wait(_timeout - elapsed);
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                        elapsed += System.currentTimeMillis() - start;
                    }

                    if (_closed.get())
                    {
                        throw new SenderException("sender is closed", _exception);
                    }

                    if (_head - _tail >= size)
                    {
                        throw new SenderException(String.format("write timed out: %s, %s", _head, _tail));
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

            buf.get(_buffer, hd_idx, length);
            _head += length;
            remaining -= length;
        }
        flush();
    }

    public void flush()
    {
        if (_idle)
        {
            synchronized (_notEmpty)
            {
                _notEmpty.notify();
            }
        }
    }

    public void close()
    {
        if (!_closed.getAndSet(true))
        {
            synchronized (_notFull)
            {
                _notFull.notify();
            }

            synchronized (_notEmpty)
            {
                _notEmpty.notify();
            }

            try
            {
                if (Thread.currentThread() != _senderThread)
                {
                    if (Transport.WINDOWS)
                    {
                       _socket.close();
                    }
                    else
                    {
                        _socket.shutdownOutput();
                    }
                    _senderThread.join(_timeout);
                    if (_senderThread.isAlive())
                    {
                        throw new SenderException("senderThread join timed out");
                    }
                }
            }
            catch (InterruptedException e)
            {
                throw new SenderException("Close interrupted", e);
            }
            catch (IOException e)
            {
                throw new SenderException("IO error closing", e);
            }

            if (_exception != null)
            {
                throw new SenderException(_exception);
            }
        }
    }

    public void run()
    {
        final int size = _buffer.length;       
        while (true)
        {
            final int hd = _head;
            final int tl = _tail;

            if (hd == tl)
            {
                if (_closed.get())
                {
                    break;
                }

                _idle = true;

                synchronized (_notEmpty)
                {
                    while (_head == _tail && !_closed.get())
                    {
                        try
                        {
                            _notEmpty.wait();
                        }
                        catch (InterruptedException e)
                        {
                            // pass
                        }
                    }
                }

                _idle = false;

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
                _out.write(_buffer, tl_idx, length);
            }
            catch (IOException e)
            {
                _log.error(e, "error in write thread");
                _exception = e;
                close();
                break;
            }
            _tail += length;
            if (_head - tl >= size)
            {
                synchronized (_notFull)
                {
                    _notFull.notify();
                }
            }
        }
    }

    public void setIdleTimeout(int i)
    {
        try
        {
            _socket.setSoTimeout(i);
        }
        catch (Exception e)
        {
            throw new SenderException(e);
        }
    }
}
