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
 *
 *
 */

package org.apache.qpid.server.security.acl;

import junit.framework.TestCase;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.*;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.AMQException;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;
import java.io.File;


public class SimpleACLTest extends TestCase implements ConnectionListener
{
    private String BROKER = "vm://:1";//"tcp://localhost:5672";

    public void setUp() throws Exception
    {
        // Initialise ACLs.
        final String QpidExampleHome = System.getProperty("QPID_EXAMPLE_HOME");
        final File defaultaclConfigFile = new File(QpidExampleHome, "etc/acl.config.xml");

        if (!defaultaclConfigFile.exists() || System.getProperty("QPID_HOME") == null)
        {
            System.err.println("Configuration file not found:" + defaultaclConfigFile);
            fail("Configuration file not found:" + defaultaclConfigFile);
        }

        ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(defaultaclConfigFile);

        ApplicationRegistry.initialise(config, 1);

        TransportConnection.createVMBroker(1);
    }

    public void tearDown()
    {
        ApplicationRegistry.remove(1);
        TransportConnection.killAllVMBrokers();
    }

    public String createConnectionString(String username, String password, String broker)
    {

        return "amqp://" + username + ":" + password + "@clientid/test?brokerlist='" + broker + "'";
    }

    public void testAccessAuthorized() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            Session sesh = conn.createSession(true, Session.SESSION_TRANSACTED);

            conn.start();

            //Do something to show connection is active.
            sesh.rollback();

