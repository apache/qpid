package org.apache.qpid.server.queue;
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


import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.subscription.Subscription;


class SubFlushRunner implements Runnable
{
    private static final Logger _logger = Logger.getLogger(SubFlushRunner.class);


    private final Subscription _sub;

    private static int IDLE = 0;
    private static int SCHEDULED = 1;
    private static int RUNNING = 2;


    private final AtomicInteger _scheduled = new AtomicInteger(IDLE);


    private static final long ITERATIONS = SimpleAMQQueue.MAX_ASYNC_DELIVERIES;
    private final AtomicBoolean _stateChange = new AtomicBoolean();

    public SubFlushRunner(Subscription sub)
    {
        _sub = sub;
    }

    public void run()
    {
        if(_scheduled.compareAndSet(SCHEDULED, RUNNING))
        {
            boolean complete = false;
            _stateChange.set(false);
            try
            {
                CurrentActor.set(_sub.getLogActor());
                complete = getQueue().flushSubscription(_sub, ITERATIONS);
            }
            catch (AMQException e)
            {
                _logger.error(e);
            }
            finally
            {
                CurrentActor.remove();
            }
            _scheduled.compareAndSet(RUNNING, IDLE);
            if ((!complete || _stateChange.compareAndSet(true,false))&& !_sub.isSuspended())
            {
                if(_scheduled.compareAndSet(IDLE,SCHEDULED))
                {
                    getQueue().execute(this);
                }
            }
        }
    }

    private SimpleAMQQueue getQueue()
    {
        return (SimpleAMQQueue) _sub.getQueue();
    }

    public String toString()
    {
        return "SubFlushRunner-" + _sub.getLogActor();
    }

    public void execute(Executor executor)
    {
        _stateChange.set(true);
        if(_scheduled.compareAndSet(IDLE,SCHEDULED))
        {
            executor.execute(this);
        }
    }
}
