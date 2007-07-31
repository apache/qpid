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
package org.apache.qpid.interop.old;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.url.URLSyntaxException;

/**
 * Publisher is the sending end of Qpid interop tests. It is capable of being run as a standalone publisher
 * that sends test messages to the listening end of the tests implemented by {@link org.apache.qpid.interop.old.Listener}.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td>
 *
 * @todo This doesn't implement the interop test spec yet. Its a port of the old topic tests but has been adapted with
 *       interop spec in mind.
 *
 * @todo I've added lots of field table types in the report request message, just to check if the other end can decode
 *       them correctly. Not really the right place to test this, so remove them from {@link #doTest()} once a better
 *       test exists.
 */
public class Publisher implements MessageListener
{
    private static Logger log = Logger.getLogger(Publisher.class);

    /** The default AMQ connection URL to use for tests. */
    public static final String DEFAULT_URI = "amqp://guest:guest@default/test?brokerlist='tcp://localhost:5672'";

    /** Holds the default test timeout for broker communications before tests give up. */
    public static final int TIMEOUT = 3000;

    /** Holds the routing key for the topic to send test messages on. */
    public static final String CONTROL_TOPIC = "topic_control";

    /** Holds the routing key for the queue to receive reports on. */
    public static final String RESPONSE_QUEUE = "response";

    /** Holds the JMS Topic to send test messages on. */
    private final Topic _topic;

    /** Holds the JMS Queue to receive reports on. */
    private final Queue _response;

    /** Holds the number of messages to send in each test run. */
    private int numMessages;

    /** A monitor used to wait for all reports to arrive back from consumers on. */
    private CountDownLatch allReportsReceivedEvt;

    /** Holds the connection to listen on. */
    private Connection _connection;

    /** Holds the channel for all test messages.*/
    private Session _session;

    /** Holds the producer to send test messages on. */
    private MessageProducer publisher;

    /**
     * Creates a topic publisher that will send the specifed number of messages and expect the specifed number of report back from test
     * subscribers.
     *
     * @param connectionUri  The broker URL.
     * @param numMessages    The number of messages to send in each test.
     * @param numSubscribers The number of subscribes that are expected to reply with a report.
     */
    Publisher(String connectionUri, int numMessages, int numSubscribers)
       throws AMQException, JMSException, URLSyntaxException
    {
        log.debug("Publisher(String connectionUri = " + connectionUri + ", int numMessages = " + numMessages
                  + ", int numSubscribers = " + numSubscribers + "): called");

        // Create a connection to the broker.
        _connection = new AMQConnection(connectionUri);

        // Establish a session on the broker.
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Set up the destinations to send test messages and listen for reports on.
        _topic = _session.createTopic(CONTROL_TOPIC);
        _response = _session.createQueue(RESPONSE_QUEUE);

        // Set this listener up to listen for reports on the response queue.
        _session.createConsumer(_response).setMessageListener(this);

        // Set up this listener with a producer to send the test messages and report requests on.
        publisher = _session.createProducer(_topic);

        // Keep the test parameters.
        this.numMessages = numMessages;

        // Set up a countdown to count all subscribers sending their reports.
        allReportsReceivedEvt = new CountDownLatch(numSubscribers);

        _connection.start();
        System.out.println("Sending messages and waiting for reports...");
    }

    /**
     * Start a test publisher. The broker URL must be specified as the first command line argument.
     *
     * @param argv The command line arguments, ignored.
     *
     * @todo Add command line arguments to configure all aspects of the test.
     */
    public static void main(String[] argv)
    {
        try
        {
            // Create an instance of this publisher with the command line parameters.
            Publisher publisher = new Publisher(DEFAULT_URI, 1, 1);

            // Publish the test messages.
            publisher.doTest();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Sends the test messages and waits for all subscribers to reply with a report.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    public void doTest() throws JMSException
    {
        log.debug("public void DoTest(): called");

        // Create a test message to send.
        Message testMessage = _session.createTextMessage("test");

        // Send the desired number of test messages.
        for (int i = 0; i < numMessages; i++)
        {
            publisher.send(testMessage);
        }

        log.debug("Sent " + numMessages + " test messages.");

        // Send the report request.
        Message reportRequestMessage = _session.createTextMessage("Report request message.");
        reportRequestMessage.setStringProperty("TYPE", "REPORT_REQUEST");

        reportRequestMessage.setBooleanProperty("BOOLEAN", false);
        //reportRequestMessage.Headers.SetByte("BYTE", 5);
        reportRequestMessage.setDoubleProperty("DOUBLE", 3.141);
        reportRequestMessage.setFloatProperty("FLOAT", 1.0f);
        reportRequestMessage.setIntProperty("INT", 1);
        reportRequestMessage.setLongProperty("LONG", 1);
        reportRequestMessage.setStringProperty("STRING", "hello");
        reportRequestMessage.setShortProperty("SHORT", (short) 2);

        publisher.send(reportRequestMessage);

        log.debug("Sent the report request message, waiting for all replies...");

        // Wait until all the reports come in.
        try
        {
            allReportsReceivedEvt.await(TIMEOUT, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        { }

        // Check if all reports were really received or if the timeout occurred.
        if (allReportsReceivedEvt.getCount() == 0)
        {
            log.debug("Got all reports.");
        }
        else
        {
            log.debug("Waiting for reports timed out, still waiting for " + allReportsReceivedEvt.getCount() + ".");
        }

        // Send the termination request.
        Message terminationRequestMessage = _session.createTextMessage("Termination request message.");
        terminationRequestMessage.setStringProperty("TYPE", "TERMINATION_REQUEST");
        publisher.send(terminationRequestMessage);

        log.debug("Sent the termination request message.");

        // Close all message producers and consumers and the connection to the broker.
        shutdown();
    }

    /**
     * Handles all report messages from subscribers. This decrements the count of subscribers that are still to reply, until this becomes
     * zero, at which time waiting threads are notified of this event.
     *
     * @param message The received report message.
     */
    public void onMessage(Message message)
    {
        log.debug("public void OnMessage(Message message = " + message + "): called");

        // Decrement the count of expected messages and release the wait monitor when this becomes zero.
        allReportsReceivedEvt.countDown();

        if (allReportsReceivedEvt.getCount() == 0)
        {
            log.debug("Got reports from all subscribers.");
        }
    }

    /**
     * Stops the message consumers and closes the connection.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    private void shutdown() throws JMSException
    {
        _session.close();
        _connection.close();
    }
}
