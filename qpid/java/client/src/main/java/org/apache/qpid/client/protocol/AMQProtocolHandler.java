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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQConnectionClosedException;
import org.apache.qpid.AMQDisconnectedException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.HeartbeatListener;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverHandler;
import org.apache.qpid.client.failover.FailoverState;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateWaiter;
import org.apache.qpid.client.state.listener.SpecificMethodFrameListener;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.HeartbeatBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.thread.Threading;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.util.BytesDataOutput;

/**
 * AMQProtocolHandler is the client side protocol handler for AMQP, it handles all protocol events received from the
 * network by MINA. The primary purpose of AMQProtocolHandler is to translate the generic event model of MINA into the
 * specific event model of AMQP, by revealing the type of the received events (from decoded data), and passing the
 * event on to more specific handlers for the type. In this sense, it channels the richer event model of AMQP,
 * expressed in terms of methods and so on, through the cruder, general purpose event model of MINA, expressed in
 * terms of "message received" and so on.
 *
 * <p/>There is a 1:1 mapping between an AMQProtocolHandler and an {@link AMQConnection}. The connection class is
 * exposed to the end user of the AMQP client API, and also implements the JMS Connection API, so provides the public
 * API calls through which an individual connection can be manipulated. This protocol handler talks to the network
 * through MINA, in a behind the scenes role; it is not an exposed part of the client API.
 *
 * <p/>There is a 1:many mapping between an AMQProtocolHandler and a set of {@link AMQSession}s. At the MINA level,
 * there is one session per connection. At the AMQP level there can be many channels which are also called sessions in
 * JMS parlance. The {@link AMQSession}s are managed through an {@link AMQProtocolSession} instance. The protocol
 * session is similar to the MINA per-connection session, except that it can span the lifecycle of multiple MINA sessions
 * in the event of failover. See below for more information about this.
 *
 * <p/>Mina provides a session container that can be used to store/retrieve arbitrary objects as String named
 * attributes. A more convenient, type-safe, container for session data is provided in the form of
 * {@link AMQProtocolSession}.
 *
 * <p/>A common way to use MINA is to have a single instance of the event handler, and for MINA to pass in its session
 * object with every event, and for per-connection data to be held in the MINA session (perhaps using a type-safe wrapper
 * as described above). This event handler is different, because dealing with failover complicates things. To the
 * end client of an AMQConnection, a failed over connection is still handled through the same connection instance, but
 * behind the scenes a new transport connection, and MINA session will have been created. The MINA session object cannot
 * be used to track the state of the fail-over process, because it is destroyed and a new one is created, as the old
 * connection is shutdown and a new one created. For this reason, an AMQProtocolHandler is created per AMQConnection
 * and the protocol session data is held outside of the MINA IOSession.
 *
 * <p/>This handler is responsible for setting up the filter chain to filter all events for this handler through.
 * The filter chain is set up as a stack of event handers that perform the following functions (working upwards from
 * the network traffic at the bottom), handing off incoming events to an asynchronous thread pool to do the work,
 * optionally handling secure sockets encoding/decoding, encoding/decoding the AMQP format itself.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Maintain fail-over state.
 * <tr><td>
 * </table>
 *
 * @todo Use a single handler instance, by shifting everything to do with the 'protocol session' state, including
 * failover state, into AMQProtocolSession, and tracking that from AMQConnection? The lifecycles of
 * AMQProtocolSesssion and AMQConnection will be the same, so if there is high cohesion between them, they could
 * be merged, although there is sense in keeping the session model separate. Will clarify things by having data
 * held per protocol handler, per protocol session, per network connection, per channel, in separate classes, so
 * that lifecycles of the fields match lifecycles of their containing objects.
 */
public class AMQProtocolHandler implements ProtocolEngine
{
    /** Used for debugging. */
    private static final Logger _logger = LoggerFactory.getLogger(AMQProtocolHandler.class);
    private static final Logger _protocolLogger = LoggerFactory.getLogger("qpid.protocol");
    private static final boolean PROTOCOL_DEBUG = (System.getProperty("amqj.protocol.logging.level") != null);

