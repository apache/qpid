/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpidity.njms;

import java.util.Vector;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSession;

import org.apache.qpid.url.QpidURL;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.nclient.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implements javax.njms.Connection, javax.njms.QueueConnection and javax.njms.TopicConnection
 */
public class ConnectionImpl implements Connection
{
    /**
     * This class's logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(ConnectionImpl.class);

    /**
     * Maps from session id (Integer) to SessionImpl instance
     */
    protected final Vector<SessionImpl> _sessions = new Vector<SessionImpl>();

    /**
     * This is the clientID
     */
    private String _clientID;

    /**
     * The Exception listenr get informed when a serious problem is detected
     */
    private ExceptionListener _exceptionListener;

    /**
     * Whether this connection is started, i.e. whether messages are flowing to consumers.
     * It has no meaning for message publication.
     */
    private boolean _started;

    /**
     * set to true if this Connection has been closed.
     * <p/>
     * A closed Connection cannot accept invocations to any of its methods with the exception
     * of close(). All other methods should throw javax.njms.IllegalStateExceptions if the
     * Connection has been closed.
     * <p/>
     * A Connection is open after creation, but not started. Once it has been closed, a Connection
     * cannot be reused any more.
     */
    private boolean _isClosed = false;


    /**
     * The QpidConeection instance that is mapped with thie JMS connection
     */
    org.apache.qpidity.nclient.Connection _qpidConnection;

    /**
     * This is the exception listener for this qpid connection.
     * The njms exception listener is registered with this listener.
     */
    QpidExceptionListenerImpl _qpidExceptionListener;

    //------ Constructors ---//
    /**
     * Create a connection.
     *
     * @param host        The broker host name.
     * @param port        The port on which the broker is listening for connection.
     * @param virtualHost The virtual host on which the broker is deployed.
     * @param username    The user name used of user identification.
     * @param password    The password name used of user identification.
     * @throws QpidException If creating a connection fails due to some internal error.
     */
    protected ConnectionImpl(String host, int port, String virtualHost, String username, String password)
            throws QpidException
    {
        _qpidConnection = Client.createConnection();
        _qpidConnection.connect(host, port, virtualHost, username, password);
    }

    /**
     * Create a connection from a QpidURL
     *
     * @param qpidURL The url used to create this connection
     * @throws QpidException If creating a connection fails due to some internal error.
     */
    protected ConnectionImpl(QpidURL qpidURL) throws QpidException
    {
        _qpidConnection = Client.createConnection();
        //_qpidConnection.connect(qpidURL);
    }

    //---- Interface javax.njms.Connection ---//
    /**
     * Creates a Session
     *
     * @param transacted      Indicates whether the session is transacted.
     * @param acknowledgeMode ignored if the session is transacted. Legal values are <code>Session.AUTO_ACKNOWLEDGE</code>,
     *                        <code>Session.CLIENT_ACKNOWLEDGE</code>, and <code>Session.DUPS_OK_ACKNOWLEDGE</code>.
     * @return A newly created session
     * @throws JMSException If the Connection object fails to create a session due to some internal error.
     */
    public synchronized Session createSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        checkNotClosed();
        SessionImpl session;
        try
        {
            session = new SessionImpl(this, transacted, acknowledgeMode, false);
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // add this session with the list of session that are handled by this connection
        _sessions.add(session);
        return session;
    }

    /**
     * Gets the client identifier for this connection.
     * <P>It is either preconfigured as a JNDI property or assigned dynamically by the application
     * by calling the <code>setClientID</code> method.
     * <p/>
     * TODO: Make sure that the client identifier can be set on the <CODE>ConnectionFactory</CODE>
     *
     * @return The unique client identifier.
     * @throws JMSException If this connection is closed.
     */
    public String getClientID() throws JMSException
    {
        checkNotClosed();
        return _clientID;
    }

    /**
     * Sets the client identifier for this connection.
     * <P>The preferred way to assign a JMS client's client identifier is for
     * it to be configured in a client-specific <CODE>ConnectionFactory</CODE>
     * object and transparently assigned to the <CODE>Connection</CODE> object
     * it creates.
     * <p> In Qpid it is not possible to change the client ID. If one is not specified
     * upon connection construction, an id is generated automatically. Therefore
     * we can always throw an exception.
     * TODO: Make sure that the client identifier can be set on the <CODE>ConnectionFactory</CODE>
     *
     * @param clientID the unique client identifier
     * @throws JMSException Always as clientID is always set at construction time.
     */
    public void setClientID(String clientID) throws JMSException
    {
        checkNotClosed();
        throw new IllegalStateException("Client name cannot be changed after being set");
    }

