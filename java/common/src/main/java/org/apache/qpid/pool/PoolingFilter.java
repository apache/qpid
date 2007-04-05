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

import org.apache.qpid.pool.Event.CloseEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;

public class PoolingFilter extends IoFilterAdapter implements Job.JobCompletionHandler
{
    private static final Logger _logger = Logger.getLogger(PoolingFilter.class);

    private final ConcurrentMap<IoSession, Job> _jobs = new ConcurrentHashMap<IoSession, Job>();
    private final ReferenceCountingExecutorService _poolReference;

    private final String _name;
    private final int _maxEvents = Integer.getInteger("amqj.server.read_write_pool.max_events", 10);

    public PoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
    {
        _poolReference = refCountingPool;
        _name = name;
    }

    void fireAsynchEvent(IoSession session, Event event)
    {
        Job job = getJobForSession(session);
 //       job.acquire(); //prevents this job being removed from _jobs
        job.add(event);

        //Additional checks on pool to check that it hasn't shutdown.
        // The alternative is to catch the RejectedExecutionException that will result from executing on a shutdown pool
        if (job.activate() && _poolReference.getPool() != null && !_poolReference.getPool().isShutdown())
        {
            _poolReference.getPool().execute(job);
        }

    }

    public void createNewJobForSession(IoSession session)
    {
        Job job = new Job(session, this, _maxEvents);
        session.setAttribute(_name, job);
    }

    private Job getJobForSession(IoSession session)
    {
        return (Job) session.getAttribute(_name);

/*        if(job == null)
        {
            System.err.println("Error in " + _name);
            Thread.dumpStack();
        }


        job = _jobs.get(session);
        return job == null ? createJobForSession(session) : job;*/
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
//        if (job.isComplete())
//        {
//            job.release();
//            if (!job.isReferenced())
//            {
//                _jobs.remove(session);
//            }
//        }
//        else
        if(!job.isComplete())
        {
            // ritchiem : 2006-12-13 Do we need to perform the additional checks here?
            //                       Can the pool be shutdown at this point?
            if (job.activate() && _poolReference.getPool() != null && !_poolReference.getPool().isShutdown())
            {
                _poolReference.getPool().execute(job);
            }
        }
    }

    //IoFilter methods that are processed by threads on the pool

    public void sessionOpened(final NextFilter nextFilter, final IoSession session) throws Exception
    {
        nextFilter.sessionOpened(session);
    }

    public void sessionClosed(final NextFilter nextFilter, final IoSession session) throws Exception
    {
        nextFilter.sessionClosed(session);
    }

    public void sessionIdle(final NextFilter nextFilter, final IoSession session,
                            final IdleStatus status) throws Exception
    {
        nextFilter.sessionIdle(session, status);
    }

    public void exceptionCaught(final NextFilter nextFilter, final IoSession session,
                                final Throwable cause) throws Exception
    {
            nextFilter.exceptionCaught(session,cause);
    }

    public void messageReceived(final NextFilter nextFilter, final IoSession session,
                                final Object message) throws Exception
    {
        nextFilter.messageReceived(session,message);
    }

    public void messageSent(final NextFilter nextFilter, final IoSession session,
                            final Object message) throws Exception
    {
        nextFilter.messageSent(session, message);
    }

    public void filterWrite(final NextFilter nextFilter, final IoSession session,
                            final WriteRequest writeRequest) throws Exception
    {
        nextFilter.filterWrite(session, writeRequest);
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

    public static class AsynchReadPoolingFilter extends PoolingFilter
    {

        public AsynchReadPoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
        {
            super(refCountingPool, name);
        }

        public void messageReceived(final NextFilter nextFilter, final IoSession session,
                                final Object message) throws Exception
        {

            fireAsynchEvent(session, new Event.ReceivedEvent(nextFilter, message));
        }

        public void sessionClosed(final NextFilter nextFilter, final IoSession session) throws Exception
        {
            fireAsynchEvent(session, new CloseEvent(nextFilter));
        }

    }

    public static class AsynchWritePoolingFilter extends PoolingFilter
    {

        public AsynchWritePoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
        {
            super(refCountingPool, name);
        }


        public void filterWrite(final NextFilter nextFilter, final IoSession session,
                                final WriteRequest writeRequest) throws Exception
        {
            fireAsynchEvent(session, new Event.WriteEvent(nextFilter, writeRequest));
        }

        public void sessionClosed(final NextFilter nextFilter, final IoSession session) throws Exception
        {
            fireAsynchEvent(session, new CloseEvent(nextFilter));
        }
        

    }

    public static PoolingFilter createAynschReadPoolingFilter(ReferenceCountingExecutorService refCountingPool,String name)
    {
        return new AsynchReadPoolingFilter(refCountingPool,name);
    }


    public static PoolingFilter createAynschWritePoolingFilter(ReferenceCountingExecutorService refCountingPool,String name)
    {
        return new AsynchWritePoolingFilter(refCountingPool,name);
    }

}


