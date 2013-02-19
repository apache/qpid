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
package org.apache.qpid.server.configuration.updater;

import java.util.concurrent.Callable;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;

public final class ChangeStateTask implements Callable<State>
{
    private ConfiguredObject _object;
    private State _expectedState;
    private State _desiredState;

    public ChangeStateTask(ConfiguredObject object, State expectedState, State desiredState)
    {
        _object = object;
        _expectedState = expectedState;
        _desiredState = desiredState;
    }

    public ConfiguredObject getObject()
    {
        return _object;
    }

    public State getExpectedState()
    {
        return _expectedState;
    }

    public State getDesiredState()
    {
        return _desiredState;
    }

    @Override
    public State call()
    {
        return _object.setDesiredState(_expectedState, _desiredState);
    }

    @Override
    public String toString()
    {
        return "ChangeStateTask [object=" + _object + ", expectedState=" + _expectedState + ", desiredState=" + _desiredState + "]";
    }
}