    /**
     * Gets the metadata for this connection.
     *
     * @return The connection metadata
     * @throws JMSException If there ie a problem getting the connection metadata for this connection.
     * @see javax.jms.ConnectionMetaData
     */
    public ConnectionMetaData getMetaData() throws JMSException
    {
        checkNotClosed();
        return ConnectionMetaDataImpl.getInstance();
    }

    /**
     * Gets the <CODE>ExceptionListener</CODE> object for this connection.
     *
     * @return the <CODE>ExceptionListener</CODE> for this connection
     * @throws JMSException In case of unforeseen problem
     */
    public synchronized ExceptionListener getExceptionListener() throws JMSException
    {
        checkNotClosed();
        return _exceptionListener;
    }

    /**
     * Sets an exception listener for this connection.
     * <p/>
     * <p> The JMS specification says:
     * <P>If a JMS provider detects a serious problem with a connection, it
     * informs the connection's <CODE>ExceptionListener</CODE>, if one has been
     * registered. It does this by calling the listener's
     * <CODE>onException</CODE> method, passing it a <CODE>JMSException</CODE>
     * object describing the problem.
     * <p/>
     * <P>A connection serializes execution of its
     * <CODE>ExceptionListener</CODE>.
     * <p/>
     * <P>A JMS provider should attempt to resolve connection problems
     * itself before it notifies the client of them.
     *
     * @param exceptionListener The connection listener.
     * @throws JMSException If the connection is closed.
     */
    public synchronized void setExceptionListener(ExceptionListener exceptionListener) throws JMSException
    {
        checkNotClosed();
        _exceptionListener = exceptionListener;
        _qpidExceptionListener.setJMSExceptionListner(_exceptionListener);
    }

    /**
     * Starts (or restarts) a connection's delivery of incoming messages.
     * A call to start on a connection that has already been
     * started is ignored.
     *
     * @throws JMSException In case of a problem due to some internal error.
     */
    public synchronized void start() throws JMSException
    {
        checkNotClosed();
        if (!_started)
        {
            // start all the sessions
            for (SessionImpl session : _sessions)
            {
                try
                {
                    session.start();
                }
                catch (Exception e)
                {
                    throw ExceptionHelper.convertQpidExceptionToJMSException(e);
                }
            }
            _started = true;
        }
    }

    /**
     * Temporarily stops a connection's delivery of incoming messages.
     * <p> The JMS specification says:
     * <p> Delivery can be restarted using the connection's <CODE>start</CODE>
     * method. When the connection is stopped, delivery to all the connection's message consumers is inhibited:
     * synchronous receives block, and messages are not delivered to message listeners.
     * <P>This call blocks until receives and/or message listeners in progress have completed.
     *
     * @throws JMSException In case of a problem due to some internal error.
     */
    public synchronized void stop() throws JMSException
    {
        checkNotClosed();
        if (_started)
        {
            // stop all the sessions
            for (SessionImpl session : _sessions)
            {
                try
                {
                    session.stop();
                }
                catch (Exception e)
                {
                    throw ExceptionHelper.convertQpidExceptionToJMSException(e);
                }
            }
            _started = false;
        }
    }

    /**
     * Closes the connection.
     * <p/>
     * <p> The JMS specification says:
     * <P>Since a provider typically allocates significant resources outside
     * the JVM on behalf of a connection, clients should close these resources
     * when they are not needed. Relying on garbage collection to eventually
     * reclaim these resources may not be timely enough.
     * <P>There is no need to close the sessions, producers, and consumers of a closed connection.
     * <P>Closing a connection causes all temporary destinations to be deleted.
     * <P>When this method is invoked, it should not return until message
     * processing has been shut down in an orderly fashion.
     *
     * @throws JMSException In case of a problem due to some internal error.
     */
    public synchronized void close() throws JMSException
    {
        checkNotClosed();
        if (!_isClosed)
        {
            _isClosed = true;
            _started = false;
            // close all the sessions
            for (SessionImpl session : _sessions)
            {
                session.close();
            }
            // close the underlaying Qpid connection
            try
            {
                _qpidConnection.close();
            }
            catch (QpidException e)
            {
                throw ExceptionHelper.convertQpidExceptionToJMSException(e);
            }
        }
    }

    /**
     * Creates a connection consumer for this connection (optional operation).
     * This is an expert facility for App server integration.
     *
     * @param destination     The destination to access.
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @param sessionPool     The session pool to associate with this connection consumer.
     * @param maxMessages     The maximum number of messages that can be assigned to a server session at one time.
     * @return Null for the moment.
     * @throws JMSException In case of a problem due to some internal error.
     */
    public ConnectionConsumer createConnectionConsumer(Destination destination, String messageSelector,
                                                       ServerSessionPool sessionPool, int maxMessages)
            throws JMSException
    {
        checkNotClosed();
        return null;
    }

