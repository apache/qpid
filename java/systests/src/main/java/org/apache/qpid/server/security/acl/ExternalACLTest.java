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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.IllegalStateException;
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
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.url.URLSyntaxException;

/**
 * Tests the V2 ACLs.  The tests perform basic AMQP operations like creating queues or excahnges and publishing and consuming messages, using
 * JMS to contact the broker.
 */
public class ExternalACLTest extends AbstractACLTestCase
{
    public void testAccessAuthorizedSuccess() throws AMQException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");
            Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
            conn.start();

            //Do something to show connection is active.
            sess.rollback();

            conn.close();
        }
        catch (Exception e)
        {
            fail("Connection was not created due to:" + e);
        }
    }
    
    public void testAccessVhostAuthorisedGuestSuccess() throws IOException, Exception
    {
        //The 'guest' user has no access to the 'test' vhost, as tested below in testAccessNoRights(), and so
        //is unable to perform actions such as connecting (and by extension, creating a queue, and consuming 
        //from a queue etc). In order to test the vhost-wide 'access' ACL right, the 'guest' user has been given 
        //this right in the 'test2' vhost.
        
        try
        {
            //get a connection to the 'test2' vhost using the guest user and perform various actions.
            Connection conn = getConnection("test2", "guest", "guest");
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            conn.start();

            //create Queues and consumers for each
            Queue namedQueue = sess.createQueue("vhostAccessCreatedQueue" + getTestQueueName());
            Queue tempQueue = sess.createTemporaryQueue();
            MessageConsumer consumer = sess.createConsumer(namedQueue);
            MessageConsumer tempConsumer = sess.createConsumer(tempQueue);

            //send a message to each queue (also causing an exchange declare)
            MessageProducer sender = ((AMQSession<?, ?>) sess).createProducer(null);
            ((org.apache.qpid.jms.MessageProducer) sender).send(namedQueue, sess.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);
            ((org.apache.qpid.jms.MessageProducer) sender).send(tempQueue, sess.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            //consume the messages from the queues
            consumer.receive(2000);
            tempConsumer.receive(2000);

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }
    
    public void testAccessNoRightsFailure() throws Exception
    {
        try
        {
            Connection conn = getConnection("test", "guest", "guest");
            Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
            conn.start();
            sess.rollback();
            
            fail("Connection was created.");
        }
        catch (JMSException e)
        {
            // JMSException -> linkedException -> cause = AMQException (403 or 320)
            Exception linkedException = e.getLinkedException();
            assertNotNull("There was no linked exception", linkedException);
            Throwable cause = linkedException.getCause();
            assertNotNull("Cause was null", cause);
            assertTrue("Wrong linked exception type", cause instanceof AMQException);
            AMQConstant errorCode = isBroker010() ? AMQConstant.CONTEXT_IN_USE : AMQConstant.ACCESS_REFUSED;
            assertEquals("Incorrect error code received", errorCode, ((AMQException) cause).getErrorCode());
        }
    }
    
    public void testClientDeleteQueueSuccess() throws Exception
    {
        try
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
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }
    
    public void testServerDeleteQueueFailure() throws Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");
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
        catch (JMSException e)
        {
            // JMSException -> linedException = AMQException.403
            check403Exception(e.getLinkedException());
        }
    }

    public void testClientConsumeFromTempQueueSuccess() throws AMQException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");

            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sess.createConsumer(sess.createTemporaryQueue());

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testClientConsumeFromNamedQueueFailure() throws NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");
            
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sess.createConsumer(sess.createQueue("IllegalQueue"));
            
            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void testClientCreateTemporaryQueueSuccess() throws JMSException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");

            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create Temporary Queue  - can't use the createTempQueue as QueueName is null.
            ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("doesnt_matter_as_autodelete_means_tmp"),
                                            true, false, false);

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testClientCreateNamedQueueFailure() throws NamingException, JMSException, AMQException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");

            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();
            
            //Create a Named Queue
            ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("IllegalQueue"), false, false, false);
            
            fail("Test failed as Queue creation succeded.");
            //conn will be automatically closed
        }
        catch (AMQException e)
        {
            check403Exception(e);
        }
    }

    public void testClientPublishUsingTransactionSuccess() throws AMQException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");

            Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

            conn.start();

            MessageProducer sender = sess.createProducer(sess.createQueue("example.RequestQueue"));

            sender.send(sess.createTextMessage("test"));

            //Send the message using a transaction as this will allow us to retrieve any errors that occur on the broker.
            sess.commit();

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test publish failed:" + e);
        }
    }

    public void testClientPublishValidQueueSuccess() throws AMQException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");

            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            MessageProducer sender = ((AMQSession<?, ?>) sess).createProducer(null);

            Queue queue = sess.createQueue("example.RequestQueue");

            // Send a message that we will wait to be sent, this should give the broker time to process the msg
            // before we finish this test. Message is set !immed !mand as the queue is invalid so want to test ACLs not
            // queue existence.
            ((org.apache.qpid.jms.MessageProducer) sender).send(queue, sess.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test publish failed:" + e);
        }
    }

    public void testClientPublishInvalidQueueSuccess() throws AMQException, URLSyntaxException, JMSException, NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            MessageProducer sender = ((AMQSession<?, ?>) session).createProducer(null);

            Queue queue = session.createQueue("Invalid");

            // Send a message that we will wait to be sent, this should give the broker time to close the connection
            // before we finish this test. Message is set !immed !mand as the queue is invalid so want to test ACLs not
            // queue existence.
            ((org.apache.qpid.jms.MessageProducer) sender).send(queue, session.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            // Test the connection with a valid consumer
            // This may fail as the session may be closed before the queue or the consumer created.
            Queue temp = session.createTemporaryQueue();

            session.createConsumer(temp).close();

            //Connection should now be closed and will throw the exception caused by the above send
            conn.close();

            fail("Close is not expected to succeed.");
        }
        catch (IllegalStateException e)
        {
            _logger.info("QPID-2345: Session became closed and we got that error rather than the authentication error.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void testServerConsumeFromNamedQueueValid() throws AMQException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");

            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sess.createConsumer(sess.createQueue("example.RequestQueue"));

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testServerConsumeFromNamedQueueInvalid() throws AMQException, URLSyntaxException, NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "client", "guest");
            
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sess.createConsumer(sess.createQueue("Invalid"));

            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void testServerConsumeFromTemporaryQueue() throws AMQException, URLSyntaxException, NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");
            
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sess.createConsumer(sess.createTemporaryQueue());
            
            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }

    public void testServerCreateNamedQueueValid() throws JMSException, URLSyntaxException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");

            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create Temporary Queue
            ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("example.RequestQueue"), false, false, false);

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testServerCreateNamedQueueInvalid() throws JMSException, URLSyntaxException, AMQException, NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");
            
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create a Named Queue
            ((AMQSession<?, ?>) sess).createQueue(new AMQShortString("IllegalQueue"), false, false, false);

            fail("Test failed as creation succeded.");
        }
        catch (Exception e)
        {
            check403Exception(e);
        }
    }

    public void testServerCreateTemporaryQueueInvalid() throws NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            session.createTemporaryQueue();

            fail("Test failed as creation succeded.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }
    
    public void testServerCreateAutoDeleteQueueInvalid() throws NamingException, JMSException, AMQException, Exception
    {
        try
        {
            Connection connection = getConnection("test", "server", "guest");
            
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            connection.start();

            ((AMQSession<?, ?>) session).createQueue(new AMQShortString("again_ensure_auto_delete_queue_for_temporary"),
                                               true, false, false);

            fail("Test failed as creation succeded.");
        }
        catch (Exception e)
        {
            check403Exception(e);
        }
    }

    /**
     * This test uses both the cilent and sender to validate that the Server is able to publish to a temporary queue.
     * The reason the client must be involved is that the Server is unable to create its own Temporary Queues.
     *
     * @throws AMQException
     * @throws URLSyntaxException
     * @throws JMSException
     */
    public void testServerPublishUsingTransactionSuccess() throws AMQException, URLSyntaxException, JMSException, NamingException, Exception
    {
        //Set up the Server
        Connection serverConnection = getConnection("test", "server", "guest");

        Session serverSession = serverConnection.createSession(true, Session.SESSION_TRANSACTED);

        Queue requestQueue = serverSession.createQueue("example.RequestQueue");

        MessageConsumer server = serverSession.createConsumer(requestQueue);

        serverConnection.start();

        //Set up the consumer
        Connection clientConnection = getConnection("test", "client", "guest");

        //Send a test mesage
        Session clientSession = clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue responseQueue = clientSession.createTemporaryQueue();

        MessageConsumer clientResponse = clientSession.createConsumer(responseQueue);

        clientConnection.start();

        Message request = clientSession.createTextMessage("Request");

        assertNotNull("Response Queue is null", responseQueue);

        request.setJMSReplyTo(responseQueue);

        clientSession.createProducer(requestQueue).send(request);

        try
        {
            Message msg = null;

            msg = server.receive(2000);

            while (msg != null && !((TextMessage) msg).getText().equals("Request"))
            {
                msg = server.receive(2000);
            }

            assertNotNull("Message not received", msg);

            assertNotNull("Reply-To is Null", msg.getJMSReplyTo());

            MessageProducer sender = serverSession.createProducer(msg.getJMSReplyTo());

            sender.send(serverSession.createTextMessage("Response"));

            //Send the message using a transaction as this will allow us to retrieve any errors that occur on the broker.
            serverSession.commit();

            //Ensure Response is received.
            Message clientResponseMsg = clientResponse.receive(2000);
            assertNotNull("Client did not receive response message,", clientResponseMsg);
            assertEquals("Incorrect message received", "Response", ((TextMessage) clientResponseMsg).getText());

        }
        catch (Exception e)
        {
            fail("Test publish failed:" + e);
        }
        finally
        {
            try
            {
                serverConnection.close();
            }
            finally
            {
                clientConnection.close();
            }
        }
    }

    public void testServerPublishInvalidQueueSuccess() throws AMQException, URLSyntaxException, JMSException, NamingException, Exception
    {
        try
        {
            Connection conn = getConnection("test", "server", "guest");

            ((AMQConnection) conn).setConnectionListener(this);

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            MessageProducer sender = ((AMQSession<?, ?>) session).createProducer(null);

            Queue queue = session.createQueue("Invalid");

            // Send a message that we will wait to be sent, this should give the broker time to close the connection
            // before we finish this test. Message is set !immed !mand as the queue is invalid so want to test ACLs not
            // queue existence.
            ((org.apache.qpid.jms.MessageProducer) sender).send(queue, session.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            // Test the connection with a valid consumer
            // This may not work as the session may be closed before the queue or consumer creation can occur.
            // The correct JMSexception with linked error will only occur when the close method is recevied whilst in
            // the failover safe block
            session.createConsumer(session.createQueue("example.RequestQueue")).close();

            //Connection should now be closed and will throw the exception caused by the above send
            conn.close();

            fail("Close is not expected to succeed.");
        }
        catch (IllegalStateException e)
        {
            _logger.info("QPID-2345: Session became closed and we got that error rather than the authentication error.");
        }
        catch (JMSException e)
        {
            check403Exception(e.getLinkedException());
        }
    }
    
    
    @Override
    public String getConfig()
    {
        return "config-systests-aclv2.xml";
    }

    @Override
    public List<String> getHostList()
    {
        return Arrays.asList("test", "test2");
    }
}
