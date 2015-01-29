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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
* Created by keith on 28/01/2015.
*/
public class SelectorThread extends Thread
{

    private final Queue<NonBlockingConnection> _unregisteredConnections = new ConcurrentLinkedQueue<>();
    private final Set<NonBlockingConnection> _unscheduledConnections = new HashSet<>();
    private Selector _selector;
    private final AtomicBoolean _closed = new AtomicBoolean();
    private final NetworkConnectionScheduler _scheduler = new NetworkConnectionScheduler();

    SelectorThread(final String name)
    {

        super("SelectorThread-"+name);
        try
        {
            _selector = Selector.open();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to create selector");
        }
    }

    @Override
    public void run()
    {

        long nextTimeout = 0;

        try
        {
            try (Selector selector = Selector.open())
            {
                _selector = selector;
                while (!_closed.get())
                {

                    _selector.select(nextTimeout);

                    List<NonBlockingConnection> toBeScheduled = new ArrayList<>();


                    Set<SelectionKey> selectionKeys = _selector.selectedKeys();
                    for (SelectionKey key : selectionKeys)
                    {
                        NonBlockingConnection connection = (NonBlockingConnection) key.attachment();

                        key.channel().register(_selector, 0);

                        toBeScheduled.add(connection);
                        _unscheduledConnections.remove(connection);

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
                        if (period < 0 || connection.isStateChanged())
                        {
                            toBeScheduled.add(connection);
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
        }
        catch (IOException e)
        {
            //TODO
            e.printStackTrace();
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
    }

    private class NetworkConnectionScheduler
    {
        public void schedule(final NonBlockingConnection connection)
        {


            boolean closed = connection.doWork();

            if (!closed)
            {
                if (connection.isStateChanged())
                {
                    schedule(connection);
                }
                else
                {
                    SelectorThread.this.addConnection(connection);
                }
            }
        }
    }

}
