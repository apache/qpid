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

package org.apache.qpid.server.transport;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SelectorThread extends Thread
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectorThread.class);

    static final String IO_THREAD_NAME_PREFIX  = "IO-";
    private final Queue<Runnable> _tasks = new ConcurrentLinkedQueue<>();

    /**
     * Queue of connections that are not currently scheduled and not registered with the selector.
     * These need to go back into the Selector.
     */
    private final Queue<NonBlockingConnection> _unregisteredConnections = new ConcurrentLinkedQueue<>();

    /** Set of connections that are currently being selected upon */
    private final Set<NonBlockingConnection> _unscheduledConnections = new HashSet<>();

    private final Selector _selector;
    private final AtomicBoolean _closed = new AtomicBoolean();
    private final NetworkConnectionScheduler _scheduler = new NetworkConnectionScheduler(this);
    private final NonBlockingNetworkTransport _transport;
    private long _nextTimeout;

    SelectorThread(final NonBlockingNetworkTransport nonBlockingNetworkTransport) throws IOException
    {
        super("SelectorThread-" + nonBlockingNetworkTransport.getConfig().getAddress().toString());

        _transport = nonBlockingNetworkTransport;
        _selector = Selector.open();
    }

    public void addAcceptingSocket(final ServerSocketChannel socketChannel)
    {
        _tasks.add(new Runnable()
                    {
                        @Override
                        public void run()
                        {

                            try
                            {
                                socketChannel.register(_selector, SelectionKey.OP_ACCEPT);
                            }
                            catch (IllegalStateException | ClosedChannelException e)
                            {
                                // TODO Communicate condition back to model object to make it go into the ERROR state
                                LOGGER.error("Failed to register selector on accepting port", e);
                            }
                        }
                    });
        _selector.wakeup();
    }

    public void cancelAcceptingSocket(final ServerSocketChannel socketChannel)
    {
        _tasks.add(new Runnable()
        {
            @Override
            public void run()
            {
                SelectionKey selectionKey = socketChannel.keyFor(_selector);
                if(selectionKey != null)
                {
                    selectionKey.cancel();
                }
            }
        });
        _selector.wakeup();
    }

    @Override
    public void run()
    {

        _nextTimeout = 0;

        try
        {
            while (!_closed.get())
            {

                try
                {
                    _selector.select(_nextTimeout);
                }
                catch (IOException e)
                {
                    // TODO Inform the model object
                    LOGGER.error("Failed to select for " + _transport.getConfig().getAddress().toString(),e );
                    break;
                }

                runTasks();

                List<NonBlockingConnection> toBeScheduled = processSelectionKeys();

                toBeScheduled.addAll(reregisterUnregisteredConnections());

                toBeScheduled.addAll(processUnscheduledConnections());

                for (NonBlockingConnection connection : toBeScheduled)
                {
                    _scheduler.schedule(connection);
                }
            }
        }
        finally
        {
            try
            {
                _selector.close();
            }
            catch (IOException e)
            {
                LOGGER.debug("Failed to close selector", e);
            }
        }

    }

    private List<NonBlockingConnection> processUnscheduledConnections()
    {
        List<NonBlockingConnection> toBeScheduled = new ArrayList<>();

        long currentTime = System.currentTimeMillis();
        Iterator<NonBlockingConnection> iterator = _unscheduledConnections.iterator();
        _nextTimeout = Integer.MAX_VALUE;
        while (iterator.hasNext())
        {
            NonBlockingConnection connection = iterator.next();

            int period = connection.getTicker().getTimeToNextTick(currentTime);

            if (period <= 0 || connection.isStateChanged())
            {
                toBeScheduled.add(connection);
                try
                {
                    SelectionKey register = connection.getSocketChannel().register(_selector, 0);
                    register.cancel();
                }
                catch (ClosedChannelException e)
                {
                    LOGGER.debug("Failed to register with selector for connection " + connection +
                                 ". Connection is probably being closed by peer.", e);
                }
                iterator.remove();
            }
            else
            {
                _nextTimeout = Math.min(period, _nextTimeout);
            }
        }

        return toBeScheduled;
    }

    private List<NonBlockingConnection> reregisterUnregisteredConnections()
    {
        List<NonBlockingConnection> unregisterableConnections = new ArrayList<>();

        while (_unregisteredConnections.peek() != null)
        {
            NonBlockingConnection unregisteredConnection = _unregisteredConnections.poll();
            _unscheduledConnections.add(unregisteredConnection);


            final int ops = (unregisteredConnection.canRead() ? SelectionKey.OP_READ : 0)
                            | (unregisteredConnection.waitingForWrite() ? SelectionKey.OP_WRITE : 0);
            try
            {
                unregisteredConnection.getSocketChannel().register(_selector, ops, unregisteredConnection);
            }
            catch (ClosedChannelException e)
            {
                unregisterableConnections.add(unregisteredConnection);
            }
        }

        return unregisterableConnections;
    }

    private List<NonBlockingConnection> processSelectionKeys()
    {
        List<NonBlockingConnection> toBeScheduled = new ArrayList<>();

        Set<SelectionKey> selectionKeys = _selector.selectedKeys();
        for (SelectionKey key : selectionKeys)
        {
            if(key.isAcceptable())
            {
                // todo - should we schedule this rather than running in this thread?
                _transport.acceptSocketChannel((ServerSocketChannel)key.channel());
            }
            else
            {
                NonBlockingConnection connection = (NonBlockingConnection) key.attachment();

                try
                {
                    key.channel().register(_selector, 0);
                }
                catch (ClosedChannelException e)
                {
                    // Ignore - we will schedule the connection anyway
                }

                toBeScheduled.add(connection);
                _unscheduledConnections.remove(connection);
            }

        }
        selectionKeys.clear();

        return toBeScheduled;
    }

    private void runTasks()
    {
        while(_tasks.peek() != null)
        {
            Runnable task = _tasks.poll();
            task.run();
        }
    }

    public void addConnection(final NonBlockingConnection connection)
    {
        _unregisteredConnections.add(connection);
        _selector.wakeup();

    }

    public void wakeup()
    {
        _selector.wakeup();
    }

    public void close()
    {
        _closed.set(true);
        _selector.wakeup();
        _scheduler.close();
    }

    boolean isIOThread()
    {
        return Thread.currentThread().getName().startsWith(SelectorThread.IO_THREAD_NAME_PREFIX);
    }
}
