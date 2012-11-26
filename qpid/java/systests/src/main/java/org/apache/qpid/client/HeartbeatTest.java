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

import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class HeartbeatTest extends QpidBrokerTestCase
{
    public void testHeartbeats() throws Exception
    {
        setTestSystemProperty("amqj.heartbeat.delay","1");
        AMQConnection conn = (AMQConnection) getConnection();
        TestListener listener = new TestListener();
        conn.setHeartbeatListener(listener);
        conn.start();

        Thread.sleep(2500);

        assertTrue("Too few heartbeats received: "+listener._heartbeatsReceived+" (expected at least 2)", listener._heartbeatsReceived>=2);
        assertTrue("Too few heartbeats sent "+listener._heartbeatsSent+" (expected at least 2)", listener._heartbeatsSent>=2);

        conn.close();
    }

    public void testNoHeartbeats() throws Exception
    {
         setTestSystemProperty("amqj.heartbeat.delay","0");
         AMQConnection conn = (AMQConnection) getConnection();
         TestListener listener = new TestListener();
         conn.setHeartbeatListener(listener);
         conn.start();

         Thread.sleep(2500);

         assertEquals("Heartbeats unexpectedly received", 0, listener._heartbeatsReceived);
         assertEquals("Heartbeats unexpectedly sent ", 0, listener._heartbeatsSent);

         conn.close();
    }

    private class TestListener implements HeartbeatListener
    {
        int _heartbeatsReceived;
        int _heartbeatsSent;
        @Override
        public void heartbeatReceived()
        {
            _heartbeatsReceived++;
        }

        @Override
        public void heartbeatSent()
        {
            _heartbeatsSent++;
        }
    }
}
