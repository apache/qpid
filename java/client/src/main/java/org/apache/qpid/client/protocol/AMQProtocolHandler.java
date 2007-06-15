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
package org.apache.qpid.client.protocol;

import org.apache.log4j.Logger;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.SSLFilter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.SSLConfiguration;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverHandler;
import org.apache.qpid.client.failover.FailoverState;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.listener.SpecificMethodFrameListener;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.HeartbeatBody;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.ssl.SSLContextFactory;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

/**
 * AMQProtocolHandler is the client side protocol handler for AMQP, it handles all protocol events received from the
 * network by MINA. The primary purpose of AMQProtocolHandler is to translate the generic event model of MINA into the
 * specific event model of AMQP, by revealing the type of the received events (from decoded data), and passing the event
 * on to more specific handlers for the type. In this sense, it channels the richer event model of AMQP, expressed in
 * terms of methods and so on, through the cruder, general purpose event model of MINA, expressed in terms of "message
 * received" and so on.
 *
 * <p/>There is a 1:1 mapping between an AMQProtocolHandler and an {@link AMQConnection}. The connection class is
 * exposed to the end user of the AMQP client API, and also implements the JMS Connection API, so provides the public
 * API calls through which an individual connection can be manipulated. This protocol handler talks to the network
 * through MINA, in a behind the scenes role; it is not an exposed part of the client API.
 *
 * <p/>There is a 1:many mapping between an AMQProtocolHandler and a set of {@link AMQSession}s. At the MINA level,
 * there is one session per connection. At the AMQP level there can be many channels which are also called sessions in
 * JMS parlance. The {@link AMQSession}s are managed through an {@link AMQProtocolSession} instance. The protocol
 * session is similar to the MINA per-connection session, except that it can span the lifecycle of multiple MINA
 * sessions in the event of failover. See below for more information about this.
 *
 * <p/>Mina provides a session container that can be used to store/retrieve arbitrary objects as String named
 * attributes. A more convenient, type-safe, container for session data is provided in the form of {@link
 * AMQProtocolSession}.
 *
 * <p/>A common way to use MINA is to have a single instance of the event handler, and for MINA to pass in its session
 * object with every event, and for per-connection data to be held in the MINA session (perhaps using a type-safe
 * wrapper as described above). This event handler is different, because dealing with failover complicates things. To
 * the end client of an AMQConnection, a failed over connection is still handled through the same connection instance,
 * but behind the scenes a new transport connection, and MINA session will have been created. The MINA session object
 * cannot be used to track the state of the fail-over process, because it is destroyed and a new one is created, as the
 * old connection is shutdown and a new one created. For this reason, an AMQProtocolHandler is created per AMQConnection
 * and the protocol session data is held outside of the MINA IOSession.
 *
 * <p/>This handler is responsibile for setting up the filter chain to filter all events for this handler through. The
 * filter chain is set up as a stack of event handers that perform the following functions (working upwards from the
 * network traffic at the bottom), handing off incoming events to an asynchronous thread pool to do the work, optionally
 * handling secure sockets encoding/decoding, encoding/decoding the AMQP format itself.
 *
 * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Create the
 * filter chain to filter this handlers events. <td> {@link ProtocolCodecFilter}, {@link SSLContextFactory}, {@link
 * SSLFilter}, {@link ReadWriteThreadModel}.
 *
 * <tr><td> Maintain fail-over state. <tr><td> </table>
 *
 * @todo Explain the system property: amqj.shared_read_write_pool. How does putting the protocol codec filter before the
 * async write filter make it a shared pool? The pooling filter uses the same thread pool for reading and writing
 * anyway, see {@link org.apache.qpid.pool.PoolingFilter}, docs for comments. Will putting the protocol codec filter
 * before it mean not doing the read/write asynchronously but in the main filter thread?
 * @todo Use a single handler instance, by shifting everything to do with the 'protocol session' state, including
 * failover state, into AMQProtocolSession, and tracking that from AMQConnection? The lifecycles of AMQProtocolSesssion
 * and AMQConnection will be the same, so if there is high cohesion between them, they could be merged, although there
 * is sense in keeping the session model seperate. Will clarify things by having data held per protocol handler, per
 * protocol session, per network connection, per channel, in seperate classes, so that lifecycles of the fields match
 * lifecycles of their containing objects.
 */
public class AMQProtocolHandler extends IoHandlerAdapter
{
    /** Used for debugging. */
    private static final Logger _logger = Logger.getLogger(AMQProtocolHandler.class);

