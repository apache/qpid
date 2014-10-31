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

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.transport.TransportException;


class SubFlushRunner implements Runnable
{
    private static final Logger _logger = Logger.getLogger(SubFlushRunner.class);


    private final QueueConsumerImpl _sub;

    private static int IDLE = 0;
    private static int SCHEDULED = 1;
    private static int RUNNING = 2;


    private final AtomicInteger _scheduled = new AtomicInteger(IDLE);


    private final AtomicBoolean _stateChange = new AtomicBoolean();

    public SubFlushRunner(QueueConsumerImpl sub)
    {
        _sub = sub;
    }

    public void run()
    {
        if(_scheduled.compareAndSet(SCHEDULED, RUNNING))
        {
            Subject.doAs(SecurityManager.getSystemTaskSubject("Sub. Delivery"), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    boolean complete = false;
                    _stateChange.set(false);
                    try
                    {
                        complete = getQueue().flushConsumer(_sub, getQueue().getMaxAsyncDeliveries());
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
                        if ((!complete || _stateChange.compareAndSet(true,false))&& !_sub.isSuspended())
                        {
                            if(_scheduled.compareAndSet(IDLE,SCHEDULED))
                            {
                                getQueue().execute(SubFlushRunner.this);
                            }
                        }
                    }
                    return null;
                }
            });

        }
    }

    private AbstractQueue<?> getQueue()
    {
        return (AbstractQueue<?>) _sub.getQueue();
    }

    public String toString()
    {
        return "SubFlushRunner-" + _sub.toLogString();
    }

    public void execute()
    {
        _stateChange.set(true);
        if(_scheduled.compareAndSet(IDLE,SCHEDULED))
        {
            getQueue().execute(this);
        }
    }
}
