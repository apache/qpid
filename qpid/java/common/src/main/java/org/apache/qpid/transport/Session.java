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
package org.apache.qpid.transport;


import org.apache.qpid.transport.network.Frame;

import org.apache.qpid.transport.util.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.qpid.transport.Option.*;
import static org.apache.qpid.transport.util.Functions.*;
import static org.apache.qpid.util.Serial.*;
import static org.apache.qpid.util.Strings.*;

/**
 * Session
 *
 * @author Rafael H. Schloming
 */

public class Session extends SessionInvoker
{

    private static final Logger log = Logger.get(Session.class);

    class DefaultSessionListener implements SessionListener
    {

        public void opened(Session ssn) {}

        public void message(Session ssn, MessageTransfer xfr)
        {
            log.info("message: %s", xfr);
        }

        public void exception(Session ssn, SessionException exc)
        {
            throw exc;
        }

        public void closed(Session ssn) {}
    }

    public static final int UNLIMITED_CREDIT = 0xFFFFFFFF;

    private static boolean ENABLE_REPLAY = false;

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

    private Connection connection;
    private Binary name;
    private int channel;
    private SessionDelegate delegate = new SessionDelegate();
    private SessionListener listener = new DefaultSessionListener();
    private long timeout = 60000;
    private boolean autoSync = false;

    // incoming command count
    int commandsIn = 0;
    // completed incoming commands
    private final Object processedLock = new Object();
    private RangeSet processed = new RangeSet();
    private int maxProcessed = commandsIn - 1;
    private int syncPoint = maxProcessed;

    // outgoing command count
    private int commandsOut = 0;
    private Map<Integer,Method> commands = new HashMap<Integer,Method>();
    private int maxComplete = commandsOut - 1;
    private boolean needSync = false;

    private AtomicBoolean closed = new AtomicBoolean(false);

    Session(Connection connection, Binary name)
    {
        this.connection = connection;
        this.name = name;
    }

    public Binary getName()
    {
        return name;
    }

    int getChannel()
    {
        return channel;
    }

    void setChannel(int channel)
    {
        this.channel = channel;
    }

    public void setSessionListener(SessionListener listener)
    {
        if (listener == null)
        {
            this.listener = new DefaultSessionListener();
        }
        else
        {
            this.listener = listener;
        }
    }

    public SessionListener getSessionListener()
    {
        return listener;
    }

    public void setAutoSync(boolean value)
    {
        synchronized (commands)
        {
            this.autoSync = value;
        }
    }

    public Map<Integer,Method> getOutstandingCommands()
    {
        return commands;
    }

    public int getCommandsOut()
    {
        return commandsOut;
    }

    public int getCommandsIn()
    {
        return commandsIn;
    }

    public int nextCommandId()
    {
        return commandsIn++;
    }

    final void identify(Method cmd)
    {
        int id = nextCommandId();
        cmd.setId(id);

        if(log.isDebugEnabled())
        {
            log.debug("ID: [%s] %s", this.channel, id);
        }

        //if ((id % 65536) == 0)
        if ((id & 0xff) == 0)
        {
            flushProcessed(TIMELY_REPLY);
        }
    }

    public void processed(Method command)
    {
        processed(command.getId());
    }

    public void processed(int command)
    {
        processed(new Range(command, command));
    }

    public void processed(int lower, int upper)
    {

        processed(new Range(lower, upper));
    }

    public void processed(Range range)
    {
        log.debug("%s processed(%s)", this, range);

        boolean flush;
        synchronized (processedLock)
        {
            processed.add(range);
            Range first = processed.getFirst();
            int lower = first.getLower();
            int upper = first.getUpper();
            int old = maxProcessed;
            if (le(lower, maxProcessed + 1))
            {
                maxProcessed = max(maxProcessed, upper);
            }
            boolean synced = ge(maxProcessed, syncPoint);
            flush = lt(old, syncPoint) && synced;
            if (synced)
            {
                syncPoint = maxProcessed;
            }
        }
        if (flush)
        {
            flushProcessed();
        }
    }

    public void flushProcessed(Option ... options)
    {
        RangeSet copy;
        synchronized (processedLock)
        {
            copy = processed.copy();
        }
        sessionCompleted(copy, options);
    }

    void knownComplete(RangeSet kc)
    {
        synchronized (processedLock)
        {
            RangeSet newProcessed = new RangeSet();
            for (Range pr : processed)
            {
                for (Range kr : kc)
                {
                    for (Range r : pr.subtract(kr))
                    {
                        newProcessed.add(r);
                    }
                }
            }
            this.processed = newProcessed;
        }
    }

    void syncPoint()
    {
        int id = getCommandsIn() - 1;
        log.debug("%s synced to %d", this, id);
        boolean flush;
        synchronized (processedLock)
        {
            syncPoint = id;
            flush = ge(maxProcessed, syncPoint);
        }
        if (flush)
        {
            flushProcessed();
        }
    }

