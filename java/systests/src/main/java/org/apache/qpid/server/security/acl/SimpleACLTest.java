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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.AMQException;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;
import javax.jms.IllegalStateException;
import java.io.File;

public class SimpleACLTest extends QpidTestCase implements ConnectionListener
{
    private String BROKER = "vm://:1";//"tcp://localhost:5672";

    public void setUp() throws Exception
    {
        //Shutdown the QTC broker
        stopBroker();

        // Initialise ACLs.
        final String QpidExampleHome = System.getProperty("QPID_EXAMPLE_HOME");
        final File defaultaclConfigFile = new File(QpidExampleHome, "etc/acl.config.xml");

        if (!defaultaclConfigFile.exists())
        {
            System.err.println("Configuration file not found:" + defaultaclConfigFile);
            fail("Configuration file not found:" + defaultaclConfigFile);
        }

        if (System.getProperty("QPID_HOME") == null)                                                                                            
        {
            fail("QPID_HOME not set");
        }

        ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(defaultaclConfigFile);
        ApplicationRegistry.initialise(config, 1);
        TransportConnection.createVMBroker(1);
    }

    public void tearDown()
    {
        TransportConnection.killAllVMBrokers();
        ApplicationRegistry.remove(1);
    }

    public String createConnectionString(String username, String password, String broker)
    {

        return "amqp://" + username + ":" + password + "@clientid/test?brokerlist='" + broker + "?retries='0''";
    }

    public void testAccessAuthorized() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = createConnection("client", "guest");

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
            Connection conn = createConnection("guest", "guest");

            //Attempt to do do things to test connection.
            Session sesh = conn.createSession(true, Session.SESSION_TRANSACTED);
            conn.start();
            sesh.rollback();

            conn.close();
            fail("Connection was created.");
        }
        catch (AMQException amqe)
        {
            Throwable cause = amqe.getCause();
            assertEquals("Exception was wrong type", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code thrown", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testClientConsumeFromTempQueueValid() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = createConnection("client", "guest");

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
            Connection conn = createConnection("client", "guest");

            //Prevent Failover
            ((AMQConnection) conn).setConnectionListener(this);

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createQueue("IllegalQueue"));
            fail("Test failed as consumer was created.");
            //conn will be automatically closed
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
            Connection conn = createConnection("client", "guest");

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create Temporary Queue  - can't use the createTempQueue as QueueName is null.
            ((AMQSession) sesh).createQueue(new AMQShortString("doesnt_matter_as_autodelete_means_tmp"),
                                            true, false, false);

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
            Connection conn = createConnection("client", "guest");

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create a Named Queue
            ((AMQSession) sesh).createQueue(new AMQShortString("IllegalQueue"), false, false, false);

            fail("Test failed as Queue creation succeded.");
            //conn will be automatically closed
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
            Connection conn = createConnection("client", "guest");

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
            Connection conn = createConnection("client", "guest");

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
            Connection conn = createConnection("client", "guest");

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
            // This may fail as the session may be closed before the queue or the consumer created.
            Queue temp = session.createTemporaryQueue();

            session.createConsumer(temp).close();

            //Connection should now be closed and will throw the exception caused by the above send
            conn.close();

            fail("Close is not expected to succeed.");
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();
            if (!(cause instanceof AMQAuthenticationException))
            {
                e.printStackTrace();
            }
            assertEquals("Incorrect exception", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code thrown", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testServerConsumeFromNamedQueueValid() throws AMQException, URLSyntaxException
    {
        try
        {
            Connection conn = createConnection("server", "guest");

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
            Connection conn = createConnection("client", "guest");

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createQueue("Invalid"));

            fail("Test failed as consumer was created.");
            //conn will be automatically closed
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
            Connection conn = createConnection("server","guest");

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            sesh.createConsumer(sesh.createTemporaryQueue());
            fail("Test failed as consumer was created.");
            //conn will be automatically closed
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();

            assertNotNull("There was no liked exception", cause);
            assertEquals("Wrong linked exception type", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code received", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    private Connection createConnection(String username, String password) throws AMQException
    {
        AMQConnection connection = null;
        try
        {
            connection = new AMQConnection(createConnectionString(username, password, BROKER));
        }
        catch (URLSyntaxException e)
        {
            // This should never happen as we generate the URLs.
            fail(e.getMessage());
        }

        //Prevent Failover
        connection.setConnectionListener(this);

        return (Connection)connection;
    }

    public void testServerCreateNamedQueueValid() throws JMSException, URLSyntaxException
    {
        try
        {
            Connection conn = createConnection("server", "guest");

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create Temporary Queue
            ((AMQSession) sesh).createQueue(new AMQShortString("example.RequestQueue"), false, false, false);

            conn.close();
        }
        catch (Exception e)
        {
            fail("Test failed due to:" + e.getMessage());
        }
    }

    public void testServerCreateNamedQueueInvalid() throws JMSException, URLSyntaxException, AMQException
    {
        try
        {
            Connection conn = createConnection("server", "guest");

            Session sesh = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            //Create a Named Queue
            ((AMQSession) sesh).createQueue(new AMQShortString("IllegalQueue"), false, false, false);

            fail("Test failed as creation succeded.");
            //conn will be automatically closed
        }
        catch (AMQAuthenticationException amqe)
        {
            assertEquals("Incorrect error code thrown", 403, amqe.getErrorCode().getCode());
        }
    }

    public void testServerCreateTemporaryQueueInvalid() throws JMSException, URLSyntaxException, AMQException
    {
        try
        {
            Connection conn = createConnection("server", "guest");

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            conn.start();

            session.createTemporaryQueue();
                    
            fail("Test failed as creation succeded.");
            //conn will be automatically closed
        }
        catch (JMSException e)
        {
            Throwable cause = e.getLinkedException();

            assertNotNull("There was no liked exception", cause);
            assertEquals("Wrong linked exception type", AMQAuthenticationException.class, cause.getClass());
            assertEquals("Incorrect error code received", 403, ((AMQAuthenticationException) cause).getErrorCode().getCode());
        }
    }

    public void testServerCreateAutoDeleteQueueInvalid() throws JMSException, URLSyntaxException, AMQException
    {
        Connection connection = null;
        try
        {
            connection = createConnection("server", "guest");

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            connection.start();

            ((AMQSession) session).createQueue(new AMQShortString("again_ensure_auto_delete_queue_for_temporary"),
                                            true, false, false);

            fail("Test failed as creation succeded.");
            //connection will be automatically closed
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
        Connection serverConnection = createConnection("server", "guest");

        ((AMQConnection) serverConnection).setConnectionListener(this);

        Session serverSession = serverConnection.createSession(true, Session.SESSION_TRANSACTED);

        Queue requestQueue = serverSession.createQueue("example.RequestQueue");

        MessageConsumer server = serverSession.createConsumer(requestQueue);

        serverConnection.start();

        //Set up the consumer
        Connection clientConnection = createConnection("client", "guest");

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

    public void testServerPublishInvalidQueueSuccess() throws AMQException, URLSyntaxException, JMSException
    {
        try
        {
            Connection conn = createConnection("server", "guest");

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
            // This may not work as the session may be closed before the queue or consumer creation can occur.
            // The correct JMSexception with linked error will only occur when the close method is recevied whilst in
            // the failover safe block
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
