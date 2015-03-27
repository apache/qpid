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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQDecoder;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.framing.*;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.connection.ConnectionPrincipal;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.logging.subjects.ConnectionLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.SessionModelListener;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.SenderClosedException;
import org.apache.qpid.transport.SenderException;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;

public class AMQProtocolEngine implements ServerProtocolEngine,
                                          AMQConnectionModel<AMQProtocolEngine, AMQChannel>,
                                          ServerMethodProcessor<ServerChannelMethodProcessor>
{



    enum ConnectionState
    {
        INIT,
        AWAIT_START_OK,
        AWAIT_SECURE_OK,
        AWAIT_TUNE_OK,
        AWAIT_OPEN,
        OPEN
    }

    private static final Logger _logger = LoggerFactory.getLogger(AMQProtocolEngine.class);

    // to save boxing the channelId and looking up in a map... cache in an array the low numbered
    // channels.  This value must be of the form 2^x - 1.
    private static final int CHANNEL_CACHE_SIZE = 0xff;
    private static final String BROKER_DEBUG_BINARY_DATA_LENGTH = "broker.debug.binaryDataLength";
    private static final int DEFAULT_DEBUG_BINARY_DATA_LENGTH = 80;
    private static final long AWAIT_CLOSED_TIMEOUT = 60000;
    private final AmqpPort<?> _port;
    private final long _creationTime;
    private final AtomicBoolean _stateChanged = new AtomicBoolean();
    private final AtomicReference<Action<ServerProtocolEngine>> _workListener = new AtomicReference<>();

    private AMQShortString _contextKey;

    private String _clientVersion = null;
    private String _clientProduct = null;
    private String _remoteProcessPid = null;

    private volatile VirtualHostImpl<?,?,?> _virtualHost;

    private final Map<Integer, AMQChannel> _channelMap =
            new HashMap<>();
    private final CopyOnWriteArrayList<SessionModelListener> _sessionListeners =
            new CopyOnWriteArrayList<>();

    private final AMQChannel[] _cachedChannels = new AMQChannel[CHANNEL_CACHE_SIZE + 1];

    private ConnectionState _state = ConnectionState.INIT;

    /**
     * The channels that the latest call to {@link #received(ByteBuffer)} applied to.
     * Used so we know which channels we need to call {@link AMQChannel#receivedComplete()}
     * on after handling the frames.
     */
    private final Set<AMQChannel> _channelsForCurrentMessage = new HashSet<>();

    private AMQDecoder _decoder;

    private SaslServer _saslServer;

    private volatile boolean _closed;

    private long _maxNoOfChannels;

    private ProtocolVersion _protocolVersion = ProtocolVersion.getLatestSupportedVersion();
    private final MethodRegistry _methodRegistry = new MethodRegistry(_protocolVersion);
    private final List<Action<? super AMQProtocolEngine>> _connectionCloseTaskList =
            new CopyOnWriteArrayList<>();

    private final Queue<Action<? super AMQProtocolEngine>> _asyncTaskList =
            new ConcurrentLinkedQueue<>();

    private Map<Integer, Long> _closingChannelsList = new ConcurrentHashMap<>();
    private ProtocolOutputConverter _protocolOutputConverter;
    private final Subject _authorizedSubject = new Subject();

    private final long _connectionId;
    private Object _reference = new Object();

    private LogSubject _logSubject;

    private volatile long _lastIoTime;

    private long _writtenBytes;

    private int _maxFrameSize;
    private final AtomicBoolean _closing = new AtomicBoolean(false);

    private final StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private NetworkConnection _network;
    private ByteBufferSender _sender;

    private volatile boolean _deferFlush;
    private volatile long _lastReceivedTime = System.currentTimeMillis();
    private volatile long _lastWriteTime = System.currentTimeMillis();
    private boolean _blocking;

    private final Broker<?> _broker;
    private final Transport _transport;

    private volatile boolean _closeWhenNoRoute;
    private volatile boolean _stopped;
    private long _readBytes;
    private boolean _authenticated;
    private boolean _compressionSupported;
    private int _messageCompressionThreshold;
    private int _currentClassId;
    private int _currentMethodId;
    private int _binaryDataLimit;
    private long _maxMessageSize;
    private volatile boolean _transportBlockedForWriting;

    private final AtomicReference<Thread> _messageAssignmentSuspended = new AtomicReference<>();


    @Override
    public boolean isMessageAssignmentSuspended()
    {
        Thread lock = _messageAssignmentSuspended.get();
        return lock != null && _messageAssignmentSuspended.get() != Thread.currentThread();
    }

    @Override
    public void setMessageAssignmentSuspended(final boolean messageAssignmentSuspended)
    {
        _messageAssignmentSuspended.set(messageAssignmentSuspended ? Thread.currentThread() : null);
        for(AMQSessionModel<?,?> session : getSessionModels())
        {
            for (Consumer<?> consumer : session.getConsumers())
            {
                ConsumerImpl consumerImpl = (ConsumerImpl) consumer;
                if (!messageAssignmentSuspended)
                {
                    consumerImpl.getTarget().notifyCurrentState();
                }
                else
                {
                    // ensure that by the time the method returns, no consumer can be in the process of
                    // delivering a message.
                    consumerImpl.getSendLock();
                    consumerImpl.releaseSendLock();
                }
            }
        }
    }


    public AMQProtocolEngine(Broker<?> broker,
                             final NetworkConnection network,
                             final long connectionId,
                             AmqpPort<?> port,
                             Transport transport)
    {
        _broker = broker;
        _port = port;
        _transport = transport;
        _maxNoOfChannels = broker.getConnection_sessionCountLimit();
        _decoder = new BrokerDecoder(this);
        _connectionId = connectionId;
        _logSubject = new ConnectionLogSubject(this);
        _binaryDataLimit = _broker.getContextKeys(false).contains(BROKER_DEBUG_BINARY_DATA_LENGTH)
                ? _broker.getContextValue(Integer.class, BROKER_DEBUG_BINARY_DATA_LENGTH)
                : DEFAULT_DEBUG_BINARY_DATA_LENGTH;

        int maxMessageSize = port.getContextValue(Integer.class, AmqpPort.PORT_MAX_MESSAGE_SIZE);
        _maxMessageSize = (maxMessageSize > 0) ? (long) maxMessageSize : Long.MAX_VALUE;

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

        _messagesDelivered = new StatisticsCounter("messages-delivered-" + _connectionId);
        _dataDelivered = new StatisticsCounter("data-delivered-" + _connectionId);
        _messagesReceived = new StatisticsCounter("messages-received-" + _connectionId);
        _dataReceived = new StatisticsCounter("data-received-" + _connectionId);
        _creationTime = System.currentTimeMillis();
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

    @Override
    public boolean isTransportBlockedForWriting()
    {
        return _transportBlockedForWriting;
    }

    @Override
    public void setTransportBlockedForWriting(final boolean blocked)
    {
        _transportBlockedForWriting = blocked;
        for(AMQChannel channel : _channelMap.values())
        {
            channel.transportStateChanged();
        }
    }

    public void setNetworkConnection(NetworkConnection network)
    {
        setNetworkConnection(network, network.getSender());
    }

    public void setNetworkConnection(NetworkConnection network, ByteBufferSender sender)
    {
        _network = network;
        _sender = sender;
    }

    public void setMaxFrameSize(int frameMax)
    {
        _maxFrameSize = frameMax;
        _decoder.setMaxFrameSize(frameMax);
    }

    public long getMaxFrameSize()
    {
        return _maxFrameSize;
    }

    public boolean isClosing()
    {
        return _closing.get();
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
                if (!_authenticated &&
                    (arrivalTime - _creationTime) > _port.getContextValue(Long.class,
                                                                          Port.CONNECTION_MAXIMUM_AUTHENTICATION_DELAY))
                {
                    _logger.warn("Connection has taken more than "
                                 + _port.getContextValue(Long.class, Port.CONNECTION_MAXIMUM_AUTHENTICATION_DELAY)
                                 + "ms to establish identity.  Closing as possible DoS.");
                    getEventLogger().message(ConnectionMessages.IDLE_CLOSE());
                    closeNetworkConnection();
                }
                _lastReceivedTime = arrivalTime;
                _lastIoTime = arrivalTime;
                _readBytes += msg.remaining();

                try
                {
                    _decoder.decodeBuffer(msg);
                    receivedComplete();
                }
                catch (ConnectionScopedRuntimeException e)
                {
                    _logger.error("Unexpected exception", e);
                    closeNetworkConnection();
                }
                catch (AMQProtocolVersionException e)
                {
                    _logger.error("Unexpected protocol version", e);
                    closeNetworkConnection();
                }
                catch (SenderClosedException e)
                {
                    _logger.debug("Sender was closed abruptly, closing network.", e);
                    closeNetworkConnection();
                }
                catch (SenderException e)
                {
                    _logger.info("Unexpected exception on send, closing network.", e);
                    closeNetworkConnection();
                }
                catch (TransportException e)
                {
                    _logger.error("Unexpected transport exception", e);
                    closeNetworkConnection();
                }
                catch (AMQFrameDecodingException e)
                {
                    _logger.error("Frame decoding", e);
                    closeNetworkConnection();
                }
                catch (IOException e)
                {
                    _logger.error("I/O Exception", e);
                    closeNetworkConnection();
                }
                catch (StoreException e)
                {
                    if (_virtualHost.getState() == State.ACTIVE)
                    {
                        throw e;
                    }
                    else
                    {
                        _logger.error("Store Exception ignored as virtual host no longer active", e);
                    }
                }
                return null;
            }
        });

    }

    private void receivedComplete()
    {
        RuntimeException exception = null;

        for (AMQChannel channel : _channelsForCurrentMessage)
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


    void channelRequiresSync(final AMQChannel amqChannel)
    {
        _channelsForCurrentMessage.add(amqChannel);
    }

    private synchronized void protocolInitiationReceived(ProtocolInitiation pi)
    {
        // this ensures the codec never checks for a PI message again
        _decoder.setExpectProtocolInitiation(false);
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
            serverProperties.setString(ConnectionStartProperties.QPID_MESSAGE_COMPRESSION_SUPPORTED,
                                       String.valueOf(_broker.isMessageCompressionEnabled()));
            serverProperties.setString(ConnectionStartProperties.QPID_CONFIRMED_PUBLISH_SUPPORTED, Boolean.TRUE.toString());

            AMQMethodBody responseBody = getMethodRegistry().createConnectionStartBody((short) getProtocolMajorVersion(),
                                                                                       (short) pv.getActualMinorVersion(),
                                                                                       serverProperties,
                                                                                       mechanisms.getBytes(),
                                                                                       locales.getBytes());
            writeFrame(responseBody.generateFrame(0));
            _state = ConnectionState.AWAIT_START_OK;

            _sender.flush();

        }
        catch (AMQException e)
        {
            _logger.info("Received unsupported protocol initiation for protocol version: " + getProtocolVersion());

            writeFrame(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion()));
            _sender.flush();
        }
    }




    public synchronized void writeFrame(AMQDataBlock frame)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("SEND: " + frame);
        }

        try
        {
            _writtenBytes += frame.writePayload(_sender);
        }
        catch (IOException e)
        {
            throw new ServerScopedRuntimeException(e);
        }


        final long time = System.currentTimeMillis();
        _lastIoTime = time;
        _lastWriteTime = time;

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

    public List<AMQChannel> getChannels()
    {
        synchronized (_channelMap)
        {
            return new ArrayList<>(_channelMap.values());
        }
    }

    public AMQChannel getChannel(int channelId)
    {
        final AMQChannel channel =
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

    public void addChannel(AMQChannel channel)
    {
        final int channelId = channel.getChannelId();

        synchronized (_channelMap)
        {
            _channelMap.put(channel.getChannelId(), channel);
            sessionAdded(channel);
            if(_blocking)
            {
                channel.block();
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

    public long getMaximumNumberOfChannels()
    {
        return _maxNoOfChannels;
    }

    public void setMaximumNumberOfChannels(Long value)
    {
        _maxNoOfChannels = value;
    }


    void closeChannel(AMQChannel channel)
    {
        closeChannel(channel, null, null, false);
    }

    public void closeChannelAndWriteFrame(AMQChannel channel, AMQConstant cause, String message)
    {
        writeFrame(new AMQFrame(channel.getChannelId(),
                                getMethodRegistry().createChannelCloseBody(cause.getCode(),
                                                                           AMQShortString.validValueOf(message),
                                                                           _currentClassId,
                                                                           _currentMethodId)));
        closeChannel(channel, cause, message, true);
    }

    public void closeChannel(int channelId, AMQConstant cause, String message)
    {
        final AMQChannel channel = getChannel(channelId);
        if (channel == null)
        {
            throw new IllegalArgumentException("Unknown channel id");
        }
        closeChannel(channel, cause, message, true);
    }

    void closeChannel(AMQChannel channel, AMQConstant cause, String message, boolean mark)
    {
        int channelId = channel.getChannelId();
        try
        {
            channel.close(cause, message);
            if(mark)
            {
                markChannelAwaitingCloseOk(channelId);
            }
        }
        finally
        {
            removeChannel(channelId);
        }
    }


    public void closeChannelOk(int channelId)
    {
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
        AMQChannel session;
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

    private void initHeartbeats(int delay)
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

    private void closeAllChannels()
    {
        try
        {
            RuntimeException firstException = null;
            for (AMQChannel channel : getChannels())
            {
                try
                {
                    channel.close();
                }
                catch (RuntimeException re)
                {
                    if (!(re instanceof ConnectionScopedRuntimeException))
                    {
                        _logger.error("Unexpected exception closing channel", re);
                    }
                    firstException = re;
                }
            }

            if (firstException != null)
            {
                throw firstException;
            }
        }
        finally
        {
            synchronized (_channelMap)
            {
                _channelMap.clear();
            }
            for (int i = 0; i <= CHANNEL_CACHE_SIZE; i++)
            {
                _cachedChannels[i] = null;
            }

        }
    }

    private void closeConnectionInternal(final boolean connectionDropped)
    {

        if(runningAsSubject())
        {
            if(_closing.compareAndSet(false,true))
            {
                // force sync of outstanding async work
                try
                {
                    receivedComplete();
                }
                finally
                {

                    finishClose(connectionDropped);
                }

            }
            else
            {
                awaitClosed();
            }
        }
        else
        {
            runAsSubject(new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    closeConnectionInternal(connectionDropped);
                    return null;
                }
            });

        }
    }

    private void finishClose(boolean connectionDropped)
    {
        if (!_closed)
        {

            try
            {
                if (_virtualHost != null)
                {
                    _virtualHost.getConnectionRegistry().deregisterConnection(this);
                }
                closeAllChannels();
            }
            finally
            {
                try
                {
                    for (Action<? super AMQProtocolEngine> task : _connectionCloseTaskList)
                    {
                        task.performAction(this);
                    }
                }
                finally
                {
                    synchronized (this)
                    {
                        _closed = true;
                        notifyAll();
                    }
                    getEventLogger().message(_logSubject, connectionDropped ? ConnectionMessages.DROPPED_CONNECTION() : ConnectionMessages.CLOSE());
                }
            }
        }
    }

    private void awaitClosed()
    {
        synchronized(this)
        {
            final long endTime = System.currentTimeMillis() + AWAIT_CLOSED_TIMEOUT;

            while(!_closed && endTime > System.currentTimeMillis())
            {
                try
                {
                    wait(1000);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!_closed)
            {
                throw new ConnectionScopedRuntimeException("Connection " + this + " failed to become closed within " + AWAIT_CLOSED_TIMEOUT + "ms.");
            }
        }
    }

    private void closeConnection(int channelId, AMQConnectionException e)
    {

        if (_logger.isInfoEnabled())
        {
            _logger.info("Closing connection due to: " + e);
        }
        closeConnection(channelId, e.getCloseFrame());
    }


    void closeConnection(AMQConstant errorCode,
                         String message, int channelId)
    {

        if (_logger.isInfoEnabled())
        {
            _logger.info("Closing connection due to: " + message);
        }
        closeConnection(channelId, new AMQFrame(0, new ConnectionCloseBody(getProtocolVersion(), errorCode.getCode(), AMQShortString.validValueOf(message), _currentClassId, _currentMethodId)));
    }

    private void closeConnection(int channelId, AMQFrame frame)
    {
        if(!_closing.get())
        {
            try
            {
                markChannelAwaitingCloseOk(channelId);
                closeConnectionInternal(false);
            }
            finally
            {
                try
                {
                    writeFrame(frame);
                }
                finally
                {
                    closeNetworkConnection();
                }
            }
        }
        else
        {
            awaitClosed();
        }
    }

    public void closeNetworkConnection()
    {
        _network.close();
    }

    public String toString()
    {
        return getRemoteAddress() + "(" + (getAuthorizedPrincipal() == null ? "?" : getAuthorizedPrincipal().getName() + ")");
    }

    private String getLocalFQDN()
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
            String compressionSupported = clientProperties.getString(ConnectionStartProperties.QPID_MESSAGE_COMPRESSION_SUPPORTED);
            if (compressionSupported != null)
            {
                _compressionSupported = Boolean.parseBoolean(compressionSupported);
                if(_logger.isDebugEnabled())
                {
                    _logger.debug("Client set compressionSupported=" + _compressionSupported + " for protocol engine " + this);
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
        _methodRegistry.setProtocolVersion(_protocolVersion);
        _protocolOutputConverter = new ProtocolOutputConverterImpl(this);
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

    public VirtualHostImpl<?,?,?> getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHostImpl<?,?,?> virtualHost)
    {
        _virtualHost = virtualHost;

        _virtualHost.getConnectionRegistry().registerConnection(this);


        _messageCompressionThreshold = virtualHost.getContextValue(Integer.class,
                                                                   Broker.MESSAGE_COMPRESSION_THRESHOLD_SIZE);
        if(_messageCompressionThreshold <= 0)
        {
            _messageCompressionThreshold = Integer.MAX_VALUE;
        }
    }

    public void addDeleteTask(Action<? super AMQProtocolEngine> task)
    {
        _connectionCloseTaskList.add(task);
    }

    public void removeDeleteTask(Action<? super AMQProtocolEngine> task)
    {
        _connectionCloseTaskList.remove(task);
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

        _authenticated = true;
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

    public void closed()
    {
        try
        {
            try
            {
                closeConnectionInternal(true);
            }
            finally
            {
                closeNetworkConnection();
            }
        }
        catch (ConnectionScopedRuntimeException | TransportException e)
        {
            _logger.error("Could not close protocol engine", e);
        }
    }

    @Override
    public void encryptedTransport()
    {
    }

    public void readerIdle()
    {
        Subject.doAs(_authorizedSubject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                getEventLogger().message(ConnectionMessages.IDLE_CLOSE());
                _network.close();
                return null;
            }
        });
    }

    public synchronized void writerIdle()
    {
        writeFrame(HeartbeatBody.FRAME);
    }

    public void exception(Throwable throwable)
    {
        if (throwable instanceof AMQProtocolHeaderException)
        {
            sendResponseAndCloseSender(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion()));

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

                ConnectionCloseBody closeBody = _methodRegistry.createConnectionCloseBody(AMQConstant.INTERNAL_ERROR.getCode(),
                                                                                             AMQShortString.validValueOf(
                                                                                                     throwable.getMessage()),
                                                                                             _currentClassId,
                                                                                             _currentMethodId);
                sendResponseAndCloseSender(closeBody.generateFrame(0));
            }
            finally
            {
                if (!(throwable instanceof TransportException
                        || throwable instanceof ConnectionScopedRuntimeException))
                {
                    if (throwable instanceof Error)
                    {
                        throw (Error) throwable;
                    }

                    if (throwable instanceof RuntimeException)
                    {
                        throw (RuntimeException) throwable;
                    }

                    if (throwable instanceof Throwable)
                    {
                        throw new ServerScopedRuntimeException("Unexpected exception", throwable);
                    }
                }
            }
        }
    }

    private void sendResponseAndCloseSender(AMQDataBlock dataBlock)
    {
        try
        {
            writeFrame(dataBlock);
        }
        catch(SenderException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Exception occurred on sending response", e);
            }
        }
        finally
        {
            try
            {
                _sender.close();
            }
            catch(SenderException e)
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Exception occurred on sender close", e);
                }
            }
        }
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
    public AmqpPort<?> getPort()
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
        return _connectionId;
    }

    public String getAddress()
    {
        return String.valueOf(getRemoteAddress());
    }

    public void closeSessionAsync(final AMQChannel session, final AMQConstant cause, final String message)
    {
        addAsyncTask(new Action<AMQProtocolEngine>()
        {

            @Override
            public void performAction(final AMQProtocolEngine object)
            {
                int channelId = session.getChannelId();
                closeChannel(channelId, cause, message);

                MethodRegistry methodRegistry = getMethodRegistry();
                ChannelCloseBody responseBody =
                        methodRegistry.createChannelCloseBody(
                                cause.getCode(),
                                AMQShortString.validValueOf(message),
                                0, 0);

                writeFrame(responseBody.generateFrame(channelId));
            }
        });

    }

    public void closeAsync(final AMQConstant cause, final String message)
    {
        Action<AMQProtocolEngine> action = new Action<AMQProtocolEngine>()
        {
            @Override
            public void performAction(final AMQProtocolEngine object)
            {
                closeConnection(0, new AMQConnectionException(cause, message, 0, 0,
                                                              getMethodRegistry(),
                                                              null));

            }
        };
        addAsyncTask(action);
    }

    private void addAsyncTask(final Action<AMQProtocolEngine> action)
    {
        _asyncTaskList.add(action);
        notifyWork();
    }

    public void block()
    {
        synchronized (_channelMap)
        {
            if(!_blocking)
            {
                _blocking = true;
                for(AMQChannel channel : _channelMap.values())
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
                for(AMQChannel channel : _channelMap.values())
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

    public List<AMQChannel> getSessionModels()
    {
		return new ArrayList<>(getChannels());
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

    @Override
    public void receiveChannelOpen(final int channelId)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV[" + channelId + "] ChannelOpen");
        }
        assertState(ConnectionState.OPEN);

        // Protect the broker against out of order frame request.
        if (_virtualHost == null)
        {
            closeConnection(AMQConstant.COMMAND_INVALID,
                            "Virtualhost has not yet been set. ConnectionOpen has not been called.", channelId);
        }
        else if(getChannel(channelId) != null || channelAwaitingClosure(channelId))
        {
            closeConnection(AMQConstant.CHANNEL_ERROR, "Channel " + channelId + " already exists", channelId);
        }
        else if(channelId > getMaximumNumberOfChannels())
        {
            closeConnection(AMQConstant.CHANNEL_ERROR,
                            "Channel " + channelId + " cannot be created as the max allowed channel id is "
                            + getMaximumNumberOfChannels(),
                            channelId);
        }
        else
        {
            _logger.info("Connecting to: " + _virtualHost.getName());

            final AMQChannel channel = new AMQChannel(this, channelId, _virtualHost.getMessageStore());

            addChannel(channel);

            ChannelOpenOkBody response;


            response = getMethodRegistry().createChannelOpenOkBody();


            writeFrame(response.generateFrame(channelId));
        }
    }

    void assertState(final ConnectionState requiredState)
    {
        if(_state != requiredState)
        {
            closeConnection(AMQConstant.COMMAND_INVALID, "Command Invalid", 0);

        }
    }

    @Override
    public void receiveConnectionOpen(AMQShortString virtualHostName,
                                      AMQShortString capabilities,
                                      boolean insist)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV ConnectionOpen[" +" virtualHost: " + virtualHostName + " capabilities: " + capabilities + " insist: " + insist + " ]");
        }

        String virtualHostStr;
        if ((virtualHostName != null) && virtualHostName.charAt(0) == '/')
        {
            virtualHostStr = virtualHostName.toString().substring(1);
        }
        else
        {
            virtualHostStr = virtualHostName == null ? null : virtualHostName.toString();
        }

        VirtualHostImpl<?,?,?> virtualHost = ((AmqpPort)getPort()).getVirtualHost(virtualHostStr);

        if (virtualHost == null)
        {
            closeConnection(AMQConstant.NOT_FOUND,
                            "Unknown virtual host: '" + virtualHostName + "'",0);

        }
        else
        {
            // Check virtualhost access
            if (virtualHost.getState() != State.ACTIVE)
            {
                String redirectHost = virtualHost.getRedirectHost(getPort());
                if(redirectHost != null)
                {
                    closeConnection(0, new AMQFrame(0,new ConnectionRedirectBody(getProtocolVersion(),AMQShortString.valueOf(redirectHost), null)));
                }
                else
                {
                    closeConnection(AMQConstant.CONNECTION_FORCED,
                                    "Virtual host '" + virtualHost.getName() + "' is not active", 0);
                }

            }
            else
            {
                setVirtualHost(virtualHost);
                try
                {

                    if(virtualHost.authoriseCreateConnection(this))
                    {
                        if (getContextKey() == null)
                        {
                            setContextKey(new AMQShortString(Long.toString(System.currentTimeMillis())));
                        }

                        MethodRegistry methodRegistry = getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createConnectionOpenOkBody(virtualHostName);

                        writeFrame(responseBody.generateFrame(0));
                        _state = ConnectionState.OPEN;

                    }
                    else
                    {
                        closeConnection(AMQConstant.ACCESS_REFUSED, "Connection refused",0);
                    }
                }
                catch (AccessControlException e)
                {
                    closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage(), 0);
                }
            }
        }
    }

    @Override
    public void receiveConnectionClose(final int replyCode,
                                       final AMQShortString replyText,
                                       final int classId,
                                       final int methodId)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV ConnectionClose[" +" replyCode: " + replyCode + " replyText: " + replyText + " classId: " + classId + " methodId: " + methodId + " ]");
        }

        if (_logger.isInfoEnabled())
        {
            _logger.info("ConnectionClose received with reply code/reply text " + replyCode + "/" +
                         replyText + " for " + this);
        }
        try
        {
            closeConnectionInternal(false);

            MethodRegistry methodRegistry = getMethodRegistry();
            ConnectionCloseOkBody responseBody = methodRegistry.createConnectionCloseOkBody();
            writeFrame(responseBody.generateFrame(0));
        }
        catch (Exception e)
        {
            _logger.error("Error closing connection for " + getRemoteAddressString(), e);
        }
        finally
        {
            closeNetworkConnection();
        }
    }

    @Override
    public void receiveConnectionCloseOk()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV ConnectionCloseOk");
        }

        _logger.info("Received Connection-close-ok");

        try
        {
            closeConnectionInternal(false);
        }
        catch (Exception e)
        {
            _logger.error("Error closing connection: " + getRemoteAddressString(), e);
        }
        finally
        {
            closeNetworkConnection();
        }

    }

    @Override
    public void receiveConnectionSecureOk(final byte[] response)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV ConnectionSecureOk[ response: ******** ] ");
        }

        assertState(ConnectionState.AWAIT_SECURE_OK);

        Broker<?> broker = getBroker();

        SubjectCreator subjectCreator = getSubjectCreator();

        SaslServer ss = getSaslServer();
        if (ss == null)
        {
            closeConnection(AMQConstant.INTERNAL_ERROR, "No SASL context set up in connection",0 );
        }
        MethodRegistry methodRegistry = getMethodRegistry();
        SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, response);
        switch (authResult.getStatus())
        {
            case ERROR:
                Exception cause = authResult.getCause();

                _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                closeConnection(AMQConstant.NOT_ALLOWED, "Authentication failed",0);

                disposeSaslServer();
                break;
            case SUCCESS:
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Connected as: " + authResult.getSubject());
                }

                int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                if (frameMax <= 0)
                {
                    frameMax = Integer.MAX_VALUE;
                }

                ConnectionTuneBody tuneBody =
                        methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                frameMax,
                                                                broker.getConnection_heartBeatDelay());
                writeFrame(tuneBody.generateFrame(0));
                _state = ConnectionState.AWAIT_TUNE_OK;
                setAuthorizedSubject(authResult.getSubject());
                disposeSaslServer();
                break;
            case CONTINUE:

                ConnectionSecureBody
                        secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                writeFrame(secureBody.generateFrame(0));
        }
    }


    private void disposeSaslServer()
    {
        SaslServer ss = getSaslServer();
        if (ss != null)
        {
            setSaslServer(null);
            try
            {
                ss.dispose();
            }
            catch (SaslException e)
            {
                _logger.error("Error disposing of Sasl server: " + e);
            }
        }
    }

    @Override
    public void receiveConnectionStartOk(final FieldTable clientProperties,
                                         final AMQShortString mechanism,
                                         final byte[] response,
                                         final AMQShortString locale)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("RECV ConnectionStartOk["
                          + " clientProperties: "
                          + clientProperties
                          + " mechanism: "
                          + mechanism
                          + " response: ********"
                          + " locale: "
                          + locale
                          + " ]");
        }

        assertState(ConnectionState.AWAIT_START_OK);

        Broker<?> broker = getBroker();

        _logger.info("SASL Mechanism selected: " + mechanism);
        _logger.info("Locale selected: " + locale);

        SubjectCreator subjectCreator = getSubjectCreator();
        SaslServer ss;
        try
        {
            ss = subjectCreator.createSaslServer(String.valueOf(mechanism),
                                                 getLocalFQDN(),
                                                 getPeerPrincipal());

            if (ss == null)
            {
                closeConnection(AMQConstant.RESOURCE_ERROR, "Unable to create SASL Server:" + mechanism, 0);

            }
            else
            {
                //save clientProperties
                setClientProperties(clientProperties);

                setSaslServer(ss);

                final SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, response);

                MethodRegistry methodRegistry = getMethodRegistry();

                switch (authResult.getStatus())
                {
                    case ERROR:
                        Exception cause = authResult.getCause();

                        _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                        closeConnection(AMQConstant.NOT_ALLOWED, "Authentication failed", 0);

                        disposeSaslServer();
                        break;

                    case SUCCESS:
                        if (_logger.isInfoEnabled())
                        {
                            _logger.info("Connected as: " + authResult.getSubject());
                        }
                        setAuthorizedSubject(authResult.getSubject());

                        int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                        if (frameMax <= 0)
                        {
                            frameMax = Integer.MAX_VALUE;
                        }

                        ConnectionTuneBody
                                tuneBody =
                                methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                        frameMax,
                                                                        broker.getConnection_heartBeatDelay());
                        writeFrame(tuneBody.generateFrame(0));
                        _state = ConnectionState.AWAIT_TUNE_OK;
                        break;
                    case CONTINUE:
                        ConnectionSecureBody
                                secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                        writeFrame(secureBody.generateFrame(0));

                        _state = ConnectionState.AWAIT_SECURE_OK;
                }
            }
        }
        catch (SaslException e)
        {
            disposeSaslServer();
            closeConnection(AMQConstant.INTERNAL_ERROR, "SASL error: " + e, 0);
        }
    }

    @Override
    public void receiveConnectionTuneOk(final int channelMax, final long frameMax, final int heartbeat)
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV ConnectionTuneOk[" +" channelMax: " + channelMax + " frameMax: " + frameMax + " heartbeat: " + heartbeat + " ]");
        }

        assertState(ConnectionState.AWAIT_TUNE_OK);

        initHeartbeats(heartbeat);

        int brokerFrameMax = getBroker().getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);
        if (brokerFrameMax <= 0)
        {
            brokerFrameMax = Integer.MAX_VALUE;
        }

        if (frameMax > (long) brokerFrameMax)
        {
            closeConnection(AMQConstant.SYNTAX_ERROR,
                            "Attempt to set max frame size to " + frameMax
                            + " greater than the broker will allow: "
                            + brokerFrameMax, 0);
        }
        else if (frameMax > 0 && frameMax < AMQConstant.FRAME_MIN_SIZE.getCode())
        {
            closeConnection(AMQConstant.SYNTAX_ERROR,
                            "Attempt to set max frame size to " + frameMax
                            + " which is smaller than the specification defined minimum: "
                            + AMQConstant.FRAME_MIN_SIZE.getCode(), 0);
        }
        else
        {
            int calculatedFrameMax = frameMax == 0 ? brokerFrameMax : (int) frameMax;
            setMaxFrameSize(calculatedFrameMax);

            //0 means no implied limit, except that forced by protocol limitations (0xFFFF)
            setMaximumNumberOfChannels( ((channelMax == 0l) || (channelMax > 0xFFFFL))
                                               ? 0xFFFFL
                                               : channelMax);

        }
        _state = ConnectionState.AWAIT_OPEN;

    }

    public int getBinaryDataLimit()
    {
        return _binaryDataLimit;
    }

    public long getMaxMessageSize()
    {
        return _maxMessageSize;
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
        public long deliverToClient(final ConsumerImpl sub, final ServerMessage message,
                                    final InstanceProperties props, final long deliveryTag)
        {
            long size = _protocolOutputConverter.writeDeliver(message,
                                                  props,
                                                  _channelId,
                                                  deliveryTag,
                                                  new AMQShortString(sub.getName()));
            registerMessageDelivered(size);
            return size;
        }

    }

    public Object getReference()
    {
        return _reference;
    }

    @Override
    public long getLastReadTime()
    {
        return _lastReceivedTime;
    }

    @Override
    public long getLastWriteTime()
    {
        return _lastWriteTime;
    }

    public boolean isCloseWhenNoRoute()
    {
        return _closeWhenNoRoute;
    }

    public boolean isCompressionSupported()
    {
        return _compressionSupported && _broker.isMessageCompressionEnabled();
    }

    public int getMessageCompressionThreshold()
    {
        return _messageCompressionThreshold;
    }

    public Broker<?> getBroker()
    {
        return _broker;
    }

    public SubjectCreator getSubjectCreator()
    {
        return _broker.getSubjectCreator(getLocalAddress(), getTransport().isSecure());
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

    @Override
    public ServerChannelMethodProcessor getChannelMethodProcessor(final int channelId)
    {
        assertState(ConnectionState.OPEN);

        ServerChannelMethodProcessor channelMethodProcessor = getChannel(channelId);
        if(channelMethodProcessor == null)
        {
            channelMethodProcessor = (ServerChannelMethodProcessor) Proxy.newProxyInstance(ServerMethodDispatcher.class.getClassLoader(),
                                                            new Class[] { ServerChannelMethodProcessor.class }, new InvocationHandler()
                    {
                        @Override
                        public Object invoke(final Object proxy, final Method method, final Object[] args)
                                throws Throwable
                        {
                            if(method.getName().startsWith("receive"))
                            {
                                closeConnection(AMQConstant.CHANNEL_ERROR,
                                                "Unknown channel id: " + channelId,
                                                channelId);
                                return null;
                            }
                            else if(method.getName().equals("ignoreAllButCloseOk"))
                            {
                                return false;
                            }
                            return null;
                        }
                    });
        }
        return channelMethodProcessor;
    }

    @Override
    public void receiveHeartbeat()
    {
        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV Heartbeat");
        }

        // No op
    }

    @Override
    public void receiveProtocolHeader(final ProtocolInitiation protocolInitiation)
    {

        if(_logger.isDebugEnabled())
        {
            _logger.debug("RECV ProtocolHeader [" + protocolInitiation + " ]");
        }

        protocolInitiationReceived(protocolInitiation);
    }

    @Override
    public void setCurrentMethod(final int classId, final int methodId)
    {
        _currentClassId = classId;
        _currentMethodId = methodId;
    }

    @Override
    public boolean ignoreAllButCloseOk()
    {
        return _closing.get();
    }

    @Override
    public void processPending()
    {
        for (AMQSessionModel session : getSessionModels())
        {
            session.processPending();
        }

        while(_asyncTaskList.peek() != null)
        {
            Action<? super AMQProtocolEngine> asyncAction = _asyncTaskList.poll();
            asyncAction.performAction(this);
        }

    }

    @Override
    public boolean hasWork()
    {
        return _stateChanged.get();
    }

    @Override
    public void notifyWork()
    {
        _stateChanged.set(true);

        final Action<ServerProtocolEngine> listener = _workListener.get();
        if(listener != null)
        {

            listener.performAction(this);
        }
    }

    @Override
    public void clearWork()
    {
        _stateChanged.set(false);
    }

    @Override
    public void setWorkListener(final Action<ServerProtocolEngine> listener)
    {
        _workListener.set(listener);
    }
}
