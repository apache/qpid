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

package org.apache.qpid.example.simple.point2point;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

/**
 * Qpid Examples: Simple Point to Point
 *
 * This example file shows how to send and recieve a message using only JMS functionality.
 *
 * JNDI is used to extract connection and queue configuration.
 *
 * A single message is sent then synchronously received.
 */
public class Simple
{
    final String BROKER = "localhost";

    final String INITIAL_CONTEXT_FACTORY = "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";

    final String CONNECTION_JNDI_NAME = "local";
    final String CONNECTION_NAME = "amqp://guest:guest@clientid/test?brokerlist='" + BROKER + "'";

    final String QUEUE_JNDI_NAME = "queue";
    final String QUEUE_NAME = "example.Queue";


    private InitialContext _ctx;

    private static final boolean NOT_TRANSACTED = false;

    private Session _producerSession;
    private MessageProducer _producer;
    private MessageConsumer _consumer;

    private Connection _connection;

    /**
     * This is the Simple example that demonstates how to send and receive a message.
     *
     * @throws JMSException if it cannot setup the Connection
     */
    public Simple() throws JMSException
    {
        setupJNDI();

        setupConnection();
    }

    /**
     * Setup the JMS Connection.
     * Using JNDI create a new Connection which contains sessions for publishing and consuming.
     * When these are all set up the connection can be started.
     *
     * @throws JMSException if it cannot setup the Connection
     */
    private void setupConnection() throws JMSException
    {
        try
        {
            _connection = ((ConnectionFactory) lookupJNDI(CONNECTION_JNDI_NAME)).createConnection();

            Destination queue = (Queue) lookupJNDI(QUEUE_JNDI_NAME);

            // The JNDI context needs to be closed when we have finished using it
            closeJNDI();

            // For the purposes of this simple example the sessions are NOT transacted and utilise Auto acknowledgements
            // The publisher session is a member variable to allow for the later creation of messages using the session.
            _producerSession = _connection.createSession(NOT_TRANSACTED, Session.AUTO_ACKNOWLEDGE);

            //The creation of the consumer on a second session is not as important in this simple example
            // however when using transactions they should be kept seperate as commits are done at the session level.
            Session consumerSession = _connection.createSession(NOT_TRANSACTED, Session.AUTO_ACKNOWLEDGE);

            _producer = _producerSession.createProducer(queue);

            _consumer = consumerSession.createConsumer(queue);

            // Now the connection is setup up start it.
            _connection.start();

        }
        catch (JMSException e)
        {
            System.err.println("Unable to setup connection, client and producer on broker");
            throw e;
        }
    }

    public void sendTestMessage() throws JMSException
    {
        TextMessage txtMessage;
        try
        {
            //Create the actual message you want to send
            txtMessage = _producerSession.createTextMessage("Sample Message.");
        }
        catch (JMSException e)
        {
            System.err.println("Unable to create message");
            return;
        }

        try
        {
            _producer.send(txtMessage);
        }
        catch (JMSException e)
        {
            //Handle the exception appropriately
            System.out.println("Unable to send message due to:" + e.getMessage());

            //Reasons for failure:
            // IllegalStateException, if the session is null or has been closed.
            // UnsupportedOperationException, if the send destination is null.
            // JMSException "Cannot send to a deleted temporary destination"

            throw e;
        }
    }

    public void receiveTestMessage()
    {
        Message receivedMessage;

        try
        {
            // Wait at most 1 second for the message to arrive.
            receivedMessage = _consumer.receive(1000);
        }
        catch (JMSException e)
        {
            System.out.println("An exception occured receiving message:" + e.getMessage());
            return;
        }

        if (receivedMessage != null)
        {
            if (receivedMessage instanceof TextMessage)
            {
                try
                {
                    System.out.println("Recieved message:" + ((TextMessage) receivedMessage).getText());
                }
                catch (JMSException e)
                {
                    System.out.println("Message received:" + receivedMessage);
                    System.out.println("Unable to getText() from message due to:" + e.getMessage());
                }
            }
            else
            {
                System.out.println("Message received is not a text message:" + receivedMessage);
            }
        }
        else
        {
            System.out.println("A message was not received");
        }
    }


    public void shutdownCleanly()
    {
        //Close the connection
        try
        {
            _connection.close();
        }
        catch (JMSException e)
        {
            System.err.println("A problem occured while shutting down the connection : " + e);
        }
    }


    /**
     * Lookup the specified name in the JNDI Context.
     *
     * @param name The string name of the object to lookup
     *
     * @return The object or null if nothing exists for specified name
     */
    private Object lookupJNDI(String name)
    {
        try
        {
            return _ctx.lookup(name);
        }
        catch (NamingException e)
        {
            System.err.println("Error looking up '" + name + "' in JNDI Context:" + e);
        }

        return null;
    }

    /**
     * Setup the JNDI context.
     *
     * In this case we are simply using a Properties object to store the pairing information.
     *
     * Further details can be found on the wiki site here:
     *
     * @see : http://cwiki.apache.org/qpid/how-to-use-jndi.html
     */
    private void setupJNDI()
    {
        // Set the properties ...
        Properties properties = new Properties();
        properties.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
        properties.put("connectionfactory." + CONNECTION_JNDI_NAME, CONNECTION_NAME);
        properties.put("queue." + QUEUE_JNDI_NAME, QUEUE_NAME);

        // Create the initial context
        try
        {
            _ctx = new InitialContext(properties);
        }
        catch (NamingException e)
        {
            System.err.println("Error Setting up JNDI Context:" + e);
        }
    }

    /** Close the JNDI Context to keep everything happy. */
    private void closeJNDI()
    {
        try
        {
            _ctx.close();
        }
        catch (NamingException e)
        {
            System.err.println("Unable to close JNDI Context : " + e);
        }
    }


    public static void main(String[] args)
    {
        Simple simple = null;

        try
        {
            simple = new Simple();
        }
        catch (JMSException e)
        {
            System.err.println("Unable to create Simple test due to :" + e.getMessage());
            System.exit(1);
        }

        try
        {


            try
            {
                simple.sendTestMessage();
            }
            catch (JMSException e)
            {
                System.err.println("Unable to send Simple test message due to :" + e.getMessage());
                System.exit(1);
            }

            simple.receiveTestMessage();

        }
        finally
        {
            simple.shutdownCleanly();
        }
    }


}
