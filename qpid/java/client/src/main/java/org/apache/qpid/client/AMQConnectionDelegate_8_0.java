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
package org.apache.qpid.client;

import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.XASession;

import org.apache.qpid.transport.Receiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.StateWaiter;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.framing.ConfirmSelectBody;
import org.apache.qpid.framing.ConfirmSelectOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.framing.TxSelectBody;
import org.apache.qpid.framing.TxSelectOkBody;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ChannelLimitReachedException;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.jms.Session;
import org.apache.qpid.properties.ConnectionStartProperties;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.OutgoingNetworkTransport;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.network.security.SecurityLayer;
import org.apache.qpid.transport.network.security.SecurityLayerFactory;

public class AMQConnectionDelegate_8_0 implements AMQConnectionDelegate
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQConnectionDelegate_8_0.class);
    private final AMQConnection _conn;
    private final long _timeout = Long.getLong(ClientProperties.QPID_SYNC_OP_TIMEOUT,
                                               Long.getLong(ClientProperties.AMQJ_DEFAULT_SYNCWRITE_TIMEOUT,
                                                            ClientProperties.DEFAULT_SYNC_OPERATION_TIMEOUT));
    private boolean _messageCompressionSupported;
    private boolean _addrSyntaxSupported;
    private boolean _confirmedPublishSupported;
    private boolean _confirmedPublishNonTransactionalSupported;

    public void closeConnection(long timeout) throws JMSException, AMQException
    {
        _conn.getProtocolHandler().closeConnection(timeout);
    }

    public AMQConnectionDelegate_8_0(AMQConnection conn)
    {
        _conn = conn;
        _addrSyntaxSupported =
                Boolean.parseBoolean(System.getProperty(ClientProperties.ADDR_SYNTAX_SUPPORTED_IN_0_8,
                                                        String.valueOf(ClientProperties.DEFAULT_ADDR_SYNTAX_0_8_SUPPORT)));
    }

    protected boolean checkException(Throwable thrown)
    {
        Throwable cause = thrown.getCause();

        if (cause == null)
        {
            cause = thrown;
        }

        return ((cause instanceof ConnectException) || (cause instanceof UnresolvedAddressException));
    }

    public boolean isConfirmedPublishSupported()
    {
        return _confirmedPublishSupported;
    }

    public ProtocolVersion makeBrokerConnection(BrokerDetails brokerDetail) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Connecting to broker:" + brokerDetail);
        }
        final Set<AMQState> openOrClosedStates =
                EnumSet.of(AMQState.CONNECTION_OPEN, AMQState.CONNECTION_CLOSED);

        ConnectionSettings settings = brokerDetail.buildConnectionSettings();
        settings.setProtocol(brokerDetail.getTransport());

        //Check connection-level ssl override setting
        String connectionSslOption = _conn.getConnectionURL().getOption(ConnectionURL.OPTIONS_SSL);
        if(connectionSslOption != null)
        {
            boolean connUseSsl = Boolean.parseBoolean(connectionSslOption);
            boolean brokerlistUseSsl = settings.isUseSSL();

            if( connUseSsl != brokerlistUseSsl)
            {
                settings.setUseSSL(connUseSsl);

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Applied connection ssl option override, setting UseSsl to: " + connUseSsl );
                }
            }
        }

        SecurityLayer securityLayer = SecurityLayerFactory.newInstance(settings);

        OutgoingNetworkTransport transport = Transport.getOutgoingTransportInstance(getProtocolVersion());

        ReceiverClosedWaiter monitoringReceiver = new ReceiverClosedWaiter(securityLayer.receiver(_conn.getProtocolHandler()));

        NetworkConnection network = transport.connect(settings, monitoringReceiver,
                                                      _conn.getProtocolHandler());

        try
        {
            _conn.getProtocolHandler().setNetworkConnection(network, securityLayer.sender(network.getSender()));

            StateWaiter waiter = _conn.getProtocolHandler().createWaiter(openOrClosedStates);
            _conn.getProtocolHandler().init(settings);

            // this blocks until the connection has been set up or when an error
            // has prevented the connection being set up

            AMQState state = waiter.await();

            if (state == AMQState.CONNECTION_OPEN)
            {
                _conn.getFailoverPolicy().attainedConnection();
                _conn.setConnected(true);
                _conn.logConnected(network.getLocalAddress(), network.getRemoteAddress());
                _messageCompressionSupported = checkMessageCompressionSupported();
                _confirmedPublishSupported = checkConfirmedPublishSupported();
                _confirmedPublishNonTransactionalSupported = checkConfirmedPublishNonTransactionalSupported();
                return null;
            }
            else
            {
                return _conn.getProtocolHandler().getSuggestedProtocolVersion();
            }
        }
        catch(AMQException | RuntimeException e)
        {
            network.close();
            throw e;
        }
        finally
        {
            // await the receiver to finish its execution (and so the IO threads too)
            if (!_conn.isConnected())
            {
                boolean closedWithinTimeout = monitoringReceiver.awaitClose(_timeout);
                if (!closedWithinTimeout)
                {
                    _logger.warn("Timed-out waiting for receiver for connection to "
                                 + brokerDetail + " to be closed.");
                }
            }
        }

    }

    // RabbitMQ supports confirmed publishing, but only on non transactional sessions
    private boolean checkConfirmedPublishNonTransactionalSupported()
    {
        FieldTable serverProperties = _conn.getProtocolHandler().getProtocolSession().getConnectionStartServerProperties();
        if( serverProperties != null
            && serverProperties.containsKey("capabilities")
            && serverProperties.get("capabilities") instanceof FieldTable)
        {
            FieldTable capabilities = serverProperties.getFieldTable("capabilities");
            if(capabilities.containsKey("publisher_confirms")
               && capabilities.get("publisher_confirms") instanceof Boolean
               && capabilities.getBoolean("publisher_confirms"))
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return false;
        }
    }

    public org.apache.qpid.jms.Session createSession(final boolean transacted, final int acknowledgeMode, final int prefetch)
            throws JMSException
    {
        return createSession(transacted, acknowledgeMode, prefetch, prefetch);
    }


    public XASession createXASession(int prefetchHigh, int prefetchLow) throws JMSException
    {
        throw new UnsupportedOperationException("0_8 version does not provide XA support");
    }

    public XASession createXASession(int ackMode) throws JMSException
    {
        throw new UnsupportedOperationException("0_8 version does not provide XA support");
    }
    public org.apache.qpid.jms.Session createSession(final boolean transacted, final int acknowledgeMode,
                                                     final int prefetchHigh, final int prefetchLow) throws JMSException
    {
        _conn.checkNotClosed();

        if (_conn.channelLimitReached())
        {
            throw new ChannelLimitReachedException(_conn.getMaximumChannelCount());
        }

        return new FailoverRetrySupport<Session, JMSException>(
                new FailoverProtectedOperation<Session, JMSException>()
                {
                    public Session execute() throws JMSException, FailoverException
                    {
                        int channelId = _conn.getNextChannelID();

                        if (_logger.isDebugEnabled())
                        {
                            _logger.debug("Write channel open frame for channel id " + channelId);
                        }

                        // We must create the session and register it before actually sending the frame to the server to
                        // open it, so that there is no window where we could receive data on the channel and not be set
                        // up to handle it appropriately.
                        AMQSession_0_8 session =
                                new AMQSession_0_8(_conn, channelId, transacted, acknowledgeMode, prefetchHigh,
                                               prefetchLow);
                        _conn.registerSession(channelId, session);

                        boolean success = false;
                        try
                        {
                            createChannelOverWire(channelId, transacted);
                            session.setPrefetchLimits(prefetchHigh, 0);
                            success = true;
                        }
                        catch (AMQException e)
                        {
                            JMSException jmse = new JMSException("Error creating session: " + e);
                            jmse.setLinkedException(e);
                            jmse.initCause(e);
                            throw jmse;
                        }
                        finally
                        {
                            if (!success)
                            {
                                _conn.deregisterSession(channelId);
                            }
                        }

                        if (_conn.started())
                        {
                            try
                            {
                                session.start();
                            }
                            catch (AMQException e)
                            {
                                throw new JMSAMQException(e);
                            }
                        }

                        return session;
                    }
                }, _conn).execute();
    }

    /**
     * Create an XASession with default prefetch values of:
     * High = MaxPrefetch
     * Low  = MaxPrefetch / 2
     * @return XASession
     * @throws JMSException thrown if there is a problem creating the session.
     */
    public XASession createXASession() throws JMSException
    {
        return createXASession((int) _conn.getMaxPrefetch(), (int) _conn.getMaxPrefetch() / 2);
    }

    private void createChannelOverWire(int channelId, boolean transacted)
            throws AMQException, FailoverException
    {
        ChannelOpenBody channelOpenBody = _conn.getProtocolHandler().getMethodRegistry().createChannelOpenBody(null);
        _conn.getProtocolHandler().syncWrite(channelOpenBody.generateFrame(channelId),  ChannelOpenOkBody.class);

        if (transacted)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Issuing TxSelect for " + channelId);
            }
            TxSelectBody body = _conn.getProtocolHandler().getMethodRegistry().createTxSelectBody();


            _conn.getProtocolHandler().syncWrite(body.generateFrame(channelId), TxSelectOkBody.class);
        }
        boolean useConfirms = (_confirmedPublishSupported || (!transacted && _confirmedPublishNonTransactionalSupported))
                              && "all".equals(_conn.getSyncPublish());
        if(useConfirms)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Issuing ConfirmSelect for " + channelId);
            }
            ConfirmSelectBody body = new ConfirmSelectBody(false);

            _conn.getProtocolHandler().syncWrite(body.generateFrame(channelId), ConfirmSelectOkBody.class);
        }
    }

    /**
     * For all sessions, and for all consumers in those sessions, resubscribe. This is called during failover handling.
     * The caller must hold the failover mutex before calling this method.
     */
    public void resubscribeSessions() throws JMSException, AMQException, FailoverException
    {
        ArrayList sessions = new ArrayList(_conn.getSessions().values());
        _logger.info(MessageFormat.format("Resubscribing sessions = {0} sessions.size={1}", sessions, sessions.size())); // FIXME: removeKey?
        for (Iterator it = sessions.iterator(); it.hasNext();)
        {
            AMQSession_0_8 s = (AMQSession_0_8) it.next();

            // reset the flow control flag
            // on opening channel, broker sends flow blocked if virtual host is blocked
            // if virtual host is not blocked, then broker does not send flow command
            // that's why we need to reset the flow control flag
            s.setFlowControl(true);
            reopenChannel(s.getChannelId(), s.getDefaultPrefetchHigh(), s.getDefaultPrefetchLow(), s.isTransacted());
            s.setPrefetchLimits(s.getDefaultPrefetchHigh(), 0);
            s.resubscribe();
        }
    }

    private void reopenChannel(int channelId, int prefetchHigh, int prefetchLow, boolean transacted)
    throws AMQException, FailoverException
    {
        try
        {
            createChannelOverWire(channelId, transacted);
        }
        catch (AMQException e)
        {
            _conn.deregisterSession(channelId);
            throw new AMQException(null, "Error reopening channel " + channelId + " after failover: " + e, e);
        }
    }

    public <T, E extends Exception> T executeRetrySupport(FailoverProtectedOperation<T,E> operation) throws E
    {
        while (true)
        {
            try
            {
                _conn.blockUntilNotFailingOver();
            }
            catch (InterruptedException e)
            {
                _logger.debug("Interrupted: " + e, e);

                return null;
            }

            synchronized (_conn.getFailoverMutex())
            {
                try
                {
                    return operation.execute();
                }
                catch (FailoverException e)
                {
                    _logger.debug("Failover exception caught during operation: " + e, e);
                }
                catch (IllegalStateException e)
                {
                    if (!(e.getMessage().startsWith("Fail-over interrupted no-op failover support")))
                    {
                        throw e;
                    }
                }
            }
        }
    }

    public int getMaxChannelID()
    {
        ConnectionTuneParameters params = _conn.getProtocolHandler().getProtocolSession().getConnectionTuneParameters();

        return params == null ? AMQProtocolSession.MAX_CHANNEL_MAX : params.getChannelMax();
    }

    public int getMinChannelID()
    {
        return AMQProtocolSession.MIN_USABLE_CHANNEL_NUM;
    }

    public ProtocolVersion getProtocolVersion()
    {
        return ProtocolVersion.v8_0;
    }

    public boolean verifyClientID() throws JMSException
    {
        return true;
    }

    /*
     * @see org.apache.qpid.client.AMQConnectionDelegate#isSupportedServerFeature(java.lang.String)
     */
    public boolean isSupportedServerFeature(String featureName)
    {
        // The Qpid Java Broker 0-8..0-9-1 does not advertise features by the qpid.features property, so for now
        // we just hardcode JMS selectors as supported.
        return ServerPropertyNames.FEATURE_QPID_JMS_SELECTOR.equals(featureName);
    }

    @Override
    public void setHeartbeatListener(HeartbeatListener listener)
    {
        _conn.getProtocolHandler().setHeartbeatListener(listener);
    }

    @Override
    public boolean supportsIsBound()
    {
        //Rough check whether the 'isBound' AMQP extension method is supported, by trying to determine if we are connected to Qpid.
        //As older versions of the Qpid broker did not send properties, the value will be assumed true if no server properties
        //are found, or the 'product' entry isn't present, and will only be false if it is present but doesn't match expectation.
        boolean connectedToQpid = true;

        FieldTable serverProperties = _conn.getProtocolHandler().getProtocolSession().getConnectionStartServerProperties();
        if(serverProperties != null)
        {
            if(serverProperties.containsKey(ConnectionStartProperties.PRODUCT))
            {
                //String.valueof to ensure it is non-null, then lowercase it
                String product = String.valueOf(serverProperties.getString(ConnectionStartProperties.PRODUCT)).toLowerCase();

                //value is "unknown" when the naming properties file hasn't been found, e.g in IDE.
                connectedToQpid = product.contains("qpid") || product.equals("unknown");
            }
        }

        if(_logger.isDebugEnabled())
        {
            _logger.debug("supportsIsBound: " + connectedToQpid);
        }

        return connectedToQpid;
    }

    private boolean checkMessageCompressionSupported()
    {
        FieldTable serverProperties = _conn.getProtocolHandler().getProtocolSession().getConnectionStartServerProperties();
        return serverProperties != null
           && Boolean.parseBoolean(serverProperties.getString(ConnectionStartProperties.QPID_MESSAGE_COMPRESSION_SUPPORTED));

    }

    private boolean checkConfirmedPublishSupported()
    {
        FieldTable serverProperties = _conn.getProtocolHandler().getProtocolSession().getConnectionStartServerProperties();
        return serverProperties != null
               && Boolean.parseBoolean(serverProperties.getString(ConnectionStartProperties.QPID_CONFIRMED_PUBLISH_SUPPORTED));

    }

    public boolean isMessageCompressionSupported()
    {
        return _messageCompressionSupported;
    }

    public boolean isAddrSyntaxSupported()
    {
        return _addrSyntaxSupported;
    }

    public boolean isConfirmedPublishNonTransactionalSupported()
    {
        return _confirmedPublishNonTransactionalSupported;
    }


    private static class ReceiverClosedWaiter implements Receiver<ByteBuffer>
    {
        private final CountDownLatch _closedWatcher;
        private final Receiver<ByteBuffer> _receiver;

        public ReceiverClosedWaiter(Receiver<ByteBuffer> receiver)
        {
            _receiver = receiver;
            _closedWatcher = new CountDownLatch(1);
        }

        @Override
        public void received(ByteBuffer msg)
        {
            _receiver.received(msg);
        }

        @Override
        public void exception(Throwable t)
        {
            _receiver.exception(t);
        }

        @Override
        public void closed()
        {
            try
            {
                _receiver.closed();
            }
            finally
            {
                _closedWatcher.countDown();
            }
        }

        public boolean awaitClose(long timeout)
        {
            try
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Waiting " + timeout + "ms for receiver to be closed");
                }

                return _closedWatcher.await(timeout, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return _closedWatcher.getCount() == 0;
            }
        }
    };
}
