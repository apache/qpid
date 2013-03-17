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
package org.apache.qpid.amqp_1_0.codec;

import org.apache.qpid.amqp_1_0.framing.AMQPProtocolHeaderHandler;
import org.apache.qpid.amqp_1_0.framing.SASLProtocolHeaderHandler;

import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;

import java.nio.ByteBuffer;

public class ProtocolHeaderHandler implements ProtocolHandler
{
    private final ConnectionEndpoint _connection;
    private ProtocolHandler[] _protocolHandlers = new ProtocolHandler[4];
    private boolean _done;

    enum State {
        AWAITING_A,
        AWAITING_M,
        AWAITING_Q,
        AWAITING_P,
        AWAITING_PROTOCOL_ID,
        ERROR
    }

    private State _state = State.AWAITING_A;

    public ProtocolHeaderHandler(final ConnectionEndpoint connection)
    {
        _connection = connection;
        _protocolHandlers[0] = new AMQPProtocolHeaderHandler(connection);
        _protocolHandlers[1] = null ; // historic apache.qpid.amqp_1_0
        _protocolHandlers[2] = null ; // TLS
        _protocolHandlers[3] = new SASLProtocolHeaderHandler(connection); // SASL


    }

    public ProtocolHandler parse(final ByteBuffer in)
    {
        if(!in.hasRemaining())
        {
            return this;
        }

        switch(_state)
        {
            case AWAITING_A:
                if(transition(in, (byte)'A', State.AWAITING_M))
                {
                    break;
                }
            case AWAITING_M:
                if(transition(in, (byte)'M', State.AWAITING_Q))
                {
                    break;
                }
            case AWAITING_Q:
                if(transition(in, (byte)'Q', State.AWAITING_P))
                {
                    break;
                }

            case AWAITING_P:
                if(transition(in, (byte)'P', State.AWAITING_PROTOCOL_ID))
                {
                    break;
                }
            case AWAITING_PROTOCOL_ID:
                int protocolId = ((int) in.get()) & 0xff;
                ProtocolHandler delegate;

                try
                {
                    delegate = _protocolHandlers[protocolId];
                }
                catch(IndexOutOfBoundsException e)
                {
                    delegate = null;
                }

                if(delegate == null)
                {
                    _state = State.ERROR;
                }
                else
                {
                    return delegate.parse(in);
                }
        }
        if(_state == State.ERROR)
        {
            _connection.invalidHeaderReceived();
            _done = true;
        }
        return this;

    }

    boolean transition(ByteBuffer in, byte expected, State next)
    {
        byte b = in.get();
        if(b == expected)
        {
            if(!in.hasRemaining())
            {
                _state =  next;
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            _state = State.ERROR;
            return true;
        }
    }


    public boolean isDone()
    {
        return _done;
    }
}
