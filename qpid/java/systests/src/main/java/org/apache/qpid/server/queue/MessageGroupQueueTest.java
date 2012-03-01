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

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;

public class MessageGroupQueueTest extends QpidBrokerTestCase
{
    protected final String QUEUE = "MessageGroupQueue";

    private Connection producerConnection;
    private MessageProducer producer;
    private Session producerSession;
    private Queue queue;
    private Connection consumerConnection;
    
    
    protected void setUp() throws Exception
    {
        super.setUp();

        producerConnection = getConnection();
        producerSession = producerConnection.createSession(true, Session.AUTO_ACKNOWLEDGE);

        producerConnection.start();

        consumerConnection = getConnection();
        
    }

    protected void tearDown() throws Exception
    {
        producerConnection.close();
        consumerConnection.close();
        super.tearDown();
    }


    public void testSimpleGroupAssignment() throws Exception
    {
        simpleGroupAssignment(false);
    }

    public void testSharedGroupSimpleGroupAssignment() throws Exception
    {
        simpleGroupAssignment(true);
    }


    /**
     * Pre populate the queue with messages with groups as follows
     *
     *  ONE
     *  TWO
     *  ONE
     *  TWO
     *
     *  Create two consumers with prefetch of 1, the first consumer should then be assigned group ONE, the second
     *  consumer assigned group TWO if they are started in sequence.
     *
     *  Thus doing
     *
     *  c1 <--- (ONE)
     *  c2 <--- (TWO)
     *  c2 ack --->
     *
     *  c2 should now be able to receive a second message from group TWO (skipping over the message from group ONE)
     *
     *  i.e.
     *
     *  c2 <--- (TWO)
     *  c2 ack --->
     *  c1 <--- (ONE)
     *  c1 ack --->
     *
     */
    private void simpleGroupAssignment(boolean sharedGroups) throws AMQException, JMSException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.group_header_key","group");
        if(sharedGroups)
        {
            arguments.put("qpid.shared_msg_group","1");
        }
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = (Queue) producerSession.createQueue("direct://amq.direct/"+QUEUE+"/"+QUEUE+"?durable='false'&autodelete='true'");

        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        String[] groups = { "ONE", "TWO"};

        for (int msg = 0; msg < 4; msg++)
        {
            producer.send(createMessage(msg, groups[msg % groups.length]));
        }
        producerSession.commit();
        producer.close();
        producerSession.close();
        producerConnection.close();

        Session cs1 = ((AMQConnection)consumerConnection).createSession(false, Session.CLIENT_ACKNOWLEDGE,1);
        Session cs2 = ((AMQConnection)consumerConnection).createSession(false, Session.CLIENT_ACKNOWLEDGE,1);


        MessageConsumer consumer1 = cs1.createConsumer(queue);
        MessageConsumer consumer2 = cs2.createConsumer(queue);

        consumerConnection.start();
        Message cs1Received = consumer1.receive(1000);
        assertNotNull("Consumer 1 should have received first message", cs1Received);

        Message cs2Received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received first message", cs2Received);

        cs2Received.acknowledge();

        Message cs2Received2 = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received second message", cs2Received2);
        assertEquals("Differing groups", cs2Received2.getStringProperty("group"),
                     cs2Received.getStringProperty("group"));

        cs1Received.acknowledge();
        Message cs1Received2 = consumer1.receive(1000);

        assertNotNull("Consumer 1 should have received second message", cs1Received2);
        assertEquals("Differing groups", cs1Received2.getStringProperty("group"),
                     cs1Received.getStringProperty("group"));

        cs1Received2.acknowledge();
        cs2Received2.acknowledge();

