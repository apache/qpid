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
package org.apache.qpid.requestreply;

import org.apache.log4j.Logger;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;
import org.apache.qpid.ping.AbstractPingProducer;
import org.apache.qpid.util.concurrent.BooleanLatch;

import javax.jms.*;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * PingPongProducer is a client that sends pings to a queue and waits for pongs to be bounced back by a bounce back
 * client (see {@link org.apache.qpid.requestreply.PingPongClient} for the bounce back client). It is designed to be run from the command line
 * as a stand alone test tool, but it may also be fairly easily instantiated by other code by supplying a session,
 * message producer and message consumer to run the ping-pong cycle on.
 *
 * <p/>The pings are sent with a reply-to field set to a single temporary queue, which is the same for all pings.
 * This means that this class has to do some work to correlate pings with pongs; it expectes the original message
 * id in the ping to be bounced back in the correlation id. If a new temporary queue per ping were used, then
 * this correlation would not need to be done.
 *
 * <p/>This implements the Runnable interface with a run method that implements an infinite ping loop. The ping loop
 * does all its work through helper methods, so that code wishing to run a ping-pong cycle is not forced to do so
 * by starting a new thread. The command line invocation does take advantage of this ping loop. A shutdown hook is
 * also registered to terminate the ping-pong loop cleanly.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a ping and wait for response cycle.
 * <tr><td> Provide command line invocation to loop the ping cycle on a configurable broker url.
 * </table>
 *
 * @todo Make temp queue per ping a command line option.
 *
 * @todo Make the queue name a command line option.
 */
