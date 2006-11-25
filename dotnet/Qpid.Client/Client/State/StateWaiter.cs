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
using System;
using System.Threading;
using log4net;

namespace Qpid.Client.State
{
    public class StateWaiter : IStateListener
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(StateWaiter));

        private readonly AMQState _state;

        private volatile bool _newStateAchieved;

        private volatile Exception _exception;

        private ManualResetEvent _resetEvent = new ManualResetEvent(false);
        
        public StateWaiter(AMQState state)
        {
            _state = state;
        }

        public void StateChanged(AMQState oldState, AMQState newState)
        {            
            if (_logger.IsDebugEnabled)
            {
                _logger.Debug("stateChanged called");
            }
            if (_state == newState)
            {
                _newStateAchieved = true;

                if (_logger.IsDebugEnabled)
                {
                    _logger.Debug("New state reached so notifying monitor");
                }
                _resetEvent.Set();
            }            
        }

        public void Error(Exception e)
        {            
            if (_logger.IsDebugEnabled)
            {
                _logger.Debug("exceptionThrown called");
            }

            _exception = e;
            _resetEvent.Set();
        }
        
        public void WaituntilStateHasChanged()
        {            
            //
            // The guard is required in case we are woken up by a spurious
            // notify().
            //
            while (!_newStateAchieved && _exception == null)
            {                    
                _logger.Debug("State not achieved so waiting...");
                _resetEvent.WaitOne();                    
            }

            if (_exception != null)
            {
                _logger.Debug("Throwable reached state waiter: " + _exception);
                if (_exception is AMQException)
                {
                    throw _exception;
                }
                else
                {
                    throw new AMQException("Error: "  + _exception, _exception);
                }
            }
        }
    }
}
