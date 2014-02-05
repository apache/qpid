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
package org.apache.qpid.server.consumer;

import org.apache.qpid.server.util.StateChangeListener;

import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractConsumerTarget implements ConsumerTarget
{

    private final AtomicReference<State> _state;
    private final AtomicReference<StateChangeListener<ConsumerTarget, State>> _stateListener =
            new AtomicReference<StateChangeListener<ConsumerTarget, State>>();

    protected AbstractConsumerTarget(final State initialState)
    {
        _state = new AtomicReference<State>(initialState);
    }


    public final State getState()
    {
        return _state.get();
    }

    protected final boolean updateState(State from, State to)
    {
        if(_state.compareAndSet(from, to))
        {
            StateChangeListener<ConsumerTarget, State> listener = _stateListener.get();
            if(listener != null)
            {
                listener.stateChanged(this, from, to);
            }
            return true;
        }
        else
        {
            return false;
        }
    }


    public final void setStateListener(StateChangeListener<ConsumerTarget, State> listener)
    {
        _stateListener.set(listener);
    }

    public final StateChangeListener<ConsumerTarget, State> getStateListener()
    {
        return _stateListener.get();
    }

}
