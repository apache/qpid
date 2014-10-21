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
package org.apache.qpid.server.protocol.v1_0;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CONNECTION_FORMAT;

import java.net.SocketAddress;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.security.auth.Subject;

import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.ConnectionEventListener;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SessionEndpoint;
import org.apache.qpid.amqp_1_0.transport.SessionEventListener;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.End;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.SessionModelListener;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class Connection_1_0 implements ConnectionEventListener, AMQConnectionModel<Connection_1_0,Session_1_0>
{

    private final AmqpPort<?> _port;
    private final Broker<?> _broker;
    private final SubjectCreator _subjectCreator;
    private VirtualHostImpl _vhost;
    private final Transport _transport;
    private final ConnectionEndpoint _conn;
    private final long _connectionId;
    private final Collection<Session_1_0> _sessions = Collections.synchronizedCollection(new ArrayList<Session_1_0>());
    private final Object _reference = new Object();
    private final Subject _subject = new Subject();

    private final CopyOnWriteArrayList<SessionModelListener> _sessionListeners =
            new CopyOnWriteArrayList<SessionModelListener>();

    private final StatisticsCounter _messageDeliveryStatistics, _messageReceiptStatistics, _dataDeliveryStatistics, _dataReceiptStatistics;

    private final LogSubject _logSubject = new LogSubject()
    {
        @Override
        public String toLogString()
        {
            return "[" +
                   MessageFormat.format(CONNECTION_FORMAT,
                                        getConnectionId(),
                                        getClientId(),
                                        getRemoteAddressString(),
                                        _vhost.getName())
                   + "] ";

        }
    };

    private volatile boolean _stopped;


    private List<Action<? super Connection_1_0>> _closeTasks =
            Collections.synchronizedList(new ArrayList<Action<? super Connection_1_0>>());
    private boolean _closedOnOpen;


    public Connection_1_0(Broker<?> broker,
                          ConnectionEndpoint conn,
                          long connectionId,
                          AmqpPort<?> port,
                          Transport transport, final SubjectCreator subjectCreator)
    {
        _broker = broker;
        _port = port;
        _transport = transport;
        _conn = conn;
        _connectionId = connectionId;
        _subject.getPrincipals().add(new ConnectionPrincipal(this));
        _subjectCreator = subjectCreator;
        _messageDeliveryStatistics = new StatisticsCounter("messages-delivered-" + getConnectionId());
        _dataDeliveryStatistics = new StatisticsCounter("data-delivered-" + getConnectionId());
        _messageReceiptStatistics = new StatisticsCounter("messages-received-" + getConnectionId());
        _dataReceiptStatistics = new StatisticsCounter("data-received-" + getConnectionId());
    }

    public Object getReference()
    {
        return _reference;
    }

    @Override
    public void openReceived()
    {
        String host = _conn.getLocalHostname();
        _vhost = ((AmqpPort)_port).getVirtualHost(host);
        if(_vhost == null)
        {
            final Error err = new Error();
            err.setCondition(AmqpError.NOT_FOUND);
            err.setDescription("Unknown hostname in connection open: '" + host + "'");
            _conn.close(err);
            _closedOnOpen = true;
        }
        else
        {
            _vhost.getConnectionRegistry().registerConnection(this);
            Subject authSubject = _subjectCreator.createSubjectWithGroups(_conn.getUser());
            _subject.getPrincipals().addAll(authSubject.getPrincipals());
            _subject.getPublicCredentials().addAll(authSubject.getPublicCredentials());
            _subject.getPrivateCredentials().addAll(authSubject.getPrivateCredentials());
        }
    }
    public void remoteSessionCreation(SessionEndpoint endpoint)
    {
        if(!_closedOnOpen)
        {
            final Session_1_0 session = new Session_1_0(this, endpoint);
            _sessions.add(session);
            sessionAdded(session);
            endpoint.setSessionEventListener(new SessionEventListener()
            {
                @Override
                public void remoteLinkCreation(final LinkEndpoint endpoint)
                {
                    Subject.doAs(session.getSubject(), new PrivilegedAction<Object>()
                    {
                        @Override
                        public Object run()
                        {
                            session.remoteLinkCreation(endpoint);
                            return null;
                        }
                    });
                }

                @Override
                public void remoteEnd(final End end)
                {
                    Subject.doAs(session.getSubject(), new PrivilegedAction<Object>()
                    {
                        @Override
                        public Object run()
                        {
                            session.remoteEnd(end);
                            return null;
                        }
                    });
                }
            });
        }
    }

    void sessionEnded(Session_1_0 session)
    {
        if(!_closedOnOpen)
        {

            _sessions.remove(session);
            sessionRemoved(session);
        }
    }

    public void removeDeleteTask(final Action<? super Connection_1_0> task)
    {
        _closeTasks.remove( task );
    }

    public void addDeleteTask(final Action<? super Connection_1_0> task)
    {
        _closeTasks.add( task );
    }

    public void closeReceived()
    {
        Collection<Session_1_0> sessions = new ArrayList(_sessions);

        for(Session_1_0 session : sessions)
        {
            session.remoteEnd(new End());
        }

        List<Action<? super Connection_1_0>> taskCopy;

        synchronized (_closeTasks)
        {
            taskCopy = new ArrayList<Action<? super Connection_1_0>>(_closeTasks);
        }
        for(Action<? super Connection_1_0> task : taskCopy)
        {
            task.performAction(this);
        }
        synchronized (_closeTasks)
        {
            _closeTasks.clear();
        }
        if(_vhost != null)
        {
            _vhost.getConnectionRegistry().deregisterConnection(this);
        }


    }

    public void closed()
    {
        closeReceived();
    }


    @Override
    public void close(AMQConstant cause, String message)
    {
        _conn.close();
    }

    @Override
    public void block()
    {
        // TODO
    }

    @Override
    public void unblock()
    {
        // TODO
    }

    @Override
    public void closeSession(Session_1_0 session, AMQConstant cause, String message)
    {
        session.close(cause, message);
    }

    @Override
    public long getConnectionId()
    {
        return _connectionId;
    }

    @Override
    public List<Session_1_0> getSessionModels()
    {
        return new ArrayList<Session_1_0>(_sessions);
    }

    @Override
    public LogSubject getLogSubject()
    {
        return _logSubject;
    }

    @Override
    public boolean isSessionNameUnique(byte[] name)
    {
        return true;  // TODO
    }

    @Override
    public String getRemoteAddressString()
    {
        return String.valueOf(_conn.getRemoteAddress());
    }

    public SocketAddress getRemoteAddress()
    {
        return _conn.getRemoteAddress();
    }

    @Override
    public String getRemoteProcessPid()
    {
        return null;  // TODO
    }

    @Override
    public String getClientId()
    {
        return _conn.getRemoteContainerId();
    }

    @Override
    public String getRemoteContainerName()
    {
        return _conn.getRemoteContainerId();
    }

    @Override
    public String getClientVersion()
    {
        return "";  //TODO
    }

    @Override
    public String getClientProduct()
    {
        return "";  //TODO
    }

    public Principal getAuthorizedPrincipal()
    {
        Set<AuthenticatedPrincipal> authPrincipals = _subject.getPrincipals(AuthenticatedPrincipal.class);
        return authPrincipals.isEmpty() ? null : authPrincipals.iterator().next();
    }

    @Override
    public long getSessionCountLimit()
    {
        return 0;  // TODO
    }

    @Override
    public long getLastIoTime()
    {
        return 0;  // TODO
    }

    @Override
    public String getVirtualHostName()
    {
        return _vhost == null ? null : _vhost.getName();
    }

    @Override
    public AmqpPort<?> getPort()
    {
        return _port;
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

    @Override
    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messageReceiptStatistics.registerEvent(1L, timestamp);
        _dataReceiptStatistics.registerEvent(messageSize, timestamp);
        _vhost.registerMessageReceived(messageSize,timestamp);

    }

    @Override
    public void registerMessageDelivered(long messageSize)
    {

        _messageDeliveryStatistics.registerEvent(1L);
        _dataDeliveryStatistics.registerEvent(messageSize);
        _vhost.registerMessageDelivered(messageSize);
    }

    @Override
    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messageDeliveryStatistics;
    }

    @Override
    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messageReceiptStatistics;
    }

    @Override
    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDeliveryStatistics;
    }

    @Override
    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceiptStatistics;
    }

    @Override
    public void resetStatistics()
    {
        _dataDeliveryStatistics.reset();
        _dataReceiptStatistics.reset();
        _messageDeliveryStatistics.reset();
        _messageReceiptStatistics.reset();
    }



    AMQConnectionModel getModel()
    {
        return this;
    }


    Subject getSubject()
    {
        return _subject;
    }

    public VirtualHostImpl getVirtualHost()
    {
        return _vhost;
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


}
