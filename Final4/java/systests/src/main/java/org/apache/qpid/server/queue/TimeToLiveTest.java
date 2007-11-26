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
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.jndi.PropertiesFileInitialContextFactory;
import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.Message;
import javax.naming.spi.InitialContextFactory;
import javax.naming.Context;
import java.util.Hashtable;


/** Test Case provided by client Non-functional Test NF101: heap exhaustion behaviour */
public class TimeToLiveTest extends TestCase
{
    private static final Logger _logger = Logger.getLogger(TimeToLiveTest.class);


    protected final String BROKER = "vm://:1";
    protected final String VHOST = "/test";
    protected final String QUEUE = "TimeToLiveQueue";

    private final long TIME_TO_LIVE = 1000L;

    Context _context;

    private Connection _clientConnection, _producerConnection;

    private MessageConsumer _consumer;
    MessageProducer _producer;
    Session _clientSession, _producerSession;
    private static final int MSG_COUNT = 50;

    protected void setUp() throws Exception
    {
        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.createVMBroker(1);
        }
        InitialContextFactory factory = new PropertiesFileInitialContextFactory();

        Hashtable<String, String> env = new Hashtable<String, String>();

        env.put("connectionfactory.connection", "amqp://guest:guest@TTL_TEST_ID" + VHOST + "?brokerlist='" + BROKER + "'");
        env.put("queue.queue", QUEUE);

        _context = factory.getInitialContext(env);

        Queue queue = (Queue) _context.lookup("queue");

        //Create Client 1
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _clientSession = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _consumer = _clientSession.createConsumer(queue);

        //Create Producer
        _producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _producerConnection.start();

        _producerSession = _producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _producer = _producerSession.createProducer(queue);
    }

    protected void tearDown() throws Exception
    {
        _clientConnection.close();

        _producerConnection.close();
        super.tearDown();
        
        if (BROKER.startsWith("vm://"))
        {
            TransportConnection.killAllVMBrokers();
        }
    }

    public void test() throws JMSException
    {
        //Set TTL
        int msg = 0;
        _producer.send(nextMessage(String.valueOf(msg), true));

        _producer.setTimeToLive(TIME_TO_LIVE);

        for (; msg < MSG_COUNT - 2; msg++)
        {
            _producer.send(nextMessage(String.valueOf(msg), false));
        }

        //Reset TTL
        _producer.setTimeToLive(0L);
        _producer.send(nextMessage(String.valueOf(msg), false));

         try
        {
            // Sleep to ensure TTL reached
            Thread.sleep(2000);
        }
        catch (InterruptedException e)
        {

        }

        _clientConnection.start();

        //Receive Message 0
        Message received = _consumer.receive(100);
        Assert.assertNotNull("First message not received", received);
        Assert.assertTrue("First message doesn't have first set.", received.getBooleanProperty("first"));
        Assert.assertEquals("First message has incorrect TTL.", 0L, received.getLongProperty("TTL"));


        received = _consumer.receive(100);
        Assert.assertNotNull("Final message not received", received);
        Assert.assertFalse("Final message has first set.", received.getBooleanProperty("first"));
        Assert.assertEquals("Final message has incorrect TTL.", 0L, received.getLongProperty("TTL"));

        received = _consumer.receive(100);
        Assert.assertNull("More messages received", received);
    }

    private Message nextMessage(String msg, boolean first) throws JMSException
    {
        Message send = _producerSession.createTextMessage("Message " + msg);
        send.setBooleanProperty("first", first);
        send.setLongProperty("TTL", _producer.getTimeToLive());
        return send;
    }


}
