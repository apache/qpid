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

import org.apache.commons.lang.StringUtils;
import org.apache.mina.common.CloseFuture;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.security.sasl.SaslClient;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.message.ReturnMessage;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.message.UnprocessedMessage_0_8;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.client.handler.ClientMethodDispatcherImpl;

/**
 * Wrapper for protocol session that provides type-safe access to session attributes. <p/> The underlying protocol
 * session is still available but clients should not use it to obtain session attributes.
 */
public class AMQProtocolSession implements AMQVersionAwareProtocolSession
{
    protected static final int LAST_WRITE_FUTURE_JOIN_TIMEOUT = 1000 * 60 * 2;

    protected static final Logger _logger = LoggerFactory.getLogger(AMQProtocolSession.class);

    public static final String PROTOCOL_INITIATION_RECEIVED = "ProtocolInitiatiionReceived";

    protected static final String CONNECTION_TUNE_PARAMETERS = "ConnectionTuneParameters";

    protected static final String AMQ_CONNECTION = "AMQConnection";

    protected static final String SASL_CLIENT = "SASLClient";

    protected final IoSession _minaProtocolSession;

    private AMQStateManager _stateManager;

    protected WriteFuture _lastWriteFuture;

    /**
     * The handler from which this session was created and which is used to handle protocol events. We send failover
     * events to the handler.
     */
    protected final AMQProtocolHandler _protocolHandler;

    /** Maps from the channel id to the AMQSession that it represents. */
    protected ConcurrentMap<Integer, AMQSession> _channelId2SessionMap = new ConcurrentHashMap<Integer, AMQSession>();

    protected ConcurrentMap _closingChannels = new ConcurrentHashMap();

    /**
     * Maps from a channel id to an unprocessed message. This is used to tie together the JmsDeliverBody (which arrives
     * first) with the subsequent content header and content bodies.
     */
    protected ConcurrentMap<Integer,UnprocessedMessage_0_8> _channelId2UnprocessedMsgMap = new ConcurrentHashMap<Integer,UnprocessedMessage_0_8>();

    /** Counter to ensure unique queue names */
    protected int _queueId = 1;
    protected final Object _queueIdLock = new Object();

    private ProtocolVersion _protocolVersion;
//    private VersionSpecificRegistry _registry =
//        MainRegistry.getVersionSpecificRegistry(ProtocolVersion.getLatestSupportedVersion());


    private MethodRegistry _methodRegistry =
            MethodRegistry.getMethodRegistry(ProtocolVersion.getLatestSupportedVersion());


    private MethodDispatcher _methodDispatcher;


    private final AMQConnection _connection;    

    public AMQProtocolSession(AMQProtocolHandler protocolHandler, IoSession protocolSession, AMQConnection connection)
    {
        this(protocolHandler, protocolSession, connection, new AMQStateManager());

    }

    public AMQProtocolSession(AMQProtocolHandler protocolHandler, IoSession protocolSession, AMQConnection connection,
        AMQStateManager stateManager)
    {
        _protocolHandler = protocolHandler;
        _minaProtocolSession = protocolSession;
        _minaProtocolSession.setAttachment(this);
        // properties of the connection are made available to the event handlers
        _minaProtocolSession.setAttribute(AMQ_CONNECTION, connection);
        // fixme - real value needed
        _minaProtocolSession.setWriteTimeout(LAST_WRITE_FUTURE_JOIN_TIMEOUT);
        _stateManager = stateManager;
        _stateManager.setProtocolSession(this);
        _protocolVersion = connection.getProtocolVersion();
        _methodDispatcher = ClientMethodDispatcherImpl.newMethodDispatcher(ProtocolVersion.getLatestSupportedVersion(),
                                                                 stateManager);
        _connection = connection;

    }

    public void init()
    {
        // start the process of setting up the connection. This is the first place that
        // data is written to the server.

        _minaProtocolSession.write(new ProtocolInitiation(_connection.getProtocolVersion()));
    }

    public String getClientID()
    {
        try
        {
            return getAMQConnection().getClientID();
        }
        catch (JMSException e)
        {
            // we never throw a JMSException here
            return null;
        }
    }

    public void setClientID(String clientID) throws JMSException
    {
        getAMQConnection().setClientID(clientID);
    }

    public AMQStateManager getStateManager()
    {
        return _stateManager;
    }

    public void setStateManager(AMQStateManager stateManager)
    {
        _stateManager = stateManager;
        _methodDispatcher = ClientMethodDispatcherImpl.newMethodDispatcher(_protocolVersion,
                                                                 stateManager);         
    }

