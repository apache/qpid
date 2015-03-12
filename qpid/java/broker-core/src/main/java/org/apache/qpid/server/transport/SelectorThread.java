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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;

import org.apache.qpid.thread.LoggingUncaughtExceptionHandler;


public class SelectorThread extends Thread
{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SelectorThread.class);

    public static final String IO_THREAD_NAME_PREFIX  = "NCS-";
    private final Queue<Runnable> _tasks = new ConcurrentLinkedQueue<>();
    private final Queue<NonBlockingConnection> _unregisteredConnections = new ConcurrentLinkedQueue<>();
    private final Set<NonBlockingConnection> _unscheduledConnections = new HashSet<>();
    private final Selector _selector;
    private final AtomicBoolean _closed = new AtomicBoolean();
    private final NetworkConnectionScheduler _scheduler = new NetworkConnectionScheduler();
    private final NonBlockingNetworkTransport _transport;

    SelectorThread(final String name, final NonBlockingNetworkTransport nonBlockingNetworkTransport)
    {
        super("SelectorThread-"+name);
        _transport = nonBlockingNetworkTransport;
        try
        {
            _selector = Selector.open();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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
                            catch (ClosedChannelException e)
                            {
                                // TODO
                                e.printStackTrace();
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

        long nextTimeout = 0;

        try
        {
            while (!_closed.get())
            {

                _selector.select(nextTimeout);

                while(_tasks.peek() != null)
                {
                    Runnable task = _tasks.poll();
                    task.run();
                }

                List<NonBlockingConnection> toBeScheduled = new ArrayList<>();


                Set<SelectionKey> selectionKeys = _selector.selectedKeys();
                for (SelectionKey key : selectionKeys)
                {
                    if(key.isAcceptable())
                    {
                        // todo - should we schedule this rather than running in this thread?
                        SocketChannel acceptedChannel = ((ServerSocketChannel)key.channel()).accept();
                        _transport.acceptSocketChannel(acceptedChannel);
                    }
                    else
                    {
                        NonBlockingConnection connection = (NonBlockingConnection) key.attachment();

                        key.channel().register(_selector, 0);

                        toBeScheduled.add(connection);
                        _unscheduledConnections.remove(connection);
                    }

                }
                selectionKeys.clear();

                while (_unregisteredConnections.peek() != null)
                {
                    NonBlockingConnection unregisteredConnection = _unregisteredConnections.poll();
                    _unscheduledConnections.add(unregisteredConnection);


                    final int ops = (unregisteredConnection.canRead() ? SelectionKey.OP_READ : 0)
                                    | (unregisteredConnection.waitingForWrite() ? SelectionKey.OP_WRITE : 0);
                    unregisteredConnection.getSocketChannel().register(_selector, ops, unregisteredConnection);

                }

                long currentTime = System.currentTimeMillis();
                Iterator<NonBlockingConnection> iterator = _unscheduledConnections.iterator();
                nextTimeout = Integer.MAX_VALUE;
                while (iterator.hasNext())
                {
                    NonBlockingConnection connection = iterator.next();

                    int period = connection.getTicker().getTimeToNextTick(currentTime);

                    if (period <= 0 || connection.isStateChanged())
                    {
                        toBeScheduled.add(connection);
                        connection.getSocketChannel().register(_selector, 0).cancel();
                        iterator.remove();
                    }
                    else
                    {
                        nextTimeout = Math.min(period, nextTimeout);
                    }
                }

                for (NonBlockingConnection connection : toBeScheduled)
                {
                    _scheduler.schedule(connection);
                }

            }
        }
        catch (IOException e)
        {
            //TODO
            e.printStackTrace();
        }
        finally
        {
            try
            {
                _selector.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
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

    private class NetworkConnectionScheduler
    {
        private final ScheduledThreadPoolExecutor _executor;
        private final AtomicInteger _running = new AtomicInteger();
        private final int _poolSize;

        private NetworkConnectionScheduler()
        {
            _poolSize = Runtime.getRuntime().availableProcessors();
            _executor = new ScheduledThreadPoolExecutor(_poolSize);
            _executor.prestartAllCoreThreads();
        }

        public void processConnection(final NonBlockingConnection connection)
        {
            try
            {
                _running.incrementAndGet();
                boolean rerun;
                do
                {
                    rerun = false;
                    boolean closed = connection.doWork();

                    if (!closed)
                    {

                        if (connection.isStateChanged())
                        {
                            if (_running.get() == _poolSize)
                            {
                                schedule(connection);
                            }
                            else
                            {
                                rerun = true;
                            }
                        }
                        else
                        {
                            SelectorThread.this.addConnection(connection);
                        }
                    }

                } while (rerun);
            }
            finally
            {
                _running.decrementAndGet();
            }
        }

        public void schedule(final NonBlockingConnection connection)
        {
            _executor.submit(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    String currentName = Thread.currentThread().getName();
                                    try
                                    {
                                        Thread.currentThread().setName(
                                                IO_THREAD_NAME_PREFIX + connection.getRemoteAddress().toString());
                                        processConnection(connection);
                                    }
                                    finally
                                    {
                                        Thread.currentThread().setName(currentName);
                                    }
                                }
                            });
        }

        public void close()
        {
            _executor.shutdown();
        }



    }
}
