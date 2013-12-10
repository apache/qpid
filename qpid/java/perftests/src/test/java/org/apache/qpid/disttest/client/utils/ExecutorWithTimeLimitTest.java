/*
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
package org.apache.qpid.disttest.client.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

import org.apache.qpid.test.utils.QpidTestCase;

public class ExecutorWithTimeLimitTest extends QpidTestCase
{
    private static final int TIMEOUT = 500;
    private static final Object RESULT = new Object();

    private ExecutorWithLimits _limiter;
    @SuppressWarnings("unchecked")
    private Callable<Object> _callback = mock(Callable.class);

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _limiter = new ExecutorWithTimeLimit(System.currentTimeMillis(), TIMEOUT);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        if (_limiter != null)
        {
            _limiter.shutdown();
        }
    }

    public void testCallableCompletesNormally() throws Exception
    {
        when(_callback.call()).thenReturn(RESULT);

        final Object actualResult = _limiter.execute(_callback);

        verify(_callback).call();
        assertEquals(RESULT, actualResult);
    }

    public void testCallableThrowsException() throws Exception
    {
        when(_callback.call()).thenThrow(new Exception("mocked exception"));

        try
        {
            _limiter.execute(_callback);
            fail("Exception not thrown");
        }
        catch (CancellationException ce)
        {
            fail("Wrong exception thrown");
        }
        catch (Exception e)
        {
            // PASS
        }
        verify(_callback).call();
    }

    public void testCallableNotRunDueToInsufficentTimeRemaining() throws Exception
    {
        long now = System.currentTimeMillis();
        ExecutorWithLimits shortTimeLimiter = new ExecutorWithTimeLimit(now - 100, 100);
        try
        {
            shortTimeLimiter.execute(_callback);
            fail("Exception not thrown");
        }
        catch (CancellationException ca)
        {
            // PASS
        }
        finally
        {
            shortTimeLimiter.shutdown();
        }

        verify(_callback, never()).call();
    }

    public void testExecutionInterruptedByTimeout() throws Exception
    {
        Callable<Void> oversleepingCallback = new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                Thread.sleep(TIMEOUT * 2);
                return null;
            }
        };

        try
        {
            _limiter.execute(oversleepingCallback);
            fail("Exception not thrown");
        }
        catch (CancellationException ca)
        {
            // PASS
        }
    }
}
