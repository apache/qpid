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
import org.apache.qpid.transport.util.Waiter;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.qpid.transport.Option.*;
import static org.apache.qpid.transport.Session.State.*;
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

    enum State { NEW, DETACHED, OPEN, CLOSING, CLOSED }

    class DefaultSessionListener implements SessionListener
    {

        public void opened(Session ssn) {}

        public void message(Session ssn, MessageTransfer xfr)
        {
            log.info("message: %s", xfr);
        }

        public void exception(Session ssn, SessionException exc)
        {
            log.error(exc, "session exception");
        }

        public void closed(Session ssn) {}
    }

    public static final int UNLIMITED_CREDIT = 0xFFFFFFFF;

    private Connection connection;
    private Binary name;
    private long expiry;
    private int channel;
    private SessionDelegate delegate = new SessionDelegate();
    private SessionListener listener = new DefaultSessionListener();
    private long timeout = 60000;
    private boolean autoSync = false;

    private boolean incomingInit;
    // incoming command count
    private int commandsIn;
    // completed incoming commands
    private final Object processedLock = new Object();
    private RangeSet processed;
    private int maxProcessed;
    private int syncPoint;

    // outgoing command count
    private int commandsOut = 0;
    private Method[] commands = new Method[64*1024];
    private int maxComplete = commandsOut - 1;
    private boolean needSync = false;

    private State state = NEW;

    Session(Connection connection, Binary name, long expiry)
    {
        this.connection = connection;
        this.name = name;
        this.expiry = expiry;
        initReceiver();
    }

    public Connection getConnection()
    {
        return connection;
    }

    public Binary getName()
    {
        return name;
    }

    void setExpiry(long expiry)
    {
        this.expiry = expiry;
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

    void setState(State state)
    {
        synchronized (commands)
        {
            this.state = state;
            commands.notifyAll();
        }
    }

    private void initReceiver()
    {
        synchronized (processedLock)
        {
            incomingInit = false;
            processed = new RangeSet();
        }
    }

    void attach()
    {
        initReceiver();
        sessionAttach(name.getBytes());
        sessionRequestTimeout(expiry);
    }

    void resume()
    {
        synchronized (commands)
        {
            for (int i = maxComplete + 1; lt(i, commandsOut); i++)
            {
                Method m = commands[mod(i, commands.length)];
                if (m != null)
                {
                    sessionCommandPoint(m.getId(), 0);
                    send(m);
                }
            }

            sessionCommandPoint(commandsOut, 0);
            sessionFlush(COMPLETED);
        }
    }

    void dump()
    {
        synchronized (commands)
        {
            for (Method m : commands)
            {
                if (m != null)
                {
                    System.out.println(m);
                }
            }
        }
    }

    final void commandPoint(int id)
    {
        synchronized (processedLock)
        {
            this.commandsIn = id;
            if (!incomingInit)
            {
                incomingInit = true;
                maxProcessed = commandsIn - 1;
                syncPoint = maxProcessed;
            }
        }
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
        if (!incomingInit)
        {
            throw new IllegalStateException();
        }

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
        log.debug("%s processed(%s) %s %s", this, range, syncPoint, maxProcessed);

        boolean flush;
        synchronized (processedLock)
        {
            log.debug("%s", processed);
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
                commands[mod(id, commands.length)] = null;
            }
            if (le(lower, maxComplete + 1))
            {
                maxComplete = max(maxComplete, upper);
            }
            log.debug("%s   commands remaining: %s", this, commandsOut - maxComplete);
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

    final private boolean isFull(int id)
    {
        return id - maxComplete >= commands.length;
    }

    public void invoke(Method m)
    {
        if (m.getEncodedTrack() == Frame.L4)
        {
            synchronized (commands)
            {
                if (state != OPEN && state != CLOSED)
                {
                    Waiter w = new Waiter(commands, timeout);
                    while (w.hasTime() && (state != OPEN && state != CLOSED))
                    {
                        w.await();
                    }
                }

                switch (state)
                {
                case OPEN:
                    break;
                case CLOSED:
                    throw new SessionClosedException();
                default:
                    throw new SessionException
                        (String.format
                         ("timed out waiting for session to become open " +
                          "(state=%s)", state));
                }

                int next = commandsOut++;
                m.setId(next);

                if (isFull(next))
                {
                    Waiter w = new Waiter(commands, timeout);
                    while (w.hasTime() && isFull(next))
                    {
                        if (state == OPEN)
                        {
                            try
                            {
                                sessionFlush(COMPLETED);
                            }
                            catch (SenderException e)
                            {
                                if (expiry > 0)
                                {
                                    // if expiry is > 0 then this will
                                    // happen again on resume
                                    log.error(e, "error sending flush (full replay buffer)");
                                }
                                else
                                {
                                    e.rethrow();
                                }
                            }
                        }
                        w.await();
                    }
                }

                if (isFull(next))
                {
                    throw new SessionException("timed out waiting for completion");
                }

                if (next == 0)
                {
                    sessionCommandPoint(0, 0);
                }
                if (expiry > 0)
                {
                    commands[mod(next, commands.length)] = m;
                }
                if (autoSync)
                {
                    m.setSync(true);
                }
                needSync = !m.isSync();
                try
                {
                    send(m);
                }
                catch (SenderException e)
                {
                    if (expiry > 0)
                    {
                        // if expiry is > 0 then this will happen
                        // again on resume
                        log.error(e, "error sending command");
                    }
                    else
                    {
                        e.rethrow();
                    }
                }
                if (autoSync)
                {
                    sync();
                }

                // flush every 64K commands to avoid ambiguity on
                // wraparound
                if ((next % 65536) == 0)
                {
                    try
                    {
                        sessionFlush(COMPLETED);
                    }
                    catch (SenderException e)
                    {
                        if (expiry > 0)
                        {
                            // if expiry is > 0 then this will happen
                            // again on resume
                            log.error(e, "error sending flush (periodic)");
                        }
                        else
                        {
                            e.rethrow();
                        }
                    }
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

            Waiter w = new Waiter(commands, timeout);
            while (w.hasTime() && state != CLOSED && lt(maxComplete, point))
            {
                log.debug("%s   waiting for[%d]: %d, %s", this, point,
                          maxComplete, commands);
                w.await();
            }

            if (lt(maxComplete, point))
            {
                if (state == CLOSED)
                {
                    throw new SessionException(getException());
                }
                else
                {
                    throw new SessionException
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
                Waiter w = new Waiter(this, timeout);
                while (w.hasTime() && state != CLOSED && !isDone())
                {
                    log.debug("%s waiting for result: %s", Session.this, this);
                    w.await();
                }
            }

            if (isDone())
            {
                return result;
            }
            else if (state == CLOSED)
            {
                throw new SessionException(getException());
            }
            else
            {
                throw new SessionException
                    (String.format("%s timed out waiting for result: %s",
                                   Session.this, this));
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
        synchronized (commands)
        {
            state = CLOSING;
            sessionRequestTimeout(0);
            sessionDetach(name.getBytes());
            Waiter w = new Waiter(commands, timeout);
            while (w.hasTime() && state != CLOSED)
            {
                w.await();
            }

            if (state != CLOSED)
            {
                throw new SessionException("close() timed out");
            }

            connection.removeSession(this);
        }
    }

    public void exception(Throwable t)
    {
        log.error(t, "caught exception");
    }

    public void closed()
    {
        synchronized (commands)
        {
            if (expiry == 0)
            {
                state = CLOSED;
            }
            else
            {
                state = DETACHED;
            }

            commands.notifyAll();

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

    public String toString()
    {
        return String.format("ssn:%s", name);
    }

}
