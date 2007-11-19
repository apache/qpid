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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for a particular state to be reached.
 */
public class StateWaiter implements StateListener
{
    private static final Logger _logger = LoggerFactory.getLogger(StateWaiter.class);

    private final AMQState _state;

    private volatile boolean _newStateAchieved;

    private volatile Throwable _throwable;

    private final Object _monitor = new Object();
    private static final long TIME_OUT = 1000 * 60 * 2;

    public StateWaiter(AMQState state)
    {
        _state = state;
    }

    public void waituntilStateHasChanged() throws AMQException
    {
        synchronized (_monitor)
        {
            //
            // The guard is required in case we are woken up by a spurious
            // notify().
            //
            while (!_newStateAchieved && (_throwable == null))
            {
                try
                {
                    _logger.debug("State " + _state + " not achieved so waiting...");
                    _monitor.wait(TIME_OUT);
                    // fixme this won't cause the timeout to exit the loop. need to set _throwable
                }
                catch (InterruptedException e)
                {
                    _logger.debug("Interrupted exception caught while waiting: " + e, e);
                }
            }
        }

        if (_throwable != null)
        {
            _logger.debug("Throwable reached state waiter: " + _throwable);
            if (_throwable instanceof AMQException)
            {
                throw (AMQException) _throwable;
            }
            else
            {
                throw new AMQException("Error: " + _throwable, _throwable); // FIXME: this will wrap FailoverException in throwable which will prevent it being caught.
            }
        }
    }

    public void stateChanged(AMQState oldState, AMQState newState)
    {
        synchronized (_monitor)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("stateChanged called changing from :" + oldState + " to :" + newState);
            }

            if (_state == newState)
            {
                _newStateAchieved = true;

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("New state reached so notifying monitor");
                }

                _monitor.notifyAll();
            }
        }
    }

    public void error(Throwable t)
    {
        synchronized (_monitor)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("exceptionThrown called");
            }

            _throwable = t;
            _monitor.notifyAll();
        }
    }
}
