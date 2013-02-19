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

import java.text.MessageFormat;
import java.util.Collection;
import org.apache.qpid.AMQException;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.ConnectionEventListener;
import org.apache.qpid.amqp_1_0.transport.SessionEndpoint;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.qpid.server.logging.subjects.LogSubjectFormat.CONNECTION_FORMAT;

public class Connection_1_0 implements ConnectionEventListener
{

    private VirtualHost _vhost;
    private final ConnectionEndpoint _conn;
    private final long _connectionId;
    private final Collection<Session_1_0> _sessions = Collections.synchronizedCollection(new ArrayList<Session_1_0>());


    public static interface Task
    {
        public void doTask(Connection_1_0 connection);
    }


    private List<Task> _closeTasks =
            Collections.synchronizedList(new ArrayList<Task>());



    public Connection_1_0(VirtualHost virtualHost, ConnectionEndpoint conn, long connectionId)
    {
        _vhost = virtualHost;
        _conn = conn;
        _connectionId = connectionId;
        _vhost.getConnectionRegistry().registerConnection(_model);

    }

    public void remoteSessionCreation(SessionEndpoint endpoint)
    {
        Session_1_0 session = new Session_1_0(_vhost, this);
        _sessions.add(session);
        endpoint.setSessionEventListener(session);
    }

    void sessionEnded(Session_1_0 session)
    {
        _sessions.remove(session);
    }

    void removeConnectionCloseTask(final Task task)
    {
        _closeTasks.remove( task );
    }

    void addConnectionCloseTask(final Task task)
    {
        _closeTasks.add( task );
    }

    public void closeReceived()
    {
        List<Task> taskCopy;
        synchronized (_closeTasks)
        {
            taskCopy = new ArrayList<Task>(_closeTasks);
        }
        for(Task task : taskCopy)
        {
            task.doTask(this);
        }
        synchronized (_closeTasks)
        {
            _closeTasks.clear();
        }
        _vhost.getConnectionRegistry().deregisterConnection(_model);


    }

    public void closed()
    {
        closeReceived();
    }

    private final AMQConnectionModel _model = new AMQConnectionModel()
    {
        private final StatisticsCounter _messageDeliveryStatistics = new StatisticsCounter();
        private final StatisticsCounter _messageReceiptStatistics = new StatisticsCounter();
        private final StatisticsCounter _dataDeliveryStatistics = new StatisticsCounter();
        private final StatisticsCounter _dataReceiptStatistics = new StatisticsCounter();

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

        @Override
        public void close(AMQConstant cause, String message) throws AMQException
        {
            // TODO
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
        public void closeSession(AMQSessionModel session, AMQConstant cause, String message) throws AMQException
        {
            // TODO
        }

        @Override
        public long getConnectionId()
        {
            return _connectionId;
        }

        @Override
        public List<AMQSessionModel> getSessionModels()
        {
            return new ArrayList<AMQSessionModel>(_sessions);
        }

        @Override
        public LogSubject getLogSubject()
        {
            return _logSubject;
        }

        @Override
        public String getUserName()
        {
            return getPrincipalAsString();
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
        public String getClientVersion()
        {
            return "";  //TODO
        }

        @Override
        public String getPrincipalAsString()
        {
            return String.valueOf(_conn.getUser());
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
        public void initialiseStatistics()
        {
            // TODO
        }

        @Override
        public void registerMessageReceived(long messageSize, long timestamp)
        {
            // TODO
        }

        @Override
        public void registerMessageDelivered(long messageSize)
        {
            // TODO
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
            // TODO
        }


    };

    AMQConnectionModel getModel()
    {
        return _model;
    }


}
