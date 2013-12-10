/*
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
 */

package org.apache.qpid.amqp_1_0.transport;

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructorRegistry;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.security.SaslChallenge;
import org.apache.qpid.amqp_1_0.type.security.SaslInit;
import org.apache.qpid.amqp_1_0.type.security.SaslMechanisms;
import org.apache.qpid.amqp_1_0.type.security.SaslOutcome;
import org.apache.qpid.amqp_1_0.type.security.SaslResponse;


import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.io.PrintWriter;
import java.util.Arrays;


public class SASLEndpointImpl
        implements DescribedTypeConstructorRegistry.Source, ValueWriter.Registry.Source, SASLEndpoint
{
    private static final short SASL_CONTROL_CHANNEL = (short) 0;

    private static final byte[] EMPTY_CHALLENGE = new byte[0];

    private FrameTransport _transport;


    private static enum State
    {
        BEGIN_SERVER,
        BEGIN_CLIENT,
        SENT_MECHANISMS,
        SENT_INIT,
        SENT_REPSONSE,
        SENT_CHALLENGE,
        SENT_OUTCOME
    };


    public PrintWriter _out;


    private State _state;

    private SaslClient _saslClient;
    private SaslServer _saslServer;

    private boolean _isReadable;
    private boolean _isWritable;
    private boolean _closedForInput;
    private boolean _closedForOutput;

    private AMQPDescribedTypeRegistry _describedTypeRegistry = AMQPDescribedTypeRegistry.newInstance().registerSecurityLayer();

    private FrameOutputHandler _frameOutputHandler;

    private byte _majorVersion;
    private byte _minorVersion;
    private byte _revision;
    private UnsignedInteger _handleMax = UnsignedInteger.MAX_VALUE;
    private ConnectionEventListener _connectionEventListener = ConnectionEventListener.DEFAULT;
    private Symbol[] _mechanisms;
    private Symbol _mechanism;


    private SASLEndpointImpl(FrameTransport transport, State initialState, Symbol... mechanisms)
    {
        _transport = transport;
        _state = initialState;
        _mechanisms = mechanisms;
    }


    public void setFrameOutputHandler(final FrameOutputHandler frameOutputHandler)
    {
        _frameOutputHandler = frameOutputHandler;
        if(_state == State.BEGIN_SERVER)
        {
            sendMechanisms();
        }
    }

    private synchronized void sendMechanisms()
    {
        SaslMechanisms saslMechanisms = new SaslMechanisms();

        saslMechanisms.setSaslServerMechanisms(_mechanisms);

        _state = State.SENT_MECHANISMS;

        send(saslMechanisms);
    }

    public boolean isReadable()
    {
        return _isReadable;
    }

    public boolean isWritable()
    {
        return _isWritable;
    }


    public synchronized void send(SaslFrameBody body)
    {
        if(!_closedForOutput)
        {
            if(_out != null)
            {
                _out.println("SEND : " + body);
                _out.flush();
            }
            //_frameOutputHandler.send(new SASLFrame(body));
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
        _majorVersion = major;
        _minorVersion = minorVersion;
        _revision = revision;
    }


    public synchronized void receive(final short channel, final Object frame)
    {
        if(_out != null)
        {
            _out.println( "RECV["+channel+"] : " + frame);
            _out.flush();
        }
        if(frame instanceof SaslFrameBody)
        {
            ((SaslFrameBody)frame).invoke(this);
        }
        else
        {
            // TODO
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


    public byte getMajorVersion()
    {
        return _majorVersion;
    }


    public byte getMinorVersion()
    {
        return _minorVersion;
    }

    public byte getRevision()
    {
        return _revision;
    }


    public void receiveSaslInit(final SaslInit saslInit)
    {
        _mechanism = saslInit.getMechanism();
        try
        {
            _saslServer = Sasl.createSaslServer(_mechanism.toString(), "AMQP", "localhost", null,  createServerCallbackHandler(_mechanism));
        }
        catch (SaslException e)
        {
            e.printStackTrace();  //TODO
        }
    }

    private CallbackHandler createServerCallbackHandler(final Symbol mechanism)
    {
        return null;  //TODO
    }

    public synchronized void receiveSaslMechanisms(final SaslMechanisms saslMechanisms)
    {
        Symbol[] serverMechanisms = saslMechanisms.getSaslServerMechanisms();
        for(Symbol mechanism : _mechanisms)
        {
            if(Arrays.asList(serverMechanisms).contains(mechanism))
            {
                _mechanism = mechanism;
                break;
            }
        }
        // TODO - no matching mechanism
        try
        {
            _saslClient = Sasl.createSaslClient(new String[] { _mechanism.toString() }, null, "AMQP", "localhost", null,
                                createClientCallbackHandler(_mechanism));
            SaslInit init = new SaslInit();
            init.setMechanism(_mechanism);
            init.setInitialResponse(_saslClient.hasInitialResponse() ? new Binary(_saslClient.evaluateChallenge(EMPTY_CHALLENGE)) : null);
            send(init);
        }
        catch (SaslException e)
        {
            e.printStackTrace();  //TODO
        }
    }

    private CallbackHandler createClientCallbackHandler(final Symbol mechanism)
    {
        return null;  //TODO
    }

    public void receiveSaslChallenge(final SaslChallenge saslChallenge)
    {
        //TODO
    }

    public void receiveSaslResponse(final SaslResponse saslResponse)
    {
        //TODO
    }


    public void receiveSaslOutcome(final SaslOutcome saslOutcome)
    {
        //TODO
    }



}
