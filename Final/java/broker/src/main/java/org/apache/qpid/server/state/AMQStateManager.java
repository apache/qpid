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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.BasicAckBody;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.BasicGetBody;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.BasicQosBody;
import org.apache.qpid.framing.BasicRecoverBody;
import org.apache.qpid.framing.BasicRejectBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.framing.ExchangeBoundBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.TxCommitBody;
import org.apache.qpid.framing.TxRollbackBody;
import org.apache.qpid.framing.TxSelectBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.server.handler.BasicAckMethodHandler;
import org.apache.qpid.server.handler.BasicCancelMethodHandler;
import org.apache.qpid.server.handler.BasicConsumeMethodHandler;
import org.apache.qpid.server.handler.BasicGetMethodHandler;
import org.apache.qpid.server.handler.BasicPublishMethodHandler;
import org.apache.qpid.server.handler.BasicQosHandler;
import org.apache.qpid.server.handler.BasicRecoverMethodHandler;
import org.apache.qpid.server.handler.BasicRejectMethodHandler;
import org.apache.qpid.server.handler.ChannelCloseHandler;
import org.apache.qpid.server.handler.ChannelCloseOkHandler;
import org.apache.qpid.server.handler.ChannelFlowHandler;
import org.apache.qpid.server.handler.ChannelOpenHandler;
import org.apache.qpid.server.handler.ConnectionCloseMethodHandler;
import org.apache.qpid.server.handler.ConnectionCloseOkMethodHandler;
import org.apache.qpid.server.handler.ConnectionOpenMethodHandler;
import org.apache.qpid.server.handler.ConnectionSecureOkMethodHandler;
import org.apache.qpid.server.handler.ConnectionStartOkMethodHandler;
import org.apache.qpid.server.handler.ConnectionTuneOkMethodHandler;
import org.apache.qpid.server.handler.ExchangeBoundHandler;
import org.apache.qpid.server.handler.ExchangeDeclareHandler;
import org.apache.qpid.server.handler.ExchangeDeleteHandler;
import org.apache.qpid.server.handler.QueueBindHandler;
import org.apache.qpid.server.handler.QueueDeclareHandler;
import org.apache.qpid.server.handler.QueueDeleteHandler;
import org.apache.qpid.server.handler.QueuePurgeHandler;
import org.apache.qpid.server.handler.TxCommitHandler;
import org.apache.qpid.server.handler.TxRollbackHandler;
import org.apache.qpid.server.handler.TxSelectHandler;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