    public Method getCommand(int id)
    {
        synchronized (commands)
        {
            return commands.get(id);
        }
    }

    boolean complete(int lower, int upper)
    {
        //avoid autoboxing
        if(log.isDebugEnabled())
        {
            log.debug("%s complete(%d, %d)", this, lower, upper);
        }
        synchronized (commands)
        {
            int old = maxComplete;
            for (int id = max(maxComplete, lower); le(id, upper); id++)
            {
                commands.remove(id);
            }
            if (le(lower, maxComplete + 1))
            {
                maxComplete = max(maxComplete, upper);
            }
            log.debug("%s   commands remaining: %s", this, commands);
            commands.notifyAll();
            return gt(maxComplete, old);
        }
    }

    void received(Method m)
    {
        m.delegate(this, delegate);
    }

    private void send(Method m)
    {
        m.setChannel(channel);
        connection.send(m);

        if (!m.isBatch())
        {
            connection.flush();
        }
    }

    public void invoke(Method m)
    {
        if (closed.get())
        {
            ExecutionException exc = getException();
            if (exc != null)
            {
                throw new SessionException(exc);
            }
            else if (close != null)
            {
                throw new ConnectionException(close);
            }
            else
            {
                throw new SessionClosedException();
            }
        }

        if (m.getEncodedTrack() == Frame.L4)
        {
            synchronized (commands)
            {
                int next = commandsOut++;
                m.setId(next);
                if (next == 0)
                {
                    sessionCommandPoint(0, 0);
                }
                if (ENABLE_REPLAY)
                {
                    commands.put(next, m);
                }
                if (autoSync)
                {
                    m.setSync(true);
                }
                needSync = !m.isSync();
                send(m);
                if (autoSync)
                {
                    sync();
                }

                // flush every 64K commands to avoid ambiguity on
                // wraparound
                if ((next % 65536) == 0)
                {
                    sessionFlush(COMPLETED);
                }
            }
        }
        else
        {
            send(m);
        }
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
            int point = commandsOut - 1;

            if (needSync && lt(maxComplete, point))
            {
                executionSync(SYNC);
            }

            long start = System.currentTimeMillis();
            long elapsed = 0;
            while (!closed.get() && elapsed < timeout && lt(maxComplete, point))
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

            if (lt(maxComplete, point))
            {
                if (closed.get())
                {
                    throw new SessionException(getException());
                }
                else
                {
                    throw new RuntimeException
                        (String.format
                         ("timed out waiting for sync: complete = %s, point = %s", maxComplete, point));
                }
            }
        }
    }

    private Map<Integer,ResultFuture<?>> results =
        new HashMap<Integer,ResultFuture<?>>();
    private ExecutionException exception = null;

    void result(int command, Struct result)
    {
        ResultFuture<?> future;
        synchronized (results)
        {
            future = results.remove(command);
        }
        future.set(result);
    }

    void setException(ExecutionException exc)
    {
        synchronized (results)
        {
            if (exception != null)
            {
                throw new IllegalStateException
                    (String.format
                     ("too many exceptions: %s, %s", exception, exc));
            }
            exception = exc;
        }
    }

    private ConnectionClose close = null;

    void closeCode(ConnectionClose close)
    {
        this.close = close;
    }

    ExecutionException getException()
    {
        synchronized (results)
        {
            return exception;
        }
    }

    protected <T> Future<T> invoke(Method m, Class<T> klass)
    {
        synchronized (commands)
        {
            int command = commandsOut;
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
                throw new SessionException(getException());
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

    public final void messageTransfer(String destination,
                                      MessageAcceptMode acceptMode,
                                      MessageAcquireMode acquireMode,
                                      Header header,
                                      byte[] body,
                                      Option ... _options) {
        messageTransfer(destination, acceptMode, acquireMode, header,
                        ByteBuffer.wrap(body), _options);
    }

    public final void messageTransfer(String destination,
                                      MessageAcceptMode acceptMode,
                                      MessageAcquireMode acquireMode,
                                      Header header,
                                      String body,
                                      Option ... _options) {
        messageTransfer(destination, acceptMode, acquireMode, header,
                        toUTF8(body), _options);
    }

    public void close()
    {
        sessionRequestTimeout(0);
        sessionDetach(name.getBytes());
        synchronized (commands)
        {
            long start = System.currentTimeMillis();
            long elapsed = 0;
            try
            {
                while (!closed.get() && elapsed < timeout)
                {
                    commands.wait(timeout - elapsed);
                    elapsed = System.currentTimeMillis() - start;
                }

                if (!closed.get())
                {
                    throw new SessionException("close() timed out");
                }
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

        connection.removeSession(this);
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

    public String toString()
    {
        return String.format("ssn:%s", name);
    }

}
