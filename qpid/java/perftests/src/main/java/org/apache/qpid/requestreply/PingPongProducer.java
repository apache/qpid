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

import java.io.IOException;
import java.net.InetAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.*;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.TestMessageFactory;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;
import org.apache.qpid.topic.Config;
import org.apache.qpid.exchange.ExchangeDefaults;

import uk.co.thebadgerset.junit.extensions.BatchedThrottle;
import uk.co.thebadgerset.junit.extensions.Throttle;

/**
 * PingPongProducer is a client that sends pings to a queue and waits for pongs to be bounced back by a bounce back
 * client (see {@link PingPongBouncer} for the bounce back client).
 * <p/>
 * <p/>The pings are sent with a reply-to field set to a single temporary queue, which is the same for all pings.
 * This means that this class has to do some work to correlate pings with pongs; it expectes the original message
 * correlation id in the ping to be bounced back in the reply correlation id.
 * <p/>
 * <p/>This ping tool accepts a vast number of configuration options, all of which are passed in to the constructor.
 * It can ping topics or queues; ping multiple destinations; do persistent pings; send messages of any size; do pings
 * within transactions; control the number of pings to send in each transaction; limit its sending rate; and perform
 * failover testing.
 * <p/>
 * <p/>This implements the Runnable interface with a run method that implements an infinite ping loop. The ping loop
 * does all its work through helper methods, so that code wishing to run a ping-pong cycle is not forced to do so
 * by starting a new thread. The command line invocation does take advantage of this ping loop. A shutdown hook is
 * also registered to terminate the ping-pong loop cleanly.
 * <p/>
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a ping and wait for all responses cycle.
 * <tr><td> Provide command line invocation to loop the ping cycle on a configurable broker url.
 * </table>
 *
 * @todo The use of a ping rate {@link #DEFAULT_RATE} and waits between pings {@link #DEFAULT_SLEEP_TIME} are overlapping.
 * Use the rate and throttling only. Ideally, optionally pass the rate throttle into the ping method, throttle to
 * be created and configured by the test runner from the -f command line option and made available through
 * the timing controller on timing aware tests or by throttling rate of calling tests methods on non-timing aware
 * tests.
 * @todo Make acknowledege mode a test option.
 * @todo Make the message listener a static for all replies to be sent to? It won't be any more of a bottle neck than
 * having one per PingPongProducer, as will synchronize on message correlation id, allowing threads to process
 * messages concurrently for different ids. Needs to be static so that when using a chained message listener and
 * shared destinations between multiple PPPs, it gets notified about all replies, not just those that happen to
 * be picked up by the PPP that it is atteched to.
 * @todo Use read/write lock in the onmessage, not for reading writing but to make use of a shared and exlcusive lock
 * pair. Obtian read lock on all messages, before decrementing the message count. At the end of the on message
 * method add a block that obtains the write lock for the very last message, releases any waiting producer. Means
 * that the last message waits until all other messages have been handled before releasing producers but allows
 * messages to be processed concurrently, unlike the current synchronized block.
 * @todo Need to multiply up the number of expected messages for pubsub tests as each can be received by many consumers?
 */
public class PingPongProducer implements Runnable, MessageListener, ExceptionListener
{
    private static final Logger _logger = Logger.getLogger(PingPongProducer.class);

    /**
     * Holds the name of the property to get the test message size from.
     */
    public static final String MESSAGE_SIZE_PROPNAME = "messagesize";

    /**
     * Holds the name of the property to get the ping queue name from.
     */
    public static final String PING_QUEUE_NAME_PROPNAME = "destinationname";

    /**
     * Holds the name of the property to get the test delivery mode from.
     */
    public static final String PERSISTENT_MODE_PROPNAME = "persistent";

    /**
     * Holds the name of the property to get the test transactional mode from.
     */
    public static final String TRANSACTED_PROPNAME = "transacted";

    /**
     * Holds the name of the property to get the test broker url from.
     */
    public static final String BROKER_PROPNAME = "broker";

    /**
     * Holds the name of the property to get the test broker virtual path.
     */
    public static final String VIRTUAL_PATH_PROPNAME = "virtualPath";

    /**
     * Holds the name of the property to get the message rate from.
     */
    public static final String RATE_PROPNAME = "rate";

    public static final String VERBOSE_OUTPUT_PROPNAME = "verbose";

    /**
     * Holds the true or false depending on wether it is P2P test or PubSub
     */
    public static final String IS_PUBSUB_PROPNAME = "pubsub";

    public static final String FAIL_AFTER_COMMIT_PROPNAME = "FailAfterCommit";

    public static final String FAIL_BEFORE_COMMIT_PROPNAME = "FailBeforeCommit";

    public static final String FAIL_AFTER_SEND_PROPNAME = "FailAfterSend";

    public static final String FAIL_BEFORE_SEND_PROPNAME = "FailBeforeSend";

    public static final String FAIL_ONCE_PROPNAME = "FailOnce";

    public static final String USERNAME_PROPNAME = "username";

    public static final String PASSWORD_PROPNAME = "password";

    public static final String SELECTOR_PROPNAME = "selector";

    public static final String PING_DESTINATION_COUNT_PROPNAME = "destinationscount";

    /**
     * Holds the name of the property to get the waiting timeout for response messages.
     */
    public static final String TIMEOUT_PROPNAME = "timeout";

