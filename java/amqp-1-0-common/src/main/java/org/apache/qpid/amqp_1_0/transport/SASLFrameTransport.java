/*
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
 */

package org.apache.qpid.amqp_1_0.transport;

import org.apache.qpid.amqp_1_0.framing.SASLFrame;

public class SASLFrameTransport implements FrameTransport<SASLFrame>
{
    private final Object _inputLock = new Object();
    private final Object _outputLock = new Object();

    private volatile boolean _inputOpen;
    private volatile boolean _outputOpen;

    public boolean isOpenForInput()
    {
        return _inputOpen;
    }

    public void closeForInput()
    {
        synchronized(_inputLock)
        {
            _inputOpen = false;
            _inputLock.notifyAll();
        }
    }
    public void processIncomingFrame(final SASLFrame frame)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public SASLFrame getNextFrame()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void closeForOutput()
    {
        synchronized (_outputLock)
        {
            _outputOpen = false;
            _outputLock.notifyAll();
        }
    }

    public boolean isOpenForOutput()
    {
        return _outputOpen;
    }

    public void setInputStateChangeListener(final StateChangeListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setOutputStateChangeListener(final StateChangeListener listener)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
