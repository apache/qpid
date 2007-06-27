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

import org.apache.log4j.Logger;

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.vmpipe.VmPipeAddress;

import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.codec.AMQDecoder;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.framing.*;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.output.ProtocolOutputConverterRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.state.AMQState;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import javax.management.JMException;
import javax.security.sasl.SaslServer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class AMQMinaProtocolSession implements AMQProtocolSession, Managable
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolSession.class);

    private static final String CLIENT_PROPERTIES_INSTANCE = ClientProperties.instance.toString();

    // to save boxing the channelId and looking up in a map... cache in an array the low numbered
    // channels.  This value must be of the form 2^x - 1.
    private static final int CHANNEL_CACHE_SIZE = 0xff;

    private final IoSession _minaProtocolSession;

    private AMQShortString _contextKey;

    private AMQShortString _clientVersion = null;

    private VirtualHost _virtualHost;

    private final Map<Integer, AMQChannel> _channelMap = new HashMap<Integer, AMQChannel>();

    private final AMQChannel[] _cachedChannels = new AMQChannel[CHANNEL_CACHE_SIZE + 1];

    private final CopyOnWriteArraySet<AMQMethodListener> _frameListeners = new CopyOnWriteArraySet<AMQMethodListener>();

    private final AMQStateManager _stateManager;

    private AMQCodecFactory _codecFactory;

    private AMQProtocolSessionMBean _managedObject;

    private SaslServer _saslServer;

    private Object _lastReceived;

    private Object _lastSent;

    private boolean _closed;
    // maximum number of channels this session should have
    private long _maxNoOfChannels = 1000;

    /* AMQP Version for this session */
    private ProtocolVersion _protocolVersion = ProtocolVersion.getLatestSupportedVersion();

    private FieldTable _clientProperties;
    private final List<Task> _taskList = new CopyOnWriteArrayList<Task>();
    private VersionSpecificRegistry _registry = MainRegistry.getVersionSpecificRegistry(_protocolVersion);
    private List<Integer> _closingChannelsList = new ArrayList<Integer>();
    private ProtocolOutputConverter _protocolOutputConverter;
    private Principal _authorizedID;

    public ManagedObject getManagedObject()
    {
        return _managedObject;
    }

    public AMQMinaProtocolSession(IoSession session, VirtualHostRegistry virtualHostRegistry, AMQCodecFactory codecFactory)
        throws AMQException
    {
        _stateManager = new AMQStateManager(virtualHostRegistry, this);
        _minaProtocolSession = session;
        session.setAttachment(this);

        _codecFactory = codecFactory;

        try
        {
            IoServiceConfig config = session.getServiceConfig();
            ReadWriteThreadModel threadModel = (ReadWriteThreadModel) config.getThreadModel();
            threadModel.getAsynchronousReadFilter().createNewJobForSession(session);
            threadModel.getAsynchronousWriteFilter().createNewJobForSession(session);
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
            // throw e;

        }

        // this(session, queueRegistry, exchangeRegistry, codecFactory, new AMQStateManager());
    }

    public AMQMinaProtocolSession(IoSession session, VirtualHostRegistry virtualHostRegistry, AMQCodecFactory codecFactory,
        AMQStateManager stateManager) throws AMQException
    {
        _stateManager = stateManager;
        _minaProtocolSession = session;
        session.setAttachment(this);

        _codecFactory = codecFactory;

    }

    private AMQProtocolSessionMBean createMBean() throws AMQException
    {
        try
        {
            return new AMQProtocolSessionMBean(this);
        }
        catch (JMException ex)
        {
            _logger.error("AMQProtocolSession MBean creation has failed ", ex);
            throw new AMQException(null, "AMQProtocolSession MBean creation has failed ", ex);
        }
    }

    public IoSession getIOSession()
    {
        return _minaProtocolSession;
    }

    public static AMQProtocolSession getAMQProtocolSession(IoSession minaProtocolSession)
    {
        return (AMQProtocolSession) minaProtocolSession.getAttachment();
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
            throw new UnknnownMessageTypeException(message, null);
        }
    }

    private void frameReceived(AMQFrame frame) throws AMQException
    {
        int channelId = frame.getChannel();
        AMQBody body = frame.getBodyFrame();

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Frame Received: " + frame);
        }

        if (body instanceof AMQMethodBody)
        {
            methodFrameReceived(channelId, (AMQMethodBody) body);
        }
        else if (body instanceof ContentHeaderBody)
        {
            contentHeaderReceived(channelId, (ContentHeaderBody) body);
        }
        else if (body instanceof ContentBody)
        {
            contentBodyReceived(channelId, (ContentBody) body);
        }
        else if (body instanceof HeartbeatBody)
        {
            // NO OP
        }
        else
        {
            _logger.warn("Unrecognised frame " + frame.getClass().getName());
        }
    }

    private void protocolInitiationReceived(ProtocolInitiation pi)
    {
        // this ensures the codec never checks for a PI message again
        ((AMQDecoder) _codecFactory.getDecoder()).setExpectProtocolInitiation(false);
        try
        {
            pi.checkVersion(); // Fails if not correct

            // This sets the protocol version (and hence framing classes) for this session.
            setProtocolVersion(pi._protocolMajor, pi._protocolMinor);

            String mechanisms = ApplicationRegistry.getInstance().getAuthenticationManager().getMechanisms();

            String locales = "en_US";

            // Interfacing with generated code - be aware of possible changes to parameter order as versions change.
            AMQFrame response =
                ConnectionStartBody.createAMQFrame((short) 0, getProtocolMajorVersion(), getProtocolMinorVersion(), // AMQP version (major, minor)
                    locales.getBytes(), // locales
                    mechanisms.getBytes(), // mechanisms
                    null, // serverProperties
                    (short) getProtocolMajorVersion(), // versionMajor
                    (short) getProtocolMinorVersion()); // versionMinor
            _minaProtocolSession.write(response);
        }
        catch (AMQException e)
        {
            _logger.error("Received incorrect protocol initiation", e);

            _minaProtocolSession.write(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion()));

            // TODO: Close connection (but how to wait until message is sent?)
            // ritchiem 2006-12-04 will this not do?
            // WriteFuture future = _minaProtocolSession.write(new ProtocolInitiation(pv[i][PROTOCOLgetProtocolMajorVersion()], pv[i][PROTOCOLgetProtocolMinorVersion()]));
            // future.join();
            // close connection

        }
    }

    private void methodFrameReceived(int channelId, AMQMethodBody methodBody)
    {

        final AMQMethodEvent<AMQMethodBody> evt = new AMQMethodEvent<AMQMethodBody>(channelId, methodBody);

        // Check that this channel is not closing
        if (channelAwaitingClosure(channelId))
        {
            if ((evt.getMethod() instanceof ChannelCloseOkBody))
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Channel[" + channelId + "] awaiting closure - processing close-ok");
                }
            }
            else
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Channel[" + channelId + "] awaiting closure ignoring");
                }

                return;
            }
        }

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
                    throw new AMQNoMethodHandlerException(evt, null);
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

                    closeSession();

                    AMQConnectionException ce =
                        evt.getMethod().getConnectionException(AMQConstant.CHANNEL_ERROR,
                            AMQConstant.CHANNEL_ERROR.getName().toString());

                    _stateManager.changeState(AMQState.CONNECTION_CLOSING);
                    writeFrame(ce.getCloseFrame(channelId));
                }
            }
            catch (AMQConnectionException e)
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Closing connection due to: " + e.getMessage());
                }

                closeSession();
                _stateManager.changeState(AMQState.CONNECTION_CLOSING);
                writeFrame(e.getCloseFrame(channelId));
            }
        }
        catch (Exception e)
        {
            _stateManager.error(e);
            for (AMQMethodListener listener : _frameListeners)
            {
                listener.error(e);
            }

            _minaProtocolSession.close();
        }
    }

    private void contentHeaderReceived(int channelId, ContentHeaderBody body) throws AMQException
    {

        AMQChannel channel = getAndAssertChannel(channelId);

        channel.publishContentHeader(body, this);

    }

    private void contentBodyReceived(int channelId, ContentBody body) throws AMQException
    {
        AMQChannel channel = getAndAssertChannel(channelId);

        channel.publishContentBody(body, this);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent to calling
     * getProtocolSession().write().
     *
     * @param frame the frame to write
     */
    public void writeFrame(AMQDataBlock frame)
    {
        _lastSent = frame;
        _minaProtocolSession.write(frame);
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
        return new ArrayList<AMQChannel>(_channelMap.values());
    }

    public AMQChannel getAndAssertChannel(int channelId) throws AMQException
    {
        AMQChannel channel = getChannel(channelId);
        if (channel == null)
        {
            throw new AMQException(AMQConstant.NOT_FOUND, "Channel not found with id:" + channelId, null);
        }

        return channel;
    }

    public AMQChannel getChannel(int channelId) throws AMQException
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
        return _closingChannelsList.contains(channelId);
    }

    public void addChannel(AMQChannel channel) throws AMQException
    {
        if (_closed)
        {
            throw new AMQException(null, "Session is closed", null);
        }

        final int channelId = channel.getChannelId();

        if (_closingChannelsList.contains(channelId))
        {
            throw new AMQException(null, "Session is marked awaiting channel close", null);
        }

        if (_channelMap.size() == _maxNoOfChannels)
        {
            String errorMessage =
                toString() + ": maximum number of channels has been reached (" + _maxNoOfChannels
                + "); can't create channel";
            _logger.error(errorMessage);
            throw new AMQException(AMQConstant.NOT_ALLOWED, errorMessage, null);
        }
        else
        {
            _channelMap.put(channel.getChannelId(), channel);
        }

        if (((channelId & CHANNEL_CACHE_SIZE) == channelId))
        {
            _cachedChannels[channelId] = channel;
        }

        checkForNotification();
    }

    private void checkForNotification()
    {
        int channelsCount = _channelMap.size();
        if (channelsCount >= _maxNoOfChannels)
        {
            _managedObject.notifyClients("Channel count (" + channelsCount + ") has reached the threshold value");
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
                channel.close(this);
                markChannelawaitingCloseOk(channelId);
            }
            finally
            {
                removeChannel(channelId);
            }
        }
    }

    public void closeChannelOk(int channelId)
    {
        _closingChannelsList.remove(new Integer(channelId));
    }

    private void markChannelawaitingCloseOk(int channelId)
    {
        _closingChannelsList.add(channelId);
    }

    /**
     * In our current implementation this is used by the clustering code.
     *
     * @param channelId The channel to remove
     */
    public void removeChannel(int channelId)
    {
        _channelMap.remove(channelId);
        if ((channelId & CHANNEL_CACHE_SIZE) == channelId)
        {
            _cachedChannels[channelId] = null;
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
            _minaProtocolSession.setIdleTime(IdleStatus.WRITER_IDLE, delay);
            _minaProtocolSession.setIdleTime(IdleStatus.READER_IDLE, HeartbeatConfig.getInstance().getTimeout(delay));
        }
    }

    /**
     * Closes all channels that were opened by this protocol session. This frees up all resources used by the channel.
     *
     * @throws AMQException if an error occurs while closing any channel
     */
    private void closeAllChannels() throws AMQException
    {
        for (AMQChannel channel : _channelMap.values())
        {
            channel.close(this);
        }

        _channelMap.clear();
        for (int i = 0; i <= CHANNEL_CACHE_SIZE; i++)
        {
            _cachedChannels[i] = null;
        }
    }

    /** This must be called when the session is _closed in order to free up any resources managed by the session. */
    public void closeSession() throws AMQException
    {
        if (!_closed)
        {
            _closed = true;
            closeAllChannels();
            if (_managedObject != null)
            {
                _managedObject.unregister();
            }

            for (Task task : _taskList)
            {
                task.doTask(this);
            }
        }
    }

    public String toString()
    {
        return "AMQProtocolSession(" + _minaProtocolSession.getRemoteAddress() + ")";
    }

    public String dump()
    {
        return this + " last_sent=" + _lastSent + " last_received=" + _lastReceived;
    }

    /** @return an object that can be used to identity */
    public Object getKey()
    {
        return _minaProtocolSession.getRemoteAddress();
    }

    /**
     * Get the fully qualified domain name of the local address to which this session is bound. Since some servers may
     * be bound to multiple addresses this could vary depending on the acceptor this session was created from.
     *
     * @return a String FQDN
     */
    public String getLocalFQDN()
    {
        SocketAddress address = _minaProtocolSession.getLocalAddress();
        // we use the vmpipe address in some tests hence the need for this rather ugly test. The host
        // information is used by SASL primary.
        if (address instanceof InetSocketAddress)
        {
            return ((InetSocketAddress) address).getHostName();
        }
        else if (address instanceof VmPipeAddress)
        {
            return "vmpipe:" + ((VmPipeAddress) address).getPort();
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

    public FieldTable getClientProperties()
    {
        return _clientProperties;
    }

    public void setClientProperties(FieldTable clientProperties)
    {
        _clientProperties = clientProperties;
        if (_clientProperties != null)
        {
            if (_clientProperties.getString(CLIENT_PROPERTIES_INSTANCE) != null)
            {
                setContextKey(new AMQShortString(_clientProperties.getString(CLIENT_PROPERTIES_INSTANCE)));
            }

            if (_clientProperties.getString(ClientProperties.version.toString()) != null)
            {
                _clientVersion = new AMQShortString(_clientProperties.getString(ClientProperties.version.toString()));
            }
        }
    }

    private void setProtocolVersion(byte major, byte minor)
    {
        _protocolVersion = new ProtocolVersion(major, minor);

        _registry = MainRegistry.getVersionSpecificRegistry(_protocolVersion);

        _protocolOutputConverter = ProtocolOutputConverterRegistry.getConverter(this);
    }

    public byte getProtocolMajorVersion()
    {
        return _protocolVersion.getMajorVersion();
    }

    public byte getProtocolMinorVersion()
    {
        return _protocolVersion.getMinorVersion();
    }

    public boolean isProtocolVersion(byte major, byte minor)
    {
        return (getProtocolMajorVersion() == major) && (getProtocolMinorVersion() == minor);
    }

    public VersionSpecificRegistry getRegistry()
    {
        return _registry;
    }

    public Object getClientIdentifier()
    {
        return _minaProtocolSession.getRemoteAddress();
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public void setVirtualHost(VirtualHost virtualHost) throws AMQException
    {
        _virtualHost = virtualHost;
        _managedObject = createMBean();
        _managedObject.register();
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

    public void setAuthorizedID(Principal authorizedID)
    {
        _authorizedID = authorizedID;
    }

    public Principal getAuthorizedID()
    {
        return _authorizedID;
    }

    public String getClientVersion()
    {
        return (_clientVersion == null) ? null : _clientVersion.toString();
    }
}