    /**
     * The connection that this protocol handler is associated with. There is a 1-1 mapping between connection instances
     * and protocol handler instances.
     */
    private AMQConnection _connection;

    /** Our wrapper for a protocol session that provides access to session values in a typesafe manner. */
    private volatile AMQProtocolSession _protocolSession;

    /** Holds the state of the protocol session. */
    private AMQStateManager _stateManager = new AMQStateManager();

    /** Holds the method listeners, */
    private final CopyOnWriteArraySet _frameListeners = new CopyOnWriteArraySet();

    /**
     * We create the failover handler when the session is created since it needs a reference to the IoSession in order
     * to be able to send errors during failover back to the client application. The session won't be available in the
     * case where we failing over due to a Connection.Redirect message from the broker.
     */
    private FailoverHandler _failoverHandler;

    /**
     * This flag is used to track whether failover is being attempted. It is used to prevent the application constantly
     * attempting failover where it is failing.
     */
    private FailoverState _failoverState = FailoverState.NOT_STARTED;

    /** Used to provide a condition to wait upon for operations that are required to wait for failover to complete. */
    private CountDownLatch _failoverLatch;

    /** Defines the default timeout to use for synchronous protocol commands. */
    private final long DEFAULT_SYNC_TIMEOUT = 1000 * 30;

    /**
     * Creates a new protocol handler, associated with the specified client connection instance.
     *
     * @param con The client connection that this is the event handler for.
     */
    public AMQProtocolHandler(AMQConnection con)
    {
        _connection = con;
    }

    /**
     * Invoked by MINA when a MINA session for a new connection is created. This method sets up the filter chain on the
     * session, which filters the events handled by this handler. The filter chain consists of, handing off events to an
     * asynchronous thread pool, optionally encoding/decoding ssl, encoding/decoding AMQP.
     *
     * @param session The MINA session.
     *
     * @throws Exception Any underlying exceptions are allowed to fall through to MINA.
     */
    public void sessionCreated(IoSession session) throws Exception
    {
        _logger.debug("Protocol session created for session " + System.identityHashCode(session));
        _failoverHandler = new FailoverHandler(this, session);

        final ProtocolCodecFilter pcf = new ProtocolCodecFilter(new AMQCodecFactory(false));

        if (Boolean.getBoolean("amqj.shared_read_write_pool"))
        {
            session.getFilterChain().addBefore("AsynchronousWriteFilter", "protocolFilter", pcf);
        }
        else
        {
            session.getFilterChain().addLast("protocolFilter", pcf);
        }
        // we only add the SSL filter where we have an SSL connection
        if (_connection.getSSLConfiguration() != null)
        {
            SSLConfiguration sslConfig = _connection.getSSLConfiguration();
            SSLContextFactory sslFactory =
                    new SSLContextFactory(sslConfig.getKeystorePath(), sslConfig.getKeystorePassword(), sslConfig.getCertType());
            SSLFilter sslFilter = new SSLFilter(sslFactory.buildClientContext());
            sslFilter.setUseClientMode(true);
            session.getFilterChain().addBefore("protocolFilter", "ssl", sslFilter);
        }

        try
        {
            ReadWriteThreadModel threadModel = ReadWriteThreadModel.getInstance();
            threadModel.getAsynchronousReadFilter().createNewJobForSession(session);
            threadModel.getAsynchronousWriteFilter().createNewJobForSession(session);
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
        }

        _protocolSession = new AMQProtocolSession(this, session, _connection, getStateManager());
        _protocolSession.init();
    }

