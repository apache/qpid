/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.ack;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.testutil.VmOrRemoteTestCase;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.junit.Assert;
import org.junit.Test;

import javax.jms.*;

public class DisconnectAndRedeliverTest extends VmOrRemoteTestCase
{
    private static final Logger _logger = Logger.getLogger(DisconnectAndRedeliverTest.class);

    static
    {
        //DOMConfigurator.configure("../etc/log4j.xml");
        DOMConfigurator.configure("broker/etc/log4j.xml");
    }

    /**
     * This tests that when there are unacknowledged messages on a channel they are requeued for delivery when
     * the channel is closed.
     * @throws Exception
     */
    @Test
    public void disconnectRedeliversMessages() throws Exception
    {
        Connection con = new AMQConnection("foo", 1, "guest", "guest", "consumer1", "/test");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        AMQQueue queue = new AMQQueue("someQ", "someQ", false, false);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        Connection con2 = new AMQConnection("bar", 2, "guest", "guest", "producer1", "/test");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        tm.acknowledge();
        _logger.info("Received and acknowledged first message");
        consumer.receive();
        consumer.receive();
        consumer.receive();
        _logger.info("Received all four messages. About to disconnect and reconnect");

        con.close();
        con = new AMQConnection("foo", 1, "guest", "guest", "consumer1", "/test");
        consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        consumer = consumerSession.createConsumer(queue);

        _logger.info("Starting second consumer connection");
        con.start();

        tm = (TextMessage) consumer.receive(3000);
        Assert.assertEquals(tm.getText(), "msg2");

        tm = (TextMessage) consumer.receive(3000);
        Assert.assertEquals(tm.getText(), "msg3");

        tm = (TextMessage) consumer.receive(3000);
        Assert.assertEquals(tm.getText(), "msg4");

        _logger.info("Received redelivery of three messages. Acknowledging last message");
        tm.acknowledge();

        con.close();

        con = new AMQConnection("foo", 1, "guest", "guest", "consumer1", "/test");
        consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        consumer = consumerSession.createConsumer(queue);
        _logger.info("Starting third consumer connection");
        con.start();
        tm = (TextMessage) consumer.receiveNoWait();
        Assert.assertNull(tm);
        _logger.info("No messages redelivered as is expected");
        con.close();
    }

    /**
     * Tests that unacknowledged messages are thrown away when the channel is closed and they cannot be
     * requeued (due perhaps to the queue being deleted).
     * @throws Exception
     */
    @Test
    public void disconnectWithTransientQueueThrowsAwayMessages() throws Exception
    {
        Connection con = new AMQConnection("foo", 1, "guest", "guest", "consumer1", "/test");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = new AMQQueue("someQ", "someQ", false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        Connection con2 = new AMQConnection("bar", 2, "guest", "guest", "producer1", "/test");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        tm.acknowledge();
        _logger.info("Received and acknowledged first message");
        consumer.receive();
        consumer.receive();
        consumer.receive();
        _logger.info("Received all four messages. About to disconnect and reconnect");

        con.close();
        con = new AMQConnection("foo", 1, "guest", "guest", "consumer1", "/test");
        consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        consumer = consumerSession.createConsumer(queue);

        _logger.info("Starting second consumer connection");
        con.start();

        tm = (TextMessage) consumer.receiveNoWait();
        Assert.assertNull(tm);
        _logger.info("No messages redelivered as is expected");
        TestableMemoryMessageStore store = (TestableMemoryMessageStore) ApplicationRegistry.getInstance().getMessageStore();
        Assert.assertTrue(store.getMessageMap().size() == 0);
        con.close();
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(DisconnectAndRedeliverTest.class);
    }
}
