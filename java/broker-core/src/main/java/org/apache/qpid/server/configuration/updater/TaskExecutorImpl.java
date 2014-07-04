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
import java.security.PrivilegedAction;
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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class TaskExecutorImpl implements TaskExecutor
{
    private static final String TASK_EXECUTION_THREAD_NAME = "Broker-Configuration-Thread";
    private static final Logger LOGGER = Logger.getLogger(TaskExecutorImpl.class);

    private volatile Thread _taskThread;
    private final AtomicBoolean _running = new AtomicBoolean();
    private volatile ExecutorService _executor;


    @Override
    public boolean isRunning()
    {
        return _running.get();
    }

    @Override
    public void start()
    {
        if (_running.compareAndSet(false, true))
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

    @Override
    public void stopImmediately()
    {
        if (_running.compareAndSet(true,false))
        {
            ExecutorService executor = _executor;
            if (executor != null)
            {
                LOGGER.debug("Stopping task executor immediately");
                List<Runnable> cancelledTasks = executor.shutdownNow();
                for (Runnable runnable : cancelledTasks)
                {
                    if (runnable instanceof RunnableFuture<?>)
                    {
                        ((RunnableFuture<?>) runnable).cancel(true);
                    }
                }

                _executor = null;
                _taskThread = null;
                LOGGER.debug("Task executor was stopped immediately. Number of unfinished tasks: " + cancelledTasks.size());
            }
        }
    }

    @Override
    public void stop()
    {
        if (_running.compareAndSet(true,false))
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

    @Override
    public <T> Future<T> submit(Task<T> task)
    {
        checkState();
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Submitting task: " + task);
        }
        Future<T> future = null;
        if (isTaskExecutorThread())
        {
            T result = executeTask(task);
            return new ImmediateFuture(result);
        }
        else
        {
            future = _executor.submit(new CallableWrapper(task));
        }
        return future;
    }

    @Override
    public void run(final VoidTask task) throws CancellationException
    {
        run(new Task<Void>()
        {
            @Override
            public Void execute()
            {
                task.execute();
                return null;
            }
        });
    }

    private static class ExceptionTaskWrapper<T, E extends Exception> implements Task<T>
    {
        private final TaskWithException<T,E> _underlying;
        private E _exception;

        private ExceptionTaskWrapper(final TaskWithException<T, E> underlying)
        {
            _underlying = underlying;
        }


        @Override
        public T execute()
        {
            try
            {
                return _underlying.execute();
            }
            catch (Exception e)
            {
                _exception = (E) e;
                return null;
            }
        }

        E getException()
        {
            return _exception;
        }
    }


    private static class ExceptionVoidTaskWrapper<E extends Exception> implements Task<Void>
    {
        private final VoidTaskWithException<E> _underlying;
        private E _exception;

        private ExceptionVoidTaskWrapper(final VoidTaskWithException<E> underlying)
        {
            _underlying = underlying;
        }


        @Override
        public Void execute()
        {
            try
            {
                _underlying.execute();

            }
            catch (Exception e)
            {
                _exception = (E) e;
            }
            return null;
        }

        E getException()
        {
            return _exception;
        }
    }

    @Override
    public <T, E extends Exception> T run(TaskWithException<T, E> task) throws CancellationException, E
    {
        ExceptionTaskWrapper<T,E> wrapper = new ExceptionTaskWrapper<T, E>(task);
        T result = run(wrapper);
        if(wrapper.getException() != null)
        {
            throw wrapper.getException();
        }
        else
        {
            return result;
        }
    }


    @Override
    public <E extends Exception> void run(VoidTaskWithException<E> task) throws CancellationException, E
    {
        ExceptionVoidTaskWrapper<E> wrapper = new ExceptionVoidTaskWrapper<E>(task);
        run(wrapper);
        if(wrapper.getException() != null)
        {
            throw wrapper.getException();
        }
    }

    @Override
    public <T> T run(Task<T> task) throws CancellationException
    {
        try
        {
            Future<T> future = submit(task);
            return future.get();
        }
        catch (InterruptedException e)
        {
            throw new ServerScopedRuntimeException("Task execution was interrupted: " + task, e);
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
                throw new ServerScopedRuntimeException("Failed to execute user task: " + task, cause);
            }
            else if (cause instanceof Error)
            {
                throw (Error) cause;
            }
            else
            {
                throw new ServerScopedRuntimeException("Failed to execute user task: " + task, cause);
            }
        }
    }

    private boolean isTaskExecutorThread()
    {
        return Thread.currentThread() == _taskThread;
    }

    private void checkState()
    {
        if (!_running.get())
        {
            throw new IllegalStateException("Task executor is not in ACTIVE state");
        }
    }

    private <T> T executeTask(Task<T> userTask)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Performing task " + userTask);
        }
        T result = userTask.execute();
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Task " + userTask + " is performed successfully with result:" + result);
        }
        return result;
    }

    private class CallableWrapper<T> implements Callable<T>
    {
        private Task<T> _userTask;
        private Subject _contextSubject;

        public CallableWrapper(Task<T> userWork)
        {
            _userTask = userWork;
            _contextSubject = Subject.getSubject(AccessController.getContext());
        }

        @Override
        public T call()
        {
            T result = null;
            result = Subject.doAs(_contextSubject, new PrivilegedAction<T>()
                {
                    @Override
                    public T run()
                    {
                        return executeTask(_userTask);
                    }
                });


            return result;
        }
    }

    private static class ImmediateFuture<T> implements Future<T>
    {
        private T _result;

        public ImmediateFuture(T result)
        {
            super();
            _result = result;
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
        public T get()
        {
            return _result;
        }

        @Override
        public T get(long timeout, TimeUnit unit)
        {
            return get();
        }
    }
}
