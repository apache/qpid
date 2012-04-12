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
package org.apache.qpid.server.store;


import java.util.EnumSet;
import junit.framework.TestCase;

public class StateManagerTest extends TestCase implements EventListener
{

    private StateManager _manager;
    private Event _event;

    public void setUp() throws Exception
    {
        super.setUp();
        _manager = new StateManager(this);
    }

    public void testInitialState()
    {
        assertEquals(State.INITIAL, _manager.getState());
    }

    public void testStateTransitionAllowed()
    {
        assertEquals(State.INITIAL, _manager.getState());

        _manager.stateTransition(State.INITIAL, State.CONFIGURING);
        assertEquals(State.CONFIGURING, _manager.getState());
    }

    public void testStateTransitionDisallowed()
    {
        assertEquals(State.INITIAL, _manager.getState());

        try
        {
            _manager.stateTransition(State.ACTIVE, State.CLOSING);
            fail("Exception not thrown");
        }
        catch (IllegalStateException e)
        {
            // PASS
        }
        assertEquals(State.INITIAL, _manager.getState());
    }

    public void testIsInState()
    {
        assertEquals(State.INITIAL, _manager.getState());
        assertFalse(_manager.isInState(State.ACTIVE));
        assertTrue(_manager.isInState(State.INITIAL));
    }

    public void testIsNotInState()
    {
        assertEquals(State.INITIAL, _manager.getState());
        assertTrue(_manager.isNotInState(State.ACTIVE));
        assertFalse(_manager.isNotInState(State.INITIAL));
    }

    public void testCheckInState()
    {
        assertEquals(State.INITIAL, _manager.getState());

        try
        {
            _manager.checkInState(State.ACTIVE);
            fail("Exception not thrown");
        }
        catch (IllegalStateException e)
        {
            // PASS
        }
        assertEquals(State.INITIAL, _manager.getState());
    }

    public void testValidStateTransitions()
    {
        assertEquals(State.INITIAL, _manager.getState());
        performValidTransition(StateManager.CONFIGURE);
        performValidTransition(StateManager.CONFIGURE_COMPLETE);
        performValidTransition(StateManager.RECOVER);
        performValidTransition(StateManager.ACTIVATE);
        performValidTransition(StateManager.QUIESCE);
        performValidTransition(StateManager.QUIESCE_COMPLETE);
        performValidTransition(StateManager.RESTART);
        performValidTransition(StateManager.ACTIVATE);
        performValidTransition(StateManager.CLOSE_ACTIVE);
        performValidTransition(StateManager.CLOSE_COMPLETE);
        
        _manager  = new StateManager(this);
        performValidTransition(StateManager.CONFIGURE);
        performValidTransition(StateManager.CONFIGURE_COMPLETE);
        performValidTransition(StateManager.RECOVER);
        performValidTransition(StateManager.ACTIVATE);
        performValidTransition(StateManager.QUIESCE);
        performValidTransition(StateManager.QUIESCE_COMPLETE);
        performValidTransition(StateManager.CLOSE_QUIESCED);
        performValidTransition(StateManager.CLOSE_COMPLETE);
    }

    private void performValidTransition(StateManager.Transition transition)
    {
        _manager.attainState(transition.getEndState());
        assertEquals("Unexpected end state", transition.getEndState(), _manager.getState());
        assertEquals("Unexpected event", transition.getEvent(), _event);
        _event = null;
    }

    public void testInvalidStateTransitions()
    {
        assertEquals(State.INITIAL, _manager.getState());


        performInvalidTransitions(StateManager.CONFIGURE, State.CONFIGURED);
        performInvalidTransitions(StateManager.CONFIGURE_COMPLETE, State.RECOVERING);
        performInvalidTransitions(StateManager.RECOVER, State.ACTIVE);
        performInvalidTransitions(StateManager.ACTIVATE, State.QUIESCING, State.CLOSING);
        performInvalidTransitions(StateManager.QUIESCE, State.QUIESCED);
        performInvalidTransitions(StateManager.QUIESCE_COMPLETE, State.RECOVERING, State.CLOSING);
        performInvalidTransitions(StateManager.CLOSE_QUIESCED, State.CLOSED);
        performInvalidTransitions(StateManager.CLOSE_COMPLETE);
        



    }

    private void performInvalidTransitions(StateManager.Transition preTransition, State... validTransitions)
    {
        if(preTransition != null)
        {
            performValidTransition(preTransition);
        }
        
        EnumSet<State> nextStates = EnumSet.allOf(State.class);

        if(validTransitions != null)
        {
            for(State state: validTransitions)
            {
                nextStates.remove(state);
            }
        }
        
        for(State nextState : nextStates)
        {
            performInvalidStateTransition(nextState);
        }

        
    }

    private void performInvalidStateTransition(State state)
    {
        try
        {
            _event = null;
            State startState = _manager.getState();
            _manager.attainState(state);
            fail("Invalid state transition performed: " + startState + " to " + state);
        }
        catch(IllegalStateException e)
        {
            // pass
        }
        assertNull("No event should have be fired", _event);
    }

    public void event(Event event)
    {
        _event = event;
    }
}
