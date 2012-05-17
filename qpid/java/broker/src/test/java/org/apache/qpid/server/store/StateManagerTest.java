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

        _manager.attainState(State.INITIALISING);
        assertEquals(State.INITIALISING, _manager.getState());
    }

    public void testStateTransitionDisallowed()
    {
        assertEquals(State.INITIAL, _manager.getState());

        try
        {
            _manager.attainState(State.CLOSING);
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
        performValidTransition(StateManager.INITIALISE);
        performValidTransition(StateManager.INITALISE_COMPLETE);
        performValidTransition(StateManager.ACTIVATE);
        performValidTransition(StateManager.ACTIVATE_COMPLETE);
        performValidTransition(StateManager.QUIESCE);
        performValidTransition(StateManager.QUIESCE_COMPLETE);
        performValidTransition(StateManager.RESTART);
        performValidTransition(StateManager.ACTIVATE_COMPLETE);
        performValidTransition(StateManager.CLOSE_ACTIVE);
        performValidTransition(StateManager.CLOSE_COMPLETE);

        _manager = new StateManager(this);
        assertEquals(State.INITIAL, _manager.getState());
        performValidTransition(StateManager.INITIALISE);
        performValidTransition(StateManager.INITALISE_COMPLETE);
        performValidTransition(StateManager.CLOSE_INITIALISED);
        performValidTransition(StateManager.CLOSE_COMPLETE);
        
        _manager  = new StateManager(this);
        performValidTransition(StateManager.INITIALISE);
        performValidTransition(StateManager.INITALISE_COMPLETE);
        performValidTransition(StateManager.ACTIVATE);
        performValidTransition(StateManager.ACTIVATE_COMPLETE);
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

        performInvalidTransitions(StateManager.INITIALISE, State.INITIALISED);
        performInvalidTransitions(StateManager.INITALISE_COMPLETE, State.ACTIVATING, State.CLOSING);
        performInvalidTransitions(StateManager.ACTIVATE, State.ACTIVE);
        performInvalidTransitions(StateManager.ACTIVATE_COMPLETE, State.QUIESCING, State.CLOSING, State.INITIALISED);
        performInvalidTransitions(StateManager.QUIESCE, State.QUIESCED);
        performInvalidTransitions(StateManager.QUIESCE_COMPLETE, State.ACTIVATING, State.CLOSING);
        performInvalidTransitions(StateManager.CLOSE_QUIESCED, State.CLOSED);
        performInvalidTransitions(StateManager.CLOSE_COMPLETE);
        
    }

    private void performInvalidTransitions(StateManager.Transition preTransition, State... validEndStates)
    {
        if(preTransition != null)
        {
            performValidTransition(preTransition);
        }
        
        EnumSet<State> endStates = EnumSet.allOf(State.class);

        if(validEndStates != null)
        {
            for(State state: validEndStates)
            {
                endStates.remove(state);
            }
        }
        
        for(State invalidEndState : endStates)
        {
            performInvalidStateTransition(invalidEndState);
        }

        
    }

    private void performInvalidStateTransition(State invalidEndState)
    {
        try
        {
            _event = null;
            State startState = _manager.getState();
            _manager.attainState(invalidEndState);
            fail("Invalid state transition performed: " + startState + " to " + invalidEndState);
        }
        catch(IllegalStateException e)
        {
            // pass
        }
        assertNull("No event should have be fired", _event);
    }

    @Override
    public void event(Event event)
    {
        _event = event;
    }
}
