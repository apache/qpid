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
package org.apache.qpid.client.state;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.client.handler.*;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.*;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The state manager is responsible for managing the state of the protocol session.
 * <p/>
 * For each AMQProtocolHandler there is a separate state manager.
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQStateManager.class);
    private AMQProtocolSession _protocolSession;

    /**
     * The current state
     */
    private AMQState _currentState;

    /**
     * Maps from an AMQState instance to a Map from Class to StateTransitionHandler.
     * The class must be a subclass of AMQFrame.
     */
    private final Map _state2HandlersMap = new HashMap();

    private final CopyOnWriteArraySet _stateListeners = new CopyOnWriteArraySet();

    private final Object _stateLock = new Object();
    private static final long MAXIMUM_STATE_WAIT_TIME = 30000l;

    public AMQStateManager()
    {
        this(null);
    }
    

    public AMQStateManager(AMQProtocolSession protocolSession)
    {
        this(AMQState.CONNECTION_NOT_STARTED, true, protocolSession);
    }

    protected AMQStateManager(AMQState state, boolean register, AMQProtocolSession protocolSession)
    {
        _protocolSession = protocolSession;
        _currentState = state;
        if(register)
        {
            registerListeners();
        }
    }

    protected void registerListeners()
    {
        Map frame2handlerMap = new HashMap();

        // we need to register a map for the null (i.e. all state) handlers otherwise you get
        // a stack overflow in the handler searching code when you present it with a frame for which
        // no handlers are registered
        //
        _state2HandlersMap.put(null, frame2handlerMap);

        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ConnectionStartBody.class, ConnectionStartMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_STARTED, frame2handlerMap);

        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ConnectionTuneBody.class, ConnectionTuneMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionSecureBody.class, ConnectionSecureMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_TUNED, frame2handlerMap);

        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ConnectionOpenOkBody.class, ConnectionOpenOkMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_NOT_OPENED, frame2handlerMap);

        //
        // ConnectionOpen handlers
        //
        frame2handlerMap = new HashMap();
        frame2handlerMap.put(ChannelCloseBody.class, ChannelCloseMethodHandler.getInstance());
        frame2handlerMap.put(ChannelCloseOkBody.class, ChannelCloseOkMethodHandler.getInstance());
        frame2handlerMap.put(ConnectionCloseBody.class, ConnectionCloseMethodHandler.getInstance());
        frame2handlerMap.put(MessageAppendBody.class, MessageAppendMethodHandler.getInstance());
        frame2handlerMap.put(MessageCheckpointBody.class, MessageCheckpointMethodHandler.getInstance());
        frame2handlerMap.put(MessageCloseBody.class, MessageCloseMethodHandler.getInstance());
        frame2handlerMap.put(MessageEmptyBody.class, MessageEmptyMethodHandler.getInstance());
        frame2handlerMap.put(MessageOffsetBody.class, MessageOffsetMethodHandler.getInstance());
        frame2handlerMap.put(MessageOkBody.class, MessageOkMethodHandler.getInstance());
        frame2handlerMap.put(MessageOpenBody.class, MessageOpenMethodHandler.getInstance());
        frame2handlerMap.put(MessageRejectBody.class, MessageRejectMethodHandler.getInstance());
        frame2handlerMap.put(MessageResumeBody.class, MessageResumeMethodHandler.getInstance());
        frame2handlerMap.put(MessageTransferBody.class, MessageTransferMethodHandler.getInstance());
        frame2handlerMap.put(ChannelFlowOkBody.class, ChannelFlowOkMethodHandler.getInstance());
        frame2handlerMap.put(QueueDeleteOkBody.class, QueueDeleteOkMethodHandler.getInstance());
        frame2handlerMap.put(ExchangeBoundOkBody.class, ExchangeBoundOkMethodHandler.getInstance());
        _state2HandlersMap.put(AMQState.CONNECTION_OPEN, frame2handlerMap);
    }

    public AMQState getCurrentState()
    {
        return _currentState;
    }

    public void changeState(AMQState newState) throws AMQException
    {
        _logger.debug("State changing to " + newState + " from old state " + _currentState);

        synchronized (_stateLock)
        {
            _currentState = newState;
            _stateLock.notifyAll();
        }
    }

    public void error(Exception e)
    {
        _logger.debug("State manager receive error notification: " + e);
        synchronized (_stateListeners)
        {
            final Iterator it = _stateListeners.iterator();
            while (it.hasNext())
            {
                final StateListener l = (StateListener) it.next();
                l.error(e);
            }
        }
    }

    public <B extends AMQMethodBody> boolean methodReceived(AMQMethodEvent<B> evt) throws AMQException
    {
        StateAwareMethodListener handler = findStateTransitionHandler(_currentState, evt.getMethod());
        if (handler != null)
        {
            handler.methodReceived(this, _protocolSession, evt);
            return true;
        }
        return false;
    }

    protected StateAwareMethodListener findStateTransitionHandler(AMQState currentState,
                                                                  AMQMethodBody frame)
            throws IllegalStateTransitionException
    {
        final Class clazz = frame.getClass();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Looking for state transition handler for frame " + clazz);
        }
        final Map classToHandlerMap = (Map) _state2HandlersMap.get(currentState);

        if (classToHandlerMap == null)
        {
            // if no specialised per state handler is registered look for a
            // handler registered for "all" states
            return findStateTransitionHandler(null, frame);
        }
        final StateAwareMethodListener handler = (StateAwareMethodListener) classToHandlerMap.get(clazz);
        if (handler == null)
        {
            if (currentState == null)
            {
                _logger.debug("No state transition handler defined for receiving frame " + frame);
                return null;
            }
            else
            {
                // if no specialised per state handler is registered look for a
                // handler registered for "all" states
                return findStateTransitionHandler(null, frame);
            }
        }
        else
        {
            return handler;
        }
    }


    public void attainState(final AMQState s) throws AMQException
    {
        synchronized(_stateLock)
        {
            final long waitUntilTime = System.currentTimeMillis() + MAXIMUM_STATE_WAIT_TIME;
            long waitTime = MAXIMUM_STATE_WAIT_TIME;

            while(_currentState != s && waitTime > 0)
            {
                try
                {
                    _stateLock.wait(MAXIMUM_STATE_WAIT_TIME);
                }
                catch (InterruptedException e)
                {
                    _logger.warn("Thread interrupted");
                }
                if(_currentState != s)
                {
                    waitTime = waitUntilTime - System.currentTimeMillis();
                }
            }
            if(_currentState != s)
            {
                throw new AMQException("State not achieved within permitted time.  Current state " + _currentState + ", desired state: " + s);
            }
        }

        // at this point the state will have changed.
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _protocolSession;
    }

    public void setProtocolSession(AMQProtocolSession session)
    {
        _protocolSession = session;
    }
}