    public String getVirtualHost()
    {
        return getAMQConnection().getVirtualHost();
    }

    public String getUsername()
    {
        return getAMQConnection().getUsername();
    }

    public String getPassword()
    {
        return getAMQConnection().getPassword();
    }

    public IoSession getIoSession()
    {
        return _minaProtocolSession;
    }

    public SaslClient getSaslClient()
    {
        return (SaslClient) _minaProtocolSession.getAttribute(SASL_CLIENT);
    }

    /**
     * Store the SASL client currently being used for the authentication handshake
     *
     * @param client if non-null, stores this in the session. if null clears any existing client being stored
     */
    public void setSaslClient(SaslClient client)
    {
        if (client == null)
        {
            _minaProtocolSession.removeAttribute(SASL_CLIENT);
        }
        else
        {
            _minaProtocolSession.setAttribute(SASL_CLIENT, client);
        }
    }

    public ConnectionTuneParameters getConnectionTuneParameters()
    {
        return (ConnectionTuneParameters) _minaProtocolSession.getAttribute(CONNECTION_TUNE_PARAMETERS);
    }

    public void setConnectionTuneParameters(ConnectionTuneParameters params)
    {
        _minaProtocolSession.setAttribute(CONNECTION_TUNE_PARAMETERS, params);
        AMQConnection con = getAMQConnection();
        con.setMaximumChannelCount(params.getChannelMax());
        con.setMaximumFrameSize(params.getFrameMax());
        initHeartbeats((int) params.getHeartbeat());
    }

    /**
     * Callback invoked from the BasicDeliverMethodHandler when a message has been received. This is invoked on the MINA
     * dispatcher thread.
     *
     * @param message
     *
     * @throws AMQException if this was not expected
     */
    public void unprocessedMessageReceived(UnprocessedMessage_0_8 message) throws AMQException
    {
        _channelId2UnprocessedMsgMap.put(message.getChannelId(), message);
    }

    public void messageContentHeaderReceived(int channelId, ContentHeaderBody contentHeader) throws AMQException
    {
        UnprocessedMessage_0_8 msg = (UnprocessedMessage_0_8) _channelId2UnprocessedMsgMap.get(channelId);
        if (msg == null)
        {
            throw new AMQException(null, "Error: received content header without having received a BasicDeliver frame first", null);
        }

        if (msg.getContentHeader() != null)
        {
            throw new AMQException(null, "Error: received duplicate content header or did not receive correct number of content body frames", null);
        }

        msg.setContentHeader(contentHeader);
        if (contentHeader.bodySize == 0)
        {
            deliverMessageToAMQSession(channelId, msg);
        }
    }

    public void messageContentBodyReceived(int channelId, ContentBody contentBody) throws AMQException
    {
        UnprocessedMessage_0_8 msg = _channelId2UnprocessedMsgMap.get(channelId);
        if (msg == null)
        {
            throw new AMQException(null, "Error: received content body without having received a JMSDeliver frame first", null);
        }

        if (msg.getContentHeader() == null)
        {
            _channelId2UnprocessedMsgMap.remove(channelId);
            throw new AMQException(null, "Error: received content body without having received a ContentHeader frame first", null);
        }

        /*try
        {*/
        msg.receiveBody(contentBody);
        /*}
        catch (UnexpectedBodyReceivedException e)
        {
            _channelId2UnprocessedMsgMap.remove(channelId);
            throw e;
        }*/

        if (msg.isAllBodyDataReceived())
        {
            deliverMessageToAMQSession(channelId, msg);
        }
    }

    /**
     * Deliver a message to the appropriate session, removing the unprocessed message from our map
     *
     * @param channelId the channel id the message should be delivered to
     * @param msg       the message
     */
    private void deliverMessageToAMQSession(int channelId, UnprocessedMessage msg)
    {
        AMQSession session = getSession(channelId);
        session.messageReceived(msg);
        _channelId2UnprocessedMsgMap.remove(channelId);
    }

