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

package org.apache.qpid.amqp_1_0.transport;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.xml.bind.DatatypeConverter;

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructorRegistry;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.framing.SASLFrame;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.security.SaslChallenge;
import org.apache.qpid.amqp_1_0.type.security.SaslCode;
import org.apache.qpid.amqp_1_0.type.security.SaslInit;
import org.apache.qpid.amqp_1_0.type.security.SaslMechanisms;
import org.apache.qpid.amqp_1_0.type.security.SaslOutcome;
import org.apache.qpid.amqp_1_0.type.security.SaslResponse;
import org.apache.qpid.amqp_1_0.type.transport.Attach;
import org.apache.qpid.amqp_1_0.type.transport.Begin;
import org.apache.qpid.amqp_1_0.type.transport.Close;
import org.apache.qpid.amqp_1_0.type.transport.ConnectionError;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Disposition;
import org.apache.qpid.amqp_1_0.type.transport.End;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Flow;
import org.apache.qpid.amqp_1_0.type.transport.Open;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;


public class ConnectionEndpoint implements DescribedTypeConstructorRegistry.Source, ValueWriter.Registry.Source,
                                           ErrorHandler, SASLEndpoint

{
    private static final short CONNECTION_CONTROL_CHANNEL = (short) 0;
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(new byte[0]);
    private static final Symbol SASL_PLAIN = Symbol.valueOf("PLAIN");
    private static final Symbol SASL_ANONYMOUS = Symbol.valueOf("ANONYMOUS");
    private static final Symbol SASL_EXTERNAL = Symbol.valueOf("EXTERNAL");

    private final Container _container;
    private Principal _user;

    private static final int DEFAULT_CHANNEL_MAX = Math.min(Integer.getInteger("amqp.channel_max", 255), 0xFFFF);
    private static final int DEFAULT_MAX_FRAME = Integer.getInteger("amqp.max_frame_size", 1 << 15);
    private static final long DEFAULT_SYNC_TIMEOUT = Long.getLong("amqp.connection_sync_timeout", 5000l);


    private ConnectionState _state = ConnectionState.UNOPENED;
    private int _channelMax = DEFAULT_CHANNEL_MAX;
    private int _maxFrameSize = 4096;
    private String _remoteContainerId;

    private SocketAddress _remoteAddress;

    // positioned by the *outgoing* channel
    private SessionEndpoint[] _sendingSessions;

    // positioned by the *incoming* channel
    private SessionEndpoint[] _receivingSessions;
    private boolean _closedForInput;
    private boolean _closedForOutput;

    private long _idleTimeout;

    private AMQPDescribedTypeRegistry _describedTypeRegistry = AMQPDescribedTypeRegistry.newInstance()
            .registerTransportLayer()
            .registerMessagingLayer()
            .registerTransactionLayer()
            .registerSecurityLayer();

    private FrameOutputHandler<FrameBody> _frameOutputHandler;

    private byte _majorVersion;
    private byte _minorVersion;
    private byte _revision;
    private UnsignedInteger _handleMax = UnsignedInteger.MAX_VALUE;
    private ConnectionEventListener _connectionEventListener = ConnectionEventListener.DEFAULT;
    private String _password;
    private boolean _requiresSASLClient;
    private final boolean _requiresSASLServer;


    private FrameOutputHandler<SaslFrameBody> _saslFrameOutput;

    private boolean _saslComplete;

    private UnsignedInteger _desiredMaxFrameSize = UnsignedInteger.valueOf(DEFAULT_MAX_FRAME);
    private Runnable _onSaslCompleteTask;

    private SaslServerProvider _saslServerProvider;
    private SaslServer _saslServer;
    private boolean _authenticated;
    private String _remoteHostname;
    private Error _remoteError;

    private Map _properties;
    private long _syncTimeout = DEFAULT_SYNC_TIMEOUT;

    private String _localHostname;
    private boolean _secure;
    private Principal _externalPrincipal;

    public ConnectionEndpoint(Container container, SaslServerProvider cbs)
    {
        _container = container;
        _saslServerProvider = cbs;
        _requiresSASLClient = false;
        _requiresSASLServer = cbs != null;
    }

    public ConnectionEndpoint(Container container, Principal user, String password)
    {
        _container = container;
        _user = user;
        _password = password;
        _requiresSASLClient = user != null;
        _requiresSASLServer = false;
    }

    public void setPrincipal(Principal user)
    {
        if (_user == null)
        {
            _user = user;
            _requiresSASLClient = user != null;
        }
    }

    public synchronized void open()
    {
        if (_requiresSASLClient)
        {
            try
            {
                waitUntil(new Predicate()
                {

                    @Override
                    public boolean isSatisfied()
                    {
                        return _saslComplete || _closedForInput;
                    }
                });
            }
            catch (TimeoutException e)
            {
                throw new RuntimeException("Could not connect - authentication error");
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }

            if (!_authenticated)
            {
                throw new RuntimeException("Could not connect - authentication error");
            }
        }
        if (_state == ConnectionState.UNOPENED)
        {
            sendOpen(_channelMax, DEFAULT_MAX_FRAME);
            _state = ConnectionState.AWAITING_OPEN;
        }
    }

    public void setFrameOutputHandler(final FrameOutputHandler<FrameBody> frameOutputHandler)
    {
        _frameOutputHandler = frameOutputHandler;
    }

    public void setProperties(Map<Symbol, Object> properties)
    {
        _properties = properties;
    }

    public synchronized SessionEndpoint createSession(String name)
    {
        // todo assert connection state
        short channel = getFirstFreeChannel();
        if (channel != -1)
        {
            SessionEndpoint endpoint = new SessionEndpoint(this);
            _sendingSessions[channel] = endpoint;
            endpoint.setSendingChannel(channel);
            Begin begin = new Begin();
            begin.setNextOutgoingId(endpoint.getNextOutgoingId());
            begin.setOutgoingWindow(endpoint.getOutgoingWindowSize());
            begin.setIncomingWindow(endpoint.getIncomingWindowSize());

            begin.setHandleMax(_handleMax);
            send(channel, begin);
            return endpoint;

        }
        else
        {
            // TODO - report error
            return null;
        }
    }


    public Container getContainer()
    {
        return _container;
    }

    public Principal getUser()
    {
        return _user;
    }

    public int getChannelMax()
    {
        return _channelMax;
    }

    public int getMaxFrameSize()
    {
        return _maxFrameSize;
    }

    public String getRemoteContainerId()
    {
        return _remoteContainerId;
    }

    private void sendOpen(final int channelMax, final int maxFrameSize)
    {
        Open open = new Open();

        if (_receivingSessions == null)
        {
            _receivingSessions = new SessionEndpoint[channelMax + 1];
            _sendingSessions = new SessionEndpoint[channelMax + 1];
        }
        if (channelMax < _channelMax)
        {
            _channelMax = channelMax;
        }
        open.setChannelMax(UnsignedShort.valueOf((short) channelMax));
        open.setContainerId(_container.getId());
        open.setMaxFrameSize(getDesiredMaxFrameSize());
        open.setHostname(getRemoteHostname());
        if (_properties != null)
        {
            open.setProperties(_properties);
        }

        send(CONNECTION_CONTROL_CHANNEL, open);
    }

    public UnsignedInteger getDesiredMaxFrameSize()
    {
        return _desiredMaxFrameSize;
    }


    public void setDesiredMaxFrameSize(UnsignedInteger size)
    {
        _desiredMaxFrameSize = size;
    }


    private void closeSender()
    {
        setClosedForOutput(true);
        _frameOutputHandler.close();
    }


    short getFirstFreeChannel()
    {
        for (int i = 0; i <= _channelMax; i++)
        {
            if (_sendingSessions[i] == null)
            {
                return (short) i;
            }
        }
        return -1;
    }

    private SessionEndpoint getSession(final short channel)
    {
        SessionEndpoint session = _receivingSessions[channel];
        if (session == null)
        {
            Error error = new Error();
            error.setCondition(ConnectionError.FRAMING_ERROR);
            error.setDescription("Frame received on channel " + channel + " which is not known as a begun session.");
            this.handleError(error);
        }

        return session;
    }


    public synchronized void receiveOpen(short channel, Open open)
    {

        _channelMax = open.getChannelMax() == null ? _channelMax
                : open.getChannelMax().intValue() < _channelMax
                        ? open.getChannelMax().intValue()
                        : _channelMax;

        if (_receivingSessions == null)
        {
            _receivingSessions = new SessionEndpoint[_channelMax + 1];
            _sendingSessions = new SessionEndpoint[_channelMax + 1];
        }

        UnsignedInteger remoteDesiredMaxFrameSize =
                open.getMaxFrameSize() == null ? UnsignedInteger.valueOf(DEFAULT_MAX_FRAME) : open.getMaxFrameSize();

        _maxFrameSize = (remoteDesiredMaxFrameSize.compareTo(_desiredMaxFrameSize) < 0
                ? remoteDesiredMaxFrameSize
                : _desiredMaxFrameSize).intValue();

        _remoteContainerId = open.getContainerId();
        _localHostname = open.getHostname();

        if (open.getIdleTimeOut() != null)
        {
            _idleTimeout = open.getIdleTimeOut().longValue();
        }

        _connectionEventListener.openReceived();

        switch (_state)
        {
            case UNOPENED:
                sendOpen(_channelMax, _maxFrameSize);
            case AWAITING_OPEN:
                _state = ConnectionState.OPEN;
            default:
                // TODO bad stuff (connection already open)

        }
        /*if(_state == ConnectionState.AWAITING_OPEN)
        {
            _state = ConnectionState.OPEN;
        }
*/
        notifyAll();
    }

    public synchronized void receiveClose(short channel, Close close)
    {
        setClosedForInput(true);
        _connectionEventListener.closeReceived();
        switch (_state)
        {
            case UNOPENED:
            case AWAITING_OPEN:
                Error error = new Error();
                error.setCondition(ConnectionError.CONNECTION_FORCED);
                error.setDescription("Connection close sent before connection was opened");
                close(error);
                break;
            case OPEN:
                _state = ConnectionState.CLOSE_RECEIVED;
                sendClose(new Close());
                _state = ConnectionState.CLOSED;
                break;
            case CLOSE_SENT:
                _state = ConnectionState.CLOSED;

            default:
        }
        _remoteError = close.getError();

        notifyAll();
    }

    public synchronized void close(Error error)
    {
        Close close = new Close();
        close.setError(error);
        switch (_state)
        {
            case UNOPENED:
                sendOpen(0, 0);
                sendClose(close);
                _state = ConnectionState.CLOSED;
                break;
            case AWAITING_OPEN:
            case OPEN:
                sendClose(close);
                _state = ConnectionState.CLOSE_SENT;
            case CLOSE_SENT:
            case CLOSED:
                // already sent our close - too late to do anything more
                break;
            default:
                // TODO Unknown state
        }
    }

    public synchronized void inputClosed()
    {
        if (!_closedForInput)
        {
            _closedForInput = true;
            _logger.received(_remoteAddress,(short)-1,"Underlying connection closed");
            switch (_state)
            {
                case UNOPENED:
                case AWAITING_OPEN:
                case CLOSE_SENT:
                    _state = ConnectionState.CLOSED;
                    closeSender();
                    break;
                case OPEN:
                    _state = ConnectionState.CLOSE_RECEIVED;
                case CLOSED:
                    // already sent our close - too late to do anything more
                    break;
                default:
            }
            if (_receivingSessions != null)
            {
                for (int i = 0; i < _receivingSessions.length; i++)
                {
                    if (_receivingSessions[i] != null)
                    {
                        _receivingSessions[i].end();
                        _receivingSessions[i] = null;

                    }
                }
            }
            if(_connectionEventListener != null)
            {
                _connectionEventListener.closeReceived();
            }
        }
        notifyAll();
    }

    private void sendClose(Close closeToSend)
    {
        send(CONNECTION_CONTROL_CHANNEL, closeToSend);
        closeSender();
    }

    private synchronized void setClosedForInput(boolean closed)
    {
        _closedForInput = closed;

        notifyAll();
    }

    public synchronized void receiveBegin(short channel, Begin begin)
    {
        short myChannelId;


        if (begin.getRemoteChannel() != null)
        {
            myChannelId = begin.getRemoteChannel().shortValue();
            SessionEndpoint endpoint;
            try
            {
                endpoint = _sendingSessions[myChannelId];
            }
            catch (IndexOutOfBoundsException e)
            {
                final Error error = new Error();
                error.setCondition(ConnectionError.FRAMING_ERROR);
                error.setDescription("BEGIN received on channel " + channel + " with given remote-channel "
                                     + begin.getRemoteChannel() + " which is outside the valid range of 0 to "
                                     + _channelMax + ".");
                close(error);
                return;
            }
            if (endpoint != null)
            {
                if (_receivingSessions[channel] == null)
                {
                    _receivingSessions[channel] = endpoint;
                    endpoint.setReceivingChannel(channel);
                    endpoint.setNextIncomingId(begin.getNextOutgoingId());
                    endpoint.setOutgoingSessionCredit(begin.getIncomingWindow());

                    if (endpoint.getState() == SessionState.END_SENT)
                    {
                        _sendingSessions[myChannelId] = null;
                    }
                }
                else
                {
                    final Error error = new Error();
                    error.setCondition(ConnectionError.FRAMING_ERROR);
                    error.setDescription("BEGIN received on channel " + channel + " which is already in use.");
                    close(error);
                }
            }
            else
            {
                final Error error = new Error();
                error.setCondition(ConnectionError.FRAMING_ERROR);
                error.setDescription("BEGIN received on channel " + channel + " with given remote-channel "
                                     + begin.getRemoteChannel() + " which is not known as a begun session.");
                close(error);
            }


        }
        else // Peer requesting session creation
        {

            myChannelId = getFirstFreeChannel();
            if (myChannelId == -1)
            {
                // close any half open channel
                myChannelId = getFirstFreeChannel();

            }

            if (_receivingSessions[channel] == null)
            {
                SessionEndpoint endpoint = new SessionEndpoint(this, begin);

                _receivingSessions[channel] = endpoint;
                _sendingSessions[myChannelId] = endpoint;

                Begin beginToSend = new Begin();

                endpoint.setReceivingChannel(channel);
                endpoint.setSendingChannel(myChannelId);
                beginToSend.setRemoteChannel(UnsignedShort.valueOf(channel));
                beginToSend.setNextOutgoingId(endpoint.getNextOutgoingId());
                beginToSend.setOutgoingWindow(endpoint.getOutgoingWindowSize());
                beginToSend.setIncomingWindow(endpoint.getIncomingWindowSize());
                send(myChannelId, beginToSend);

                _connectionEventListener.remoteSessionCreation(endpoint);
            }
            else
            {
                final Error error = new Error();
                error.setCondition(ConnectionError.FRAMING_ERROR);
                error.setDescription("BEGIN received on channel " + channel + " which is already in use.");
                close(error);
            }

        }


    }


    public synchronized void receiveEnd(short channel, End end)
    {
        SessionEndpoint endpoint = _receivingSessions[channel];
        if (endpoint != null)
        {
            _receivingSessions[channel] = null;

            endpoint.receiveEnd(end);
        }
        else
        {
            // TODO error
        }

    }


    public synchronized void sendEnd(short channel, End end, boolean remove)
    {
        send(channel, end);
        if (remove)
        {
            _sendingSessions[channel] = null;
        }
    }

    public synchronized void receiveAttach(short channel, Attach attach)
    {
        SessionEndpoint endPoint = getSession(channel);
        if (endPoint != null)
        {
            endPoint.receiveAttach(attach);
        }
    }


    public synchronized void receiveDetach(short channel, Detach detach)
    {
        SessionEndpoint endPoint = getSession(channel);
        if (endPoint != null)
        {
            endPoint.receiveDetach(detach);
        }
    }

    public synchronized void receiveTransfer(short channel, Transfer transfer)
    {
        SessionEndpoint endPoint = getSession(channel);
        if (endPoint != null)
        {
            endPoint.receiveTransfer(transfer);
        }
    }

    public synchronized void receiveDisposition(short channel, Disposition disposition)
    {
        SessionEndpoint endPoint = getSession(channel);
        if (endPoint != null)
        {
            endPoint.receiveDisposition(disposition);
        }
    }

    public synchronized void receiveFlow(short channel, Flow flow)
    {
        SessionEndpoint endPoint = getSession(channel);
        if (endPoint != null)
        {
            endPoint.receiveFlow(flow);
        }
    }


    public synchronized void send(short channel, FrameBody body)
    {
        send(channel, body, null);
    }


    public synchronized int send(short channel, FrameBody body, ByteBuffer payload)
    {
        if (!_closedForOutput)
        {
            ValueWriter<FrameBody> writer = _describedTypeRegistry.getValueWriter(body);
            int size = writer.writeToBuffer(EMPTY_BYTE_BUFFER);
            ByteBuffer payloadDup = payload == null ? null : payload.duplicate();
            int payloadSent = getMaxFrameSize() - (size + 9);
            if (payloadSent < (payload == null ? 0 : payload.remaining()))
            {

                if (body instanceof Transfer)
                {
                    ((Transfer) body).setMore(Boolean.TRUE);
                }

                writer = _describedTypeRegistry.getValueWriter(body);
                size = writer.writeToBuffer(EMPTY_BYTE_BUFFER);
                payloadSent = getMaxFrameSize() - (size + 9);

                try
                {
                    payloadDup.limit(payloadDup.position() + payloadSent);
                }
                catch (NullPointerException npe)
                {
                    throw npe;
                }
            }
            else
            {
                payloadSent = payload == null ? 0 : payload.remaining();
            }
            _frameOutputHandler.send(AMQFrame.createAMQFrame(channel, body, payloadDup));
            return payloadSent;
        }
        else
        {
            return -1;
        }
    }

    public void invalidHeaderReceived()
    {
        setClosedForInput(true);
    }

    public synchronized boolean closedForInput()
    {
        return _closedForInput;
    }

    public synchronized void protocolHeaderReceived(final byte major, final byte minorVersion, final byte revision)
    {
        if (_requiresSASLServer && _state != ConnectionState.UNOPENED)
        {
            // TODO - bad stuff
        }

        _majorVersion = major;
        _minorVersion = minorVersion;
        _revision = revision;
    }

    public synchronized void handleError(final Error error)
    {
        if (!closedForOutput())
        {
            Close close = new Close();
            close.setError(error);
            send((short) 0, close);

            this.setClosedForOutput(true);
        }
    }

    public void setExternalPrincipal(final Principal externalPrincipal)
    {
        _externalPrincipal = externalPrincipal;
    }

    public static interface FrameReceiptLogger
    {
        boolean isEnabled();

        void received(final SocketAddress remoteAddress, short channel, Object frame);
    }


    private FrameReceiptLogger _logger =
            new FrameReceiptLogger()
            {
                Logger _underlying = Logger.getLogger("FRM");

                @Override
                public boolean isEnabled()
                {
                    return _underlying.isLoggable(Level.FINE);
                }

                @Override
                public void received(final SocketAddress remoteAddress, final short channel, final Object frame)
                {
                    _underlying.fine("RECV[" + remoteAddress + "|" + channel + "] : " + frame);
                }

            };

    public void setLogger(final FrameReceiptLogger logger)
    {
        _logger = logger;
    }

    public synchronized void receive(final short channel, final Object frame)
    {
        if (_logger.isEnabled())
        {
            _logger.received(_remoteAddress, channel, frame);
        }
        if (frame instanceof FrameBody)
        {
            ((FrameBody) frame).invoke(channel, this);
        }
        else if (frame instanceof SaslFrameBody)
        {
            ((SaslFrameBody) frame).invoke(this);
        }
    }

    public AMQPDescribedTypeRegistry getDescribedTypeRegistry()
    {
        return _describedTypeRegistry;
    }

    public synchronized void setClosedForOutput(boolean closed)
    {
        _closedForOutput = closed;
        notifyAll();
    }

    public synchronized boolean closedForOutput()
    {
        return _closedForOutput;
    }


    public Object getLock()
    {
        return this;
    }

    public synchronized long getIdleTimeout()
    {
        return _idleTimeout;
    }

    public synchronized void close()
    {
        switch (_state)
        {
            case AWAITING_OPEN:
            case OPEN:
                Close closeToSend = new Close();
                sendClose(closeToSend);
                _state = ConnectionState.CLOSE_SENT;
                break;
            case CLOSE_SENT:
            default:
        }

    }

    public void setConnectionEventListener(final ConnectionEventListener connectionEventListener)
    {
        _connectionEventListener = connectionEventListener;
    }

    public ConnectionEventListener getConnectionEventListener()
    {
        return _connectionEventListener;
    }

    public byte getMinorVersion()
    {
        return _minorVersion;
    }

    public byte getRevision()
    {
        return _revision;
    }

    public byte getMajorVersion()
    {
        return _majorVersion;
    }

    public void receiveSaslInit(final SaslInit saslInit)
    {
        String mechanism = saslInit.getMechanism() == null ? null : saslInit.getMechanism().toString();
        final Binary initialResponse = saslInit.getInitialResponse();
        byte[] response = initialResponse == null ? new byte[0] : initialResponse.getArray();


        try
        {
            _saslServer = _saslServerProvider.getSaslServer(mechanism, "localhost");

            // Process response from the client
            byte[] challenge = _saslServer.evaluateResponse(response != null ? response : new byte[0]);

            if (_saslServer.isComplete())
            {
                SaslOutcome outcome = new SaslOutcome();

                outcome.setCode(SaslCode.OK);
                _saslFrameOutput.send(new SASLFrame(outcome), null);
                synchronized (getLock())
                {
                    _saslComplete = true;
                    _authenticated = true;
                    _user = _saslServerProvider.getAuthenticatedPrincipal(_saslServer);
                    getLock().notifyAll();
                }

                if (_onSaslCompleteTask != null)
                {
                    _onSaslCompleteTask.run();
                }

            }
            else
            {
                SaslChallenge challengeBody = new SaslChallenge();
                challengeBody.setChallenge(new Binary(challenge));
                _saslFrameOutput.send(new SASLFrame(challengeBody), null);

            }
        }
        catch (SaslException e)
        {
            SaslOutcome outcome = new SaslOutcome();

            outcome.setCode(SaslCode.AUTH);
            _saslFrameOutput.send(new SASLFrame(outcome), null);
            synchronized (getLock())
            {
                _saslComplete = true;
                _authenticated = false;
                getLock().notifyAll();
            }
            if (_onSaslCompleteTask != null)
            {
                _onSaslCompleteTask.run();
            }

        }
    }

    private final AmqpSaslClient[] _supportedSaslClientMechanisms =
            new AmqpSaslClient[]{new ScramSHA256SaslClient(), new ScramSHA1SaslClient(), new ExternalSaslClient(),
                    new CramMD5SaslClient(), new CramMD5HashedSaslClient(), new PlainSaslClient(), new AnonymousSaslClient()};

    private AmqpSaslClient _saslClient;

    public void receiveSaslMechanisms(final SaslMechanisms saslMechanisms)
    {
        SaslInit init = new SaslInit();
        init.setHostname(_remoteHostname);

        Set<Symbol> mechanisms = new HashSet<Symbol>(Arrays.asList(saslMechanisms.getSaslServerMechanisms()));

        for (AmqpSaslClient saslClient : _supportedSaslClientMechanisms)
        {
            if (mechanisms.contains(saslClient.getMechanismName()) && saslClient.canSupportMechanism())
            {
                _saslClient = saslClient;
                break;
            }
        }
        if (_saslClient != null)
        {
            try
            {

                init.setMechanism(_saslClient.getMechanismName());
                if (_saslClient.hasInitialResponse())
                {
                    init.setInitialResponse(new Binary(_saslClient.getResponse(new byte[0])));
                }
                _saslFrameOutput.send(new SASLFrame(init), null);

            }
            catch (SaslException e)
            {
                closeSaslWithFailure();

            }
        }
        else
        {
            closeSaslWithFailure();

        }
    }

    public void closeSaslWithFailure()
    {
        synchronized (getLock())
        {
            _saslComplete = true;
            _authenticated = false;
            getLock().notifyAll();
        }
        setClosedForInput(true);
        _saslFrameOutput.close();
    }

    public void receiveSaslChallenge(final SaslChallenge saslChallenge)
    {
        try
        {
            ByteBuffer challenge = saslChallenge.getChallenge().asByteBuffer();
            final byte[] challengeBytes = new byte[challenge.remaining()];
            challenge.get(challengeBytes);
            byte[] responseBytes = _saslClient.getResponse(challengeBytes);
            SaslResponse response = new SaslResponse();
            response.setResponse(new Binary(responseBytes));
            _saslFrameOutput.send(new SASLFrame(response), null);
        }
        catch (SaslException e)
        {
            closeSaslWithFailure();
        }

    }

    public void receiveSaslResponse(final SaslResponse saslResponse)
    {
        final Binary responseBinary = saslResponse.getResponse();
        byte[] response = responseBinary == null ? new byte[0] : responseBinary.getArray();


        try
        {

            // Process response from the client
            byte[] challenge = _saslServer.evaluateResponse(response != null ? response : new byte[0]);

            if (_saslServer.isComplete())
            {
                SaslOutcome outcome = new SaslOutcome();

                outcome.setCode(SaslCode.OK);
                _saslFrameOutput.send(new SASLFrame(outcome), null);
                synchronized (getLock())
                {
                    _saslComplete = true;
                    _authenticated = true;
                    _user = _saslServerProvider.getAuthenticatedPrincipal(_saslServer);
                    getLock().notifyAll();
                }
                if (_onSaslCompleteTask != null)
                {
                    _onSaslCompleteTask.run();
                }

            }
            else
            {
                SaslChallenge challengeBody = new SaslChallenge();
                challengeBody.setChallenge(new Binary(challenge));
                _saslFrameOutput.send(new SASLFrame(challengeBody), null);

            }
        }
        catch (SaslException e)
        {
            SaslOutcome outcome = new SaslOutcome();

            outcome.setCode(SaslCode.AUTH);
            _saslFrameOutput.send(new SASLFrame(outcome), null);
            synchronized (getLock())
            {
                _saslComplete = true;
                _authenticated = false;
                getLock().notifyAll();
            }
            if (_onSaslCompleteTask != null)
            {
                _onSaslCompleteTask.run();
            }

        }
    }

    public void receiveSaslOutcome(final SaslOutcome saslOutcome)
    {
        if (saslOutcome.getCode() == SaslCode.OK)
        {
            _saslFrameOutput.close();
            synchronized (getLock())
            {
                _saslComplete = true;
                _authenticated = true;
                getLock().notifyAll();
            }
            if (_onSaslCompleteTask != null)
            {
                _onSaslCompleteTask.run();
            }
        }
        else
        {
            closeSaslWithFailure();
        }
    }

    public boolean requiresSASL()
    {
        return _requiresSASLClient || _requiresSASLServer;
    }

    public void setSaslFrameOutput(final FrameOutputHandler<SaslFrameBody> saslFrameOutput)
    {
        _saslFrameOutput = saslFrameOutput;
    }

    public void setOnSaslComplete(Runnable task)
    {
        _onSaslCompleteTask = task;

    }

    public boolean isAuthenticated()
    {
        return _authenticated;
    }

    public void initiateSASL(String[] mechanismNames)
    {
        SaslMechanisms mechanisms = new SaslMechanisms();
        ArrayList<Symbol> mechanismsList = new ArrayList<Symbol>();
        for (String name : mechanismNames)
        {
            mechanismsList.add(Symbol.valueOf(name));
        }
        mechanisms.setSaslServerMechanisms(mechanismsList.toArray(new Symbol[mechanismsList.size()]));
        _saslFrameOutput.send(new SASLFrame(mechanisms), null);
    }

    public boolean isSASLComplete()
    {
        return _saslComplete;
    }

    public SocketAddress getRemoteAddress()
    {
        return _remoteAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress)
    {
        _remoteAddress = remoteAddress;
    }

    public String getRemoteHostname()
    {
        return _remoteHostname;
    }

    public void setRemoteHostname(final String remoteHostname)
    {
        _remoteHostname = remoteHostname;
    }

    public String getLocalHostname()
    {
        return _localHostname;
    }


    public boolean isOpen()
    {
        return _state == ConnectionState.OPEN;
    }

    public boolean isClosed()
    {
        return _state == ConnectionState.CLOSED
               || _state == ConnectionState.CLOSE_RECEIVED;
    }

    public Error getRemoteError()
    {
        return _remoteError;
    }

    public void setChannelMax(final short channelMax)
    {
        _channelMax = channelMax;
    }

    public long getSyncTimeout()
    {
        return _syncTimeout;
    }

    public void setSyncTimeout(final long syncTimeout)
    {
        _syncTimeout = syncTimeout;
    }

    public void waitUntil(Predicate predicate) throws InterruptedException, TimeoutException
    {
        waitUntil(predicate, _syncTimeout);
    }

    public void waitUntil(Predicate predicate, long timeout) throws InterruptedException, TimeoutException
    {
        long endTime = System.currentTimeMillis() + timeout;

        synchronized (getLock())
        {
            while (!predicate.isSatisfied())
            {
                getLock().wait(timeout);

                if (!predicate.isSatisfied())
                {
                    timeout = endTime - System.currentTimeMillis();
                    if (timeout <= 0l)
                    {
                        throw new TimeoutException();
                    }
                }
            }
        }

    }

    private interface AmqpSaslClient
    {
        boolean canSupportMechanism();

        Symbol getMechanismName();

        boolean hasInitialResponse();

        byte[] getResponse(byte[] challenge) throws SaslException;
    }

    private class AnonymousSaslClient implements AmqpSaslClient
    {

        @Override
        public boolean canSupportMechanism()
        {
            return true;
        }

        @Override
        public Symbol getMechanismName()
        {
            return Symbol.valueOf("ANONYMOUS");
        }

        @Override
        public boolean hasInitialResponse()
        {
            return false;
        }

        @Override
        public byte[] getResponse(final byte[] challenge)
        {
            return new byte[0];
        }
    }

    private class ExternalSaslClient implements AmqpSaslClient
    {

        @Override
        public boolean canSupportMechanism()
        {
            return ConnectionEndpoint.this._externalPrincipal != null;
        }

        @Override
        public Symbol getMechanismName()
        {
            return Symbol.valueOf("EXTERNAL");
        }

        @Override
        public boolean hasInitialResponse()
        {
            return false;
        }

        @Override
        public byte[] getResponse(final byte[] challenge)
        {
            return new byte[0];
        }
    }

    private class PlainSaslClient implements AmqpSaslClient
    {

        private boolean _initResponseSent;

        @Override
        public boolean canSupportMechanism()
        {
            return ConnectionEndpoint.this._user != null
                   && ConnectionEndpoint.this._password != null;
        }

        @Override
        public Symbol getMechanismName()
        {
            return Symbol.valueOf("PLAIN");
        }

        @Override
        public boolean hasInitialResponse()
        {
            return true;
        }

        @Override
        public byte[] getResponse(final byte[] challenge)
        {
            if (_initResponseSent)
            {
                return new byte[0];
            }
            else
            {
                _initResponseSent = true;
                byte[] usernameBytes = _user.getName().getBytes(Charset.forName("UTF-8"));
                byte[] passwordBytes = _password.getBytes(Charset.forName("UTF-8"));
                byte[] initResponse = new byte[usernameBytes.length + passwordBytes.length + 2];
                System.arraycopy(usernameBytes, 0, initResponse, 1, usernameBytes.length);
                System.arraycopy(passwordBytes, 0, initResponse, usernameBytes.length + 2, passwordBytes.length);
                return initResponse;
            }
        }
    }


    private static final Charset ASCII = Charset.forName("ASCII");

    abstract static private class AbstractScramSaslClient implements AmqpSaslClient
    {


        private static final byte[] INT_1 = new byte[]{0, 0, 0, 1};
        private static final String GS2_HEADER = "n,,";

        private final String _digestName;
        private final String _hmacName;
        private final ConnectionEndpoint _endpoint;

        private String _username;
        private final String _clientNonce = UUID.randomUUID().toString();
        private String _serverNonce;
        private byte[] _salt;
        private int _iterationCount;
        private String _clientFirstMessageBare;
        private byte[] _serverSignature;

        enum State
        {
            INITIAL,
            CLIENT_FIRST_SENT,
            CLIENT_PROOF_SENT,
            COMPLETE
        }

        public final Symbol _mechanism;


        private State _state = State.INITIAL;

        public AbstractScramSaslClient(ConnectionEndpoint endpoint,
                                       final Symbol mechanism,
                                       final String digestName,
                                       final String hmacName)
        {
            _endpoint = endpoint;
            _mechanism = mechanism;
            _digestName = digestName;
            _hmacName = hmacName;

        }


        @Override
        public boolean canSupportMechanism()
        {
            return _endpoint._user != null
                   && _endpoint._password != null;
        }


        @Override
        public Symbol getMechanismName()
        {
            return _mechanism;
        }

        @Override
        public boolean hasInitialResponse()
        {
            return true;
        }

        @Override
        public byte[] getResponse(final byte[] challenge) throws SaslException
        {
            byte[] response;
            switch (_state)
            {
                case INITIAL:
                    response = initialResponse();
                    _state = State.CLIENT_FIRST_SENT;
                    break;
                case CLIENT_FIRST_SENT:
                    response = calculateClientProof(challenge);
                    _state = State.CLIENT_PROOF_SENT;
                    break;
                case CLIENT_PROOF_SENT:
                    evaluateOutcome(challenge);
                    response = new byte[0];
                    _state = State.COMPLETE;
                    break;
                default:
                    throw new SaslException("No challenge expected in state " + _state);
            }
            return response;
        }

        private void evaluateOutcome(final byte[] challenge) throws SaslException
        {
            String serverFinalMessage = new String(challenge, ASCII);
            String[] parts = serverFinalMessage.split(",");
            if (!parts[0].startsWith("v="))
            {
                throw new SaslException("Server final message did not contain verifier");
            }
            byte[] serverSignature = DatatypeConverter.parseBase64Binary(parts[0].substring(2));
            if (!Arrays.equals(_serverSignature, serverSignature))
            {
                throw new SaslException("Server signature did not match");
            }
        }

        private byte[] calculateClientProof(final byte[] challenge) throws SaslException
        {
            try
            {
                String serverFirstMessage = new String(challenge, ASCII);
                String[] parts = serverFirstMessage.split(",");
                if (parts.length < 3)
                {
                    throw new SaslException("Server challenge '" + serverFirstMessage + "' cannot be parsed");
                }
                else if (parts[0].startsWith("m="))
                {
                    throw new SaslException("Server requires mandatory extension which is not supported: " + parts[0]);
                }
                else if (!parts[0].startsWith("r="))
                {
                    throw new SaslException("Server challenge '"
                                            + serverFirstMessage
                                            + "' cannot be parsed, cannot find nonce");
                }
                String nonce = parts[0].substring(2);
                if (!nonce.startsWith(_clientNonce))
                {
                    throw new SaslException("Server challenge did not use correct client nonce");
                }
                _serverNonce = nonce;
                if (!parts[1].startsWith("s="))
                {
                    throw new SaslException("Server challenge '"
                                            + serverFirstMessage
                                            + "' cannot be parsed, cannot find salt");
                }
                String base64Salt = parts[1].substring(2);
                _salt = DatatypeConverter.parseBase64Binary(base64Salt);
                if (!parts[2].startsWith("i="))
                {
                    throw new SaslException("Server challenge '"
                                            + serverFirstMessage
                                            + "' cannot be parsed, cannot find iteration count");
                }
                String iterCountString = parts[2].substring(2);
                _iterationCount = Integer.parseInt(iterCountString);
                if (_iterationCount <= 0)
                {
                    throw new SaslException("Iteration count " + _iterationCount + " is not a positive integer");
                }

                byte[] passwordBytes = saslPrep(_endpoint._password).getBytes("UTF-8");

                byte[] saltedPassword = generateSaltedPassword(passwordBytes);


                String clientFinalMessageWithoutProof =
                        "c=" + DatatypeConverter.printBase64Binary(GS2_HEADER.getBytes(ASCII))
                        + ",r=" + _serverNonce;

                String authMessage =
                        _clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;

                byte[] clientKey = computeHmac(saltedPassword, "Client Key");
                byte[] storedKey = MessageDigest.getInstance(_digestName).digest(clientKey);

                byte[] clientSignature = computeHmac(storedKey, authMessage);

                byte[] clientProof = clientKey.clone();
                for (int i = 0; i < clientProof.length; i++)
                {
                    clientProof[i] ^= clientSignature[i];
                }
                byte[] serverKey = computeHmac(saltedPassword, "Server Key");
                _serverSignature = computeHmac(serverKey, authMessage);

                String finalMessageWithProof = clientFinalMessageWithoutProof
                                               + ",p=" + DatatypeConverter.printBase64Binary(clientProof);
                return finalMessageWithProof.getBytes();
            }
            catch (IllegalArgumentException | IOException | NoSuchAlgorithmException e)
            {
                throw new SaslException(e.getMessage(), e);
            }
        }

        private byte[] computeHmac(final byte[] key, final String string)
                throws SaslException, UnsupportedEncodingException
        {
            Mac mac = createHmac(key);
            mac.update(string.getBytes(ASCII));
            return mac.doFinal();
        }

        private byte[] generateSaltedPassword(final byte[] passwordBytes) throws SaslException
        {
            Mac mac = createHmac(passwordBytes);

            mac.update(_salt);
            mac.update(INT_1);
            byte[] result = mac.doFinal();

            byte[] previous = null;
            for (int i = 1; i < _iterationCount; i++)
            {
                mac.update(previous != null ? previous : result);
                previous = mac.doFinal();
                for (int x = 0; x < result.length; x++)
                {
                    result[x] ^= previous[x];
                }
            }

            return result;
        }

        private Mac createHmac(final byte[] keyBytes)
                throws SaslException
        {
            try
            {
                SecretKeySpec key = new SecretKeySpec(keyBytes, _hmacName);
                Mac mac = Mac.getInstance(_hmacName);
                mac.init(key);
                return mac;
            }
            catch (NoSuchAlgorithmException | InvalidKeyException e)
            {
                throw new SaslException(e.getMessage(), e);
            }
        }


        private byte[] initialResponse() throws SaslException
        {
            StringBuffer buf = new StringBuffer("n=");
            _username = _endpoint.getUser().getName();
            buf.append(saslPrep(_username));
            buf.append(",r=");
            buf.append(_clientNonce);
            _clientFirstMessageBare = buf.toString();
            return (GS2_HEADER + _clientFirstMessageBare).getBytes(ASCII);
        }

        private String saslPrep(String name) throws SaslException
        {
            // TODO - a real implementation of SaslPrep

            if (!ASCII.newEncoder().canEncode(name))
            {
                throw new SaslException("Can only encode names and passwords which are restricted to ASCII characters");
            }

            name = name.replace("=", "=3D");
            name = name.replace(",", "=2C");
            return name;
        }

        public boolean isComplete()
        {
            return _state == State.COMPLETE;
        }

    }

    private final class ScramSHA1SaslClient extends AbstractScramSaslClient
    {

        public ScramSHA1SaslClient()
        {
            super(ConnectionEndpoint.this, Symbol.valueOf("SCRAM-SHA-1"), "SHA-1", "HmacSHA1");
        }
    }


    private final class ScramSHA256SaslClient extends AbstractScramSaslClient
    {

        public ScramSHA256SaslClient()
        {
            super(ConnectionEndpoint.this, Symbol.valueOf("SCRAM-SHA-256"), "SHA-256", "HmacSHA256");
        }
    }

    private class CramMD5SaslClient implements AmqpSaslClient
    {

        @Override
        public boolean canSupportMechanism()
        {
            return ConnectionEndpoint.this._user != null
                   && ConnectionEndpoint.this._password != null;
        }

        @Override
        public Symbol getMechanismName()
        {
            return Symbol.valueOf("CRAM-MD5");
        }

        @Override
        public boolean hasInitialResponse()
        {
            return false;
        }

        @Override
        public byte[] getResponse(final byte[] challenge) throws SaslException
        {

            try
            {
                SecretKeySpec key = new SecretKeySpec(getSharedSecretBytes(), "HmacMD5");

                Mac mac = Mac.getInstance("HmacMD5");
                mac.init(key);

                mac.update(challenge);
                byte[] result = mac.doFinal();

                StringBuilder responseBeforeBase64 = new StringBuilder(ConnectionEndpoint.this.getUser().getName());
                responseBeforeBase64.append(" ");
                for (byte b : result)
                {
                    responseBeforeBase64.append(String.format("%02x", b));
                }

                return responseBeforeBase64.toString().getBytes(ASCII);
            }
            catch (NoSuchAlgorithmException | InvalidKeyException e)
            {
                throw new SaslException(e.getMessage(), e);
            }
        }

        public byte[] getSharedSecretBytes() throws SaslException
        {
            return ConnectionEndpoint.this._password.getBytes(ASCII);
        }
    }

    private final class CramMD5HashedSaslClient extends CramMD5SaslClient
    {

        @Override
        public Symbol getMechanismName()
        {
            return Symbol.valueOf("CRAM-MD5-HASHED");
        }

        public byte[] getSharedSecretBytes() throws SaslException
        {

            try
            {

                byte[] data = ConnectionEndpoint.this._password.getBytes("utf-8");
                MessageDigest md = MessageDigest.getInstance("MD5");
                for (byte b : data)
                {
                    md.update(b);
                }

                byte[] digest = md.digest();

                char[] hash = new char[digest.length];

                int index = 0;
                for (byte b : digest)
                {
                    hash[index++] = (char) b;
                }

                return new String(hash).getBytes("utf-8");
            }
            catch (NoSuchAlgorithmException | UnsupportedEncodingException e)
            {
                throw new SaslException(e.getMessage(), e);
            }

        }

    }
}