    /**
     * Called when the network connection is closed. This can happen, either because the client explicitly requested
     * that the connection be closed, in which case nothing is done, or because the connection died. In the case where
     * the connection died, an attempt to failover automatically to a new connection may be started. The failover
     * process will be started, provided that it is the clients policy to allow failover, and provided that a failover
     * has not already been started or failed.
     *
     * <p/>It is important to note that when the connection dies this method may be called or {@link #exceptionCaught}
     * may be called first followed by this method. This depends on whether the client was trying to send data at the
     * time of the failure.
     *
     * @param session The MINA session.
     *
     * @todo Clarify: presumably exceptionCaught is called when the client is sending during a connection failure and
     * not otherwise? The above comment doesn't make that clear.
     */
    public void sessionClosed(IoSession session)
    {
        if (_connection.isClosed())
        {
            _logger.debug("Session closed called by client");
        }
        else
        {
            _logger.debug("Session closed called with failover state currently " + _failoverState);

            // reconnetablility was introduced here so as not to disturb the client as they have made their intentions
            // known through the policy settings.

            if ((_failoverState != FailoverState.IN_PROGRESS) && _connection.failoverAllowed())
            {
                _logger.debug("FAILOVER STARTING");
                if (_failoverState == FailoverState.NOT_STARTED)
                {
                    _failoverState = FailoverState.IN_PROGRESS;
                    startFailoverThread();
                }
                else
                {
                    _logger.debug("Not starting failover as state currently " + _failoverState);
                }
            }
            else
            {
                _logger.debug("Failover not allowed by policy."); // or already in progress?

                if (_logger.isDebugEnabled())
                {
                    _logger.debug(_connection.getFailoverPolicy().toString());
                }

                if (_failoverState != FailoverState.IN_PROGRESS)
                {
                    _logger.info("sessionClose() not allowed to failover");
                    _connection.exceptionReceived(
                            new AMQDisconnectedException("Server closed connection and reconnection " +
                                                         "not permitted.", null));
                }
                else
                {
                    _logger.debug("sessionClose() failover in progress");
                }
            }
        }

        _logger.debug("Protocol Session [" + this + "] closed");
    }

    /** See {@link FailoverHandler} to see rationale for separate thread. */
    private void startFailoverThread()
    {
        Thread failoverThread = new Thread(_failoverHandler);
        failoverThread.setName("Failover");
        // Do not inherit daemon-ness from current thread as this can be a daemon
        // thread such as a AnonymousIoService thread.
        failoverThread.setDaemon(false);
        failoverThread.start();
    }

    public void sessionIdle(IoSession session, IdleStatus status) throws Exception
    {
        _logger.debug("Protocol Session [" + this + ":" + session + "] idle: " + status);
        if (IdleStatus.WRITER_IDLE.equals(status))
        {
            // write heartbeat frame:
            _logger.debug("Sent heartbeat");
            session.write(HeartbeatBody.FRAME);
            HeartbeatDiagnostics.sent();
        }
        else if (IdleStatus.READER_IDLE.equals(status))
        {
            // failover:
            HeartbeatDiagnostics.timeout();
            _logger.warn("Timed out while waiting for heartbeat from peer.");
            session.close();
        }
    }

    /**
     * Invoked when any exception is thrown by a user IoHandler implementation or by MINA. If the cause is an
     * IOException, MINA will close the connection automatically.
     *
     * @param session The MINA session.
     * @param cause   The exception that triggered this event.
     */
    public void exceptionCaught(IoSession session, Throwable cause)
    {
        if (_failoverState == FailoverState.NOT_STARTED)
        {
            // if (!(cause instanceof AMQUndeliveredException) && (!(cause instanceof AMQAuthenticationException)))
            if (cause instanceof AMQConnectionClosedException)
            {
                _logger.info("Exception caught therefore going to attempt failover: " + cause, cause);
                // this will attemp failover

                sessionClosed(session);
            }

            // FIXME Need to correctly handle other exceptions. Things like ...
            // if (cause instanceof AMQChannelClosedException)
            // which will cause the JMSSession to end due to a channel close and so that Session needs
            // to be removed from the map so we can correctly still call close without an exception when trying to close
            // the server closed session.  See also CloseChannelMethodHandler as the sessionClose is never called on exception
        }
        // we reach this point if failover was attempted and failed therefore we need to let the calling app
        // know since we cannot recover the situation
        else if (_failoverState == FailoverState.FAILED)
        {
            _logger.error("Exception caught by protocol handler: " + cause, cause);

            // we notify the state manager of the error in case we have any clients waiting on a state
            // change. Those "waiters" will be interrupted and can handle the exception
            AMQException amqe = new AMQException(null, "Protocol handler error: " + cause, cause);
            propagateExceptionToWaiters(amqe);
            _connection.exceptionReceived(cause);
        }
    }

    /**
     * There are two cases where we have other threads potentially blocking for events to be handled by this class.
     * These are for the state manager (waiting for a state change) or a frame listener (waiting for a particular type
     * of frame to arrive). When an error occurs we need to notify these waiters so that they can react appropriately.
     *
     * @param e the exception to propagate
     */
    public void propagateExceptionToWaiters(Exception e)
    {
        getStateManager().error(e);
        if (!_frameListeners.isEmpty())
        {
            final Iterator it = _frameListeners.iterator();
            while (it.hasNext())
            {
                final AMQMethodListener ml = (AMQMethodListener) it.next();
                ml.error(e);
            }
        }
    }

    private static int _messageReceivedCount;

