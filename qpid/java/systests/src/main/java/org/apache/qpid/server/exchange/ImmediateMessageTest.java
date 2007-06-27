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
package org.apache.qpid.server.exchange;

import junit.framework.TestCase;

import org.apache.log4j.NDC;

import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import static org.apache.qpid.server.exchange.MessagingTestConfigProperties.*;
import org.apache.qpid.server.registry.ApplicationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;
import uk.co.thebadgerset.junit.extensions.util.TestContextProperties;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ImmediateMessageTest tests for the desired behaviour of immediate messages. Immediate messages are a non-JMS
 * feature. A message may be marked with an immediate delivery flag, which means that a consumer must be connected
 * to receive the message, through a valid route, when it is sent, or when its transaction is committed in the case
 * of transactional messaging. If this is not the case, the broker should return the message with a NO_CONSUMERS code.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Check that an immediate message is sent succesfully not using transactions when a consumer is connected.
 * <tr><td> Check that an immediate message is committed succesfully in a transaction when a consumer is connected.
 * <tr><td> Check that an immediate message results in no consumers code, not using transactions, when no consumer is
 *          connected.
 * <tr><td> Check that an immediate message results in no consumers code, upon transaction commit, when a consumer is
 *          connected.
 * <tr><td> Check that an immediate message results in no consumers code, not using transactions, when a consumer is
 *          disconnected.
 * <tr><dt> Check that an immediate message results in no consumers code, in a transaction, when a consumer is
 *          disconnected.
 * </table>
 *
 * @todo Write a test decorator, the sole function of which is to populate test context properties, from sys properties,
 *       from trailing prop=value pairs on the command line, from test properties files or other sources. This should
 *       run through stanard JUnit without the JUnit toolkit extensions, and through Maven surefire, and also through
 *       the JUnit toolkit extended test runners.
 *
 * @todo Veto test topologies using bounce back. Or else the bounce back client will act as an immediate consumer.
 */
public class ImmediateMessageTest extends TestCase
{
    /** Used for debugging. */
    private static final Logger log = LoggerFactory.getLogger(ImmediateMessageTest.class);

    /** Used to read the tests configurable properties through. */
    ParsedProperties testProps;

    /** Used to create unique destination names for each test.
     * @todo Move into the test framework.
     */
    private static AtomicLong uniqueDestsId = new AtomicLong();

