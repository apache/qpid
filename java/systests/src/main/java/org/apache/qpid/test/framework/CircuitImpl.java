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
package org.apache.qpid.test.framework;

import junit.framework.Assert;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.framework.MessageMonitor;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import javax.jms.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CircuitImpl provides an implementation of the test circuit. This is a first prototype implementation and only supports
 * a single producer/consumer on each end of the circuit, with both ends of the circuit on the same JVM.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Supply the publishing and receiving ends of a test messaging circuit.
 *     <td> {@link PublisherImpl}, {@link ReceiverImpl}
 * <tr><td> Start the circuit running.
 * <tr><td> Close the circuit down.
 * <tr><td> Take a reading of the circuits state.
 * <tr><td> Apply assertions against the circuits state. <td> {@link Assertion}
 * <tr><td> Send test messages over the circuit.
 * <tr><td> Perform the default test procedure on the circuit.
 * <tr><td> Provide access to connection and session exception monitors <td> {@link ExceptionMonitor}
 * </table>
 */
public class CircuitImpl implements Circuit
{
    /** Used to create unique destination names for each test. */
    private static AtomicLong uniqueDestsId = new AtomicLong();

    /** Holds the test configuration for the circuit. */
    private ParsedProperties testProps;

    /** Holds the publishing end of the circuit. */
    private PublisherImpl publisher;

    /** Holds the receiving end of the circuit. */
    private ReceiverImpl receiver;

    /** Holds the connection for the publishing end of the circuit. */
    private Connection connection;

    /** Holds the exception listener for the connection on the publishing end of the circuit. */
    private ExceptionMonitor connectionExceptionMonitor;

    /** Holds the exception listener for the session on the publishing end of the circuit. */
    private ExceptionMonitor exceptionMonitor;

    /**
     * Creates a test circuit using the specified test parameters. The publisher, receiver, connection and
     * connection monitor must already have been created, to assemble the circuit.
     *
     * @param testProps                  The test parameters.
     * @param publisher                  The test publisher.
     * @param receiver                   The test receiver.
     * @param connection                 The connection.
     * @param connectionExceptionMonitor The connection exception monitor.
     */
    protected CircuitImpl(ParsedProperties testProps, PublisherImpl publisher, ReceiverImpl receiver, Connection connection,
        ExceptionMonitor connectionExceptionMonitor)
    {
        this.testProps = testProps;
        this.publisher = publisher;
        this.receiver = receiver;
        this.connection = connection;
        this.connectionExceptionMonitor = connectionExceptionMonitor;
        this.exceptionMonitor = new ExceptionMonitor();

        // Set this as the parent circuit on the publisher and receiver.
        publisher.setCircuit(this);
        receiver.setCircuit(this);
    }

    /**
     * Creates a test circuit from the specified test parameters.
     *
     * @param testProps The test parameters.
     *
     * @return A connected and ready to start, test circuit.
     */
    public static Circuit createCircuit(ParsedProperties testProps)
    {
        // Create a standard publisher/receiver test client pair on a shared connection, individual sessions.
        try
        {
            // Get a unique offset to append to destination names to make them unique to the connection.
            long uniqueId = uniqueDestsId.incrementAndGet();

            // Extract the standard test configuration parameters relevant to the connection.
            String destinationSendRoot =
                testProps.getProperty(MessagingTestConfigProperties.SEND_DESTINATION_NAME_ROOT_PROPNAME) + "_" + uniqueId;
            String destinationReceiveRoot =
                testProps.getProperty(MessagingTestConfigProperties.RECEIVE_DESTINATION_NAME_ROOT_PROPNAME) + "_" + uniqueId;
            boolean createPublisherProducer =
                testProps.getPropertyAsBoolean(MessagingTestConfigProperties.PUBLISHER_PRODUCER_BIND_PROPNAME);
            boolean createPublisherConsumer =
                testProps.getPropertyAsBoolean(MessagingTestConfigProperties.PUBLISHER_CONSUMER_BIND_PROPNAME);
            boolean createReceiverProducer =
                testProps.getPropertyAsBoolean(MessagingTestConfigProperties.RECEIVER_PRODUCER_BIND_PROPNAME);
            boolean createReceiverConsumer =
                testProps.getPropertyAsBoolean(MessagingTestConfigProperties.RECEIVER_CONSUMER_BIND_PROPNAME);

            // Check which JMS flags and options are to be set.
            int ackMode = testProps.getPropertyAsInteger(MessagingTestConfigProperties.ACK_MODE_PROPNAME);
            boolean useTopics = testProps.getPropertyAsBoolean(MessagingTestConfigProperties.PUBSUB_PROPNAME);
            boolean transactional = testProps.getPropertyAsBoolean(MessagingTestConfigProperties.TRANSACTED_PROPNAME);
            boolean durableSubscription =
                testProps.getPropertyAsBoolean(MessagingTestConfigProperties.DURABLE_SUBSCRIPTION_PROPNAME);

            // Check if any Qpid/AMQP specific flags or options need to be set.
            boolean immediate = testProps.getPropertyAsBoolean(MessagingTestConfigProperties.IMMEDIATE_PROPNAME);
            boolean mandatory = testProps.getPropertyAsBoolean(MessagingTestConfigProperties.MANDATORY_PROPNAME);
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
            Connection connection = TestUtils.createConnection(testProps);

            // Add the connection exception listener to assert on exception conditions with.
            ExceptionMonitor exceptionMonitor = new ExceptionMonitor();
            connection.setExceptionListener(exceptionMonitor);

            Session publisherSession = connection.createSession(transactional, ackMode);
            Session receiverSession = connection.createSession(transactional, ackMode);

            Destination publisherProducerDestination =
                useTopics ? publisherSession.createTopic(destinationSendRoot)
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
                useTopics ? receiverSession.createTopic(destinationSendRoot)
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
            PublisherImpl publisher = new PublisherImpl(publisherProducer, publisherConsumer, publisherSession);
            ReceiverImpl receiver = new ReceiverImpl(receiverProducer, receiverConsumer, receiverSession);

            return new CircuitImpl(testProps, publisher, receiver, connection, exceptionMonitor);
        }
        catch (JMSException e)
        {
            throw new RuntimeException("Could not create publisher/receiver pair due to a JMSException.", e);
        }
    }

