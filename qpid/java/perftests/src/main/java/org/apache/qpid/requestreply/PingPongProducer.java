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

import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;
import org.apache.qpid.ping.AbstractPingProducer;
import org.apache.qpid.ping.Throttle;
import org.apache.qpid.topic.Config;

/**
 * PingPongProducer is a client that sends pings to a queue and waits for pongs to be bounced back by a bounce back
 * client (see {@link PingPongBouncer} for the bounce back client). It is designed to be run from the command line
 * as a stand alone test tool, but it may also be fairly easily instantiated by other code by supplying a session,
 * message producer and message consumer to run the ping-pong cycle on.
 * <p/>
 * <p/>The pings are sent with a reply-to field set to a single temporary queue, which is the same for all pings.
 * This means that this class has to do some work to correlate pings with pongs; it expectes the original message
 * id in the ping to be bounced back in the correlation id. If a new temporary queue per ping were used, then
 * this correlation would not need to be done.
 * <p/>
 * <p/>This implements the Runnable interface with a run method that implements an infinite ping loop. The ping loop
 * does all its work through helper methods, so that code wishing to run a ping-pong cycle is not forced to do so
 * by starting a new thread. The command line invocation does take advantage of this ping loop. A shutdown hook is
 * also registered to terminate the ping-pong loop cleanly.
 * <p/>
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a ping and wait for response cycle.
 * <tr><td> Provide command line invocation to loop the ping cycle on a configurable broker url.
 * </table>
 *
 * @todo Make temp queue per ping a command line option.
 * @todo Make the queue name a command line option.
 */
