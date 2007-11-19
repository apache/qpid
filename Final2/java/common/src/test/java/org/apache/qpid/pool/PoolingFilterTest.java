/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.pool;

import junit.framework.TestCase;
import junit.framework.Assert;
import org.apache.qpid.session.TestSession;
import org.apache.mina.common.IoFilter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IdleStatus;

import java.util.concurrent.RejectedExecutionException;

public class PoolingFilterTest extends TestCase
{
    private PoolingFilter _pool;
    ReferenceCountingExecutorService _executorService;

    public void setUp()
    {

        //Create Pool
        _executorService = ReferenceCountingExecutorService.getInstance();
        _executorService.acquireExecutorService();
        _pool = PoolingFilter.createAynschWritePoolingFilter(_executorService,
                                  "AsynchronousWriteFilter");

    }

    public void testRejectedExecution() throws Exception
    {

        TestSession testSession = new TestSession();
        _pool.createNewJobForSession(testSession);
        _pool.filterWrite(new NoOpFilter(), testSession, new IoFilter.WriteRequest("Message"));

        //Shutdown the pool
        _executorService.getPool().shutdownNow();

        try
        {

            testSession = new TestSession();
            _pool.createNewJobForSession(testSession);
            //prior to fix for QPID-172 this would throw RejectedExecutionException
            _pool.filterWrite(null, testSession, null);
        }
        catch (RejectedExecutionException rje)
        {
            Assert.fail("RejectedExecutionException should not occur after pool has shutdown:" + rje);
        }
    }

    private static class NoOpFilter implements IoFilter.NextFilter
    {

        public void sessionOpened(IoSession session)
        {
        }

        public void sessionClosed(IoSession session)
        {
        }

        public void sessionIdle(IoSession session, IdleStatus status)
        {
        }

        public void exceptionCaught(IoSession session, Throwable cause)
        {
        }

        public void messageReceived(IoSession session, Object message)
        {
        }

        public void messageSent(IoSession session, Object message)
        {
        }

        public void filterWrite(IoSession session, IoFilter.WriteRequest writeRequest)
        {
        }

        public void filterClose(IoSession session)
        {
        }

        public void sessionCreated(IoSession session)
        {
        }
    }
}