    private static final long MAXIMUM_STATE_WAIT_TIME = Long.parseLong(System.getProperty("amqj.MaximumStateWait", "30000"));

    /**
     * The connection that this protocol handler is associated with. There is a 1-1 mapping between connection
     * instances and protocol handler instances.
     */
    private AMQConnection _connection;

    /** Our wrapper for a protocol session that provides access to session values in a typesafe manner. */
    private volatile AMQProtocolSession _protocolSession;

    /** Holds the state of the protocol session. */
    private AMQStateManager _stateManager;

    /** Holds the method listeners, */
    private final CopyOnWriteArraySet<AMQMethodListener> _frameListeners = new CopyOnWriteArraySet<AMQMethodListener>();

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

    /** The last failover exception that occurred */
    private FailoverException _lastFailoverException;

    /** Defines the default timeout to use for synchronous protocol commands. */
    private final long DEFAULT_SYNC_TIMEOUT = Long.getLong(ClientProperties.QPID_SYNC_OP_TIMEOUT,
                                                           Long.getLong(ClientProperties.AMQJ_DEFAULT_SYNCWRITE_TIMEOUT,
                                                                        ClientProperties.DEFAULT_SYNC_OPERATION_TIMEOUT));

    /** Object to lock on when changing the latch */
    private Object _failoverLatchChange = new Object();
    private AMQCodecFactory _codecFactory;

    private ProtocolVersion _suggestedProtocolVersion;

    private long _writtenBytes;
    private long _readBytes;

    private NetworkConnection _network;
    private Sender<ByteBuffer> _sender;
    private long _lastReadTime = System.currentTimeMillis();
    private long _lastWriteTime = System.currentTimeMillis();
    private HeartbeatListener _heartbeatListener = HeartbeatListener.DEFAULT;
    private Throwable _initialConnectionException;

    /**
     * Creates a new protocol handler, associated with the specified client connection instance.
     *
     * @param con The client connection that this is the event handler for.
     */
    public AMQProtocolHandler(AMQConnection con)
    {
        _connection = con;
        _protocolSession = new AMQProtocolSession(this, _connection);
        _stateManager = new AMQStateManager(_protocolSession);
        _codecFactory = new AMQCodecFactory(false, _protocolSession);
        _failoverHandler = new FailoverHandler(this);
    }

