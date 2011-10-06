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

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructorRegistry;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.framing.SASLFrame;
import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.security.SaslChallenge;
import org.apache.qpid.amqp_1_0.type.security.SaslCode;
import org.apache.qpid.amqp_1_0.type.security.SaslInit;
import org.apache.qpid.amqp_1_0.type.security.SaslMechanisms;
import org.apache.qpid.amqp_1_0.type.security.SaslOutcome;
import org.apache.qpid.amqp_1_0.type.security.SaslResponse;
import org.apache.qpid.amqp_1_0.type.transport.*;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;


import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;


public class ConnectionEndpoint implements DescribedTypeConstructorRegistry.Source, ValueWriter.Registry.Source,
                                           ErrorHandler, SASLEndpoint

{
    private static final short CONNECTION_CONTROL_CHANNEL = (short) 0;
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(new byte[0]);

    private final Container _container;
    private Principal _user;

    private static final short DEFAULT_CHANNEL_MAX = 255;
    private static final int DEFAULT_MAX_FRAME = Integer.getInteger("amqp.max_frame_size",1<<15);


    private ConnectionState _state = ConnectionState.UNOPENED;
    private short _channelMax;
    private int _maxFrameSize = 4096;
    private String _remoteContainerId;

    private SocketAddress _remoteAddress;

    // positioned by the *outgoing* channel
    private SessionEndpoint[] _sendingSessions = new SessionEndpoint[DEFAULT_CHANNEL_MAX+1];

    // positioned by the *incoming* channel
    private SessionEndpoint[] _receivingSessions = new SessionEndpoint[DEFAULT_CHANNEL_MAX+1];
    private boolean _closedForInput;
    private boolean _closedForOutput;

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
    private final boolean _requiresSASLClient;
    private final boolean _requiresSASLServer;


    private FrameOutputHandler<SaslFrameBody> _saslFrameOutput;

    private boolean _saslComplete;

    private UnsignedInteger _desiredMaxFrameSize = UnsignedInteger.valueOf(DEFAULT_MAX_FRAME);
    private Runnable _onSaslCompleteTask;

    private CallbackHanderSource _callbackHandlersource;
    private SaslServer _saslServer;
    private boolean _authenticated;
    private String _remoteHostname;

    public ConnectionEndpoint(Container container, CallbackHanderSource cbs)
    {
        _container = container;
        _callbackHandlersource = cbs;
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


    public synchronized void open()
    {
        if(_requiresSASLClient)
        {
            synchronized (getLock())
            {
                while(!_saslComplete)
                {
                    try
                    {
                        getLock().wait();
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            }
            if(!_authenticated)
            {
                throw new RuntimeException("Could not connect - authentication error");
            }
        }
        if(_state == ConnectionState.UNOPENED)
        {
            sendOpen(DEFAULT_CHANNEL_MAX, DEFAULT_MAX_FRAME);
            _state = ConnectionState.AWAITING_OPEN;
        }
    }

    public void setFrameOutputHandler(final FrameOutputHandler<FrameBody> frameOutputHandler)
    {
        _frameOutputHandler = frameOutputHandler;
    }

    public synchronized SessionEndpoint createSession(String name)
    {
        // todo assert connection state
        SessionEndpoint endpoint = new SessionEndpoint(this);
        short channel =  getFirstFreeChannel();
        if(channel != -1)
        {
            _sendingSessions[channel] = endpoint;
            endpoint.setSendingChannel(channel);
            Begin begin = new Begin();
            begin.setNextOutgoingId(endpoint.getNextOutgoingId());
            begin.setOutgoingWindow(endpoint.getOutgoingWindowSize());
            begin.setIncomingWindow(endpoint.getIncomingWindowSize());

            begin.setHandleMax(_handleMax);
            send(channel, begin);

        }
        else
        {
            // todo error
        }
        return endpoint;
    }


    public Container getContainer()
    {
        return _container;
    }

    public Principal getUser()
    {
        return _user;
    }

    public short getChannelMax()
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

    private void sendOpen(final short channelMax, final int maxFrameSize)
    {
        Open open = new Open();

        open.setChannelMax(UnsignedShort.valueOf(DEFAULT_CHANNEL_MAX));
        open.setContainerId(_container.getId());
        open.setMaxFrameSize(getDesiredMaxFrameSize());
        open.setHostname(getRemoteHostname());


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
        for(int i = 0; i<_sendingSessions.length;i++)
        {
            if(_sendingSessions[i]==null)
            {
                return (short) i;
            }
        }
        return -1;
    }

    private SessionEndpoint getSession(final short channel)
    {
        // TODO assert existence, check channel state
        return _receivingSessions[channel];
    }


    public synchronized void receiveOpen(short channel, Open open)
    {

        _channelMax = open.getChannelMax() == null ? DEFAULT_CHANNEL_MAX
                                                   : open.getChannelMax().shortValue() < DEFAULT_CHANNEL_MAX
                                                        ? DEFAULT_CHANNEL_MAX
                                                        : open.getChannelMax().shortValue();

        UnsignedInteger remoteDesiredMaxFrameSize = open.getMaxFrameSize() == null ? UnsignedInteger.valueOf(DEFAULT_MAX_FRAME) : open.getMaxFrameSize();

        _maxFrameSize = (remoteDesiredMaxFrameSize.compareTo(_desiredMaxFrameSize) < 0 ? remoteDesiredMaxFrameSize : _desiredMaxFrameSize).intValue();

        _remoteContainerId = open.getContainerId();

        switch(_state)
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
    }

    public synchronized void receiveClose(short channel, Close close)
    {
        setClosedForInput(true);
        _connectionEventListener.closeReceived();
        switch(_state)
        {
            case UNOPENED:
            case AWAITING_OPEN:
                Error error = new Error();
                error.setCondition(ConnectionError.CONNECTION_FORCED);
                error.setDescription("Connection close sent before connection was opened");
                connectionError(error);
                break;
            case OPEN:
                sendClose(new Close());
                break;
            case CLOSE_SENT:
            default:
        }
    }

    protected synchronized void connectionError(Error error)
    {
        Close close = new Close();
        close.setError(error);
        switch(_state)
        {
            case UNOPENED:
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

    public synchronized  void inputClosed()
    {
        if(!_closedForInput)
        {
            _closedForInput = true;
            for(int i = 0; i < _receivingSessions.length; i++)
            {
                if(_receivingSessions[i] != null)
                {
                    _receivingSessions[i].end();
                    _receivingSessions[i]=null;

                }
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



        if(begin.getRemoteChannel() != null)
        {
            myChannelId = begin.getRemoteChannel().shortValue();
            SessionEndpoint endpoint;
            try
            {
                endpoint = _sendingSessions[myChannelId];
            }
            catch(IndexOutOfBoundsException e)
            {
                final Error error = new Error();
                error.setCondition(ConnectionError.FRAMING_ERROR);
                error.setDescription("BEGIN received on channel " + channel + " with given remote-channel "
                                     + begin.getRemoteChannel() + " which is outside the valid range of 0 to "
                                     + _channelMax + ".");
                connectionError(error);
                return;
            }
            if(endpoint != null)
            {
                if(_receivingSessions[channel] == null)
                {
                    _receivingSessions[channel] = endpoint;
                    endpoint.setReceivingChannel(channel);
                    endpoint.setNextIncomingId(begin.getNextOutgoingId());
                    endpoint.setOutgoingSessionCredit(begin.getIncomingWindow());
                }
                else
                {
                    final Error error = new Error();
                    error.setCondition(ConnectionError.FRAMING_ERROR);
                    error.setDescription("BEGIN received on channel " + channel + " which is already in use.");
                    connectionError(error);
                }
            }
            else
            {
                final Error error = new Error();
                error.setCondition(ConnectionError.FRAMING_ERROR);
                error.setDescription("BEGIN received on channel " + channel + " with given remote-channel "
                                     + begin.getRemoteChannel() + " which is not known as a begun session.");
                connectionError(error);
            }


        }
        else // Peer requesting session creation
        {

            myChannelId = getFirstFreeChannel();
            if(myChannelId == -1)
            {
                // close any half open channel
                myChannelId = getFirstFreeChannel();

            }

            if(_receivingSessions[channel] == null)
            {
                SessionEndpoint endpoint = new SessionEndpoint(this,begin);

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
                connectionError(error);
            }

        }



    }



    public synchronized void receiveEnd(short channel, End end)
    {
        SessionEndpoint endpoint = _receivingSessions[channel];
        if(endpoint != null)
        {
            _receivingSessions[channel] = null;

            endpoint.end(end);
        }
        else
        {
            // TODO error
        }

    }


    public synchronized void sendEnd(short channel, End end)
    {
        send(channel, end);
        _sendingSessions[channel] = null;
    }

    public synchronized void receiveAttach(short channel, Attach attach)
    {
        SessionEndpoint endPoint = getSession(channel);
        endPoint.receiveAttach(attach);
    }


    public synchronized void receiveDetach(short channel, Detach detach)
    {
        SessionEndpoint endPoint = getSession(channel);
        endPoint.receiveDetach(detach);
    }

    public synchronized void receiveTransfer(short channel, Transfer transfer)
    {
        SessionEndpoint endPoint = getSession(channel);
        endPoint.receiveTransfer(transfer);
    }

    public synchronized void receiveDisposition(short channel, Disposition disposition)
    {
        SessionEndpoint endPoint = getSession(channel);
        endPoint.receiveDisposition(disposition);
    }

    public synchronized void receiveFlow(short channel, Flow flow)
    {
        SessionEndpoint endPoint = getSession(channel);
        endPoint.receiveFlow(flow);
    }


    public synchronized void send(short channel, FrameBody body)
    {
        send(channel, body, null);
    }


    public synchronized int send(short channel, FrameBody body, ByteBuffer payload)
    {
        if(!_closedForOutput)
        {
            ValueWriter<FrameBody> writer = _describedTypeRegistry.getValueWriter(body);
            int size = writer.writeToBuffer(EMPTY_BYTE_BUFFER);
            ByteBuffer payloadDup = payload == null ? null : payload.duplicate();
            int payloadSent = getMaxFrameSize() - (size + 9);
            if(payloadSent < 0)
            {
                System.err.println("What's up?");
            }
            if(payloadSent < (payload == null ? 0 : payload.remaining()))
            {

                if(body instanceof Transfer)
                {
                    ((Transfer)body).setMore(Boolean.TRUE);
                }

                writer = _describedTypeRegistry.getValueWriter(body);
                size = writer.writeToBuffer(EMPTY_BYTE_BUFFER);
                payloadSent = getMaxFrameSize() - (size + 9);

                try
                {
                    payloadDup.limit(payloadDup.position()+payloadSent);
                }
                catch(NullPointerException npe)
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
        // TODO
        _closedForInput = true;
    }

    public synchronized boolean closedForInput()
    {
        return _closedForInput;
    }

    public synchronized void protocolHeaderReceived(final byte major, final byte minorVersion, final byte revision)
    {
        if(_requiresSASLServer && _state != ConnectionState.UNOPENED)
        {
            // TODO - bad stuff
        }

        _majorVersion = major;
        _minorVersion = minorVersion;
        _revision = revision;
    }

    public synchronized void handleError(final Error error)
    {
        if(!closedForOutput())
        {
            Close close = new Close();
            close.setError(error);
            send((short) 0, close);
        }
        _closedForInput = true;
    }

    private final Logger _logger = Logger.getLogger("FRM");

    public synchronized void receive(final short channel, final Object frame)
    {
        if(_logger.isLoggable(Level.FINE))
        {
            _logger.fine("RECV["+ _remoteAddress + "|"+channel+"] : " + frame);
        }
        if(frame instanceof FrameBody)
        {
            ((FrameBody)frame).invoke(channel, this);
        }
        else if(frame instanceof SaslFrameBody)
        {
            ((SaslFrameBody)frame).invoke(this);
        }
    }

    public AMQPDescribedTypeRegistry getDescribedTypeRegistry()
    {
        return _describedTypeRegistry;
    }

    public synchronized void setClosedForOutput(boolean b)
    {
        _closedForOutput = true;
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

    public synchronized void close()
    {
        switch(_state)
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
        Symbol mechanism = saslInit.getMechanism();
        final Binary initialResponse = saslInit.getInitialResponse();
        byte[] response = initialResponse == null ? new byte[0] : initialResponse.getArray();


        try
        {
            _saslServer = Sasl.createSaslServer(mechanism.toString(),
                                                "AMQP",
                                                "localhost",
                                                null,
                                                _callbackHandlersource.getHandler(mechanism.toString()));

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
                    getLock().notifyAll();
                }

                if(_onSaslCompleteTask != null)
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
            if(_onSaslCompleteTask != null)
            {
                _onSaslCompleteTask.run();
            }

        }
    }

    public void receiveSaslMechanisms(final SaslMechanisms saslMechanisms)
    {
        if(Arrays.asList(saslMechanisms.getSaslServerMechanisms()).contains(Symbol.valueOf("PLAIN")))
        {
            SaslInit init = new SaslInit();
            init.setMechanism(Symbol.valueOf("PLAIN"));
            init.setHostname(_remoteHostname);
            byte[] usernameBytes = _user.getName().getBytes(Charset.forName("UTF-8"));
            byte[] passwordBytes = _password.getBytes(Charset.forName("UTF-8"));
            byte[] initResponse = new byte[usernameBytes.length+passwordBytes.length+2];
            System.arraycopy(usernameBytes,0,initResponse,1,usernameBytes.length);
            System.arraycopy(passwordBytes,0,initResponse,usernameBytes.length+2,passwordBytes.length);
            init.setInitialResponse(new Binary(initResponse));
            _saslFrameOutput.send(new SASLFrame(init),null);
        }
    }

    public void receiveSaslChallenge(final SaslChallenge saslChallenge)
    {
        //To change body of implemented methods use File | Settings | File Templates.
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
                        _saslFrameOutput.send(new SASLFrame(outcome),null);
                        synchronized (getLock())
                        {
                            _saslComplete = true;
                            _authenticated = true;
                            getLock().notifyAll();
                        }
                        if(_onSaslCompleteTask != null)
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
                    _saslFrameOutput.send(new SASLFrame(outcome),null);
                    synchronized (getLock())
                    {
                        _saslComplete = true;
                        _authenticated = false;
                        getLock().notifyAll();
                    }
                    if(_onSaslCompleteTask != null)
                    {
                        _onSaslCompleteTask.run();
                    }

                }
        }

    public void receiveSaslOutcome(final SaslOutcome saslOutcome)
    {
        if(saslOutcome.getCode() == SaslCode.OK)
        {
            _saslFrameOutput.close();
            synchronized (getLock())
            {
                _saslComplete = true;
                _authenticated = true;
                getLock().notifyAll();
            }
            if(_onSaslCompleteTask != null)
            {
                _onSaslCompleteTask.run();
            }
        }
        else
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

    public void initiateSASL()
    {
        SaslMechanisms mechanisms = new SaslMechanisms();
        final Enumeration<SaslServerFactory> saslServerFactories = Sasl.getSaslServerFactories();

        SaslServerFactory f;
        ArrayList<Symbol> mechanismsList = new ArrayList<Symbol>();
        while(saslServerFactories.hasMoreElements())
        {
            f = saslServerFactories.nextElement();
            final String[] mechanismNames = f.getMechanismNames(null);
            for(String name : mechanismNames)
            {
                mechanismsList.add(Symbol.valueOf(name));
            }

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
}
