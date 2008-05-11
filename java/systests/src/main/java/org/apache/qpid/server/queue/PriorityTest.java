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
package org.apache.qpid.server.queue;

import junit.framework.TestCase;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;

import javax.jms.*;
import javax.naming.NamingException;
import javax.naming.Context;
import javax.naming.spi.InitialContextFactory;
import java.util.Hashtable;

/** Test Case provided by client Non-functional Test NF101: heap exhaustion behaviour */
public class PriorityTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(PriorityTest.class);


    protected final String BROKER = "vm://:1";
    protected final String VHOST = "/test";
    protected final String QUEUE = "PriorityQueue";


    private static final int MSG_COUNT = 50;

    protected void setUp() throws Exception
    {
        super.setUp();

        if (usingInVMBroker())
        {
            TransportConnection.createVMBroker(1);
        }


    }

    private boolean usingInVMBroker()
    {
        return BROKER.startsWith("vm://");
    }

    protected void tearDown() throws Exception
    {
        if (usingInVMBroker())
        {
            TransportConnection.killAllVMBrokers();
        }
        super.tearDown();
    }

    public void testPriority() throws JMSException, NamingException, AMQException
    {
        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@TTL_TEST_ID" + VHOST + "?brokerlist='" + BROKER + "'");
        env.put("queue.queue", QUEUE);

        Context context = factory.getInitialContext(env);

        Connection producerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();

        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        final FieldTable arguments = new FieldTable();
        arguments.put(new AMQShortString("x-qpid-priorities"),10);

        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);

        Queue queue = (Queue) context.lookup("queue");

        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);






        producerConnection.start();


        MessageProducer producer = producerSession.createProducer(queue);





        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.setPriority(msg % 10);
            producer.send(nextMessage(msg, false, producerSession, producer));
        }

        producer.close();
        producerSession.close();
        producerConnection.close();


        Connection consumerConnection = ((ConnectionFactory) context.lookup("connection")).createConnection();
        Session consumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = consumerSession.createConsumer(queue);




        consumerConnection.start();

        Message received;
        //Receive Message 0
        StringBuilder buf = new StringBuilder();
        int receivedCount = 0;
        Message previous = null;
        while((received = consumer.receive(1000))!=null)
        {
            if(previous != null)
            {
                assertTrue("Messages arrived in unexpected order", (previous.getJMSPriority() > received.getJMSPriority()) || ((previous.getJMSPriority() == received.getJMSPriority()) && previous.getIntProperty("msg") < received.getIntProperty("msg")) );
            }

            previous = received;
            receivedCount++;
        }

        assertEquals("Incorrect number of message received", 50, receivedCount);

        producerSession.close();
        producer.close();

    }

    private Message nextMessage(int msg, boolean first, Session producerSession, MessageProducer producer) throws JMSException
    {
        Message send = producerSession.createTextMessage("Message: " + msg);
        send.setIntProperty("msg", msg);

        return send;
    }


}
