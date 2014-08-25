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
package org.apache.qpid.server.protocol.v0_10;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CONNECTION_FORMAT;
import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.SOCKET_FORMAT;
import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.USER_FORMAT;

import java.net.SocketAddress;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.Subject;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.SessionModelListener;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionCloseCode;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.ExecutionException;
import org.apache.qpid.transport.Method;
import org.apache.qpid.transport.ProtocolEvent;
import org.apache.qpid.transport.Session;

public class ServerConnection extends Connection implements AMQConnectionModel<ServerConnection, ServerSession>,
                                                            LogSubject, AuthorizationHolder
{

    private final Broker _broker;
    private Runnable _onOpenTask;
    private AtomicBoolean _logClosed = new AtomicBoolean(false);

    private final Subject _authorizedSubject = new Subject();
    private Principal _authorizedPrincipal = null;
    private final StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;
    private final long _connectionId;
    private final Object _reference = new Object();
    private VirtualHostImpl<?,?,?> _virtualHost;
    private Port<?> _port;
    private AtomicLong _lastIoTime = new AtomicLong();
    private boolean _blocking;
    private Transport _transport;

    private final CopyOnWriteArrayList<Action<? super ServerConnection>> _taskList =
            new CopyOnWriteArrayList<Action<? super ServerConnection>>();

    private final CopyOnWriteArrayList<SessionModelListener> _sessionListeners =
            new CopyOnWriteArrayList<SessionModelListener>();

    private volatile boolean _stopped;
    private int _messageCompressionThreshold;

    public ServerConnection(final long connectionId, Broker broker)
    {
        _connectionId = connectionId;
        _authorizedSubject.getPrincipals().add(new ConnectionPrincipal(this));
        _broker = broker;

        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getConnectionId());
        _dataDelivered = new StatisticsCounter("data-delivered-" + getConnectionId());
        _messagesReceived = new StatisticsCounter("messages-received-" + getConnectionId());
        _dataReceived = new StatisticsCounter("data-received-" + getConnectionId());
    }

    public Object getReference()
    {
        return _reference;
    }

    @Override
    protected void invoke(Method method)
    {
        super.invoke(method);
    }

    EventLogger getEventLogger()
    {
        return _virtualHost == null ? _broker.getEventLogger() : _virtualHost.getEventLogger();
    }

    @Override
    protected void setState(State state)
    {
        super.setState(state);

        if (state == State.OPEN)
        {
            if (_onOpenTask != null)
            {
                _onOpenTask.run();
            }
            getEventLogger().message(ConnectionMessages.OPEN(getClientId(),
                                                             "0-10",
                                                             getClientVersion(),
                                                             getClientProduct(),
                                                             true,
                                                             true,
                                                             true,
                                                             true));

            getVirtualHost().getConnectionRegistry().registerConnection(this);
        }

        if (state == State.CLOSE_RCVD || state == State.CLOSED || state == State.CLOSING)
        {
            if(_virtualHost != null)
            {
                _virtualHost.getConnectionRegistry().deregisterConnection(this);
            }
        }

        if (state == State.CLOSED)
        {
            logClosed();
        }
    }

    protected void logClosed()
    {
        if(_logClosed.compareAndSet(false, true))
        {
            getEventLogger().message(this, ConnectionMessages.CLOSE());
        }
    }

    @Override
    public ServerConnectionDelegate getConnectionDelegate()
    {
        return (ServerConnectionDelegate) super.getConnectionDelegate();
    }

    public void setConnectionDelegate(ServerConnectionDelegate delegate)
    {
        super.setConnectionDelegate(delegate);
    }

    public VirtualHostImpl<?,?,?> getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHostImpl<?,?,?> virtualHost)
    {
        _virtualHost = virtualHost;
        _messageCompressionThreshold =
                virtualHost.getContextValue(Integer.class,
                                            Broker.MESSAGE_COMPRESSION_THRESHOLD_SIZE);

        if(_messageCompressionThreshold <= 0)
        {
            _messageCompressionThreshold = Integer.MAX_VALUE;
        }
    }

    @Override
    public String getVirtualHostName()
    {
        return _virtualHost == null ? null : _virtualHost.getName();
    }

    @Override
    public Port<?> getPort()
    {
        return _port;
    }

    public void setPort(Port<?> port)
    {
        _port = port;
    }

    @Override
    public Transport getTransport()
    {
        return _transport;
    }

    @Override
    public void stop()
    {
        _stopped = true;
    }

    @Override
    public boolean isStopped()
    {
        return _stopped;
    }

    public void setTransport(Transport transport)
    {
        _transport = transport;
    }

    public void onOpen(final Runnable task)
    {
        _onOpenTask = task;
    }

    public void closeSession(ServerSession session, AMQConstant cause, String message)
    {
        ExecutionException ex = new ExecutionException();
        ExecutionErrorCode code = ExecutionErrorCode.INTERNAL_ERROR;
        try
        {
	        code = ExecutionErrorCode.get(cause.getCode());
        }
        catch (IllegalArgumentException iae)
        {
            // Ignore, already set to INTERNAL_ERROR
        }
        ex.setErrorCode(code);
        ex.setDescription(message);
        session.invoke(ex);

        session.close(cause, message);
    }

    public LogSubject getLogSubject()
    {
        return this;
    }

    @Override
    public void exception(final Throwable t)
    {
        try
        {
            super.exception(t);
        }
        finally
        {
            if(t instanceof Error)
            {
                throw (Error) t;
            }
            if(t instanceof ServerScopedRuntimeException)
            {
                throw (ServerScopedRuntimeException) t;
            }
        }
    }

    @Override
    public void received(final ProtocolEvent event)
    {
        _lastIoTime.set(System.currentTimeMillis());
        Subject subject;
        if (event.isConnectionControl())
        {
            subject = _authorizedSubject;
        }
        else
        {
            ServerSession channel = (ServerSession) getSession(event.getChannel());
            if (channel != null)
            {
                subject = channel.getAuthorizedSubject();
            }
            else
            {
                subject = _authorizedSubject;
            }
        }

        Subject.doAs(subject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                ServerConnection.super.received(event);
                return null;
            }
        });

    }

    public String toLogString()
    {
        boolean hasVirtualHost = (null != this.getVirtualHost());
        boolean hasClientId = (null != getClientId());

        if (hasClientId && hasVirtualHost)
        {
            return "[" +
                    MessageFormat.format(CONNECTION_FORMAT,
                                         getConnectionId(),
                                         getClientId(),
                                         getRemoteAddressString(),
                                         getVirtualHost().getName())
                 + "] ";
        }
        else if (hasClientId)
        {
            return "[" +
                    MessageFormat.format(USER_FORMAT,
                                         getConnectionId(),
                                         getClientId(),
                                         getRemoteAddressString())
                 + "] ";

        }
        else
        {
            return "[" +
                    MessageFormat.format(SOCKET_FORMAT,
                                         getConnectionId(),
                                         getRemoteAddressString())
                 + "] ";
        }
    }

    public void close(AMQConstant cause, String message)
    {
        closeSubscriptions();
        performDeleteTasks();
        ConnectionCloseCode replyCode = ConnectionCloseCode.NORMAL;
        try
        {
            replyCode = ConnectionCloseCode.get(cause.getCode());
        }
        catch (IllegalArgumentException iae)
        {
            // Ignore
        }
        close(replyCode, message);
    }

    protected void performDeleteTasks()
    {
        for(Action<? super ServerConnection> task : _taskList)
        {
            task.performAction(this);
        }
    }

    public synchronized void block()
    {
        if(!_blocking)
        {
            _blocking = true;
            for(AMQSessionModel ssn : getSessionModels())
            {
                ssn.block();
            }
        }
    }

    public synchronized void unblock()
    {
        if(_blocking)
        {
            _blocking = false;
            for(AMQSessionModel ssn : getSessionModels())
            {
                ssn.unblock();
            }
        }
    }

    @Override
    public synchronized void registerSession(final Session ssn)
    {
        super.registerSession(ssn);
        sessionAdded((ServerSession)ssn);
        if(_blocking)
        {
            ((ServerSession)ssn).block();
        }
    }

    @Override
    public synchronized void removeSession(final Session ssn)
    {
        sessionRemoved((ServerSession)ssn);
        super.removeSession(ssn);
    }

    public List<ServerSession> getSessionModels()
    {
        List<ServerSession> sessions = new ArrayList<ServerSession>();
        for (Session ssn : getChannels())
        {
            sessions.add((ServerSession) ssn);
        }
        return sessions;
    }

    public void registerMessageDelivered(long messageSize)
    {
        _messagesDelivered.registerEvent(1L);
        _dataDelivered.registerEvent(messageSize);
        _virtualHost.registerMessageDelivered(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messagesReceived.registerEvent(1L, timestamp);
        _dataReceived.registerEvent(messageSize, timestamp);
        _virtualHost.registerMessageReceived(messageSize, timestamp);
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }

    public void resetStatistics()
    {
        _messagesDelivered.reset();
        _dataDelivered.reset();
        _messagesReceived.reset();
        _dataReceived.reset();
    }

    /**
     * @return authorizedSubject
     */
    public Subject getAuthorizedSubject()
    {
        return _authorizedSubject;
    }

    /**
     * Sets the authorized subject.  It also extracts the UsernamePrincipal from the subject
     * and caches it for optimisation purposes.
     *
     * @param authorizedSubject
     */
    public void setAuthorizedSubject(final Subject authorizedSubject)
    {
        if (authorizedSubject == null)
        {
            _authorizedPrincipal = null;
        }
        else
        {
            _authorizedSubject.getPrincipals().addAll(authorizedSubject.getPrincipals());

            _authorizedPrincipal = AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(authorizedSubject);
        }
    }

    public Principal getAuthorizedPrincipal()
    {
        return _authorizedPrincipal;
    }

    public long getConnectionId()
    {
        return _connectionId;
    }

    public boolean isSessionNameUnique(byte[] name)
    {
        return !super.hasSessionWithName(name);
    }

    public String getRemoteAddressString()
    {
        return String.valueOf(getRemoteAddress());
    }

    @Override
    public String getRemoteProcessPid()
    {
        return getConnectionDelegate().getRemoteProcessPid();
    }

    @Override
    public void closed()
    {
        performDeleteTasks();
        closeSubscriptions();
        super.closed();
    }

    private void closeSubscriptions()
    {
        for (Session ssn : getChannels())
        {
            ((ServerSession)ssn).unregisterSubscriptions();
        }
    }

    public void receivedComplete()
    {
        for (Session ssn : getChannels())
        {
            ((ServerSession)ssn).receivedComplete();
        }
    }

    @Override
    public void send(ProtocolEvent event)
    {
        _lastIoTime.set(System.currentTimeMillis());
        super.send(event);
    }

    public long getLastIoTime()
    {
        return _lastIoTime.longValue();
    }

    @Override
    public String getClientId()
    {
        return getConnectionDelegate().getClientId();
    }

    @Override
    public String getRemoteContainerName()
    {
        return getConnectionDelegate().getClientId();
    }

    @Override
    public void addSessionListener(final SessionModelListener listener)
    {
        _sessionListeners.add(listener);
    }

    @Override
    public void removeSessionListener(final SessionModelListener listener)
    {
        _sessionListeners.remove(listener);
    }

    private void sessionAdded(final AMQSessionModel<?,?> session)
    {
        for(SessionModelListener l : _sessionListeners)
        {
            l.sessionAdded(session);
        }
    }

    private void sessionRemoved(final AMQSessionModel<?,?> session)
    {
        for(SessionModelListener l : _sessionListeners)
        {
            l.sessionRemoved(session);
        }
    }


    @Override
    public String getClientVersion()
    {
        return getConnectionDelegate().getClientVersion();
    }

    @Override
    public String getClientProduct()
    {
        return getConnectionDelegate().getClientProduct();
    }

    public long getSessionCountLimit()
    {
        return getChannelMax();
    }

    public Principal getPeerPrincipal()
    {
        return getNetworkConnection().getPeerPrincipal();
    }

    @Override
    public void setRemoteAddress(SocketAddress remoteAddress)
    {
        super.setRemoteAddress(remoteAddress);
    }

    @Override
    public void setLocalAddress(SocketAddress localAddress)
    {
        super.setLocalAddress(localAddress);
    }

    public void doHeartBeat()
    {
        super.doHeartBeat();
    }

    @Override
    public void addDeleteTask(final Action<? super ServerConnection> task)
    {
        _taskList.add(task);
    }

    @Override
    public void removeDeleteTask(final Action<? super ServerConnection> task)
    {
        _taskList.remove(task);
    }

    public int getMessageCompressionThreshold()
    {
        return _messageCompressionThreshold;
    }
}
