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
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoSessionConfig;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.codec.AMQDecoder;

import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.management.Managable;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.state.AMQStateManager;

import javax.management.JMException;
import javax.security.sasl.SaslServer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CopyOnWriteArrayList;

public class AMQMinaProtocolSession implements AMQProtocolSession,
                                               ProtocolVersionList,
                                               Managable
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolSession.class);

    private static final String CLIENT_PROPERTIES_INSTANCE = ClientProperties.instance.toString();

    // to save boxing the channelId and looking up in a map... cache in an array the low numbered
    // channels.  This value must be of the form 2^x - 1.
    private static final int CHANNEL_CACHE_SIZE = 0xff;

    private final IoSession _minaProtocolSession;

    private AMQShortString _contextKey;

    private VirtualHost _virtualHost;

    private final Map<Integer, AMQChannel> _channelMap = new HashMap<Integer, AMQChannel>();

    private final AMQChannel[] _cachedChannels = new AMQChannel[CHANNEL_CACHE_SIZE+1];

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
    private byte _major = pv[pv.length-1][PROTOCOL_MAJOR];
    private byte _minor = pv[pv.length-1][PROTOCOL_MINOR];
    private FieldTable _clientProperties;
    private final List<Task> _taskList = new CopyOnWriteArrayList<Task>();
    private VersionSpecificRegistry _registry = MainRegistry.getVersionSpecificRegistry(pv[pv.length-1][PROTOCOL_MAJOR],pv[pv.length-1][PROTOCOL_MINOR]);
    


    public ManagedObject getManagedObject()
    {
        return _managedObject;
    }


    public AMQMinaProtocolSession(IoSession session, VirtualHostRegistry virtualHostRegistry,
                                  AMQCodecFactory codecFactory)
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
        //    throw e;

        }




