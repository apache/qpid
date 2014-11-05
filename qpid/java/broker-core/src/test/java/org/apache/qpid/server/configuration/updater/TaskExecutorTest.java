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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import junit.framework.TestCase;

import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class TaskExecutorTest extends TestCase
{
    private TaskExecutorImpl _executor;

    protected void setUp() throws Exception
    {
        super.setUp();
        _executor = new TaskExecutorImpl();
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
        assertFalse("Unexpected initial state", _executor.isRunning());
    }

    public void testStart()
    {
        _executor.start();
        assertTrue("Unexpected started state", _executor.isRunning());
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
        Thread t1 = new Thread(runnable);
        t1.start();
        Thread t2 = new Thread(runnable);
        t2.start();

        final long timeout = 2000l;
        boolean awaitSubmissions = submitLatch.await(timeout, TimeUnit.MILLISECONDS);
        assertTrue(submitLatch.getCount() + " task(s) have not been submitted within expected time", awaitSubmissions);
        assertTrue("The first task has not been triggered", waitForCallLatch.await(timeout, TimeUnit.MILLISECONDS));

        _executor.stopImmediately();
        assertFalse("Unexpected stopped state", _executor.isRunning());

        Exception e = submitExceptions.poll(timeout, TimeUnit.MILLISECONDS);
        assertNotNull("The task execution was not interrupted or cancelled", e);
        Exception e2 = submitExceptions.poll(timeout, TimeUnit.MILLISECONDS);
        assertNotNull("The task execution was not interrupted or cancelled", e2);

        assertTrue("One of the exceptions should be CancellationException:", e2 instanceof CancellationException
                || e instanceof CancellationException);
        assertTrue("One of the exceptions should be InterruptedException:", e2 instanceof InterruptedException
                || e instanceof InterruptedException);

        t1.join(timeout);
        t2.join(timeout);
    }

    public void testStop()
    {
        _executor.start();
        _executor.stop();
        assertFalse("Unexpected stopped state", _executor.isRunning());
    }

    public void testSubmitAndWait() throws Exception
    {
        _executor.start();
        Object result = _executor.run(new Task<Object>()
        {
            @Override
            public String execute()
            {
                return "DONE";
            }
        });
        assertEquals("Unexpected task execution result", "DONE", result);
    }

    public void testSubmitAndWaitInNotAuthorizedContext()
    {
        _executor.start();
        Object subject = _executor.run(new SubjectRetriever());
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
                return _executor.run(new SubjectRetriever());
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
                return _executor.run(new SubjectRetriever());
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
            _executor.run(new Task<Object>()
            {

                @Override
                public Void execute()
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
        Subject subject = new Subject();
        final AtomicReference<Subject> taskSubject = new AtomicReference<Subject>();
        Subject.doAs(subject, new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                _executor.run(new Task<Object>()
                {
                    @Override
                    public Void execute()
                    {
                        taskSubject.set(Subject.getSubject(AccessController.getContext()));
                        return null;
                    }
                });
                return null;
            }
        });

        assertEquals("Unexpected security manager subject", subject, taskSubject.get());
    }

    private class SubjectRetriever implements Task<Subject>
    {
        @Override
        public Subject execute()
        {
            return Subject.getSubject(AccessController.getContext());
        }
    }

    private class NeverEndingCallable implements Task<Void>
    {
        private CountDownLatch _waitLatch;

        public NeverEndingCallable(CountDownLatch waitLatch)
        {
            super();
            _waitLatch = waitLatch;
        }

        @Override
        public Void execute()
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
