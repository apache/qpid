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
package org.apache.qpid.server.protocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
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
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.*;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.handler.ServerMethodDispatcherImpl;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.AMQPConnectionActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.ManagementActor;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.logging.subjects.ConnectionLogSubject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.output.ProtocolOutputConverterRegistry;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.security.auth.AuthenticatedPrincipal;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.subscription.ClientDeliveryMethod;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionImpl;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.util.BytesDataOutput;

public class AMQProtocolEngine implements ServerProtocolEngine, AMQProtocolSession
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolEngine.class);

    // to save boxing the channelId and looking up in a map... cache in an array the low numbered
    // channels.  This value must be of the form 2^x - 1.
    private static final int CHANNEL_CACHE_SIZE = 0xff;
    private static final int REUSABLE_BYTE_BUFFER_CAPACITY = 65 * 1024;

    private AMQShortString _contextKey;

    private String _clientVersion = null;

    private VirtualHost _virtualHost;

    private final Map<Integer, AMQChannel> _channelMap = new HashMap<Integer, AMQChannel>();

    private final AMQChannel[] _cachedChannels = new AMQChannel[CHANNEL_CACHE_SIZE + 1];

    private final CopyOnWriteArraySet<AMQMethodListener> _frameListeners = new CopyOnWriteArraySet<AMQMethodListener>();

    private final AMQStateManager _stateManager;

    private AMQCodecFactory _codecFactory;

    private SaslServer _saslServer;

    private Object _lastReceived;

    private Object _lastSent;

    private volatile boolean _closed;

    // maximum number of channels this session should have
    private long _maxNoOfChannels;

    /* AMQP Version for this session */
    private ProtocolVersion _protocolVersion = ProtocolVersion.getLatestSupportedVersion();
    private MethodRegistry _methodRegistry = MethodRegistry.getMethodRegistry(_protocolVersion);
    private FieldTable _clientProperties;
    private final List<Task> _taskList = new CopyOnWriteArrayList<Task>();

    private Map<Integer, Long> _closingChannelsList = new ConcurrentHashMap<Integer, Long>();
    private ProtocolOutputConverter _protocolOutputConverter;
    private Subject _authorizedSubject;
    private MethodDispatcher _dispatcher;

    private final long _connectionID;
    private Object _reference = new Object();

    private AMQPConnectionActor _actor;
    private LogSubject _logSubject;

    private long _lastIoTime;

    private long _writtenBytes;
    private long _readBytes;


    private long _maxFrameSize;
    private final AtomicBoolean _closing = new AtomicBoolean(false);
    private long _createTime = System.currentTimeMillis();

    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private NetworkConnection _network;
    private Sender<ByteBuffer> _sender;

    private volatile boolean _deferFlush;
    private long _lastReceivedTime;
    private boolean _blocking;

    private final Lock _receivedLock;
    private AtomicLong _lastWriteTime = new AtomicLong(System.currentTimeMillis());
    private final Broker _broker;


    public AMQProtocolEngine(Broker broker, NetworkConnection network, final long connectionId)
    {
        _broker = broker;
        _maxNoOfChannels = (Integer)broker.getAttribute(Broker.SESSION_COUNT_LIMIT);
        _receivedLock = new ReentrantLock();
        _stateManager = new AMQStateManager(broker, this);
        _codecFactory = new AMQCodecFactory(true, this);

        setNetworkConnection(network);
        _connectionID = connectionId;

        _actor = new AMQPConnectionActor(this, _broker.getRootMessageLogger());

        _logSubject = new ConnectionLogSubject(this);

        _actor.message(ConnectionMessages.OPEN(null, null, null, false, false, false));

        initialiseStatistics();

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

    public LogActor getLogActor()
    {
        return _actor;
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
        final long arrivalTime = System.currentTimeMillis();
        _lastReceivedTime = arrivalTime;
        _lastIoTime = arrivalTime;

        _receivedLock.lock();
        try
        {
            final ArrayList<AMQDataBlock> dataBlocks = _codecFactory.getDecoder().decodeBuffer(msg);
            final int len = dataBlocks.size();
            for (int i = 0; i < len; i++)
            {
                AMQDataBlock dataBlock = dataBlocks.get(i);
                try
                {
                    dataBlockReceived(dataBlock);
                }
                catch (Exception e)
                {
                    _logger.error("Unexpected exception when processing datablock", e);
                    closeProtocolSession();
                }
            }
            receiveComplete();
        }
        catch (Exception e)
        {
            _logger.error("Unexpected exception when processing datablock", e);
            closeProtocolSession();
        }
        finally
        {
            _receivedLock.unlock();
        }
    }

    private void receiveComplete()
    {
        for (AMQChannel channel : _channelMap.values())
        {
            channel.receivedComplete();
        }

    }

    public void dataBlockReceived(AMQDataBlock message) throws Exception
    {
        _lastReceived = message;
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

    private void frameReceived(AMQFrame frame) throws AMQException
    {
        int channelId = frame.getChannel();
        AMQBody body = frame.getBodyFrame();

        //Look up the Channel's Actor and set that as the current actor
        // If that is not available then we can use the ConnectionActor
        // that is associated with this AMQMPSession.
        LogActor channelActor = null;
        if (_channelMap.get(channelId) != null)
        {
            channelActor = _channelMap.get(channelId).getLogActor();
        }
        CurrentActor.set(channelActor == null ? _actor : channelActor);

        try
        {
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
            catch (AMQException e)
            {
                closeChannel(channelId);
                throw e;
            }
            catch (TransportException e)
            {
                closeChannel(channelId);
                throw e;
            }

            if(_logger.isDebugEnabled())
            {
                _logger.debug("Frame handled in " + (System.currentTimeMillis() - startTime) + " ms. Frame: " + frameToString);
            }
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    private synchronized void protocolInitiationReceived(ProtocolInitiation pi)
    {
        // this ensures the codec never checks for a PI message again
        (_codecFactory.getDecoder()).setExpectProtocolInitiation(false);
        try
        {
            // Log incomming protocol negotiation request
            _actor.message(ConnectionMessages.OPEN(null, pi.getProtocolMajor() + "-" + pi.getProtocolMinor(), null, false, true, false));

            ProtocolVersion pv = pi.checkVersion(); // Fails if not correct

            // This sets the protocol version (and hence framing classes) for this session.
            setProtocolVersion(pv);

            String mechanisms = _broker.getSubjectCreator(getLocalAddress()).getMechanisms();

            String locales = "en_US";

            AMQMethodBody responseBody = getMethodRegistry().createConnectionStartBody((short) getProtocolMajorVersion(),
                                                                                       (short) pv.getActualMinorVersion(),
                                                                                       null,
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
            throw new RuntimeException(e);
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

                if (!_frameListeners.isEmpty())
                {
                    for (AMQMethodListener listener : _frameListeners)
                    {
                        wasAnyoneInterested = listener.methodReceived(evt) || wasAnyoneInterested;
                    }
                }

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
                    closeChannel(channelId);
                }
                else
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("ChannelException occured on non-existent channel:" + e.getMessage());
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
            catch (AMQSecurityException e)
            {
                AMQConnectionException ce = evt.getMethod().getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage());
                _logger.info(e.getMessage() + " whilst processing:" + methodBody);
                closeConnection(channelId, ce);
            }
        }
        catch (Exception e)
        {
            for (AMQMethodListener listener : _frameListeners)
            {
                listener.error(e);
            }

            _logger.error("Unexpected exception while processing frame.  Closing connection.", e);

            closeProtocolSession();
        }
    }

    public void contentHeaderReceived(int channelId, ContentHeaderBody body) throws AMQException
    {

        AMQChannel channel = getAndAssertChannel(channelId);

        channel.publishContentHeader(body);

    }

    public void contentBodyReceived(int channelId, ContentBody body) throws AMQException
    {
        AMQChannel channel = getAndAssertChannel(channelId);

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

    public List<AMQChannel> getChannels()
    {
        synchronized (_channelMap)
        {
            return new ArrayList<AMQChannel>(_channelMap.values());
        }
    }

    public AMQChannel getAndAssertChannel(int channelId) throws AMQException
    {
        AMQChannel channel = getChannel(channelId);
        if (channel == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Channel not found with id:" + channelId);
        }

        return channel;
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

    public void addChannel(AMQChannel channel) throws AMQException
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

    public Long getMaximumNumberOfChannels()
    {
        return _maxNoOfChannels;
    }

    public void setMaximumNumberOfChannels(Long value)
    {
        _maxNoOfChannels = value;
    }

    public void commitTransactions(AMQChannel channel) throws AMQException
    {
        if ((channel != null) && channel.isTransactional())
        {
            channel.commit();
        }
    }

    public void rollbackTransactions(AMQChannel channel) throws AMQException
    {
        if ((channel != null) && channel.isTransactional())
        {
            channel.rollback();
        }
    }

    /**
     * Close a specific channel. This will remove any resources used by the channel, including: <ul><li>any queue
     * subscriptions (this may in turn remove queues if they are auto delete</li> </ul>
     *
     * @param channelId id of the channel to close
     *
     * @throws AMQException             if an error occurs closing the channel
     * @throws IllegalArgumentException if the channel id is not valid
     */
    public void closeChannel(int channelId) throws AMQException
    {
        final AMQChannel channel = getChannel(channelId);
        if (channel == null)
        {
            throw new IllegalArgumentException("Unknown channel id");
        }
        else
        {
            try
            {
                channel.close();
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
        // todo QPID-847 - This is called from two lcoations ChannelCloseHandler and ChannelCloseOkHandler.
        // When it is the CC_OK_Handler then it makes sence to remove the channel else we will leak memory.
        // We do it from the Close Handler as we are sending the OK back to the client.
        // While this is AMQP spec compliant. The Java client in the event of an IllegalArgumentException
        // will send a close-ok.. Where we should call removeChannel.
        // However, due to the poor exception handling on the client. The client-user will be notified of the
        // InvalidArgument and if they then decide to close the session/connection then the there will be time
        // for that to occur i.e. a new close method be sent before the exeption handling can mark the session closed.

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
        synchronized (_channelMap)
        {
            _channelMap.remove(channelId);

            if ((channelId & CHANNEL_CACHE_SIZE) == channelId)
            {
                _cachedChannels[channelId] = null;
            }
        }
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
            _network.setMaxReadIdle(BrokerProperties.DEFAULT_HEART_BEAT_TIMEOUT_FACTOR * delay);
        }
    }

    /**
     * Closes all channels that were opened by this protocol session. This frees up all resources used by the channel.
     *
     * @throws AMQException if an error occurs while closing any channel
     */
    private void closeAllChannels() throws AMQException
    {
        for (AMQChannel channel : getChannels())
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
    public void closeSession() throws AMQException
    {
        if(_closing.compareAndSet(false,true))
        {
            // force sync of outstanding async work
            receiveComplete();

            // REMOVE THIS SHOULD NOT BE HERE.
            if (CurrentActor.get() == null)
            {
                CurrentActor.set(_actor);
            }
            if (!_closed)
            {
                if (_virtualHost != null)
                {
                    _virtualHost.getConnectionRegistry().deregisterConnection(this);
                }

                closeAllChannels();

                for (Task task : _taskList)
                {
                    task.doTask(this);
                }

                synchronized(this)
                {
                    _closed = true;
                    notifyAll();
                }
                CurrentActor.get().message(_logSubject, ConnectionMessages.CLOSE());
            }
        }
        else
        {
            synchronized(this)
            {
                while(!_closed)
                {
                    try
                    {
                        wait(1000);
                    }
                    catch (InterruptedException e)
                    {

                    }
                }
            }
        }
    }

    private void closeConnection(int channelId, AMQConnectionException e) throws AMQException
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

    public void closeProtocolSession()
    {
        _network.close();

        try
        {
            _stateManager.changeState(AMQState.CONNECTION_CLOSED);
        }
        catch (AMQException e)
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

    public String dump()
    {
        return this + " last_sent=" + _lastSent + " last_received=" + _lastReceived;
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
        _clientProperties = clientProperties;
        if (_clientProperties != null)
        {
            _clientVersion = _clientProperties.getString(ConnectionStartProperties.VERSION_0_8);

            if (_clientProperties.getString(ConnectionStartProperties.CLIENT_ID_0_8) != null)
            {
                String clientID = _clientProperties.getString(ConnectionStartProperties.CLIENT_ID_0_8);
                setContextKey(new AMQShortString(clientID));

                // Log the Opening of the connection for this client
                _actor.message(ConnectionMessages.OPEN(clientID, _protocolVersion.toString(), _clientVersion, true, true, true));
            }
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

    public boolean isProtocolVersion(byte major, byte minor)
    {
        return (getProtocolMajorVersion() == major) && (getProtocolMinorVersion() == minor);
    }

    public MethodRegistry getRegistry()
    {
        return getMethodRegistry();
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHost virtualHost) throws AMQException
    {
        _virtualHost = virtualHost;

        _virtualHost.getConnectionRegistry().registerConnection(this);

    }

    public void addSessionCloseTask(Task task)
    {
        _taskList.add(task);
    }

    public void removeSessionCloseTask(Task task)
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
        _authorizedSubject = authorizedSubject;
    }

    public Subject getAuthorizedSubject()
    {
        return _authorizedSubject;
    }

    public Principal getAuthorizedPrincipal()
    {
        return _authorizedSubject == null ? null : AuthenticatedPrincipal.getAuthenticatedPrincipalFromSubject(_authorizedSubject);
    }

    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
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
        catch (AMQException e)
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
            _logger.error("IOException caught in" + this + ", session closed implictly: " + throwable);
        }
        else
        {
            _logger.error("Exception caught in" + this + ", closing session explictly: " + throwable, throwable);


            MethodRegistry methodRegistry = MethodRegistry.getMethodRegistry(getProtocolVersion());
            ConnectionCloseBody closeBody = methodRegistry.createConnectionCloseBody(200,new AMQShortString(throwable.getMessage()),0,0);

            writeFrame(closeBody.generateFrame(0));

            _sender.close();
        }
    }

    public void init()
    {
        // Do nothing
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

    public long getLastReceivedTime()
    {
        return _lastReceivedTime;
    }

    public String getClientVersion()
    {
        return _clientVersion;
    }

    public String getPrincipalAsString()
    {
        return getAuthId();
    }

    public long getSessionCountLimit()
    {
        return getMaximumNumberOfChannels();
    }

    public Boolean isIncoming()
    {
        return true;
    }

    public Boolean isSystemConnection()
    {
        return false;
    }

    public Boolean isFederationLink()
    {
        return false;
    }

    public String getAuthId()
    {
        return getAuthorizedPrincipal().getName();
    }

    public Integer getRemotePID()
    {
        return null;
    }

    public String getRemoteProcessName()
    {
        return null;
    }

    public Integer getRemoteParentPID()
    {
        return null;
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

    public long getCreateTime()
    {
        return _createTime;
    }

    public Boolean isShadow()
    {
        return false;
    }

    public void mgmtClose()
    {
        MethodRegistry methodRegistry = getMethodRegistry();
        ConnectionCloseBody responseBody =
                methodRegistry.createConnectionCloseBody(
                        AMQConstant.REPLY_SUCCESS.getCode(),
                        new AMQShortString("The connection was closed using the broker's management interface."),
                        0,0);

        // This seems ugly but because we use closeConnection in both normal
        // broker operation and as part of the management interface it cannot
        // be avoided. The Current Actor will be null when this method is
        // called via the QMF management interface. As such we need to set one.
        boolean removeActor = false;
        if (CurrentActor.get() == null)
        {
            removeActor = true;
            CurrentActor.set(new ManagementActor(_actor.getRootMessageLogger()));
        }

        try
        {
            writeFrame(responseBody.generateFrame(0));

            try
            {

                closeSession();
            }
            catch (AMQException ex)
            {
                throw new RuntimeException(ex);
            }
        }
        finally
        {
            if (removeActor)
            {
                CurrentActor.remove();
            }
        }
    }

    public void mgmtCloseChannel(int channelId)
    {
        MethodRegistry methodRegistry = getMethodRegistry();
        ChannelCloseBody responseBody =
                methodRegistry.createChannelCloseBody(
                        AMQConstant.REPLY_SUCCESS.getCode(),
                        new AMQShortString("The channel was closed using the broker's management interface."),
                        0,0);

        // This seems ugly but because we use AMQChannel.close() in both normal
        // broker operation and as part of the management interface it cannot
        // be avoided. The Current Actor will be null when this method is
        // called via the QMF management interface. As such we need to set one.
        boolean removeActor = false;
        if (CurrentActor.get() == null)
        {
            removeActor = true;
            CurrentActor.set(new ManagementActor(_actor.getRootMessageLogger()));
        }

        try
        {
            writeFrame(responseBody.generateFrame(channelId));

            try
            {
                closeChannel(channelId);
            }
            catch (AMQException ex)
            {
                throw new RuntimeException(ex);
            }
        }
        finally
        {
            if (removeActor)
            {
                CurrentActor.remove();
            }
        }
    }

    public String getClientID()
    {
        return getContextKey().toString();
    }

    public void closeSession(AMQSessionModel session, AMQConstant cause, String message) throws AMQException
    {
        int channelId = ((AMQChannel)session).getChannelId();
        closeChannel(channelId);

        MethodRegistry methodRegistry = getMethodRegistry();
        ChannelCloseBody responseBody =
                methodRegistry.createChannelCloseBody(
                        cause.getCode(),
                        new AMQShortString(message),
                        0,0);

        writeFrame(responseBody.generateFrame(channelId));
    }

    public void close(AMQConstant cause, String message) throws AMQException
    {
        closeConnection(0, new AMQConnectionException(cause, message, 0, 0,
		                getProtocolOutputConverter().getProtocolMajorVersion(),
		                getProtocolOutputConverter().getProtocolMinorVersion(),
		                (Throwable) null));
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

    public List<AMQSessionModel> getSessionModels()
    {
		return new ArrayList<AMQSessionModel>(getChannels());
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

    public void initialiseStatistics()
    {
        _messagesDelivered = new StatisticsCounter("messages-delivered-" + getSessionID());
        _dataDelivered = new StatisticsCounter("data-delivered-" + getSessionID());
        _messagesReceived = new StatisticsCounter("messages-received-" + getSessionID());
        _dataReceived = new StatisticsCounter("data-received-" + getSessionID());
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

    public void setDeferFlush(boolean deferFlush)
    {
        _deferFlush = deferFlush;
    }

    public String getUserName()
    {
        return getAuthorizedPrincipal().getName();
    }

    public final class WriteDeliverMethod
            implements ClientDeliveryMethod
    {
        private final int _channelId;

        public WriteDeliverMethod(int channelId)
        {
            _channelId = channelId;
        }

        public void deliverToClient(final Subscription sub, final QueueEntry entry, final long deliveryTag)
                throws AMQException
        {
            registerMessageDelivered(entry.getMessage().getSize());
            _protocolOutputConverter.writeDeliver(entry, _channelId, deliveryTag, ((SubscriptionImpl)sub).getConsumerTag());
            entry.incrementDeliveryCount();
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
}
