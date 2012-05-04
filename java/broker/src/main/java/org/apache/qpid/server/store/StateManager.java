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


import java.util.EnumMap;
import java.util.Map;

public class StateManager
{
    private State _state = State.INITIAL;
    private EventListener _eventListener;

    private static final Map<State,Map<State, Transition>> _validTransitions = new EnumMap<State, Map<State, Transition>>(State.class);

    
    static class Transition
    {
        private final Event _event;
        private final State _endState;
        private final State _startState;

        public Transition(State startState, State endState, Event event)
        {
            _event = event;
            _startState = startState;
            _endState = endState;

            Map<State, Transition> stateTransitions = _validTransitions.get(startState);
            if(stateTransitions == null)
            {
                stateTransitions = new EnumMap<State, Transition>(State.class);
                _validTransitions.put(startState, stateTransitions);
            }
            stateTransitions.put(endState, this);
        }

        public Event getEvent()
        {
            return _event;
        }

        public State getStartState()
        {
            return _startState;
        }

        public State getEndState()
        {
            return _endState;
        }

    }

    public static final Transition CONFIGURE = new Transition(State.INITIAL, State.CONFIGURING, Event.BEFORE_INIT);
    public static final Transition CONFIGURE_COMPLETE = new Transition(State.CONFIGURING, State.CONFIGURED, Event.AFTER_INIT);
    public static final Transition RECOVER = new Transition(State.CONFIGURED, State.RECOVERING, Event.BEFORE_ACTIVATE);
    public static final Transition ACTIVATE = new Transition(State.RECOVERING, State.ACTIVE, Event.AFTER_ACTIVATE);
    public static final Transition CLOSE_ACTIVE = new Transition(State.ACTIVE, State.CLOSING, Event.BEFORE_CLOSE);
    public static final Transition CLOSE_QUIESCED = new Transition(State.QUIESCED, State.CLOSING, Event.BEFORE_CLOSE);
    public static final Transition CLOSE_COMPLETE = new Transition(State.CLOSING, State.CLOSED, Event.AFTER_CLOSE);
    public static final Transition QUIESCE = new Transition(State.ACTIVE, State.QUIESCING, Event.BEFORE_PASSIVATE);
    public static final Transition QUIESCE_COMPLETE = new Transition(State.QUIESCING, State.QUIESCED, Event.BEFORE_PASSIVATE);
    public static final Transition RESTART = new Transition(State.QUIESCED, State.RECOVERING, Event.BEFORE_ACTIVATE);


    public StateManager(final EventManager eventManager)
    {
        this(new EventListener()
            {
                @Override
                public void event(Event event)
                {
                    eventManager.notifyEvent(event);
                }
            });
    }


    public StateManager(EventListener eventListener)
    {
        _eventListener = eventListener;
    }

    public synchronized State getState()
    {
        return _state;
    }

    public synchronized void stateTransition(final State current, final State desired)
    {
        if (_state != current)
        {
            throw new IllegalStateException("Cannot transition to the state: " + desired + "; need to be in state: " + current
                                   + "; currently in state: " + _state);
        }
        attainState(desired);
    }

    public synchronized void attainState(State desired)
    {
        Transition transition = null;
        final Map<State, Transition> stateTransitionMap = _validTransitions.get(_state);
        if(stateTransitionMap != null)
        {
            transition = stateTransitionMap.get(desired);
        }
        if(transition == null)
        {
            throw new IllegalStateException("No valid transition from state " + _state + " to state " + desired);
        }
        _state = desired;
        _eventListener.event(transition.getEvent());
    }
    
    public synchronized boolean isInState(State testedState)
    {
        return _state.equals(testedState);
    }

    public synchronized boolean isNotInState(State testedState)
    {
        return !isInState(testedState);
    }

    public synchronized void checkInState(State checkedState)
    {
        if (isNotInState(checkedState))
        {
            throw new IllegalStateException("Unexpected state. Was : " + _state + " but expected : " + checkedState);
        }
    }
}
