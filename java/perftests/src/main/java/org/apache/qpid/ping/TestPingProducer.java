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
package org.apache.qpid.ping;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;

/**
 * PingProducer is a client that sends timestamped pings to a queue. It is designed to be run from the command line
 * as a stand alone test tool, but it may also be fairly easily instantiated by other code by supplying a session and
 * configured message producer.
 *
 * <p/>This implements the Runnable interface with a run method that implements an infinite ping loop. The ping loop
 * does all its work through helper methods, so that code wishing to run a ping cycle is not forced to do so
 * by starting a new thread. The command line invocation does take advantage of this ping loop. A shutdown hook is
 * also registered to terminate the ping loop cleanly.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a ping cycle.
 * <tr><td> Provide command line invocation to loop the ping cycle on a configurable broker url.
 * </table>
 */
class TestPingProducer extends AbstractPingProducer
{
    private static final Logger _logger = Logger.getLogger(TestPingProducer.class);

    /** Used to set up a default message size. */
    private static final int DEFAULT_MESSAGE_SIZE = 0;

    /** Used to define how long to wait between pings. */
    private static final long SLEEP_TIME = 250;

    /** Holds the name of the queue to send pings on. */
    private static final String PING_QUEUE_NAME = "ping";

    private static TestPingProducer _pingProducer;

    /** Holds the message producer to send the pings through. */
    private MessageProducer _producer;

    /** Determines whether this producer sends persistent messages from the run method. */
    private boolean _persistent = false;

    /** Holds the message size to send, from the run method. */
    private int _messageSize = DEFAULT_MESSAGE_SIZE;

    /** Used to indicate that the ping loop should print out whenever it pings. */
    private boolean _verbose = false;

    public TestPingProducer(String brokerDetails, String username, String password, String virtualpath, String queueName,
                            boolean transacted, boolean persistent, int messageSize, boolean verbose) throws Exception
    {
        // Create a connection to the broker.
        InetAddress address = InetAddress.getLocalHost();
        String clientID = address.getHostName() + System.currentTimeMillis();

        setConnection(new AMQConnection(brokerDetails, username, password, clientID, virtualpath));

        // Create a transactional or non-transactional session, based on the command line arguments.
        setProducerSession((Session) getConnection().createSession(transacted, Session.AUTO_ACKNOWLEDGE));

        // Create a queue to send the pings on.
        Queue pingQueue = new AMQQueue(queueName);
        _producer = (MessageProducer) getProducerSession().createProducer(pingQueue);

        _persistent = persistent;
        _messageSize = messageSize;

        _verbose = verbose;
    }

    /**
     * Starts a ping-pong loop running from the command line. The bounce back client {@link TestPingClient} also needs
     * to be started to bounce the pings back again.
     *
     * @param args The command line arguments as defined above.
     */
    public static void main(String[] args) throws Exception
    {
        // Extract the command line.
        if (args.length < 2)
        {
            System.err.println(
                "Usage: TestPingPublisher <brokerDetails> <virtual path> [verbose] [transacted] [persistent] [message size in bytes]");
            System.exit(0);
        }

        String brokerDetails = args[0];
        String virtualpath = args[1];
        boolean verbose = (args.length >= 3) ? Boolean.parseBoolean(args[2]) : true;
        boolean transacted = (args.length >= 4) ? Boolean.parseBoolean(args[3]) : false;
        boolean persistent = (args.length >= 5) ? Boolean.parseBoolean(args[4]) : false;
        int messageSize = (args.length >= 6) ? Integer.parseInt(args[5]) : DEFAULT_MESSAGE_SIZE;

        // Create a ping producer to generate the pings.
        _pingProducer = new TestPingProducer(brokerDetails, "guest", "guest", virtualpath, PING_QUEUE_NAME, transacted,
                                             persistent, messageSize, verbose);

        // Start the connection running.
        _pingProducer.getConnection().start();

        // Create a shutdown hook to terminate the ping-pong producer.
        Runtime.getRuntime().addShutdownHook(_pingProducer.getShutdownHook());

        // Ensure the ping loop execption listener is registered on the connection to terminate it on error.
        _pingProducer.getConnection().setExceptionListener(_pingProducer);

        // Start the ping loop running until it is interrupted.
        Thread pingThread = new Thread(_pingProducer);
        pingThread.run();
        pingThread.join();
    }

    /**
     * Sends the specified ping message.
     *
     * @param message The message to send.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    public void ping(Message message) throws JMSException
    {
        _producer.send(message);

        // Keep the messageId to correlate with the reply.
        String messageId = message.getJMSMessageID();

        // Commit the transaction if running in transactional mode. This must happen now, rather than at the end of
        // this method, as the message will not be sent until the transaction is committed.
        commitTx(getProducerSession());
    }

    /**
     * The ping loop implementation. This send out pings of the configured size, persistence and transactionality, and
     * waits for short pauses in between each.
     */
    public void pingLoop()
    {
        try
        {
            // Generate a sample message and time stamp it.
            ObjectMessage msg = getTestMessage(null, _messageSize, _persistent);
            msg.setLongProperty("timestamp", System.currentTimeMillis());

            // Send the message.
            ping(msg);

            if (_verbose)
            {
                System.out.println("Pinged at: " + timestampFormatter.format(new Date())); //" + " with id: " + msg.getJMSMessageID());
            }
            // Introduce a short pause if desired.
            pause(SLEEP_TIME);
        }
        catch (JMSException e)
        {
            _publish = false;
            _logger.error("There was a JMSException: " + e.getMessage(), e);
        }
    }
}
