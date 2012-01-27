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
package org.apache.qpid.server.queue;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.logging.actors.CurrentActor;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QueueRunners are Runnables used to process a queue when requiring
 * asynchronous message delivery to subscriptions, which is necessary
 * when straight-through delivery of a message to a subscription isn't
 * possible during the enqueue operation.
 */
public class QueueRunner implements Runnable
{
    private static final Logger _logger = Logger.getLogger(QueueRunner.class);

    private final SimpleAMQQueue _queue;

    private static int IDLE = 0;
    private static int SCHEDULED = 1;
    private static int RUNNING = 2;


    private final AtomicInteger _scheduled = new AtomicInteger(IDLE);

    private final AtomicBoolean _stateChange = new AtomicBoolean();

    private final AtomicLong _lastRunAgain = new AtomicLong();
    private final AtomicLong _lastRunTime = new AtomicLong();

    private long _continues;

    public QueueRunner(SimpleAMQQueue queue)
    {
        _queue = queue;
    }

    public void run()
    {
        if(_scheduled.compareAndSet(SCHEDULED,RUNNING))
        {
            long runAgain = Long.MIN_VALUE;
            _stateChange.set(false);
            try
            {
                CurrentActor.set(_queue.getLogActor());

                runAgain = _queue.processQueue(this);
            }
            catch (AMQException e)
            {
                _logger.error("Exception during asynchronous delivery by " + toString(), e);
            }
            finally
            {
                CurrentActor.remove();
            }
            _scheduled.compareAndSet(RUNNING, IDLE);
            long stateChangeCount = _queue.getStateChangeCount();
            _lastRunAgain.set(runAgain);
            _lastRunTime.set(System.nanoTime());
            if(runAgain == 0L || runAgain != stateChangeCount || _stateChange.compareAndSet(true,false))
            {
                _continues++;
                if(_scheduled.compareAndSet(IDLE, SCHEDULED))
                {
                    _queue.execute(this);
                }
            }

        }
    }

    public String toString()
    {
        return "QueueRunner-" + _queue.getLogActor();
    }

    public void execute(Executor executor)
    {
        _stateChange.set(true);
        if(_scheduled.compareAndSet(IDLE, SCHEDULED))
        {
            executor.execute(this);
        }
    }

    public boolean isIdle()
    {
        return _scheduled.get() == IDLE;
    }

}