    /** Check that an immediate message is sent succesfully not using transactions when a consumer is connected. */
    public void test_QPID_517_ImmediateOkNoTxP2P() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);
        testProps.setProperty(PUBSUB_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message with no errors.
        testClients.testNoExceptions(testProps);
    }

    /** Check that an immediate message is committed succesfully in a transaction when a consumer is connected. */
    public void test_QPID_517_ImmediateOkTxP2P() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, true);
        testProps.setProperty(PUBSUB_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message with no errors.
        testClients.testNoExceptions(testProps);
    }

    /** Check that an immediate message results in no consumers code, not using transactions, when a consumer is disconnected. */
    public void test_QPID_517_ImmediateFailsConsumerDisconnectedNoTxP2P() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);
        testProps.setProperty(PUBSUB_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Disconnect the consumer.
        testClients.getReceiver().getConsumer().close();

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoConsumersException.class);
    }

    /** Check that an immediate message results in no consumers code, in a transaction, when a consumer is disconnected. */
    public void test_QPID_517_ImmediateFailsConsumerDisconnectedTxP2P() throws Exception
    {
        // Ensure transactional sessions are on.
        testProps.setProperty(TRANSACTED_PROPNAME, true);
        testProps.setProperty(PUBSUB_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Disconnect the consumer.
        testClients.getReceiver().getConsumer().close();

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoConsumersException.class);
    }

    /** Check that an immediate message results in no consumers code, not using transactions, when no consumer is connected. */
    public void test_QPID_517_ImmediateFailsNoRouteNoTxP2P() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);
        testProps.setProperty(PUBSUB_PROPNAME, false);

        // Set up the messaging topology so that only the publishers producer is bound (do not set up the receiver to
        // collect its messages).
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoRouteException.class);
    }

    /** Check that an immediate message results in no consumers code, upon transaction commit, when a consumer is connected. */
    public void test_QPID_517_ImmediateFailsNoRouteTxP2P() throws Exception
    {
        // Ensure transactional sessions are on.
        testProps.setProperty(TRANSACTED_PROPNAME, true);
        testProps.setProperty(PUBSUB_PROPNAME, false);

        // Set up the messaging topology so that only the publishers producer is bound (do not set up the receiver to
        // collect its messages).
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoRouteException.class);
    }

    /** Check that an immediate message is sent succesfully not using transactions when a consumer is connected. */
    public void test_QPID_517_ImmediateOkNoTxPubSub() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);
        testProps.setProperty(PUBSUB_PROPNAME, true);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message with no errors.
        testClients.testNoExceptions(testProps);
    }

    /** Check that an immediate message is committed succesfully in a transaction when a consumer is connected. */
    public void test_QPID_517_ImmediateOkTxPubSub() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, true);
        testProps.setProperty(PUBSUB_PROPNAME, true);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message with no errors.
        testClients.testNoExceptions(testProps);
    }

    /** Check that an immediate message results in no consumers code, not using transactions, when a consumer is disconnected. */
    public void test_QPID_517_ImmediateFailsConsumerDisconnectedNoTxPubSub() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);
        testProps.setProperty(PUBSUB_PROPNAME, true);

        // Use durable subscriptions, so that the route remains open with no subscribers.
        testProps.setProperty(DURABLE_SUBSCRIPTION_PROPNAME, true);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Disconnect the consumer.
        testClients.getReceiver().getConsumer().close();

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoConsumersException.class);
    }

    /** Check that an immediate message results in no consumers code, in a transaction, when a consumer is disconnected. */
    public void test_QPID_517_ImmediateFailsConsumerDisconnectedTxPubSub() throws Exception
    {
        // Ensure transactional sessions are on.
        testProps.setProperty(TRANSACTED_PROPNAME, true);
        testProps.setProperty(PUBSUB_PROPNAME, true);

        // Use durable subscriptions, so that the route remains open with no subscribers.
        testProps.setProperty(DURABLE_SUBSCRIPTION_PROPNAME, true);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Disconnect the consumer.
        testClients.getReceiver().getConsumer().close();

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoConsumersException.class);
    }

    /** Check that an immediate message results in no consumers code, not using transactions, when no consumer is connected. */
    public void test_QPID_517_ImmediateFailsNoRouteNoTxPubSub() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);
        testProps.setProperty(PUBSUB_PROPNAME, true);

        // Set up the messaging topology so that only the publishers producer is bound (do not set up the receiver to
        // collect its messages).
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoRouteException.class);
    }

    /** Check that an immediate message results in no consumers code, upon transaction commit, when a consumer is connected. */
    public void test_QPID_517_ImmediateFailsNoRouteTxPubSub() throws Exception
    {
        // Ensure transactional sessions are on.
        testProps.setProperty(TRANSACTED_PROPNAME, true);
        testProps.setProperty(PUBSUB_PROPNAME, true);

        // Set up the messaging topology so that only the publishers producer is bound (do not set up the receiver to
        // collect its messages).
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, false);

        PublisherReceiver testClients = PublisherReceiverImpl.connectClients(testProps);

        // Send one message and get a linked no consumers exception.
        testClients.testWithAssertions(testProps, AMQNoRouteException.class);
    }

    protected void setUp() throws Exception
    {
        NDC.push(getName());

        testProps = TestContextProperties.getInstance(MessagingTestConfigProperties.defaults);

        /** All these tests should have the immediate flag on. */
        testProps.setProperty(IMMEDIATE_PROPNAME, true);

        /** Bind the receivers consumer by default. */
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, true);

        // Ensure that the in-vm broker is created.
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        try
        {
            // Ensure that the in-vm broker is cleaned up so that the next test starts afresh.
            TransportConnection.killVMBroker(1);
            ApplicationRegistry.remove(1);
        }
        finally
        {
            NDC.pop();
        }
    }

    /*
     * Stuff below:
     *
     * This will get tidied into some sort on JMS convenience framework, through which practically any usefull test
     * topology can be created. This will become a replacement for PingPongProducer.
     *
     * Base everything on standard connection properties defined in PingPongProducer. Split JMS and AMQP-only properties.
     *
     * Integrate with ConversationFactory, so that it will work with prod/con pairs.
     *
     * Support pub/rec pairs.
     * Support m*n pub/rec setups. All pubs/recs on one machine.
     *
     * Support bounce back clients, with configurable bounce back behavior. All, one in X, round robin one in m, etc.
     *
     * Support pairing of m*n pub/rec setups with bounce back clients. JVM running a test, can simulate m publishers,
     * will receive (a known subset of) all messages sent, bounced back to n receivers. Co-location of pub/rec will be
     * the normal model to allow accurate timings to be taken.
     *
     * Support creation of pub or rec only.
     * Support clock synching of pub/rec on different JVMs, by calculating clock offsets. Must also provide an accuracy
     * estimate to +- the results.
     *
     * Augment the interop Coordinator, to become a full distributed test coordinator. Capable of querying available
     * tests machines, looking at test parameters and farming out tests onto the test machines, passing all test
     * parameters, standard naming of pub/rec config parameters used to set up m*n test topologies, run test cases,
     * report results, tear down m*n topologies. Need to split the re-usable general purpose distributed test coordinator
     * from the Qpid specific test framework for creating test-topoloigies and passing Qpid specific parameters.
     *
     * Write all tests against pub/rec pairs, without coding to the fact that the topology may be anything from 1:1 in
     * JVM to m*n with bounce back clients accross many machines. That is, make the test topology orthogonal to the test
     * case.
     */

    private static class ExceptionMonitor implements ExceptionListener
    {
        List<JMSException> exceptions = new ArrayList<JMSException>();

        public void onException(JMSException e)
        {
            log.debug("ExceptionMonitor got JMSException: ", e);

            exceptions.add(e);
        }

        public boolean assertNoExceptions()
        {
            return exceptions.isEmpty();
        }

        public boolean assertOneJMSException()
        {
            return exceptions.size() == 1;
        }

        public boolean assertOneJMSExceptionWithLinkedCause(Class aClass)
        {
            if (exceptions.size() == 1)
            {
                JMSException e = exceptions.get(0);

                Exception linkedCause = e.getLinkedException();

                if ((linkedCause != null) && aClass.isInstance(linkedCause))
                {
                    return true;
                }
            }

            return false;
        }

        /**
         * Reports the number of exceptions held by this monitor.
         *
         * @return The number of exceptions held by this monitor.
         */
        public int size()
        {
            return exceptions.size();
        }

        public void reset()
        {
            exceptions = new ArrayList();
        }

        /**
         * Provides a dump of the stack traces of all exceptions that this exception monitor was notified of. Mainly
         * use for debugging/test failure reporting purposes.
         *
         * @return A string containing a dump of the stack traces of all exceptions.
         */
        public String toString()
        {
            String result = "ExceptionMonitor: holds " + exceptions.size() + " exceptions.\n\n";

            for (JMSException ex : exceptions)
            {
                result += getStackTrace(ex) + "\n";
            }

            return result;
        }

        /**
         * Prints an exception stack trace into a string.
         *
         * @param t The throwable to get the stack trace from.
         *
         * @return A string containing the throwables stack trace.
         */
        public static String getStackTrace(Throwable t)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, true);
            t.printStackTrace(pw);
            pw.flush();
            sw.flush();

            return sw.toString();
        }
    }

    public static class MessageMonitor implements MessageListener
    {
        public void onMessage(Message message)
        {
            log.debug("public void onMessage(Message message): called");
        }
    }

    /**
     * Establishes a JMS connection using a properties file and qpids built in JNDI implementation. This is a simple
     * convenience method for code that does anticipate handling connection failures. All exceptions that indicate
     * that the connection has failed, are wrapped as rutime exceptions, preumably handled by a top level failure
     * handler.
     *
     * @param messagingProps Any additional connection properties.
     *
     * @return A JMS conneciton.
     *
     * @todo Move this to a Utils library class or base test class. Also move the copy in interop.TestClient too.
     *
     * @todo Make in VM broker creation step optional on whether one is to be used or not.
     */
    public static Connection createConnection(ParsedProperties messagingProps)
    {
        log.debug("public static Connection createConnection(Properties messagingProps = " + messagingProps + "): called");

        try
        {
            // Extract the configured connection properties from the test configuration.
            String conUsername = messagingProps.getProperty(USERNAME_PROPNAME);
            String conPassword = messagingProps.getProperty(PASSWORD_PROPNAME);
            String virtualHost = messagingProps.getProperty(VIRTUAL_HOST_PROPNAME);
            String brokerUrl = messagingProps.getProperty(BROKER_PROPNAME);

            // Set up the broker connection url.
            String connectionString =
                "amqp://" + conUsername + ":" + conPassword + "/" + ((virtualHost != null) ? virtualHost : "")
                + "?brokerlist='" + brokerUrl + "'";

            // messagingProps.setProperty(CONNECTION_PROPNAME, connectionString);

            Context ctx = new InitialContext(messagingProps);

            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_NAME);
            Connection connection = cf.createConnection();

            return connection;
        }
        catch (NamingException e)
        {
            log.debug("Got NamingException: ", e);
            throw new RuntimeException("Got JNDI NamingException whilst looking up the connection factory.", e);
        }
        catch (JMSException e)
        {
            log.debug("Got JMSException: ", e);
            throw new RuntimeException("Could not establish connection due to JMSException.", e);
        }
    }

    /**
     * Creates a publisher and a receiver on the same connection, configured according the to specified standard
     * properties.
     *
     * @param messagingProps The connection properties.
     *
     * @return A publisher/receiver client pair.
     */
    public static PublisherReceiver createPublisherReceiverPairSharedConnection(ParsedProperties messagingProps)
    {
        try
        {
            // Get a unique offset to append to destination names to make them unique to the connection.
            long uniqueId = uniqueDestsId.incrementAndGet();

            // Extract the standard test configuration parameters relevant to the connection.
            String destinationSendRoot = messagingProps.getProperty(SEND_DESTINATION_NAME_ROOT_PROPNAME) + "_" + uniqueId;
            String destinationReceiveRoot =
                messagingProps.getProperty(RECEIVE_DESTINATION_NAME_ROOT_PROPNAME) + "_" + uniqueId;
            boolean createPublisherProducer = messagingProps.getPropertyAsBoolean(PUBLISHER_PRODUCER_BIND_PROPNAME);
            boolean createPublisherConsumer = messagingProps.getPropertyAsBoolean(PUBLISHER_CONSUMER_BIND_PROPNAME);
            boolean createReceiverProducer = messagingProps.getPropertyAsBoolean(RECEIVER_PRODUCER_BIND_PROPNAME);
            boolean createReceiverConsumer = messagingProps.getPropertyAsBoolean(RECEIVER_CONSUMER_BIND_PROPNAME);

            // Check which JMS flags and options are to be set.
            int ackMode = messagingProps.getPropertyAsInteger(ACK_MODE_PROPNAME);
            boolean useTopics = messagingProps.getPropertyAsBoolean(PUBSUB_PROPNAME);
            boolean transactional = messagingProps.getPropertyAsBoolean(TRANSACTED_PROPNAME);
            boolean durableSubscription = messagingProps.getPropertyAsBoolean(DURABLE_SUBSCRIPTION_PROPNAME);

            // Check if any Qpid/AMQP specific flags or options need to be set.
            boolean immediate = messagingProps.getPropertyAsBoolean(IMMEDIATE_PROPNAME);
            boolean mandatory = messagingProps.getPropertyAsBoolean(MANDATORY_PROPNAME);
            boolean needsQpidOptions = immediate | mandatory;

            /*log.debug("ackMode = " + ackMode);
            log.debug("useTopics = " + useTopics);
            log.debug("destinationSendRoot = " + destinationSendRoot);
            log.debug("destinationReceiveRoot = " + destinationReceiveRoot);
            log.debug("createPublisherProducer = " + createPublisherProducer);
            log.debug("createPublisherConsumer = " + createPublisherConsumer);
            log.debug("createReceiverProducer = " + createReceiverProducer);
            log.debug("createReceiverConsumer = " + createReceiverConsumer);
            log.debug("transactional = " + transactional);
            log.debug("immediate = " + immediate);
            log.debug("mandatory = " + mandatory);
            log.debug("needsQpidOptions = " + needsQpidOptions);*/

            // Create connection, sessions and producer/consumer pairs on each session.
            Connection connection = createConnection(messagingProps);

            // Add the connection exception listener to assert on exception conditions with.
            ExceptionMonitor exceptionMonitor = new ExceptionMonitor();
            connection.setExceptionListener(exceptionMonitor);

            Session publisherSession = connection.createSession(transactional, ackMode);
            Session receiverSession = connection.createSession(transactional, ackMode);

            Destination publisherProducerDestination =
                useTopics ? (Destination) publisherSession.createTopic(destinationSendRoot)
                          : publisherSession.createQueue(destinationSendRoot);

            MessageProducer publisherProducer =
                createPublisherProducer
                ? (needsQpidOptions
                    ? ((AMQSession) publisherSession).createProducer(publisherProducerDestination, mandatory, immediate)
                    : publisherSession.createProducer(publisherProducerDestination)) : null;

            MessageConsumer publisherConsumer =
                createPublisherConsumer
                ? publisherSession.createConsumer(publisherSession.createQueue(destinationReceiveRoot)) : null;

            if (publisherConsumer != null)
            {
                publisherConsumer.setMessageListener(new MessageMonitor());
            }

            MessageProducer receiverProducer =
                createReceiverProducer ? receiverSession.createProducer(receiverSession.createQueue(destinationReceiveRoot))
                                       : null;

            Destination receiverConsumerDestination =
                useTopics ? (Destination) receiverSession.createTopic(destinationSendRoot)
                          : receiverSession.createQueue(destinationSendRoot);

            MessageConsumer receiverConsumer =
                createReceiverConsumer
                ? ((durableSubscription && useTopics)
                    ? receiverSession.createDurableSubscriber((Topic) receiverConsumerDestination, "testsub")
                    : receiverSession.createConsumer(receiverConsumerDestination)) : null;

            if (receiverConsumer != null)
            {
                receiverConsumer.setMessageListener(new MessageMonitor());
            }

            // Start listening for incoming messages.
            connection.start();

            // Package everything up.
            ProducerConsumerPair publisher =
                new ProducerConsumerPairImpl(publisherProducer, publisherConsumer, publisherSession);
            ProducerConsumerPair receiver =
                new ProducerConsumerPairImpl(receiverProducer, receiverConsumer, receiverSession);

            PublisherReceiver result = new PublisherReceiverImpl(publisher, receiver, connection, exceptionMonitor);

            return result;
        }
        catch (JMSException e)
        {
            log.debug("Got JMSException: ", e);
            throw new RuntimeException("Could not create publisher/receiver pair due to a JMSException.", e);
        }
    }

    public static Message createTestMessage(ProducerConsumerPair client, ParsedProperties testProps) throws JMSException
    {
        return client.getSession().createTextMessage("Hello");
            // return client.getSession().createMessage();
    }

    /**
     * A ProducerConsumerPair is a pair consisting of one message producer and one message consumer. It is a standard
     * unit of connectivity allowing a full-duplex conversation to be held, provided both the consumer and producer
     * are instantiated and configured.
     *
     * In some situations a test, or piece of application code will be written with differing numbers of publishers
     * and receivers in different roles, where one role produces only and one consumes only. This messaging topology
     * can still make use of producer/consumer pairs as standard building blocks, combined into publisher/receiver
     * units to fulfill the different messaging roles, with the publishers consumer uninstantiated and the receivers
     * producer uninstantiated. Use a {@link PublisherReceiver} for this.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities
     * <tr><td> Provide a message producer for sending messages.
     * <tr><td> Provide a message consumer for receiving messages.
     * </table>
     *
     * @todo Update the {@link org.apache.qpid.util.ConversationFactory} so that it accepts these as the basic
     *       conversation connection units.
     */
    public static interface ProducerConsumerPair
    {
        public MessageProducer getProducer();

        public MessageConsumer getConsumer();

        public void send(Message message) throws JMSException;

        public Session getSession();

        public void close() throws JMSException;
    }

    /**
     * A single producer and consumer.
     */
    public static class ProducerConsumerPairImpl implements ProducerConsumerPair
    {
        MessageProducer producer;

        MessageConsumer consumer;

        Session session;

        public ProducerConsumerPairImpl(MessageProducer producer, MessageConsumer consumer, Session session)
        {
            this.producer = producer;
            this.consumer = consumer;
            this.session = session;
        }

        public MessageProducer getProducer()
        {
            return producer;
        }

        public MessageConsumer getConsumer()
        {
            return consumer;
        }

        public void send(Message message) throws JMSException
        {
            producer.send(message);
        }

        public Session getSession()
        {
            return session;
        }

        public void close() throws JMSException
        {
            if (producer != null)
            {
                producer.close();
            }

            if (consumer != null)
            {
                consumer.close();
            }
        }
    }

    /**
     * Multiple producers and consumers made to look like a single producer and consumer. All methods repeated accross
     * all producers and consumers.
     */
    public static class MultiProducerConsumerPairImpl implements ProducerConsumerPair
    {
        public MessageProducer getProducer()
        {
            throw new RuntimeException("Not implemented.");
        }

        public MessageConsumer getConsumer()
        {
            throw new RuntimeException("Not implemented.");
        }

        public void send(Message message) throws JMSException
        {
            throw new RuntimeException("Not implemented.");
        }

        public Session getSession()
        {
            throw new RuntimeException("Not implemented.");
        }

        public void close()
        {
            throw new RuntimeException("Not implemented.");
        }
    }

    /**
     * A PublisherReceiver consists of two sets of producer/consumer pairs, one for an 'instigating' publisher
     * role, and one for a more 'passive' receiver role.
     *
     * <p/>A set of publishers and receivers forms a typical test configuration where both roles are to be controlled
     * from within a single JVM. This is not a particularly usefull arrangement for applications which want to place
     * these roles on physically seperate machines and pass messages between them. It is a faily normal arrangement for
     * test code though, either to publish and receive messages through an in-VM message broker in order to test its
     * expected behaviour, or to publish and receive (possibly bounced back) messages through a seperate broker instance
     * in order to take performance timings. In the case of performance timings, the co-location of the publisher and
     * receiver means that the timings are taken on the same machine for accurate timing without the need for clock
     * synchronization.
     *
     * <p/><table id="crc"><caption>CRC Card</caption>
     * <tr><th> Responsibilities
     * <tr><td> Manage an m*n array of publisher and recievers.
     * </table>
     */
    public static interface PublisherReceiver
    {
        public ProducerConsumerPair getPublisher();

        public ProducerConsumerPair getReceiver();

        public void start();

        public void send(ParsedProperties testProps, int numMessages);

        public ExceptionMonitor getConnectionExceptionMonitor();

        public ExceptionMonitor getExceptionMonitor();

        public void testWithAssertions(ParsedProperties testProps, Class aClass /*, assertions */);

        public void testNoExceptions(ParsedProperties testProps);

        public void close();
    }

    public static class PublisherReceiverImpl implements PublisherReceiver
    {
        private ProducerConsumerPair publisher;
        private ProducerConsumerPair receiver;
        private Connection connection;
        private ExceptionMonitor connectionExceptionMonitor;
        private ExceptionMonitor exceptionMonitor;

        public PublisherReceiverImpl(ProducerConsumerPair publisher, ProducerConsumerPair receiver, Connection connection,
            ExceptionMonitor connectionExceptionMonitor)
        {
            this.publisher = publisher;
            this.receiver = receiver;
            this.connection = connection;
            this.connectionExceptionMonitor = connectionExceptionMonitor;
            this.exceptionMonitor = new ExceptionMonitor();
        }

        public ProducerConsumerPair getPublisher()
        {
            return publisher;
        }

        public ProducerConsumerPair getReceiver()
        {
            return receiver;
        }

        public void start()
        { }

        public void close()
        {
            try
            {
                publisher.close();
                receiver.close();
                connection.close();
            }
            catch (JMSException e)
            {
                throw new RuntimeException("Got JMSException during close.", e);
            }
        }

        public ExceptionMonitor getConnectionExceptionMonitor()
        {
            return connectionExceptionMonitor;
        }

        public ExceptionMonitor getExceptionMonitor()
        {
            return exceptionMonitor;
        }

        public void send(ParsedProperties testProps, int numMessages)
        {
            boolean transactional = testProps.getPropertyAsBoolean(TRANSACTED_PROPNAME);

            // Send an immediate message through the publisher and ensure that it results in a JMSException.
            try
            {
                getPublisher().send(createTestMessage(getPublisher(), testProps));

                if (transactional)
                {
                    getPublisher().getSession().commit();
                }
            }
            catch (JMSException e)
            {
                log.debug("Got JMSException: ", e);
                exceptionMonitor.onException(e);
            }
        }

        public void testWithAssertions(ParsedProperties testProps, Class aClass /*, assertions */)
        {
            start();
            send(testProps, 1);
            pause(1000L);

            String errors = "";

            ExceptionMonitor connectionExceptionMonitor = getConnectionExceptionMonitor();
            if (!connectionExceptionMonitor.assertOneJMSExceptionWithLinkedCause(aClass))
            {
                errors += "Was expecting linked exception type " + aClass.getName() + " on the connection.\n";
                errors +=
                    (connectionExceptionMonitor.size() > 0)
                    ? ("Actually got the following exceptions on the connection, " + connectionExceptionMonitor)
                    : "Got no exceptions on the connection.";
            }

            // Clean up the publisher/receiver client pair.
            close();

            assertEquals(errors, "", errors);
        }

        /**
         */
        public void testNoExceptions(ParsedProperties testProps)
        {
            start();
            send(testProps, 1);
            pause(1000L);

            String errors = "";

            if (!getConnectionExceptionMonitor().assertNoExceptions())
            {
                errors += "Was expecting no exceptions.\n";
                errors += "Got the following exceptions on the connection, " + getConnectionExceptionMonitor();
            }

            if (!getExceptionMonitor().assertNoExceptions())
            {
                errors += "Was expecting no exceptions.\n";
                errors += "Got the following exceptions on the producer, " + getExceptionMonitor();
            }

            // Clean up the publisher/receiver client pair.
            close();

            assertEquals(errors, "", errors);
        }

        public static PublisherReceiver connectClients(ParsedProperties testProps)
        {
            // Create a standard publisher/receiver test client pair on a shared connection, individual sessions.
            return createPublisherReceiverPairSharedConnection(testProps);
        }
    }

    /**
     * Pauses for the specified length of time. In the event of failing to pause for at least that length of time
     * due to interuption of the thread, a RutimeException is raised to indicate the failure. The interupted status
     * of the thread is restores in that case. This method should only be used when it is expected that the pause
     * will be succesfull, for example in test code that relies on inejecting a pause.
     *
     * @param t The minimum time to pause for in milliseconds.
     */
    public static void pause(long t)
    {
        try
        {
            Thread.sleep(t);
        }
        catch (InterruptedException e)
        {
            // Restore the interrupted status
            Thread.currentThread().interrupt();

            throw new RuntimeException("Failed to generate the requested pause length.", e);
        }
    }
}
