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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.jms.JMSException;
import javax.security.sasl.SaslClient;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.mina.common.CloseFuture;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteFuture;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.ConnectionTuneParameters;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQRequestBody;
import org.apache.qpid.framing.AMQResponseBody;
import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.framing.ProtocolVersionList;
import org.apache.qpid.framing.RequestManager;
import org.apache.qpid.framing.ResponseManager;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.protocol.AMQProtocolWriter;

/**
 * Wrapper for protocol session that provides type-safe access to session attributes.
 *
 * The underlying protocol session is still available but clients should not
 * use it to obtain session attributes.
 */
public class AMQProtocolSession implements AMQProtocolWriter, ProtocolVersionList
{

    protected static final int LAST_WRITE_FUTURE_JOIN_TIMEOUT = 1000 * 60 * 2;

    protected static final Logger _logger = Logger.getLogger(AMQProtocolSession.class);

    public static final String PROTOCOL_INITIATION_RECEIVED = "ProtocolInitiatiionReceived";

    protected static final String CONNECTION_TUNE_PARAMETERS = "ConnectionTuneParameters";

    protected static final String AMQ_CONNECTION = "AMQConnection";

    protected static final String SASL_CLIENT = "SASLClient";

    protected final IoSession _minaProtocolSession;
 
    private AMQStateManager _stateManager;

    protected WriteFuture _lastWriteFuture;

    /**
     * The handler from which this session was created and which is used to handle protocol events.
     * We send failover events to the handler.
     */
    protected final AMQProtocolHandler _protocolHandler;

    /**
     * Maps from the channel id to the AMQSession that it represents.
     */
    protected ConcurrentMap _channelId2SessionMap = new ConcurrentHashMap();

    protected ConcurrentMap _closingChannels = new ConcurrentHashMap();

    /**
     * Maps from a channel id to an unprocessed message. This is used to tie together the
     * JmsDeliverBody (which arrives first) with the subsequent content header and content bodies.
     */
    protected ConcurrentMap _referenceId2UnprocessedMsgMap = new ConcurrentHashMap();

    protected ConcurrentMap _channelId2RequestMgrMap = new ConcurrentHashMap();
    protected ConcurrentMap _channelId2ResponseMgrMap = new ConcurrentHashMap();

    /**
     * Counter to ensure unique queue names
     */
    protected int _queueId = 1;
    protected final Object _queueIdLock = new Object();

    /**
     * No-arg constructor for use by test subclass - has to initialise final vars
     * NOT intended for use other then for test
     */
    public AMQProtocolSession()
    {
        _protocolHandler = null;
        _minaProtocolSession = null;
        _stateManager = new AMQStateManager(this);

        // Add channel 0 request and response managers, since they will not be added through the usual mechanism
        _channelId2RequestMgrMap.put(0, new RequestManager(0, this, false));
        _channelId2ResponseMgrMap.put(0, new ResponseManager(0, _stateManager, this, false));
    }

    public AMQProtocolSession(AMQProtocolHandler protocolHandler, IoSession protocolSession, AMQConnection connection)
    {
        _protocolHandler = protocolHandler;
        _minaProtocolSession = protocolSession;
        // properties of the connection are made available to the event handlers
        _minaProtocolSession.setAttribute(AMQ_CONNECTION, connection);
        _stateManager = new AMQStateManager(this);

        // Add channel 0 request and response managers, since they will not be added through the usual mechanism
        _channelId2RequestMgrMap.put(0, new RequestManager(0, this, false));
        _channelId2ResponseMgrMap.put(0, new ResponseManager(0, _stateManager, this, false));
    }
 
    public AMQProtocolSession(AMQProtocolHandler protocolHandler, IoSession protocolSession, AMQConnection connection, AMQStateManager stateManager)
    {
        _protocolHandler = protocolHandler;
        _minaProtocolSession = protocolSession;
        // properties of the connection are made available to the event handlers
        _minaProtocolSession.setAttribute(AMQ_CONNECTION, connection);

        _stateManager = stateManager;
        _stateManager.setProtocolSession(this);
                
        // Add channel 0 request and response managers, since they will not be added through the usual mechanism
        _channelId2RequestMgrMap.put(0, new RequestManager(0, this, false));
        _channelId2ResponseMgrMap.put(0, new ResponseManager(0, _stateManager, this, false));
    }

