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
package org.apache.qpid.server.cluster;

import org.apache.mina.common.IoSession;

import java.util.List;
import java.util.ArrayList;

import junit.framework.TestCase;

public class InductionBufferTest extends TestCase
{
    public void test() throws Exception
    {
        IoSession session1 = new TestSession();
        IoSession session2 = new TestSession();
        IoSession session3 = new TestSession();

        TestMessageHandler handler = new TestMessageHandler();
        InductionBuffer buffer = new InductionBuffer(handler);

        buffer.receive(session1, "one");
        buffer.receive(session2, "two");
        buffer.receive(session3, "three");

        buffer.receive(session1, "four");
        buffer.receive(session1, "five");
        buffer.receive(session1, "six");

        buffer.receive(session3, "seven");
        buffer.receive(session3, "eight");

        handler.checkEmpty();
        buffer.deliver();

        handler.check(session1, "one");
        handler.check(session2, "two");
        handler.check(session3, "three");

        handler.check(session1, "four");
        handler.check(session1, "five");
        handler.check(session1, "six");

        handler.check(session3, "seven");
        handler.check(session3, "eight");
        handler.checkEmpty();

        buffer.receive(session1, "nine");
        buffer.receive(session2, "ten");
        buffer.receive(session3, "eleven");

        handler.check(session1, "nine");
        handler.check(session2, "ten");
        handler.check(session3, "eleven");        

        handler.checkEmpty();
    }

    private static class TestMessageHandler implements InductionBuffer.MessageHandler
    {
        private final List<IoSession> _sessions = new ArrayList<IoSession>();
        private final List<Object> _msgs = new ArrayList<Object>();

        public synchronized void deliver(IoSession session, Object msg) throws Exception
        {
            _sessions.add(session);
            _msgs.add(msg);
        }

        void check(IoSession actualSession, Object actualMsg)
        {
            assertFalse(_sessions.isEmpty());
            assertFalse(_msgs.isEmpty());
            IoSession expectedSession = _sessions.remove(0);
            Object expectedMsg = _msgs.remove(0);
            assertEquals(expectedSession, actualSession);
            assertEquals(expectedMsg, actualMsg);
        }

        void checkEmpty()
        {
            assertTrue(_sessions.isEmpty());
            assertTrue(_msgs.isEmpty());
        }
    }
}

