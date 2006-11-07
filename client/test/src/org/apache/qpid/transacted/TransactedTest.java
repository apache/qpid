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
package org.apache.qpid.transacted;

import junit.framework.JUnit4TestAdapter;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;

import javax.jms.*;

public class TransactedTest
{
    private AMQQueue queue1;
    private AMQQueue queue2;

    private AMQConnection con;
    private Session session;
    private MessageConsumer consumer1;
    private MessageProducer producer2;

    private AMQConnection prepCon;
    private Session prepSession;
    private MessageProducer prepProducer1;

    private AMQConnection testCon;
    private Session testSession;
    private MessageConsumer testConsumer1;
    private MessageConsumer testConsumer2;

    @BeforeClass
    public static void setupVM()
    {
        System.setProperty("amqj.NoAutoCreateVMBroker", "true");
    }

    @Before
    public void setup() throws Exception
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create VM Broker: " + e.getMessage());
        }

        queue1 = new AMQQueue("Q1", false);
        queue2 = new AMQQueue("Q2", false);


        con = new AMQConnection("vm://:1", "guest", "guest", "TransactedTest", "/test");
        session = con.createSession(true, 0);
        consumer1 = session.createConsumer(queue1);
        producer2 = session.createProducer(queue2);
        con.start();

        prepCon = new AMQConnection("vm://:1", "guest", "guest", "PrepConnection", "/test");
        prepSession = prepCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        prepProducer1 = prepSession.createProducer(queue1);
        prepCon.start();

        //add some messages
        prepProducer1.send(prepSession.createTextMessage("A"));
        prepProducer1.send(prepSession.createTextMessage("B"));
        prepProducer1.send(prepSession.createTextMessage("C"));

        testCon = new AMQConnection("vm://:1", "guest", "guest", "TestConnection", "/test");
        testSession = testCon.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        testConsumer1 = testSession.createConsumer(queue1);
        testConsumer2 = testSession.createConsumer(queue2);
        testCon.start();

        // Sleep to ensure all queues have been created in the Broker.
        try
        {
            System.out.println("Finishing Setup");
            Thread.sleep(3000);
        }
        catch (InterruptedException e)
        {
            //do nothing
        }
        System.out.println("Setup Complete");
    }

    @After
    public void shutdown() throws Exception
    {
        con.close();
        testCon.close();
        prepCon.close();

        TransportConnection.killVMBroker(1);
    }

    @Test
    public void commit() throws Exception
    {
        //send and receive some messages
        producer2.send(session.createTextMessage("X"));
        producer2.send(session.createTextMessage("Y"));
        producer2.send(session.createTextMessage("Z"));
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        expect("C", consumer1.receive(1000));

        //commit
        session.commit();

        //ensure sent messages can be received and received messages are gone
        expect("X", testConsumer2.receive(1000));
        expect("Y", testConsumer2.receive(1000));
        expect("Z", testConsumer2.receive(1000));

        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }

    @Test
    public void rollback() throws Exception
    {
        producer2.send(session.createTextMessage("X"));
        producer2.send(session.createTextMessage("Y"));
        producer2.send(session.createTextMessage("Z"));
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        expect("C", consumer1.receive(1000));

        //rollback
        session.rollback();

        //ensure sent messages are not visible and received messages are requeued
        expect("A", consumer1.receive(1000));
        expect("B", consumer1.receive(1000));
        expect("C", consumer1.receive(1000));

        assertTrue(null == testConsumer1.receive(1000));
        assertTrue(null == testConsumer2.receive(1000));
    }

    private void expect(String text, Message msg) throws JMSException
    {
        assertTrue(msg instanceof TextMessage);
        assertEquals(text, ((TextMessage) msg).getText());
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TransactedTest.class);
    }

}
