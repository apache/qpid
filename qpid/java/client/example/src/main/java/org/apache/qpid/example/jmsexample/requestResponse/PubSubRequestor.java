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
package org.apache.qpid.example.jmsexample.requestResponse;

import org.apache.qpid.example.jmsexample.BaseExample;

import javax.jms.*;

/**
 * This example illustrates the use of the JMS utility class <code>TopicRequestor</code>
 * which provides a synchronous RPC-like abstraction using temporary destinations
 * to deliver responses back to the client.
 */
public class PubSubRequestor extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "PubSubRequestor";

    /* The  topic name  */
    private String _topicName;

    /**
     * Create a PubSubRequestor client.
     *
     * @param args Command line arguments.
     */
    public PubSubRequestor(String[] args)
    {
        super(CLASS, args);
        _topicName = _argProcessor.getStringArgument("-topicName");
    }

    /**
     * Run the message requestor example.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        _options.put("-topicName", "The topic name");
        _defaults.put("-topicName", "world");
        PubSubRequestor requestor = new PubSubRequestor(args);
        requestor.runTest();
    }

    /**
     * Start the example.
     */
    private void runTest()
    {
        try
        {
            // Declare the connection
            TopicConnection connection = (TopicConnection) getConnection();

            // As this application is using a MessageConsumer we need to set an ExceptionListener on the connection
            // so that errors raised within the JMS client library can be reported to the application
            System.out.println(
                    CLASS + ": Setting an ExceptionListener on the connection as sample uses a MessageConsumer");

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    // The connection may have broken invoke reconnect code if available.
                    System.err.println(CLASS + ": The sample received an exception through the ExceptionListener");
                    System.exit(0);
                }
            });

            // Create a session on the connection.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            TopicSession session = connection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            // Lookup the destination
            System.out.println(CLASS + ": Looking up topic with name: " + _topicName);
            Topic destination = session.createTopic(_topicName);

            // Create a TopicRequestor
            System.out.println(CLASS + ": Creating a TopicRequestor");
            TopicRequestor requestor = new TopicRequestor(session, destination);

            // Now start the connection
            System.out.println(CLASS + ": Starting connection");
            connection.start();

            // Create a message to send as a request for service
            TextMessage request;
            request = session.createTextMessage(
                    "\"Twas brillig, and the slithy toves\",\n" + "\t\t\"Did gire and gymble in the wabe.\",\n" + "\t\t\"All mimsy were the borogroves,\",\n" + "\t\t\"And the mome raths outgrabe.\"");

            // Declare a message to be used for receiving any response
            Message response;

            // Get the number of times that this sample should request service
            for (int i = 0; i < getNumberMessages(); i++)
            {
                /**
                 * Set a message correlation value. This is not strictly required it is
                 * just an example of how messages requests can be tied together.
                 */
                request.setJMSCorrelationID("Message " + i);
                System.out.println(CLASS + ": Sending request " + i);

                response = requestor.request(request);

                // Print out the details of the message sent
                System.out.println(CLASS + ": Message details of request");
                System.out.println("\tID = " + request.getJMSMessageID());
                System.out.println("\tCorrelationID = " + request.getJMSCorrelationID());
                System.out.println("\tContents= " + ((TextMessage) request).getText());

                // Print out the details of the response received
                System.out.println(CLASS + ": Message details of response");
                System.out.println("\tID = " + response.getJMSMessageID());
                System.out.println("\tCorrelationID = " + response.getJMSCorrelationID());
                if (response instanceof TextMessage)
                {
                    System.out.println("\tContents= " + ((TextMessage) response).getText());
                }
            }
            // And send a final message to indicate termination.
            request.setText("That's all, folks!");
            MessageProducer messageProducer = session.createProducer(destination);
            messageProducer.send(request, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

            // Close the connection to the server
            System.out.println(CLASS + ": Closing connection");
            connection.close();

            // Close the JNDI reference
            System.out.println(CLASS + ": Closing JNDI context");
            getInitialContext().close();
        }
        catch (Exception exp)
        {
            System.err.println(CLASS + ": Caught an Exception: " + exp);
        }
    }
}