    public static final String COMMIT_BATCH_SIZE_PROPNAME = "CommitBatchSize";

    public static final String UNIQUE_PROPNAME = "uniqueDests";

    public static final String ACK_MODE_PROPNAME = "ackMode";

    public static final String PAUSE_AFTER_BATCH_PROPNAME = "pausetimeAfterEachBatch";
    
    /**
     * Used to set up a default message size.
     */
    public static final int DEFAULT_MESSAGE_SIZE = 0;

    /**
     * Holds the name of the default destination to send pings on.
     */
    public static final String DEFAULT_PING_DESTINATION_NAME = "ping";

    /**
     * Defines the default number of destinations to ping.
     */
    public static final int DEFAULT_DESTINATION_COUNT = 1;

    /**
     * Defines the default rate (in pings per second) to send pings at. 0 means as fast as possible, no restriction.
     */
    public static final int DEFAULT_RATE = 0;

    /**
     * Defines the default wait between pings.
     */
    public static final long DEFAULT_SLEEP_TIME = 250;

    /**
     * Default time to wait before assuming that a ping has timed out.
     */
    public static final long DEFAULT_TIMEOUT = 30000;

    /**
     * Defines the default number of pings to send in each transaction when running transactionally.
     */
    public static final int DEFAULT_TX_BATCH_SIZE = 100;

    /**
     * Defines the default prefetch size to use when consuming messages.
     */
    public static final int DEFAULT_PREFETCH = 100;

    /**
     * Defines the default value of the no local flag to use when consuming messages.
     */
    public static final boolean DEFAULT_NO_LOCAL = false;

    /**
     * Defines the default value of the exclusive flag to use when consuming messages.
     */
    public static final boolean DEFAULT_EXCLUSIVE = false;

    /**
     * Holds the message delivery mode to use for the test.
     */
    public static final boolean DEFAULT_PERSISTENT_MODE = false;

    /**
     * Holds the transactional mode to use for the test.
     */
    public static final boolean DEFAULT_TRANSACTED = false;

    /**
     * Holds the default broker url for the test.
     */
    public static final String DEFAULT_BROKER = "tcp://localhost:5672";

    /**
     * Holds the default virtual path for the test.
     */
    public static final String DEFAULT_VIRTUAL_PATH = "test";

    /**
     * Holds the pub/sub mode default, true means ping a topic, false means ping a queue.
     */
    public static final boolean DEFAULT_PUBSUB = false;

    /**
     * Holds the default broker log on username.
     */
    public static final String DEFAULT_USERNAME = "guest";

    /**
     * Holds the default broker log on password.
     */
    public static final String DEFAULT_PASSWORD = "guest";

    /**
     * Holds the default message selector.
     */
    public static final String DEFAULT_SELECTOR = null;

    /**
     * Holds the default failover after commit test flag.
     */
    public static final String DEFAULT_FAIL_AFTER_COMMIT = "false";

    /**
     * Holds the default failover before commit test flag.
     */
    public static final String DEFAULT_FAIL_BEFORE_COMMIT = "false";

    /**
     * Holds the default failover after send test flag.
     */
    public static final String DEFAULT_FAIL_AFTER_SEND = "false";

    /**
     * Holds the default failover before send test flag.
     */
    public static final String DEFAULT_FAIL_BEFORE_SEND = "false";

    /**
     * Holds the default failover only once flag, true means only do one failover, false means failover on every commit cycle.
     */
    public static final String DEFAULT_FAIL_ONCE = "true";

    /**
     * Holds the default verbose mode.
     */
    public static final boolean DEFAULT_VERBOSE = false;

    public static final boolean DEFAULT_UNIQUE = true;

    public static final int DEFAULT_ACK_MODE = Session.NO_ACKNOWLEDGE;

    /**
     * Holds the name of the property to store nanosecond timestamps in ping messages with.
     */
    public static final String MESSAGE_TIMESTAMP_PROPNAME = "timestamp";

    /**
     * A source for providing sequential unique correlation ids. These will be unique within the same JVM.
     */
    private static AtomicLong _correlationIdGenerator = new AtomicLong(0L);

    /**
     * Holds a map from message ids to latches on which threads wait for replies. This map is shared accross
     * multiple ping producers on the same JVM.
     */
    /*private static Map<String, CountDownLatch> trafficLights =
        Collections.synchronizedMap(new HashMap<String, CountDownLatch>());*/
    private static Map<String, PerCorrelationId> perCorrelationIds =
            Collections.synchronizedMap(new HashMap<String, PerCorrelationId>());

    /**
     * A convenient formatter to use when time stamping output.
     */
    protected static final DateFormat timestampFormatter = new SimpleDateFormat("hh:mm:ss:SS");

    /**
     * This id generator is used to generate ids to append to the queue name to ensure that queues can be unique when
     * creating multiple ping producers in the same JVM.
     */
    protected static AtomicInteger _queueJVMSequenceID = new AtomicInteger();

    /**
     * Destination where the response messages will arrive.
     */
    private Destination _replyDestination;

    /**
     * Determines whether this producer sends persistent messages.
     */
    protected boolean _persistent;

    private int _ackMode = Session.NO_ACKNOWLEDGE;

    /**
     * Determines what size of messages this producer sends.
     */
    protected int _messageSize;

    /**
     * Used to indicate that the ping loop should print out whenever it pings.
     */
    protected boolean _verbose = false;