            conn.close();
        }
        catch (Exception e)
        {
            fail("Connection was not created due to:" + e.getMessage());
        }
    }

    public void testAccessNoRights() throws URLSyntaxException, JMSException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("guest", "guest", BROKER));

            //Attempt to do do things to test connection.
            Session sesh = conn.createSession(true, Session.SESSION_TRANSACTED);
            conn.start();
            sesh.rollback();

            conn.close();
            fail("Connection was created.");
        }
        catch (AMQException amqe)
        {
            if (amqe.getCause() instanceof Exception)
            {
                System.err.println("QPID-594 : WARNING RACE CONDITION. Unable to determine cause of Connection Failure.");
            }
            assertEquals("Linked Exception Incorrect", JMSException.class, amqe.getCause().getClass());
            Exception linked = ((JMSException) amqe.getCause()).getLinkedException();
            assertEquals("Exception was wrong type", AMQAuthenticationException.class, linked.getClass());
            assertEquals("Incorrect error code thrown", 403, ((AMQAuthenticationException) linked).getErrorCode().getCode());
        }
    }

    public void testClientConsumeFromTempQueueValid() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createTemporaryQueue());

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testClientConsumeFromNamedQueueInvalid() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            //Prevent Failover
            ((AMQConnection) conn).setConnectionListener(this);

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createQueue("IllegalQueue"));
            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();

            assertNotNull("There was no liked exception", cause);
            assertEquals("Wrong linked exception type", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code received", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testClientCreateTemporaryQueue() throws JMSException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create Temporary Queue
            ((AMQSession) sesh).declareQueue((AMQDestination) sesh.createTemporaryQueue(),
                                             ((AMQSession) sesh).getProtocolHandler());

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testClientCreateNamedQueue() throws JMSException, URLSyntaxException, AMQException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create a Named Queue
            ((AMQSession) sesh).declareQueue((AMQDestination) sesh.createQueue("IllegalQueue"),
                                             ((AMQSession) sesh).getProtocolHandler());

            fail("Test failed as Queue creation succeded.");
        }
        catch (AMQAuthenticationException amqe)
        {
            assertEquals("Incorrect error code thrown", 403, ((AMQAuthenticationException) amqe).getErrorCode().getCode());
        }
    }

    public void testClientPublishUsingTransactionSuccess() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            ((AMQConnection) conn).setConnectionListener(this);

            Session sesh = conn.createSession(true, Session.SESSION_TRANSACTED);

            conn.start();

            MessageProducer sender = sesh.createProducer(sesh.createQueue("example.RequestQueue"));

            sender.send(sesh.createTextMessage("test"));

            //Send the message using a transaction as this will allow us to retrieve any errors that occur on the broker.
            sesh.commit();

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test publish failed:" + e);
        }
    }

    public void testClientPublishValidQueueSuccess() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            ((AMQConnection) conn).setConnectionListener(this);

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            MessageProducer sender = ((AMQSession) sesh).createProducer(null);

            Queue queue = sesh.createQueue("example.RequestQueue");

            // Send a message that we will wait to be sent, this should give the broker time to process the msg
            // before we finish this test. Message is set !immed !mand as the queue is invalid so want to test ACLs not
            // queue existence.
            ((org.apache.qpid.jms.MessageProducer) sender).send(queue, sesh.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test publish failed:" + e);
        }
    }

    public void testClientPublishInvalidQueueSuccess() throws AMQException, URLSyntaxException, JMSException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            ((AMQConnection) conn).setConnectionListener(this);

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            MessageProducer sender = ((AMQSession) session).createProducer(null);

            Queue queue = session.createQueue("Invalid");

            // Send a message that we will wait to be sent, this should give the broker time to close the connection
            // before we finish this test. Message is set !immed !mand as the queue is invalid so want to test ACLs not
            // queue existence.
            ((org.apache.qpid.jms.MessageProducer) sender).send(queue, session.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            // Test the connection with a valid consumer
            session.createConsumer(session.createTemporaryQueue()).close();

            //Connection should now be closed and will throw the exception caused by the above send
            conn.close();

            fail("Close is not expected to succeed.");
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();
            assertEquals("Incorrect exception", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code thrown", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testServerConsumeFromNamedQueueValid() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("server", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createQueue("example.RequestQueue"));

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testServerConsumeFromNamedQueueInvalid() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("client", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createQueue("Invalid"));

            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();

            assertNotNull("There was no liked exception", cause);
            assertEquals("Wrong linked exception type", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code received", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testServerConsumeFromTemporaryQueue() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("server", "guest", BROKER));

            //Prevent Failover
            ((AMQConnection) conn).setConnectionListener(this);

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createTemporaryQueue());
            fail("Test failed as consumer was created.");
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();

            assertNotNull("There was no liked exception", cause);
            assertEquals("Wrong linked exception type", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code received", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testServerCreateNamedQueueValid() throws JMSException, URLSyntaxException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("server", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create Temporary Queue
            ((AMQSession) sesh).declareQueue((AMQDestination) sesh.createQueue("example.RequestQueue"),
                                             ((AMQSession) sesh).getProtocolHandler());

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testServerCreateNamedQueueInValid() throws JMSException, URLSyntaxException, AMQException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("server", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create a Named Queue
            ((AMQSession) sesh).declareQueue((AMQDestination) sesh.createQueue("IllegalQueue"),
                                             ((AMQSession) sesh).getProtocolHandler());

            fail("Test failed as creation succeded.");
        }
        catch (AMQAuthenticationException amqe)
        {
            assertEquals("Incorrect error code thrown", 403, amqe.getErrorCode().getCode());
        }
    }

    public void testServerCreateTemporyQueueInvalid() throws JMSException, URLSyntaxException, AMQException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("server", "guest", BROKER));

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            ((AMQSession) sesh).declareQueue((AMQDestination) sesh.createTemporaryQueue(),
                                             ((AMQSession) sesh).getProtocolHandler());

            fail("Test failed as creation succeded.");
        }
        catch (AMQAuthenticationException amqe)
        {
            assertEquals("Incorrect error code thrown", 403, amqe.getErrorCode().getCode());
        }
    }

    /**
     * This test uses both the cilent and sender to validate that the Server is able to publish to a temporary queue.
     * The reason the client must be in volved is that the Serve is unable to create its own Temporary Queues.
     *
     * @throws AMQException
     * @throws URLSyntaxException
     * @throws JMSException
     */
    public void testServerPublishUsingTransactionSuccess() throws AMQException, URLSyntaxException, JMSException
    {
        //Set up the Server
        Connection serverConnection = new AMQConnection(createConnectionString("server", "guest", BROKER));

        ((AMQConnection) serverConnection).setConnectionListener(this);

        Session serverSession = serverConnection.createSession(true, Session.SESSION_TRANSACTED);

        Queue requestQueue = serverSession.createQueue("example.RequestQueue");

        MessageConsumer server = serverSession.createConsumer(requestQueue);

        serverConnection.start();

        //Set up the consumer
        Connection clientConnection = new AMQConnection(createConnectionString("client", "guest", BROKER));

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

            serverConnection.close();

            //Ensure Response is received.
            Message clientResponseMsg = clientResponse.receive(2000);
            assertNotNull("Client did not receive response message,", clientResponseMsg);
            assertEquals("Incorrect message received", "Response", ((TextMessage) clientResponseMsg).getText());

        }
        catch (Exception e)
        {
            fail("Test publish failed:" + e);
        }
    }

    public void testServerPublishInvalidQueueSuccess() throws AMQException, URLSyntaxException, JMSException
    {
        try
        {
            Connection conn = new AMQConnection(createConnectionString("server", "guest", BROKER));

            ((AMQConnection) conn).setConnectionListener(this);

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            MessageProducer sender = ((AMQSession) session).createProducer(null);

            Queue queue = session.createQueue("Invalid");

            // Send a message that we will wait to be sent, this should give the broker time to close the connection
            // before we finish this test. Message is set !immed !mand as the queue is invalid so want to test ACLs not
            // queue existence.
            ((org.apache.qpid.jms.MessageProducer) sender).send(queue, session.createTextMessage("test"),
                                                                DeliveryMode.NON_PERSISTENT, 0, 0L, false, false, true);

            // Test the connection with a valid consumer
            session.createConsumer(session.createQueue("example.RequestQueue")).close();

            //Connection should now be closed and will throw the exception caused by the above send
            conn.close();

            fail("Close is not expected to succeed.");
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();
            assertEquals("Incorrect exception", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code thrown", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    // Connection Listener Interface - Used here to block failover

    public void bytesSent(long count)
    {
    }

    public void bytesReceived(long count)
    {
    }

    public boolean preFailover(boolean redirect)
    {
        //Prevent failover.
        return false;
    }

    public boolean preResubscribe()
    {
        return false;
    }

    public void failoverComplete()
    {
    }
}