        assertNull(consumer1.receive(1000));
        assertNull(consumer2.receive(1000));
    }


    public void testConsumerCloseGroupAssignment() throws Exception
    {
        consumerCloseGroupAssignment(false);
    }

    public void testSharedGroupConsumerCloseGroupAssignment() throws Exception
    {
        consumerCloseGroupAssignment(true);
    }

    /**
     *
     * Tests that upon closing a consumer, groups previously assigned to that consumer are reassigned to a different
     * consumer.
     *
     * Pre-populate the queue as ONE, ONE, TWO, ONE
     *
     * create in sequence two consumers
     *
     * receive first from c1 then c2 (thus ONE is assigned to c1, TWO to c2)
     *
     * Then close c1 before acking.
     *
     * If we now attempt to receive from c2, then the remaining messages in group ONE should be available (which
     * requires c2 to go "backwards" in the queue).
     *
     **/
    private void consumerCloseGroupAssignment(boolean sharedGroups) throws AMQException, JMSException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.group_header_key","group");
        if(sharedGroups)
        {
            arguments.put("qpid.shared_msg_group","1");
        }
        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = (Queue) producerSession.createQueue("direct://amq.direct/"+QUEUE+"/"+QUEUE+"?durable='false'&autodelete='true'");

        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        producer.send(createMessage(1, "ONE"));
        producer.send(createMessage(2, "ONE"));
        producer.send(createMessage(3, "TWO"));
        producer.send(createMessage(4, "ONE"));

        producerSession.commit();
        producer.close();
        producerSession.close();
        producerConnection.close();

        Session cs1 = ((AMQConnection)consumerConnection).createSession(true, Session.SESSION_TRANSACTED,1);
        Session cs2 = ((AMQConnection)consumerConnection).createSession(true, Session.SESSION_TRANSACTED,1);

        MessageConsumer consumer1 = cs1.createConsumer(queue);

        consumerConnection.start();
        MessageConsumer consumer2 = cs2.createConsumer(queue);

        Message cs1Received = consumer1.receive(1000);
        assertNotNull("Consumer 1 should have received first message", cs1Received);
        assertEquals("incorrect message received", 1, cs1Received.getIntProperty("msg"));

        Message cs2Received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received first message", cs2Received);
        assertEquals("incorrect message received", 3, cs2Received.getIntProperty("msg"));
        cs2.commit();

        Message cs2Received2 = consumer2.receive(1000);

        assertNull("Consumer 2 should not yet have received a second message", cs2Received2);

        consumer1.close();

        cs1.commit();
        Message cs2Received3 = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received second message", cs2Received3);
        assertEquals("Unexpected group", "ONE", cs2Received3.getStringProperty("group"));
        assertEquals("incorrect message received", 2, cs2Received3.getIntProperty("msg"));

        cs2.commit();


        Message cs2Received4 = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received third message", cs2Received4);
        assertEquals("Unexpected group", "ONE", cs2Received4.getStringProperty("group"));
        assertEquals("incorrect message received", 4, cs2Received4.getIntProperty("msg"));
        cs2.commit();

        assertNull(consumer2.receive(1000));
    }



    
    public void testConsumerCloseWithRelease() throws Exception
    {
        consumerCloseWithRelease(false);
    }

    public void testSharedGroupConsumerCloseWithRelease() throws Exception
    {
        consumerCloseWithRelease(true);
    }


    /**
     *
     * Tests that upon closing a consumer and its session, groups previously assigned to that consumer are reassigned
     * toa different consumer, including messages which were previously delivered but have now been released.
     *
     * Pre-populate the queue as ONE, ONE, TWO, ONE
     *
     * create in sequence two consumers
     *
     * receive first from c1 then c2 (thus ONE is assigned to c1, TWO to c2)
     *
     * Then close c1 and its session without acking.
     *
     * If we now attempt to receive from c2, then the all messages in group ONE should be available (which
     * requires c2 to go "backwards" in the queue). The first such message should be marked as redelivered
     *
     */
    private void consumerCloseWithRelease(boolean sharedGroups) throws AMQException, JMSException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.group_header_key","group");
        if(sharedGroups)
        {
            arguments.put("qpid.shared_msg_group","1");
        }

        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = (Queue) producerSession.createQueue("direct://amq.direct/"+QUEUE+"/"+QUEUE+"?durable='false'&autodelete='true'");

        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        producer.send(createMessage(1, "ONE"));
        producer.send(createMessage(2, "ONE"));
        producer.send(createMessage(3, "TWO"));
        producer.send(createMessage(4, "ONE"));

        producerSession.commit();
        producer.close();
        producerSession.close();
        producerConnection.close();

        Session cs1 = ((AMQConnection)consumerConnection).createSession(true, Session.SESSION_TRANSACTED,1);
        Session cs2 = ((AMQConnection)consumerConnection).createSession(true, Session.SESSION_TRANSACTED,1);


        MessageConsumer consumer1 = cs1.createConsumer(queue);

        consumerConnection.start();

        MessageConsumer consumer2 = cs2.createConsumer(queue);

        Message cs1Received = consumer1.receive(1000);
        assertNotNull("Consumer 1 should have received its first message", cs1Received);
        assertEquals("incorrect message received", 1, cs1Received.getIntProperty("msg"));

        Message received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received its first message", received);
        assertEquals("incorrect message received", 3, received.getIntProperty("msg"));

        received = consumer2.receive(1000);

        assertNull("Consumer 2 should not yet have received second message", received);

        consumer1.close();
        cs1.close();
        cs2.commit();
        received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should now have received second message", received);
        assertEquals("Unexpected group", "ONE", received.getStringProperty("group"));
        assertEquals("incorrect message received", 1, received.getIntProperty("msg"));
        assertTrue("Expected second message to be marked as redelivered " + received.getIntProperty("msg"),
                   received.getJMSRedelivered());

        cs2.commit();


        received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received a third message", received);
        assertEquals("Unexpected group", "ONE", received.getStringProperty("group"));
        assertEquals("incorrect message received", 2, received.getIntProperty("msg"));

        cs2.commit();

        received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received a fourth message", received);
        assertEquals("Unexpected group", "ONE", received.getStringProperty("group"));
        assertEquals("incorrect message received", 4, received.getIntProperty("msg"));

        cs2.commit();


        assertNull(consumer2.receive(1000));
    }

    public void testGroupAssignmentSurvivesEmpty() throws JMSException, AMQException
    {
        groupAssignmentOnEmpty(false);
    }

    public void testSharedGroupAssignmentDoesNotSurviveEmpty() throws JMSException, AMQException
    {
        groupAssignmentOnEmpty(true);
    }

    private void groupAssignmentOnEmpty(boolean sharedGroups) throws AMQException, JMSException
    {
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put("qpid.group_header_key","group");
        if(sharedGroups)
        {
            arguments.put("qpid.shared_msg_group","1");
        }

        ((AMQSession) producerSession).createQueue(new AMQShortString(QUEUE), true, false, false, arguments);
        queue = (Queue) producerSession.createQueue("direct://amq.direct/"+QUEUE+"/"+QUEUE+"?durable='false'&autodelete='true'");

        ((AMQSession) producerSession).declareAndBind((AMQDestination)queue);
        producer = producerSession.createProducer(queue);

        producer.send(createMessage(1, "ONE"));
        producer.send(createMessage(2, "TWO"));
        producer.send(createMessage(3, "THREE"));
        producer.send(createMessage(4, "ONE"));

        producerSession.commit();
        producer.close();
        producerSession.close();
        producerConnection.close();

        Session cs1 = ((AMQConnection)consumerConnection).createSession(true, Session.SESSION_TRANSACTED,1);
        Session cs2 = ((AMQConnection)consumerConnection).createSession(true, Session.SESSION_TRANSACTED,1);


        MessageConsumer consumer1 = cs1.createConsumer(queue);

        consumerConnection.start();

        MessageConsumer consumer2 = cs2.createConsumer(queue);

        Message received = consumer1.receive(1000);
        assertNotNull("Consumer 1 should have received its first message", received);
        assertEquals("incorrect message received", 1, received.getIntProperty("msg"));

        received = consumer2.receive(1000);

        assertNotNull("Consumer 2 should have received its first message", received);
        assertEquals("incorrect message received", 2, received.getIntProperty("msg"));

        cs1.commit();

        received = consumer1.receive(1000);
        assertNotNull("Consumer 1 should have received its second message", received);
        assertEquals("incorrect message received", 3, received.getIntProperty("msg"));

        // We expect different behaviours from "shared groups": here the assignment of a subscription to a group
        // is terminated when there are no outstanding delivered but unacknowledged messages.  In contrast, with a
        // standard message grouping queue the assignment will be retained until the subscription is no longer
        // registered
        if(sharedGroups)
        {
            cs2.commit();
            received = consumer2.receive(1000);

            assertNotNull("Consumer 2 should have received its second message", received);
            assertEquals("incorrect message received", 4, received.getIntProperty("msg"));

            cs2.commit();
        }
        else
        {
            cs2.commit();
            received = consumer2.receive(1000);

            assertNull("Consumer 2 should not have received a second message", received);

            cs1.commit();

            received = consumer1.receive(1000);
            assertNotNull("Consumer 1 should have received its third message", received);
            assertEquals("incorrect message received", 4, received.getIntProperty("msg"));

        }

    }


    private Message createMessage(int msg, String group) throws JMSException
    {
        Message send = producerSession.createTextMessage("Message: " + msg);
        send.setIntProperty("msg", msg);
        send.setStringProperty("group", group);

        return send;
    }
}
