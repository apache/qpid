/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.test.unit.ct;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 *   Crash Recovery tests for durable subscription
 *
 */
public class DurableSubscriberTest extends QpidBrokerTestCase
{
    private final String _topicName = "durableSubscriberTopic";

    /**
     * test strategy:
     * create and register a durable subscriber then close it
     * create a publisher and send a persistant message followed by a non persistant message
     * crash and restart the broker
     * recreate the durable subscriber and check that only the first message is received
     */
    public void testDurSubRestoredAfterNonPersistentMessageSent() throws Exception
    {
        if (isBrokerStorePersistent() || !isBroker08())
        {
            TopicConnectionFactory factory = getConnectionFactory();
            Topic topic = (Topic) getInitialContext().lookup(_topicName);
            //create and register a durable subscriber then close it
            TopicConnection durConnection = factory.createTopicConnection("guest", "guest");
            TopicSession durSession = durConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicSubscriber durSub1 = durSession.createDurableSubscriber(topic, "dursub");
            durConnection.start();
            durSub1.close();
            durSession.close();
            durConnection.stop();

            //create a publisher and send a persistant message followed by a non persistant message
            TopicConnection pubConnection = factory.createTopicConnection("guest", "guest");
            TopicSession pubSession = pubConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicPublisher publisher = pubSession.createPublisher(topic);
            Message message = pubSession.createMessage();
            message.setIntProperty("count", 1);
            publisher.publish(message, javax.jms.DeliveryMode.PERSISTENT, javax.jms.Message.DEFAULT_PRIORITY,
                              javax.jms.Message.DEFAULT_TIME_TO_LIVE);
            message.setIntProperty("count", 2);
            publisher.publish(message, javax.jms.DeliveryMode.NON_PERSISTENT, javax.jms.Message.DEFAULT_PRIORITY,
                              javax.jms.Message.DEFAULT_TIME_TO_LIVE);
            publisher.close();
            pubSession.close();
            //now stop the server
            try
            {
                restartBroker();
            }
            catch (Exception e)
            {
                _logger.error("problems restarting broker: " + e);
                throw e;
            }
            //now recreate the durable subscriber and check the received messages
            factory = getConnectionFactory();
            topic = (Topic) getInitialContext().lookup(_topicName);
            TopicConnection durConnection2 = factory.createTopicConnection("guest", "guest");
            TopicSession durSession2 = durConnection2.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicSubscriber durSub2 = durSession2.createDurableSubscriber(topic, "dursub");
            durConnection2.start();
            Message m1 = durSub2.receive(1000);
            if (m1 == null)
            {
                assertTrue("testDurSubRestoredAfterNonPersistentMessageSent test failed. no message was returned",
                           false);
            }
            assertTrue("testDurSubRestoredAfterNonPersistentMessageSent test failed. Wrong message was returned.",
                       m1.getIntProperty("count") == 1);
            durSession2.unsubscribe("dursub");
            durConnection2.close();
        }
    }