    protected AMQSession getSession(int channelId)
    {
        return _connection.getSession(channelId);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent to calling
     * getProtocolSession().write().
     *
     * @param frame the frame to write
     */
    public void writeFrame(AMQDataBlock frame)
    {
        writeFrame(frame, false);
    }

    public void writeFrame(AMQDataBlock frame, boolean wait)
    {
        WriteFuture f = _minaProtocolSession.write(frame);
        if (wait)
        {
            // fixme -- time out?
            f.join();
        }
        else
        {
            _lastWriteFuture = f;
        }
    }

    /**
     * Starts the process of closing a session
     *
     * @param session the AMQSession being closed
     */
    public void closeSession(AMQSession session)
    {
        _logger.debug("closeSession called on protocol session for session " + session.getChannelId());
        final int channelId = session.getChannelId();
        if (channelId <= 0)
        {
            throw new IllegalArgumentException("Attempt to close a channel with id < 0");
        }
        // we need to know when a channel is closing so that we can respond
        // with a channel.close frame when we receive any other type of frame
        // on that channel
        _closingChannels.putIfAbsent(channelId, session);
    }

    /**
     * Called from the ChannelClose handler when a channel close frame is received. This method decides whether this is
     * a response or an initiation. The latter case causes the AMQSession to be closed and an exception to be thrown if
     * appropriate.
     *
     * @param channelId the id of the channel (session)
     *
     * @return true if the client must respond to the server, i.e. if the server initiated the channel close, false if
     *         the channel close is just the server responding to the client's earlier request to close the channel.
     */
    public boolean channelClosed(int channelId, AMQConstant code, String text) throws AMQException
    {

        // if this is not a response to an earlier request to close the channel
        if (_closingChannels.remove(channelId) == null)
        {
            final AMQSession session = getSession(channelId);
            try
            {
                session.closed(new AMQException(code, text, null));
            }
            catch (JMSException e)
            {
                throw new AMQException(null, "JMSException received while closing session", e);
            }

            return true;
        }
        else
        {
            return false;
        }
    }

    public AMQConnection getAMQConnection()
    {
        return (AMQConnection) _minaProtocolSession.getAttribute(AMQ_CONNECTION);
    }

    public void closeProtocolSession()
    {
        closeProtocolSession(true);
    }

    public void closeProtocolSession(boolean waitLast)
    {
        _logger.debug("Waiting for last write to join.");
        if (waitLast && (_lastWriteFuture != null))
        {
            _lastWriteFuture.join(LAST_WRITE_FUTURE_JOIN_TIMEOUT);
        }

        _logger.debug("Closing protocol session");
        final CloseFuture future = _minaProtocolSession.close();
        future.join(LAST_WRITE_FUTURE_JOIN_TIMEOUT);
    }

    public void failover(String host, int port)
    {
        _protocolHandler.failover(host, port);
    }

    protected AMQShortString generateQueueName()
    {
        int id;
        synchronized (_queueIdLock)
        {
            id = _queueId++;
        }
        // get rid of / and : and ; from address for spec conformance
        String localAddress = StringUtils.replaceChars(_minaProtocolSession.getLocalAddress().toString(), "/;:", "");

        return new AMQShortString("tmp_" + localAddress + "_" + id);
    }

    /** @param delay delay in seconds (not ms) */
    void initHeartbeats(int delay)
    {
        if (delay > 0)
        {
            _minaProtocolSession.setIdleTime(IdleStatus.WRITER_IDLE, delay);
            _minaProtocolSession.setIdleTime(IdleStatus.READER_IDLE, HeartbeatConfig.CONFIG.getTimeout(delay));
            HeartbeatDiagnostics.init(delay, HeartbeatConfig.CONFIG.getTimeout(delay));
        }
    }

    public void confirmConsumerCancelled(int channelId, AMQShortString consumerTag)
    {
        final AMQSession session = getSession(channelId);

        session.confirmConsumerCancelled(consumerTag);
    }

    public void setProtocolVersion(final ProtocolVersion pv)
    {
        _protocolVersion = pv;
        _methodRegistry = MethodRegistry.getMethodRegistry(pv);
        _methodDispatcher = ClientMethodDispatcherImpl.newMethodDispatcher(pv, _stateManager);

      //  _registry = MainRegistry.getVersionSpecificRegistry(versionMajor, versionMinor);
    }

    public byte getProtocolMinorVersion()
    {
        return _protocolVersion.getMinorVersion();
    }

    public byte getProtocolMajorVersion()
    {
        return _protocolVersion.getMajorVersion();
    }

    public ProtocolVersion getProtocolVersion()
    {
        return _protocolVersion;
    }

//    public VersionSpecificRegistry getRegistry()
//    {
//        return _registry;
//    }

    public MethodRegistry getMethodRegistry()
    {
        return _methodRegistry;
    }

    public MethodDispatcher getMethodDispatcher()
    {
        return _methodDispatcher;
    }


    public void setTicket(int ticket, int channelId)
    {
        final AMQSession session = getSession(channelId);
        session.setTicket(ticket);
    }


    public void setMethodDispatcher(MethodDispatcher methodDispatcher)
    {
        _methodDispatcher = methodDispatcher;
    }
}
