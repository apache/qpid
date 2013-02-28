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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;

import org.apache.qpid.test.utils.QpidTestCase;

public class ExecutorWithNoLimitsTest extends QpidTestCase
{
    private final static Object RESULT = new Object();

    private ExecutorWithLimits _limiter = new ExecutorWithNoLimits();
    @SuppressWarnings("unchecked")
    private Callable<Object> _callback = mock(Callable.class);

    public void testNormalExecution() throws Exception
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
        catch (Exception e)
        {
            // PASS
        }
        verify(_callback).call();
    }
}
