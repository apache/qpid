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

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.handler.*;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.EnumMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The state manager is responsible for managing the state of the protocol session.
 * <p/>
 * For each AMQProtocolHandler there is a separate state manager.
 *
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQStateManager.class);

    private final VirtualHostRegistry _virtualHostRegistry;
    private final AMQProtocolSession _protocolSession;
    /**
     * The current state
     */
    private AMQState _currentState;

    /**
     * Maps from an AMQState instance to a Map from Class to StateTransitionHandler.
     * The class must be a subclass of AMQFrame.
     */
    private final EnumMap<AMQState, Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>> _state2HandlersMap =
            new EnumMap<AMQState, Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>>(AMQState.class);


    private CopyOnWriteArraySet<StateListener> _stateListeners = new CopyOnWriteArraySet<StateListener>();


    public AMQStateManager(VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession)
    {
        this(AMQState.CONNECTION_NOT_STARTED, true, virtualHostRegistry, protocolSession);
    }

    protected AMQStateManager(AMQState initial, boolean register, VirtualHostRegistry virtualHostRegistry, AMQProtocolSession protocolSession)
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
        _logger.error("State manager received error notification: " + e, e);
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

            handler.methodReceived(this,  evt);
            return true;
        }
        return false;
    }

    private <B extends AMQMethodBody> void checkChannel(AMQMethodEvent<B> evt, AMQProtocolSession protocolSession)
            throws AMQException
    {
        if(evt.getChannelId() != 0
                && !(evt.getMethod() instanceof ChannelOpenBody)
                && protocolSession.getChannel(evt.getChannelId()) == null)
        {
            throw evt.getMethod().getConnectionException(AMQConstant.CHANNEL_ERROR.getCode(),"No such channel: " + evt.getChannelId());
        }
    }

    protected <B extends AMQMethodBody> StateAwareMethodListener<B> findStateTransitionHandler(AMQState currentState,
                                                                                             B frame)
            throws IllegalStateTransitionException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Looking for state transition handler for frame " + frame.getClass());
        }
        final Map<Class<? extends AMQMethodBody>, StateAwareMethodListener<? extends AMQMethodBody>>
                classToHandlerMap = _state2HandlersMap.get(currentState);

        final StateAwareMethodListener<B> handler = classToHandlerMap == null
                                                          ? null
                                                          : (StateAwareMethodListener<B>) classToHandlerMap.get(frame.getClass());

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
