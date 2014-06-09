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
package org.apache.qpid.server.store.berkeleydb;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.apache.qpid.server.store.StoreFuture;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;

public class CoalescingCommiter implements Committer
{
    private final CommitTask _commitTask;
    private final ExecutorService _taskExecutor;

    public CoalescingCommiter(final String name, EnvironmentFacade environmentFacade)
    {
        _commitTask = new CommitTask(environmentFacade);
        _taskExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
        {
            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "Commit-Thread-" + name);
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public void start()
    {
        if (_commitTask.start())
        {
            _taskExecutor.submit(_commitTask);
        }
    }

    @Override
    public void stop()
    {
        _commitTask.stop();
    }

    @Override
    public void close()
    {
        try
        {
            _commitTask.close();
        }
        finally
        {
            _taskExecutor.shutdown();
        }
    }

    @Override
    public StoreFuture commit(Transaction tx, boolean syncCommit)
    {
        if (isStarted())
        {
            BDBCommitFuture commitFuture = new BDBCommitFuture(_commitTask, tx, syncCommit);
            try
            {
                commitFuture.commit();
                return commitFuture;
            }
            catch(IllegalStateException e)
            {
                // IllegalStateException is thrown when commit thread is stopped whilst commit is called
            }
        }

        return StoreFuture.IMMEDIATE_FUTURE;
    }

    public boolean isStarted()
    {
        return !_commitTask.isStopped();
    }

    private static final class BDBCommitFuture implements StoreFuture
    {
        private static final Logger LOGGER = Logger.getLogger(BDBCommitFuture.class);

        private final CommitTask _commitTask;
        private final Transaction _tx;
        private final boolean _syncCommit;
        private RuntimeException _databaseException;
        private boolean _complete;

        public BDBCommitFuture(CommitTask commitTask, Transaction tx, boolean syncCommit)
        {
            _commitTask = commitTask;
            _tx = tx;
            _syncCommit = syncCommit;
        }

        public synchronized void complete()
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("complete() called for transaction " + _tx);
            }
            _complete = true;

            notifyAll();
        }

        public synchronized void abort(RuntimeException databaseException)
        {
            _complete = true;
            _databaseException = databaseException;

            notifyAll();
        }

        public void commit() throws DatabaseException
        {
            _commitTask.addJob(this, _syncCommit);

            if(!_syncCommit)
            {
                if(LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("CommitAsync was requested, returning immediately.");
                }
                return;
            }

            waitForCompletion();

            if (_databaseException != null)
            {
                throw _databaseException;
            }

        }

        public synchronized boolean isComplete()
        {
            return _complete;
        }

