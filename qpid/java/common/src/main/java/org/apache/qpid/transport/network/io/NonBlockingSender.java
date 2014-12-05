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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderClosedException;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.util.Logger;


public final class NonBlockingSender implements Runnable, Sender<ByteBuffer>
{

    private static final Logger log = Logger.get(NonBlockingSender.class);

    // by starting here, we ensure that we always test the wraparound
    // case, we should probably make this configurable somehow so that
    // we can test other cases as well
    private final static int START = Integer.MAX_VALUE - 10;

    private final long timeout;
    private final SocketChannel socket;

    private final ConcurrentLinkedQueue<ByteBuffer> _buffers = new ConcurrentLinkedQueue<>();
    private final Object _isEmpty = new Object();

    private volatile boolean idle = true;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread senderThread;
    private NonBlockingReceiver _receiver;
    private final String _remoteSocketAddress;

    private volatile Throwable exception = null;

    public NonBlockingSender(SocketChannel socket, int bufferSize, long timeout)
    {
        this.socket = socket;
        this.timeout = timeout;
        _remoteSocketAddress = socket.socket().getRemoteSocketAddress().toString();


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
        senderThread.setName(String.format("IoSender - %s", _remoteSocketAddress));
    }

    public void initiate()
    {
        senderThread.start();
    }


    public void send(ByteBuffer buf)
    {
        checkNotAlreadyClosed();

        if(!senderThread.isAlive())
        {
            throw new SenderException(String.format("sender thread for socket %s is not alive", _remoteSocketAddress));
        }


        _buffers.add(buf);
        synchronized (_isEmpty)
        {
            _isEmpty.notifyAll();
        }

    }

    public void flush()
    {
        if (idle)
        {
            synchronized (_isEmpty)
            {
                _isEmpty.notify();
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

            synchronized (_isEmpty)
            {
                _isEmpty.notify();
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
        while (true)
        {

            if (closed.get())
            {
                break;
            }

            idle = true;

            synchronized (_isEmpty)
            {
                while (_buffers.isEmpty() && !closed.get())
                {
                    try
                    {
                        _isEmpty.wait();
                    }
                    catch (InterruptedException e)
                    {
                        // pass
                    }
                }
            }

            idle = false;

            ByteBuffer[] bufArray = new ByteBuffer[_buffers.size()];
            Iterator<ByteBuffer> bufferIterator = _buffers.iterator();
            for(int i = 0; i < bufArray.length; i++)
            {
                bufArray[i] = bufferIterator.next();
            }

            try
            {
                socket.write(bufArray);
                for(ByteBuffer buf : bufArray)
                {
                    if(buf.remaining() == 0)
                    {
                        _buffers.poll();
                    }
                    else
                    {
                        break;
                    }
                }
            }
            catch (IOException e)
            {
                log.info("Exception in thread sending to '" + _remoteSocketAddress + "': " + e);
                exception = e;
                close(false, false);
                break;
            }

        }

    }

    public void setIdleTimeout(int i)
    {
        try
        {
            socket.socket().setSoTimeout(i);
        }
        catch (Exception e)
        {
            throw new SenderException(e);
        }
    }

    public void setReceiver(NonBlockingReceiver receiver)
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
