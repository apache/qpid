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
package org.apache.qpid.pool;

import org.apache.log4j.Logger;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PoolingFilter extends IoFilterAdapter implements Job.JobCompletionHandler
{
    private static final Logger _logger = Logger.getLogger(PoolingFilter.class);
    public static final Set<EventType> READ_EVENTS = new HashSet<EventType>(Arrays.asList(EventType.RECEIVED));
    public static final Set<EventType> WRITE_EVENTS = new HashSet<EventType>(Arrays.asList(EventType.WRITE));

    private final ConcurrentMap<IoSession, Job> _jobs = new ConcurrentHashMap<IoSession, Job>();
    private final ReferenceCountingExecutorService _poolReference;
    private final Set<EventType> _asyncTypes;

    private final String _name;
    private final int _maxEvents = Integer.getInteger("amqj.server.read_write_pool.max_events", 10);

    public PoolingFilter(ReferenceCountingExecutorService refCountingPool, Set<EventType> asyncTypes, String name)
    {
        _poolReference = refCountingPool;
        _asyncTypes = asyncTypes;
        _name = name;
    }

    private void fireEvent(IoSession session, Event event)
    {
        if (_asyncTypes.contains(event.getType()))
        {
            Job job = getJobForSession(session);
            job.acquire(); //prevents this job being removed from _jobs
            job.add(event);
            if (job.activate())
            {
                _poolReference.getPool().execute(job);
            }
        }
        else
        {
            event.process(session);
        }
    }

    private Job getJobForSession(IoSession session)
    {
        Job job = _jobs.get(session);
        return job == null ? createJobForSession(session) : job;
    }

    private Job createJobForSession(IoSession session)
    {
        return addJobForSession(session, new Job(session, this, _maxEvents));
    }

    private Job addJobForSession(IoSession session, Job job)
    {
        //atomic so ensures all threads agree on the same job
        Job existing = _jobs.putIfAbsent(session, job);
        return existing == null ? job : existing;
    }

    //Job.JobCompletionHandler
    public void completed(IoSession session, Job job)
    {
        if (job.isComplete())
        {
            job.release();
            if (!job.isReferenced())
            {
                _jobs.remove(session);
            }
        }
        else
        {
            if (job.activate())
            {
                _poolReference.getPool().execute(job);
            }
        }
    }

    //IoFilter methods that are processed by threads on the pool

    public void sessionOpened(NextFilter nextFilter, IoSession session) throws Exception
    {
        fireEvent(session, new Event(nextFilter, EventType.OPENED, null));
    }

    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception
    {
        fireEvent(session, new Event(nextFilter, EventType.CLOSED, null));
    }

    public void sessionIdle(NextFilter nextFilter, IoSession session,
                            IdleStatus status) throws Exception
    {
        fireEvent(session, new Event(nextFilter, EventType.IDLE, status));
    }

    public void exceptionCaught(NextFilter nextFilter, IoSession session,
                                Throwable cause) throws Exception
    {
        fireEvent(session, new Event(nextFilter, EventType.EXCEPTION, cause));
    }

    public void messageReceived(NextFilter nextFilter, IoSession session,
                                Object message) throws Exception
    {
        //ByteBufferUtil.acquireIfPossible( message );
        fireEvent(session, new Event(nextFilter, EventType.RECEIVED, message));
    }

    public void messageSent(NextFilter nextFilter, IoSession session,
                            Object message) throws Exception
    {
        //ByteBufferUtil.acquireIfPossible( message );
        fireEvent(session, new Event(nextFilter, EventType.SENT, message));
    }

    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception
    {
        fireEvent(session, new Event(nextFilter, EventType.WRITE, writeRequest));
    }

    //IoFilter methods that are processed on current thread (NOT on pooled thread)

    public void filterClose(NextFilter nextFilter, IoSession session) throws Exception
    {
        nextFilter.filterClose(session);
    }

    public void sessionCreated(NextFilter nextFilter, IoSession session)
    {
        nextFilter.sessionCreated(session);
    }

    public String toString()
    {
        return _name;
    }

    // LifeCycle methods

    public void init()
    {
        _logger.info("Init called on PoolingFilter " + toString());
        // called when the filter is initialised in the chain. If the reference count is
        // zero this acquire will initialise the pool
        _poolReference.acquireExecutorService();
    }

    public void destroy()
    {
        _logger.info("Destroy called on PoolingFilter " + toString());
        // when the reference count gets to zero we release the executor service
        _poolReference.releaseExecutorService();
    }
}
