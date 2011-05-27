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

import java.io.IOException;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.management.common.JMXConnnectionFactory;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @todo Code to check that a consumer gets only one particular method could be factored into a re-usable method (as
 *       a static on a base test helper class, e.g. TestUtils.
 *
 * @todo Code to create test end-points using session per connection, or all sessions on one connection, to be factored
 *       out to make creating this test variation simpler. Want to make this variation available through LocalCircuit,
 *       driven by the test model.
 */
public class DurableSubscriptionTest extends QpidBrokerTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(DurableSubscriptionTest.class);
    
    /** Timeout for receive() if we are expecting a message */
    private static final long POSITIVE_RECEIVE_TIMEOUT = 2000;
    
    /** Timeout for receive() if we are not expecting a message */
    private static final long NEGATIVE_RECEIVE_TIMEOUT = 1000;
    
    private JMXConnector _jmxc;
    private MBeanServerConnection _mbsc;
    private static final String USER = "admin";
    private static final String PASSWORD = "admin";
    private boolean _jmxConnected;

    public void setUp() throws Exception
    {
        setConfigurationProperty("management.enabled", "true");     
        _jmxConnected=false;
        super.setUp();
    }

    public void tearDown() throws Exception
    {
        if(_jmxConnected)
        {
            try
            {
                _jmxc.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        
        super.tearDown();
    }
    
    public void testUnsubscribe() throws Exception
    {
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQTopic topic = new AMQTopic(con, "MyDurableSubscriptionTestTopic");
        _logger.info("Create Session 1");
        Session session1 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _logger.info("Create Consumer on Session 1");
        MessageConsumer consumer1 = session1.createConsumer(topic);
        _logger.info("Create Producer on Session 1");
        MessageProducer producer = session1.createProducer(topic);

        _logger.info("Create Session 2");
        Session session2 = con.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _logger.info("Create Durable Subscriber on Session 2");
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        _logger.info("Starting connection");
        con.start();

        _logger.info("Producer sending message A");
        producer.send(session1.createTextMessage("A"));
        
        ((AMQSession<?, ?>) session1).sync();
        
        //check the dur sub's underlying queue now has msg count 1
        AMQQueue subQueue = new AMQQueue("amq.topic", "clientid" + ":" + "MySubscription");
        assertEquals("Msg count should be 1", 1, ((AMQSession<?, ?>) session1).getQueueDepth(subQueue));

        Message msg;
        _logger.info("Receive message on consumer 1:expecting A");
        msg = consumer1.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Message should have been received",msg);
        assertEquals("A", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertEquals(null, msg);

        _logger.info("Receive message on consumer 2:expecting A");
        msg = consumer2.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Message should have been received",msg);
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(NEGATIVE_RECEIVE_TIMEOUT);
        _logger.info("Receive message on consumer 1 :expecting null");
        assertEquals(null, msg);
        
        ((AMQSession<?, ?>) session2).sync();
        
        //check the dur sub's underlying queue now has msg count 0
        assertEquals("Msg count should be 0", 0, ((AMQSession<?, ?>) session2).getQueueDepth(subQueue));

        consumer2.close();
        _logger.info("Unsubscribe session2/consumer2");
        session2.unsubscribe("MySubscription");
        
        ((AMQSession<?, ?>) session2).sync();
        
        if(isJavaBroker() && isExternalBroker())
        {
            //Verify that the queue was deleted by querying for its JMX MBean
            _jmxc = JMXConnnectionFactory.getJMXConnection(5000, "127.0.0.1",
                    getManagementPort(getPort()), USER, PASSWORD);

            _jmxConnected = true;
            _mbsc = _jmxc.getMBeanServerConnection();
            
            //must replace the occurrence of ':' in queue name with '-'
            String queueObjectNameText = "clientid" + "-" + "MySubscription";
            
            ObjectName objName = new ObjectName("org.apache.qpid:type=VirtualHost.Queue,name=" 
                                                + queueObjectNameText + ",*");
            
            Set<ObjectName> objectInstances = _mbsc.queryNames(objName, null);
            
            if(objectInstances.size() != 0)
            {
                fail("Queue MBean was found. Expected queue to have been deleted");
            }
            else
            {
                _logger.info("Underlying dueue for the durable subscription was confirmed deleted.");
            }
        }
        
        //verify unsubscribing the durable subscriber did not affect the non-durable one
        _logger.info("Producer sending message B");
        producer.send(session1.createTextMessage("B"));

        _logger.info("Receive message on consumer 1 :expecting B");
        msg = consumer1.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Message should have been received",msg);
        assertEquals("B", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertEquals(null, msg);

        _logger.info("Close connection");
        con.close();
    }
    
    public void testDurabilityNOACK() throws Exception
    {
        durabilityImpl(AMQSession.NO_ACKNOWLEDGE, false);
    }

    public void testDurabilityAUTOACK() throws Exception
    {
        durabilityImpl(Session.AUTO_ACKNOWLEDGE, false);
    }
    
    public void testDurabilityAUTOACKwithRestartIfPersistent() throws Exception
    {
        if(!isBrokerStorePersistent())
        {
            System.out.println("The broker store is not persistent, skipping this test.");
            return;
        }
        
        durabilityImpl(Session.AUTO_ACKNOWLEDGE, true);
    }

    public void testDurabilityNOACKSessionPerConnection() throws Exception
    {
        durabilityImplSessionPerConnection(AMQSession.NO_ACKNOWLEDGE);
    }

    public void testDurabilityAUTOACKSessionPerConnection() throws Exception
    {
        durabilityImplSessionPerConnection(Session.AUTO_ACKNOWLEDGE);
    }

    private void durabilityImpl(int ackMode, boolean restartBroker) throws Exception
    {        
        AMQConnection con = (AMQConnection) getConnection("guest", "guest");
        AMQTopic topic = new AMQTopic(con, "MyTopic");
        Session session1 = con.createSession(false, ackMode);
        MessageConsumer consumer1 = session1.createConsumer(topic);

        Session sessionProd = con.createSession(false, ackMode);
        MessageProducer producer = sessionProd.createProducer(topic);

        Session session2 = con.createSession(false, ackMode);
        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        con.start();

        //send message A and check both consumers receive
        producer.send(session1.createTextMessage("A"));

        Message msg;
        _logger.info("Receive message on consumer 1 :expecting A");
        msg = consumer1.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Message should have been received",msg);
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer1.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertEquals(null, msg);

        _logger.info("Receive message on consumer 2 :expecting A");
        msg = consumer2.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Message should have been received",msg);
        assertEquals("A", ((TextMessage) msg).getText());
        msg = consumer2.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertEquals(null, msg);

        //send message B, receive with consumer 1, and disconnect consumer 2 to leave the message behind (if not NO_ACK)
        producer.send(session1.createTextMessage("B"));

        _logger.info("Receive message on consumer 1 :expecting B");
        msg = consumer1.receive(500);
        assertNotNull("Consumer 1 should get message 'B'.", msg);
        assertEquals("Incorrect Message received on consumer1.", "B", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(500);
        assertNull("There should be no more messages for consumption on consumer1.", msg);

        consumer2.close();
        session2.close();
        
        //Send message C, then connect consumer 3 to durable subscription and get
        //message B if not using NO_ACK, then receive C with consumer 1 and 3
        producer.send(session1.createTextMessage("C"));

        Session session3 = con.createSession(false, ackMode);
        MessageConsumer consumer3 = session3.createDurableSubscriber(topic, "MySubscription");

        if(ackMode == AMQSession.NO_ACKNOWLEDGE)
        {
            //Do nothing if NO_ACK was used, as prefetch means the message was dropped
            //when we didn't call receive() to get it before closing consumer 2
        }
        else
        {
            _logger.info("Receive message on consumer 3 :expecting B");
            msg = consumer3.receive(500);
            assertNotNull("Consumer 3 should get message 'B'.", msg);
            assertEquals("Incorrect Message received on consumer3.", "B", ((TextMessage) msg).getText());
        }

        _logger.info("Receive message on consumer 1 :expecting C");
        msg = consumer1.receive(500);
        assertNotNull("Consumer 1 should get message 'C'.", msg);
        assertEquals("Incorrect Message received on consumer1.", "C", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(500);
        assertNull("There should be no more messages for consumption on consumer1.", msg);

        _logger.info("Receive message on consumer 3 :expecting C");
        msg = consumer3.receive(500);
        assertNotNull("Consumer 3 should get message 'C'.", msg);
        assertEquals("Incorrect Message received on consumer3.", "C", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 3 :expecting null");
        msg = consumer3.receive(500);
        assertNull("There should be no more messages for consumption on consumer3.", msg);

        consumer1.close();
        consumer3.close();

        session3.unsubscribe("MySubscription");

        con.close();
        
        if(restartBroker)
        {
            try
            {
                restartBroker();
            }
            catch (Exception e)
            {
                fail("Error restarting the broker");
            }
        }
    }

    private void durabilityImplSessionPerConnection(int ackMode) throws Exception
    {
        Message msg;
        // Create producer.
        AMQConnection con0 = (AMQConnection) getConnection("guest", "guest");
        con0.start();
        Session session0 = con0.createSession(false, ackMode);

        AMQTopic topic = new AMQTopic(con0, "MyTopic");

        Session sessionProd = con0.createSession(false, ackMode);
        MessageProducer producer = sessionProd.createProducer(topic);

        // Create consumer 1.
        AMQConnection con1 = (AMQConnection) getConnection("guest", "guest");
        con1.start();
        Session session1 = con1.createSession(false, ackMode);

        MessageConsumer consumer1 = session1.createConsumer(topic);

        // Create consumer 2.
        AMQConnection con2 = (AMQConnection) getConnection("guest", "guest");
        con2.start();
        Session session2 = con2.createSession(false, ackMode);

        TopicSubscriber consumer2 = session2.createDurableSubscriber(topic, "MySubscription");

        // Send message and check that both consumers get it and only it.
        producer.send(session0.createTextMessage("A"));

        msg = consumer1.receive(500);
        assertNotNull("Message should be available", msg);
        assertEquals("Message Text doesn't match", "A", ((TextMessage) msg).getText());
        msg = consumer1.receive(500);
        assertNull("There should be no more messages for consumption on consumer1.", msg);

        msg = consumer2.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Message should have been received",msg);
        assertEquals("Consumer 2 should also received the first msg.", "A", ((TextMessage) msg).getText());
        msg = consumer2.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertNull("There should be no more messages for consumption on consumer2.", msg);

        // Send message and receive on consumer 1.
        producer.send(session0.createTextMessage("B"));

        _logger.info("Receive message on consumer 1 :expecting B");
        msg = consumer1.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertEquals("B", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertEquals(null, msg);
        
        // Detach the durable subscriber.
        consumer2.close();
        session2.close();
        con2.close();
        
        // Send message C and receive on consumer 1
        producer.send(session0.createTextMessage("C"));

        _logger.info("Receive message on consumer 1 :expecting C");
        msg = consumer1.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertEquals("C", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 1 :expecting null");
        msg = consumer1.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertEquals(null, msg);

        // Re-attach a new consumer to the durable subscription, and check that it gets message B it left (if not NO_ACK)
        // and also gets message C sent after it was disconnected.
        AMQConnection con3 = (AMQConnection) getConnection("guest", "guest");
        con3.start();
        Session session3 = con3.createSession(false, ackMode);

        TopicSubscriber consumer3 = session3.createDurableSubscriber(topic, "MySubscription");

        if(ackMode == AMQSession.NO_ACKNOWLEDGE)
        {
            //Do nothing if NO_ACK was used, as prefetch means the message was dropped
            //when we didn't call receive() to get it before closing consumer 2
        }
        else
        {
            _logger.info("Receive message on consumer 3 :expecting B");
            msg = consumer3.receive(POSITIVE_RECEIVE_TIMEOUT);
            assertNotNull(msg);
            assertEquals("B", ((TextMessage) msg).getText());
        }
        
        _logger.info("Receive message on consumer 3 :expecting C");
        msg = consumer3.receive(POSITIVE_RECEIVE_TIMEOUT);
        assertNotNull("Consumer 3 should get message 'C'.", msg);
        assertEquals("Incorrect Message recevied on consumer3.", "C", ((TextMessage) msg).getText());
        _logger.info("Receive message on consumer 3 :expecting null");
        msg = consumer3.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertNull("There should be no more messages for consumption on consumer3.", msg);

        consumer1.close();
        consumer3.close();

        session3.unsubscribe("MySubscription");

        con0.close();
        con1.close();
        con3.close();
    }

    /**
     * This tests the fix for QPID-1085
     * Creates a durable subscriber with an invalid selector, checks that the
     * exception is thrown correctly and that the subscription is not created. 
     * @throws Exception 
     */
    public void testDurableWithInvalidSelector() throws Exception
    {
    	Connection conn = getConnection();
    	conn.start();
    	Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
    	AMQTopic topic = new AMQTopic((AMQConnection) conn, "MyTestDurableWithInvalidSelectorTopic");
    	MessageProducer producer = session.createProducer(topic);
    	producer.send(session.createTextMessage("testDurableWithInvalidSelector1"));
    	try 
    	{
    		TopicSubscriber deadSubscriber = session.createDurableSubscriber(topic, "testDurableWithInvalidSelectorSub",
																	 		 "=TEST 'test", true);
    		assertNull("Subscriber should not have been created", deadSubscriber);
    	} 
    	catch (JMSException e)
    	{
    		assertTrue("Wrong type of exception thrown", e instanceof InvalidSelectorException);
    	}
    	TopicSubscriber liveSubscriber = session.createDurableSubscriber(topic, "testDurableWithInvalidSelectorSub");
    	assertNotNull("Subscriber should have been created", liveSubscriber);

    	producer.send(session.createTextMessage("testDurableWithInvalidSelector2"));
    	
    	Message msg = liveSubscriber.receive(POSITIVE_RECEIVE_TIMEOUT);
    	assertNotNull ("Message should have been received", msg);
    	assertEquals ("testDurableWithInvalidSelector2", ((TextMessage) msg).getText());
    	assertNull("Should not receive subsequent message", liveSubscriber.receive(200));
        liveSubscriber.close();
        session.unsubscribe("testDurableWithInvalidSelectorSub");
    }
    
    /**
     * This tests the fix for QPID-1085
     * Creates a durable subscriber with an invalid destination, checks that the
     * exception is thrown correctly and that the subscription is not created. 
     * @throws Exception 
     */
    public void testDurableWithInvalidDestination() throws Exception
    {
    	Connection conn = getConnection();
    	conn.start();
    	Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
    	AMQTopic topic = new AMQTopic((AMQConnection) conn, "testDurableWithInvalidDestinationTopic");
    	try 
    	{
    		TopicSubscriber deadSubscriber = session.createDurableSubscriber(null, "testDurableWithInvalidDestinationsub");
    		assertNull("Subscriber should not have been created", deadSubscriber);
    	} 
    	catch (InvalidDestinationException e)
    	{
    		// This was expected
    	}
    	MessageProducer producer = session.createProducer(topic);    	
    	producer.send(session.createTextMessage("testDurableWithInvalidSelector1"));
    	
    	TopicSubscriber liveSubscriber = session.createDurableSubscriber(topic, "testDurableWithInvalidDestinationsub");
    	assertNotNull("Subscriber should have been created", liveSubscriber);
    	
    	producer.send(session.createTextMessage("testDurableWithInvalidSelector2"));
    	Message msg = liveSubscriber.receive(POSITIVE_RECEIVE_TIMEOUT);
    	assertNotNull ("Message should have been received", msg);
    	assertEquals ("testDurableWithInvalidSelector2", ((TextMessage) msg).getText());
    	assertNull("Should not receive subsequent message", liveSubscriber.receive(200));

        session.unsubscribe("testDurableWithInvalidDestinationsub");
    }
    
    /**
     * Creates a durable subscription with a selector, then changes that selector on resubscription
     * <p>
     * QPID-1202, QPID-2418
     */
    public void testResubscribeWithChangedSelector() throws Exception
    {
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        AMQTopic topic = new AMQTopic((AMQConnection) conn, "testResubscribeWithChangedSelector");
        MessageProducer producer = session.createProducer(topic);
        
        // Create durable subscriber that matches A
        TopicSubscriber subA = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelector",
                "Match = True", false);

        // Send 1 matching message and 1 non-matching message
        sendMatchingAndNonMatchingMessage(session, producer);

        Message rMsg = subA.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertNotNull(rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelector1",
                     ((TextMessage) rMsg).getText());
        
        rMsg = subA.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertNull(rMsg);
        
        // Disconnect subscriber
        subA.close();
        
        // Reconnect with new selector that matches B
        TopicSubscriber subB = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelector","Match = False", false);

        //verify no messages are now recieved.
        rMsg = subB.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertNull("Should not have received message as the selector was changed", rMsg);

        // Check that new messages are received properly
        sendMatchingAndNonMatchingMessage(session, producer);
        rMsg = subB.receive(POSITIVE_RECEIVE_TIMEOUT);

        assertNotNull("Message should have been received", rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelector2",
                     ((TextMessage) rMsg).getText());
        
        
        rMsg = subB.receive(NEGATIVE_RECEIVE_TIMEOUT);
        assertNull("Message should not have been received",rMsg);
        session.unsubscribe("testResubscribeWithChangedSelector");
    }

    public void testDurableSubscribeWithTemporaryTopic() throws Exception
    {
        Connection conn = getConnection();
        conn.start();
        Session ssn = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = ssn.createTemporaryTopic();
        try
        {
            ssn.createDurableSubscriber(topic, "test");
            fail("expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // this is expected
        }
        try
        {
            ssn.createDurableSubscriber(topic, "test", null, false);
            fail("expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // this is expected
        }
    }

    private void sendMatchingAndNonMatchingMessage(Session session, MessageProducer producer) throws JMSException
    {
        TextMessage msg = session.createTextMessage("testResubscribeWithChangedSelector1");
        msg.setBooleanProperty("Match", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelector2");
        msg.setBooleanProperty("Match", false);
        producer.send(msg);
    }


    /**
     * create and register a durable subscriber with a message selector and then close it
     * create a publisher and send  5 right messages and 5 wrong messages
     * create another durable subscriber with the same selector and name
     * check messages are still there
     * <p>
     * QPID-2418
     */
    public void testDurSubSameMessageSelector() throws Exception
    {        
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(true, Session.SESSION_TRANSACTED);
        AMQTopic topic = new AMQTopic((AMQConnection) conn, "sameMessageSelector");
                
        //create and register a durable subscriber with a message selector and then close it
        TopicSubscriber subOne = session.createDurableSubscriber(topic, "sameMessageSelector", "testprop = TRUE", false);
        subOne.close();

        MessageProducer producer = session.createProducer(topic);
        for (int i = 0; i < 5; i++)
        {
            Message message = session.createMessage();
            message.setBooleanProperty("testprop", true);
            producer.send(message);
            message = session.createMessage();
            message.setBooleanProperty("testprop", false);
            producer.send(message);
        }
        session.commit();
        producer.close();

        // should be 5 or 10 messages on queue now
        // (5 for the java broker due to use of server side selectors, and 10 for the cpp broker due to client side selectors only)
        AMQQueue queue = new AMQQueue("amq.topic", "clientid" + ":" + "sameMessageSelector");
        assertEquals("Queue depth is wrong", isJavaBroker() ? 5 : 10, ((AMQSession<?, ?>) session).getQueueDepth(queue));

        // now recreate the durable subscriber and check the received messages
        TopicSubscriber subTwo = session.createDurableSubscriber(topic, "sameMessageSelector", "testprop = TRUE", false);

        for (int i = 0; i < 5; i++)
        {
            Message message = subTwo.receive(1000);
            if (message == null)
            {
                fail("sameMessageSelector test failed. no message was returned");
            }
            else
            {
                assertEquals("sameMessageSelector test failed. message selector not reset",
                        "true", message.getStringProperty("testprop"));
            }
        }
        
        session.commit();
        
        // Check queue has no messages
        if (isJavaBroker())
        {
            assertEquals("Queue should be empty", 0, ((AMQSession<?, ?>) session).getQueueDepth(queue));
        }
        else
        {
            assertTrue("At most the queue should have only 1 message", ((AMQSession<?, ?>) session).getQueueDepth(queue) <= 1);
        }
        
        // Unsubscribe
        session.unsubscribe("sameMessageSelector");
        
        conn.close();
    }

    /**
     * <ul>
     * <li>create and register a durable subscriber with a message selector
     * <li>create another durable subscriber with a different selector and same name
     * <li>check first subscriber is now closed
     * <li>create a publisher and send messages
     * <li>check messages are received correctly
     * </ul>
     * <p>
     * QPID-2418
     */
    public void testResubscribeWithChangedSelectorNoClose() throws Exception
    {
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        AMQTopic topic = new AMQTopic((AMQConnection) conn, "testResubscribeWithChangedSelectorNoClose");
        
        // Create durable subscriber that matches A
        TopicSubscriber subA = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelectorNoClose",
                "Match = True", false);
        
        // Reconnect with new selector that matches B
        TopicSubscriber subB = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelectorNoClose",
                "Match = false", false);
        
        // First subscription has been closed
        try
        {
            subA.receive(1000);
            fail("First subscription was not closed");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        conn.stop();
        
        // Send 1 matching message and 1 non-matching message
        MessageProducer producer = session.createProducer(topic);
        TextMessage msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart1");
        msg.setBooleanProperty("Match", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart2");
        msg.setBooleanProperty("Match", false);
        producer.send(msg);

        // should be 1 or 2 messages on queue now
        // (1 for the java broker due to use of server side selectors, and 2 for the cpp broker due to client side selectors only)
        AMQQueue queue = new AMQQueue("amq.topic", "clientid" + ":" + "testResubscribeWithChangedSelectorNoClose");
        assertEquals("Queue depth is wrong", isJavaBroker() ? 1 : 2, ((AMQSession<?, ?>) session).getQueueDepth(queue));

        conn.start();
        
        Message rMsg = subB.receive(1000);
        assertNotNull(rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelectorAndRestart2",
                     ((TextMessage) rMsg).getText());
        
        rMsg = subB.receive(1000);
        assertNull(rMsg);
        
        // Check queue has no messages
        assertEquals("Queue should be empty", 0, ((AMQSession<?, ?>) session).getQueueDepth(queue));
        
        conn.close();
    }

    /**
     * <ul>
     * <li>create and register a durable subscriber with no message selector
     * <li>create another durable subscriber with a selector and same name
     * <li>check first subscriber is now closed
     * <li>create a publisher and send  messages
     * <li>check messages are recieved correctly
     * </ul>
     * <p>
     * QPID-2418
     */
    public void testDurSubAddMessageSelectorNoClose() throws Exception
    {        
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        AMQTopic topic = new AMQTopic((AMQConnection) conn, "subscriptionName");
                
        // create and register a durable subscriber with no message selector
        TopicSubscriber subOne = session.createDurableSubscriber(topic, "subscriptionName", null, false);

        // now create a durable subscriber with a selector
        TopicSubscriber subTwo = session.createDurableSubscriber(topic, "subscriptionName", "testprop = TRUE", false);

        // First subscription has been closed
        try
        {
            subOne.receive(1000);
            fail("First subscription was not closed");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        conn.stop();
        
        // Send 1 matching message and 1 non-matching message
        MessageProducer producer = session.createProducer(topic);
        TextMessage msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart1");
        msg.setBooleanProperty("testprop", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart2");
        msg.setBooleanProperty("testprop", false);
        producer.send(msg);

        // should be 1 or 2 messages on queue now
        // (1 for the java broker due to use of server side selectors, and 2 for the cpp broker due to client side selectors only)
        AMQQueue queue = new AMQQueue("amq.topic", "clientid" + ":" + "subscriptionName");
        assertEquals("Queue depth is wrong", isJavaBroker() ? 1 : 2, ((AMQSession<?, ?>) session).getQueueDepth(queue));
        
        conn.start();
        
        Message rMsg = subTwo.receive(1000);
        assertNotNull(rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelectorAndRestart1",
                     ((TextMessage) rMsg).getText());
        
        rMsg = subTwo.receive(1000);
        assertNull(rMsg);
        
        // Check queue has no messages
        assertEquals("Queue should be empty", 0, ((AMQSession<?, ?>) session).getQueueDepth(queue));
        
        conn.close();
    }

    /**
     * <ul>
     * <li>create and register a durable subscriber with no message selector
     * <li>try to create another durable with the same name, should fail
     * </ul>
     * <p>
     * QPID-2418
     */
    public void testDurSubNoSelectorResubscribeNoClose() throws Exception
    {        
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        AMQTopic topic = new AMQTopic((AMQConnection) conn, "subscriptionName");
                
        // create and register a durable subscriber with no message selector
        session.createDurableSubscriber(topic, "subscriptionName", null, false);

        // try to recreate the durable subscriber
        try
        {
            session.createDurableSubscriber(topic, "subscriptionName", null, false);
            fail("Subscription should not have been created");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
