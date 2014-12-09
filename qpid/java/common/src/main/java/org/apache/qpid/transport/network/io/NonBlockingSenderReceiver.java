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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.network.Ticker;

public class NonBlockingSenderReceiver  implements Runnable, Sender<ByteBuffer>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NonBlockingSenderReceiver.class);

    private final SocketChannel _socketChannel;
    private final Selector _selector;

    private final ConcurrentLinkedQueue<ByteBuffer> _buffers = new ConcurrentLinkedQueue<>();

    private final Thread _ioThread;
    private final String _remoteSocketAddress;
    private final AtomicBoolean _closed = new AtomicBoolean(false);
    private final Receiver<ByteBuffer> _receiver;
    private final int _receiveBufSize;
    private final Ticker _ticker;

    private ByteBuffer _currentBuffer;


    public NonBlockingSenderReceiver(final SocketChannel socketChannel, Receiver<ByteBuffer> receiver, int receiveBufSize, Ticker ticker)
    {
        _socketChannel = socketChannel;
        _receiver = receiver;
        _receiveBufSize = receiveBufSize;
        _ticker = ticker;

        try
        {
            _remoteSocketAddress = socketChannel.getRemoteAddress().toString();
            _socketChannel.configureBlocking(false);
            _selector = Selector.open();
            _socketChannel.register(_selector, SelectionKey.OP_READ);
        }
        catch (IOException e)
        {
            throw new SenderException("Unable to prepare the channel for non-blocking IO", e);
        }
        try
        {
            //Create but deliberately don't start the thread.
            _ioThread = Threading.getThreadFactory().createThread(this);
        }
        catch(Exception e)
        {
            throw new SenderException("Error creating SenderReceiver thread for " + _remoteSocketAddress, e);
        }

        _ioThread.setDaemon(true);
        _ioThread.setName(String.format("IoSenderReceiver - %s", _remoteSocketAddress));

    }

    public void initiate()
    {
        _ioThread.start();
    }

    @Override
    public void setIdleTimeout(final int i)
    {
        // Probably unused - dead code to be removed??
    }

    @Override
    public void send(final ByteBuffer msg)
    {
        // append to list and do selector wakeup
        _buffers.add(msg);
        _selector.wakeup();
    }

    @Override
    public void run()
    {

        LOGGER.debug("I/O for thread " + _remoteSocketAddress + " started");

        // never ending loop doing
        //  try to write all pending byte buffers, handle situation where zero bytes or part of a byte buffer is written
        //  read as much as you can
        //  try to write all pending byte buffers

        while (!_closed.get())
        {

            try
            {
                long currentTime = System.currentTimeMillis();
                int tick = _ticker.getTimeToNextTick(currentTime);
                if(tick <= 0)
                {
                    tick = _ticker.tick(currentTime);
                }

                LOGGER.debug("Tick " + tick);

                int numberReady = _selector.select(tick <= 0 ? 1 : tick);
                Set<SelectionKey> selectionKeys = _selector.selectedKeys();
                selectionKeys.clear();

                LOGGER.debug("Number Ready " +  numberReady);

                doWrite();
                doRead();
                boolean fullyWritten = doWrite();

                _socketChannel.register(_selector, fullyWritten ? SelectionKey.OP_READ : (SelectionKey.OP_WRITE | SelectionKey.OP_READ));
            }
            catch (IOException e)
            {
                LOGGER.info("Exception performing I/O for thread '" + _remoteSocketAddress + "': " + e);
                close();
            }
        }

        try(Selector selector = _selector; SocketChannel channel = _socketChannel)
        {
            while(!doWrite())
            {
            }

            _receiver.closed();
        }
        catch (IOException e)
        {
            LOGGER.info("Exception performing final output for thread '" + _remoteSocketAddress + "': " + e);
        }
        finally
        {
            LOGGER.info("Shutting down IO thread for " + _remoteSocketAddress);
        }
    }



    @Override
    public void flush()
    {
        // maybe just wakeup?

    }

    @Override
    public void close()
    {
        LOGGER.debug("Closing " +  _remoteSocketAddress);

        _closed.set(true);
        _selector.wakeup();

    }

    private boolean doWrite() throws IOException
    {
        int byteBuffersWritten = 0;

        ByteBuffer[] bufArray = new ByteBuffer[_buffers.size()];
        Iterator<ByteBuffer> bufferIterator = _buffers.iterator();
        for (int i = 0; i < bufArray.length; i++)
        {
            bufArray[i] = bufferIterator.next();
        }

        _socketChannel.write(bufArray);

        for (ByteBuffer buf : bufArray)
        {
            if (buf.remaining() == 0)
            {
                byteBuffersWritten++;
                _buffers.poll();
            }
        }

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Written " + byteBuffersWritten + " byte buffer(s) completely");
        }

        return bufArray.length == byteBuffersWritten;
    }

    private void doRead() throws IOException
    {

        int remaining;
        do
        {
            if(_currentBuffer == null || _currentBuffer.remaining() == 0)
            {
                _currentBuffer = ByteBuffer.allocate(_receiveBufSize);
            }
            _socketChannel.read(_currentBuffer);
            remaining = _currentBuffer.remaining();
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Read " + _currentBuffer.position() + " byte(s)");
            }
            ByteBuffer dup = _currentBuffer.duplicate();
            dup.flip();
            _currentBuffer = _currentBuffer.slice();
            _receiver.received(dup);
        }
        while (remaining == 0);

    }
}