/**
 * The state manager is responsible for managing the state of the protocol session. <p/> For each AMQProtocolHandler
 * there is a separate state manager.
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQStateManager.class);

    private final VirtualHostRegistry _virtualHostRegistry;
    private final AMQProtocolSession _protocolSession;
    /** The current state */
    private AMQState _currentState;

    /**
     * Maps from an AMQState instance to a Map from Class to StateTransitionHandler. The class must be a subclass of
     * AMQFrame.
     */
    private final EnumMap<AMQState, Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>> _state2HandlersMap =
        new EnumMap<AMQState, Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>>(
            AMQState.class);

    private CopyOnWriteArraySet<StateListener> _stateListeners = new CopyOnWriteArraySet<StateListener>();

    public AMQStateManager(VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession)
    {
        this(AMQState.CONNECTION_NOT_STARTED, true, virtualHostRegistry, protocolSession);
    }

    protected AMQStateManager(AMQState initial, boolean register, VirtualHostRegistry virtualHostRegistry,
        AMQProtocolSession protocolSession)
    {
        _virtualHostRegistry = virtualHostRegistry;
        _protocolSession = protocolSession;
        _currentState = initial;
        if (register)
        {
            registerListeners();
        }
    }

    protected void registerListeners()
    {
        Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>> frame2handlerMap;

        frame2handlerMap = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();
        frame2handlerMap.put(ConnectionStartOkBody.class, ConnectionStartOkMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_STARTED, frame2handlerMap);

        frame2handlerMap = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();
        frame2handlerMap.put(ConnectionSecureOkBody.class, ConnectionSecureOkMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_AUTH, frame2handlerMap);

        frame2handlerMap = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();
        frame2handlerMap.put(ConnectionTuneOkBody.class, ConnectionTuneOkMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_TUNED, frame2handlerMap);

        frame2handlerMap = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();
        frame2handlerMap.put(ConnectionOpenBody.class, ConnectionOpenMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_OPENED, frame2handlerMap);

        //
        // ConnectionOpen handlers
        //
        frame2handlerMap = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();
        frame2handlerMap.put(ChannelOpenBody.class, ChannelOpenHandler.getInstance());
        frame2handlerMap.put(ChannelCloseBody.class, ChannelCloseHandler.getInstance());
        frame2handlerMap.put(ChannelCloseOkBody.class, ChannelCloseOkHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        frame2handlerMap.put(ExchangeDeclareBody.class, ExchangeDeclareHandler.getInstance());
        frame2handlerMap.put(ExchangeDeleteBody.class, ExchangeDeleteHandler.getInstance());
        frame2handlerMap.put(ExchangeBoundBody.class, ExchangeBoundHandler.getInstance());
        frame2handlerMap.put(BasicAckBody.class, BasicAckMethodHandler.getInstance());
        frame2handlerMap.put(BasicRecoverBody.class, BasicRecoverMethodHandler.getInstance());
        frame2handlerMap.put(BasicConsumeBody.class, BasicConsumeMethodHandler.getInstance());
        frame2handlerMap.put(BasicGetBody.class, BasicGetMethodHandler.getInstance());
        frame2handlerMap.put(BasicCancelBody.class, BasicCancelMethodHandler.getInstance());
        frame2handlerMap.put(BasicPublishBody.class, BasicPublishMethodHandler.getInstance());
        frame2handlerMap.put(BasicQosBody.class, BasicQosHandler.getInstance());
        frame2handlerMap.put(QueueBindBody.class, QueueBindHandler.getInstance());
        frame2handlerMap.put(QueueDeclareBody.class, QueueDeclareHandler.getInstance());
        frame2handlerMap.put(QueueDeleteBody.class, QueueDeleteHandler.getInstance());
        frame2handlerMap.put(QueuePurgeBody.class, QueuePurgeHandler.getInstance());
        frame2handlerMap.put(ChannelFlowBody.class, ChannelFlowHandler.getInstance());
        frame2handlerMap.put(TxSelectBody.class, TxSelectHandler.getInstance());
        frame2handlerMap.put(TxCommitBody.class, TxCommitHandler.getInstance());
        frame2handlerMap.put(TxRollbackBody.class, TxRollbackHandler.getInstance());
        frame2handlerMap.put(BasicRejectBody.class, BasicRejectMethodHandler.getInstance());

        _state2HandlersMap.put(AMQState.CONNECTION_OPEN, frame2handlerMap);

        frame2handlerMap = new HashMap<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>();
        frame2handlerMap.put(ConnectionCloseOkBody.class, ConnectionCloseOkMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_CLOSING, frame2handlerMap);

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
        StateAwareMethodListener<B> handler = findStateTransitionHandler(_currentState, evt.getMethod());
        if (handler != null)
        {

            checkChannel(evt, _protocolSession);

            handler.methodReceived(this, evt);

            return true;
        }

        return false;
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

    protected <B extends AMQMethodBody> StateAwareMethodListener<B> findStateTransitionHandler(AMQState currentState,
        B frame)
    // throws IllegalStateTransitionException
    {
        final Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>> classToHandlerMap =
            _state2HandlersMap.get(currentState);

        final StateAwareMethodListener<B> handler =
            (classToHandlerMap == null) ? null : (StateAwareMethodListener<B>) classToHandlerMap.get(frame.getClass());

        if (handler == null)
        {
            _logger.debug("No state transition handler defined for receiving frame " + frame);

            return null;
        }
        else
        {
            return handler;
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
        return _virtualHostRegistry;
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }
}