public class PingPongProducer extends AbstractPingProducer implements Runnable, MessageListener, ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(PingPongProducer.class);

    /**
     * Used to set up a default message size.
     */
    protected static final int DEFAULT_MESSAGE_SIZE = 0;

    /**
     * This is set and used when the test is for multiple-destinations
     */
    protected static final int DEFAULT_DESTINATION_COUNT = 0;

    protected static final int DEFAULT_RATE = 0;

    /**
     * Used to define how long to wait between pings.
     */
    protected static final long SLEEP_TIME = 250;

    /**
     * Used to define how long to wait before assuming that a ping has timed out.
     */
    protected static final long TIMEOUT = 9000;

    /**
     * Holds the name of the destination to send pings on.
     */
    protected static final String PING_DESTINATION_NAME = "ping";

    /**
     * The batch size.
     */
    protected static final int DEFAULT_BATCH_SIZE = 100;

    protected static final int PREFETCH = 100;
    protected static final boolean NO_LOCAL = true;
    protected static final boolean EXCLUSIVE = false;

    /**
     * The number of priming loops to run.
     */
    protected static final int PRIMING_LOOPS = 3;

    /**
     * A source for providing sequential unique correlation ids.
     */
    private static AtomicLong idGenerator = new AtomicLong(0L);

    /**
     * Holds a map from message ids to latches on which threads wait for replies.
     */
    private static Map<String, CountDownLatch> trafficLights = new HashMap<String, CountDownLatch>();

    /**
     * Destination where the responses messages will arrive
     */
    private Destination _replyDestination;

    /**
     * Destination where the producer will be sending message to
     */
    private Destination _pingDestination;

    /**
     * Determines whether this producer sends persistent messages from the run method.
     */
    protected boolean _persistent;

    /**
     * Holds the message size to send, from the run method.
     */
    protected int _messageSize;

    /**
     * Used to indicate that the ping loop should print out whenever it pings.
     */
    protected boolean _verbose = false;

    protected Session _consumerSession;

    /**
     * Used to restrict the sending rate to a specified limit.
     */
    private Throttle rateLimiter = null;

    /**
     * The throttler can only reliably restrict to a few hundred cycles per second, so a throttling batch size is used
     * to group sends together into batches large enough that the throttler runs slower than that.
     */
    int _throttleBatchSize;

    private MessageListener _messageListener = null;

    private PingPongProducer(String brokerDetails, String username, String password, String virtualpath, boolean transacted,
                             boolean persistent, int messageSize, boolean verbose, boolean afterCommit, boolean beforeCommit,
                             boolean afterSend, boolean beforeSend, boolean failOnce, int batchSize, int rate)
            throws Exception
    {
        // Create a connection to the broker.
        InetAddress address = InetAddress.getLocalHost();
        String clientID = address.getHostName() + System.currentTimeMillis();

        setConnection(new AMQConnection(brokerDetails, username, password, clientID, virtualpath));

        // Create transactional or non-transactional sessions, based on the command line arguments.
        setProducerSession((Session) getConnection().createSession(transacted, Session.AUTO_ACKNOWLEDGE));
        _consumerSession = (Session) getConnection().createSession(transacted, Session.AUTO_ACKNOWLEDGE);

        _persistent = persistent;
        _messageSize = messageSize;
        _verbose = verbose;

        // Set failover interrupts
        _failAfterCommit = afterCommit;
        _failBeforeCommit = beforeCommit;
        _failAfterSend = afterSend;
        _failBeforeSend = beforeSend;
        _failOnce = failOnce;
        _txBatchSize = batchSize;

        // Calculate a throttling batch size and rate such that the throttle runs slower than 100 cycles per second
        // and batched sends within each cycle multiply up to give the desired rate.
        //
        // total rate = throttle rate * batch size.
        // 1 < throttle rate < 100
        // 1 < total rate < 20000
        if (rate > DEFAULT_RATE)
        {
            // Log base 10 over 2 is used here to get a feel for what power of 100 the total rate is.
            // As the total rate goes up the powers of 100 the batch size goes up by powers of 100 to keep the
            // throttle rate back into the range 1 to 100.
            int x = (int) (Math.log10(rate) / 2);
            _throttleBatchSize = (int) Math.pow(100, x);
            int throttleRate = rate / _throttleBatchSize;

            _logger.debug("rate = " + rate);
            _logger.debug("x = " + x);
            _logger.debug("_throttleBatchSize = " + _throttleBatchSize);
            _logger.debug("throttleRate = " + throttleRate);

            rateLimiter = new Throttle();
            rateLimiter.setRate(throttleRate);
        }
    }

    /**
     * Creates a ping pong producer with the specified connection details and type.
     *
     * @param brokerDetails
     * @param username
     * @param password
     * @param virtualpath
     * @param transacted
     * @throws Exception All allowed to fall through. This is only test code...
     */
    public PingPongProducer(String brokerDetails, String username, String password, String virtualpath,
                            String destinationName, String selector, boolean transacted, boolean persistent,
                            int messageSize, boolean verbose, boolean afterCommit, boolean beforeCommit,
                            boolean afterSend, boolean beforeSend, boolean failOnce, int batchSize,
                            int noOfDestinations, int rate, boolean pubsub) throws Exception
    {
        this(brokerDetails, username, password, virtualpath, transacted, persistent, messageSize, verbose, afterCommit,
             beforeCommit, afterSend, beforeSend, failOnce, batchSize, rate);

        _destinationCount = noOfDestinations;
        setPubSub(pubsub);

        if (noOfDestinations == DEFAULT_DESTINATION_COUNT)
        {
            if (destinationName != null)
            {
                createPingDestination(destinationName);
                // Create producer and the consumer
                createProducer();
                createConsumer(selector);
            }
            else
            {
                _logger.error("Destination is not specified");
                throw new IllegalArgumentException("Destination is not specified");
            }
        }
    }

    private void createPingDestination(String name)
    {
        if (isPubSub())
        {
            _pingDestination = new AMQTopic(name);
        }
        else
        {
            _pingDestination = new AMQQueue(name);
        }
    }

    /**
     * Creates the producer to send the pings on.  If the tests are with nultiple-destinations, then producer
     * is created with null destination, so that any destination can be specified while sending
     *
     * @throws JMSException
     */
    public void createProducer() throws JMSException
    {
        if (getDestinationsCount() > DEFAULT_DESTINATION_COUNT)
        {
            // create producer with initial destination as null for test with multiple-destinations
            // In this case, a different destination will be used while sending the message
            _producer = (MessageProducer) getProducerSession().createProducer(null);
        }
        else
        {
            // Create a producer with known destination to send the pings on.
            _producer = (MessageProducer) getProducerSession().createProducer(_pingDestination);

        }

        _producer.setDisableMessageTimestamp(true);
        _producer.setDeliveryMode(_persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
    }

    /**
     * Creates the temporary destination to listen to the responses
     *
     * @param selector
     * @throws JMSException
     */
    public void createConsumer(String selector) throws JMSException
    {
        // Create a temporary destination to get the pongs on.
        if (isPubSub())
        {
            _replyDestination = _consumerSession.createTemporaryTopic();
        }
        else
        {
            _replyDestination = _consumerSession.createTemporaryQueue();
        }

        // Create a message consumer to get the replies with and register this to be called back by it.
        MessageConsumer consumer = _consumerSession.createConsumer(_replyDestination, PREFETCH, NO_LOCAL, EXCLUSIVE, selector);
        consumer.setMessageListener(this);
    }

    /**
     * Creates consumer instances for each destination. This is used when test is being done with multiple destinations.
     *
     * @param selector
     * @throws JMSException
     */
    public void createConsumers(String selector) throws JMSException
    {
        for (int i = 0; i < getDestinationsCount(); i++)
        {
            MessageConsumer consumer =
                    getConsumerSession().createConsumer(getDestination(i), PREFETCH, false, EXCLUSIVE, selector);
            consumer.setMessageListener(this);
        }
    }


    public Session getConsumerSession()
    {
        return _consumerSession;
    }

    public Destination getPingDestination()
    {
        return _pingDestination;
    }

    protected void setPingDestination(Destination destination)
    {
        _pingDestination = destination;
    }

    /**
     * Starts a ping-pong loop running from the command line. The bounce back client {@link org.apache.qpid.requestreply.PingPongBouncer} also needs
     * to be started to bounce the pings back again.
     * <p/>
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
        Config config = new Config();
        config.setOptions(args);
        if (args.length == 0)
        {
            _logger.info("Running test with default values...");
            //usage();
            //System.exit(0);
        }

        String brokerDetails = config.getHost() + ":" + config.getPort();
        String virtualpath = "/test";
        String selector = config.getSelector();
        boolean verbose = true;
        boolean transacted = config.isTransacted();
        boolean persistent = config.usePersistentMessages();
        int messageSize = (config.getPayload() != 0) ? config.getPayload() : DEFAULT_MESSAGE_SIZE;
        //int messageCount = config.getMessages();
        int destCount = (config.getDestinationsCount() != 0) ? config.getDestinationsCount() : DEFAULT_DESTINATION_COUNT;
        int batchSize = (config.getBatchSize() != 0) ? config.getBatchSize() : DEFAULT_BATCH_SIZE;
        int rate = (config.getRate() != 0) ? config.getRate() : DEFAULT_RATE;
        boolean pubsub = config.isPubSub();

        String destName = config.getDestination();
        if (destName == null)
        {
            destName = PING_DESTINATION_NAME;
        }

        boolean afterCommit = false;
        boolean beforeCommit = false;
        boolean afterSend = false;
        boolean beforeSend = false;
        boolean failOnce = false;

        for (String arg : args)
        {
            if (arg.startsWith("failover:"))
            {
                //failover:<before|after>:<send:commit> | failover:once
                String[] parts = arg.split(":");
                if (parts.length == 3)
                {
                    if (parts[2].equals("commit"))
                    {
                        afterCommit = parts[1].equals("after");
                        beforeCommit = parts[1].equals("before");
                    }

                    if (parts[2].equals("send"))
                    {
                        afterSend = parts[1].equals("after");
                        beforeSend = parts[1].equals("before");
                    }

                    if (parts[1].equals("once"))
                    {
                        failOnce = true;
                    }
                }
                else
                {
                    System.out.println("Unrecognized failover request:" + arg);
                }
            }
        }

        // Create a ping producer to handle the request/wait/reply cycle.
        PingPongProducer pingProducer = new PingPongProducer(brokerDetails, "guest", "guest", virtualpath,
                                                             destName, selector, transacted, persistent, messageSize, verbose,
                                                             afterCommit, beforeCommit, afterSend, beforeSend, failOnce, batchSize,
                                                             destCount, rate, pubsub);

        pingProducer.getConnection().start();

        // Run a few priming pings to remove warm up time from test results.
        //pingProducer.prime(PRIMING_LOOPS);
        // Create a shutdown hook to terminate the ping-pong producer.
        Runtime.getRuntime().addShutdownHook(pingProducer.getShutdownHook());

        // Ensure that the ping pong producer is registered to listen for exceptions on the connection too.
        pingProducer.getConnection().setExceptionListener(pingProducer);

        // Create the ping loop thread and run it until it is terminated by the shutdown hook or exception.
        Thread pingThread = new Thread(pingProducer);
        pingThread.run();
        pingThread.join();
    }

    private static void usage()
    {
        System.err.println("Usage: TestPingPublisher \n" + "-host : broker host" + "-port : broker port" +
                           "-destinationname : queue/topic name\n" +
                           "-transacted : (true/false). Default is false\n" +
                           "-persistent : (true/false). Default is false\n" +
                           "-pubsub     : (true/false). Default is false\n" +
                           "-selector   : selector string\n" +
                           "-payload    : paylaod size. Default is 0\n" +
                           //"-messages   : no of messages to be sent (if 0, the ping loop will run indefinitely)\n" +
                           "-destinationscount : no of destinations for multi-destinations test\n" +
                           "-batchsize  : batch size\n" +
                           "-rate : thruput rate\n");
    }

    /**
     * Primes the test loop by sending a few messages, then introduces a short wait. This allows the bounce back client
     * on the other end a chance to configure its reply producer on the reply to destination. It is also worth calling
     * this a few times, in order to prime the JVMs JIT compilation.
     *
     * @param x The number of priming loops to run.
     * @throws JMSException All underlying exceptions are allowed to fall through.
     */
    public void prime(int x) throws JMSException
    {
        for (int i = 0; i < x; i++)
        {
            // Create and send a small message.
            Message first = getTestMessage(_replyDestination, 0, false);
            sendMessage(first);

            commitTx();

            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException ignore)
            {

            }
        }


    }

    /**
     * Stores the received message in the replies map, then resets the boolean latch that a thread waiting for a
     * correlating reply may be waiting on. This is only done if the reply has a correlation id that is expected
     * in the replies map.
     *
     * @param message The received message.
     */
    public void onMessage(Message message)
    {

        try
        {

            // Store the reply, if it has a correlation id that is expected.
            String correlationID = message.getJMSCorrelationID();

            if (_verbose)
            {
                _logger.info(timestampFormatter.format(new Date()) + ": Got reply with correlation id, " + correlationID);
                //_logger.debug("Received from : " + message.getJMSDestination());
            }

            // Turn the traffic light to green.
            CountDownLatch trafficLight = trafficLights.get(correlationID);

            if (trafficLight != null)
            {
                if (_messageListener != null)
                {
                    synchronized (trafficLight)
                    {
                        _messageListener.onMessage(message);
                        trafficLight.countDown();
                    }
                }
                else
                {
                    trafficLight.countDown();
                }

                _logger.trace("Reply was expected, decrementing the latch for the id.");

                long remainingCount = trafficLight.getCount();

                if ((remainingCount % _txBatchSize) == 0)
                {
                    commitTx(getConsumerSession());
                }

            }
            else
            {
                _logger.trace("There was no thread waiting for reply: " + correlationID);
            }

            if (_verbose)
            {
                Long timestamp = message.getLongProperty("timestamp");

                if (timestamp != null)
                {
                    long diff = System.currentTimeMillis() - timestamp;
                    _logger.trace("Time for round trip: " + diff);
                }
            }
        }
        catch (JMSException e)
        {
            _logger.warn("There was a JMSException: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the specified number of ping message and then waits for all correlating replies. If the wait times out
     * before a reply arrives, then a null reply is returned from this method.
     *
     * @param message  The message to send.
     * @param numPings The number of ping messages to send.
     * @param timeout  The timeout in milliseconds.
     * @return The number of replies received. This may be less than the number sent if the timeout terminated the
     *         wait for all prematurely.
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    public int pingAndWaitForReply(Message message, int numPings, long timeout) throws JMSException, InterruptedException
    {
        String messageCorrelationId = null;

        try
        {
            // Put a unique correlation id on the message before sending it.
            messageCorrelationId = Long.toString(getNewID());

            pingNoWaitForReply(message, numPings, messageCorrelationId);

            CountDownLatch trafficLight = trafficLights.get(messageCorrelationId);
            // Block the current thread until a reply to the message is received, or it times out.
            trafficLight.await(timeout, TimeUnit.MILLISECONDS);

            // Work out how many replies were receieved.
            int numReplies = numPings - (int) trafficLight.getCount();

            if ((numReplies < numPings) && _verbose)
            {
                _logger.info("Timed out (" + timeout + " ms) before all replies received on id, " + messageCorrelationId);
            }
            else if (_verbose)
            {
                _logger.info("Got all replies on id, " + messageCorrelationId);
            }

            commitTx(getConsumerSession());

            return numReplies;
        }
        finally
        {
            removeLock(messageCorrelationId);
        }
    }

    public long getNewID()
    {
        return idGenerator.incrementAndGet();
    }

    public CountDownLatch removeLock(String correlationID)
    {
        return trafficLights.remove(correlationID);
    }


    /*
    * Sends the specified ping message but does not wait for a correlating reply.
    *
    * @param message  The message to send.
    * @param numPings The number of pings to send.
    * @return The reply, or null if no reply arrives before the timeout.
    * @throws JMSException All underlying JMSExceptions are allowed to fall through.
    */
    public void pingNoWaitForReply(Message message, int numPings, String messageCorrelationId) throws JMSException, InterruptedException
    {
        // Create a count down latch to count the number of replies with. This is created before the message is sent
        // so that the message is not received before the count down is created.
        CountDownLatch trafficLight = new CountDownLatch(numPings);
        trafficLights.put(messageCorrelationId, trafficLight);

        message.setJMSCorrelationID(messageCorrelationId);

        // Set up a committed flag to detect uncommitted message at the end of the send loop. This may occurr if the
        // transaction batch size is not a factor of the number of pings. In which case an extra commit at the end is
        // needed.
        boolean committed = false;

        // Send all of the ping messages.
        for (int i = 0; i < numPings; i++)
        {
            // Reset the committed flag to indicate that there are uncommitted message.
            committed = false;

            // Re-timestamp the message.
            message.setLongProperty("timestamp", System.currentTimeMillis());

            // Check if the test is with multiple-destinations, in which case round robin the destinations
            // as the messages are sent.
            if (getDestinationsCount() > DEFAULT_DESTINATION_COUNT)
            {
                sendMessage(getDestination(i % getDestinationsCount()), message);
            }
            else
            {
                sendMessage(message);
            }

            // Apply message rate throttling if a rate limit has been set up and the throttling batch limit has been
            // reached. See the comment on the throttle batch size for information about the use of batches here.
            if ((rateLimiter != null) && ((i % _throttleBatchSize) == 0))
            {
                rateLimiter.throttle();
            }

            // Call commit every time the commit batch size is reached.
            if ((i % _txBatchSize) == 0)
            {
                commitTx();
                committed = true;
            }
        }

        // Call commit if the send loop finished before reaching a batch size boundary so there may still be uncommitted messages.
        if (!committed)
        {
            commitTx();
        }

        // Spew out per message timings only in verbose mode.
        if (_verbose)
        {
            _logger.info(timestampFormatter.format(new Date()) + ": Pinged at with correlation id, " + messageCorrelationId);
        }

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
            ObjectMessage msg = getTestMessage(_replyDestination, _messageSize, _persistent);
            msg.setLongProperty("timestamp", System.currentTimeMillis());

            // Send the message and wait for a reply.
            pingAndWaitForReply(msg, DEFAULT_BATCH_SIZE, TIMEOUT);

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

    public Destination getReplyDestination()
    {
        return _replyDestination;
    }

    protected void setReplyDestination(Destination destination)
    {
        _replyDestination = destination;
    }

    public void setMessageListener(MessageListener messageListener)
    {
        _messageListener = messageListener;
    }

    public CountDownLatch getEndLock(String correlationID)
    {
        return trafficLights.get(correlationID);
    }

    /*
    * When the test is being performed with multiple queues, then this method will be used, which has a loop to
    * pick up the next queue from the queues list and sends message to it.
    *
    * @param message
    * @param numPings
    * @throws JMSException
    */
    /*private void pingMultipleQueues(Message message, int numPings) throws JMSException
    {
        int queueIndex = 0;
        for (int i = 0; i < numPings; i++)
        {
            // Re-timestamp the message.
            message.setLongProperty("timestamp", System.currentTimeMillis());

            sendMessage(getDestination(queueIndex++), message);

            // reset the counter to get the first queue
            if (queueIndex == (getDestinationsCount() - 1))
            {
                queueIndex = 0;
            }
        }
    }*/

    /**
     * A connection listener that logs out any failover complete events. Could do more interesting things with this
     * at some point...
     */
    public static class FailoverNotifier implements ConnectionListener
    {
        public void bytesSent(long count)
        {
        }

        public void bytesReceived(long count)
        {
        }

        public boolean preFailover(boolean redirect)
        {
            return true; //Allow failover
        }

        public boolean preResubscribe()
        {
            return true; // Allow resubscription
        }

        public void failoverComplete()
        {
            _logger.info("App got failover complete callback.");
        }
    }
}
