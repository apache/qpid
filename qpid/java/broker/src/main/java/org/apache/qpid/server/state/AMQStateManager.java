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
package org.apache.qpid.server.state;

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
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The state manager is responsible for managing the state of the protocol session. <p/> For each AMQProtocolHandler
 * there is a separate state manager.
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQStateManager.class);

    private final Broker _broker;
    private final AMQProtocolSession _protocolSession;
    /** The current state */
    private AMQState _currentState;

    private CopyOnWriteArraySet<StateListener> _stateListeners = new CopyOnWriteArraySet<StateListener>();

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

    public AMQState getCurrentState()
    {
        return _currentState;
    }

    public void changeState(AMQState newState) throws AMQException
    {
        _logger.debug("State changing to " + newState + " from old state " + _currentState);
        final AMQState oldState = _currentState;
        _currentState = newState;

        for (StateListener l : _stateListeners)
        {
            l.stateChanged(oldState, newState);
        }
    }

    public void error(Exception e)
    {
        _logger.error("State manager received error notification[Current State:" + _currentState + "]: " + e, e);
        for (StateListener l : _stateListeners)
        {
            l.error(e);
        }
    }

    public <B extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<B> evt) throws AMQException
    {
        MethodDispatcher dispatcher = _protocolSession.getMethodDispatcher();

        final int channelId = evt.getChannelId();
        B body = evt.getMethod();

        if(channelId != 0 && _protocolSession.getChannel(channelId)== null)
        {

            if(! ((body instanceof ChannelOpenBody)
                  || (body instanceof ChannelCloseOkBody)
                  || (body instanceof ChannelCloseBody)))
            {
                throw body.getConnectionException(AMQConstant.CHANNEL_ERROR, "channel is closed won't process:" + body);
            }

        }

        return body.execute(dispatcher, channelId);

    }

    private <B extends AMQMethodBody> void checkChannel(AMQMethodEvent<B> evt, AMQProtocolSession protocolSession)
        throws AMQException
    {
        if ((evt.getChannelId() != 0) && !(evt.getMethod() instanceof ChannelOpenBody)
                && (protocolSession.getChannel(evt.getChannelId()) == null)
                && !protocolSession.channelAwaitingClosure(evt.getChannelId()))
        {
            throw evt.getMethod().getChannelNotFoundException(evt.getChannelId());
        }
    }

    public void addStateListener(StateListener listener)
    {
        _logger.debug("Adding state listener");
        _stateListeners.add(listener);
    }

    public void removeStateListener(StateListener listener)
    {
        _stateListeners.remove(listener);
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _broker.getVirtualHostRegistry();
    }

    public AMQProtocolSession getProtocolSession()
    {
        SecurityManager.setThreadSubject(_protocolSession.getAuthorizedSubject());
        return _protocolSession;
    }

    
    public SubjectCreator getSubjectCreator()
    {
        return _broker.getSubjectCreator(getProtocolSession().getLocalAddress());
    }
}
