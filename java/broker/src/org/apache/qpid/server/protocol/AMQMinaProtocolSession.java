/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.protocol;

import org.apache.log4j.Logger;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.qpid.AMQChannelException;
import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.codec.AMQDecoder;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.management.DefaultManagedObject;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;

import javax.security.sasl.SaslServer;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;
import javax.management.openmbean.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class AMQMinaProtocolSession implements AMQProtocolSession, ProtocolVersionList
{
    private static final Logger _logger = Logger.getLogger(AMQProtocolSession.class);

    private final IoSession _minaProtocolSession;

    private String _contextKey;

    private final Map<Integer, AMQChannel> _channelMap = new HashMap<Integer, AMQChannel>();

    private final CopyOnWriteArraySet<AMQMethodListener> _frameListeners = new CopyOnWriteArraySet<AMQMethodListener>();

    private final AMQStateManager _stateManager;

    private final QueueRegistry _queueRegistry;

    private final ExchangeRegistry _exchangeRegistry;

    private AMQCodecFactory _codecFactory;

    private AMQProtocolSessionMBean _managedObject;

    private SaslServer _saslServer;

    private Object _lastReceived;

    private Object _lastSent;

    private boolean _closed;

    private long _maxNoOfChannels;
    
    /* AMQP Version for this session */
    
    private byte _major;
    private byte _minor;

    /**
     * This class implements the management interface (is an MBean). In order to make more attributes, operations
     * and notifications available over JMX simply augment the ManagedConnection interface and add the appropriate
     * implementation here.
     */
    private final class AMQProtocolSessionMBean extends DefaultManagedObject implements ManagedConnection
    {
        /**
         * Represents the channel attributes sent with channel data.
         */
        private String[] _channelAtttibuteNames = { "ChannelId",
                                                    "ChannelName",
                                                    "Transactional",
                                                    "DefaultQueue"};
        private String[] _channelAttributeDescriptions = { "Channel Identifier",
                                                           "Channel Name",
                                                           "is Channel Transactional?",
                                                           "Default Queue Name" };
        private OpenType[] _channelAttributeTypes = { SimpleType.INTEGER,
                                                      SimpleType.OBJECTNAME,
                                                      SimpleType.BOOLEAN,
                                                      SimpleType.STRING };
        /**
         * Channels in the list will be indexed according to channelId.
         */
        private String[] _indexNames = { "ChannelId" };

        /**
         * represents the data type for channel data.
         */
        private CompositeType _channelType = null;
        /**
         * Datatype for list of channelsType.
         */
        private TabularType  _channelsType = null;

        private TabularDataSupport _channelsList = null;

        public AMQProtocolSessionMBean()
        {
            super(ManagedConnection.class, ManagedConnection.TYPE);
            init();
        }

        /**
         * initialises the CompositeTypes and TabularType attributes.
         */
        private void init()
        {
            try
            {
                _channelType = new CompositeType("channel",
                                              "a Channel",
                                              _channelAtttibuteNames,
                                              _channelAttributeDescriptions,
                                              _channelAttributeTypes);

                _channelsType = new TabularType("channelsType",
                                       "List of available channelsType",
                                       _channelType,
                                       _indexNames);
            }
            catch(OpenDataException ex)
            {
                // It should never occur.
                _logger.error("OpenDataTypes could not be created.", ex);
                throw new RuntimeException(ex);
            }
        }

        public Date getLastIoTime()
        {
            return new Date(_minaProtocolSession.getLastIoTime());
        }

        public String getRemoteAddress()
        {
            return _minaProtocolSession.getRemoteAddress().toString();
        }

        public long getWrittenBytes()
        {
            return _minaProtocolSession.getWrittenBytes();
        }

        public long getReadBytes()
        {
            return _minaProtocolSession.getReadBytes();
        }

        public long getMaximumNumberOfAllowedChannels()
        {
            return _maxNoOfChannels;
        }

        public void setMaximumNumberOfAllowedChannels(long value)
        {
            _maxNoOfChannels = value;
        }

        public String getObjectInstanceName()
        {
            String remote = getRemoteAddress();
            return "anonymous".equals(remote) ? remote + hashCode() : remote;
        }

        /**
         * Creates the list of channels in tabular form from the _channelMap.
         * @return  list of channels in tabular form.
         * @throws OpenDataException
         */
        private TabularData getChannels()
            throws OpenDataException
        {
            _channelsList = new TabularDataSupport(_channelsType);

            for (Map.Entry<Integer, AMQChannel> entry : _channelMap.entrySet())
            {
                AMQChannel channel = entry.getValue();
                ObjectName channelObjectName = null;

                try
                {
                    channelObjectName = channel.getObjectName();
                }
                catch (MalformedObjectNameException ex)
                {
                    _logger.error("Unable to create object name: ", ex);
                }

                Object[] itemValues = {channel.getChannelId(),
                                       channelObjectName,
                                       channel.isTransactional(),
                                       (channel.getDefaultQueue() != null) ? channel.getDefaultQueue().getName() : null};

                CompositeData channelData = new CompositeDataSupport(_channelType,
                                                            _channelAtttibuteNames,
                                                            itemValues);

                _channelsList.put(channelData);
            }

            return _channelsList;
        }

        public TabularData viewChannels()
            throws OpenDataException
        {
            return getChannels();
        }

        public void closeChannel(int id)
            throws Exception
        {
            try
            {
                AMQMinaProtocolSession.this.closeChannel(id);
            }
            catch (AMQException ex)
            {
                throw new Exception(ex.toString());
            }
        }

        public void closeConnection()
            throws Exception
        {
            try
            {
                AMQMinaProtocolSession.this.closeSession();
            }
            catch (AMQException ex)
            {
                throw new Exception(ex.toString());
            }
        }

    }

    public AMQMinaProtocolSession(IoSession session, QueueRegistry queueRegistry, ExchangeRegistry exchangeRegistry,
                                  AMQCodecFactory codecFactory)
            throws AMQException
    {
        this(session, queueRegistry, exchangeRegistry, codecFactory, new AMQStateManager());
    }

    public AMQMinaProtocolSession(IoSession session, QueueRegistry queueRegistry, ExchangeRegistry exchangeRegistry,
                                  AMQCodecFactory codecFactory, AMQStateManager stateManager)
            throws AMQException
    {
        _stateManager = stateManager;
        _minaProtocolSession = session;
        session.setAttachment(this);
        _frameListeners.add(_stateManager);
        _queueRegistry = queueRegistry;
        _exchangeRegistry = exchangeRegistry;
        _codecFactory = codecFactory;
        _managedObject = new AMQProtocolSessionMBean();
        _managedObject.register();
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
            ((AMQDecoder)_codecFactory.getDecoder()).setExpectProtocolInitiation(false);
            try {
                pi.checkVersion(this); // Fails if not correct
                // This sets the protocol version (and hence framing classes) for this session.
                _major = pi.protocolMajor;
                _minor = pi.protocolMinor;
                String mechanisms = ApplicationRegistry.getInstance().getAuthenticationManager().getMechanisms();
                String locales = "en_US";
                AMQFrame response = ConnectionStartBody.createAMQFrame((short)0, pi.protocolMajor, pi.protocolMinor, null,
                                                                       mechanisms.getBytes(), locales.getBytes());
                _minaProtocolSession.write(response);
            } catch (AMQException e) {
                _logger.error("Received incorrect protocol initiation", e);
                /* Find last protocol version in protocol version list. Make sure last protocol version
                listed in the build file (build-module.xml) is the latest version which will be used
                here. */
                int i = pv.length - 1;
                _minaProtocolSession.write(new ProtocolInitiation(pv[i][PROTOCOL_MAJOR], pv[i][PROTOCOL_MINOR]));
                // TODO: Close connection (but how to wait until message is sent?)
            }
        }
        else
        {
            AMQFrame frame = (AMQFrame) message;

            if (frame.bodyFrame instanceof AMQMethodBody)
            {
                methodFrameReceived(frame);
            }
            else
            {
                try
                {
                    contentFrameReceived(frame);
                }
                catch (RequiredDeliveryException e)
                {
                    //need to return the message:
                    _logger.info("Returning message to " + this + " channel " + frame.channel
                                 + ": " + e.getMessage());
                    writeFrame(e.getReturnMessage(frame.channel));
                }
            }
        }
    }

    private void methodFrameReceived(AMQFrame frame)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Method frame received: " + frame);
        }
        final AMQMethodEvent<AMQMethodBody> evt = new AMQMethodEvent<AMQMethodBody>(frame.channel,
                                                                                    (AMQMethodBody)frame.bodyFrame);
        try
        {
            boolean wasAnyoneInterested = false;
            for (AMQMethodListener listener : _frameListeners)
            {
                wasAnyoneInterested = listener.methodReceived(evt, this, _queueRegistry, _exchangeRegistry) ||
                                      wasAnyoneInterested;
            }
            if (!wasAnyoneInterested)
            {
                throw new AMQException("AMQMethodEvent " + evt + " was not processed by any listener.");
            }
        }
        catch (AMQChannelException e)
        {
            _logger.error("Closing channel due to: " + e.getMessage());
            writeFrame(e.getCloseFrame(frame.channel));
        }
        catch (AMQException e)
        {
            for (AMQMethodListener listener : _frameListeners)
            {
                listener.error(e);
            }
            _minaProtocolSession.close();
        }
    }

    private void contentFrameReceived(AMQFrame frame) throws AMQException
    {
        if (frame.bodyFrame instanceof ContentHeaderBody)
        {
            contentHeaderReceived(frame);
        }
        else if (frame.bodyFrame instanceof ContentBody)
        {
            contentBodyReceived(frame);
        }
        else if (frame.bodyFrame instanceof HeartbeatBody)
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
        getChannel(frame.channel).publishContentHeader((ContentHeaderBody)frame.bodyFrame);
    }

    private void contentBodyReceived(AMQFrame frame) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Content body frame received: " + frame);
        }
        getChannel(frame.channel).publishContentBody((ContentBody)frame.bodyFrame);
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

    public String getContextKey()
    {
        return _contextKey;
    }

    public void setContextKey(String contextKey)
    {
        _contextKey = contextKey;
    }

    public AMQChannel getChannel(int channelId) throws AMQException
    {
        return _channelMap.get(channelId);
    }

    public void addChannel(AMQChannel channel)
    {
        _channelMap.put(channel.getChannelId(), channel);
    }

    /**
     * Close a specific channel. This will remove any resources used by the channel, including:
     * <ul><li>any queue subscriptions (this may in turn remove queues if they are auto delete</li>
     * </ul>
     * @param channelId id of the channel to close
     * @throws AMQException if an error occurs closing the channel
     * @throws IllegalArgumentException if the channel id is not valid
     */
    public void closeChannel(int channelId) throws AMQException
    {
        final AMQChannel channel = _channelMap.get(channelId);
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
                _channelMap.remove(channelId);
            }
        }
    }

    /**
     * In our current implementation this is used by the clustering code.
     * @param channelId
     */
    public void removeChannel(int channelId)
    {
        _channelMap.remove(channelId);
    }

    /**
     * Initialise heartbeats on the session.
     * @param delay delay in seconds (not ms)
     */
    public void initHeartbeats(int delay)
    {
        if(delay > 0)
        {
            _minaProtocolSession.setIdleTime(IdleStatus.WRITER_IDLE, delay);
            _minaProtocolSession.setIdleTime(IdleStatus.READER_IDLE, HeartbeatConfig.getInstance().getTimeout(delay));
        }
    }

    /**
     * Closes all channels that were opened by this protocol session. This frees up all resources
     * used by the channel.
     * @throws AMQException if an error occurs while closing any channel
     */
    private void closeAllChannels() throws AMQException
    {
        for (AMQChannel channel : _channelMap.values())
        {
            channel.close(this);
        }
    }

    /**
     * This must be called when the session is _closed in order to free up any resources
     * managed by the session.
     */
    public void closeSession() throws AMQException
    {
        if(!_closed)
        {
            _closed = true;
            closeAllChannels();
            if (_managedObject != null)
            {
                _managedObject.unregister();
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
            return ((InetSocketAddress)address).getHostName();
        }
        else if (address instanceof VmPipeAddress)
        {
            return "vmpipe:" + ((VmPipeAddress)address).getPort();
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
    
    /**
     * Convenience methods for managing AMQP version.
     * NOTE: Both major and minor will be set to 0 prior to protocol initiation.
     */
    
    public byte getAmqpMajor()
    {
        return _major;
    }
    
    public byte getAmqpMinor()
    {
        return _minor;
    }
    
    public boolean amqpVersionEquals(byte major, byte minor)
    {
        return _major == major && _minor == minor;
    }
}
