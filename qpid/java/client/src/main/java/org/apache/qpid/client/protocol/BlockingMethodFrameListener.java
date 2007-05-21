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
package org.apache.qpid.client.protocol;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;

public abstract class BlockingMethodFrameListener implements AMQMethodListener
{
    private volatile boolean _ready = false;

    public abstract boolean processMethod(int channelId, AMQMethodBody frame) throws AMQException;

    private final Object _lock = new Object();

    /**
     * This is set if there is an exception thrown from processCommandFrame and the
     * exception is rethrown to the caller of blockForFrame()
     */
    private volatile Exception _error;

    protected int _channelId;

    protected AMQMethodEvent _doneEvt = null;

    public BlockingMethodFrameListener(int channelId)
    {
        _channelId = channelId;
    }

    /**
     * This method is called by the MINA dispatching thread. Note that it could
     * be called before blockForFrame() has been called.
     *
     * @param evt the frame event
     * @return true if the listener has dealt with this frame
     * @throws AMQException
     */
    public boolean methodReceived(AMQMethodEvent evt) throws AMQException
    {
        AMQMethodBody method = evt.getMethod();

        try
        {
            boolean ready = (evt.getChannelId() == _channelId) && processMethod(evt.getChannelId(), method);
            if (ready)
            {
                // we only update the flag from inside the synchronized block
                // so that the blockForFrame method cannot "miss" an update - it
                // will only ever read the flag from within the synchronized block
                synchronized (_lock)
                {
                    _doneEvt = evt;
                    _ready = ready;
                    _lock.notify();
                }
            }
            return ready;
        }
        catch (AMQException e)
        {
            error(e);
            // we rethrow the error here, and the code in the frame dispatcher will go round
            // each listener informing them that an exception has been thrown
            throw e;
        }
    }

    /**
     * This method is called by the thread that wants to wait for a frame.
     */
    public AMQMethodEvent blockForFrame(long timeout) throws AMQException
    {
        synchronized (_lock)
        {
            while (!_ready)
            {
                try
                {
                    if (timeout == -1)
                    {
                        _lock.wait();
                    }
                    else
                    {

                        _lock.wait(timeout);
                        if (!_ready)
                        {
                            _error = new AMQTimeoutException("Server did not respond in a timely fashion", null);
                            _ready = true;
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    // IGNORE    -- //fixme this isn't ideal as being interrupted isn't equivellant to sucess
//                    if (!_ready && timeout != -1)
//                    {
//                        _error = new AMQException("Server did not respond timely");
//                        _ready = true;
//                    }
                }
            }
        }
        if (_error != null)
        {
            if (_error instanceof AMQException)
            {
                throw(AMQException) _error;
            }
            else if (_error instanceof FailoverException)
            {
                // This should ensure that FailoverException is not wrapped and can be caught.                
                throw(FailoverException) _error;  // needed to expose FailoverException.
            }
            else
            {
                throw new AMQException(null, "Woken up due to " + _error.getClass(), _error);
            }
        }

        return _doneEvt;
    }

    /**
     * This is a callback, called by the MINA dispatcher thread only. It is also called from within this
     * class to avoid code repetition but again is only called by the MINA dispatcher thread.
     *
     * @param e
     */
    public void error(Exception e)
    {
        // set the error so that the thread that is blocking (against blockForFrame())
        // can pick up the exception and rethrow to the caller
        _error = e;
        synchronized (_lock)
        {
            _ready = true;
            _lock.notify();
        }
    }
}
