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

package org.apache.qpid.example.simple.reqresp;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class Server implements MessageListener
{
    final String BROKER = "localhost";

    final String INITIAL_CONTEXT_FACTORY = "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";

    final String CONNECTION_JNDI_NAME = "local";
    final String CONNECTION_NAME = "amqp://guest:guest@clientid/test?brokerlist='" + BROKER + "'";

    final String QUEUE_JNDI_NAME = "queue";
    final String QUEUE_NAME = "example.RequestQueue";

    private static boolean TRANSACTED = true;
    private static final boolean NOT_TRANSACTED = !TRANSACTED;


    private InitialContext _ctx;
    private Session _session;
    private MessageProducer _replyProducer;
    private CountDownLatch _shutdownHook = new CountDownLatch(1);

    public Server()
    {
        setupJNDI();

        Connection connection = null;
        try
        {
            connection = ((ConnectionFactory) lookupJNDI(CONNECTION_JNDI_NAME)).createConnection();

            _session = connection.createSession(NOT_TRANSACTED, Session.AUTO_ACKNOWLEDGE);

            Destination requestQueue = (Queue) lookupJNDI(QUEUE_JNDI_NAME);

            closeJNDI();

            //Setup a message producer to respond to messages from clients, we will get the destination
            //to send to from the JMSReplyTo header field from a Message so we MUST set the destination here to null.
            this._replyProducer = _session.createProducer(null);

            //Set up a consumer to consume messages off of the request queue
            MessageConsumer consumer = _session.createConsumer(requestQueue);
            consumer.setMessageListener(this);

            //Now start the connection
            connection.start();
        }
        catch (JMSException e)
        {
            //Handle the exception appropriately
            System.err.println("JMSException occured setting up server :" + e);
            return;
        }

        //Wait to process an single message then quit.
        try
        {
            _shutdownHook.await();
        }
        catch (InterruptedException e)
        {
            // Ignore this as we are quitting anyway.
        }

        //Close the connection
        try
        {
            connection.close();
        }
        catch (JMSException e)
        {
            System.err.println("A problem occured while shutting down the connection : " + e);
        }
    }

    public void onMessage(Message message)
    {
        try
        {
            TextMessage response = this._session.createTextMessage();

            //Check we have the right message type.
            if (message instanceof TextMessage)
            {
                TextMessage txtMsg = (TextMessage) message;
                String messageText = txtMsg.getText();

                //Perform the request
                System.out.println("Received request:" + messageText + " for message :" + message.getJMSMessageID());

                //Set the response back to the client
                response.setText("Response to Request:" + messageText);
            }

            //Set the correlation ID from the received message to be the correlation id of the response message
            //this lets the client identify which message this is a response to if it has more than
            //one outstanding message to the server
            response.setJMSCorrelationID(message.getJMSCorrelationID());

            //Send the response to the Destination specified by the JMSReplyTo field of the received message,
            //this is presumably a temporary queue created by the client
            _replyProducer.send(message.getJMSReplyTo(), response);
        }
        catch (JMSException e)
        {
            //Handle the exception appropriately
        }

        _shutdownHook.countDown();
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
        Context ctx = null;
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
        new Server();
    }
}
