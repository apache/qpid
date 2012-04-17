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


public class StateManager
{
    private State _state = State.INITIAL;

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
        _state = desired;
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