    public void init()
    {
        // start the process of setting up the connection. This is the first place that
        // data is written to the server.
        /* Find last protocol version in protocol version list. Make sure last protocol version
        listed in the build file (build-module.xml) is the latest version which will be used
        here. */
        int i = pv.length - 1;
        _minaProtocolSession.write(new ProtocolInitiation(pv[i][PROTOCOL_MAJOR], pv[i][PROTOCOL_MINOR]));
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
     * @param client if non-null, stores this in the session. if null clears any existing client
     * being stored
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
     * This is involed from MessageTransferMethodHandler if type is CONTENT_TYPE_REFERENCE
     * This is invoked on the MINA dispatcher thread.
     * @param message
     * @throws AMQException if this was not expected
     */
    public void unprocessedMessageReceived(String referenceId, UnprocessedMessage message) throws AMQException
    {
        _referenceId2UnprocessedMsgMap.put(referenceId, message);
    }
    
    public void messageAppendBodyReceived(MessageAppendBody appendBody) throws Exception
    {
    	String referenceId = new String(appendBody.getReference());
    	UnprocessedMessage msg = (UnprocessedMessage)_referenceId2UnprocessedMsgMap.get(referenceId);
    	msg.addContent(appendBody.bytes);
    }
    
    public void messageRequestBodyReceived(int channelId, AMQRequestBody requestBody) throws Exception
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Request frame received: " + requestBody);
        }
        ResponseManager responseManager = (ResponseManager)_channelId2ResponseMgrMap.get(channelId);
        if (responseManager == null)
            throw new AMQException("Unable to find ResponseManager for channel " + channelId);
        responseManager.requestReceived(requestBody);
    }
    
    public void messageResponseBodyReceived(int channelId, AMQResponseBody responseBody) throws Exception
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Response frame received: " + responseBody);
        }
        RequestManager requestManager = (RequestManager)_channelId2RequestMgrMap.get(channelId);
        if (requestManager == null)
            throw new AMQException("Unable to find RequestManager for channel " + channelId);
        requestManager.responseReceived(responseBody);
    }

    /**
     * This is involed from MessageTransferMethodHandler if type is CONTENT_TYPE_INLINE
     * Deliver a message to the appropriate session, removing the unprocessed message
     * from our map
     * @param channelId the channel id the message should be delivered to
     * @param msg the message
     */
    public void deliverMessageToAMQSession(int channelId, UnprocessedMessage msg)
    {
        AMQSession session = (AMQSession) _channelId2SessionMap.get(channelId);
        msg.contentHeader.setSize(msg.bytesReceived);
        session.messageReceived(msg);
    }
    
    /**
     * This is involed from MessageCloseMethodHandler if type is CONTENT_TYPE_REFERENCE
     * In this case we use the reference id to obtain the unprocessed message
     * The channel id is used to retrive a session
     */
    public void deliverMessageToAMQSession(int channelId, String referenceId)
    {
    	UnprocessedMessage msg = (UnprocessedMessage)_referenceId2UnprocessedMsgMap.get(referenceId);
    	deliverMessageToAMQSession(channelId,msg);
    	_referenceId2UnprocessedMsgMap.remove(referenceId);
    }
    
    public long writeRequest(int channelNum, AMQMethodBody methodBody,
                             AMQMethodListener methodListener)
        throws AMQException
    {
        RequestManager requestManager = (RequestManager)_channelId2RequestMgrMap.get(channelNum);
        if (requestManager == null)
            throw new AMQException("Unable to find RequestManager for channel " + channelNum);
        return requestManager.sendRequest(methodBody, methodListener);
    }

    public void writeResponse(int channelNum, long requestId, AMQMethodBody methodBody)
        throws AMQException
    {
        ResponseManager responseManager = (ResponseManager)_channelId2ResponseMgrMap.get(channelNum);
        if (responseManager == null)
            throw new AMQException("Unable to find ResponseManager for channel " + channelNum);
        responseManager.sendResponse(requestId, methodBody);
    }

    public void writeResponse(AMQMethodEvent evt, AMQMethodBody response)
        throws AMQException
    {
        writeResponse(evt.getChannelId(), evt.getRequestId(), response);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent
     * to calling getProtocolSession().write().
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
            f.join();
        }
        else
        {
            _lastWriteFuture = f;
        }
    }

    public void addSessionByChannel(int channelId, AMQSession session)
    {
        if (channelId <= 0)
        {
            throw new IllegalArgumentException("Attempt to register a session with a channel id <= zero");
        }
        if (session == null)
        {
            throw new IllegalArgumentException("Attempt to register a null session");
        }
        _logger.debug("Add session with channel id  " + channelId);
        _channelId2SessionMap.put(channelId, session);
        
        // Add request and response handlers, one per channel, if they do not already exist
        if (_channelId2RequestMgrMap.get(channelId) == null)
        {
            _channelId2RequestMgrMap.put(channelId, new RequestManager(channelId, this, false));
        }
        if (_channelId2ResponseMgrMap.get(channelId) == null)
        {
            
            _channelId2ResponseMgrMap.put(channelId, new ResponseManager(channelId, _stateManager, this, false));
        }
    }

    public void removeSessionByChannel(int channelId)
    {
        if (channelId <= 0)
        {
            throw new IllegalArgumentException("Attempt to deregister a session with a channel id <= zero");
        }
        _logger.debug("Removing session with channelId " + channelId);
        _channelId2SessionMap.remove(channelId);
    }

    /**
     * Starts the process of closing a session
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
     * Called from the ChannelClose handler when a channel close frame is received.
     * This method decides whether this is a response or an initiation. The latter
     * case causes the AMQSession to be closed and an exception to be thrown if
     * appropriate.
     * @param channelId the id of the channel (session)
     * @return true if the client must respond to the server, i.e. if the server
     * initiated the channel close, false if the channel close is just the server
     * responding to the client's earlier request to close the channel.
     */
    public boolean channelClosed(int channelId, int code, String text)
    {
        final Integer chId = channelId;
        // if this is not a response to an earlier request to close the channel
        if (_closingChannels.remove(chId) == null)
        {
            final AMQSession session = (AMQSession) _channelId2SessionMap.get(chId);
            session.closed(new AMQException(_logger, code, text));
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
        _logger.debug("Waiting for last write to join.");
        if (_lastWriteFuture != null)
        {
            _lastWriteFuture.join(LAST_WRITE_FUTURE_JOIN_TIMEOUT);
        }

        _logger.debug("Closing protocol session");
        final CloseFuture future = _minaProtocolSession.close();
        future.join();
    }

    public void failover(String host, int port)
    {
        _protocolHandler.failover(host, port);
    }

    protected String generateQueueName()
    {
        int id;
        synchronized(_queueIdLock)
        {
            id = _queueId++;
        }
        //get rid of / and : and ; from address for spec conformance
        String localAddress = StringUtils.replaceChars(_minaProtocolSession.getLocalAddress().toString(),"/;:","");
        return "tmp_" + localAddress + "_" + id;
    }

    /**
     *
     * @param delay delay in seconds (not ms)
     */
    void initHeartbeats(int delay)
    {
        if (delay > 0)
        {
            _minaProtocolSession.setIdleTime(IdleStatus.WRITER_IDLE, delay);
            _minaProtocolSession.setIdleTime(IdleStatus.READER_IDLE, HeartbeatConfig.CONFIG.getTimeout(delay));
            HeartbeatDiagnostics.init(delay, HeartbeatConfig.CONFIG.getTimeout(delay));
        }
    }

    public void confirmConsumerCancelled(int channelId, String consumerTag)
    {
        final Integer chId = channelId;
        final AMQSession session = (AMQSession) _channelId2SessionMap.get(chId);

        session.confirmConsumerCancelled(consumerTag);
    }
}