    public void messageReceived(IoSession session, Object message) throws Exception
    {
        final boolean debug = _logger.isDebugEnabled();
        final long msgNumber = ++_messageReceivedCount;

        if (debug && ((msgNumber % 1000) == 0))
        {
            _logger.debug("Received " + _messageReceivedCount + " protocol messages");
        }

        AMQFrame frame = (AMQFrame) message;

        final AMQBody bodyFrame = frame.getBodyFrame();

        HeartbeatDiagnostics.received(bodyFrame instanceof HeartbeatBody);

        switch (bodyFrame.getFrameType())
        {
            case AMQMethodBody.TYPE:

                if (debug)
                {
                    _logger.debug("(" + System.identityHashCode(this) + ")Method frame received: " + frame);
                }

                final AMQMethodEvent<AMQMethodBody> evt =
                        new AMQMethodEvent<AMQMethodBody>(frame.getChannel(), (AMQMethodBody) bodyFrame);

                try
                {

                    boolean wasAnyoneInterested = getStateManager().methodReceived(evt);
                    if (!_frameListeners.isEmpty())
                    {
                        Iterator it = _frameListeners.iterator();
                        while (it.hasNext())
                        {
                            final AMQMethodListener listener = (AMQMethodListener) it.next();
                            wasAnyoneInterested = listener.methodReceived(evt) || wasAnyoneInterested;
                        }
                        if (!wasAnyoneInterested)
                        {
                            throw new AMQException(null, "AMQMethodEvent " + evt + " was not processed by any listener.  Listeners:" + _frameListeners, null);
                        }
                    }

                    if (!wasAnyoneInterested)
                    {
                        throw new AMQException(null, "AMQMethodEvent " + evt + " was not processed by any listener.  Listeners:"
                                                     + _frameListeners, null);
                    }
                }
                catch (AMQException e)
                {
                    getStateManager().error(e);
                    if (!_frameListeners.isEmpty())
                    {
                        Iterator it = _frameListeners.iterator();
                        while (it.hasNext())
                        {
                            final AMQMethodListener listener = (AMQMethodListener) it.next();
                            listener.error(e);
                        }
                    }

                    exceptionCaught(session, e);
                }

                break;

            case ContentHeaderBody.TYPE:

                _protocolSession.messageContentHeaderReceived(frame.getChannel(), (ContentHeaderBody) bodyFrame);
                break;

            case ContentBody.TYPE:

                _protocolSession.messageContentBodyReceived(frame.getChannel(), (ContentBody) bodyFrame);
                break;

            case HeartbeatBody.TYPE:

                if (debug)
                {
                    _logger.debug("Received heartbeat");
                }

                break;

            default:

        }

        _connection.bytesReceived(_protocolSession.getIoSession().getReadBytes());
    }

    private static int _messagesOut;

    public void messageSent(IoSession session, Object message) throws Exception
    {
        final long sentMessages = _messagesOut++;

        final boolean debug = _logger.isDebugEnabled();

        if (debug && ((sentMessages % 1000) == 0))
        {
            _logger.debug("Sent " + _messagesOut + " protocol messages");
        }

        _connection.bytesSent(session.getWrittenBytes());
        if (debug)
        {
            _logger.debug("Sent frame " + message);
        }
    }