    /**
     * create and register a durable subscriber with a message selector and then close it
     * crash the broker
     * create a publisher and send  5 right messages and 5 wrong messages
     * recreate the durable subscriber and check we receive the 5 expected messages
     */
    public void testDurSubRestoresMessageSelector() throws Exception
    {
        if (isBrokerStorePersistent() || !isBroker08())
        {
            TopicConnectionFactory factory = getConnectionFactory();
            Topic topic = (Topic) getInitialContext().lookup(_topicName);
            //create and register a durable subscriber with a message selector and then close it
            TopicConnection durConnection = factory.createTopicConnection("guest", "guest");
            TopicSession durSession = durConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicSubscriber durSub1 = durSession.createDurableSubscriber(topic, "dursub", "testprop='true'", false);
            durConnection.start();
            durSub1.close();
            durSession.close();
            durConnection.stop();
            //now stop the server
            try
            {
                restartBroker();
            }
            catch (Exception e)
            {
                _logger.error("problems restarting broker: " + e);
                throw e;
            }
            topic = (Topic) getInitialContext().lookup(_topicName);
            factory = getConnectionFactory();
            TopicConnection pubConnection = factory.createTopicConnection("guest", "guest");
            TopicSession pubSession = pubConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicPublisher publisher = pubSession.createPublisher(topic);
            for (int i = 0; i < 5; i++)
            {
                Message message = pubSession.createMessage();
                message.setStringProperty("testprop", "true");
                publisher.publish(message);
                message = pubSession.createMessage();
                message.setStringProperty("testprop", "false");
                publisher.publish(message);
            }
            publisher.close();
            pubSession.close();

            //now recreate the durable subscriber and check the received messages
            TopicConnection durConnection2 = factory.createTopicConnection("guest", "guest");
            TopicSession durSession2 = durConnection2.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicSubscriber durSub2 = durSession2.createDurableSubscriber(topic, "dursub", "testprop='true'", false);
            durConnection2.start();
            for (int i = 0; i < 5; i++)
            {
                Message message = durSub2.receive(1000);
                if (message == null)
                {
                    assertTrue("testDurSubRestoresMessageSelector test failed. no message was returned", false);
                }
                else
                {
                    assertTrue("testDurSubRestoresMessageSelector test failed. message selector not reset",
                               message.getStringProperty("testprop").equals("true"));
                }
            }
            durSession2.unsubscribe("dursub");
            durConnection2.close();
        }
    }
    
