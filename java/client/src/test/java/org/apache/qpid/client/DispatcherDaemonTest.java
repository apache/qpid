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
package org.apache.qpid.client;

import javax.jms.Session;

import org.apache.qpid.AMQException;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.url.URLSyntaxException;

public class DispatcherDaemonTest extends QpidTestCase
{
    private AMQSession<?,?> _session;

    public void tearDown() throws Exception
    {
        super.tearDown();
        if (_session != null && _session.getDispatcherThread() != null)
        {
            _session.getDispatcherThread().interrupt();
        }
    }

    public void testDispatcherIsRunInDaemonThreadWithNoMessageListener() throws Exception
    {
        _session = createSession();
        _session.startDispatcherIfNecessary();
        assertFalse("Dispatcher thread should be non daemon as qpid.jms.daemon.dispatcher is not set",
                _session.getDispatcherThread().isDaemon());
    }

    public void testDispatcherIsRunInDaemonThreadWithConsumerMessageListenerAndDaemonFlagOn() throws Exception
    {
        setTestSystemProperty(AMQSession.DAEMON_DISPATCHER, "true");
        _session = createSession();
        _session.startDispatcherIfNecessary();
        assertTrue("Dispatcher thread should be daemon as qpid.jms.daemon.dispatcher is set to true",
                _session.getDispatcherThread().isDaemon());
    }

    private AMQSession<?,?> createSession() throws AMQException, URLSyntaxException
    {
        AMQConnection amqConnection = new MockAMQConnection(
                "amqp://guest:guest@client/test?brokerlist='tcp://localhost:1'&maxprefetch='0'");

        AMQSession_0_8 session = new AMQSession_0_8(amqConnection, 1, true, Session.SESSION_TRANSACTED, 1, 1);
        return session;
    }

}
