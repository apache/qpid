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
package org.apache.qpidity.transport;


import org.apache.qpidity.transport.network.Frame;

import org.apache.qpidity.transport.util.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.qpidity.transport.Option.*;

/**
 * Session
 *
 * @author Rafael H. Schloming
 */

public class Session extends Invoker
{
    static
    {
        String enableReplay = "enable_command_replay";
        try
        {
            ENABLE_REPLAY  = new Boolean(System.getProperties().getProperty(enableReplay, "false"));
        }
        catch (Exception e)
        {
            ENABLE_REPLAY = false;
        }
    }
    private static boolean ENABLE_REPLAY = false;
    private static final Logger log = Logger.get(Session.class);

    private byte[] name;
    private long timeout = 60000;

    // channel may be null
    Channel channel;

    // incoming command count
    long commandsIn = 0;
    // completed incoming commands
    private final RangeSet processed = new RangeSet();
    private Range syncPoint = null;

    // outgoing command count
    private long commandsOut = 0;
    private Map<Long,Method> commands = new HashMap<Long,Method>();
    private long maxComplete = -1;

    private AtomicBoolean closed = new AtomicBoolean(false);

    public Session(byte[] name)
    {
        this.name = name;
    }

    public byte[] getName()
    {
        return name;
    }

    public Map<Long,Method> getOutstandingCommands()
    {
        return commands;
    }

    public long getCommandsOut()
    {
        return commandsOut;
    }

    public long getCommandsIn()
    {
        return commandsIn;
    }

    public long nextCommandId()
    {
        return commandsIn++;
    }

    public RangeSet getProcessed()
    {
        return processed;
    }

    public void processed(Method command)
    {
        processed(command.getId());
    }

    public void processed(long command)
    {
        processed(new Range(command, command));
    }

    public void processed(long lower, long upper)
    {

        processed(new Range(lower, upper));
    }

    public void processed(Range range)
    {
        log.debug("%s processed(%s)", this, range);

        boolean flush;
        synchronized (processed)
        {
            processed.add(range);
            flush = syncPoint != null && processed.includes(syncPoint);
        }
        if (flush)
        {
            flushProcessed();
        }
    }

   public void flushProcessed()
    {
        RangeSet copy;
        synchronized (processed)
        {
            copy = processed.copy();
        }
        sessionCompleted(copy);
    }

    void syncPoint()
    {
        long id = getCommandsIn() - 1;
        log.debug("%s synced to %d", this, id);
        Range range = new Range(0, id - 1);
        boolean flush;
        synchronized (processed)
        {
            flush = processed.includes(range);
            if (!flush)
            {
                syncPoint = range;
            }
        }
        if (flush)
        {
            flushProcessed();
        }
    }

    public void attach(Channel channel)
    {
        this.channel = channel;
        channel.setSession(this);
    }

    public Method getCommand(long id)
    {
        synchronized (commands)
        {
            return commands.get(id);
        }
    }

    void complete(long lower, long upper)
    {
        log.debug("%s complete(%d, %d)", this, lower, upper);
        synchronized (commands)
        {
            for (long id = maxComplete; id <= upper; id++)
            {
                commands.remove(id);
            }
            if (lower <= maxComplete + 1)
            {
                maxComplete = Math.max(maxComplete, upper);
            }
            commands.notifyAll();
            log.debug("%s   commands remaining: %s", this, commands);
        }
    }

    protected void invoke(Method m)
    {
        if (m.getEncodedTrack() == Frame.L4)
        {
            synchronized (commands)
            {
                long next = commandsOut++;
                if (next == 0)
                {
                    sessionCommandPoint(0, 0);
                }
                if (ENABLE_REPLAY)
                {
                    commands.put(next, m);
                }
                channel.method(m);
            }
        }
        else
        {
            channel.method(m);
        }
    }

    public void header(Header header)
    {
        channel.header(header);
    }

    public Header header(List<Struct> structs)
    {
        Header res = new Header(structs, false);
        header(res);
        return res;
    }

    public Header header(Struct ... structs)
    {
        return header(Arrays.asList(structs));
    }

    public void data(ByteBuffer buf)
    {
        channel.data(buf);
    }

    public void data(String str)
    {
        channel.data(str);
    }

    public void data(byte[] bytes)
    {
        channel.data(bytes);
    }

    public void endData()
    {
        channel.end();
    }

    public void sync()
    {
        sync(timeout);
    }

    public void sync(long timeout)
    {
        log.debug("%s sync()", this);
        synchronized (commands)
        {
            long point = commandsOut - 1;

            if (maxComplete < point)
            {
                ExecutionSync sync = new ExecutionSync();
                sync.setSync(true);
                invoke(sync);
            }

            long start = System.currentTimeMillis();
            long elapsed = 0;
            while (!closed.get() && elapsed < timeout && maxComplete < point)
            {
                try {
                    log.debug("%s   waiting for[%d]: %d, %s", this, point,
                              maxComplete, commands);
                    commands.wait(timeout - elapsed);
                    elapsed = System.currentTimeMillis() - start;
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }

            if (maxComplete < point)
            {
                if (closed.get())
                {
                    throw new RuntimeException("session closed");
                }
                else
                {
                    throw new RuntimeException("timed out waiting for sync");
                }
            }
        }
    }

    private Map<Long,ResultFuture<?>> results =
        new HashMap<Long,ResultFuture<?>>();

    void result(long command, Struct result)
    {
        ResultFuture<?> future;
        synchronized (results)
        {
            future = results.remove(command);
        }
        future.set(result);
    }

    protected <T> Future<T> invoke(Method m, Class<T> klass)
    {
        synchronized (commands)
        {
            long command = commandsOut;
            ResultFuture<T> future = new ResultFuture<T>(klass);
            synchronized (results)
            {
                results.put(command, future);
            }
            invoke(m);
            return future;
        }
    }

    private class ResultFuture<T> implements Future<T>
    {

        private final Class<T> klass;
        private T result;

        private ResultFuture(Class<T> klass)
        {
            this.klass = klass;
        }

        private void set(Struct result)
        {
            synchronized (this)
            {
                this.result = klass.cast(result);
                notifyAll();
            }
        }

        public T get(long timeout)
        {
            synchronized (this)
            {
                long start = System.currentTimeMillis();
                long elapsed = 0;
                while (!closed.get() && timeout - elapsed > 0 && !isDone())
                {
                    try
                    {
                        log.debug("%s waiting for result: %s", Session.this, this);
                        wait(timeout - elapsed);
                        elapsed = System.currentTimeMillis() - start;
                    }
                    catch (InterruptedException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (isDone())
            {
                return result;
            }
            else if (closed.get())
            {
                throw new RuntimeException("session closed");
            }
            else
            {
                return null;
            }
        }

        public T get()
        {
            return get(timeout);
        }

        public boolean isDone()
        {
            return result != null;
        }

        public String toString()
        {
            return String.format("Future(%s)", isDone() ? result : klass);
        }

    }

    public void close()
    {
        sessionRequestTimeout(0);
        sessionDetach(name);
        // XXX: channel.close();
    }

    public void exception(Throwable t)
    {
        log.error(t, "caught exception");
    }

    public void closed()
    {
        closed.set(true);
        synchronized (commands)
        {
            commands.notifyAll();
        }
        synchronized (results)
        {
            for (ResultFuture<?> result : results.values())
            {
                synchronized(result)
                {
                    result.notifyAll();
                }
            }
        }
    }

}
