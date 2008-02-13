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
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The state manager is responsible for managing the state of the protocol session. <p/> For each AMQProtocolHandler
 * there is a separate state manager.
 */
public class AMQStateManager implements AMQMethodListener
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQStateManager.class);

    private AMQProtocolSession _protocolSession;

    /** The current state */
    private AMQState _currentState;


    /**
     * Maps from an AMQState instance to a Map from Class to StateTransitionHandler. The class must be a subclass of
     * AMQFrame.
     */

    private final CopyOnWriteArraySet _stateListeners = new CopyOnWriteArraySet();
    private final Object _stateLock = new Object();
    private static final long MAXIMUM_STATE_WAIT_TIME = Long.parseLong(System.getProperty("amqj.MaximumStateWait", "30000"));

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

        B method = evt.getMethod();
        
        //    StateAwareMethodListener handler = findStateTransitionHandler(_currentState, evt.getMethod());
        method.execute(_protocolSession.getMethodDispatcher(), evt.getChannelId());
        return true;
    }


    public void attainState(final AMQState s) throws AMQException
    {
        synchronized (_stateLock)
        {
            final long waitUntilTime = System.currentTimeMillis() + MAXIMUM_STATE_WAIT_TIME;
            long waitTime = MAXIMUM_STATE_WAIT_TIME;

            while ((_currentState != s) && (waitTime > 0))
            {
                try
                {
                    _stateLock.wait(MAXIMUM_STATE_WAIT_TIME);
                }
                catch (InterruptedException e)
                {
                    _logger.warn("Thread interrupted");
                }

                if (_currentState != s)
                {
                    waitTime = waitUntilTime - System.currentTimeMillis();
                }
            }

            if (_currentState != s)
            {
                _logger.warn("State not achieved within permitted time.  Current state " + _currentState
                             + ", desired state: " + s);
                throw new AMQException(null, "State not achieved within permitted time.  Current state " + _currentState
                                             + ", desired state: " + s, null);
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

    public MethodRegistry getMethodRegistry()
    {
        return getProtocolSession().getMethodRegistry();
    }

    public AMQState attainState(Set<AMQState> stateSet) throws AMQException
    {
        synchronized (_stateLock)
        {
            final long waitUntilTime = System.currentTimeMillis() + MAXIMUM_STATE_WAIT_TIME;
            long waitTime = MAXIMUM_STATE_WAIT_TIME;

            while (!stateSet.contains(_currentState) && (waitTime > 0))
            {
                try
                {
                    _stateLock.wait(MAXIMUM_STATE_WAIT_TIME);
                }
                catch (InterruptedException e)
                {
                    _logger.warn("Thread interrupted");
                }

                if (!stateSet.contains(_currentState))
                {
                    waitTime = waitUntilTime - System.currentTimeMillis();
                }
            }

            if (!stateSet.contains(_currentState))
            {
                _logger.warn("State not achieved within permitted time.  Current state " + _currentState
                             + ", desired state: " + stateSet);
                throw new AMQException(null, "State not achieved within permitted time.  Current state " + _currentState
                                       + ", desired state: " + stateSet, null);
            }
            return _currentState;
        }


    }
}
