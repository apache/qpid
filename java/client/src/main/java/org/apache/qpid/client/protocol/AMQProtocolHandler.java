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
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.failover.FailoverHandler;
import org.apache.qpid.client.failover.FailoverState;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.listener.SpecificMethodFrameListener;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.ssl.BogusSSLContextFactory;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

public class AMQProtocolHandler extends IoHandlerAdapter
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolHandler.class);

    /**
     * The connection that this protocol handler is associated with. There is a 1-1
     * mapping between connection instances and protocol handler instances.
     */
    private AMQConnection _connection;

    /**
     * Used only when determining whether to add the SSL filter or not. This should be made more
     * generic in future since we will potentially have many transport layer options
     */
    private boolean _useSSL;

    /**
     * Our wrapper for a protocol session that provides access to session values
     * in a typesafe manner.
     */
    private volatile AMQProtocolSession _protocolSession;

//    private AMQStateManager _stateManager = new AMQStateManager();

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

    private CountDownLatch _failoverLatch;

    public AMQProtocolHandler(AMQConnection con)
    {
        _connection = con;

        // We add a proxy for the state manager so that we can substitute the state manager easily in this class.
        // We substitute the state manager when performing failover
/*        _frameListeners.add(new AMQMethodListener()
        {
            public boolean methodReceived(AMQMethodEvent evt) throws AMQException
            {
                return _stateManager.methodReceived(evt);
            }

            public void error(Exception e)
            {
                _stateManager.error(e);
            }
        });*/
    }

    public boolean isUseSSL()
    {
        return _useSSL;
    }

    public void setUseSSL(boolean useSSL)
    {
        _useSSL = useSSL;
    }

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
        if (_useSSL)
        {
            //FIXME: Bogus context cannot be used in production.
            SSLFilter sslFilter = new SSLFilter(BogusSSLContextFactory.getInstance(false));
            sslFilter.setUseClientMode(true);
            session.getFilterChain().addBefore("protocolFilter", "ssl", sslFilter);
        }

        _protocolSession = new AMQProtocolSession(this, session, _connection);
        _protocolSession.init();
    }

    public void sessionOpened(IoSession session) throws Exception
    {
        System.setProperty("foo", "bar");
    }

    /**
     * When the broker connection dies we can either get sessionClosed() called or exceptionCaught() followed by
     * sessionClosed() depending on whether we were trying to send data at the time of failure.
     *
     * @param session
     * @throws Exception
     */
    public void sessionClosed(IoSession session) throws Exception
    {
        if (_connection.isClosed())
        {
            _logger.info("Session closed called by client");
        }
        else
        {
            _logger.info("Session closed called with failover state currently " + _failoverState);

            //reconnetablility was introduced here so as not to disturb the client as they have made their intentions
            // known through the policy settings.

            if ((_failoverState != FailoverState.IN_PROGRESS) && _connection.failoverAllowed())
            {
                _logger.info("FAILOVER STARTING");
                if (_failoverState == FailoverState.NOT_STARTED)
                {
                    _failoverState = FailoverState.IN_PROGRESS;
                    startFailoverThread();
                }
                else
                {
                    _logger.info("Not starting failover as state currently " + _failoverState);
                }
            }
            else
            {
                _logger.info("Failover not allowed by policy.");

                if (_logger.isDebugEnabled())
                {
                    _logger.debug(_connection.getFailoverPolicy().toString());
                }

                if (_failoverState != FailoverState.IN_PROGRESS)
                {
                    _logger.info("sessionClose() not allowed to failover");
                    _connection.exceptionReceived(
                            new AMQDisconnectedException("Server closed connection and reconnection " +
                                                         "not permitted."));
                }
                else
                {
                    _logger.info("sessionClose() failover in progress");
                }
            }
        }

        _logger.info("Protocol Session [" + this + "] closed");
    }

    /**
     * See {@link FailoverHandler} to see rationale for separate thread.
     */
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
            //write heartbeat frame:
            _logger.debug("Sent heartbeat");
            session.write(HeartbeatBody.FRAME);
            HeartbeatDiagnostics.sent();
        }
        else if (IdleStatus.READER_IDLE.equals(status))
        {
            //failover:
            HeartbeatDiagnostics.timeout();
            _logger.warn("Timed out while waiting for heartbeat from peer.");
            session.close();
        }
    }

    public void exceptionCaught(IoSession session, Throwable cause) throws Exception
    {
        if (_failoverState == FailoverState.NOT_STARTED)
        {
            //if (!(cause instanceof AMQUndeliveredException) && (!(cause instanceof AMQAuthenticationException)))
            if (cause instanceof AMQConnectionClosedException)
            {
                _logger.info("Exception caught therefore going to attempt failover: " + cause, cause);
                // this will attemp failover

                sessionClosed(session);
            }
        }
        // we reach this point if failover was attempted and failed therefore we need to let the calling app
        // know since we cannot recover the situation
        else if (_failoverState == FailoverState.FAILED)
        {
            _logger.error("Exception caught by protocol handler: " + cause, cause);
            // we notify the state manager of the error in case we have any clients waiting on a state
            // change. Those "waiters" will be interrupted and can handle the exception
            AMQException amqe = new AMQException("Protocol handler error: " + cause, cause);
            propagateExceptionToWaiters(amqe);
            _connection.exceptionReceived(cause);
        }
    }

    /**
     * There are two cases where we have other threads potentially blocking for events to be handled by this
     * class. These are for the state manager (waiting for a state change) or a frame listener (waiting for a
     * particular type of frame to arrive). When an error occurs we need to notify these waiters so that they can
     * react appropriately.
     *
     * @param e the exception to propagate
     */
    public void propagateExceptionToWaiters(Exception e)
    {
        _protocolSession.getStateManager().error(e);
        if(!_frameListeners.isEmpty())
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
        final long msgNumber = ++_messageReceivedCount;

        if (_logger.isDebugEnabled() && (msgNumber % 1000 == 0))
        {
            _logger.debug("Received " + _messageReceivedCount + " protocol messages");
        }

        AMQFrame frame = (AMQFrame) message;

        HeartbeatDiagnostics.received(frame.bodyFrame instanceof HeartbeatBody);

        if (frame.bodyFrame instanceof AMQMethodBody)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Method frame received: " + frame);
            }

            final AMQMethodEvent<AMQMethodBody> evt = new AMQMethodEvent<AMQMethodBody>(frame.channel, (AMQMethodBody) frame.bodyFrame);

            try
            {

                boolean wasAnyoneInterested = _protocolSession.getStateManager().methodReceived(evt);
                if(!_frameListeners.isEmpty())
                {
                    Iterator it = _frameListeners.iterator();
                    while (it.hasNext())
                    {
                        final AMQMethodListener listener = (AMQMethodListener) it.next();
                        wasAnyoneInterested = listener.methodReceived(evt) || wasAnyoneInterested;
                    }
                }
                if (!wasAnyoneInterested)
                {
                    throw new AMQException("AMQMethodEvent " + evt + " was not processed by any listener.");
                }
            }
            catch (AMQException e)
            {
                _protocolSession.getStateManager().error(e);
                if(!_frameListeners.isEmpty())
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
        }
        else if (frame.bodyFrame instanceof ContentHeaderBody)
        {
            _protocolSession.messageContentHeaderReceived(frame.channel,
                                                          (ContentHeaderBody) frame.bodyFrame);
        }
        else if (frame.bodyFrame instanceof ContentBody)
        {
            _protocolSession.messageContentBodyReceived(frame.channel,
                                                        (ContentBody) frame.bodyFrame);
        }
        else if (frame.bodyFrame instanceof HeartbeatBody)
        {
            _logger.debug("Received heartbeat");
        }
        _connection.bytesReceived(_protocolSession.getIoSession().getReadBytes());
    }

    private static int _messagesOut;

    public void messageSent(IoSession session, Object message) throws Exception
    {
        final long sentMessages = _messagesOut++;

        final boolean debug = _logger.isDebugEnabled();

        if (debug && (sentMessages % 1000 == 0))
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
        _protocolSession.getStateManager().attainState(s);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent
     * to calling getProtocolSession().write().
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
     * Convenience method that writes a frame to the protocol session and waits for
     * a particular response. Equivalent to calling getProtocolSession().write() then
     * waiting for the response.
     *
     * @param frame
     * @param listener the blocking listener. Note the calling thread will block.
     */
    private AMQMethodEvent writeCommandFrameAndWaitForReply(AMQFrame frame,
                                                            BlockingMethodFrameListener listener)
            throws AMQException
    {
        try
        {
            _frameListeners.add(listener);
            _protocolSession.writeFrame(frame);

            AMQMethodEvent e = listener.blockForFrame();
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

    /**
     * More convenient method to write a frame and wait for it's response.
     */
    public AMQMethodEvent syncWrite(AMQFrame frame, Class responseClass) throws AMQException
    {
        return writeCommandFrameAndWaitForReply(frame,
                                                new SpecificMethodFrameListener(frame.channel, responseClass));
    }

    /**
     * Convenience method to register an AMQSession with the protocol handler. Registering
     * a session with the protocol handler will ensure that messages are delivered to the
     * consumer(s) on that session.
     *
     * @param channelId the channel id of the session
     * @param session   the session instance.
     */
    public void addSessionByChannel(int channelId, AMQSession session)
    {
        _protocolSession.addSessionByChannel(channelId, session);
    }

    /**
     * Convenience method to deregister an AMQSession with the protocol handler.
     *
     * @param channelId then channel id of the session
     */
    public void removeSessionByChannel(int channelId)
    {
        _protocolSession.removeSessionByChannel(channelId);
    }

    public void closeSession(AMQSession session) throws AMQException
    {
        _protocolSession.closeSession(session);
    }

    public void closeConnection() throws AMQException
    {
        _protocolSession.getStateManager().changeState(AMQState.CONNECTION_CLOSING);

        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        final AMQFrame frame = ConnectionCloseBody.createAMQFrame(0,
            (byte)8, (byte)0,	// AMQP version (major, minor)
            0,	// classId
            0,	// methodId
            AMQConstant.REPLY_SUCCESS.getCode(),	// replyCode
            new AMQShortString("JMS client is closing the connection."));	// replyText
        syncWrite(frame, ConnectionCloseOkBody.class);

        _protocolSession.closeProtocolSession();
    }

    /**
     * @return the number of bytes read from this protocol session
     */
    public long getReadBytes()
    {
        return _protocolSession.getIoSession().getReadBytes();
    }

    /**
     * @return the number of bytes written to this protocol session
     */
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
        return _protocolSession.getStateManager();
    }

    public void setStateManager(AMQStateManager stateManager)
    {
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
}
