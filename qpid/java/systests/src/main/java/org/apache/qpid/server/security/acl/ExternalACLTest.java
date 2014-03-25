/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
*  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.qpid.server.security.acl;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.naming.NamingException;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.url.URLSyntaxException;

/**
 * Tests the V2 ACLs.  The tests perform basic AMQP operations like creating queues or exchanges and publishing and consuming messages, using
 * JMS to contact the broker.
 */
public class ExternalACLTest extends AbstractACLTestCase
{

    public void setUpAccessAuthorizedSuccess() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST");
    }

    public void testAccessAuthorizedSuccess() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        conn.close();
    }

    public void setUpAccessNoRightsFailure() throws Exception
    {
        writeACLFile("ACL DENY-LOG client ACCESS VIRTUALHOST");
    }

    public void testAccessNoRightsFailure() throws Exception
    {
        try
        {
            getConnection("test", "client", "guest");
            fail("Connection was created.");
        }
        catch (JMSException e)
        {
            assertAccessDeniedException(e);
        }
    }

    private void assertAccessDeniedException(JMSException e)
    {
        assertEquals("Unexpected exception message", "Error creating connection: Permission denied: test", e.getMessage());

        // JMSException -> linkedException -> cause = AMQException (403 or 320)
        Exception linkedException = e.getLinkedException();
        assertNotNull("There was no linked exception", linkedException);
        Throwable cause = linkedException.getCause();
        assertNotNull("Cause was null", cause);
        assertTrue("Wrong linked exception type", cause instanceof AMQException);
        AMQConstant errorCode = isBroker010() ? AMQConstant.CONNECTION_FORCED : AMQConstant.ACCESS_REFUSED;
        assertEquals("Incorrect error code received", errorCode, ((AMQException) cause).getErrorCode());
    }

    public void setUpAccessVirtualHostWithName() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST name='test'", "ACL DENY-LOG guest ACCESS VIRTUALHOST name='test'",
                           "ACL ALLOW-LOG server ACCESS VIRTUALHOST name='*'");
    }

    public void testAccessVirtualHostWithName() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        conn.close();

        try
        {
            getConnection("test", "guest", "guest");
            fail("Access should be denied");
        }
        catch (JMSException e)
        {
            assertAccessDeniedException(e);
        }

        Connection conn2 = getConnection("test", "server", "guest");
        conn2.close();
    }

    public void setUpClientCreateVirtualHostQueue() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE virtualhost_name='test'",
                     "ACL ALLOW-LOG client CONSUME QUEUE",
                     "ACL ALLOW-LOG client BIND EXCHANGE",
                     "ACL ALLOW-LOG guest ACCESS VIRTUALHOST",
                     "ACL DENY-LOG  guest CREATE QUEUE virtualhost_name='test'");
    }

    public void testClientCreateVirtualHostQueue() throws NamingException, JMSException, AMQException, Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination dest = sess.createQueue(getTestQueueName());
        sess.createConsumer(dest);
        conn.close();

        try
        {
            conn = getConnection("test", "guest", "guest");
            sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            sess.createConsumer(dest);

            fail("Queue creation for user 'guest' is denied");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }


    public void setUpClientDeleteQueueSuccess() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE durable=\"true\"",
                     "ACL ALLOW-LOG client CONSUME QUEUE name=\"clientid:kipper\"" ,
                     "ACL ALLOW-LOG client BIND EXCHANGE name=\"amq.topic\" durable=true routingKey=kipper",
                     "ACL ALLOW-LOG client DELETE QUEUE durable=\"true\"",
                     "ACL ALLOW-LOG client UNBIND EXCHANGE name=\"amq.topic\" durable=true routingKey=kipper");
    }

    public void testClientDeleteQueueSuccess() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
        conn.start();

        // create kipper
        Topic kipper = sess.createTopic("kipper");
        TopicSubscriber subscriber = sess.createDurableSubscriber(kipper, "kipper");

        subscriber.close();
        sess.unsubscribe("kipper");

        //Do something to show connection is active.
        sess.rollback();
        conn.close();
    }


    public void setUpClientDeleteQueueFailure() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE durable=\"true\"",
                     "ACL ALLOW-LOG client CONSUME QUEUE name=\"clientid:kipper\"" ,
                     "ACL ALLOW-LOG client BIND EXCHANGE name=\"amq.topic\" durable=true routingKey=kipper",
                     "ACL DENY-LOG client DELETE QUEUE durable=\"true\"",
                     "ACL DENY-LOG client UNBIND EXCHANGE name=\"amq.topic\" durable=true routingKey=kipper");
    }

    public void testClientDeleteQueueFailure() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
        conn.start();

        // create kipper
        Topic kipper = sess.createTopic("kipper");
        TopicSubscriber subscriber = sess.createDurableSubscriber(kipper, "kipper");

        subscriber.close();
        try
        {
            sess.unsubscribe("kipper");

            //Do something to show connection is active.
            sess.rollback();

            fail("Exception was not thrown");
        }
        catch (JMSException e)
        {
            // JMSException -> linedException = AMQException.403
            check403Exception(e.getLinkedException());
        }
    }


    public void testClientConsumeFromTempQueueSuccess() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");

        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        conn.start();

        sess.createConsumer(sess.createTemporaryQueue());
    }

    public void setUpClientConsumeFromNamedQueueValid() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE name=\"example.RequestQueue\"",
                     "ACL ALLOW-LOG client CONSUME QUEUE name=\"example.RequestQueue\"" ,
                     "ACL ALLOW-LOG client BIND EXCHANGE name=\"amq.direct\" routingKey=\"example.RequestQueue\"");
    }


    public void testClientConsumeFromNamedQueueValid() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");

        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        conn.start();

        sess.createConsumer(sess.createQueue("example.RequestQueue"));
    }

    public void setUpClientConsumeFromNamedQueueFailure() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE",
                     "ACL ALLOW-LOG client BIND EXCHANGE" ,
                     "ACL DENY-LOG client CONSUME QUEUE name=\"IllegalQueue\"");
    }

    public void testClientConsumeFromNamedQueueFailure() throws NamingException, Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();
        Destination dest = sess.createQueue("IllegalQueue");

        try
        {
            sess.createConsumer(dest);

            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void setUpClientCreateTemporaryQueueSuccess() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE temporary=\"true\"",
                     "ACL ALLOW-LOG client BIND EXCHANGE name=\"amq.direct\" temporary=true" ,
                     "ACL ALLOW-LOG client DELETE QUEUE temporary=\"true\"",
                     "ACL ALLOW-LOG client UNBIND EXCHANGE name=\"amq.direct\" temporary=true");
    }

    public void testClientCreateTemporaryQueueSuccess() throws JMSException, URLSyntaxException, Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();

        sess.createTemporaryQueue();
        conn.close();
    }

    public void setUpClientCreateTemporaryQueueFailed() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL DENY-LOG client CREATE QUEUE temporary=\"true\"");
    }

    public void testClientCreateTemporaryQueueFailed() throws NamingException, Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();

        try
        {

            session.createTemporaryQueue();

            fail("Test failed as creation succeded.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void setUpClientCreateNamedQueueFailure() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE name=\"ValidQueue\"");
    }

    public void testClientCreateNamedQueueFailure() throws NamingException, JMSException, AMQException, Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        conn.start();
        Destination dest = sess.createQueue("IllegalQueue");

        try
        {
            //Create a Named Queue as side effect
            sess.createConsumer(dest);
            fail("Test failed as Queue creation succeded.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void setUpClientPublishUsingTransactionSuccess() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client CREATE QUEUE",
                     "ACL ALLOW-LOG client BIND EXCHANGE" ,
                     "ACL ALLOW-LOG client PUBLISH EXCHANGE name=\"amq.direct\" routingKey=\"example.RequestQueue\"");
    }

    public void testClientPublishUsingTransactionSuccess() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");

        Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

        conn.start();

        Queue queue = sess.createQueue("example.RequestQueue");

        ((AMQSession<?,?>)sess).declareAndBind((AMQDestination)queue);

        MessageProducer sender = sess.createProducer(queue);

        sender.send(sess.createTextMessage("test"));

        //Send the message using a transaction as this will allow us to retrieve any errors that occur on the broker.
        sess.commit();

        conn.close();
    }

    public void setUpRequestResponseSuccess() throws Exception
    {
        // The group "messaging-users", referenced in the ACL below, is currently defined
        // in broker/etc/groups-systests.
        // We tolerate a dependency from this test to that file because its
        // contents are expected to change rarely.

        writeACLFile("ACL ALLOW-LOG messaging-users ACCESS VIRTUALHOST", "# Server side",
                     "ACL ALLOW-LOG server CREATE QUEUE name=\"example.RequestQueue\"",
                     "ACL ALLOW-LOG server BIND EXCHANGE" ,
                     "ACL ALLOW-LOG server PUBLISH EXCHANGE name=\"amq.direct\" routingKey=\"TempQueue*\"",
                     "ACL ALLOW-LOG server CONSUME QUEUE name=\"example.RequestQueue\"",
                     "# Client side",
                     "ACL ALLOW-LOG client PUBLISH EXCHANGE name=\"amq.direct\" routingKey=\"example.RequestQueue\"",
                     "ACL ALLOW-LOG client CONSUME QUEUE temporary=true",
                     "ACL ALLOW-LOG client BIND EXCHANGE name=\"amq.direct\" temporary=true",
                     "ACL ALLOW-LOG client UNBIND EXCHANGE name=\"amq.direct\" temporary=true",
                     "ACL ALLOW-LOG client CREATE QUEUE temporary=true",
                     "ACL ALLOW-LOG client DELETE QUEUE temporary=true");
    }


    public void testRequestResponseSuccess() throws Exception
    {
        //Set up the Server
        Connection serverConnection = getConnection("test", "server", "guest");
        Session serverSession = serverConnection.createSession(true, Session.SESSION_TRANSACTED);
        Queue requestQueue = serverSession.createQueue("example.RequestQueue");
        MessageConsumer server = serverSession.createConsumer(requestQueue);
        serverConnection.start();

        //Set up the consumer
        Connection clientConnection = getConnection("test", "client", "guest");
        Session clientSession = clientConnection.createSession(true, Session.SESSION_TRANSACTED);
        Queue responseQueue = clientSession.createTemporaryQueue();
        MessageConsumer clientResponse = clientSession.createConsumer(responseQueue);
        clientConnection.start();

        // Client
        Message request = clientSession.createTextMessage("Request");
        request.setJMSReplyTo(responseQueue);

        clientSession.createProducer(requestQueue).send(request);
        clientSession.commit();

        // Server
        Message msg = server.receive(2000);
        assertNotNull("Server should have received client's request", msg);
        assertNotNull("Received msg should have Reply-To", msg.getJMSReplyTo());

        MessageProducer sender = serverSession.createProducer(msg.getJMSReplyTo());
        sender.send(serverSession.createTextMessage("Response"));
        serverSession.commit();

        // Client
        Message clientResponseMsg = clientResponse.receive(2000);
        clientSession.commit();
        assertNotNull("Client did not receive response message,", clientResponseMsg);
        assertEquals("Incorrect message received", "Response", ((TextMessage) clientResponseMsg).getText());
    }

    public void setUpClientDeleteQueueSuccessWithOnlyAllPermissions() throws Exception
    {
        writeACLFile("ACL ALLOW-LOG client ACCESS VIRTUALHOST",
                     "ACL ALLOW-LOG client ALL QUEUE",
                     "ACL ALLOW-LOG client ALL EXCHANGE");
    }

    public void testClientDeleteQueueSuccessWithOnlyAllPermissions() throws Exception
    {
        Connection conn = getConnection("test", "client", "guest");
        Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
        conn.start();

        // create kipper
        String topicName = "kipper";
        Topic topic = sess.createTopic(topicName);
        TopicSubscriber subscriber = sess.createDurableSubscriber(topic, topicName);

        subscriber.close();
        sess.unsubscribe(topicName);

        //Do something to show connection is active.
        sess.rollback();
        conn.close();
    }

    public void setUpFirewallAllow() throws Exception
    {
        writeACLFile("ACL ALLOW client ACCESS VIRTUALHOST from_network=\"127.0.0.1\"");
    }

    public void testFirewallAllow() throws Exception
    {
        getConnection("test", "client", "guest");
        // test pass because we successfully connected
    }

    public void setUpFirewallDeny() throws Exception
    {
        writeACLFile("ACL DENY client ACCESS VIRTUALHOST from_network=\"127.0.0.1\"");
    }

    public void testFirewallDeny() throws Exception
    {
        try
        {
            getConnection("test", "client", "guest");
            fail("We expected the connection to fail");
        }
        catch(JMSException e)
        {
            // pass
        }
    }
}
