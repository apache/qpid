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

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A blocking queue that emits events above a user specified threshold allowing the caller to take action (e.g. flow
 * control) to try to prevent the queue growing (much) further. The underlying queue itself is not bounded therefore the
 * caller is not obliged to react to the events. <p/> This implementation is <b>only</b> safe where we have a single
 * thread adding items and a single (different) thread removing items.
 *
 * @todo Make this implement java.util.Queue and hide the implementation. Then different queue types can be substituted.
 */
public class FlowControllingBlockingQueue
{
    /** This queue is bounded and is used to store messages before being dispatched to the consumer */
    private final BlockingQueue _queue = new LinkedBlockingQueue();

    private final int _flowControlHighThreshold;
    private final int _flowControlLowThreshold;

    private final ThresholdListener _listener;

    /** We require a separate count so we can track whether we have reached the threshold */
    private int _count;

    public boolean isEmpty()
    {
        return _queue.isEmpty();
    }

    public interface ThresholdListener
    {
        void aboveThreshold(int currentValue);

        void underThreshold(int currentValue);
    }

    public FlowControllingBlockingQueue(int threshold, ThresholdListener listener)
    {
        this(threshold, threshold, listener);
    }

    public FlowControllingBlockingQueue(int highThreshold, int lowThreshold, ThresholdListener listener)
    {
        _flowControlHighThreshold = highThreshold;
        _flowControlLowThreshold = lowThreshold;
        _listener = listener;
    }

    public Object poll(long time, TimeUnit unit) throws InterruptedException
    {
        Object o = _queue.poll(time, unit);
        if (o != null && _listener != null)
        {
            synchronized (_listener)
            {
                if (_count-- == _flowControlLowThreshold)
                {
                    _listener.underThreshold(_count);
                }
            }
        }

        return o;
    }

    public void add(Object o)
    {
        _queue.add(o);
        if (_listener != null)
        {
            synchronized (_listener)
            {
                if (++_count == _flowControlHighThreshold)
                {
                    _listener.aboveThreshold(_count);
                }
            }
        }
    }

    public Iterator iterator()
    {
        return _queue.iterator();
    }
}
