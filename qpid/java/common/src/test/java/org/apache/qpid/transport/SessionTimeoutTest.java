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
 */
package org.apache.qpid.transport;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.test.utils.QpidTestCase;


public class SessionTimeoutTest extends QpidTestCase
{
    public void testSessionTimeout()
    {
        try
        {
            long timeout = 1;
            setTestSystemProperty("qpid.sync_op_timeout", Long.toString(timeout));
            assertSessionTimeout(timeout);
        }
        finally
        {
            revertTestSystemProperties();
        }
    }

    public void testSessionTimeoutSetWith_amqj_default_syncwrite_timeout()
    {
        try
        {
            long timeout = 1;
            setTestSystemProperty("amqj.default_syncwrite_timeout", Long.toString(timeout));
            setTestSystemProperty("qpid.sync_op_timeout", null);
            assertSessionTimeout(timeout);
        }
        finally
        {
            revertTestSystemProperties();
        }
    }

    private void assertSessionTimeout(long timeout)
    {
        Session session = new TestSession(null, null, 0);
        long startTime = System.currentTimeMillis();
        try
        {
            session.awaitOpen();
            fail("SessionTimeoutException is expected!");
        }
        catch (SessionException e)
        {
            long elapsedTime = System.currentTimeMillis() - startTime;
            assertTrue("Expected timeout should happened in " + timeout + " ms but timeout occured in "
                    + elapsedTime + " ms!", elapsedTime >= timeout && elapsedTime < ClientProperties.DEFAULT_SYNC_OPERATION_TIMEOUT);
        }
    }

    class TestSession extends Session
    {
        public TestSession(Connection connection, Binary name, long expiry)
        {
            super(connection, name, expiry);
        }
    }

}
