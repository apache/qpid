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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mina.common.IoSession;

/**
 * A Job is a continuation that batches together other continuations, specifically {@link Event}s, into one continuation.
 * The {@link Event}s themselves provide methods to process themselves, so processing a job simply consists of sequentially
 * processing all of its aggregated events.
 *
 * The constructor accepts a maximum number of events for the job, and only runs up to that maximum number when
 * processing the job, but the add method does not enforce this maximum. In other words, not all the enqueued events
 * may be processed in each run of the job, several runs may be required to clear the queue.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Aggregate many coninuations together into a single continuation.
 * <tr><td> Sequentially process aggregated continuations. <td> {@link Event}
 * <tr><td> Provide running and completion status of the aggregate continuation.
 * <tr><td> Execute a terminal continuation upon job completion. <td> {@link JobCompletionHandler}
 * </table>
 *
 * @todo Could make Job implement Runnable, FutureTask, or a custom Continuation interface, to clarify its status as a
 *       continuation. Job is a continuation that aggregates other continuations and as such is a usefull re-usable
 *       piece of code. There may be other palces than the mina filter chain where continuation batching is used within
 *       qpid, so abstracting this out could provide a usefull building block. This also opens the way to different
 *       kinds of job with a common interface, e.g. parallel or sequential jobs etc.
 *
 * @todo For better re-usability could make the completion handler optional. Only run it when one is set.
 */
public class Job implements ReadWriteRunnable
{
    /** The maximum number of events to process per run of the job. More events than this may be queued in the job. */
    private final int _maxEvents;

    /** The Mina session. */
    private final IoSession _session;

    /** Holds the queue of events that make up the job. */
    private final java.util.Queue<Event> _eventQueue = new ConcurrentLinkedQueue<Event>();

    /** Holds a status flag, that indicates when the job is actively running. */
    private final AtomicBoolean _active = new AtomicBoolean();

    /** Holds the completion continuation, called upon completion of a run of the job. */
    private final JobCompletionHandler _completionHandler;

    private final boolean _readJob;

    /**
     * Creates a new job that aggregates many continuations together.
     *
     * @param session           The Mina session.
     * @param completionHandler The per job run, terminal continuation.
     * @param maxEvents         The maximum number of aggregated continuations to process per run of the job.
     * @param readJob
     */
    Job(IoSession session, JobCompletionHandler completionHandler, int maxEvents, final boolean readJob)
    {
        _session = session;
        _completionHandler = completionHandler;
        _maxEvents = maxEvents;
        _readJob = readJob;
    }

    /**
     * Enqueus a continuation for sequential processing by this job.
     *
     * @param evt The continuation to enqueue.
     */
    void add(Event evt)
    {
        _eventQueue.add(evt);
    }

    /**
     * Sequentially processes, up to the maximum number per job, the aggregated continuations in enqueued in this job.
     */
    boolean processAll()
    {
        // limit the number of events processed in one run
        int i = _maxEvents;
        while( --i != 0 )
        {
            Event e = _eventQueue.poll();
            if (e == null)
            {
                return true;
            }
            else
            {
                e.process(_session);
            }
        }
        return false;
    }

    /**
     * Tests if there are no more enqueued continuations to process.
     *
     * @return <tt>true</tt> if there are no enqueued continuations in this job, <tt>false</tt> otherwise.
     */
    public boolean isComplete()
    {
        return _eventQueue.peek() == null;
    }

    /**
     * Marks this job as active if it is inactive. This method is thread safe.
     *
     * @return <tt>true</tt> if this job was inactive and has now been marked as active, <tt>false</tt> otherwise.
     */
    public boolean activate()
    {
        return _active.compareAndSet(false, true);
    }

    /**
     * Marks this job as inactive. This method is thread safe.
     */
    public void deactivate()
    {
        _active.set(false);
    }

    /**
     * Processes a batch of aggregated continuations, marks this job as inactive and call the terminal continuation.
     */
    public void run()
    {
        if(processAll())
        {
            deactivate();
            _completionHandler.completed(_session, this);
        }
        else
        {
            _completionHandler.notCompleted(_session, this);
        }
    }

    public boolean isReadJob()
    {
        return _readJob;
    }

    public boolean isRead()
    {
        return _readJob;
    }

    public boolean isWrite()
    {
        return !_readJob;
    }


    /**
     * Another interface for a continuation.
     *
     * @todo Get rid of this interface as there are other interfaces that could be used instead, such as FutureTask,
     *       Runnable or a custom Continuation interface.
     */
    static interface JobCompletionHandler
    {
        public void completed(IoSession session, Job job);

        public void notCompleted(final IoSession session, final Job job);
    }
}
