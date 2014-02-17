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

import java.security.Principal;
import java.text.MessageFormat;
import java.util.Collection;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.ConnectionEventListener;
import org.apache.qpid.amqp_1_0.transport.SessionEndpoint;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CONNECTION_FORMAT;

public class Connection_1_0 implements ConnectionEventListener, AMQConnectionModel<Connection_1_0,Session_1_0>
{

    private final Port _port;
    private VirtualHost _vhost;
    private final Transport _transport;
    private final ConnectionEndpoint _conn;
    private final long _connectionId;
    private final Collection<Session_1_0> _sessions = Collections.synchronizedCollection(new ArrayList<Session_1_0>());
    private final Object _reference = new Object();


    private StatisticsCounter _messageDeliveryStatistics = new StatisticsCounter();
    private StatisticsCounter _messageReceiptStatistics = new StatisticsCounter();
    private StatisticsCounter _dataDeliveryStatistics = new StatisticsCounter();
    private StatisticsCounter _dataReceiptStatistics = new StatisticsCounter();

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



    public Connection_1_0(VirtualHost virtualHost,
                          ConnectionEndpoint conn,
                          long connectionId,
                          Port port,
                          Transport transport)
    {
        _vhost = virtualHost;
        _port = port;
        _transport = transport;
        _conn = conn;
        _connectionId = connectionId;
        _vhost.getConnectionRegistry().registerConnection(this);

    }

    public Object getReference()
    {
        return _reference;
    }

    public void remoteSessionCreation(SessionEndpoint endpoint)
    {
        Session_1_0 session = new Session_1_0(_vhost, this, endpoint);
        _sessions.add(session);
        endpoint.setSessionEventListener(session);
    }

    void sessionEnded(Session_1_0 session)
    {
        _sessions.remove(session);
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
        _vhost.getConnectionRegistry().deregisterConnection(this);


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
            return _conn.getUser();
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
        public Port getPort()
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
        public void initialiseStatistics()
        {
            _messageDeliveryStatistics = new StatisticsCounter("messages-delivered-" + getConnectionId());
            _dataDeliveryStatistics = new StatisticsCounter("data-delivered-" + getConnectionId());
            _messageReceiptStatistics = new StatisticsCounter("messages-received-" + getConnectionId());
            _dataReceiptStatistics = new StatisticsCounter("data-received-" + getConnectionId());
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


}