        public synchronized void waitForCompletion()
        {
            long startTime = 0;
            if(LOGGER.isDebugEnabled())
            {
                startTime = System.currentTimeMillis();
            }

            while (!isComplete())
            {
                _commitTask.explicitNotify();
                try
                {
                    wait(250);
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }

                if (!_commitTask.isClosed() && _commitTask.isStopped() && !isComplete())
                {
                    // coalesing sync is not required anymore
                    // flush log and mark transaction as completed
                    try
                    {
                        _commitTask.flushLog();
                    }
                    catch(DatabaseException e)
                    {
                        _databaseException = e;
                    }
                    finally
                    {
                        complete();
                    }
                }
            }

            if(LOGGER.isDebugEnabled())
            {
                long duration = System.currentTimeMillis() - startTime;
                LOGGER.debug("waitForCompletion returning after " + duration + " ms for transaction " + _tx);
            }
        }
    }

    /**
     * Implements a {@link Runnable} which batches and commits a queue of {@link BDBCommitFuture} operations. The commit operations
     * themselves are responsible for adding themselves to the queue and waiting for the commit to happen before
     * continuing, but it is the responsibility of this thread to tell the commit operations when they have been
     * completed by calling back on their {@link BDBCommitFuture#complete()} and {@link BDBCommitFuture#abort} methods.
     *
     * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations </table>
     */
    private static class CommitTask implements Runnable
    {
        private static final Logger LOGGER = Logger.getLogger(CommitTask.class);

        private final AtomicBoolean _stopped = new AtomicBoolean(true);
        private final AtomicBoolean _closed = new AtomicBoolean(false);
        private final Queue<BDBCommitFuture> _jobQueue = new ConcurrentLinkedQueue<BDBCommitFuture>();
        private final Object _lock = new Object();
        private final EnvironmentFacade _environmentFacade;

        public CommitTask(EnvironmentFacade environmentFacade)
        {
            _environmentFacade = environmentFacade;
        }

        public boolean isClosed()
        {
            return _closed.get();
        }

        public boolean isStopped()
        {
            return _stopped.get();
        }

        public void explicitNotify()
        {
            synchronized (_lock)
            {
                _lock.notify();
            }
        }

        @Override
        public void run()
        {
            while (!_stopped.get())
            {
                synchronized (_lock)
                {
                    while (!_stopped.get() && !hasJobs())
                    {
                        try
                        {
                            // Periodically wake up and check, just in case we
                            // missed a notification. Don't want to lock the broker hard.
                            _lock.wait(1000);
                        }
                        catch (InterruptedException e)
                        {
                        }
                    }
                }
                processJobs();
            }

            // process remaining jobs if such were added whilst stopped
            if (hasJobs())
            {
                processJobs();
            }
        }

        private void processJobs()
        {
            int size = _jobQueue.size();

            try
            {
                long startTime = 0;
                if(LOGGER.isDebugEnabled())
                {
                    startTime = System.currentTimeMillis();
                }

                flushLog();

                if(LOGGER.isDebugEnabled())
                {
                    long duration = System.currentTimeMillis() - startTime;
                    LOGGER.debug("flushLog completed in " + duration  + " ms");
                }

                for(int i = 0; i < size; i++)
                {
                    BDBCommitFuture commit = _jobQueue.poll();
                    if (commit == null)
                    {
                        break;
                    }
                    commit.complete();
                }

            }
            catch (DatabaseException e)
            {
                try
                {
                    LOGGER.error("Exception during environment log flush", e);

                    for(int i = 0; i < size; i++)
                    {
                        BDBCommitFuture commit = _jobQueue.poll();
                        if (commit == null)
                        {
                            break;
                        }
                        commit.abort(e);
                    }
                }
                finally
                {
                    LOGGER.error("Closing store environment", e);

                    try
                    {
                        _environmentFacade.close();
                    }
                    catch (DatabaseException ex)
                    {
                        LOGGER.error("Exception closing store environment", ex);
                    }
                }
            }
        }

        private void flushLog()
        {
            Environment environment = _environmentFacade.getEnvironment();
            if (environment != null && environment.isValid())
            {
                environment.flushLog(true);
            }
        }

        private boolean hasJobs()
        {
            return !_jobQueue.isEmpty();
        }

        public void addJob(BDBCommitFuture commit, final boolean sync)
        {
            if (_stopped.get())
            {
                throw new IllegalStateException("Commit thread is stopped");
            }
            _jobQueue.add(commit);
            if(sync)
            {
                synchronized (_lock)
                {
                    _lock.notifyAll();
                }
            }
        }

        public boolean start()
        {
            return _stopped.compareAndSet(true, false);
        }

        public void stop()
        {
            if (_stopped.compareAndSet(false, true))
            {
                synchronized (_lock)
                {
                    _lock.notifyAll();
                }
                _jobQueue.clear();
            }
        }

        public void close()
        {
            if (_closed.compareAndSet(false, true))
            {
                RuntimeException e = new RuntimeException("Commit thread has been closed");
                synchronized (_lock)
                {
                    _stopped.set(true);
                    BDBCommitFuture commit = null;
                    int abortedCommits = 0;
                    while ((commit = _jobQueue.poll()) != null)
                    {
                        abortedCommits++;
                        commit.abort(e);
                    }
                    if (LOGGER.isDebugEnabled() && abortedCommits > 0)
                    {
                        LOGGER.debug(abortedCommits + " commit(s) were aborted during close.");
                    }
                    _lock.notifyAll();
                }
            }
        }
    }
}