    /**
     * Create a durable connection consumer for this connection (optional operation).
     *
     * @param topic            The topic to access.
     * @param subscriptionName Durable subscription name.
     * @param messageSelector  Only messages with properties matching the message selector expression are delivered.
     * @param sessionPool      The server session pool to associate with this durable connection consumer.
     * @param maxMessages      The maximum number of messages that can be assigned to a server session at one time.
     * @return Null for the moment.
     * @throws JMSException In case of a problem due to some internal error.
     */
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName,
                                                              String messageSelector, ServerSessionPool sessionPool,
                                                              int maxMessages) throws JMSException
    {
        checkNotClosed();
        return null;
    }

    //-------------- QueueConnection API

    /**
     * Create a QueueSession.
     *
     * @param transacted      Indicates whether the session is transacted.
     * @param acknowledgeMode Indicates whether the consumer or the
     *                        client will acknowledge any messages it receives; ignored if the session
     *                        is transacted. Legal values are <code>Session.AUTO_ACKNOWLEDGE</code>,
     *                        <code>Session.CLIENT_ACKNOWLEDGE</code> and <code>Session.DUPS_OK_ACKNOWLEDGE</code>.
     * @return A queueSession object/
     * @throws JMSException If creating a QueueSession fails due to some internal error.
     */
    public synchronized QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        checkNotClosed();
        QueueSessionImpl queueSession;
        try
        {
            queueSession = new QueueSessionImpl(this, transacted, acknowledgeMode);
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // add this session to the list of handled sessions.
        _sessions.add(queueSession);
        return queueSession;
    }

    /**
     * Creates a connection consumer for this connection (optional operation).
     * This is an expert facility for App server integration.
     *
     * @param queue           The queue to access.
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @param sessionPool     The session pool to associate with this connection consumer.
     * @param maxMessages     The maximum number of messages that can be assigned to a server session at one time.
     * @return Null for the moment.
     * @throws JMSException In case of a problem due to some internal error.
     */
    public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
                                                       ServerSessionPool sessionPool, int maxMessages)
            throws JMSException
    {
        return createConnectionConsumer((Destination) queue, messageSelector, sessionPool, maxMessages);
    }

    //-------------- TopicConnection API
    /**
     * Create a TopicSession.
     *
     * @param transacted      Indicates whether the session is transacted
     * @param acknowledgeMode Legal values are <code>Session.AUTO_ACKNOWLEDGE</code>, <code>Session.CLIENT_ACKNOWLEDGE</code>, and
     *                        <code>Session.DUPS_OK_ACKNOWLEDGE</code>.
     * @return a newly created topic session
     * @throws JMSException  If creating the session fails due to some internal error.
     */
    public synchronized TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException
    {
        checkNotClosed();
        TopicSessionImpl session;
        try
        {
            session = new TopicSessionImpl(this, transacted, acknowledgeMode);
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // add the session with this Connection's sessions
        // important for when the Connection is closed.
        _sessions.add(session);
        return session;
    }

    /**
     * Creates a connection consumer for this connection (optional operation).
     * This is an expert facility for App server integration.
     *
     * @param topic           The topic to access.
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @param sessionPool     The session pool to associate with this connection consumer.
     * @param maxMessages     The maximum number of messages that can be assigned to a server session at one time.
     * @return Null for the moment.
     * @throws JMSException In case of a problem due to some internal error.
     */
    public ConnectionConsumer createConnectionConsumer(Topic topic, String messageSelector,
                                                       ServerSessionPool sessionPool, int maxMessages)
            throws JMSException
    {
        return createConnectionConsumer((Destination) topic, messageSelector, sessionPool, maxMessages);
    }

    //-------------- protected and private methods
    /**
     * Validate that the Connection is not closed.
     * <p/>
     * If the Connection has been closed, throw a IllegalStateException. This behaviour is
     * required by the JMS specification.
     *
     * @throws IllegalStateException If the session is closed.
     */
    protected synchronized void checkNotClosed() throws IllegalStateException
    {
        if (_isClosed)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Connection has been closed. Cannot invoke any further operations.");
            }
            throw new javax.jms.IllegalStateException(
                    "Connection has been closed. Cannot invoke any further operations.");
        }
    }

    /**
     * Provide access to the underlying qpid Connection.
     *
     * @return This JMS connection underlying Qpid Connection.
     */
    protected org.apache.qpidity.nclient.Connection getQpidConnection()
    {
        return _qpidConnection;
    }
}