    /*
      public void addFrameListener(AMQMethodListener listener)
      {
          _frameListeners.add(listener);
      }

      public void removeFrameListener(AMQMethodListener listener)
      {
          _frameListeners.remove(listener);
      }
     */
    public void attainState(AMQState s) throws AMQException
    {
        getStateManager().attainState(s);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent to calling
     * getProtocolSession().write().
     *
     * @param frame the frame to write
     */
    public void writeFrame(AMQDataBlock frame)
    {
        _protocolSession.writeFrame(frame);
    }

    public void writeFrame(AMQDataBlock frame, boolean wait)
    {
        _protocolSession.writeFrame(frame, wait);
    }

    /**
     * Convenience method that writes a frame to the protocol session and waits for a particular response. Equivalent to
     * calling getProtocolSession().write() then waiting for the response.
     *
     * @param frame
     * @param listener the blocking listener. Note the calling thread will block.
     */
    public AMQMethodEvent writeCommandFrameAndWaitForReply(AMQFrame frame, BlockingMethodFrameListener listener)
            throws AMQException, FailoverException
    {
        return writeCommandFrameAndWaitForReply(frame, listener, DEFAULT_SYNC_TIMEOUT);
    }

    /**
     * Convenience method that writes a frame to the protocol session and waits for a particular response. Equivalent to
     * calling getProtocolSession().write() then waiting for the response.
     *
     * @param frame
     * @param listener the blocking listener. Note the calling thread will block.
     */
    public AMQMethodEvent writeCommandFrameAndWaitForReply(AMQFrame frame, BlockingMethodFrameListener listener,
                                                           long timeout) throws AMQException, FailoverException
    {
        try
        {
            _frameListeners.add(listener);
            _protocolSession.writeFrame(frame);

            AMQMethodEvent e = listener.blockForFrame(timeout);

            return e;
            // When control resumes before this line, a reply will have been received
            // that matches the criteria defined in the blocking listener
        }
        catch (AMQException e)
        {
            throw e;
        }
        finally
        {
            // If we don't removeKey the listener then no-one will
            _frameListeners.remove(listener);
        }

    }

    /** More convenient method to write a frame and wait for it's response. */
    public AMQMethodEvent syncWrite(AMQFrame frame, Class responseClass) throws AMQException, FailoverException
    {
        return syncWrite(frame, responseClass, DEFAULT_SYNC_TIMEOUT);
    }

    /** More convenient method to write a frame and wait for it's response. */
    public AMQMethodEvent syncWrite(AMQFrame frame, Class responseClass, long timeout) throws AMQException, FailoverException
    {
        return writeCommandFrameAndWaitForReply(frame, new SpecificMethodFrameListener(frame.getChannel(), responseClass),
                                                timeout);
    }

    public void closeSession(AMQSession session) throws AMQException
    {
        _protocolSession.closeSession(session);
    }

    /**
     * Closes the connection.
     *
     * <p/>If a failover exception occurs whilst closing the connection it is ignored, as the connection is closed
     * anyway.
     *
     * @param timeout The timeout to wait for an acknowledgement to the close request.
     *
     * @throws AMQException If the close fails for any reason.
     */
    public void closeConnection(long timeout) throws AMQException
    {
        getStateManager().changeState(AMQState.CONNECTION_CLOSING);

        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        final AMQFrame frame =
                ConnectionCloseBody.createAMQFrame(0, _protocolSession.getProtocolMajorVersion(),
                                                   _protocolSession.getProtocolMinorVersion(), // AMQP version (major, minor)
                                                   0, // classId
                                                   0, // methodId
                                                   AMQConstant.REPLY_SUCCESS.getCode(), // replyCode
                                                   new AMQShortString("JMS client is closing the connection.")); // replyText

        try
        {
            syncWrite(frame, ConnectionCloseOkBody.class, timeout);
            _protocolSession.closeProtocolSession();
        }
        catch (AMQTimeoutException e)
        {
            _protocolSession.closeProtocolSession(false);
        }
        catch (FailoverException e)
        {
            _logger.debug("FailoverException interrupted connection close, ignoring as connection close anyway.");
        }
    }

    /** @return the number of bytes read from this protocol session */
    public long getReadBytes()
    {
        return _protocolSession.getIoSession().getReadBytes();
    }

    /** @return the number of bytes written to this protocol session */
    public long getWrittenBytes()
    {
        return _protocolSession.getIoSession().getWrittenBytes();
    }

    public void failover(String host, int port)
    {
        _failoverHandler.setHost(host);
        _failoverHandler.setPort(port);
        // see javadoc for FailoverHandler to see rationale for separate thread
        startFailoverThread();
    }

    public void blockUntilNotFailingOver() throws InterruptedException
    {
        if (_failoverLatch != null)
        {
            _failoverLatch.await();
        }
    }

    public AMQShortString generateQueueName()
    {
        return _protocolSession.generateQueueName();
    }

    public CountDownLatch getFailoverLatch()
    {
        return _failoverLatch;
    }

    public void setFailoverLatch(CountDownLatch failoverLatch)
    {
        _failoverLatch = failoverLatch;
    }

    public AMQConnection getConnection()
    {
        return _connection;
    }

    public AMQStateManager getStateManager()
    {
        return _stateManager;
    }

    public void setStateManager(AMQStateManager stateManager)
    {
        _stateManager = stateManager;
        _protocolSession.setStateManager(stateManager);
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }

    FailoverState getFailoverState()
    {
        return _failoverState;
    }

    public void setFailoverState(FailoverState failoverState)
    {
        _failoverState = failoverState;
    }

    public byte getProtocolMajorVersion()
    {
        return _protocolSession.getProtocolMajorVersion();
    }

    public byte getProtocolMinorVersion()
    {
        return _protocolSession.getProtocolMinorVersion();
    }
}
