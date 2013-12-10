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
package org.apache.qpid.server.flow;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractFlowCreditManager implements FlowCreditManager
{
    private final AtomicBoolean _suspended = new AtomicBoolean(false);
    private final ArrayList<FlowCreditManagerListener> _listeners = new ArrayList<FlowCreditManagerListener>();

    public final void addStateListener(FlowCreditManagerListener listener)
    {
        synchronized(_listeners)
        {
            if(!_listeners.contains(listener))
            {
                _listeners.add(listener);
            }
        }
    }

    public final boolean removeListener(FlowCreditManagerListener listener)
    {
        synchronized(_listeners)
        {
            return _listeners.remove(listener);
        }
    }

    private void notifyListeners(final boolean suspended)
    {
        synchronized(_listeners)
        {
            final int size = _listeners.size();
            for(int i = 0; i<size; i++)
            {
                _listeners.get(i).creditStateChanged(!suspended);
            }
        }
    }

    protected final void setSuspended(final boolean suspended)
    {
        if(_suspended.compareAndSet(!suspended, suspended))
        {
            notifyListeners(suspended);
        }
    }

    protected final void notifyIncreaseBytesCredit()
    {
        notifyListeners(false);
    }
}
