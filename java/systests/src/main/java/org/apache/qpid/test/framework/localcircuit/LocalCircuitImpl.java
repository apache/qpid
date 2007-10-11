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
package org.apache.qpid.test.framework.localcircuit;

import org.apache.log4j.Logger;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.test.framework.*;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import javax.jms.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LocalCircuitImpl provides an implementation of the test circuit. This is a local only circuit implementation that
 * supports a single producer/consumer on each end of the circuit, with both ends of the circuit on the same JVM.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Supply the publishing and receiving ends of a test messaging circuit.
 *     <td> {@link LocalPublisherImpl}, {@link LocalReceiverImpl}
 * <tr><td> Start the circuit running.
 * <tr><td> Close the circuit down.
 * <tr><td> Take a reading of the circuits state.
 * <tr><td> Apply assertions against the circuits state. <td> {@link Assertion}
 * <tr><td> Send test messages over the circuit.
 * <tr><td> Perform the default test procedure on the circuit.
 * <tr><td> Provide access to connection and controlSession exception monitors <td> {@link ExceptionMonitor}
 * </table>
 */
public class LocalCircuitImpl implements Circuit
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(LocalCircuitImpl.class);

    /** Used to create unique destination names for each test. */
    private static AtomicLong uniqueDestsId = new AtomicLong();

    /** Holds the test configuration for the circuit. */
    private ParsedProperties testProps;

    /** Holds the publishing end of the circuit. */
    private LocalPublisherImpl publisher;

    /** Holds the receiving end of the circuit. */
    private LocalReceiverImpl receiver;

    /** Holds the connection for the publishing end of the circuit. */
    private Connection connection;

    /** Holds the exception listener for the connection on the publishing end of the circuit. */
    private ExceptionMonitor connectionExceptionMonitor;

    /** Holds the exception listener for the controlSession on the publishing end of the circuit. */
    private ExceptionMonitor exceptionMonitor;

    /**
     * Creates a test circuit using the specified test parameters. The publisher, receivers, connection and
     * connection monitor must already have been created, to assemble the circuit.
     *
     * @param testProps                  The test parameters.
     * @param publisher                  The test publisher.
     * @param receiver                   The test receivers.
     * @param connection                 The connection.
     * @param connectionExceptionMonitor The connection exception monitor.
     */
    protected LocalCircuitImpl(ParsedProperties testProps, LocalPublisherImpl publisher, LocalReceiverImpl receiver,
        Connection connection, ExceptionMonitor connectionExceptionMonitor)
    {
        this.testProps = testProps;
        this.publisher = publisher;
        this.receiver = receiver;
        this.connection = connection;
        this.connectionExceptionMonitor = connectionExceptionMonitor;
        this.exceptionMonitor = new ExceptionMonitor();

        // Set this as the parent circuit on the publisher and receivers.
        publisher.setCircuit(this);
        receiver.setCircuit(this);
    }

    /**
     * Creates a local test circuit from the specified test parameters.
     *
     * @param testProps The test parameters.
     *
     * @return A connected and ready to start, test circuit.
     */
    public static Circuit createCircuit(ParsedProperties testProps)
    {
        // Create a standard publisher/receivers test client pair on a shared connection, individual sessions.
        try
        {
            // Cast the test properties into a typed interface for convenience.
            MessagingTestConfigProperties props = new MessagingTestConfigProperties(testProps);

            // Get a unique offset to append to destination names to make them unique to the connection.
            long uniqueId = uniqueDestsId.incrementAndGet();

            // Set up the connection.
            Connection connection = TestUtils.createConnection(testProps);

            // Add the connection exception listener to assert on exception conditions with.
            // ExceptionMonitor exceptionMonitor = new ExceptionMonitor();
            // connection.setExceptionListener(exceptionMonitor);

            // Set up the publisher.
            CircuitEndBase publisherEnd = createPublisherCircuitEnd(connection, props, uniqueId);

            // Set up the receiver.
            CircuitEndBase receiverEnd = createReceiverCircuitEnd(connection, props, uniqueId);

            // Start listening for incoming messages.
            connection.start();

            // Package everything up.
            LocalPublisherImpl publisher = new LocalPublisherImpl(publisherEnd);
            LocalReceiverImpl receiver = new LocalReceiverImpl(receiverEnd);

            return new LocalCircuitImpl(testProps, publisher, receiver, connection, publisher.getExceptionMonitor());
        }
        catch (JMSException e)
        {
            throw new RuntimeException("Could not create publisher/receivers pair due to a JMSException.", e);
        }
    }

    /**
     * Builds a circuit end suitable for the publishing side of a test circuit, from standard test parameters.
     *
     * @param connection The connection to build the circuit end on.
     * @param testProps  The test parameters to configure the circuit end construction.
     * @param uniqueId   A unique number to being numbering destinations from, to make this circuit unique.
     *
     * @return A circuit end suitable for the publishing side of a test circuit.
     *
     * @throws JMSException Any underlying JMSExceptions are allowed to fall through and fail the creation.
     */
    public static CircuitEndBase createPublisherCircuitEnd(Connection connection, ParsedProperties testProps, long uniqueId)
        throws JMSException
    {
        log.debug(
            "public static CircuitEndBase createPublisherCircuitEnd(Connection connection, ParsedProperties testProps, long uniqueId = "
            + uniqueId + "): called");

        // Cast the test properties into a typed interface for convenience.
        MessagingTestConfigProperties props = new MessagingTestConfigProperties(testProps);

        Session session = connection.createSession(props.getTransacted(), props.getAckMode());

        Destination destination =
            props.getPubsub() ? session.createTopic(props.getSendDestinationNameRoot() + "_" + uniqueId)
                              : session.createQueue(props.getSendDestinationNameRoot() + "_" + uniqueId);

        MessageProducer producer =
            props.getPublisherProducerBind()
            ? ((props.getImmediate() | props.getMandatory())
                ? ((AMQSession) session).createProducer(destination, props.getMandatory(), props.getImmediate())
                : session.createProducer(destination)) : null;

        MessageConsumer consumer =
            props.getPublisherConsumerBind()
            ? session.createConsumer(session.createQueue(props.getReceiveDestinationNameRoot() + "_" + uniqueId)) : null;

        MessageMonitor messageMonitor = new MessageMonitor();

        if (consumer != null)
        {
            consumer.setMessageListener(messageMonitor);
        }

        ExceptionMonitor exceptionMonitor = new ExceptionMonitor();
        connection.setExceptionListener(exceptionMonitor);

        if (!props.getPublisherConsumerActive() && (consumer != null))
        {
            consumer.close();
        }

        return new CircuitEndBase(producer, consumer, session, messageMonitor, exceptionMonitor);
    }

    /**
     * Builds a circuit end suitable for the receiving side of a test circuit, from standard test parameters.
     *
     * @param connection The connection to build the circuit end on.
     * @param testProps  The test parameters to configure the circuit end construction.
     * @param uniqueId   A unique number to being numbering destinations from, to make this circuit unique.
     *
     * @return A circuit end suitable for the receiving side of a test circuit.
     *
     * @throws JMSException Any underlying JMSExceptions are allowed to fall through and fail the creation.
     */
    public static CircuitEndBase createReceiverCircuitEnd(Connection connection, ParsedProperties testProps, long uniqueId)
        throws JMSException
    {
        log.debug(
            "public static CircuitEndBase createReceiverCircuitEnd(Connection connection, ParsedProperties testProps, long uniqueId = "
            + uniqueId + "): called");

        // Cast the test properties into a typed interface for convenience.
        MessagingTestConfigProperties props = new MessagingTestConfigProperties(testProps);

        Session session = connection.createSession(props.getTransacted(), props.getAckMode());

        MessageProducer producer =
            props.getReceiverProducerBind()
            ? session.createProducer(session.createQueue(props.getReceiveDestinationNameRoot() + "_" + uniqueId)) : null;

        Destination destination =
            props.getPubsub() ? session.createTopic(props.getSendDestinationNameRoot() + "_" + uniqueId)
                              : session.createQueue(props.getSendDestinationNameRoot() + "_" + uniqueId);

        MessageConsumer consumer =
            props.getReceiverConsumerBind()
            ? ((props.getDurableSubscription() && props.getPubsub())
                ? session.createDurableSubscriber((Topic) destination, "testsub") : session.createConsumer(destination))
            : null;

        MessageMonitor messageMonitor = new MessageMonitor();

        if (consumer != null)
        {
            consumer.setMessageListener(messageMonitor);
        }

        if (!props.getReceiverConsumerActive() && (consumer != null))
        {
            consumer.close();
        }

        return new CircuitEndBase(producer, consumer, session, messageMonitor, null);
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
     * Gets the local publishing circuit end, for direct manipulation.
     *
     * @return The local publishing circuit end.
     */
    public CircuitEnd getLocalPublisherCircuitEnd()
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
     * Gets the local receiving circuit end, for direct manipulation.
     *
     * @return The local receiving circuit end.
     */
    public CircuitEnd getLocalReceiverCircuitEnd()
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
            throw new RuntimeException("Got JMSException during close:" + e.getMessage(), e);
        }
    }

    /**
     * Sends a message on the test circuit. The exact nature of the message sent is controlled by the test parameters.
     */
    protected void send()
    {
        boolean transactional = testProps.getPropertyAsBoolean(MessagingTestConfigProperties.TRANSACTED_PROPNAME);

        // Send an immediate message through the publisher and ensure that it results in a JMSException.
        try
        {
            CircuitEnd end = getLocalPublisherCircuitEnd();

            end.send(createTestMessage(end));

            if (transactional)
            {
                end.getSession().commit();
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
        TestUtils.pause(500L);

        // Request a status report.
        check();

        // Clean up the publisher/receivers/controlSession/connections.
        close();

        // Apply all of the requested assertions, keeping record of any that fail.
        List<Assertion> failures = applyAssertions(assertions);

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
     * Gets the exception monitor for the publishing ends controlSession.
     *
     * @return The exception monitor for the publishing ends controlSession.
     */
    public ExceptionMonitor getExceptionMonitor()
    {
        return exceptionMonitor;
    }
}