    /**
     * Holds the session on which ping replies are received.
     */
    protected Session _consumerSession;

    /**
     * Used to restrict the sending rate to a specified limit.
     */
    private Throttle _rateLimiter = null;

    /**
     * Holds a message listener that this message listener chains all its messages to.
     */
    private ChainedMessageListener _chainedMessageListener = null;

    /**
     * Flag used to indicate if this is a point to point or pub/sub ping client.
     */
    protected boolean _isPubSub = false;

    /**
     * Flag used to indicate if the destinations should be unique client.
     */
    protected static boolean _isUnique = false;

    /**
     * This id generator is used to generates ids that are only unique within this pinger. Creating multiple pingers
     * on the same JVM using this id generator will allow them to ping on the same queues.
     */
    protected AtomicInteger _queueSharedId = new AtomicInteger();

    /**
     * Used to tell the ping loop when to terminate, it only runs while this is true.
     */
    protected boolean _publish = true;

    /**
     * Holds the connection to the broker.
     */
    private Connection _connection;

    /**
     * Holds the producer session, needed to create ping messages.
     */
    private Session _producerSession;

    /**
     * Holds the set of destiniations that this ping producer pings.
     */
    protected List<Destination> _pingDestinations = new ArrayList<Destination>();

    /**
     * Holds the message producer to send the pings through.
     */
    protected MessageProducer _producer;

    /**
     * Flag used to indicate that the user should be prompted to terminate a broker, to test failover before a commit.
     */
    protected boolean _failBeforeCommit = false;

    /**
     * Flag used to indicate that the user should be prompted to terminate a broker, to test failover after a commit.
     */
    protected boolean _failAfterCommit = false;

    /**
     * Flag used to indicate that the user should be prompted to terminate a broker, to test failover before a send.
     */
    protected boolean _failBeforeSend = false;

    /**
     * Flag used to indicate that the user should be prompted to terminate a broker, to test failover after a send.
     */
    protected boolean _failAfterSend = false;

    /**
     * Flag used to indicate that failover prompting should only be done on the first commit, not on every commit.
     */
    protected boolean _failOnce = true;

    /**
     * Holds the number of sends that should be performed in every transaction when using transactions.
     */
    protected int _txBatchSize = 1;

    private static long _pausetimeAfterEachBatch = 0;

    /**
     * Holds the number of consumers that will be attached to each topic.
     * Each pings will result in a reply from each of the attached clients
     */
    static int _consumersPerTopic = 1;

    /**
     * Creates a ping producer with the specified parameters, of which there are many. See their individual comments
     * for details. This constructor creates a connection to the broker and creates producer and consumer sessions on it,
     * to send and recieve its pings and replies on. The other options are kept, and control how this pinger behaves.
     *
     * @param brokerDetails    The URL of the broker to send pings to.
     * @param username         The username to log onto the broker with.
     * @param password         The password to log onto the broker with.
     * @param virtualpath      The virtual host name to use on the broker.
     * @param destinationName  The name (or root where multiple destinations are used) of the desitination to send
     *                         pings to.
     * @param selector         The selector to filter replies with.
     * @param transacted       Indicates whether or not pings are sent and received in transactions.
     * @param persistent       Indicates whether pings are sent using peristent delivery.
     * @param messageSize      Specifies the size of ping messages to send.
     * @param verbose          Indicates that information should be printed to the console on every ping.
     * @param afterCommit      Indicates that the user should be promted to terminate a broker after commits to test failover.
     * @param beforeCommit     Indicates that the user should be promted to terminate a broker before commits to test failover.
     * @param afterSend        Indicates that the user should be promted to terminate a broker after sends to test failover.
     * @param beforeSend       Indicates that the user should be promted to terminate a broker before sends to test failover.
     * @param failOnce         Indicates that the failover testing behaviour should only happen on the first commit, not all.
     * @param txBatchSize      Specifies the number of pings to send in each transaction.
     * @param noOfDestinations The number of destinations to ping. Must be 1 or more.
     * @param rate             Specified the number of pings per second to send. Setting this to 0 means send as fast as
     *                         possible, with no rate restriction.
     * @param pubsub           True to ping topics, false to ping queues.
     * @param unique           True to use unique destinations for each ping pong producer, false to share.
     * @throws Exception Any exceptions are allowed to fall through.
     */
    public PingPongProducer(String brokerDetails, String username, String password, String virtualpath,
                            String destinationName, String selector, boolean transacted, boolean persistent, int messageSize,
                            boolean verbose, boolean afterCommit, boolean beforeCommit, boolean afterSend,
                            boolean beforeSend, boolean failOnce, int txBatchSize, int noOfDestinations, int rate,
                            boolean pubsub, boolean unique, int ackMode, long pause) throws Exception
    {
        _logger.debug("public PingPongProducer(String brokerDetails = " + brokerDetails + ", String username = " + username
                      + ", String password = " + password + ", String virtualpath = " + virtualpath
                      + ", String destinationName = " + destinationName + ", String selector = " + selector
                      + ", boolean transacted = " + transacted + ", boolean persistent = " + persistent
                      + ", int messageSize = " + messageSize + ", boolean verbose = " + verbose + ", boolean afterCommit = "
                      + afterCommit + ", boolean beforeCommit = " + beforeCommit + ", boolean afterSend = " + afterSend
                      + ", boolean beforeSend = " + beforeSend + ", boolean failOnce = " + failOnce + ", int txBatchSize = "
                      + txBatchSize + ", int noOfDestinations = " + noOfDestinations + ", int rate = " + rate
                      + ", boolean pubsub = " + pubsub + ", boolean unique = " + unique
                      + ", ackMode = " + ackMode + "): called");

        // Keep all the relevant options.
        _persistent = persistent;
        _messageSize = messageSize;
        _verbose = verbose;
        _failAfterCommit = afterCommit;
        _failBeforeCommit = beforeCommit;
        _failAfterSend = afterSend;
        _failBeforeSend = beforeSend;
        _failOnce = failOnce;
        _txBatchSize = txBatchSize;
        _isPubSub = pubsub;
        _isUnique = unique;
        _pausetimeAfterEachBatch = pause;
        if (ackMode != 0)
        {
            _ackMode = ackMode;
        }
        
        // Check that one or more destinations were specified.
        if (noOfDestinations < 1)
        {
            throw new IllegalArgumentException("There must be at least one destination.");
        }

        // Create a connection to the broker.
        InetAddress address = InetAddress.getLocalHost();
        String clientID = address.getHostName() + System.currentTimeMillis();

        _connection = new AMQConnection(brokerDetails, username, password, clientID, virtualpath);

        // Create transactional or non-transactional sessions, based on the command line arguments.
        _producerSession = (Session) getConnection().createSession(transacted, _ackMode);
        _consumerSession = (Session) getConnection().createSession(transacted, _ackMode);

        // Set up a throttle to control the send rate, if a rate > 0 is specified.
        if (rate > 0)
        {
            _rateLimiter = new BatchedThrottle();
            _rateLimiter.setRate(rate);
        }

        // Create the temporary queue for replies.
        _replyDestination = _consumerSession.createTemporaryQueue();

        // Create the producer and the consumers for all reply destinations.
        createProducer();
        createPingDestinations(noOfDestinations, selector, destinationName, unique);
        createReplyConsumers(getReplyDestinations(), selector);
    }

