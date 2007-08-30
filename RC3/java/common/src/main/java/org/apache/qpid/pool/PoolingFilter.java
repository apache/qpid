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

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;

import org.apache.qpid.pool.Event.CloseEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * PoolingFilter, is a no-op pass through filter that hands all events down the Mina filter chain by default. As it
 * adds no behaviour by default to the filter chain, it is abstract.
 *
 * <p/>PoolingFilter provides a capability, available to sub-classes, to handle events in the chain asynchronously, by
 * adding them to a job. If a job is not active, adding an event to it activates it. If it is active, the event is
 * added to the job, which will run to completion and eventually process the event. The queue on the job itself acts as
 * a buffer between stages of the pipeline.
 *
 * <p/>There are two convenience methods, {@link #createAynschReadPoolingFilter} and
 * {@link #createAynschWritePoolingFilter}, for obtaining pooling filters that handle 'messageReceived' and
 * 'filterWrite' events, making it possible to process these event streams seperately.
 *
 * <p/>Pooling filters have a name, in order to distinguish different filter types. They set up a {@link Job} on the
 * Mina session they are working with, and store it in the session against their identifying name. This allows different
 * filters with different names to be set up on the same filter chain, on the same Mina session, that batch their
 * workloads in different jobs.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Implement default, pass through filter.
 * <tr><td> Create pooling filters and a specific thread pool. <td> {@link ReferenceCountingExecutorService}
 * <tr><td> Provide the ability to batch Mina events for asynchronous processing. <td> {@link Job}, {@link Event}
 * <tr><td> Provide a terminal continuation to keep jobs running till empty.
 *     <td> {@link Job}, {@link Job.JobCompletionHandler}
 * </table>
 *
 * @todo This seems a bit bizarre. ReadWriteThreadModel creates seperate pooling filters for read and write events.
 *       The pooling filters themselves batch read and write events into jobs, but hand these jobs to a common thread
 *       pool for execution. So the same thread pool ends up handling read and write events, albeit with many threads
 *       so there is concurrency. But why go to the trouble of seperating out the read and write events in that case?
 *       Why not just batch them into jobs together? Perhaps its so that seperate thread pools could be used for these
 *       stages.
 *
 * @todo Why set an event limit of 10 on the Job? This also seems bizarre, as the job can have more than 10 events in
 *       it. Its just that it runs them 10 at a time, but the completion hander here checks if there are more to run
 *       and trips off another batch of 10 until they are all done. Why not just have a straight forward
 *       consumer/producer queue scenario without the batches of 10? So instead of having many jobs with batches of 10
 *       in them, just have one queue of events and worker threads taking the next event. There will be coordination
 *       between worker threads and new events arriving on the job anyway, so the simpler scenario may have the same
 *       amount of contention. I can see that the batches of 10 is done, so that no job is allowed to hog the worker
 *       pool for too long. I'm not convinced this fairly complex scheme will actually add anything, and it might be
 *       better to encapsulate it under a Queue interface anyway, so that different queue implementations can easily
 *       be substituted in.
 *
 * @todo The static helper methods are pointless. Could just call new.
 */
public abstract class PoolingFilter extends IoFilterAdapter implements Job.JobCompletionHandler
{
    /** Used for debugging purposes. */
    private static final Logger _logger = LoggerFactory.getLogger(PoolingFilter.class);

    /** Holds a mapping from Mina sessions to batched jobs for execution. */
    private final ConcurrentMap<IoSession, Job> _jobs = new ConcurrentHashMap<IoSession, Job>();

    /** Holds the managed reference to obtain the executor for the batched jobs. */
    private final ReferenceCountingExecutorService _poolReference;

    /** Used to hold a name for identifying differeny pooling filter types. */
    private final String _name;

    /** Defines the maximum number of events that will be batched into a single job. */
    private final int _maxEvents = Integer.getInteger("amqj.server.read_write_pool.max_events", 10);

    /**
     * Creates a named pooling filter, on the specified shared thread pool.
     *
     * @param refCountingPool The thread pool reference.
     * @param name            The identifying name of the filter type.
     */
    public PoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
    {
        _poolReference = refCountingPool;
        _name = name;
    }

    /**
     * Helper method to get an instance of a pooling filter that handles read events asynchronously.
     *
     * @param refCountingPool A managed reference to the thread pool.
     * @param name            The filter types identifying name.
     *
     * @return A pooling filter for asynchronous read events.
     */
    public static PoolingFilter createAynschReadPoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
    {
        return new AsynchReadPoolingFilter(refCountingPool, name);
    }

    /**
     * Helper method to get an instance of a pooling filter that handles write events asynchronously.
     *
     * @param refCountingPool A managed reference to the thread pool.
     * @param name            The filter types identifying name.
     *
     * @return A pooling filter for asynchronous write events.
     */
    public static PoolingFilter createAynschWritePoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
    {
        return new AsynchWritePoolingFilter(refCountingPool, name);
    }

    /**
     * Called by Mina to initialize this filter. Takes a reference to the thread pool.
     */
    public void init()
    {
        _logger.debug("Init called on PoolingFilter " + toString());

        // Called when the filter is initialised in the chain. If the reference count is
        // zero this acquire will initialise the pool.
        _poolReference.acquireExecutorService();
    }

    /**
     * Called by Mina to clean up this filter. Releases the reference to the thread pool.
     */
    public void destroy()
    {
        _logger.debug("Destroy called on PoolingFilter " + toString());

        // When the reference count gets to zero we release the executor service.
        _poolReference.releaseExecutorService();
    }

    /**
     * Adds an {@link Event} to a {@link Job}, triggering the execution of the job if it is not already running.
     *
     * @param session The Mina session to work in.
     * @param event   The event to hand off asynchronously.
     */
    void fireAsynchEvent(IoSession session, Event event)
    {
        Job job = getJobForSession(session);
        // job.acquire(); //prevents this job being removed from _jobs
        job.add(event);

        // Additional checks on pool to check that it hasn't shutdown.
        // The alternative is to catch the RejectedExecutionException that will result from executing on a shutdown pool
        if (job.activate() && (_poolReference.getPool() != null) && !_poolReference.getPool().isShutdown())
        {
            _poolReference.getPool().execute(job);
        }

    }

    /**
     * Creates a Job on the Mina session, identified by this filters name, in which this filter places asynchronously
     * handled events.
     *
     * @param session The Mina session.
     */
    public void createNewJobForSession(IoSession session)
    {
        Job job = new Job(session, this, _maxEvents);
        session.setAttribute(_name, job);
    }

    /**
     * Retrieves this filters Job, by this filters name, from the Mina session.
     *
     * @param session The Mina session.
     *
     * @return The Job for this filter to place asynchronous events into.
     */
    private Job getJobForSession(IoSession session)
    {
        return (Job) session.getAttribute(_name);
    }

    /*private Job createJobForSession(IoSession session)
    {
        return addJobForSession(session, new Job(session, this, _maxEvents));
    }*/

    /*private Job addJobForSession(IoSession session, Job job)
    {
        // atomic so ensures all threads agree on the same job
        Job existing = _jobs.putIfAbsent(session, job);

        return (existing == null) ? job : existing;
    }*/

    /**
     * Implements a terminal continuation for the {@link Job} for this filter. Whenever the Job completes its processing
     * of a batch of events this is called. This method simply re-activates the job, if it has more events to process.
     *
     * @param session The Mina session to work in.
     * @param job     The job that completed.
     */
    public void completed(IoSession session, Job job)
    {
        // if (job.isComplete())
        // {
        // job.release();
        // if (!job.isReferenced())
        // {
        // _jobs.remove(session);
        // }
        // }
        // else
        if (!job.isComplete())
        {
            // ritchiem : 2006-12-13 Do we need to perform the additional checks here?
            // Can the pool be shutdown at this point?
            if (job.activate() && (_poolReference.getPool() != null) && !_poolReference.getPool().isShutdown())
            {
                _poolReference.getPool().execute(job);
            }
        }
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void sessionOpened(final NextFilter nextFilter, final IoSession session) throws Exception
    {
        nextFilter.sessionOpened(session);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void sessionClosed(final NextFilter nextFilter, final IoSession session) throws Exception
    {
        nextFilter.sessionClosed(session);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     * @param status     The session idle status.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void sessionIdle(final NextFilter nextFilter, final IoSession session, final IdleStatus status) throws Exception
    {
        nextFilter.sessionIdle(session, status);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     * @param cause      The underlying exception.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void exceptionCaught(final NextFilter nextFilter, final IoSession session, final Throwable cause) throws Exception
    {
        nextFilter.exceptionCaught(session, cause);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     * @param message    The message received.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void messageReceived(final NextFilter nextFilter, final IoSession session, final Object message) throws Exception
    {
        nextFilter.messageReceived(session, message);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     * @param message    The message sent.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void messageSent(final NextFilter nextFilter, final IoSession session, final Object message) throws Exception
    {
        nextFilter.messageSent(session, message);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter   The next filter in the chain.
     * @param session      The Mina session.
     * @param writeRequest The write request event.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void filterWrite(final NextFilter nextFilter, final IoSession session, final WriteRequest writeRequest)
        throws Exception
    {
        nextFilter.filterWrite(session, writeRequest);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void filterClose(NextFilter nextFilter, IoSession session) throws Exception
    {
        nextFilter.filterClose(session);
    }

    /**
     * No-op pass through filter to the next filter in the chain.
     *
     * @param nextFilter The next filter in the chain.
     * @param session    The Mina session.
     *
     * @throws Exception This method does not throw any exceptions, but has Exception in its signature to allow
     *                   overriding sub-classes the ability to.
     */
    public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception
    {
        nextFilter.sessionCreated(session);
    }

    /**
     * Prints the filter types identifying name to a string, mainly for debugging purposes.
     *
     * @return The filter types identifying name.
     */
    public String toString()
    {
        return _name;
    }

    /**
     * AsynchReadPoolingFilter is a pooling filter that handles 'messageReceived' and 'sessionClosed' events
     * asynchronously.
     */
    public static class AsynchReadPoolingFilter extends PoolingFilter
    {
        /**
         * Creates a pooling filter that handles read events asynchronously.
         *
         * @param refCountingPool A managed reference to the thread pool.
         * @param name            The filter types identifying name.
         */
        public AsynchReadPoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
        {
            super(refCountingPool, name);
        }

        /**
         * Hands off this event for asynchronous execution.
         *
         * @param nextFilter The next filter in the chain.
         * @param session    The Mina session.
         * @param message    The message received.
         */
        public void messageReceived(NextFilter nextFilter, final IoSession session, Object message)
        {

            fireAsynchEvent(session, new Event.ReceivedEvent(nextFilter, message));
        }

        /**
         * Hands off this event for asynchronous execution.
         *
         * @param nextFilter The next filter in the chain.
         * @param session    The Mina session.
         */
        public void sessionClosed(final NextFilter nextFilter, final IoSession session)
        {
            fireAsynchEvent(session, new CloseEvent(nextFilter));
        }
    }

    /**
     * AsynchWritePoolingFilter is a pooling filter that handles 'filterWrite' and 'sessionClosed' events
     * asynchronously.
     */
    public static class AsynchWritePoolingFilter extends PoolingFilter
    {
        /**
         * Creates a pooling filter that handles write events asynchronously.
         *
         * @param refCountingPool A managed reference to the thread pool.
         * @param name            The filter types identifying name.
         */
        public AsynchWritePoolingFilter(ReferenceCountingExecutorService refCountingPool, String name)
        {
            super(refCountingPool, name);
        }

        /**
         * Hands off this event for asynchronous execution.
         *
         * @param nextFilter   The next filter in the chain.
         * @param session      The Mina session.
         * @param writeRequest The write request event.
         */
        public void filterWrite(final NextFilter nextFilter, final IoSession session, final WriteRequest writeRequest)
        {
            fireAsynchEvent(session, new Event.WriteEvent(nextFilter, writeRequest));
        }

        /**
         * Hands off this event for asynchronous execution.
         *
         * @param nextFilter The next filter in the chain.
         * @param session    The Mina session.
         */
        public void sessionClosed(final NextFilter nextFilter, final IoSession session)
        {
            fireAsynchEvent(session, new CloseEvent(nextFilter));
        }
    }
}