    /**
     * Gets the interface on the publishing end of the circuit.
     *
     * @return The publishing end of the circuit.
     */
    public Publisher getPublisher()
    {
        return publisher;
    }

    /**
     * Gets the interface on the receiving end of the circuit.
     *
     * @return The receiving end of the circuit.
     */
    public Receiver getReceiver()
    {
        return receiver;
    }

    /**
     * Checks the test circuit. The effect of this is to gather the circuits state, for both ends of the circuit,
     * into a report, against which assertions may be checked.
     */
    public void check()
    { }

    /**
     * Applied a list of assertions against the test circuit. The {@link #check()} method should be called before doing
     * this, to ensure that the circuit has gathered its state into a report to assert against.
     *
     * @param assertions The list of assertions to apply.
     * @return Any assertions that failed.
     */
    public List<Assertion> applyAssertions(List<Assertion> assertions)
    {
        List<Assertion> failures = new LinkedList<Assertion>();

        for (Assertion assertion : assertions)
        {
            if (!assertion.apply())
            {
                failures.add(assertion);
            }
        }

        return failures;
    }

    /**
     * Connects and starts the circuit. After this method is called the circuit is ready to send messages.
     */
    public void start()
    { }

    /**
     * Closes the circuit. All associated resources are closed.
     */
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

    /**
     * Sends a message on the test circuit. The exact nature of the message sent is controlled by the test parameters.
     */
    public void send()
    {
        boolean transactional = testProps.getPropertyAsBoolean(MessagingTestConfigProperties.TRANSACTED_PROPNAME);

        // Send an immediate message through the publisher and ensure that it results in a JMSException.
        try
        {
            getPublisher().send(createTestMessage(getPublisher()));

            if (transactional)
            {
                getPublisher().getSession().commit();
            }
        }
        catch (JMSException e)
        {
            exceptionMonitor.onException(e);
        }
    }

    /**
     * Runs the default test procedure against the circuit, and checks that all of the specified assertions hold. The
     * outline of the default test procedure is:
     *
     * <p/><pre>
     * Start the circuit.
     * Send test messages.
     * Request a status report.
     * Assert conditions on the publishing end of the circuit.
     * Assert conditions on the receiving end of the circuit.
     * Close the circuit.
     * Pass with no failed assertions or fail with a list of failed assertions.
     * </pre>
     *
     * @param numMessages The number of messages to send using the default test procedure.
     * @param assertions  The list of assertions to apply.
     * @return Any assertions that failed.
     */
    public List<Assertion> test(int numMessages, List<Assertion> assertions)
    {
        // Start the test circuit.
        start();

        // Send the requested number of test messages.
        for (int i = 0; i < numMessages; i++)
        {
            send();
        }

        // Inject a short pause to allow time for exceptions to come back asynchronously.
        TestUtils.pause(100L);

        // Request a status report.
        check();

        // Apply all of the requested assertions, keeping record of any that fail.
        List<Assertion> failures = applyAssertions(assertions);

        // Clean up the publisher/receiver/session/connections.
        close();

        // Return any failed assertions to the caller.
        return failures;
    }

    /**
     * Creates a message with the properties defined as per the test parameters.
     *
     * @param client The circuit end to create the message on.
     *
     * @return The test message.
     *
     * @throws JMSException Any JMSException occurring during creation of the message is allowed to fall through.
     */
    private Message createTestMessage(CircuitEnd client) throws JMSException
    {
        return client.getSession().createTextMessage("Hello");
    }

    /**
     * Gets the exception monitor for the publishing ends connection.
     *
     * @return The exception monitor for the publishing ends connection.
     */
    public ExceptionMonitor getConnectionExceptionMonitor()
    {
        return connectionExceptionMonitor;
    }

    /**
     * Gets the exception monitor for the publishing ends session.
     *
     * @return The exception monitor for the publishing ends session.
     */
    public ExceptionMonitor getExceptionMonitor()
    {
        return exceptionMonitor;
    }
}
