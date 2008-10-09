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

import org.apache.qpid.transport.network.ConnectionBinding;
import org.apache.qpid.transport.network.io.IoTransport;
import org.apache.qpid.transport.util.Logger;
import org.apache.qpid.transport.util.Waiter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.UUID;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslServer;

import static org.apache.qpid.transport.Connection.State.*;


/**
 * Connection
 *
 * @author Rafael H. Schloming
 *
 * @todo the channels map should probably be replaced with something
 * more efficient, e.g. an array or a map implementation that can use
 * short instead of Short
 */

public class Connection
    implements Receiver<ProtocolEvent>, Sender<ProtocolEvent>
{

    enum State { NEW, CLOSED, OPENING, OPEN, CLOSING, CLOSE_RCVD }

    private static final Logger log = Logger.get(Connection.class);

    class DefaultConnectionListener implements ConnectionListener
    {
        public void opened(Connection conn) {}
        public void exception(Connection conn, ConnectionException exception)
        {
            throw exception;
        }
        public void closed(Connection conn) {}
    }

    private ConnectionDelegate delegate;
    private Sender<ProtocolEvent> sender;

    final private Map<Integer,Channel> channels = new HashMap<Integer,Channel>();

    private State state = NEW;
    private Object lock = new Object();
    private long timeout = 60000;
    private ConnectionListener listener = new DefaultConnectionListener();
    private Throwable error = null;

    private int channelMax = 1;
    private String locale;
    private SaslServer saslServer;
    private SaslClient saslClient;

    // want to make this final
    private int _connectionId;

    public Connection() {}

    public void setConnectionDelegate(ConnectionDelegate delegate)
    {
        this.delegate = delegate;
    }

    public void setConnectionListener(ConnectionListener listener)
    {
        if (listener == null)
        {
            this.listener = new DefaultConnectionListener();
        }
        else
        {
            this.listener = listener;
        }
    }

    public Sender<ProtocolEvent> getSender()
    {
        return sender;
    }

    public void setSender(Sender<ProtocolEvent> sender)
    {
        this.sender = sender;
    }

    void setState(State state)
    {
        synchronized (lock)
        {
            this.state = state;
            lock.notifyAll();
        }
    }

    void setLocale(String locale)
    {
        this.locale = locale;
    }

    String getLocale()
    {
        return locale;
    }

    void setSaslServer(SaslServer saslServer)
    {
        this.saslServer = saslServer;
    }

    SaslServer getSaslServer()
    {
        return saslServer;
    }

    void setSaslClient(SaslClient saslClient)
    {
        this.saslClient = saslClient;
    }

    SaslClient getSaslClient()
    {
        return saslClient;
    }

    public void connect(String host, int port, String vhost, String username, String password)
    {
        synchronized (lock)
        {
            state = OPENING;

            delegate = new ClientDelegate(vhost, username, password);

            IoTransport.connect(host, port, ConnectionBinding.get(this));
            send(new ProtocolHeader(1, 0, 10));

            Waiter w = new Waiter(lock, timeout);
            while (w.hasTime() && state == OPENING && error == null)
            {
                w.await();
            }

            if (error != null)
            {
                Throwable t = error;
                error = null;
                close();
                throw new ConnectionException(t);
            }

            switch (state)
            {
            case OPENING:
                close();
                throw new ConnectionException("connect() timed out");
            case OPEN:
                break;
            case CLOSED:
                throw new ConnectionException("connect() aborted");
            default:
                throw new IllegalStateException(String.valueOf(state));
            }
        }

        listener.opened(this);
    }

    public Session createSession()
    {
        return createSession(0);
    }

    public Session createSession(long expiryInSeconds)
    {
        Channel ch = getChannel();
        Session ssn = new Session(UUID.randomUUID().toString().getBytes());
        ssn.attach(ch);
        ssn.sessionAttach(ssn.getName());
        ssn.sessionRequestTimeout(expiryInSeconds);
        return ssn;
    }

    public void setConnectionId(int id)
    {
        _connectionId = id;
    }

    public int getConnectionId()
    {
        return _connectionId;
    }

    public ConnectionDelegate getConnectionDelegate()
    {
        return delegate;
    }

    public void received(ProtocolEvent event)
    {
        log.debug("RECV: [%s] %s", this, event);
        Channel channel = getChannel(event.getChannel());
        channel.received(event);
    }

    public void send(ProtocolEvent event)
    {
        log.debug("SEND: [%s] %s", this, event);
        Sender s = sender;
        if (s == null)
        {
            throw new ConnectionException("connection closed");
        }
        s.send(event);
    }

    public void flush()
    {
        log.debug("FLUSH: [%s]", this);
        sender.flush();
    }

    public int getChannelMax()
    {
        return channelMax;
    }

    void setChannelMax(int max)
    {
        channelMax = max;
    }

    public Channel getChannel()
    {
        synchronized (lock)
        {
            for (int i = 0; i < getChannelMax(); i++)
            {
                if (!channels.containsKey(i))
                {
                    return getChannel(i);
                }
            }

            throw new RuntimeException("no more channels available");
        }
    }

    public Channel getChannel(int number)
    {
        synchronized (lock)
        {
            Channel channel = channels.get(number);
            if (channel == null)
            {
                channel = new Channel(this, number, delegate.getSessionDelegate());
                channels.put(number, channel);
            }
            return channel;
        }
    }

    void removeChannel(int number)
    {
        synchronized (lock)
        {
            channels.remove(number);
        }
    }

    public void exception(ConnectionException e)
    {
        synchronized (lock)
        {
            switch (state)
            {
            case OPENING:
            case CLOSING:
                error = e;
                lock.notifyAll();
                break;
            default:
                listener.exception(this, e);
                break;
            }
        }
    }

    public void exception(Throwable t)
    {
        synchronized (lock)
        {
            switch (state)
            {
            case OPENING:
            case CLOSING:
                error = t;
                lock.notifyAll();
                break;
            default:
                listener.exception(this, new ConnectionException(t));
                break;
            }
        }
    }

    void closeCode(ConnectionClose close)
    {
        synchronized (lock)
        {
            for (Channel ch : channels.values())
            {
                ch.closeCode(close);
            }
            ConnectionCloseCode code = close.getReplyCode();
            if (code != ConnectionCloseCode.NORMAL)
            {
                exception(new ConnectionException(close));
            }
        }
    }

    public void closed()
    {
        log.debug("connection closed: %s", this);

        if (state == OPEN)
        {
            exception(new ConnectionException("connection aborted"));
        }

        synchronized (lock)
        {
            List<Channel> values = new ArrayList<Channel>(channels.values());
            for (Channel ch : values)
            {
                ch.closed();
            }

            sender = null;
            setState(CLOSED);
        }

        listener.closed(this);
    }

    public void close()
    {
        synchronized (lock)
        {
            switch (state)
            {
            case OPEN:
                Channel ch = getChannel(0);
                state = CLOSING;
                ch.connectionClose(ConnectionCloseCode.NORMAL, null);
                Waiter w = new Waiter(lock, timeout);
                while (w.hasTime() && state == CLOSING && error == null)
                {
                    w.await();
                }

                if (error != null)
                {
                    close();
                    throw new ConnectionException(error);
                }

                switch (state)
                {
                case CLOSING:
                    close();
                    throw new ConnectionException("close() timed out");
                case CLOSED:
                    break;
                default:
                    throw new IllegalStateException(String.valueOf(state));
                }
                break;
            case CLOSED:
                break;
            default:
                if (sender != null)
                {
                    sender.close();
                    w = new Waiter(lock, timeout);
                    while (w.hasTime() && sender != null && error == null)
                    {
                        w.await();
                    }

                    if (error != null)
                    {
                        throw new ConnectionException(error);
                    }

                    if (sender != null)
                    {
                        throw new ConnectionException("close() timed out");
                    }
                }
                break;
            }
        }
    }

    public String toString()
    {
        return String.format("conn:%x", System.identityHashCode(this));
    }

}
