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

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.jms.ConnectionConsumer;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.net.ssl.SSLContext;

import org.apache.qpid.amqp_1_0.client.ConnectionErrorException;
import org.apache.qpid.amqp_1_0.client.ConnectionException;
import org.apache.qpid.amqp_1_0.jms.Connection;
import org.apache.qpid.amqp_1_0.jms.ConnectionMetaData;
import org.apache.qpid.amqp_1_0.jms.Session;
import org.apache.qpid.amqp_1_0.transport.Container;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.transport.ConnectionError;
import org.apache.qpid.amqp_1_0.type.transport.Error;

public class ConnectionImpl implements Connection, QueueConnection, TopicConnection
{

    private final String _protocol;
    private final SSLContext _sslContext;
    private ConnectionMetaData _connectionMetaData;
    private volatile ExceptionListener _exceptionListener;

    private final List<SessionImpl> _sessions = new ArrayList<SessionImpl>();

    private final Object _lock = new Object();

    private org.apache.qpid.amqp_1_0.client.Connection _conn;
    private boolean _isQueueConnection;
    private boolean _isTopicConnection;
    private final Collection<CloseTask> _closeTasks = new ArrayList<CloseTask>();
    private String _host;
    private int _port;
    private final String _username;
    private final String _password;
    private String _remoteHost;
    private String _clientId;
    private String _queuePrefix;
    private String _topicPrefix;
    private boolean _useBinaryMessageId = Boolean.parseBoolean(System.getProperty("qpid.use_binary_message_id", "true"));
    private Boolean _syncPublish;
    private int _maxSessions;
    private int _maxPrefetch;

    public void setMaxPrefetch(final int maxPrefetch)
    {
        _maxPrefetch = maxPrefetch;
    }

    private static enum State
    {
        UNCONNECTED,
        STOPPED,
        STARTED,
        CLOSED
    }