    /**
     * create and register a durable subscriber without a message selector and then unsubscribe it
     * create and register a durable subscriber with a message selector and then close it
     * restart the broker
     * send matching and non matching messages
     * recreate and register the durable subscriber with a message selector
     * verify only the matching messages are received
     */
    public void testDurSubChangedToHaveSelectorThenRestart() throws Exception
    {
        if (! isBrokerStorePersistent())
        {
            _logger.warn("Test skipped due to requirement of a persistent store");
            return;
        }
        
        final String SUB_NAME=getTestQueueName();
        
        TopicConnectionFactory factory = getConnectionFactory();
        Topic topic = (Topic) getInitialContext().lookup(_topicName);
        
        //create and register a durable subscriber then unsubscribe it
        TopicConnection durConnection = factory.createTopicConnection("guest", "guest");
        TopicSession durSession = durConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber durSub1 = durSession.createDurableSubscriber(topic, SUB_NAME);
        durConnection.start();
        durSub1.close();
        durSession.unsubscribe(SUB_NAME);
        durSession.close();
        durConnection.close();

        //create and register a durable subscriber with a message selector and then close it
        TopicConnection durConnection2 = factory.createTopicConnection("guest", "guest");
        TopicSession durSession2 = durConnection2.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber durSub2 = durSession2.createDurableSubscriber(topic, SUB_NAME, "testprop='true'", false);
        durConnection2.start();
        durSub2.close();
        durSession2.close();
        durConnection2.close();
        
        //now restart the server
        try
        {
            restartBroker();
        }
        catch (Exception e)
        {
            _logger.error("problems restarting broker: " + e);
            throw e;
        }
        
        //send messages matching and not matching the selector
        TopicConnection pubConnection = factory.createTopicConnection("guest", "guest");
        TopicSession pubSession = pubConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicPublisher publisher = pubSession.createPublisher(topic);
        for (int i = 0; i < 5; i++)
        {
            Message message = pubSession.createMessage();
            message.setStringProperty("testprop", "true");
            publisher.publish(message);
            message = pubSession.createMessage();
            message.setStringProperty("testprop", "false");
            publisher.publish(message);
        }
        publisher.close();
        pubSession.close();

        //now recreate the durable subscriber with selector to check there are no exceptions generated
        //and then verify the messages are received correctly
        TopicConnection durConnection3 = (TopicConnection) factory.createConnection("guest", "guest");
        TopicSession durSession3 = (TopicSession) durConnection3.createSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber durSub3 = durSession3.createDurableSubscriber(topic, SUB_NAME, "testprop='true'", false);
        durConnection3.start();
        
        for (int i = 0; i < 5; i++)
        {
            Message message = durSub3.receive(2000);
            if (message == null)
            {
                fail("testDurSubChangedToHaveSelectorThenRestart test failed. Expected message " + i + " was not returned");
            }
            else
            {
                assertTrue("testDurSubChangedToHaveSelectorThenRestart test failed. Got message not matching selector",
                           message.getStringProperty("testprop").equals("true"));
            }
        }

        durSub3.close();
        durSession3.unsubscribe(SUB_NAME);
        durSession3.close();
        durConnection3.close();
    }

    
    /**
     * create and register a durable subscriber with a message selector and then unsubscribe it
     * create and register a durable subscriber without a message selector and then close it
     * restart the broker
     * send matching and non matching messages
     * recreate and register the durable subscriber without a message selector
     * verify ALL the sent messages are received
     */
    public void testDurSubChangedToNotHaveSelectorThenRestart() throws Exception
    {
        if (! isBrokerStorePersistent())
        {
            _logger.warn("Test skipped due to requirement of a persistent store");
            return;
        }
        
        final String SUB_NAME=getTestQueueName();
        
        TopicConnectionFactory factory = getConnectionFactory();
        Topic topic = (Topic) getInitialContext().lookup(_topicName);
        
        //create and register a durable subscriber with selector then unsubscribe it
        TopicConnection durConnection = factory.createTopicConnection("guest", "guest");
        TopicSession durSession = durConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber durSub1 = durSession.createDurableSubscriber(topic, SUB_NAME, "testprop='true'", false);
        durConnection.start();
        durSub1.close();
        durSession.unsubscribe(SUB_NAME);
        durSession.close();
        durConnection.close();

        //create and register a durable subscriber without the message selector and then close it
        TopicConnection durConnection2 = factory.createTopicConnection("guest", "guest");
        TopicSession durSession2 = durConnection2.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber durSub2 = durSession2.createDurableSubscriber(topic, SUB_NAME);
        durConnection2.start();
        durSub2.close();
        durSession2.close();
        durConnection2.close();
        
        //now restart the server
        try
        {
            restartBroker();
        }
        catch (Exception e)
        {
            _logger.error("problems restarting broker: " + e);
            throw e;
        }
        
        //send messages matching and not matching the original used selector
        TopicConnection pubConnection = factory.createTopicConnection("guest", "guest");
        TopicSession pubSession = pubConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicPublisher publisher = pubSession.createPublisher(topic);
        for (int i = 1; i <= 5; i++)
        {
            Message message = pubSession.createMessage();
            message.setStringProperty("testprop", "true");
            publisher.publish(message);
            message = pubSession.createMessage();
            message.setStringProperty("testprop", "false");
            publisher.publish(message);
        }
        publisher.close();
        pubSession.close();

        //now recreate the durable subscriber without selector to check there are no exceptions generated
        //then verify ALL messages sent are received
        TopicConnection durConnection3 = (TopicConnection) factory.createConnection("guest", "guest");
        TopicSession durSession3 = (TopicSession) durConnection3.createSession(false, Session.AUTO_ACKNOWLEDGE);
        TopicSubscriber durSub3 = durSession3.createDurableSubscriber(topic, SUB_NAME);
        durConnection3.start();
        
        for (int i = 1; i <= 10; i++)
        {
            Message message = durSub3.receive(2000);
            if (message == null)
            {
                fail("testDurSubChangedToNotHaveSelectorThenRestart test failed. Expected message " + i + " was not received");
            }
        }
        
        durSub3.close();
        durSession3.unsubscribe(SUB_NAME);
        durSession3.close();
        durConnection3.close();
    }
    
    
    public void testResubscribeWithChangedSelectorAndRestart() throws Exception
    {
        if (! isBrokerStorePersistent())
        {
            _logger.warn("Test skipped due to requirement of a persistent store");
            return;
        }
        
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        AMQTopic topic = new AMQTopic((AMQConnection) conn, "testResubscribeWithChangedSelectorAndRestart");
        MessageProducer producer = session.createProducer(topic);
        
        // Create durable subscriber that matches A
        TopicSubscriber subA = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelector",
                "Match = True", false);

