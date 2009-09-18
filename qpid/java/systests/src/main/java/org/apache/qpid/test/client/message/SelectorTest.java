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
package org.apache.qpid.test.client.message;

import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import junit.framework.Assert;

import org.apache.log4j.BasicConfigurator;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.BasicMessageProducer;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SelectorTest extends QpidTestCase implements MessageListener
{
    private static final Logger _logger = LoggerFactory.getLogger(SelectorTest.class);

    private AMQConnection _connection;
    private AMQDestination _destination;
    private AMQSession _session;
    private int count;
    public String _connectionString = "vm://:1";
    private static final String INVALID_SELECTOR = "Cost LIKE 5";
    CountDownLatch _responseLatch = new CountDownLatch(1);

    private static final String BAD_MATHS_SELECTOR = " 1 % 5";

    private static final long RECIEVE_TIMEOUT = 1000;

    protected void setUp() throws Exception
    {
        super.setUp();
        BasicConfigurator.configure();
        init((AMQConnection) getConnection("guest", "guest"));
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        if (_session != null)
        {
            try
            {
                _session.close();
            }
            catch (JMSException e)
            {
                fail("Error cleaning up:" + e.getMessage());
            }
        }
        if (_connection != null)
        {
            try
            {
                _connection.close();
            }
            catch (JMSException e)
            {
                fail("Error cleaning up:" + e.getMessage());
            }
        }
    }

    private void init(AMQConnection connection) throws JMSException
    {
        init(connection, new AMQQueue(connection, randomize("SessionStartTest"), true));
    }

    private void init(AMQConnection connection, AMQDestination destination) throws JMSException
    {
        _connection = connection;
        _destination = destination;
        connection.start();

        String selector = null;
        selector = "Cost = 2 AND \"property-with-hyphen\" = 'wibble'";
        // selector = "JMSType = Special AND Cost = 2 AND AMQMessageID > 0 AND JMSDeliveryMode=" + DeliveryMode.NON_PERSISTENT;

        _session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        // _session.createConsumer(destination).setMessageListener(this);
        _session.createConsumer(destination, selector).setMessageListener(this);
    }

    public void test() throws Exception
    {
        try
        {
            init((AMQConnection) getConnection("guest", "guest", randomize("Client")));

            Message msg = _session.createTextMessage("Message");
            msg.setJMSPriority(1);
            msg.setIntProperty("Cost", 2);
            msg.setStringProperty("property-with-hyphen", "wibble");
            msg.setJMSType("Special");

            _logger.info("Sending Message:" + msg);

            ((BasicMessageProducer) _session.createProducer(_destination)).send(msg, DeliveryMode.NON_PERSISTENT);
            _logger.info("Message sent, waiting for response...");

            _responseLatch.await();

            if (count > 0)
            {
                _logger.info("Got message");
            }

            if (count == 0)
            {
                fail("Did not get message!");
                // throw new RuntimeException("Did not get message!");
            }
        }
        catch (JMSException e)
        {
            _logger.debug("JMS:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            if (!(e instanceof InvalidSelectorException))
            {
                fail("Wrong exception:" + e.getMessage());
            }
            else
            {
                System.out.println("SUCCESS!!");
            }
        }
        catch (InterruptedException e)
        {
            _logger.debug("IE :" + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
        catch (URLSyntaxException e)
        {
            _logger.debug("URL:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            fail("Wrong exception");
        }
        catch (AMQException e)
        {
            _logger.debug("AMQ:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            fail("Wrong exception");
        }

        finally
        {
            if (_session != null)
            {
                _session.close();
            }
            if (_connection != null)
            {
                _connection.close();
            }
        }
    }

    public void testUnparsableSelectors() throws Exception
    {
        Connection connection = getConnection("guest", "guest", randomize("Client"));
        _session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        boolean caught = false;

        //Try Creating a Browser
        try
        {
            _session.createBrowser(_session.createQueue("Ping"), INVALID_SELECTOR);
        }
        catch (JMSException e)
        {
            _logger.debug("JMS:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            if (!(e instanceof InvalidSelectorException))
            {
                fail("Wrong exception:" + e.getMessage());
            }
            caught = true;
        }
        assertTrue("No exception thrown!", caught);
        caught = false;

        //Try Creating a Consumer
        try
        {
            _session.createConsumer(_session.createQueue("Ping"), INVALID_SELECTOR);
        }
        catch (JMSException e)
        {
            _logger.debug("JMS:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            if (!(e instanceof InvalidSelectorException))
            {
                fail("Wrong exception:" + e.getMessage());
            }
            caught = true;
        }
        assertTrue("No exception thrown!", caught);
        caught = false;

        //Try Creating a Receiever
        try
        {
            _session.createReceiver(_session.createQueue("Ping"), INVALID_SELECTOR);
        }
        catch (JMSException e)
        {
            _logger.debug("JMS:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            if (!(e instanceof InvalidSelectorException))
            {
                fail("Wrong exception:" + e.getMessage());
            }
            caught = true;
        }
        assertTrue("No exception thrown!", caught);
        caught = false;

        try
        {
            _session.createReceiver(_session.createQueue("Ping"), BAD_MATHS_SELECTOR);
        }
        catch (JMSException e)
        {
            _logger.debug("JMS:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            if (!(e instanceof InvalidSelectorException))
            {
                fail("Wrong exception:" + e.getMessage());
            }
            caught = true;
        }
        assertTrue("No exception thrown!", caught);
        caught = false;
        
    }
    
    public void testRuntimeSelectorError() throws JMSException
    {
        MessageConsumer consumer = _session.createConsumer(_destination , "testproperty % 5 = 1");
        MessageProducer producer = _session.createProducer(_destination);
        Message sentMsg = _session.createTextMessage();
        
        sentMsg.setIntProperty("testproperty", 1); // 1 % 5
        producer.send(sentMsg);
        Message recvd = consumer.receive(RECIEVE_TIMEOUT);
        assertNotNull(recvd);
        
        sentMsg.setStringProperty("testproperty", "hello"); // "hello" % 5 makes no sense
        producer.send(sentMsg);
        try
        {
            recvd = consumer.receive(RECIEVE_TIMEOUT);
            assertNull(recvd);
        }
        catch (Exception e)
        {
            
        }
        assertTrue("Connection should be closed", _connection.isClosed());
    }
        
    public void testSelectorWithJMSMessageID() throws Exception
    {
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(true, Session.AUTO_ACKNOWLEDGE);
        
        MessageProducer prod = session.createProducer(_destination);
        MessageConsumer consumer = session.createConsumer(_destination,"JMSMessageID IS NOT NULL");
        
        for (int i=0; i<2; i++)
        {
            Message msg = session.createTextMessage("Msg" + String.valueOf(i));
            prod.send(msg);
        }
        session.commit();
        
        Message msg1 = consumer.receive(1000);
        Message msg2 = consumer.receive(1000);
        
        Assert.assertNotNull("Msg1 should not be null", msg1);
        Assert.assertNotNull("Msg2 should not be null", msg2);
        
        session.commit();
        
        prod.setDisableMessageID(true);
        
        for (int i=0; i<2; i++)
        {
            Message msg = session.createTextMessage("Msg" + String.valueOf(i));
            prod.send(msg);
        }
        
        session.commit();
        Message msg3 = consumer.receive(1000);        
        Assert.assertNull("Msg3 should be null", msg3);
        session.commit();
        consumer = session.createConsumer(_destination,"JMSMessageID IS NULL");
        
        Message msg4 = consumer.receive(1000);
        Message msg5 = consumer.receive(1000);
        session.commit();
        Assert.assertNotNull("Msg4 should not be null", msg4);
        Assert.assertNotNull("Msg5 should not be null", msg5);
    }

    public void onMessage(Message message)
    {
        count++;
        _logger.info("Got Message:" + message);
        _responseLatch.countDown();
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    public static void main(String[] argv) throws Exception
    {
        SelectorTest test = new SelectorTest();
        test._connectionString = (argv.length == 0) ? "localhost:3000" : argv[0];

        try
        {
            while (true)
            {
                if (test._connectionString.contains("vm://:1"))
                {
                    test.setUp();
                }
                test.test();

                if (test._connectionString.contains("vm://:1"))
                {
                    test.tearDown();
                }
            }
        }
        catch (Exception e)
        {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(SelectorTest.class);
    }
}