//        this(session, queueRegistry, exchangeRegistry, codecFactory, new AMQStateManager());
    }

     public AMQMinaProtocolSession(IoSession session, VirtualHostRegistry virtualHostRegistry,
                                  AMQCodecFactory codecFactory, AMQStateManager stateManager)
            throws AMQException
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
            throw new AMQException("AMQProtocolSession MBean creation has failed ", ex);
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

    public void dataBlockReceived(AMQDataBlock message)
            throws Exception
    {
        _lastReceived = message;
        if (message instanceof ProtocolInitiation)
        {
            ProtocolInitiation pi = (ProtocolInitiation) message;
            // this ensures the codec never checks for a PI message again
            ((AMQDecoder) _codecFactory.getDecoder()).setExpectProtocolInitiation(false);
            try
            {
                pi.checkVersion(this); // Fails if not correct

                // This sets the protocol version (and hence framing classes) for this session.
                setProtocolVersion(pi.protocolMajor,pi.protocolMinor);

                String mechanisms = ApplicationRegistry.getInstance().getAuthenticationManager().getMechanisms();

                String locales = "en_US";

                // Interfacing with generated code - be aware of possible changes to parameter order as versions change.
                AMQFrame response = ConnectionStartBody.createAMQFrame((short) 0,
            		_major, _minor,	// AMQP version (major, minor)
                    locales.getBytes(),	// locales
                    mechanisms.getBytes(),	// mechanisms
                    null,	// serverProperties
                	(short)_major,	// versionMajor
                    (short)_minor);	// versionMinor
                _minaProtocolSession.write(response);
            }
            catch (AMQException e)
            {
                _logger.error("Received incorrect protocol initiation", e);
                /* Find last protocol version in protocol version list. Make sure last protocol version
                listed in the build file (build-module.xml) is the latest version which will be used
                here. */
                int i = pv.length - 1;
                _minaProtocolSession.write(new ProtocolInitiation(pv[i][PROTOCOL_MAJOR], pv[i][PROTOCOL_MINOR]));
                // TODO: Close connection (but how to wait until message is sent?)
                // ritchiem 2006-12-04 will this not do?
//                WriteFuture future = _minaProtocolSession.write(new ProtocolInitiation(pv[i][PROTOCOL_MAJOR], pv[i][PROTOCOL_MINOR]));
//                future.join();
//                close connection

            }
        }
        else
        {
            AMQFrame frame = (AMQFrame) message;

            if (frame.getBodyFrame() instanceof AMQMethodBody)
            {
                methodFrameReceived(frame);
            }
            else
            {
                contentFrameReceived(frame);
            }
        }
    }

    private void methodFrameReceived(AMQFrame frame)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Method frame received: " + frame);
        }
        final AMQMethodEvent<AMQMethodBody> evt = new AMQMethodEvent<AMQMethodBody>(frame.getChannel(),
                                                                                    (AMQMethodBody) frame.getBodyFrame());
        try
        {
            try
            {
                boolean wasAnyoneInterested = _stateManager.methodReceived(evt);

                if(!_frameListeners.isEmpty())
                {
                    for (AMQMethodListener listener : _frameListeners)
                    {
                        wasAnyoneInterested = listener.methodReceived(evt) ||
                                              wasAnyoneInterested;
                    }
                }
                if (!wasAnyoneInterested)
                {
                    throw new AMQException("AMQMethodEvent " + evt + " was not processed by any listener.");
                }
            }
            catch (AMQChannelException e)
            {
                _logger.error("Closing channel due to: " + e.getMessage());
                writeFrame(e.getCloseFrame(frame.getChannel()));
                closeChannel(frame.getChannel());
            }
            catch (AMQConnectionException e)
            {
                _logger.error("Closing connection due to: " + e.getMessage());
                closeSession();
                writeFrame(e.getCloseFrame(frame.getChannel()));
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

    private void contentFrameReceived(AMQFrame frame) throws AMQException
    {
        if (frame.getBodyFrame() instanceof ContentHeaderBody)
        {
            contentHeaderReceived(frame);
        }
        else if (frame.getBodyFrame() instanceof ContentBody)
        {
            contentBodyReceived(frame);
        }
        else if (frame.getBodyFrame() instanceof HeartbeatBody)
        {
            _logger.debug("Received heartbeat from client");
        }
        else
        {
            _logger.warn("Unrecognised frame " + frame.getClass().getName());
        }
    }

    private void contentHeaderReceived(AMQFrame frame) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Content header frame received: " + frame);
        }
        getChannel(frame.getChannel()).publishContentHeader((ContentHeaderBody) frame.getBodyFrame());
    }

    private void contentBodyReceived(AMQFrame frame) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Content body frame received: " + frame);
        }
        getChannel(frame.getChannel()).publishContentBody((ContentBody)frame.getBodyFrame(), this);
    }

    /**
     * Convenience method that writes a frame to the protocol session. Equivalent
     * to calling getProtocolSession().write().
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

    public AMQChannel getChannel(int channelId) throws AMQException
    {
        return ((channelId & CHANNEL_CACHE_SIZE) == channelId)
                ? _cachedChannels[channelId]
                : _channelMap.get(channelId);
    }

    public void addChannel(AMQChannel channel) throws AMQException
    {
        if (_closed)
        {
            throw new AMQException("Session is closed");    
        }

        final int channelId = channel.getChannelId();
        _channelMap.put(channelId, channel);

        if(((channelId & CHANNEL_CACHE_SIZE) == channelId))
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
        if (channel != null && channel.isTransactional())
        {
            channel.commit();
        }
    }

    public void rollbackTransactions(AMQChannel channel) throws AMQException
    {
        if (channel != null && channel.isTransactional())
        {
            channel.rollback();
        }
    }

    /**
     * Close a specific channel. This will remove any resources used by the channel, including:
     * <ul><li>any queue subscriptions (this may in turn remove queues if they are auto delete</li>
     * </ul>
     *
     * @param channelId id of the channel to close
     * @throws AMQException if an error occurs closing the channel
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
            }
            finally
            {
                removeChannel(channelId);

            }
        }
    }

    /**
     * In our current implementation this is used by the clustering code.
     *
     * @param channelId
     */
    public void removeChannel(int channelId)
    {
        _channelMap.remove(channelId);
        if((channelId & CHANNEL_CACHE_SIZE) == channelId)
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
     * Closes all channels that were opened by this protocol session. This frees up all resources
     * used by the channel.
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
        for(int i = 0; i <= CHANNEL_CACHE_SIZE; i++)
        {
            _cachedChannels[i]=null;
        }
    }

    /**
     * This must be called when the session is _closed in order to free up any resources
     * managed by the session.
     */
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
            for(Task task : _taskList)
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

    /**
     * @return an object that can be used to identity
     */
    public Object getKey()
    {
        return _minaProtocolSession.getRemoteAddress();
    }

    /**
     * Get the fully qualified domain name of the local address to which this session is bound. Since some servers
     * may be bound to multiple addresses this could vary depending on the acceptor this session was created from.
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
        if((_clientProperties != null) && (_clientProperties.getString(CLIENT_PROPERTIES_INSTANCE) != null))
        {
            setContextKey(new AMQShortString(_clientProperties.getString(CLIENT_PROPERTIES_INSTANCE)));
        }
    }

    private void setProtocolVersion(byte major, byte minor)
    {
        _major = major;
        _minor = minor;
        _registry = MainRegistry.getVersionSpecificRegistry(major,minor);
    }

    public byte getProtocolMajorVersion()
    {
        return _major;
    }

    public byte getProtocolMinorVersion()
    {
        return _minor;
    }

    public boolean isProtocolVersion(byte major, byte minor)
    {
        return _major == major && _minor == minor;
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


}