        // Send 1 matching message and 1 non-matching message
        TextMessage msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart1");
        msg.setBooleanProperty("Match", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart2");
        msg.setBooleanProperty("Match", false);
        producer.send(msg);

        Message rMsg = subA.receive(1000);
        assertNotNull(rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelectorAndRestart1",
                     ((TextMessage) rMsg).getText());

        // Queue has no messages left
        AMQQueue subQueueTmp = new AMQQueue("amq.topic", "clientid" + ":" + "testResubscribeWithChangedSelectorAndRestart");
        assertEquals("Msg count should be 0", 0, ((AMQSession<?, ?>) session).getQueueDepth(subQueueTmp));
        
        rMsg = subA.receive(1000);
        assertNull(rMsg);
        
        // Send another 1 matching message and 1 non-matching message
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart1");
        msg.setBooleanProperty("Match", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart2");
        msg.setBooleanProperty("Match", false);
        producer.send(msg);
        
        // Disconnect subscriber without receiving the message to 
        //leave it on the underlying queue
        subA.close();
        
        // Reconnect with new selector that matches B
        TopicSubscriber subB = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelectorAndRestart",
                "Match = false", false);
        
        //verify no messages are now present on the queue as changing selector should have issued
        //an unsubscribe and thus deleted the previous durable backing queue for the subscription.
        //check the dur sub's underlying queue now has msg count 0
        AMQQueue subQueue = new AMQQueue("amq.topic", "clientid" + ":" + "testResubscribeWithChangedSelectorAndRestart");
        assertEquals("Msg count should be 0", 0, ((AMQSession<?, ?>) session).getQueueDepth(subQueue));
        
        // Check that new messages are received properly
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart1");
        msg.setBooleanProperty("Match", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart2");
        msg.setBooleanProperty("Match", false);
        producer.send(msg);
        
        rMsg = subB.receive(1000);
        assertNotNull(rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelectorAndRestart2",
                     ((TextMessage) rMsg).getText());
        
        rMsg = subB.receive(1000);
        assertNull(rMsg);

        //check the dur sub's underlying queue now has msg count 0
        subQueue = new AMQQueue("amq.topic", "clientid" + ":" + "testResubscribeWithChangedSelectorAndRestart");
        assertEquals("Msg count should be 0", 0, ((AMQSession<?, ?>) session).getQueueDepth(subQueue));

        //now restart the server
        try
        {
            restartBroker();
        }
        catch (Exception e)
        {
            _logger.error("problems restarting broker: " + e);
            throw e;
        }
        
        // Reconnect to broker
        Connection connection = getConnectionFactory().createConnection("guest", "guest");
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        topic = new AMQTopic((AMQConnection) connection, "testResubscribeWithChangedSelectorAndRestart");
        producer = session.createProducer(topic);

        //verify no messages now present on the queue after we restart the broker
        //check the dur sub's underlying queue now has msg count 0
        subQueue = new AMQQueue("amq.topic", "clientid" + ":" + "testResubscribeWithChangedSelectorAndRestart");
        assertEquals("Msg count should be 0", 0, ((AMQSession<?, ?>) session).getQueueDepth(subQueue));

        // Reconnect with new selector that matches B
        TopicSubscriber subC = session.createDurableSubscriber(topic, 
                "testResubscribeWithChangedSelectorAndRestart",
                "Match = False", false);
        
        // Check that new messages are still sent and recieved properly
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart1");
        msg.setBooleanProperty("Match", true);
        producer.send(msg);
        msg = session.createTextMessage("testResubscribeWithChangedSelectorAndRestart2");
        msg.setBooleanProperty("Match", false);
        producer.send(msg);

        //check the dur sub's underlying queue now has msg count 1
        subQueue = new AMQQueue("amq.topic", "clientid" + ":" + "testResubscribeWithChangedSelectorAndRestart");
        assertEquals("Msg count should be 1", 1, ((AMQSession<?, ?>) session).getQueueDepth(subQueue));
        
        rMsg = subC.receive(1000);
        assertNotNull(rMsg);
        assertEquals("Content was wrong", 
                     "testResubscribeWithChangedSelectorAndRestart2",
                     ((TextMessage) rMsg).getText());
        
        rMsg = subC.receive(1000);
        assertNull(rMsg);
        
        session.unsubscribe("testResubscribeWithChangedSelectorAndRestart");
        
        subC.close();
        session.close();
        connection.close();
    }
}