    private volatile State _state = State.UNCONNECTED;

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
        this(host, port, username, password, clientId, remoteHost, ssl,0);
    }


    public ConnectionImpl(String host, int port, String username, String password, String clientId, String remoteHost, boolean ssl, int maxSessions) throws JMSException
    {
        this(ssl?"amqps":"amqp",host,port,username,password,clientId,remoteHost,ssl,maxSessions);
    }

    public ConnectionImpl(String protocol, String host, int port, String username, String password, String clientId, String remoteHost, boolean ssl, int maxSessions) throws JMSException
    {
        this(protocol,
             host,
             port,
             username,
             password,
             clientId,
             remoteHost,
             ssl ? getDefaultSSLContext() : null,
             maxSessions);
    }

    private static SSLContext getDefaultSSLContext() throws JMSException
    {
        try
        {
            return SSLContext.getDefault();
        }
        catch (NoSuchAlgorithmException e)
        {
            JMSException jmsException = new JMSException(e.getMessage());
            jmsException.setLinkedException(e);
            jmsException.initCause(e);
            throw jmsException;
        }
    }

    public ConnectionImpl(String protocol, String host, int port, String username, String password, String clientId, String remoteHost, SSLContext sslContext, int maxSessions) throws JMSException
    {
        _protocol = protocol;
        _host = host;
        _port = port;
        _username = username;
        _password = password;
        _clientId = clientId;
        _remoteHost = remoteHost;
        _sslContext = sslContext;
        _maxSessions = maxSessions;

        if(! "".equals(System.getProperty("qpid.sync_publish","")))
        {
            _syncPublish = Boolean.getBoolean("qpid.sync_publish");
        }
    }

    private void connect() throws JMSException
    {
        synchronized(_lock)
        {
            // already connected?
            if( _state == State.UNCONNECTED )
            {
                _state = State.STOPPED;

                Container container = _clientId == null ? new Container() : new Container(_clientId);

                try
                {
                    _conn = new org.apache.qpid.amqp_1_0.client.Connection(_protocol, _host,
                            _port, _username, _password, container, _remoteHost, _sslContext,
                            _maxSessions - 1);
                    _conn.setConnectionErrorTask(new ConnectionErrorTask());
                    // TODO - retrieve negotiated AMQP version
                    _connectionMetaData = new ConnectionMetaDataImpl(1,0,0);
                    Error connectionError = _conn.getConnectionError();
                    if(connectionError != null)
                    {
                        throw new JMSException(connectionError.getDescription(),
                                               connectionError.getCondition().toString());
                    }
                    else if(_conn.getEndpoint().requiresSASL() && !_conn.getEndpoint().isAuthenticated())
                    {
                        throw new JMSException("Authentication Failed");
                    }
                }
                catch (ConnectionException e)
                {
                    JMSException jmsEx = new JMSException(e.getMessage());
                    jmsEx.setLinkedException(e);
                    jmsEx.initCause(e);
                    throw jmsEx;
                }
            }
        }
    }

    private void checkNotConnected(String msg) throws IllegalStateException
    {
        synchronized(_lock)
        {
            if( _state != State.UNCONNECTED )
            {
                throw new IllegalStateException(msg);
            }
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
        boolean started = false;
        synchronized(_lock)
        {
            if(_state == State.CLOSED)
            {
                throw new IllegalStateException("Cannot create a session on a closed connection");
            }
            else if(_state == State.UNCONNECTED)
            {
                connect();
                started = true;
            }
        }

        try
        {
            SessionImpl session = new SessionImpl(this, acknowledgeMode);
            session.setQueueSession(_isQueueConnection);
            session.setTopicSession(_isTopicConnection);
            if(_maxPrefetch != 0)
            {
                session.setMaxPrefetch(_maxPrefetch);
            }
            
            boolean connectionStarted = false;
            synchronized(_lock)
            {
                checkClosed();
                _sessions.add(session);
                connectionStarted = _state == State.STARTED;
            }
            
            if(connectionStarted)
            {
                session.start();
            }
            
            return session;
        }
        catch(JMSException e)
        {
            Error remoteError;
            if(started
               && e.getLinkedException() instanceof ConnectionErrorException
               && (remoteError = ((ConnectionErrorException)e.getLinkedException()).getRemoteError()).getCondition() == ConnectionError.REDIRECT)
            {
                String networkHost = (String) remoteError.getInfo().get(Symbol.valueOf("network-host"));
                int port = (Integer) remoteError.getInfo().get(Symbol.valueOf("port"));
                String hostName = (String) remoteError.getInfo().get(Symbol.valueOf("hostname"));
                reconnect(networkHost,port,hostName);
                return createSession(acknowledgeMode);

            }
            else
            {
                throw e;
            }
        }
    }

    void removeSession(SessionImpl session)
    {
        synchronized (_lock)
        {
            _sessions.remove(session);
        }
    }

    private void reconnect(String networkHost, int port, String hostName)
    {
        synchronized(_lock)
        {
            _state = State.UNCONNECTED;
            _host = networkHost;
            _port = port;
            _remoteHost = hostName;
            _conn = null;
        }
    }

    public String getClientID() throws JMSException
    {
        checkClosed();
        return _clientId;
    }

    public void setClientID(final String value) throws JMSException
    {
        checkNotConnected("Cannot set client-id to \""
                          + value
                          + "\"; client-id must be set before the connection is used");
        if( _clientId !=null )
        {
            throw new IllegalStateException("client-id has already been set");
        }
        _clientId = value;
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
        List<SessionImpl> stoppedSessions = null;
        synchronized(_lock)
        {
            checkClosed();
            connect();
            if(_state == State.STOPPED)
            {
                // TODO

                _state = State.STARTED;
                stoppedSessions = new ArrayList<SessionImpl>(_sessions);
            }

            _lock.notifyAll();
        }

        if (stoppedSessions != null)
        {
            for(SessionImpl session : stoppedSessions)
            {
                session.start();
            }
        }
    }

    public void stop() throws JMSException
    {
        List<SessionImpl> startedSessions = null;
        synchronized(_lock)
        {
            switch(_state)
            {
                case STARTED:
                    startedSessions = new ArrayList<SessionImpl>(_sessions);
                case UNCONNECTED:
                    _state = State.STOPPED;
                    break;
                case CLOSED:
                    throw new javax.jms.IllegalStateException("Closed");
            }

            _lock.notifyAll();
        }
        
        if (startedSessions != null)
        {
            for(SessionImpl session : startedSessions)
            {
                session.stop();
            }
        }
    }


    static interface CloseTask
    {
        public void onClose() throws JMSException;
    }

    void addOnCloseTask(CloseTask task)
    {
        synchronized (_lock)
        {
            _closeTasks.add(task);
        }
    }


    void removeOnCloseTask(CloseTask task)
    {
        synchronized (_lock)
        {
            _closeTasks.remove(task);
        }
    }

    public void close() throws JMSException
    {
        List<SessionImpl> sessions = null;
        List<CloseTask> closeTasks = null;
        boolean closeConnection = false;
        synchronized(_lock)
        {
            if(_state != State.CLOSED)
            {
                _state = State.CLOSED;
                sessions = new ArrayList<SessionImpl>(_sessions);
                closeTasks = new ArrayList<CloseTask>(_closeTasks);
                closeConnection = _conn != null && _state != State.UNCONNECTED;
            }
            
            _lock.notifyAll();
        }

        List<JMSException> errors = new ArrayList<JMSException>();

        if (sessions != null)
        {
            for(SessionImpl session : sessions)
            {
                try
                {
                    session.close();
                }
                catch(JMSException e)
                {
                    errors.add(e);
                }
            }
            for(CloseTask task : closeTasks)
            {
                task.onClose();
            }
            if(closeConnection)
            {
                try
                {
                    _conn.close();
                }
                catch (ConnectionErrorException e)
                {
                    final JMSException jmsException = new JMSException("Error while closing connection: " + e.getMessage());
                    jmsException.setLinkedException(e);
                    throw jmsException;
                }
            }
        }

        if(!errors.isEmpty())
        {
            final JMSException jmsException = new JMSException("Error while closing connection: " + errors.get(0).getMessage());
            jmsException.setLinkedException(errors.get(0));
            throw jmsException;
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
        SessionImpl session = createSession(transacted, acknowledgeMode);
        session.setTopicSession(true);
        return session;
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
        if (_isQueueConnection)
        {
            throw new IllegalStateException("QueueConnection cannot be used to create Pub/Sub based resources.");
        }
        return null;  //TODO
    }

    public QueueSession createQueueSession(final boolean transacted, final int acknowledgeMode) throws JMSException
    {
        checkClosed();
        SessionImpl session = createSession(transacted, acknowledgeMode);
        session.setQueueSession(true);
        return session;
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
        return _state == State.STARTED;
    }

    void setQueueConnection(final boolean queueConnection)
    {
        _isQueueConnection = queueConnection;
    }

    void setTopicConnection(final boolean topicConnection)
    {
        _isTopicConnection = topicConnection;
    }

    public String getTopicPrefix()
    {
        return _topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix)
    {
        _topicPrefix = topicPrefix;
    }

    public String getQueuePrefix()
    {
        return _queuePrefix;
    }

    public void setQueuePrefix(String queueprefix)
    {
        _queuePrefix = queueprefix;
    }

    DecodedDestination toDecodedDestination(DestinationImpl dest)
    {
        String address = dest.getAddress();
        Set<String> kind = null;
        Class clazz = dest.getClass();
        if( clazz==QueueImpl.class )
        {
            kind = MessageImpl.JMS_QUEUE_ATTRIBUTES;
            if( _queuePrefix!=null )
            {
                // Avoid double prefixing..
                if( !address.startsWith(_queuePrefix) )
                {
                    address = _queuePrefix+address;
                }
            }
        }
        else if( clazz==TopicImpl.class )
        {
            kind = MessageImpl.JMS_TOPIC_ATTRIBUTES;
            if( _topicPrefix!=null )
            {
                // Avoid double prefixing..
                if( !address.startsWith(_topicPrefix) )
                {
                    address = _topicPrefix+address;
                }
            }
        }
        else if( clazz==TemporaryQueueImpl.class )
        {
            kind = MessageImpl.JMS_TEMP_QUEUE_ATTRIBUTES;
        }
        else if( clazz==TemporaryTopicImpl.class )
        {
            kind = MessageImpl.JMS_TEMP_TOPIC_ATTRIBUTES;
        }
        return new DecodedDestination(address, kind);
    }

    DecodedDestination toDecodedDestination(String address, Set<String> kind)
    {
        if( (kind == null || kind.equals(MessageImpl.JMS_QUEUE_ATTRIBUTES)) && _queuePrefix!=null && address.startsWith(_queuePrefix))
        {
            return new DecodedDestination(address.substring(_queuePrefix.length()), MessageImpl.JMS_QUEUE_ATTRIBUTES);
        }
        if( (kind == null || kind.equals(MessageImpl.JMS_TOPIC_ATTRIBUTES)) && _topicPrefix!=null && address.startsWith(_topicPrefix))
        {
            return new DecodedDestination(address.substring(_topicPrefix.length()), MessageImpl.JMS_TOPIC_ATTRIBUTES);
        }
        return new DecodedDestination(address, kind);
    }

    void setUseBinaryMessageId(boolean useBinaryMessageId)
    {
        _useBinaryMessageId = useBinaryMessageId;
    }

    boolean useBinaryMessageId()
    {
        return _useBinaryMessageId;
    }

    void setSyncPublish(Boolean syncPublish)
    {
        _syncPublish = syncPublish;
    }

    Boolean syncPublish()
    {
        return _syncPublish;
    }

    private class ConnectionErrorTask implements Runnable
    {

        @Override
        public void run()
        {

            try
            {
                final ExceptionListener exceptionListener = getExceptionListener();

                if(exceptionListener != null)
                {
                    final org.apache.qpid.amqp_1_0.type.transport.Error connectionError = _conn.getConnectionError();
                    if(connectionError != null)
                    {
                        exceptionListener.onException(new JMSException(connectionError.getDescription(),
                                connectionError.getCondition().toString()));
                    }
                }
            }
            catch (JMSException ignored)
            {
                // ignored
            }
        }
    }

}
