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
package org.apache.qpid.client.util;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * A {@link BlockingQueue} that emits events above a user specified threshold allowing the caller to take action (e.g. flow
 * control) to try to prevent the queue growing (much) further. The underlying queue itself is not bounded therefore the
 * caller is not obliged to react to the events.
 */
public class FlowControllingBlockingQueue<E> extends LinkedBlockingQueue<E>
{
    private final int _flowControlHighThreshold;
    private final int _flowControlLowThreshold;

    private final ThresholdListener _listener;
    
    private boolean _disableFlowControl; 

    public interface ThresholdListener
    {
        void aboveThreshold(int currentValue);

        void underThreshold(int currentValue);
    }
    
    public FlowControllingBlockingQueue()
    {
        this(0, null);
    }

    public FlowControllingBlockingQueue(int threshold, ThresholdListener listener)
    {
        this(threshold, threshold, listener);
    }

    public FlowControllingBlockingQueue(int highThreshold, int lowThreshold, ThresholdListener listener)
    {
        super();
        
        _flowControlHighThreshold = highThreshold;
        _flowControlLowThreshold = lowThreshold;
        _listener = listener;
        
        if (highThreshold == 0)
        {
        	_disableFlowControl = true;
        }
    }

    public E take() throws InterruptedException
    {
        E e = super.take();
        
        if (!_disableFlowControl && _listener != null)
        {
            synchronized (_listener)
            {
                if (size() == _flowControlLowThreshold)
                {
                    _listener.underThreshold(size());
                }
            }
            
        }

        return e;
    }

    public boolean add(E e)
    {
        super.add(e);
        
        if (!_disableFlowControl && _listener != null)
        {
            synchronized (_listener)
            {
                if (size() == _flowControlHighThreshold)
                {
                    _listener.aboveThreshold(size());
                }
            }
        }
        
        return true;
    }
}