public class PingPongProducer extends AbstractPingProducer implements Runnable, MessageListener, ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(PingPongProducer.class);

    /** Used to set up a default message size. */
    private static final int DEFAULT_MESSAGE_SIZE = 0;

    /** Used to define how long to wait between pings. */
    private static final long SLEEP_TIME = 250;

    /** Used to define how long to wait before assuming that a ping has timed out. */
    private static final long TIMEOUT = 3000;

    /** Holds the name of the queue to send pings on. */
    private static final String PING_QUEUE_NAME = "ping";

    /** Keeps track of the ping producer instance used in the run loop. */
    private static PingPongProducer _pingProducer;

    /** Holds the message producer to send the pings through. */
    private MessageProducer _producer;

    /** Holds the queue to send the ping replies to. */
    private Queue _replyQueue;

    /** Determines whether this producer sends persistent messages from the run method. */
    private boolean _persistent;

    /** Holds the message size to send, from the run method. */
    private int _messageSize;

    /** Holds a map from message ids to latches on which threads wait for replies. */
    private Map<String, BooleanLatch> trafficLights = new HashMap<String, BooleanLatch>();

    /** Holds a map from message ids to correlated replies. */
    private Map<String, Message> replies = new HashMap<String, Message>();

    public PingPongProducer(Session session, Queue replyQueue, MessageProducer producer, MessageConsumer consumer)
                     throws JMSException
    {
        super(session);
        _producer = producer;
        _replyQueue = replyQueue;

        consumer.setMessageListener(this);
    }

    public PingPongProducer(Session session, Queue replyQueue, MessageProducer producer, MessageConsumer consumer,
                            boolean persistent, int messageSize) throws JMSException
    {
        this(session, replyQueue, producer, consumer);

        _persistent = persistent;
        _messageSize = messageSize;
    }

    /**
     * Starts a ping-pong loop running from the command line. The bounce back client {@link org.apache.qpid.requestreply.PingPongClient} also needs
     * to be started to bounce the pings back again.
     *
     * <p/>The command line takes from 2 to 4 arguments:
     * <p/><table>
     * <tr><td>brokerDetails <td> The broker connection string.
     * <tr><td>virtualPath   <td> The virtual path.
     * <tr><td>transacted    <td> A boolean flag, telling this client whether or not to use transactions.
     * <tr><td>size          <td> The size of ping messages to use, in bytes.
     * </table>
     *
     * @param args The command line arguments as defined above.
     */
    public static void main(String[] args) throws Exception
    {
        // Extract the command line.
        if (args.length < 2)
        {
            System.err.println(
                "Usage: TestPingPublisher <brokerDetails> <virtual path> [transacted] [persistent] [message size in bytes]");
            System.exit(0);
        }

        String brokerDetails = args[0];
        String virtualpath = args[1];
        boolean transacted = (args.length >= 3) ? Boolean.parseBoolean(args[2]) : false;
        boolean persistent = (args.length >= 4) ? Boolean.parseBoolean(args[3]) : false;
        int messageSize = (args.length >= 5) ? Integer.parseInt(args[4]) : DEFAULT_MESSAGE_SIZE;

        // Create a connection to the broker.
        InetAddress address = InetAddress.getLocalHost();
        String clientID = address.getHostName() + System.currentTimeMillis();

        Connection _connection = new AMQConnection(brokerDetails, "guest", "guest", clientID, virtualpath);

        // Create a transactional or non-transactional session, based on the command line arguments.
        Session session;

        if (transacted)
        {
            session = (Session) _connection.createSession(true, Session.SESSION_TRANSACTED);
        }
        else
        {
            session = (Session) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        }

        // Create a queue to send the pings on.
        Queue pingQueue = new AMQQueue(PING_QUEUE_NAME);
        MessageProducer producer = (MessageProducer) session.createProducer(pingQueue);

        // Create a temporary queue to reply with the pongs on.
        Queue replyQueue = session.createTemporaryQueue();

        // Create a message consumer to get the replies with.
        MessageConsumer consumer = session.createConsumer(replyQueue);

        // Create a ping producer to handle the request/wait/reply cycle.
        _pingProducer = new PingPongProducer(session, replyQueue, producer, consumer, persistent, messageSize);

        // Start the message consumers running.
        _connection.start();

        // Create a shutdown hook to terminate the ping-pong producer.
        Runtime.getRuntime().addShutdownHook(_pingProducer.getShutdownHook());

        // Start the ping loop running, ensuring that it is registered to listen for exceptions on the connection too.
        _connection.setExceptionListener(_pingProducer);
        Thread pingThread = new Thread(_pingProducer);
        pingThread.run();

        // Run until the ping loop is terminated.
        pingThread.join();
    }

    /**
     * Stores the received message in the replies map, then resets the boolean latch that a thread waiting for a
     * correlating reply may be waiting on.
     *
     * @param message The received message.
     */
    public void onMessage(Message message)
    {
        try
        {
            // Store the reply.
            String correlationID = message.getJMSCorrelationID();
            replies.put(correlationID, message);

            // Turn the traffic light to green.
            BooleanLatch trafficLight = trafficLights.get(correlationID);

            if (trafficLight != null)
            {
                trafficLight.signal();
            }
            else
            {
                _logger.debug("There was no thread waiting for reply: " + correlationID);
            }
        }
        catch (JMSException e)
        {
            _logger.warn("There was a JMSException: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the specified ping message and then waits for a correlating reply. If the wait times out before a reply
     * arrives, then a null reply is returned from this method.
     *
     * @param message The message to send.
     * @param timeout The timeout in milliseconds.
     *
     * @return The reply, or null if no reply arrives before the timeout.
     *
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    public Message pingAndWaitForReply(Message message, long timeout) throws JMSException, InterruptedException
    {
        _producer.send(message);

        // Keep the messageId to correlate with the reply.
        String messageId = message.getJMSMessageID();

        // Commit the transaction if running in transactional mode. This must happen now, rather than at the end of
        // this method, as the message will not be sent until the transaction is committed.
        commitTx();

        // Block the current thread until a reply to the message is received, or it times out.
        BooleanLatch trafficLight = new BooleanLatch();
        trafficLights.put(messageId, trafficLight);

        // Note that this call expects a timeout in nanoseconds, millisecond timeout is multiplied up.
        trafficLight.await(timeout * 1000);

        // Check the replies to see if one was generated, if not then the reply timed out.
        Message result = replies.get(messageId);

        return result;
    }

    /**
     * Callback method, implementing ExceptionListener. This should be registered to listen for exceptions on the
     * connection, this clears the publish flag which in turn will halt the ping loop.
     *
     * @param e The exception that triggered this callback method.
     */
    public void onException(JMSException e)
    {
        _publish = false;
        _logger.debug("There was a JMSException: " + e.getMessage(), e);
    }

    /**
     * The ping loop implementation. This send out pings of the configured size, persistence and transactionality, and
     * waits for replies and inserts short pauses in between each.
     */
    public void pingLoop()
    {
        try
        {
            // Generate a sample message and time stamp it.
            ObjectMessage msg = getTestMessage(_session, _replyQueue, _messageSize, System.currentTimeMillis(), _persistent);
            msg.setLongProperty("timestamp", System.currentTimeMillis());

            // Send the message and wait for a reply.
            pingAndWaitForReply(msg, TIMEOUT);

            // Introduce a short pause if desired.
            pause(SLEEP_TIME);
        }
        catch (JMSException e)
        {
            _publish = false;
            _logger.debug("There was a JMSException: " + e.getMessage(), e);
        }
        catch (InterruptedException e)
        {
            _publish = false;
            _logger.debug("There was an interruption: " + e.getMessage(), e);
        }
    }
}
