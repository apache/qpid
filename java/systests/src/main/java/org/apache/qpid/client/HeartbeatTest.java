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

import static org.apache.qpid.configuration.ClientProperties.AMQJ_HEARTBEAT_DELAY;
import static org.apache.qpid.configuration.ClientProperties.IDLE_TIMEOUT_PROP_NAME;
import static org.apache.qpid.configuration.ClientProperties.QPID_HEARTBEAT_INTERVAL;

import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class HeartbeatTest extends QpidBrokerTestCase
{
    private static final String CONNECTION_URL_WITH_HEARTBEAT = "amqp://guest:guest@clientid/?brokerlist='localhost:%d?heartbeat='%d''";
    private TestListener _listener = new TestListener();

    @Override
    public void setUp() throws Exception
    {
        if (getName().equals("testHeartbeatsEnabledBrokerSide"))
        {
            getBrokerConfiguration().setBrokerAttribute(Broker.CONNECTION_HEART_BEAT_DELAY, "1");
        }
        super.setUp();
    }

    public void testHeartbeatsEnabledUsingUrl() throws Exception
    {
        final String url = String.format(CONNECTION_URL_WITH_HEARTBEAT, DEFAULT_PORT, 1);
        AMQConnection conn = (AMQConnection) getConnection(new AMQConnectionURL(url));
        conn.setHeartbeatListener(_listener);
        conn.start();

        Thread.sleep(2500);

        assertTrue("Too few heartbeats received: "+_listener._heartbeatsReceived+" (expected at least 2)", _listener._heartbeatsReceived>=2);
        assertTrue("Too few heartbeats sent "+_listener._heartbeatsSent+" (expected at least 2)", _listener._heartbeatsSent>=2);

        conn.close();
    }

    public void testHeartbeatsEnabledUsingSystemProperty() throws Exception
    {
        setTestSystemProperty(QPID_HEARTBEAT_INTERVAL, "1");
        AMQConnection conn = (AMQConnection) getConnection();
        conn.setHeartbeatListener(_listener);
        conn.start();

        Thread.sleep(2500);

        assertTrue("Too few heartbeats received: "+_listener._heartbeatsReceived+" (expected at least 2)", _listener._heartbeatsReceived>=2);
        assertTrue("Too few heartbeats sent "+_listener._heartbeatsSent+" (expected at least 2)", _listener._heartbeatsSent>=2);

        conn.close();
    }

    public void testHeartbeatsDisabledUsingSystemProperty() throws Exception
    {
         setTestSystemProperty(QPID_HEARTBEAT_INTERVAL, "0");
         AMQConnection conn = (AMQConnection) getConnection();
         conn.setHeartbeatListener(_listener);
         conn.start();

         Thread.sleep(2500);

         assertEquals("Heartbeats unexpectedly received", 0, _listener._heartbeatsReceived);
         assertEquals("Heartbeats unexpectedly sent ", 0, _listener._heartbeatsSent);

         conn.close();
    }

    /**
     * This test carefully arranges message flow so that bytes flow only from producer to broker
     * on the producer side and broker to consumer on the consumer side, deliberately leaving the
     * reverse path quiet so heartbeats will flow.
     */
    public void testUnidirectionalHeartbeating() throws Exception
    {
        setTestSystemProperty(QPID_HEARTBEAT_INTERVAL,"1");
        AMQConnection receiveConn = (AMQConnection) getConnection();
        AMQConnection sendConn = (AMQConnection) getConnection();
        Destination destination = getTestQueue();
        TestListener receiveListener = new TestListener();
        TestListener sendListener = new TestListener();


        Session receiveSession = receiveConn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Session senderSession = sendConn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        MessageConsumer consumer = receiveSession.createConsumer(destination);
        MessageProducer producer = senderSession.createProducer(destination);

        receiveConn.setHeartbeatListener(receiveListener);
        sendConn.setHeartbeatListener(sendListener);
        receiveConn.start();

        for(int i = 0; i < 5; i++)
        {
            producer.send(senderSession.createTextMessage("Msg " + i));
            Thread.sleep(500);
            assertNotNull("Expected to received message", consumer.receive(500));
            // Consumer does not ack the message in  order to generate no bytes from consumer back to Broker
        }

        assertTrue("Too few heartbeats sent "+receiveListener._heartbeatsSent+" (expected at least 2)", receiveListener._heartbeatsSent>=2);
        assertEquals("Unexpected sent at the sender: ",0,sendListener._heartbeatsSent);

        assertTrue("Too few heartbeats received at the sender "+sendListener._heartbeatsReceived+" (expected at least 2)", sendListener._heartbeatsReceived>=2);
        assertEquals("Unexpected received at the receiver: ",0,receiveListener._heartbeatsReceived);

        receiveConn.close();
        sendConn.close();
    }

    public void testHeartbeatsEnabledBrokerSide() throws Exception
    {

        AMQConnection conn = (AMQConnection) getConnection();
        conn.setHeartbeatListener(_listener);
        conn.start();

        Thread.sleep(2500);

        assertTrue("Too few heartbeats received: "+_listener._heartbeatsReceived+" (expected at least 2)", _listener._heartbeatsReceived>=2);
        assertTrue("Too few heartbeats sent "+_listener._heartbeatsSent+" (expected at least 2)", _listener._heartbeatsSent>=2);

        conn.close();
    }


    @SuppressWarnings("deprecation")
    public void testHeartbeatsEnabledUsingAmqjLegacySystemProperty() throws Exception
    {
        setTestSystemProperty(AMQJ_HEARTBEAT_DELAY, "1");
        AMQConnection conn = (AMQConnection) getConnection();
        conn.setHeartbeatListener(_listener);
        conn.start();

        Thread.sleep(2500);

        assertTrue("Too few heartbeats received: "+_listener._heartbeatsReceived+" (expected at least 2)", _listener._heartbeatsReceived>=2);
        assertTrue("Too few heartbeats sent "+_listener._heartbeatsSent+" (expected at least 2)", _listener._heartbeatsSent>=2);

        conn.close();
    }

    @SuppressWarnings("deprecation")
    public void testHeartbeatsEnabledUsingOlderLegacySystemProperty() throws Exception
    {
        setTestSystemProperty(IDLE_TIMEOUT_PROP_NAME, "1000");
        AMQConnection conn = (AMQConnection) getConnection();
        conn.setHeartbeatListener(_listener);
        conn.start();

        Thread.sleep(2500);

        assertTrue("Too few heartbeats received: "+_listener._heartbeatsReceived+" (expected at least 2)", _listener._heartbeatsReceived>=2);
        assertTrue("Too few heartbeats sent "+_listener._heartbeatsSent+" (expected at least 2)", _listener._heartbeatsSent>=2);

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