    /**
     * Starts a ping-pong loop running from the command line. The bounce back client {@link PingPongBouncer} also needs
     * to be started to bounce the pings back again.
     *
     * @param args The command line arguments.
     * @throws Exception When something went wrong with the test
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
        String virtualpath = DEFAULT_VIRTUAL_PATH;
        String selector = (config.getSelector() == null) ? DEFAULT_SELECTOR : config.getSelector();
        boolean verbose = true;
        boolean transacted = config.isTransacted();
        boolean persistent = config.usePersistentMessages();
        int messageSize = (config.getPayload() != 0) ? config.getPayload() : DEFAULT_MESSAGE_SIZE;
        int messageCount = config.getMessages();
        int destCount = (config.getDestinationsCount() != 0) ? config.getDestinationsCount() : DEFAULT_DESTINATION_COUNT;
        int batchSize = (config.getBatchSize() != 0) ? config.getBatchSize() : DEFAULT_TX_BATCH_SIZE;
        int rate = (config.getRate() != 0) ? config.getRate() : DEFAULT_RATE;
        boolean pubsub = config.isPubSub();
        long timeout = (config.getTimeout() != 0) ? config.getTimeout() : DEFAULT_TIMEOUT;

        String destName = config.getDestination();
        if (destName == null)
        {
            destName = DEFAULT_PING_DESTINATION_NAME;
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
        PingPongProducer pingProducer =
                new PingPongProducer(brokerDetails, DEFAULT_USERNAME, DEFAULT_PASSWORD, virtualpath, destName, selector,
                                     transacted, persistent, messageSize, verbose, afterCommit, beforeCommit, afterSend,
                                     beforeSend, failOnce, batchSize, destCount, rate, pubsub, false, 0, 0);

        pingProducer.getConnection().start();

        // Create a shutdown hook to terminate the ping-pong producer.
        Runtime.getRuntime().addShutdownHook(pingProducer.getShutdownHook());

        // Ensure that the ping pong producer is registered to listen for exceptions on the connection too.
        pingProducer.getConnection().setExceptionListener(pingProducer);

        // If messageount is 0, then continue sending
        if (messageCount == 0)
        {
            // Create the ping loop thread and run it until it is terminated by the shutdown hook or exception.
            Thread pingThread = new Thread(pingProducer);
            pingThread.start();
            pingThread.join();
        }
        else
        {
            pingProducer.ping(messageCount, timeout);
        }
        pingProducer.close();
    }

    /**
     * Convenience method for a short pause.
     *
     * @param sleepTime The time in milliseconds to pause for.
     */
    public static void pause(long sleepTime)
    {
        if (sleepTime > 0)
        {
            try
            {
                Thread.sleep(sleepTime);
            }
            catch (InterruptedException ie)
            {
                //ignore
            }
        }
    }

    /**
     * Gets all the reply destinations (to listen for replies on). In this case this will just be the single reply
     * to destination of this pinger.
     *
     * @return The single reply to destination of this pinger, wrapped in a list.
     */
    public List<Destination> getReplyDestinations()
    {
        _logger.debug("public List<Destination> getReplyDestinations(): called");

        List<Destination> replyDestinations = new ArrayList<Destination>();
        replyDestinations.add(_replyDestination);

        return replyDestinations;
    }

