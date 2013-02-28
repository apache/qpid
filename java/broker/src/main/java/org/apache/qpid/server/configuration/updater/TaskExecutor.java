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
package org.apache.qpid.server.configuration.updater;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.SecurityManager;

public class TaskExecutor
{
    private static final String TASK_EXECUTION_THREAD_NAME = "Broker-Configuration-Thread";
    private static final Logger LOGGER = Logger.getLogger(TaskExecutor.class);

    private volatile Thread _taskThread;
    private final AtomicReference<State> _state;
    private volatile ExecutorService _executor;

    public TaskExecutor()
    {
        _state = new AtomicReference<State>(State.INITIALISING);
    }

    public State getState()
    {
        return _state.get();
    }

    public void start()
    {
        if (_state.compareAndSet(State.INITIALISING, State.ACTIVE))
        {
            LOGGER.debug("Starting task executor");
            _executor = Executors.newFixedThreadPool(1, new ThreadFactory()
            {
                @Override
                public Thread newThread(Runnable r)
                {
                    _taskThread = new Thread(r, TASK_EXECUTION_THREAD_NAME);
                    return _taskThread;
                }
            });
            LOGGER.debug("Task executor is started");
        }
    }

    public void stopImmediately()
    {
        if (_state.compareAndSet(State.ACTIVE, State.STOPPED))
        {
            ExecutorService executor = _executor;
            if (executor != null)
            {
                LOGGER.debug("Stopping task executor immediately");
                List<Runnable> cancelledTasks = executor.shutdownNow();
                if (cancelledTasks != null)
                {
                    for (Runnable runnable : cancelledTasks)
                    {
                        if (runnable instanceof RunnableFuture<?>)
                        {
                            ((RunnableFuture<?>) runnable).cancel(true);
                        }
                    }
                }
                _executor = null;
                _taskThread = null;
                LOGGER.debug("Task executor was stopped immediately. Number of unfinished tasks: " + cancelledTasks.size());
            }
        }
    }

    public void stop()
    {
        if (_state.compareAndSet(State.ACTIVE, State.STOPPED))
        {
            ExecutorService executor = _executor;
            if (executor != null)
            {
                LOGGER.debug("Stopping task executor");
                executor.shutdown();
                _executor = null;
                _taskThread = null;
                LOGGER.debug("Task executor is stopped");
            }
        }
    }

    Future<?> submit(Callable<?> task)
    {
        checkState();
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Submitting task: " + task);
        }
        Future<?> future = null;
        if (isTaskExecutorThread())
        {
            Object result = executeTaskAndHandleExceptions(task);
            return new ImmediateFuture(result);
        }
        else
        {
            future = _executor.submit(new CallableWrapper(task));
        }
        return future;
    }

    public Object submitAndWait(Callable<?> task) throws CancellationException
    {
        try
        {
            Future<?> future = submit(task);
            return future.get();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Task execution was interrupted: " + task, e);
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            else if (cause instanceof Exception)
            {
                throw new RuntimeException("Failed to execute user task: " + task, cause);
            }
            else if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            else
            {
                throw new RuntimeException("Failed to execute user task: " + task, cause);
            }
        }
    }

    public boolean isTaskExecutorThread()
    {
        return Thread.currentThread() == _taskThread;
    }

    private void checkState()
    {
        if (_state.get() != State.ACTIVE)
        {
            throw new IllegalStateException("Task executor is not in ACTIVE state");
        }
    }

    private Object executeTaskAndHandleExceptions(Callable<?> userTask)
    {
        try
        {
            return executeTask(userTask);
        }
        catch (Exception e)
        {
            if (e instanceof RuntimeException)
            {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Failed to execute user task: " + userTask, e);
        }
    }

    private Object executeTask(Callable<?> userTask) throws Exception
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Performing task " + userTask);
        }
        Object result = userTask.call();
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Task " + userTask + " is performed successfully with result:" + result);
        }
        return result;
    }

    private class CallableWrapper implements Callable<Object>
    {
        private Callable<?> _userTask;
        private Subject _securityManagerSubject;
        private LogActor _actor;
        private Subject _contextSubject;

        public CallableWrapper(Callable<?> userWork)
        {
            _userTask = userWork;
            _securityManagerSubject = SecurityManager.getThreadSubject();
            _actor = CurrentActor.get();
            _contextSubject = Subject.getSubject(AccessController.getContext());
        }

        @Override
        public Object call() throws Exception
        {
            SecurityManager.setThreadSubject(_securityManagerSubject);
            CurrentActor.set(_actor);

            try
            {
                Object result = null;
                try
                {
                    result = Subject.doAs(_contextSubject, new PrivilegedExceptionAction<Object>()
                    {
                        @Override
                        public Object run() throws Exception
                        {
                            return executeTask(_userTask);
                        }
                    });
                }
                catch (PrivilegedActionException e)
                {
                    throw e.getException();
                }
                return result;
            }
            finally
            {
                try
                {
                    CurrentActor.remove();
                }
                catch (Exception e)
                {
                    LOGGER.warn("Unxpected exception on current actor removal", e);
                }
                try
                {
                    SecurityManager.setThreadSubject(null);
                }
                catch (Exception e)
                {
                    LOGGER.warn("Unxpected exception on nullifying of subject for a security manager", e);
                }
            }
        }
    }

    private class ImmediateFuture implements Future<Object>
    {
        private Object _result;

        public ImmediateFuture(Object result)
        {
            super();
            this._result = result;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            return false;
        }

        @Override
        public boolean isCancelled()
        {
            return false;
        }

        @Override
        public boolean isDone()
        {
            return true;
        }

        @Override
        public Object get()
        {
            return _result;
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
        {
            return get();
        }
    }
}
