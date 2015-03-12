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

import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.transport.TransportException;

/**
 * QueueRunners are Runnables used to process a queue when requiring
 * asynchronous message delivery to consumers, which is necessary
 * when straight-through delivery of a message to a consumer isn't
 * possible during the enqueue operation.
 */
public class QueueRunner implements Runnable
{
    private static final Logger _logger = LoggerFactory.getLogger(QueueRunner.class);

    private final AbstractQueue _queue;

    private static int IDLE = 0;
    private static int SCHEDULED = 1;
    private static int RUNNING = 2;

    private final AtomicInteger _scheduled = new AtomicInteger(IDLE);

    private final AtomicBoolean _stateChange = new AtomicBoolean();

    private final AtomicLong _lastRunAgain = new AtomicLong();
    private final AtomicLong _lastRunTime = new AtomicLong();

    public QueueRunner(AbstractQueue queue)
    {
        _queue = queue;
    }

    public void run()
    {
        if(_scheduled.compareAndSet(SCHEDULED,RUNNING))
        {
            Subject.doAs(SecurityManager.getSystemTaskSubject("Queue Delivery"), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    long runAgain = Long.MIN_VALUE;
                    _stateChange.set(false);
                    try
                    {
                        runAgain = _queue.processQueue(QueueRunner.this);
                    }
                    catch (ConnectionScopedRuntimeException | TransportException  e)
                    {
                        final String errorMessage = "Problem during asynchronous delivery by " + toString();
                        if(_logger.isDebugEnabled())
                        {
                            _logger.debug(errorMessage, e);
                        }
                        else
                        {
                            _logger.info(errorMessage + ' ' + e.getMessage());
                        }
                    }
                    finally
                    {
                        _scheduled.compareAndSet(RUNNING, IDLE);
                        final long stateChangeCount = _queue.getStateChangeCount();
                        _lastRunAgain.set(runAgain);
                        _lastRunTime.set(System.nanoTime());
                        if(runAgain == 0L || runAgain != stateChangeCount || _stateChange.compareAndSet(true,false))
                        {
                            if(_scheduled.compareAndSet(IDLE, SCHEDULED))
                            {
                                _queue.execute(QueueRunner.this);
                            }
                        }
                    }
                    return null;
                }

            });
        }
    }

    public String toString()
    {
        return "QueueRunner-" + _queue.getLogSubject().toLogString();
    }

    public void execute()
    {
        _stateChange.set(true);
        if(_scheduled.compareAndSet(IDLE, SCHEDULED))
        {
            _queue.execute(this);
        }
    }

    public boolean isIdle()
    {
        return _scheduled.get() == IDLE;
    }

}
