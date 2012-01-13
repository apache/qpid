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
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.jms.Connection;
import org.apache.qpid.amqp_1_0.jms.ConnectionMetaData;
import org.apache.qpid.amqp_1_0.jms.Session;
import org.apache.qpid.amqp_1_0.transport.Container;

import javax.jms.*;
import javax.jms.IllegalStateException;
import java.util.ArrayList;
import java.util.List;

public class ConnectionImpl implements Connection, QueueConnection, TopicConnection
{

    private ConnectionMetaData _connectionMetaData;
    private volatile ExceptionListener _exceptionListener;

    private final List<SessionImpl> _sessions = new ArrayList<SessionImpl>();

    private final Object _lock = new Object();

    private org.apache.qpid.amqp_1_0.client.Connection _conn;
    private boolean _isQueueConnection;
    private boolean _isTopicConnection;


    private static enum State
    {
        STOPPED,
        STARTED,
        CLOSED
    }

    private volatile State _state = State.STOPPED;

    public ConnectionImpl(String host, int port, String username, String password, String clientId) throws JMSException
    {
          this(host,port,username,password,clientId,false);
    }

    public ConnectionImpl(String host, int port, String username, String password, String clientId, boolean ssl) throws JMSException
    {
          this(host,port,username,password,clientId,null,ssl);
    }

    public ConnectionImpl(String host, int port, String username, String password, String clientId, String remoteHost, boolean ssl) throws JMSException
    {
        Container container = clientId == null ? new Container() : new Container(clientId);
        // TODO - authentication, containerId, clientId, ssl?, etc
        try
        {
            _conn = new org.apache.qpid.amqp_1_0.client.Connection(host, port, username, password, container, remoteHost, ssl);
            // TODO - retrieve negotiated AMQP version
            _connectionMetaData = new ConnectionMetaDataImpl(1,0,0);
        }
        catch (org.apache.qpid.amqp_1_0.client.Connection.ConnectionException e)
        {
            JMSException jmsEx = new JMSException(e.getMessage());
            jmsEx.setLinkedException(e);
            jmsEx.initCause(e);
            throw jmsEx;
        }
    }

    public SessionImpl createSession(final boolean transacted, final int acknowledgeMode) throws JMSException
    {
        Session.AcknowledgeMode ackMode;

        try
        {
            ackMode = transacted ? Session.AcknowledgeMode.SESSION_TRANSACTED
                                 : Session.AcknowledgeMode.values()[acknowledgeMode];
        }
        catch (IndexOutOfBoundsException e)
        {
            JMSException jmsEx = new JMSException("Unknown acknowledgement mode " + acknowledgeMode);
            jmsEx.setLinkedException(e);
            jmsEx.initCause(e);
            throw jmsEx;
        }

        return createSession(ackMode);
    }

    public SessionImpl createSession(final Session.AcknowledgeMode acknowledgeMode) throws JMSException
    {
        synchronized(_lock)
        {
            if(_state == State.CLOSED)
            {
                throw new IllegalStateException("Cannot create a session on a closed connection");
            }

            SessionImpl session = new SessionImpl(this, acknowledgeMode);
            session.setQueueSession(_isQueueConnection);
            session.setTopicSession(_isTopicConnection);
            _sessions.add(session);

            return session;
        }

    }

    public String getClientID() throws JMSException
    {
        checkClosed();
        return _conn.getEndpoint().getContainer().getId();
    }

    public void setClientID(final String s) throws JMSException
    {
        throw new IllegalStateException("Cannot set client-id to \""
                                        + s
                                        + "\"; client-id must be set on connection creation");
    }

    public ConnectionMetaData getMetaData() throws JMSException
    {
        checkClosed();
        return _connectionMetaData;
    }

    public ExceptionListener getExceptionListener() throws JMSException
    {
        checkClosed();
        return _exceptionListener;
    }

    public void setExceptionListener(final ExceptionListener exceptionListener) throws JMSException
    {
        checkClosed();
        _exceptionListener = exceptionListener;
    }

    public void start() throws JMSException
    {
        synchronized(_lock)
        {
            checkClosed();
            if(_state == State.STOPPED)
            {
                // TODO

                _state = State.STARTED;

                for(SessionImpl session : _sessions)
                {
                    session.start();
                }

            }

            _lock.notifyAll();
        }

    }

    public void stop() throws JMSException
    {
        synchronized(_lock)
        {
            switch(_state)
            {
                case STARTED:
                    for(SessionImpl session : _sessions)
                    {
                        session.stop();
                    }
                    _state = State.STOPPED;
                    break;
                case CLOSED:
                    throw new javax.jms.IllegalStateException("Closed");
            }

            _lock.notifyAll();
        }
    }

    public void close() throws JMSException
    {
        synchronized(_lock)
        {
            if(_state != State.CLOSED)
            {
                stop();
                for(SessionImpl session : _sessions)
                {
                    session.close();
                }
                // TODO - close underlying
                _state = State.CLOSED;
            }

            _lock.notifyAll();
        }
    }

    private void checkClosed() throws IllegalStateException
    {
        if(_state == State.CLOSED)
            throw new IllegalStateException("Closed");
    }

    public ConnectionConsumer createConnectionConsumer(final Destination destination,
                                                       final String s,
                                                       final ServerSessionPool serverSessionPool,
                                                       final int i) throws JMSException
    {
        checkClosed();
        return null;  //TODO
    }

    public TopicSession createTopicSession(final boolean transacted, final int acknowledgeMode) throws JMSException
    {
        checkClosed();
        return createSession(transacted, acknowledgeMode);
    }

    public ConnectionConsumer createConnectionConsumer(final Topic topic,
                                                       final String s,
                                                       final ServerSessionPool serverSessionPool,
                                                       final int i) throws JMSException
    {
        checkClosed();
        return null;  //TODO
    }

    public ConnectionConsumer createDurableConnectionConsumer(final Topic topic,
                                                              final String s,
                                                              final String s1,
                                                              final ServerSessionPool serverSessionPool,
                                                              final int i) throws JMSException
    {
        checkClosed();
        return null;  //TODO
    }

    public QueueSession createQueueSession(final boolean transacted, final int acknowledgeMode) throws JMSException
    {
        checkClosed();
        return createSession(transacted, acknowledgeMode);
    }

    public ConnectionConsumer createConnectionConsumer(final Queue queue,
                                                       final String s,
                                                       final ServerSessionPool serverSessionPool,
                                                       final int i) throws JMSException
    {
        checkClosed();
        return null;  //TODO
    }



    protected org.apache.qpid.amqp_1_0.client.Connection getClientConnection()
    {
        return _conn;
    }

    public boolean isStarted()
    {
        synchronized (_lock)
        {
            return _state == State.STARTED;
        }
    }

    void setQueueConnection(final boolean queueConnection)
    {
        _isQueueConnection = queueConnection;
    }

    void setTopicConnection(final boolean topicConnection)
    {
        _isTopicConnection = topicConnection;
    }
}
