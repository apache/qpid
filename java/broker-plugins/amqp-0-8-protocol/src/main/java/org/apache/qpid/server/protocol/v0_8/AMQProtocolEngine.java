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
package org.apache.qpid.server.protocol.v0_8;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.security.auth.Subject;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.*;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.SessionModelListener;
import org.apache.qpid.server.protocol.v0_8.handler.ServerMethodDispatcherImpl;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.logging.subjects.ConnectionLogSubject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.protocol.v0_8.output.ProtocolOutputConverter;
import org.apache.qpid.server.protocol.v0_8.output.ProtocolOutputConverterRegistry;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.protocol.v0_8.state.AMQState;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.util.BytesDataOutput;

public class AMQProtocolEngine implements ServerProtocolEngine, AMQProtocolSession<AMQProtocolEngine>
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolEngine.class);

    // to save boxing the channelId and looking up in a map... cache in an array the low numbered
    // channels.  This value must be of the form 2^x - 1.
    private static final int CHANNEL_CACHE_SIZE = 0xff;
    private static final int REUSABLE_BYTE_BUFFER_CAPACITY = 65 * 1024;
    private final Port _port;

    private AMQShortString _contextKey;

    private String _clientVersion = null;
    private String _clientProduct = null;
    private String _remoteProcessPid = null;

    private VirtualHostImpl _virtualHost;

    private final Map<Integer, AMQChannel<AMQProtocolEngine>> _channelMap =
            new HashMap<Integer, AMQChannel<AMQProtocolEngine>>();
    private final CopyOnWriteArrayList<SessionModelListener> _sessionListeners =
            new CopyOnWriteArrayList<SessionModelListener>();

    @SuppressWarnings("unchecked")
    private final AMQChannel<AMQProtocolEngine>[] _cachedChannels = new AMQChannel[CHANNEL_CACHE_SIZE + 1];

    /**
     * The channels that the latest call to {@link #received(ByteBuffer)} applied to.
     * Used so we know which channels we need to call {@link AMQChannel#receivedComplete()}
     * on after handling the frames.
     *
     * Thread-safety: guarded by {@link #_receivedLock}.
     */
    private final Set<AMQChannel<AMQProtocolEngine>> _channelsForCurrentMessage =
            new HashSet<AMQChannel<AMQProtocolEngine>>();

    private final AMQStateManager _stateManager;

    private AMQCodecFactory _codecFactory;

    private SaslServer _saslServer;

    private volatile boolean _closed;

    // maximum number of channels this session should have
    private long _maxNoOfChannels;

    /* AMQP Version for this session */
    private ProtocolVersion _protocolVersion = ProtocolVersion.getLatestSupportedVersion();
    private MethodRegistry _methodRegistry = MethodRegistry.getMethodRegistry(_protocolVersion);
    private final List<Action<? super AMQProtocolEngine>> _taskList =
            new CopyOnWriteArrayList<Action<? super AMQProtocolEngine>>();

    private Map<Integer, Long> _closingChannelsList = new ConcurrentHashMap<Integer, Long>();
    private ProtocolOutputConverter _protocolOutputConverter;
    private final Subject _authorizedSubject = new Subject();
    private MethodDispatcher _dispatcher;

    private final long _connectionID;
    private Object _reference = new Object();

    private LogSubject _logSubject;

    private long _lastIoTime;

    private long _writtenBytes;

    private long _maxFrameSize;
    private final AtomicBoolean _closing = new AtomicBoolean(false);

    private final StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private NetworkConnection _network;
    private Sender<ByteBuffer> _sender;

    private volatile boolean _deferFlush;
    private long _lastReceivedTime;
    private boolean _blocking;

    private final ReentrantLock _receivedLock;
    private AtomicLong _lastWriteTime = new AtomicLong(System.currentTimeMillis());
    private final Broker _broker;
    private final Transport _transport;

    private volatile boolean _closeWhenNoRoute;
    private volatile boolean _stopped;
    private long _readBytes;

    public AMQProtocolEngine(Broker broker,
                             final NetworkConnection network,
                             final long connectionId,
                             Port port,
                             Transport transport)
    {
        _broker = broker;
        _port = port;
        _transport = transport;
        _maxNoOfChannels = broker.getConnection_sessionCountLimit();
        _receivedLock = new ReentrantLock();
        _stateManager = new AMQStateManager(broker, this);
        _codecFactory = new AMQCodecFactory(true, this);
        _connectionID = connectionId;
        _logSubject = new ConnectionLogSubject(this);

        _authorizedSubject.getPrincipals().add(new ConnectionPrincipal(this));
        runAsSubject(new PrivilegedAction<Void>()
        {

            @Override
            public Void run()
            {
                setNetworkConnection(network);

                getEventLogger().message(ConnectionMessages.OPEN(null, null, null, null, false, false, false, false));

                _closeWhenNoRoute = _broker.getConnection_closeWhenNoRoute();

                return null;
            }
        });

        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getSessionID());
        _dataDelivered = new StatisticsCounter("data-delivered-" + getSessionID());
        _messagesReceived = new StatisticsCounter("messages-received-" + getSessionID());
        _dataReceived = new StatisticsCounter("data-received-" + getSessionID());
    }

    private <T> T runAsSubject(PrivilegedAction<T> action)
    {
        return Subject.doAs(getAuthorizedSubject(), action);
    }

    private boolean runningAsSubject()
    {
        return getAuthorizedSubject().equals(Subject.getSubject(AccessController.getContext()));
    }

    @Override
    public Subject getSubject()
    {
        return _authorizedSubject;
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

    public long getSessionID()
    {
        return _connectionID;
    }

    public void setMaxFrameSize(long frameMax)
    {
        _maxFrameSize = frameMax;
    }

    public long getMaxFrameSize()
    {
        return _maxFrameSize;
    }

    public boolean isClosing()
    {
        return _closing.get();
    }

    public synchronized void flushBatched()
    {
        _sender.flush();
    }


    public ClientDeliveryMethod createDeliveryMethod(int channelId)
    {
        return new WriteDeliverMethod(channelId);
    }

    public void received(final ByteBuffer msg)
    {
        Subject.doAs(_authorizedSubject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                final long arrivalTime = System.currentTimeMillis();
                _lastReceivedTime = arrivalTime;
                _lastIoTime = arrivalTime;
                _readBytes += msg.remaining();

                _receivedLock.lock();
                try
                {
                    final ArrayList<AMQDataBlock> dataBlocks = _codecFactory.getDecoder().decodeBuffer(msg);
                    for (AMQDataBlock dataBlock : dataBlocks)
                    {
                        try
                        {
                            dataBlockReceived(dataBlock);
                        }
                        catch(AMQConnectionException e)
                        {
                            if(_logger.isDebugEnabled())
                            {
                                _logger.debug("Caught AMQConnectionException but will simply stop processing data blocks - the connection should already be closed.", e);
                            }
                            break;
                        }
                        catch (Exception e)
                        {
                            _logger.error("Unexpected exception when processing datablock", e);
                            closeProtocolSession();
                            break;
                        }
                    }
                    receivedComplete();
                }
                catch (ConnectionScopedRuntimeException e)
                {
                    _logger.error("Unexpected exception", e);
                    closeProtocolSession();
                }
                catch (AMQProtocolVersionException e)
                {
                    _logger.error("Unexpected protocol version", e);
                    closeProtocolSession();
                }
                catch (AMQFrameDecodingException e)
                {
                    _logger.error("Frame decoding", e);
                    closeProtocolSession();
                }
                catch (IOException e)
                {
                    _logger.error("I/O Exception", e);
                    closeProtocolSession();
                }
                finally
                {
                    _receivedLock.unlock();
                }
                return null;
            }
        });

    }

    private void receivedComplete()
    {
        RuntimeException exception = null;

        for (AMQChannel<AMQProtocolEngine> channel : _channelsForCurrentMessage)
        {
            try
            {
                channel.receivedComplete();
            }
            catch(RuntimeException exceptionForThisChannel)
            {
                if(exception == null)
                {
                    exception = exceptionForThisChannel;
                }
                _logger.error("Error informing channel that receiving is complete. Channel: " + channel,
                              exceptionForThisChannel);
            }
        }

        _channelsForCurrentMessage.clear();

        if(exception != null)
        {
            throw exception;
        }
    }

    /**
     * Process the data block.
     * If the message is for a channel it is added to {@link #_channelsForCurrentMessage}.
     *
     * @throws AMQConnectionException if unable to process the data block. In this case,
     * the connection is already closed by the time the exception is thrown. If any other
     * type of exception is thrown, the connection is not already closed.
     */
    private void dataBlockReceived(AMQDataBlock message) throws Exception
    {
        if (message instanceof ProtocolInitiation)
        {
            protocolInitiationReceived((ProtocolInitiation) message);

        }
        else if (message instanceof AMQFrame)
        {
            AMQFrame frame = (AMQFrame) message;
            frameReceived(frame);

        }
        else
        {
            throw new AMQException("Unknown message type: " + message.getClass().getName() + ": " + message);
        }
    }

    /**
     * Handle the supplied frame.
     * Adds this frame's channel to {@link #_channelsForCurrentMessage}.
     *
     * @throws AMQConnectionException if unable to process the data block. In this case,
     * the connection is already closed by the time the exception is thrown. If any other
     * type of exception is thrown, the connection is not already closed.
     */
    private void frameReceived(AMQFrame frame) throws AMQException
    {
        int channelId = frame.getChannel();
        AMQChannel<AMQProtocolEngine> amqChannel = _channelMap.get(channelId);
        if(amqChannel != null)
        {
            // The _receivedLock is already acquired in the caller
            // It is safe to add channel
            _channelsForCurrentMessage.add(amqChannel);
        }
        else
        {
            // Not an error. The frame is probably a channel Open for this channel id, which
            // does not require asynchronous work therefore its absence from
            // _channelsForCurrentMessage is ok.
        }

        AMQBody body = frame.getBodyFrame();

        long startTime = 0;
        String frameToString = null;
        if (_logger.isDebugEnabled())
        {
            startTime = System.currentTimeMillis();
            frameToString = frame.toString();
            _logger.debug("RECV: " + frame);
        }

        // Check that this channel is not closing
        if (channelAwaitingClosure(channelId))
        {
            if ((frame.getBodyFrame() instanceof ChannelCloseOkBody))
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Channel[" + channelId + "] awaiting closure - processing close-ok");
                }
            }
            else
            {
                // The channel has been told to close, we don't process any more frames until
                // it's closed.
                return;
            }
        }

        try
        {
            body.handle(channelId, this);
        }
        catch(AMQConnectionException e)
        {
            _logger.info(e.getMessage() + " whilst processing frame: " + body);
            closeConnection(channelId, e);
            throw e;
        }
        catch (AMQException e)
        {
            closeChannel(channelId, e.getErrorCode() == null ? AMQConstant.INTERNAL_ERROR : e.getErrorCode(), e.getMessage());
            throw e;
        }
        catch (TransportException e)
        {
            closeChannel(channelId, AMQConstant.CHANNEL_ERROR, e.getMessage());
            throw e;
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("Frame handled in " + (System.currentTimeMillis() - startTime) + " ms. Frame: " + frameToString);
        }
    }

    private synchronized void protocolInitiationReceived(ProtocolInitiation pi)
    {
        // this ensures the codec never checks for a PI message again
        (_codecFactory.getDecoder()).setExpectProtocolInitiation(false);
        try
        {
            // Log incoming protocol negotiation request
            getEventLogger().message(ConnectionMessages.OPEN(null,
                                                             pi.getProtocolMajor() + "-" + pi.getProtocolMinor(),
                                                             null,
                                                             null,
                                                             false,
                                                             true,
                                                             false,
                                                             false));

            ProtocolVersion pv = pi.checkVersion(); // Fails if not correct

            // This sets the protocol version (and hence framing classes) for this session.
            setProtocolVersion(pv);

            StringBuilder mechanismBuilder = new StringBuilder();
            for(String mechanismName : _broker.getSubjectCreator(getLocalAddress(), _transport.isSecure()).getMechanisms())
            {
                if(mechanismBuilder.length() != 0)
                {
                    mechanismBuilder.append(' ');
                }
                mechanismBuilder.append(mechanismName);
            }
            String mechanisms = mechanismBuilder.toString();

            String locales = "en_US";


            FieldTable serverProperties = FieldTableFactory.newFieldTable();

            serverProperties.setString(ServerPropertyNames.PRODUCT,
                    QpidProperties.getProductName());
            serverProperties.setString(ServerPropertyNames.VERSION,
                    QpidProperties.getReleaseVersion());
            serverProperties.setString(ServerPropertyNames.QPID_BUILD,
                    QpidProperties.getBuildVersion());
            serverProperties.setString(ServerPropertyNames.QPID_INSTANCE_NAME,
                    _broker.getName());
            serverProperties.setString(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE,
                    String.valueOf(_closeWhenNoRoute));

            AMQMethodBody responseBody = getMethodRegistry().createConnectionStartBody((short) getProtocolMajorVersion(),
                                                                                       (short) pv.getActualMinorVersion(),
                                                                                       serverProperties,
                                                                                       mechanisms.getBytes(),
                                                                                       locales.getBytes());
            _sender.send(asByteBuffer(responseBody.generateFrame(0)));
            _sender.flush();

        }
        catch (AMQException e)
        {
            _logger.info("Received unsupported protocol initiation for protocol version: " + getProtocolVersion());

            _sender.send(asByteBuffer(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion())));
            _sender.flush();
        }
    }


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
            throw new ServerScopedRuntimeException(e);
        }

        final ByteBuffer buf;

        if(size <= REUSABLE_BYTE_BUFFER_CAPACITY)
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

    public void methodFrameReceived(int channelId, AMQMethodBody methodBody)
    {
        final AMQMethodEvent<AMQMethodBody> evt = new AMQMethodEvent<AMQMethodBody>(channelId, methodBody);

        try
        {
            try
            {
                boolean wasAnyoneInterested = _stateManager.methodReceived(evt);

                if (!wasAnyoneInterested)
                {
                    throw new AMQNoMethodHandlerException(evt);
                }
            }
            catch (AMQChannelException e)
            {
                if (getChannel(channelId) != null)
                {
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Closing channel due to: " + e.getMessage());
                    }

                    writeFrame(e.getCloseFrame(channelId));
                    closeChannel(channelId, e.getErrorCode() == null ? AMQConstant.INTERNAL_ERROR : e.getErrorCode(), e.getMessage());
                }
                else
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("ChannelException occurred on non-existent channel:" + e.getMessage());
                    }

                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Closing connection due to: " + e.getMessage());
                    }

                    AMQConnectionException ce =
                            evt.getMethod().getConnectionException(AMQConstant.CHANNEL_ERROR,
                                                                   AMQConstant.CHANNEL_ERROR.getName().toString());

                    _logger.info(e.getMessage() + " whilst processing:" + methodBody);
                    closeConnection(channelId, ce);
                }
            }
            catch (AMQConnectionException e)
            {
                _logger.info(e.getMessage() + " whilst processing:" + methodBody);
                closeConnection(channelId, e);
            }
        }
        catch (Exception e)
        {
            _logger.error("Unexpected exception while processing frame.  Closing connection.", e);

            closeProtocolSession();
        }
    }

    public void contentHeaderReceived(int channelId, ContentHeaderBody body) throws AMQException
    {

        AMQChannel<AMQProtocolEngine> channel = getAndAssertChannel(channelId);

        channel.publishContentHeader(body);

    }

    public void contentBodyReceived(int channelId, ContentBody body) throws AMQException
    {
        AMQChannel<AMQProtocolEngine> channel = getAndAssertChannel(channelId);

        channel.publishContentBody(body);
    }

    public void heartbeatBodyReceived(int channelId, HeartbeatBody body)
    {
        // NO - OP
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent to calling
     * getProtocolSession().write().
     *
     * @param frame the frame to write
     */
    public synchronized void writeFrame(AMQDataBlock frame)
    {

        final ByteBuffer buf = asByteBuffer(frame);
        _writtenBytes += buf.remaining();

        if(_logger.isDebugEnabled())
        {
            _logger.debug("SEND: " + frame);
        }

        _sender.send(buf);
        final long time = System.currentTimeMillis();
        _lastIoTime = time;
        _lastWriteTime.set(time);

        if(!_deferFlush)
        {
            _sender.flush();
        }
    }

    public AMQShortString getContextKey()
    {
        return _contextKey;
    }

    public void setContextKey(AMQShortString contextKey)
    {
        _contextKey = contextKey;
    }

    public List<AMQChannel<AMQProtocolEngine>> getChannels()
    {
        synchronized (_channelMap)
        {
            return new ArrayList<AMQChannel<AMQProtocolEngine>>(_channelMap.values());
        }
    }

    public AMQChannel<AMQProtocolEngine> getAndAssertChannel(int channelId) throws AMQException
    {
        AMQChannel<AMQProtocolEngine> channel = getChannel(channelId);
        if (channel == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Channel not found with id:" + channelId);
        }

        return channel;
    }

    public AMQChannel<AMQProtocolEngine> getChannel(int channelId)
    {
        final AMQChannel<AMQProtocolEngine> channel =
                ((channelId & CHANNEL_CACHE_SIZE) == channelId) ? _cachedChannels[channelId] : _channelMap.get(channelId);
        if ((channel == null) || channel.isClosing())
        {
            return null;
        }
        else
        {
            return channel;
        }
    }

    public boolean channelAwaitingClosure(int channelId)
    {
        return !_closingChannelsList.isEmpty() && _closingChannelsList.containsKey(channelId);
    }

    public void addChannel(AMQChannel<AMQProtocolEngine> channel) throws AMQException
    {
        if (_closed)
        {
            throw new AMQException("Session is closed");
        }

        final int channelId = channel.getChannelId();

        if (_closingChannelsList.containsKey(channelId))
        {
            throw new AMQException("Session is marked awaiting channel close");
        }

        if (_channelMap.size() == _maxNoOfChannels)
        {
            String errorMessage =
                    toString() + ": maximum number of channels has been reached (" + _maxNoOfChannels
                    + "); can't create channel";
            _logger.error(errorMessage);
            throw new AMQException(AMQConstant.NOT_ALLOWED, errorMessage);
        }
        else
        {
            synchronized (_channelMap)
            {
                _channelMap.put(channel.getChannelId(), channel);
                sessionAdded(channel);
                if(_blocking)
                {
                    channel.block();
                }
            }
        }

        if (((channelId & CHANNEL_CACHE_SIZE) == channelId))
        {
            _cachedChannels[channelId] = channel;
        }
    }

    private void sessionAdded(final AMQSessionModel<?,?> session)
    {
        for(SessionModelListener l : _sessionListeners)
        {
            l.sessionAdded(session);
        }
    }

    private void sessionRemoved(final AMQSessionModel<?,?> session)
    {
        for(SessionModelListener l : _sessionListeners)
        {
            l.sessionRemoved(session);
        }
    }

    public Long getMaximumNumberOfChannels()
    {
        return _maxNoOfChannels;
    }

    public void setMaximumNumberOfChannels(Long value)
    {
        _maxNoOfChannels = value;
    }

    /**
     * Close a specific channel. This will remove any resources used by the channel, including: <ul><li>any queue
     * subscriptions (this may in turn remove queues if they are auto delete</li> </ul>
     *
     * @param channelId id of the channel to close
     *
     * @throws IllegalArgumentException if the channel id is not valid
     */
    @Override
    public void closeChannel(int channelId)
    {
        closeChannel(channelId, null, null);
    }

    public void closeChannel(int channelId, AMQConstant cause, String message)
    {
        final AMQChannel<AMQProtocolEngine> channel = getChannel(channelId);
        if (channel == null)
        {
            throw new IllegalArgumentException("Unknown channel id");
        }
        else
        {
            try
            {
                channel.close(cause, message);
                markChannelAwaitingCloseOk(channelId);
            }
            finally
            {
                removeChannel(channelId);
            }
        }
    }

    public void closeChannelOk(int channelId)
    {
        // todo QPID-847 - This is called from two locations ChannelCloseHandler and ChannelCloseOkHandler.
        // When it is the CC_OK_Handler then it makes sense to remove the channel else we will leak memory.
        // We do it from the Close Handler as we are sending the OK back to the client.
        // While this is AMQP spec compliant. The Java client in the event of an IllegalArgumentException
        // will send a close-ok.. Where we should call removeChannel.
        // However, due to the poor exception handling on the client. The client-user will be notified of the
        // InvalidArgument and if they then decide to close the session/connection then the there will be time
        // for that to occur i.e. a new close method be sent before the exception handling can mark the session closed.

        _closingChannelsList.remove(channelId);
    }

    private void markChannelAwaitingCloseOk(int channelId)
    {
        _closingChannelsList.put(channelId, System.currentTimeMillis());
    }

    /**
     * In our current implementation this is used by the clustering code.
     *
     * @param channelId The channel to remove
     */
    public void removeChannel(int channelId)
    {
        AMQChannel<AMQProtocolEngine> session;
        synchronized (_channelMap)
        {
            session = _channelMap.remove(channelId);
            if ((channelId & CHANNEL_CACHE_SIZE) == channelId)
            {
                _cachedChannels[channelId] = null;
            }
        }
        sessionRemoved(session);
    }

    /**
     * Initialise heartbeats on the session.
     *
     * @param delay delay in seconds (not ms)
     */
    public void initHeartbeats(int delay)
    {
        if (delay > 0)
        {
            _network.setMaxWriteIdle(delay);
            _network.setMaxReadIdle(BrokerProperties.HEARTBEAT_TIMEOUT_FACTOR * delay);
        }
        else
        {
            _network.setMaxWriteIdle(0);
            _network.setMaxReadIdle(0);
        }
    }

    /**
     * Closes all channels that were opened by this protocol session. This frees up all resources used by the channel.
     */
    private void closeAllChannels()
    {
        for (AMQChannel<AMQProtocolEngine> channel : getChannels())
        {
            channel.close();
        }
        synchronized (_channelMap)
        {
            _channelMap.clear();
        }
        for (int i = 0; i <= CHANNEL_CACHE_SIZE; i++)
        {
            _cachedChannels[i] = null;
        }
    }

    /** This must be called when the session is _closed in order to free up any resources managed by the session. */
    @Override
    public void closeSession()
    {

        if(runningAsSubject())
        {
            if(_closing.compareAndSet(false,true))
            {
                // force sync of outstanding async work
                _receivedLock.lock();
                try
                {
                    receivedComplete();
                }
                finally
                {
                    _receivedLock.unlock();
                }

                if (!_closed)
                {
                    if (_virtualHost != null)
                    {
                        _virtualHost.getConnectionRegistry().deregisterConnection(this);
                    }

                    closeAllChannels();

                    for (Action<? super AMQProtocolEngine> task : _taskList)
                    {
                        task.performAction(this);
                    }

                    synchronized(this)
                    {
                        _closed = true;
                        notifyAll();
                    }
                    getEventLogger().message(_logSubject, ConnectionMessages.CLOSE());
                }
            }
            else
            {
                synchronized(this)
                {

                    boolean lockHeld = _receivedLock.isHeldByCurrentThread();

                    while(!_closed)
                    {
                        try
                        {
                            if(lockHeld)
                            {
                                _receivedLock.unlock();
                            }
                            wait(1000);
                        }
                        catch (InterruptedException e)
                        {
                            // do nothing
                        }
                        finally
                        {
                            if(lockHeld)
                            {
                                _receivedLock.lock();
                            }
                        }
                    }
                }
            }
        }
        else
        {
            runAsSubject(new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    closeSession();
                    return null;
                }
            });

        }
    }

    private void closeConnection(int channelId, AMQConnectionException e)
    {
        try
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Closing connection due to: " + e);
            }

            markChannelAwaitingCloseOk(channelId);
            closeSession();
        }
        finally
        {
            try
            {
                _stateManager.changeState(AMQState.CONNECTION_CLOSING);
                writeFrame(e.getCloseFrame(channelId));
            }
            finally
            {
                closeProtocolSession();
            }
        }


    }

    @Override
    public void closeProtocolSession()
    {
        _network.close();

        try
        {
            _stateManager.changeState(AMQState.CONNECTION_CLOSED);
        }
        catch (ConnectionScopedRuntimeException e)
        {
            _logger.info(e.getMessage());
        }
        catch (TransportException e)
        {
            _logger.info(e.getMessage());
        }
    }

    public String toString()
    {
        return getRemoteAddress() + "(" + (getAuthorizedPrincipal() == null ? "?" : getAuthorizedPrincipal().getName() + ")");
    }

    /** @return an object that can be used to identity */
    public Object getKey()
    {
        return getRemoteAddress();
    }

    /**
     * Get the fully qualified domain name of the local address to which this session is bound. Since some servers may
     * be bound to multiple addresses this could vary depending on the acceptor this session was created from.
     *
     * @return a String FQDN
     */
    public String getLocalFQDN()
    {
        SocketAddress address = _network.getLocalAddress();
        if (address instanceof InetSocketAddress)
        {
            return ((InetSocketAddress) address).getHostName();
        }
        else
        {
            throw new IllegalArgumentException("Unsupported socket address class: " + address);
        }
    }

    public SaslServer getSaslServer()
    {
        return _saslServer;
    }

    public void setSaslServer(SaslServer saslServer)
    {
        _saslServer = saslServer;
    }

    public void setClientProperties(FieldTable clientProperties)
    {
        if (clientProperties != null)
        {
            String closeWhenNoRoute = clientProperties.getString(ConnectionStartProperties.QPID_CLOSE_WHEN_NO_ROUTE);
            if (closeWhenNoRoute != null)
            {
                _closeWhenNoRoute = Boolean.parseBoolean(closeWhenNoRoute);
                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Client set closeWhenNoRoute=" + _closeWhenNoRoute + " for protocol engine " + this);
                }
            }

            _clientVersion = clientProperties.getString(ConnectionStartProperties.VERSION_0_8);
            _clientProduct = clientProperties.getString(ConnectionStartProperties.PRODUCT);
            _remoteProcessPid = clientProperties.getString(ConnectionStartProperties.PID);

            String clientId = clientProperties.getString(ConnectionStartProperties.CLIENT_ID_0_8);
            if (clientId != null)
            {
                setContextKey(new AMQShortString(clientId));
            }

            getEventLogger().message(ConnectionMessages.OPEN(clientId,
                                                             _protocolVersion.toString(),
                                                             _clientVersion,
                                                             _clientProduct,
                                                             true,
                                                             true,
                                                             true,
                                                             true));
        }
    }

    private void setProtocolVersion(ProtocolVersion pv)
    {
        _protocolVersion = pv;
        _methodRegistry = MethodRegistry.getMethodRegistry(_protocolVersion);
        _protocolOutputConverter = ProtocolOutputConverterRegistry.getConverter(this);
        _dispatcher = ServerMethodDispatcherImpl.createMethodDispatcher(_stateManager, _protocolVersion);
    }

    public byte getProtocolMajorVersion()
    {
        return _protocolVersion.getMajorVersion();
    }

    public ProtocolVersion getProtocolVersion()
    {
        return _protocolVersion;
    }

    public byte getProtocolMinorVersion()
    {
        return _protocolVersion.getMinorVersion();
    }

    public MethodRegistry getRegistry()
    {
        return getMethodRegistry();
    }

    public VirtualHostImpl getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHostImpl virtualHost) throws AMQException
    {
        _virtualHost = virtualHost;

        _virtualHost.getConnectionRegistry().registerConnection(this);

    }

    public void addDeleteTask(Action<? super AMQProtocolEngine> task)
    {
        _taskList.add(task);
    }

    public void removeDeleteTask(Action<? super AMQProtocolEngine> task)
    {
        _taskList.remove(task);
    }

    public ProtocolOutputConverter getProtocolOutputConverter()
    {
        return _protocolOutputConverter;
    }

    public void setAuthorizedSubject(final Subject authorizedSubject)
    {
        if (authorizedSubject == null)
        {
            throw new IllegalArgumentException("authorizedSubject cannot be null");
        }

        _authorizedSubject.getPrincipals().addAll(authorizedSubject.getPrincipals());
        _authorizedSubject.getPrivateCredentials().addAll(authorizedSubject.getPrivateCredentials());
        _authorizedSubject.getPublicCredentials().addAll(authorizedSubject.getPublicCredentials());

    }

    public Subject getAuthorizedSubject()
    {
        return _authorizedSubject;
    }

    public Principal getAuthorizedPrincipal()
    {

        return _authorizedSubject.getPrincipals(AuthenticatedPrincipal.class).size() == 0 ? null : AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(_authorizedSubject);
    }

    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
    }

    @Override
    public String getRemoteProcessPid()
    {
        return _remoteProcessPid;
    }

    public SocketAddress getLocalAddress()
    {
        return _network.getLocalAddress();
    }

    public Principal getPeerPrincipal()
    {
        return _network.getPeerPrincipal();
    }

    public MethodRegistry getMethodRegistry()
    {
        return _methodRegistry;
    }

    public MethodDispatcher getMethodDispatcher()
    {
        return _dispatcher;
    }

    public void closed()
    {
        try
        {
            try
            {
                closeSession();
            }
            finally
            {
                closeProtocolSession();
            }
        }
        catch (ConnectionScopedRuntimeException e)
        {
            _logger.error("Could not close protocol engine", e);
        }
        catch (TransportException e)
        {
           _logger.error("Could not close protocol engine", e);
        }
    }

    public void readerIdle()
    {
        // TODO - enforce disconnect on lack of inbound data
    }

    public synchronized void writerIdle()
    {
        writeFrame(HeartbeatBody.FRAME);
    }

    public void exception(Throwable throwable)
    {
        if (throwable instanceof AMQProtocolHeaderException)
        {
            writeFrame(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion()));
            _sender.close();

            _logger.error("Error in protocol initiation " + this + ":" + getRemoteAddress() + " :" + throwable.getMessage(), throwable);
        }
        else if (throwable instanceof IOException)
        {
            _logger.info("IOException caught in " + this + ", connection closed implicitly: " + throwable);
        }
        else
        {
            try
            {
                _logger.error("Exception caught in " + this + ", closing connection explicitly: " + throwable, throwable);


                MethodRegistry methodRegistry = MethodRegistry.getMethodRegistry(getProtocolVersion());
                ConnectionCloseBody closeBody = methodRegistry.createConnectionCloseBody(200,new AMQShortString(throwable.getMessage()),0,0);

                writeFrame(closeBody.generateFrame(0));

                _sender.close();
            }
            finally
            {
                if(throwable instanceof Error)
                {
                    throw (Error) throwable;
                }
                if(throwable instanceof ServerScopedRuntimeException)
                {
                    throw (ServerScopedRuntimeException) throwable;
                }

            }
        }
    }

    public void setSender(Sender<ByteBuffer> sender)
    {
        // Do nothing
    }

    public long getReadBytes()
    {
        return _readBytes;
    }

    public long getWrittenBytes()
    {
        return _writtenBytes;
    }

    public long getLastIoTime()
    {
        return _lastIoTime;
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
    public String getVirtualHostName()
    {
        return _virtualHost == null ? null : _virtualHost.getName();
    }

    public long getLastReceivedTime()
    {
        return _lastReceivedTime;
    }

    public String getClientVersion()
    {
        return _clientVersion;
    }

    @Override
    public String getClientProduct()
    {
        return _clientProduct;
    }

    public long getSessionCountLimit()
    {
        return getMaximumNumberOfChannels();
    }

    public boolean isDurable()
    {
        return false;
    }

    public long getConnectionId()
    {
        return getSessionID();
    }

    public String getAddress()
    {
        return String.valueOf(getRemoteAddress());
    }

    public void closeSession(AMQChannel<AMQProtocolEngine> session, AMQConstant cause, String message)
    {
        int channelId = session.getChannelId();
        closeChannel(channelId, cause, message);

        MethodRegistry methodRegistry = getMethodRegistry();
        ChannelCloseBody responseBody =
                methodRegistry.createChannelCloseBody(
                        cause.getCode(),
                        new AMQShortString(message),
                        0,0);

        writeFrame(responseBody.generateFrame(channelId));
    }

    public void close(AMQConstant cause, String message)
    {
        closeConnection(0, new AMQConnectionException(cause, message, 0, 0,
		                getProtocolOutputConverter().getProtocolMajorVersion(),
		                getProtocolOutputConverter().getProtocolMinorVersion(),
		                null));
    }

    public void block()
    {
        synchronized (_channelMap)
        {
            if(!_blocking)
            {
                _blocking = true;
                for(AMQChannel<AMQProtocolEngine> channel : _channelMap.values())
                {
                    channel.block();
                }
            }
        }
    }

    public void unblock()
    {
        synchronized (_channelMap)
        {
            if(_blocking)
            {
                _blocking = false;
                for(AMQChannel<AMQProtocolEngine> channel : _channelMap.values())
                {
                    channel.unblock();
                }
            }
        }
    }

    public boolean isClosed()
    {
        return _closed;
    }

    public List<AMQChannel<AMQProtocolEngine>> getSessionModels()
    {
		return new ArrayList<AMQChannel<AMQProtocolEngine>>(getChannels());
    }

    public LogSubject getLogSubject()
    {
        return _logSubject;
    }

    public void registerMessageDelivered(long messageSize)
    {
        _messagesDelivered.registerEvent(1L);
        _dataDelivered.registerEvent(messageSize);
        _virtualHost.registerMessageDelivered(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messagesReceived.registerEvent(1L, timestamp);
        _dataReceived.registerEvent(messageSize, timestamp);
        _virtualHost.registerMessageReceived(messageSize, timestamp);
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }

    public void resetStatistics()
    {
        _messagesDelivered.reset();
        _dataDelivered.reset();
        _messagesReceived.reset();
        _dataReceived.reset();
    }

    public boolean isSessionNameUnique(byte[] name)
    {
        // 0-8/0-9/0-9-1 sessions don't have names
        return true;
    }

    public String getRemoteAddressString()
    {
        return String.valueOf(getRemoteAddress());
    }

    public String getClientId()
    {
        return String.valueOf(getContextKey());
    }

    @Override
    public String getRemoteContainerName()
    {
        return String.valueOf(getContextKey());
    }

    @Override
    public void addSessionListener(final SessionModelListener listener)
    {
        _sessionListeners.add(listener);
    }

    @Override
    public void removeSessionListener(final SessionModelListener listener)
    {
        _sessionListeners.remove(listener);
    }

    public void setDeferFlush(boolean deferFlush)
    {
        _deferFlush = deferFlush;
    }

    public final class WriteDeliverMethod
            implements ClientDeliveryMethod
    {
        private final int _channelId;

        public WriteDeliverMethod(int channelId)
        {
            _channelId = channelId;
        }

        @Override
        public void deliverToClient(final ConsumerImpl sub, final ServerMessage message,
                                    final InstanceProperties props, final long deliveryTag)
        {
            registerMessageDelivered(message.getSize());
            _protocolOutputConverter.writeDeliver(message,
                                                  props,
                                                  _channelId,
                                                  deliveryTag,
                                                  new AMQShortString(sub.getName()));
        }

    }

    public Object getReference()
    {
        return _reference;
    }

    public Lock getReceivedLock()
    {
        return _receivedLock;
    }

    @Override
    public long getLastReadTime()
    {
        return _lastReceivedTime;
    }

    @Override
    public long getLastWriteTime()
    {
        return _lastWriteTime.get();
    }

    @Override
    public boolean isCloseWhenNoRoute()
    {
        return _closeWhenNoRoute;
    }

    public EventLogger getEventLogger()
    {
        if(_virtualHost != null)
        {
            return _virtualHost.getEventLogger();
        }
        else
        {
            return _broker.getEventLogger();
        }
    }
}