    /**
     * Called when the network connection is closed. This can happen, either because the client explicitly requested
     * that the connection be closed, in which case nothing is done, or because the connection died. In the case
     * where the connection died, an attempt to failover automatically to a new connection may be started. The failover
     * process will be started, provided that it is the clients policy to allow failover, and provided that a failover
     * has not already been started or failed.
     *
     * @todo Clarify: presumably exceptionCaught is called when the client is sending during a connection failure and
     * not otherwise? The above comment doesn't make that clear.
     */
    public void closed()
    {
        if (_connection.isClosed())
        {
            _logger.debug("Session closed called by client");
        }
        else
        {
            // Use local variable to keep flag whether fail-over allowed or not,
            // in order to execute AMQConnection#exceptionRecievedout out of synchronization block,
            // otherwise it might deadlock with failover mutex
            boolean failoverNotAllowed = false;
            boolean failedWithoutConnecting = false;
            Throwable initialConnectionException = null;
            synchronized (this)
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Session closed called with failover state " + _failoverState);
                }

                // reconnetablility was introduced here so as not to disturb the client as they have made their intentions
                // known through the policy settings.
                if (_failoverState == FailoverState.NOT_STARTED)
                {
                    // close the sender
                    try
                    {
                        _sender.close();
                    }
                    catch (Exception e)
                    {
                        _logger.warn("Exception occured on closing the sender", e);
                    }
                    if (_connection.failoverAllowed())
                    {
                        _failoverState = FailoverState.IN_PROGRESS;

                        _logger.debug("FAILOVER STARTING");
                        startFailoverThread();
                    }
                    else if (_connection.isConnected())
                    {
                        failoverNotAllowed = true;
                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Failover not allowed by policy:" + _connection.getFailoverPolicy());
                        }
                    }
                    else
                    {
                        failedWithoutConnecting = true;
                        initialConnectionException = _initialConnectionException;
                        _logger.debug("We are in process of establishing the initial connection");
                    }
                    _initialConnectionException = null;
                }
                else
                {
                    _logger.debug("Not starting the failover thread as state currently " + _failoverState);
                }
            }

            if (failoverNotAllowed)
            {
                _connection.exceptionReceived(new AMQDisconnectedException(
                        "Server closed connection and reconnection not permitted.", _stateManager.getLastException()));
            }
            else if(failedWithoutConnecting)
            {
                if(initialConnectionException == null)
                {
                    initialConnectionException = _stateManager.getLastException();
                }
                String message = initialConnectionException == null ? "" : initialConnectionException.getMessage();
                _connection.exceptionReceived(new AMQDisconnectedException(
                        "Connection could not be established: " + message, initialConnectionException));
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Protocol Session [" + this + "] closed");
        }
    }

    /** See {@link FailoverHandler} to see rationale for separate thread. */
    private void startFailoverThread()
    {
        if(!_connection.isClosed())
        {
            final Thread failoverThread;
            try
            {
                failoverThread = Threading.getThreadFactory().createThread(_failoverHandler);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to create thread", e);
            }
            failoverThread.setName("Failover");
            // Do not inherit daemon-ness from current thread as this can be a daemon
            // thread such as a AnonymousIoService thread.
            failoverThread.setDaemon(false);
            failoverThread.start();
        }
    }

    public void readerIdle()
    {
        _logger.debug("Protocol Session [" + this + "] idle: reader");
        //  failover:
        _logger.warn("Timed out while waiting for heartbeat from peer.");
        _network.close();
    }

    public void writerIdle()
    {
        _logger.debug("Protocol Session [" + this + "] idle: writer");
        writeFrame(HeartbeatBody.FRAME);
        _heartbeatListener.heartbeatSent();
    }

    /**
     * Invoked when any exception is thrown by the NetworkDriver
     */
    public void exception(Throwable cause)
    {
        boolean causeIsAConnectionProblem =
                cause instanceof AMQConnectionClosedException ||
                cause instanceof IOException ||
                cause instanceof TransportException;

        if (causeIsAConnectionProblem)
        {
            //ensure the IoSender and IoReceiver are closed
            try
            {
                _network.close();
            }
            catch (Exception e)
            {
                //ignore
            }
        }
        FailoverState state = getFailoverState();
        if (state == FailoverState.NOT_STARTED)
        {
            if (causeIsAConnectionProblem)
            {
                _logger.info("Connection exception caught therefore going to attempt failover: " + cause, cause);
                _initialConnectionException = cause;
            }
            else
            {
                _connection.exceptionReceived(cause);
            }

            // FIXME Need to correctly handle other exceptions. Things like ...
            // AMQChannelClosedException
            // which will cause the JMSSession to end due to a channel close and so that Session needs
            // to be removed from the map so we can correctly still call close without an exception when trying to close
            // the server closed session.  See also CloseChannelMethodHandler as the sessionClose is never called on exception
        }
        // we reach this point if failover was attempted and failed therefore we need to let the calling app
        // know since we cannot recover the situation
        else if (state == FailoverState.FAILED)
        {
            _logger.error("Exception caught by protocol handler: " + cause, cause);

            // we notify the state manager of the error in case we have any clients waiting on a state
            // change. Those "waiters" will be interrupted and can handle the exception
            AMQException amqe = new AMQException("Protocol handler error: " + cause, cause);
            propagateExceptionToAllWaiters(amqe);
            _connection.exceptionReceived(cause);
        }
        else
        {
            _logger.warn("Exception caught by protocol handler: " + cause, cause);
        }
    }

    /**
     * There are two cases where we have other threads potentially blocking for events to be handled by this class.
     * These are for the state manager (waiting for a state change) or a frame listener (waiting for a particular type
     * of frame to arrive). When an error occurs we need to notify these waiters so that they can react appropriately.
     *
     * This should be called only when the exception is fatal for the connection.
     *
     * @param e the exception to propagate
     *
     * @see #propagateExceptionToFrameListeners
     */
    public void propagateExceptionToAllWaiters(Exception e)
    {
        getStateManager().error(e);

        propagateExceptionToFrameListeners(e);
    }

    /**
     * This caters for the case where we only need to propagate an exception to the the frame listeners to interupt any
     * protocol level waits.
     *
     * This will would normally be used to notify all Frame Listeners that Failover is about to occur and they should
     * stop waiting and relinquish the Failover lock {@see FailoverHandler}.
     *
     * Once the {@link FailoverHandler} has re-established the connection then the listeners will be able to re-attempt
     * their protocol request and so listen again for the correct frame.
     *
     * @param e the exception to propagate
     */
    public void propagateExceptionToFrameListeners(Exception e)
    {
        synchronized (_frameListeners)
        {
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
    }

    public void notifyFailoverStarting()
    {
        // Set the last exception in the sync block to ensure the ordering with add.
        // either this gets done and the add does the ml.error
        // or the add completes first and the iterator below will do ml.error
        synchronized (_frameListeners)
        {
            _lastFailoverException = new FailoverException("Failing over about to start");
        }

        //Only notify the Frame listeners that failover is going to occur as the State listeners shouldn't be
        // interrupted unless failover cannot restore the state.
        propagateExceptionToFrameListeners(_lastFailoverException);
    }

    public void failoverInProgress()
    {
        _lastFailoverException = null;
    }

    private static int _messageReceivedCount;


    public void received(ByteBuffer msg)
    {
        _readBytes += msg.remaining();
        _lastReadTime = System.currentTimeMillis();
        try
        {
            final ArrayList<AMQDataBlock> dataBlocks = _codecFactory.getDecoder().decodeBuffer(msg);

            // Decode buffer
            int size = dataBlocks.size();
            for (int i = 0; i < size; i++)
            {
                AMQDataBlock message = dataBlocks.get(i);
                    if (PROTOCOL_DEBUG)
                    {
                        _protocolLogger.info(String.format("RECV: [%s] %s", this, message));
                    }

                    if(message instanceof AMQFrame)
                    {

                        final long msgNumber = ++_messageReceivedCount;

                        if (((msgNumber % 1000) == 0) && _logger.isDebugEnabled())
                        {
                            _logger.debug("Received " + _messageReceivedCount + " protocol messages");
                        }

                        AMQFrame frame = (AMQFrame) message;

                        final AMQBody bodyFrame = frame.getBodyFrame();

                        bodyFrame.handle(frame.getChannel(), _protocolSession);

                        _connection.bytesReceived(_readBytes);
                    }
                    else if (message instanceof ProtocolInitiation)
                    {
                        // We get here if the server sends a response to our initial protocol header
                        // suggesting an alternate ProtocolVersion; the server will then close the
                        // connection.
                        ProtocolInitiation protocolInit = (ProtocolInitiation) message;
                        _suggestedProtocolVersion = protocolInit.checkVersion();
                        _logger.info("Broker suggested using protocol version:" + _suggestedProtocolVersion);

                        // get round a bug in old versions of qpid whereby the connection is not closed
                        _stateManager.changeState(AMQState.CONNECTION_CLOSED);
                    }
                }
        }
        catch (Exception e)
        {
            _logger.error("Exception processing frame", e);
            propagateExceptionToFrameListeners(e);
            exception(e);
        }


    }

    public void methodBodyReceived(final int channelId, final AMQBody bodyFrame)
            throws AMQException
    {

        if (_logger.isDebugEnabled())
        {
            _logger.debug("(" + System.identityHashCode(this) + ")Method frame received: " + bodyFrame);
        }

        final AMQMethodEvent<AMQMethodBody> evt =
                new AMQMethodEvent<AMQMethodBody>(channelId, (AMQMethodBody) bodyFrame);

        try
        {

            boolean wasAnyoneInterested = getStateManager().methodReceived(evt);
            synchronized (_frameListeners)
            {
                if (!_frameListeners.isEmpty())
                {
                    //This iterator is safe from the error state as the frame listeners always add before they send so their
                    // will be ready and waiting for this response.
                    Iterator it = _frameListeners.iterator();
                    while (it.hasNext())
                    {
                        final AMQMethodListener listener = (AMQMethodListener) it.next();
                        wasAnyoneInterested = listener.methodReceived(evt) || wasAnyoneInterested;
                    }
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
            propagateExceptionToFrameListeners(e);

            exception(e);
        }

    }

    private static int _messagesOut;

    public StateWaiter createWaiter(Set<AMQState> states) throws AMQException
    {
        return getStateManager().createWaiter(states);
    }

    public void writeFrame(AMQDataBlock frame)
    {
        writeFrame(frame, true);
    }

    public  synchronized void writeFrame(AMQDataBlock frame, boolean flush)
    {
        final ByteBuffer buf = asByteBuffer(frame);
        _lastWriteTime = System.currentTimeMillis();
        _writtenBytes += buf.remaining();
        _sender.send(buf);
        if(flush)
        {
            _sender.flush();
        }

        if (PROTOCOL_DEBUG)
        {
            _protocolLogger.debug(String.format("SEND: [%s] %s", this, frame));
        }

        final long sentMessages = _messagesOut++;

        final boolean debug = _logger.isDebugEnabled();

        if (debug && ((sentMessages % 1000) == 0))
        {
            _logger.debug("Sent " + _messagesOut + " protocol messages");
        }

        _connection.bytesSent(_writtenBytes);

    }

    private static final int REUSABLE_BYTE_BUFFER_CAPACITY = 65 * 1024;
    private final byte[] _reusableBytes = new byte[REUSABLE_BYTE_BUFFER_CAPACITY];
    private final ByteBuffer _reusableByteBuffer = ByteBuffer.wrap(_reusableBytes);
    private final BytesDataOutput _reusableDataOutput = new BytesDataOutput(_reusableBytes);

    private ByteBuffer asByteBuffer(AMQDataBlock block)
    {
        final int size = (int) block.getSize();

        final byte[] data;


        if(size > REUSABLE_BYTE_BUFFER_CAPACITY)
        {
            data= new byte[size];
        }
        else
        {

            data = _reusableBytes;
        }
        _reusableDataOutput.setBuffer(data);

        try
        {
            block.writePayload(_reusableDataOutput);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        final ByteBuffer buf;

        if(size < REUSABLE_BYTE_BUFFER_CAPACITY)
        {
            buf = _reusableByteBuffer;
            buf.position(0);
        }
        else
        {
            buf = ByteBuffer.wrap(data);
        }
        buf.limit(_reusableDataOutput.length());

        return buf;
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
            synchronized (_frameListeners)
            {
                if (_lastFailoverException != null)
                {
                    throw _lastFailoverException;
                }

                if(_stateManager.getCurrentState() == AMQState.CONNECTION_CLOSED ||
                        _stateManager.getCurrentState() == AMQState.CONNECTION_CLOSING)
                {
                    Exception e = _stateManager.getLastException();
                    if (e != null)
                    {
                        if (e instanceof AMQException)
                        {
                            AMQException amqe = (AMQException) e;

                            throw amqe.cloneForCurrentThread();
                        }
                        else
                        {
                            throw new AMQException(AMQConstant.INTERNAL_ERROR, e.getMessage(), e);
                        }
                    }
                }

                _frameListeners.add(listener);
                //FIXME: At this point here we should check or before add we should check _stateManager is in an open
                // state so as we don't check we are likely just to time out here as I believe is being seen in QPID-1255
            }
            writeFrame(frame);

            long actualTimeout = timeout == -1 ? DEFAULT_SYNC_TIMEOUT : timeout;
            return listener.blockForFrame(actualTimeout);
            // When control resumes before this line, a reply will have been received
            // that matches the criteria defined in the blocking listener
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
     * @param timeout The timeout to wait for an acknowledgment to the close request.
     *
     * @throws AMQException If the close fails for any reason.
     */
    public void closeConnection(long timeout) throws AMQException
    {
        if (!getStateManager().getCurrentState().equals(AMQState.CONNECTION_CLOSED))
        {
            // Connection is already closed then don't do a syncWrite
            try
            {
                final ConnectionCloseBody body = _protocolSession.getMethodRegistry().createConnectionCloseBody(AMQConstant.REPLY_SUCCESS.getCode(), // replyCode
                        new AMQShortString("JMS client is closing the connection."), 0, 0);
                final AMQFrame frame = body.generateFrame(0);

                syncWrite(frame, ConnectionCloseOkBody.class, timeout);
                _network.close();
                closed();
            }
            catch (AMQTimeoutException e)
            {
                closed();
            }
            catch (FailoverException e)
            {
                _logger.debug("FailoverException interrupted connection close, ignoring as connection closed anyway.");
            }
        }
    }

    /** @return the number of bytes read from this protocol session */
    public long getReadBytes()
    {
        return _readBytes;
    }

    /** @return the number of bytes written to this protocol session */
    public long getWrittenBytes()
    {
        return _writtenBytes;
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
        synchronized(_failoverLatchChange)
        {
            if (_failoverLatch != null)
            {
                if(!_failoverLatch.await(MAXIMUM_STATE_WAIT_TIME, TimeUnit.MILLISECONDS))
                {

                }
            }
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
        synchronized (_failoverLatchChange)
        {
            _failoverLatch = failoverLatch;
        }
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
        _stateManager.setProtocolSession(_protocolSession);
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }

    synchronized FailoverState getFailoverState()
    {
        return _failoverState;
    }

    public synchronized void setFailoverState(FailoverState failoverState)
    {
        _failoverState= failoverState;
    }

    public byte getProtocolMajorVersion()
    {
        return _protocolSession.getProtocolMajorVersion();
    }

    public byte getProtocolMinorVersion()
    {
        return _protocolSession.getProtocolMinorVersion();
    }

    public MethodRegistry getMethodRegistry()
    {
        return _protocolSession.getMethodRegistry();
    }

    public ProtocolVersion getProtocolVersion()
    {
        return _protocolSession.getProtocolVersion();
    }

    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _network.getLocalAddress();
    }

    public void setNetworkConnection(NetworkConnection network)
    {
        setNetworkConnection(network, network.getSender());
    }

    public void setNetworkConnection(NetworkConnection network, Sender<ByteBuffer> sender)
    {
        _network = network;
        _sender = sender;
    }

    @Override
    public long getLastReadTime()
    {
        return _lastReadTime;
    }

    @Override
    public long getLastWriteTime()
    {
        return _lastWriteTime;
    }

    protected Sender<ByteBuffer> getSender()
    {
        return _sender;
    }

    void initHeartbeats(int delay, float timeoutFactor)
    {
        if (delay > 0)
        {
            _network.setMaxWriteIdle(delay);
            int readerIdle = (int)(delay * timeoutFactor);
            _network.setMaxReadIdle(readerIdle);
        }
    }

    public NetworkConnection getNetworkConnection()
    {
        return _network;
    }

    public ProtocolVersion getSuggestedProtocolVersion()
    {
        return _suggestedProtocolVersion;
    }


    public void setHeartbeatListener(HeartbeatListener listener)
    {
        _heartbeatListener = listener == null ? HeartbeatListener.DEFAULT : listener;
    }

    public void heartbeatBodyReceived()
    {
        _heartbeatListener.heartbeatReceived();
    }
}
