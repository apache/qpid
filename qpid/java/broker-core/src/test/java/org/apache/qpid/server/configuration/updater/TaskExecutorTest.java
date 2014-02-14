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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.NullRootMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class TaskExecutorTest extends TestCase
{
    private TaskExecutor _executor;

    protected void setUp() throws Exception
    {
        super.setUp();
        _executor = new TaskExecutor();
    }

    protected void tearDown() throws Exception
    {
        try
        {
            _executor.stopImmediately();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testGetState()
    {
        assertEquals("Unexpected initial state", State.INITIALISING, _executor.getState());
    }

    public void testStart()
    {
        _executor.start();
        assertEquals("Unexpected started state", State.ACTIVE, _executor.getState());
    }

    public void testStopImmediately() throws Exception
    {
        _executor.start();
        final CountDownLatch submitLatch = new CountDownLatch(2);
        final CountDownLatch waitForCallLatch = new CountDownLatch(1);
        final BlockingQueue<Exception> submitExceptions = new LinkedBlockingQueue<Exception>();

        Runnable runnable = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    Future<Void> f = _executor.submit(new NeverEndingCallable(waitForCallLatch));
                    submitLatch.countDown();
                    f.get();
                }
                catch (Exception e)
                {
                    if (e instanceof ExecutionException)
                    {
                        e = (Exception) e.getCause();
                    }
                    if(e instanceof RuntimeException && e.getCause() instanceof Exception)
                    {
                        submitExceptions.add((Exception)e.getCause());
                    }
                    else
                    {
                        submitExceptions.add(e);
                    }
                }
            }
        };
        new Thread(runnable).start();
        new Thread(runnable).start();
        assertTrue("Tasks have not been submitted", submitLatch.await(1000, TimeUnit.MILLISECONDS));
        assertTrue("The first task has not been triggered", waitForCallLatch.await(1000, TimeUnit.MILLISECONDS));

        _executor.stopImmediately();
        assertEquals("Unexpected stopped state", State.STOPPED, _executor.getState());

        Exception e = submitExceptions.poll(1000l, TimeUnit.MILLISECONDS);
        assertNotNull("The task execution was not interrupted or cancelled", e);
        Exception e2 = submitExceptions.poll(1000l, TimeUnit.MILLISECONDS);
        assertNotNull("The task execution was not interrupted or cancelled", e2);

        assertTrue("One of the exceptions should be CancellationException:", e2 instanceof CancellationException
                || e instanceof CancellationException);
        assertTrue("One of the exceptions should be InterruptedException:", e2 instanceof InterruptedException
                || e instanceof InterruptedException);
    }

    public void testStop()
    {
        _executor.start();
        _executor.stop();
        assertEquals("Unexpected stopped state", State.STOPPED, _executor.getState());
    }

    public void testSubmitAndWait() throws Exception
    {
        _executor.start();
        Object result = _executor.submitAndWait(new TaskExecutor.Task<Object>()
        {
            @Override
            public String call()
            {
                return "DONE";
            }
        });
        assertEquals("Unexpected task execution result", "DONE", result);
    }

    public void testSubmitAndWaitInNotAuthorizedContext()
    {
        _executor.start();
        Object subject = _executor.submitAndWait(new SubjectRetriever());
        assertNull("Subject must be null", subject);
    }

    public void testSubmitAndWaitInAuthorizedContext()
    {
        _executor.start();
        Subject subject = new Subject();
        Object result = Subject.doAs(subject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                return _executor.submitAndWait(new SubjectRetriever());
            }
        });
        assertEquals("Unexpected subject", subject, result);
    }

    public void testSubmitAndWaitInAuthorizedContextWithNullSubject()
    {
        _executor.start();
        Object result = Subject.doAs(null, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                return _executor.submitAndWait(new SubjectRetriever());
            }
        });
        assertEquals("Unexpected subject", null, result);
    }

    public void testSubmitAndWaitReThrowsOriginalRuntimeException()
    {
        final RuntimeException exception = new RuntimeException();
        _executor.start();
        try
        {
            _executor.submitAndWait(new TaskExecutor.Task<Object>()
            {

                @Override
                public Void call()
                {
                    throw exception;
                }
            });
            fail("Exception is expected");
        }
        catch (Exception e)
        {
            assertEquals("Unexpected exception", exception, e);
        }
    }

    public void testSubmitAndWaitCurrentActorAndSecurityManagerSubjectAreRespected() throws Exception
    {
        _executor.start();
        LogActor actor = new TestLogActor(new NullRootMessageLogger());
        Subject subject = new Subject();
        Subject currentSecurityManagerSubject = SecurityManager.getThreadSubject();
        final AtomicReference<LogActor> taskLogActor = new AtomicReference<LogActor>();
        final AtomicReference<Subject> taskSubject = new AtomicReference<Subject>();
        try
        {
            CurrentActor.set(actor);
            SecurityManager.setThreadSubject(subject);
            _executor.submitAndWait(new TaskExecutor.Task<Object>()
            {
                @Override
                public Void call()
                {
                    taskLogActor.set(CurrentActor.get());
                    taskSubject.set(SecurityManager.getThreadSubject());
                    return null;
                }
            });
        }
        finally
        {
            SecurityManager.setThreadSubject(currentSecurityManagerSubject);
            CurrentActor.remove();
        }
        assertEquals("Unexpected task log actor", actor, taskLogActor.get());
        assertEquals("Unexpected security manager subject", subject, taskSubject.get());
    }

    private class SubjectRetriever implements TaskExecutor.Task<Subject>
    {
        @Override
        public Subject call()
        {
            return Subject.getSubject(AccessController.getContext());
        }
    }

    private class NeverEndingCallable implements TaskExecutor.Task<Void>
    {
        private CountDownLatch _waitLatch;

        public NeverEndingCallable(CountDownLatch waitLatch)
        {
            super();
            _waitLatch = waitLatch;
        }

        @Override
        public Void call()
        {
            if (_waitLatch != null)
            {
                _waitLatch.countDown();
            }

            // wait forever
            synchronized (this)
            {
                try
                {
                    this.wait();
                }
                catch (InterruptedException e)
                {
                    throw new ServerScopedRuntimeException(e);
                }
            }
            return null;
        }
    }
}