    /**
     * Creates the producer to send the pings on. This is created without a default destination. Its persistent delivery
     * flag is set accoring the ping producer creation options.
     *
     * @throws JMSException Any JMSExceptions are allowed to fall through.
     */
    public void createProducer() throws JMSException
    {
        _logger.debug("public void createProducer(): called");

        _producer = (MessageProducer) _producerSession.createProducer(null);
        //_producer.setDisableMessageTimestamp(true);
        _producer.setDeliveryMode(_persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
    }

    /**
     * Creates consumers for the specified number of destinations. The destinations themselves are also created by
     * this method.
     *
     * @param noOfDestinations The number of destinations to create consumers for.
     * @param selector         The message selector to filter the consumers with.
     * @param rootName         The root of the name, or actual name if only one is being created.
     * @param unique           <tt>true</tt> to make the destinations unique to this pinger, <tt>false</tt> to share
     *                         the numbering with all pingers on the same JVM.
     * @throws JMSException Any JMSExceptions are allowed to fall through.
     */
    public void createPingDestinations(int noOfDestinations, String selector, String rootName, boolean unique)
            throws JMSException
    {
        _logger.debug("public void createPingDestinations(int noOfDestinations = " + noOfDestinations
                      + ", String selector = " + selector + ", String rootName = " + rootName + ", boolean unique = "
                      + unique + "): called");

        // Create the desired number of ping destinations and consumers for them.
        for (int i = 0; i < noOfDestinations; i++)
        {
            AMQDestination destination;

            String id;

            // Generate an id, unique within this pinger or to the whole JVM depending on the unique flag.
            if (unique)
            {
                _logger.debug("Creating unique destinations.");
                id = "_" + _queueJVMSequenceID.incrementAndGet() + "_" + _connection.getClientID();
            }
            else
            {
                _logger.debug("Creating shared destinations.");
                id = "_" + _queueSharedId.incrementAndGet();
            }

            // Check if this is a pub/sub pinger, in which case create topics.
            if (_isPubSub)
            {
                destination = new AMQTopic(ExchangeDefaults.TOPIC_EXCHANGE_NAME, rootName + id);
                _logger.debug("Created topic " + destination);
            }
            // Otherwise this is a p2p pinger, in which case create queues.
            else
            {
                destination = new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, rootName + id);
                _logger.debug("Created queue " + destination);
            }

            // Keep the destination.
            _pingDestinations.add(destination);
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
        _logger.debug("public void onMessage(Message message): called");

        try
        {
            // Extract the messages correlation id.
            String correlationID = message.getJMSCorrelationID();
            _logger.debug("correlationID = " + correlationID);

            // Countdown on the traffic light if there is one for the matching correlation id.
            PerCorrelationId perCorrelationId = perCorrelationIds.get(correlationID);

            if (perCorrelationId != null)
            {
                CountDownLatch trafficLight = perCorrelationId.trafficLight;

                // Restart the timeout timer on every message.
                perCorrelationId.timeOutStart = System.nanoTime();

                _logger.debug("Reply was expected, decrementing the latch for the id, " + correlationID);

                // Decrement the countdown latch. Before this point, it is possible that two threads might enter this
                // method simultanesouly with the same correlation id. Decrementing the latch in a synchronized block
                // ensures that each thread will get a unique value for the remaining messages.
                long trueCount = -1;
                long remainingCount = -1;

                synchronized (trafficLight)
                {
                    trafficLight.countDown();

                    trueCount = trafficLight.getCount();
                    remainingCount = trueCount - 1;

                    _logger.debug("remainingCount = " + remainingCount);
                    _logger.debug("trueCount = " + trueCount);

                    // Commit on transaction batch size boundaries. At this point in time the waiting producer remains
                    // blocked, even on the last message.
                    if ((remainingCount % _txBatchSize) == 0)
                    {
                        commitTx(_consumerSession);
                        if (!_consumerSession.getTransacted() &&
                            _consumerSession.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
                        {
                            ((AMQSession)_consumerSession).acknowledge();
                        }
                    }
                    
                    // Forward the message and remaining count to any interested chained message listener.
                    if (_chainedMessageListener != null)
                    {
                        _chainedMessageListener.onMessage(message, (int) remainingCount);
                    }

                    // Check if this is the last message, in which case release any waiting producers. This is done
                    // after the transaction has been committed and any listeners notified.
                    if (trueCount == 1)
                    {
                        trafficLight.countDown();
                    }
                }
            }
            else
            {
                _logger.warn("Got unexpected message with correlationId: " + correlationID);
            }

            // Print out ping times for every message in verbose mode only.
            if (_verbose)
            {
                Long timestamp = message.getLongProperty(MESSAGE_TIMESTAMP_PROPNAME);

                if (timestamp != null)
                {
                    long diff = System.nanoTime() - timestamp;
                    _logger.trace("Time for round trip (nanos): " + diff);
                }
            }
        }
        catch (JMSException e)
        {
            _logger.warn("There was a JMSException: " + e.getMessage(), e);
        }

        _logger.debug("public void onMessage(Message message): ending");
    }

    /**
     * Sends the specified number of ping message and then waits for all correlating replies. If the wait times out
     * before a reply arrives, then a null reply is returned from this method. This method generates a new unqiue
     * correlation id for the messages.
     *
     * @param message  The message to send.
     * @param numPings The number of ping messages to send.
     * @param timeout  The timeout in milliseconds.
     * @return The number of replies received. This may be less than the number sent if the timeout terminated the
     *         wait for all prematurely.
     * @throws JMSException         All underlying JMSExceptions are allowed to fall through.
     * @throws InterruptedException When interrupted by a timeout.
     */
    public int pingAndWaitForReply(Message message, int numPings, long timeout) throws JMSException, InterruptedException
    {
        _logger.debug("public int pingAndWaitForReply(Message message, int numPings = " + numPings + ", long timeout = "
                      + timeout + "): called");

        // Create a unique correlation id to put on the messages before sending them.
        String messageCorrelationId = Long.toString(_correlationIdGenerator.incrementAndGet());

        return pingAndWaitForReply(message, numPings, timeout, messageCorrelationId);
    }

    public int pingAndWaitForReply(int numPings, long timeout, String messageCorrelationId)
            throws JMSException, InterruptedException
    {
        return pingAndWaitForReply(null, numPings, timeout, messageCorrelationId);   
    }

    /**
     * Sends the specified number of ping message and then waits for all correlating replies. If the wait times out
     * before a reply arrives, then a null reply is returned from this method. This method allows the caller to specify
     * the correlation id.
     *
     * @param message              The message to send.
     * @param numPings             The number of ping messages to send.
     * @param timeout              The timeout in milliseconds.
     * @param messageCorrelationId The message correlation id.
     * @return The number of replies received. This may be less than the number sent if the timeout terminated the
     *         wait for all prematurely.
     * @throws JMSException         All underlying JMSExceptions are allowed to fall through.
     * @throws InterruptedException When interrupted by a timeout
     */
    public int pingAndWaitForReply(Message message, int numPings, long timeout, String messageCorrelationId)
            throws JMSException, InterruptedException
    {
        _logger.debug("public int pingAndWaitForReply(Message message, int numPings = " + numPings + ", long timeout = "
                      + timeout + ", String messageCorrelationId = " + messageCorrelationId + "): called");

        try
        {
            // Create a count down latch to count the number of replies with. This is created before the messages are
            // sent so that the replies cannot be received before the count down is created.
            // One is added to this, so that the last reply becomes a special case. The special case is that the
            // chained message listener must be called before this sender can be unblocked, but that decrementing the
            // countdown needs to be done before the chained listener can be called.
            PerCorrelationId perCorrelationId = new PerCorrelationId();

            perCorrelationId.trafficLight = new CountDownLatch(getExpectedNumPings(numPings) + 1);
            perCorrelationIds.put(messageCorrelationId, perCorrelationId);

            // Set up the current time as the start time for pinging on the correlation id. This is used to determine
            // timeouts.
            perCorrelationId.timeOutStart = System.nanoTime();

            // Send the specifed number of messages.
            pingNoWaitForReply(message, numPings, messageCorrelationId);

            boolean timedOut = false;
            boolean allMessagesReceived = false;
            int numReplies = 0;

            do
            {
                // Block the current thread until replies to all the messages are received, or it times out.
                perCorrelationId.trafficLight.await(timeout, TimeUnit.MILLISECONDS);

                // Work out how many replies were receieved.
                numReplies = getExpectedNumPings(numPings) - (int) perCorrelationId.trafficLight.getCount();

                allMessagesReceived = numReplies == getExpectedNumPings(numPings);

                _logger.debug("numReplies = " + numReplies);
                _logger.debug("allMessagesReceived = " + allMessagesReceived);

                // Recheck the timeout condition.
                long now = System.nanoTime();
                long lastMessageReceievedAt = perCorrelationId.timeOutStart;
                timedOut = (now - lastMessageReceievedAt) > (timeout * 1000000);

                _logger.debug("now = " + now);
                _logger.debug("lastMessageReceievedAt = " + lastMessageReceievedAt);
            }
            while (!timedOut && !allMessagesReceived);

            if ((numReplies < getExpectedNumPings(numPings)) && _verbose)
            {
                _logger.info("Timed out (" + timeout + " ms) before all replies received on id, " + messageCorrelationId);
            }
            else if (_verbose)
            {
                _logger.info("Got all replies on id, " + messageCorrelationId);
            }

            commitTx(_consumerSession);

            _logger.debug("public int pingAndWaitForReply(Message message, int numPings, long timeout): ending");

            return numReplies;
        }
        // Ensure that the message countdown latch is always removed from the reply map. The reply map is long lived,
        // so will be a memory leak if this is not done.
        finally
        {
            perCorrelationIds.remove(messageCorrelationId);
        }
    }

    /**
     * Sends the specified number of ping messages and does not wait for correlating replies.
     *
     * @param message              The message to send.
     * @param numPings             The number of pings to send.
     * @param messageCorrelationId A correlation id to place on all messages sent.
     * @throws JMSException All underlying JMSExceptions are allowed to fall through.
     */
    public void pingNoWaitForReply(Message message, int numPings, String messageCorrelationId) throws JMSException
    {
        _logger.debug("public void pingNoWaitForReply(Message message, int numPings = " + numPings
                      + ", String messageCorrelationId = " + messageCorrelationId + "): called");

        if (message == null)
        {
            message = getTestMessage(getReplyDestinations().get(0), _messageSize, _persistent);
        }

        message.setJMSCorrelationID(messageCorrelationId);

        // Set up a committed flag to detect uncommitted messages at the end of the send loop. This may occurr if the
        // transaction batch size is not a factor of the number of pings. In which case an extra commit at the end is
        // needed.
        boolean committed = false;

        // Send all of the ping messages.
        for (int i = 0; i < numPings; i++)
        {
            // Reset the committed flag to indicate that there are uncommitted messages.
            committed = false;

            // Re-timestamp the message.
            message.setLongProperty(MESSAGE_TIMESTAMP_PROPNAME, System.nanoTime());

            // Round robin the destinations as the messages are sent.
            //return _destinationCount;
            sendMessage(_pingDestinations.get(i % _pingDestinations.size()), message);

            // Apply message rate throttling if a rate limit has been set up.
            if (_rateLimiter != null)
            {
                _rateLimiter.throttle();
            }

            // Call commit every time the commit batch size is reached.
            if ((i % _txBatchSize) == 0)
            {
                commitTx(_producerSession);
                committed = true;
                /* This pause is required for some cases. eg in load testing when sessions are non-transacted the
                   Mina IO layer can't clear the cache in time. So this pause gives enough time for mina to clear
                   the cache (without this mina throws OutOfMemoryError). pause() will check if time is != 0
                */
                pause(_pausetimeAfterEachBatch);
            }

            // Spew out per message timings on every message sonly in verbose mode.
            if (_verbose)
            {
                _logger.info(timestampFormatter.format(new Date()) + ": Pinged at with correlation id, "
                             + messageCorrelationId);
            }
        }

        // Call commit if the send loop finished before reaching a batch size boundary so there may still be uncommitted messages.
        if (!committed)
        {
            commitTx(_producerSession);
        }
    }

    /**
     * The ping implementation. This sends out pings waits for replies and inserts short pauses in between each.
     */
    public void ping(int pingCount, long timeout)
    {
        try
        {
            // Generate a sample message and time stamp it.
            ObjectMessage msg = getTestMessage(_replyDestination, _messageSize, _persistent);
            msg.setLongProperty(MESSAGE_TIMESTAMP_PROPNAME, System.nanoTime());

            // Send the message and wait for a reply.
            pingAndWaitForReply(msg, pingCount, timeout);

            // Introduce a short pause if desired.
            pause(DEFAULT_SLEEP_TIME);
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

    public void ping()
    {
        ping(DEFAULT_TX_BATCH_SIZE, DEFAULT_TIMEOUT);
    }

    public Destination getReplyDestination()
    {
        return getReplyDestinations().get(0);
    }

    /**
     * Sets a chained message listener. The message listener on this pinger, chains all its messages to the one set
     * here.
     *
     * @param messageListener The chained message listener.
     */
    public void setChainedMessageListener(ChainedMessageListener messageListener)
    {
        _chainedMessageListener = messageListener;
    }

    /**
     * Removes any chained message listeners from this pinger.
     */
    public void removeChainedMessageListener()
    {
        _chainedMessageListener = null;
    }

    /**
     * Generates a test message of the specified size, with the specified reply-to destination and persistence flag.
     *
     * @param replyQueue  The reply-to destination for the message.
     * @param messageSize The desired size of the message in bytes.
     * @param persistent  <tt>true</tt> if the message should use persistent delivery, <tt>false</tt> otherwise.
     * @return A freshly generated test message.
     * @throws javax.jms.JMSException All underlying JMSException are allowed to fall through.
     */
    public ObjectMessage getTestMessage(Destination replyQueue, int messageSize, boolean persistent) throws JMSException
    {
        ObjectMessage msg = TestMessageFactory.newObjectMessage(_producerSession, replyQueue, messageSize, persistent);

        // Timestamp the message in nanoseconds.
        msg.setLongProperty(MESSAGE_TIMESTAMP_PROPNAME, System.nanoTime());

        return msg;
    }

    /**
     * Stops the ping loop by clearing the publish flag. The current loop will complete before it notices that this
     * flag has been cleared.
     */
    public void stop()
    {
        _publish = false;
    }

    /**
     * Implements a ping loop that repeatedly pings until the publish flag becomes false.
     */
    public void run()
    {
        // Keep running until the publish flag is cleared.
        while (_publish)
        {
            ping();
        }
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
     * Gets a shutdown hook that will cleanly shut this down when it is running the ping loop. This can be registered
     * with the runtime system as a shutdown hook.
     *
     * @return A shutdown hook for the ping loop.
     */
    public Thread getShutdownHook()
    {
        return new Thread(new Runnable()
        {
            public void run()
            {
                stop();
            }
        });
    }

    /**
     * Gets the underlying connection that this ping client is running on.
     *
     * @return The underlying connection that this ping client is running on.
     */
    public Connection getConnection()
    {
        return _connection;
    }

    /**
     * Creates consumers for the specified destinations and registers this pinger to listen to their messages.
     *
     * @param destinations The destinations to listen to.
     * @param selector     A selector to filter the messages with.
     * @throws javax.jms.JMSException Any JMSExceptions are allowed to fall through.
     */
    public void createReplyConsumers(Collection<Destination> destinations, String selector) throws JMSException
    {
        _logger.debug("public void createReplyConsumers(Collection<Destination> destinations = " + destinations
                      + ", String selector = " + selector + "): called");

        for (Destination destination : destinations)
        {
            // Create a consumer for the destination and set this pinger to listen to its messages.
            MessageConsumer consumer =
                    _consumerSession.createConsumer(destination, DEFAULT_PREFETCH, DEFAULT_NO_LOCAL, DEFAULT_EXCLUSIVE,
                                                    selector);
            consumer.setMessageListener(this);
        }
    }

    /**
     * Closes the pingers connection.
     *
     * @throws JMSException All JMSException are allowed to fall through.
     */
    public void close() throws JMSException
    {
        _logger.debug("public void close(): called");

        if (_connection != null)
        {
            _connection.close();
        }
    }

    /**
     * Convenience method to commit the transaction on the specified session. If the session to commit on is not
     * a transactional session, this method does nothing (unless the failover after send flag is set).
     * <p/>
     * <p/>If the {@link #_failAfterSend} flag is set, this will prompt the user to kill the broker before the commit
     * is applied. This flag applies whether the pinger is transactional or not.
     * <p/>
     * <p/>If the {@link #_failBeforeCommit} flag is set, this will prompt the user to kill the broker before the
     * commit is applied. If the {@link #_failAfterCommit} flag is set, this will prompt the user to kill the broker
     * after the commit is applied. These flags will only apply if using a transactional pinger.
     *
     * @param session The session to commit
     * @throws javax.jms.JMSException If the commit fails and then the rollback fails.
     *                                <p/>
     *                                //todo @todo Consider moving the fail after send logic into the send method. It is confusing to have it in this commit
     *                                method, because commits only apply to transactional pingers, but fail after send applied to transactional
     *                                and non-transactional alike.
     */
    protected void commitTx(Session session) throws JMSException
    {
        _logger.debug("protected void commitTx(Session session): called");

        _logger.trace("Batch time reached");
        if (_failAfterSend)
        {
            _logger.trace("Batch size reached");
            if (_failOnce)
            {
                _failAfterSend = false;
            }

            _logger.trace("Failing After Send");
            doFailover();
        }

        if (session.getTransacted())
        {
            try
            {
                if (_failBeforeCommit)
                {
                    if (_failOnce)
                    {
                        _failBeforeCommit = false;
                    }

                    _logger.trace("Failing Before Commit");
                    doFailover();
                }

                long l = System.currentTimeMillis();
                session.commit();
                _logger.debug("Time taken to commit :" + (System.currentTimeMillis() - l) + " ms" );

                if (_failAfterCommit)
                {
                    if (_failOnce)
                    {
                        _failAfterCommit = false;
                    }

                    _logger.trace("Failing After Commit");
                    doFailover();
                }

                _logger.trace("Session Commited.");
            }
            catch (JMSException e)
            {
                _logger.trace("JMSException on commit:" + e.getMessage(), e);

                // Warn that the bounce back client is not available.
                if (e.getLinkedException() instanceof AMQNoConsumersException)
                {
                    _logger.debug("No consumers on queue.");
                }

                try
                {
                    session.rollback();
                    _logger.trace("Message rolled back.");
                }
                catch (JMSException jmse)
                {
                    _logger.trace("JMSE on rollback:" + jmse.getMessage(), jmse);

                    // Both commit and rollback failed. Throw the rollback exception.
                    throw jmse;
                }
            }
        }
    }

    /**
     * Sends the message to the specified destination. If the destination is null, it gets sent to the default destination
     * of the ping producer. If an explicit destination is set, this overrides the default.
     *
     * @param destination The destination to send to.
     * @param message     The message to send.
     * @throws javax.jms.JMSException All underlying JMSExceptions are allowed to fall through.
     */
    protected void sendMessage(Destination destination, Message message) throws JMSException
    {
        if (_failBeforeSend)
        {
            if (_failOnce)
            {
                _failBeforeSend = false;
            }

            _logger.trace("Failing Before Send");
            doFailover();
        }

        if (destination == null)
        {
            _producer.send(message);
        }
        else
        {
            _producer.send(destination, message);
        }
    }

    /**
     * Prompts the user to terminate the broker, in order to test failover functionality. This method will block
     * until the user supplied some input on the terminal.
     */
    protected void doFailover()
    {
        System.out.println("Kill Broker now then press return");
        try
        {
            System.in.read();
        }
        catch (IOException e)
        {
            //ignore
        }

        System.out.println("Continuing.");
    }

    /**
     * This value will be changed by PingClient to represent the number of clients connected to each topic.
     *
     * @return int The number of consumers subscribing to each topic.
     */
    public int getConsumersPerTopic()
    {
        return _consumersPerTopic;
    }

    public int getExpectedNumPings(int numpings)
    {
        return numpings * getConsumersPerTopic();
    }


    /**
     * Defines a chained message listener interface that can be attached to this pinger. Whenever this pinger's
     * {@link PingPongProducer#onMessage} method is called, the chained listener set through the
     * {@link PingPongProducer#setChainedMessageListener} method is passed the message, and the remaining expected
     * count of messages with that correlation id.
     * <p/>
     * Provided only one pinger is producing messages with that correlation id, the chained listener will always be
     * given unique message counts. It will always be called while the producer waiting for all messages to arrive is
     * still blocked.
     */
    public static interface ChainedMessageListener
    {
        public void onMessage(Message message, int remainingCount) throws JMSException;
    }

    /**
     * Holds information on each correlation id. The countdown latch, the current timeout timer... More stuff to be
     * added to this: read/write lock to make onMessage more concurrent as described in class header comment.
     */
    protected static class PerCorrelationId
    {
        /**
         * Holds a countdown on number of expected messages.
         */
        CountDownLatch trafficLight;

        /**
         * Holds the last timestamp that the timeout was reset to.
         */
        Long timeOutStart;
    }
}
