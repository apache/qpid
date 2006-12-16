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
package org.apache.qpid.test.unit.ack;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.apache.qpid.testutil.VMBrokerSetup;

import javax.jms.*;

import junit.framework.TestCase;

public class RecoverTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(RecoverTest.class);

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
        //Thread.sleep(2000);
    }



    public void testRecoverResendsMsgs() throws Exception
    {
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "/test");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = new AMQQueue("someQ", "someQ", false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        //force synch to ensure the consumer has resulted in a bound queue
        ((AMQSession) consumerSession).declareExchangeSynch("amq.direct", "direct");

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "/test");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        con2.close();

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        tm.acknowledge();
        _logger.info("Received and acknowledged first message");
        consumer.receive();
        consumer.receive();
        consumer.receive();
        _logger.info("Received all four messages. Calling recover with three outstanding messages");
        // no ack for last three messages so when I call recover I expect to get three messages back
        consumerSession.recover();
        tm = (TextMessage) consumer.receive(3000);
        assertEquals("msg2", tm.getText());

        tm = (TextMessage) consumer.receive(3000);
        assertEquals("msg3", tm.getText());

        tm = (TextMessage) consumer.receive(3000);
        assertEquals("msg4", tm.getText());

        _logger.info("Received redelivery of three messages. Acknowledging last message");
        tm.acknowledge();

        _logger.info("Calling acknowledge with no outstanding messages");
        // all acked so no messages to be delivered
        consumerSession.recover();

        tm = (TextMessage) consumer.receiveNoWait();
        assertNull(tm);
        _logger.info("No messages redelivered as is expected");

        con.close();
    }


    public void testRecoverResendsMsgsAckOnEarlier() throws Exception
    {
        Connection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "/test");

        Session consumerSession = con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = new AMQQueue("someQ", "someQ", false, true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);
        //force synch to ensure the consumer has resulted in a bound queue
        ((AMQSession) consumerSession).declareExchangeSynch("amq.direct", "direct");

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "/test");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        _logger.info("Sending four messages");
        producer.send(producerSession.createTextMessage("msg1"));
        producer.send(producerSession.createTextMessage("msg2"));
        producer.send(producerSession.createTextMessage("msg3"));
        producer.send(producerSession.createTextMessage("msg4"));

        con2.close();

        _logger.info("Starting connection");
        con.start();
        TextMessage tm = (TextMessage) consumer.receive();
        TextMessage tm2 = (TextMessage) consumer.receive();
        tm.acknowledge();
        _logger.info("Received 2 messages, acknowledge() first message, should acknowledge both");

        consumer.receive();
        consumer.receive();
        _logger.info("Received all four messages. Calling recover with two outstanding messages");
        // no ack for last three messages so when I call recover I expect to get three messages back
        consumerSession.recover();
        TextMessage tm3 = (TextMessage) consumer.receive(3000);
        assertEquals("msg3", tm3.getText());

        TextMessage tm4 = (TextMessage) consumer.receive(3000);
        assertEquals("msg4", tm4.getText());


        _logger.info("Received redelivery of two messages. calling acknolwedgeThis() first of those message");
        ((org.apache.qpid.jms.Message)tm3).acknowledgeThis();

        _logger.info("Calling recover");
        // all acked so no messages to be delivered
        consumerSession.recover();

        tm4 = (TextMessage) consumer.receive(3000);
        assertEquals("msg4", tm4.getText());
        ((org.apache.qpid.jms.Message)tm4).acknowledgeThis();

        _logger.info("Calling recover");
        // all acked so no messages to be delivered
        consumerSession.recover();


        tm = (TextMessage) consumer.receiveNoWait();
        assertNull(tm);
        _logger.info("No messages redelivered as is expected");

        con.close();
    }


    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(RecoverTest.class);
    }
}
