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

import java.util.Random;

import javax.jms.*;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.url.URLSyntaxException;

/**
 * Listener implements the listening end of the Qpid interop tests. It is capable of being run as a standalone listener
 * that responds to the test messages send by the publishing end of the tests implemented by {@link org.apache.qpid.interop.old.Publisher}.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Count messages received on a topic. <td> {@link org.apache.qpid.interop.old.Publisher}
 * <tr><td> Send reports on messages received, when requested to. <td> {@link org.apache.qpid.interop.old.Publisher}
 * <tr><td> Shutdown, when requested to. <td> {@link org.apache.qpid.interop.old.Publisher}
 * <tr><td>
 *
 * @todo This doesn't implement the interop test spec yet. Its a port of the old topic tests but has been adapted with
 *       interop spec in mind.
 *
 * @todo I've added lots of field table types in the report message, just to check if the other end can decode them
 *       correctly. Not really the right place to test this, so remove them from {@link #sendReport()} once a better
 *       test exists.
 */
public class Listener implements MessageListener
{
    private static Logger log = Logger.getLogger(Listener.class);

    /** The default AMQ connection URL to use for tests. */
    public static final String DEFAULT_URI = "amqp://guest:guest@default/test?brokerlist='tcp://localhost:5672'";

    /** Holds the name of (routing key for) the topic to receive test messages on. */
    public static final String CONTROL_TOPIC = "topic_control";

    /** Holds the name of (routing key for) the queue to send reports to. */
    public static final String RESPONSE_QUEUE = "response";

    /** Holds the JMS Topic to receive test messages on. */
    private final Topic _topic;

    /** Holds the JMS Queue to send reports to. */
    private final Queue _response;

    /** Holds the connection to listen on. */
    private final Connection _connection;

    /** Holds the producer to send control messages on. */
    private final MessageProducer _controller;

    /** Holds the JMS session. */
    private final javax.jms.Session _session;

    /** Holds a flag to indicate that a timer has begun on the first message. Reset when report is sent. */
    private boolean init;

    /** Holds the count of messages received by this listener. */
    private int count;

    /** Used to hold the start time of the first message. */
    private long start;

    /**
     * Creates a topic listener using the specified broker URL.
     *
     * @param connectionUrl The broker URL to listen on.
     *
     * @throws AMQException If the broker connection cannot be established.
     * @throws URLSyntaxException If the broker URL syntax is not correct.
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    Listener(String connectionUrl) throws AMQException, JMSException, URLSyntaxException
    {
        log.debug("Listener(String connectionUrl = " + connectionUrl + "): called");

        // Create a connection to the broker.
        _connection = new AMQConnection(connectionUrl);

        // Establish a session on the broker.
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Set up the destinations to listen for test and control messages on.
        _topic = _session.createTopic(CONTROL_TOPIC);
        _response = _session.createQueue(RESPONSE_QUEUE);

        // Set this listener up to listen for incoming messages on the test topic.
        _session.createConsumer(_topic).setMessageListener(this);

        // Set up this listener with a producer to send the reports on.
        _controller = _session.createProducer(_response);

        _connection.start();
        System.out.println("Waiting for messages...");
    }

    /**
     * Starts a test subscriber. The broker URL must be specified as the first command line argument.
     *
     * @param argv The command line arguments, ignored.
     *
     * @todo Add command line arguments to configure all aspects of the test.
     */
    public static void main(String[] argv)
    {
        try
        {
            new Listener(DEFAULT_URI);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Handles all message received by this listener. Test messages are counted, report messages result in a report being sent and
     * shutdown messages result in this listener being terminated.
     *
     * @param message The received message.
     */
    public void onMessage(Message message)
    {
        log.debug("public void onMessage(Message message = " + message + "): called");

        // Take the start time of the first message if this is the first message.
        if (!init)
        {
            start = System.nanoTime() / 1000000;
            count = 0;
            init = true;
        }

        try
        {
            // Check if the message is a control message telling this listener to shut down.
            if (isShutdown(message))
            {
                log.debug("Got a shutdown message.");
                shutdown();
            }
            // Check if the message is a report request message asking this listener to respond with the message count.
            else if (isReport(message))
            {
                log.debug("Got a report request message.");

                // Send the message count report.
                sendReport();

                // Reset the initialization flag so that the next message is considered to be the first.
                init = false;
            }
            // Otherwise it is an ordinary test message, so increment the message count.
            else
            {
                count++;
            }
        }
        catch (JMSException e)
        {
            log.warn("There was a JMSException during onMessage.", e);
        }
    }

    /**
     * Checks a message to see if it is a termination request control message.
     *
     * @param m The message to check.
     *
     * @return <tt>true</tt> if it is a termination request control message, <tt>false</tt> otherwise.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    boolean isShutdown(Message m) throws JMSException
    {
        boolean result = checkTextField(m, "TYPE", "TERMINATION_REQUEST");

        return result;
    }

    /**
     * Checks a message to see if it is a report request control message.
     *
     * @param m The message to check.
     *
     * @return <tt>true</tt> if it is a report request control message, <tt>false</tt> otherwise.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    boolean isReport(Message m) throws JMSException
    {
        boolean result = checkTextField(m, "TYPE", "REPORT_REQUEST");

        return result;
    }

    /**
     * Checks whether or not a text field on a message has the specified value.
     *
     * @param m         The message to check.
     * @param fieldName The name of the field to check.
     * @param value     The expected value of the field to compare with.
     *
     * @return <tt>true</tt>If the specified field has the specified value, <tt>fals</tt> otherwise.
     *
     * @throws JMSException Any JMSExceptions are allowed to fall through.
     */
    private static boolean checkTextField(Message m, String fieldName, String value) throws JMSException
    {
        //log.debug("private static boolean checkTextField(Message m = " + m + ", String fieldName = " + fieldName
        //          + ", String value = " + value + "): called");

        String comp = m.getStringProperty(fieldName);
        //log.debug("comp = " + comp);

        boolean result = (comp != null) && comp.equals(value);
        //log.debug("result = " + result);

        return result;
    }

    /**
     * Closes down the connection to the broker.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    private void shutdown() throws JMSException
    {
        _session.close();
        _connection.stop();
        _connection.close();
    }

    /**
     * Send the report message to the response queue.
     *
     * @throws JMSException Any underlying JMSException is allowed to fall through.
     */
    private void sendReport() throws JMSException
    {
        log.debug("private void report(): called");

        // Create the report message.
        long time = ((System.nanoTime() / 1000000) - start);
        String msg = "Received " + count + " in " + time + "ms";
        Message message = _session.createTextMessage(msg);

        // Shove some more field table types in the message just to see if the other end can handle it.
        message.setBooleanProperty("BOOLEAN", true);
        //message.setByteProperty("BYTE", (byte) 5);
        message.setDoubleProperty("DOUBLE", Math.PI);
        message.setFloatProperty("FLOAT", 1.0f);
        message.setIntProperty("INT", 1);
        message.setShortProperty("SHORT", (short) 1);
        message.setLongProperty("LONG", (long) 1827361278);
        message.setStringProperty("STRING", "hello");

        // Send the report message.
        _controller.send(message);
        log.debug("Sent report: " + msg);
    }
}
