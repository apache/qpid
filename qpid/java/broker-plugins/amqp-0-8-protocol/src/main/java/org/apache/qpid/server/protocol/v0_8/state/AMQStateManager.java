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
package org.apache.qpid.server.protocol.v0_8.state;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.MethodDispatcher;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

/**
 * The state manager is responsible for managing the state of the protocol session.
 * <p>
 * For each AMQProtocolHandler there is a separate state manager.
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQStateManager.class);

    private final Broker _broker;
    private final AMQProtocolSession _protocolSession;
    /** The current state */
    private AMQState _currentState;

    public AMQStateManager(Broker broker, AMQProtocolSession protocolSession)
    {
        _broker = broker;
        _protocolSession = protocolSession;
        _currentState = AMQState.CONNECTION_NOT_STARTED;

    }

    /**
     * Get the Broker instance
     *
     * @return the Broker
     */
    public Broker getBroker()
    {
        return _broker;
    }

    public void changeState(AMQState newState)
    {
        _logger.debug("State changing to " + newState + " from old state " + _currentState);
        final AMQState oldState = _currentState;
        _currentState = newState;

    }

    public void error(Exception e)
    {
        _logger.error("State manager received error notification[Current State:" + _currentState + "]: " + e, e);
    }

    public <B extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<B> evt) throws AMQException
    {
        final MethodDispatcher dispatcher = _protocolSession.getMethodDispatcher();

        final int channelId = evt.getChannelId();
        final B body = evt.getMethod();

        final AMQChannel channel = _protocolSession.getChannel(channelId);
        if(channelId != 0 && channel == null)
        {

            if(! ((body instanceof ChannelOpenBody)
                  || (body instanceof ChannelCloseOkBody)
                  || (body instanceof ChannelCloseBody)))
            {
                throw body.getConnectionException(AMQConstant.CHANNEL_ERROR, "channel is closed won't process:" + body);
            }

        }
        if(channel == null)
        {
            return body.execute(dispatcher, channelId);
        }
        else
        {
            try
            {
                return Subject.doAs(channel.getSubject(), new PrivilegedExceptionAction<Boolean>()
                {
                    @Override
                    public Boolean run() throws AMQException
                    {
                        return body.execute(dispatcher, channelId);
                    }
                });
            }
            catch (PrivilegedActionException e)
            {
                if(e.getCause() instanceof AMQException)
                {
                    throw (AMQException) e.getCause();
                }
                else
                {
                    throw new ServerScopedRuntimeException(e.getCause());
                }
            }


        }

    }

    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }


    public SubjectCreator getSubjectCreator()
    {
        return _broker.getSubjectCreator(getProtocolSession().getLocalAddress(), getProtocolSession().getTransport().isSecure());
    }
}
