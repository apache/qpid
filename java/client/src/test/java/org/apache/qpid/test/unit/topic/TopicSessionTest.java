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
package org.apache.qpid.test.unit.topic;

import junit.framework.TestCase;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;

import javax.jms.TopicSession;
import javax.jms.TextMessage;
import javax.jms.TopicPublisher;
import javax.jms.MessageConsumer;

/**
 * @author Apache Software Foundation
 */
public class TopicSessionTest extends TestCase
{
    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }

    public void testTextMessageCreation() throws Exception
    {
        AMQTopic topic = new AMQTopic("MyTopic");
        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "test", "/test");
        TopicSession session1 = con.createTopicSession(false, AMQSession.NO_ACKNOWLEDGE);
        TopicPublisher publisher = session1.createPublisher(topic);
        MessageConsumer consumer1 = session1.createConsumer(topic);
        con.start();
        TextMessage tm = session1.createTextMessage("Hello");
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        String msgText = tm.getText();
        assertEquals("Hello", msgText);
        tm = session1.createTextMessage();
        msgText = tm.getText();
        assertNull(msgText);
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        msgText = tm.getText();
        assertNull(msgText);
        tm.clearBody();
        tm.setText("Now we are not null");
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        msgText = tm.getText();
        assertEquals("Now we are not null", msgText);

        tm = session1.createTextMessage("");
        msgText = tm.getText();
        assertEquals("Empty string not returned", "", msgText);
        publisher.publish(tm);
        tm = (TextMessage) consumer1.receive(2000);
        assertNotNull(tm);
        assertEquals("Empty string not returned", "", msgText);
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TopicSessionTest.class);
    }
}
