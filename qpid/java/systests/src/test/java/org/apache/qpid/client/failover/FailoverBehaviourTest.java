/*
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
 */
package org.apache.qpid.client.failover;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionListener;
import org.apache.qpid.jms.FailoverPolicy;
import org.apache.qpid.test.utils.FailoverBaseCase;
import org.apache.qpid.url.URLSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TransactionRolledBackException;
import javax.naming.NamingException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test suite to test all possible failover corner cases
 */
public class FailoverBehaviourTest extends FailoverBaseCase implements ConnectionListener, ExceptionListener
{
    protected static final Logger _LOGGER = LoggerFactory.getLogger(FailoverBehaviourTest.class);

    private static final String TEST_MESSAGE_FORMAT = "test message {0}";

    /** Indicates whether tests are run against clustered broker */
    private static boolean CLUSTERED = Boolean.getBoolean("profile.clustered");

    /** Default number of messages to send before failover */
    private static final int DEFAULT_NUMBER_OF_MESSAGES = 40;

    /** Actual number of messages to send before failover */
    protected int _messageNumber = Integer.getInteger("profile.failoverMsgCount", DEFAULT_NUMBER_OF_MESSAGES);

    /** Test connection */
    protected Connection _connection;

    /**
     * Failover completion latch is used to wait till connectivity to broker is
     * restored
     */
    private CountDownLatch _failoverComplete;

    /**
     * Consumer session
     */
    private Session _consumerSession;

    /**
     * Test destination
     */
    private Destination _destination;

    /**
     * Consumer
     */
    private MessageConsumer _consumer;

    /**
     * Producer session
     */
    private Session _producerSession;

    /**
     * Producer
     */
    private MessageProducer _producer;

    /**
     * Holds exception sent into {@link ExceptionListener} on failover
     */
    private JMSException _exceptionListenerException;

    /**
     * Latch to check that failover mutex is hold by a failover thread
     */
    private CountDownLatch _failoverStarted;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _connection = getConnection();
        _connection.setExceptionListener(this);
        ((AMQConnection) _connection).setConnectionListener(this);
        _failoverComplete = new CountDownLatch(1);
        _failoverStarted = new CountDownLatch(1);
    }

    /**
     * Test whether MessageProducer can successfully publish messages after
     * failover and rollback transaction
     */
    public void testMessageProducingAndRollbackAfterFailover() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);
        produceMessages();
        causeFailure();

        assertFailoverException();
        // producer should be able to send messages after failover
        _producer.send(_producerSession.createTextMessage("test message " + _messageNumber));

        // rollback after failover
        _producerSession.rollback();

        // tests whether sending and committing is working after failover
        produceMessages();
        _producerSession.commit();

        // tests whether receiving and committing is working after failover
        consumeMessages();
        _consumerSession.commit();
    }

    /**
     * Test whether {@link TransactionRolledBackException} is thrown on commit
     * of dirty transacted session after failover.
     * <p>
     * Verifies whether second after failover commit is successful.
     */
    public void testTransactionRolledBackExceptionThrownOnCommitAfterFailoverOnProducingMessages() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);
        produceMessages();
        causeFailure();

        assertFailoverException();

        // producer should be able to send messages after failover
        _producer.send(_producerSession.createTextMessage("test message " + _messageNumber));

        try
        {
            _producerSession.commit();
            fail("TransactionRolledBackException is expected on commit after failover with dirty session!");
        }
        catch (JMSException t)
        {
            assertTrue("Expected TransactionRolledBackException but thrown " + t,
                    t instanceof TransactionRolledBackException);
        }

        // simulate process of user replaying the transaction
        produceMessages("replayed test message {0}", _messageNumber, false);

        // no exception should be thrown
        _producerSession.commit();

        // only messages sent after rollback should be received
        consumeMessages("replayed test message {0}", _messageNumber);

        // no exception should be thrown
        _consumerSession.commit();
    }

    /**
     * Tests JMSException is not thrown on commit with a clean session after
     * failover
     */
    public void testNoJMSExceptionThrownOnCommitAfterFailoverWithCleanProducerSession() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);

        causeFailure();

        assertFailoverException();

        // should not throw an exception for a clean session
        _producerSession.commit();

        // tests whether sending and committing is working after failover
        produceMessages();
        _producerSession.commit();

        // tests whether receiving and committing is working after failover
        consumeMessages();
        _consumerSession.commit();
    }

    /**
     * Tests {@link TransactionRolledBackException} is thrown on commit of dirty
     * transacted session after failover.
     * <p>
     * Verifies whether second after failover commit is successful.
     */
    public void testTransactionRolledBackExceptionThrownOnCommitAfterFailoverOnMessageReceiving() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);
        produceMessages();
        _producerSession.commit();

        // receive messages but do not commit
        consumeMessages();

        causeFailure();

        assertFailoverException();

        try
        {
            // should throw TransactionRolledBackException
            _consumerSession.commit();
            fail("TransactionRolledBackException is expected on commit after failover");
        }
        catch (Exception t)
        {
            assertTrue("Expected TransactionRolledBackException but thrown " + t,
                    t instanceof TransactionRolledBackException);
        }

        resendMessagesIfNecessary();

        // consume messages successfully
        consumeMessages();
        _consumerSession.commit();
    }

    /**
     * Tests JMSException is not thrown on commit with a clean session after failover
     */
    public void testNoJMSExceptionThrownOnCommitAfterFailoverWithCleanConsumerSession() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);
        produceMessages();
        _producerSession.commit();

        consumeMessages();
        _consumerSession.commit();

        causeFailure();

        assertFailoverException();

        // should not throw an exception with a clean consumer session
        _consumerSession.commit();
    }

    /**
     * Test that TransactionRolledBackException is thrown on commit of
     * dirty session in asynchronous consumer after failover.
     */
    public void testTransactionRolledBackExceptionThrownOnCommitAfterFailoverOnReceivingMessagesAsynchronously()
    throws Exception
    {
        init(Session.SESSION_TRANSACTED, false);
        FailoverTestMessageListener ml = new FailoverTestMessageListener();
        _consumer.setMessageListener(ml);

        _connection.start();

        produceMessages();
        _producerSession.commit();

        // wait for message receiving
        ml.awaitForEnd();

        assertEquals("Received unexpected number of messages!", _messageNumber, ml.getMessageCounter());

        // assert messages
        int counter = 0;
        for (Message message : ml.getReceivedMessages())
        {
            assertReceivedMessage(message, TEST_MESSAGE_FORMAT, counter++);
        }
        ml.reset();

        causeFailure();
        assertFailoverException();


        try
        {
            _consumerSession.commit();
            fail("TransactionRolledBackException should be thrown!");
        }
        catch (TransactionRolledBackException e)
        {
            // that is what is expected
        }

        resendMessagesIfNecessary();

        // wait for message receiving
        ml.awaitForEnd();

        assertEquals("Received unexpected number of messages!", _messageNumber, ml.getMessageCounter());

        // assert messages
        counter = 0;
        for (Message message : ml.getReceivedMessages())
        {
            assertReceivedMessage(message, TEST_MESSAGE_FORMAT, counter++);
        }

        // commit again. It should be successful
        _consumerSession.commit();
    }

    /**
     * Test that {@link Session#rollback()} does not throw exception after failover
     * and that we are able to consume messages.
     */
    public void testRollbackAfterFailover() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);

        produceMessages();
        _producerSession.commit();

        consumeMessages();

        causeFailure();

        assertFailoverException();

        _consumerSession.rollback();

        resendMessagesIfNecessary();

        // tests whether receiving and committing is working after failover
        consumeMessages();
        _consumerSession.commit();
    }

    /**
     * Test that {@link Session#rollback()} does not throw exception after receiving further messages
     * after failover, and we can receive published messages after rollback.
     */
    public void testRollbackAfterReceivingAfterFailover() throws Exception
    {
        init(Session.SESSION_TRANSACTED, true);

        produceMessages();
        _producerSession.commit();

        consumeMessages();
        causeFailure();

        assertFailoverException();

        resendMessagesIfNecessary();

        consumeMessages();

        _consumerSession.rollback();

        // tests whether receiving and committing is working after failover
        consumeMessages();
        _consumerSession.commit();
    }

    /**
     * Test that {@link Session#recover()} does not throw an exception after failover
     * and that we can consume messages after recover.
     */
    public void testRecoverAfterFailover() throws Exception
    {
        init(Session.CLIENT_ACKNOWLEDGE, true);

        produceMessages();

        // consume messages but do not acknowledge them
        consumeMessages();

        causeFailure();

        assertFailoverException();

        _consumerSession.recover();

        resendMessagesIfNecessary();

        // tests whether receiving and acknowledgment is working after recover
        Message lastMessage = consumeMessages();
        lastMessage.acknowledge();
    }

    /**
     * Test that receiving more messages after failover and then calling
     * {@link Session#recover()} does not throw an exception
     * and that we can consume messages after recover.
     */
    public void testRecoverWithConsumedMessagesAfterFailover() throws Exception
    {
        init(Session.CLIENT_ACKNOWLEDGE, true);

        produceMessages();

        // consume messages but do not acknowledge them
        consumeMessages();

        causeFailure();

        assertFailoverException();

        // publishing should work after failover
        resendMessagesIfNecessary();

        // consume messages again on a dirty session
        consumeMessages();

        // recover should successfully restore session
        _consumerSession.recover();

        // tests whether receiving and acknowledgment is working after recover
        Message lastMessage = consumeMessages();
        lastMessage.acknowledge();
    }

    /**
     * Test that first call to {@link Message#acknowledge()} after failover
     * throws a JMSEXception if session is dirty.
     */
    public void testAcknowledgeAfterFailover() throws Exception
    {
        init(Session.CLIENT_ACKNOWLEDGE, true);

        produceMessages();

        // consume messages but do not acknowledge them
        Message lastMessage = consumeMessages();
        causeFailure();

        assertFailoverException();

        try
        {
            // an implicit recover performed when acknowledge throws an exception due to failover
            lastMessage.acknowledge();
            fail("JMSException should be thrown");
        }
        catch (JMSException t)
        {
            // TODO: assert error code and/or expected exception type
        }

        resendMessagesIfNecessary();

        // tests whether receiving and acknowledgment is working after recover
        lastMessage = consumeMessages();
        lastMessage.acknowledge();
    }

    /**
     * Test that calling acknowledge before failover leaves the session
     * clean for use after failover.
     */
    public void testAcknowledgeBeforeFailover() throws Exception
    {
        init(Session.CLIENT_ACKNOWLEDGE, true);

        produceMessages();

        // consume messages and acknowledge them
        Message lastMessage = consumeMessages();
        lastMessage.acknowledge();

        causeFailure();

        assertFailoverException();

        produceMessages();

        // tests whether receiving and acknowledgment is working after recover
        lastMessage = consumeMessages();
        lastMessage.acknowledge();
    }

    /**
     * Test that receiving of messages after failover prior to calling
     * {@link Message#acknowledge()} still results in acknowledge throwing an exception.
     */
    public void testAcknowledgeAfterMessageReceivingAfterFailover() throws Exception
    {
        init(Session.CLIENT_ACKNOWLEDGE, true);

        produceMessages();

        // consume messages but do not acknowledge them
        consumeMessages();
        causeFailure();

        assertFailoverException();

        resendMessagesIfNecessary();

        // consume again on dirty session
        Message lastMessage = consumeMessages();
        try
        {
            // an implicit recover performed when acknowledge throws an exception due to failover
            lastMessage.acknowledge();
            fail("JMSException should be thrown");
        }
        catch (JMSException t)
        {
            // TODO: assert error code and/or expected exception type
        }

        // tests whether receiving and acknowledgment is working on a clean session
        lastMessage = consumeMessages();
        lastMessage.acknowledge();
    }

    /**
     * Tests that call to {@link Message#acknowledge()} after failover throws an exception in asynchronous consumer
     * and we can consume messages after acknowledge.
     */
    public void testAcknowledgeAfterFailoverForAsynchronousConsumer() throws Exception
    {
        init(Session.CLIENT_ACKNOWLEDGE, false);
        FailoverTestMessageListener ml = new FailoverTestMessageListener();
        _consumer.setMessageListener(ml);
        _connection.start();

        produceMessages();

        // wait for message receiving
        ml.awaitForEnd();

        assertEquals("Received unexpected number of messages!", _messageNumber, ml.getMessageCounter());

        // assert messages
        int counter = 0;
        Message currentMessage = null;
        for (Message message : ml.getReceivedMessages())
        {
            assertReceivedMessage(message, TEST_MESSAGE_FORMAT, counter++);
            currentMessage = message;
        }
        ml.reset();

        causeFailure();
        assertFailoverException();


        try
        {
            currentMessage.acknowledge();
            fail("JMSException should be thrown!");
        }
        catch (JMSException e)
        {
            // TODO: assert error code and/or expected exception type
        }

        resendMessagesIfNecessary();

        // wait for message receiving
        ml.awaitForEnd();

        assertEquals("Received unexpected number of messages!", _messageNumber, ml.getMessageCounter());

        // assert messages
        counter = 0;
        for (Message message : ml.getReceivedMessages())
        {
            assertReceivedMessage(message, TEST_MESSAGE_FORMAT, counter++);
            currentMessage = message;
        }

        // acknowledge again. It should be successful
        currentMessage.acknowledge();
    }

    /**
     * Test whether {@link Session#recover()} works as expected after failover
     * in AA mode.
     */
    public void testRecoverAfterFailoverInAutoAcknowledgeMode() throws Exception
    {
        init(Session.AUTO_ACKNOWLEDGE, true);

        produceMessages();

        // receive first message in order to start a dispatcher thread
        Message receivedMessage = _consumer.receive(1000l);
        assertReceivedMessage(receivedMessage, TEST_MESSAGE_FORMAT, 0);

        causeFailure();

        assertFailoverException();

        _consumerSession.recover();

        resendMessagesIfNecessary();

        // tests whether receiving is working after recover
        consumeMessages();
    }

    public void testClientAcknowledgedSessionCloseAfterFailover() throws Exception
    {
        sessionCloseAfterFailoverImpl(Session.CLIENT_ACKNOWLEDGE);
    }

    public void testTransactedSessionCloseAfterFailover() throws Exception
    {
        sessionCloseAfterFailoverImpl(Session.SESSION_TRANSACTED);
    }

    public void testAutoAcknowledgedSessionCloseAfterFailover() throws Exception
    {
        sessionCloseAfterFailoverImpl(Session.AUTO_ACKNOWLEDGE);
    }

    public void testPublishAutoAcknowledgedWhileFailover() throws Exception
    {
        publishWhileFailingOver(Session.AUTO_ACKNOWLEDGE);
    }

    public void testPublishClientAcknowledgedWhileFailover() throws Exception
    {
        Message receivedMessage = publishWhileFailingOver(Session.CLIENT_ACKNOWLEDGE);
        receivedMessage.acknowledge();
    }

    public void testPublishTransactedAcknowledgedWhileFailover() throws Exception
    {
        publishWhileFailingOver(Session.SESSION_TRANSACTED);
        _consumerSession.commit();
    }

    public void testPublishAutoAcknowledgedWithFailoverMutex() throws Exception
    {
        publishWithFailoverMutex(Session.AUTO_ACKNOWLEDGE);
    }

    public void testPublishClientAcknowledgedWithFailoverMutex() throws Exception
    {
        publishWithFailoverMutex(Session.CLIENT_ACKNOWLEDGE);

    }

    public void testPublishTransactedAcknowledgedWithFailoverMutex() throws Exception
    {
        publishWithFailoverMutex(Session.SESSION_TRANSACTED);
    }

    public void testClientAcknowledgedSessionCloseWhileFailover() throws Exception
    {
        sessionCloseWhileFailoverImpl(Session.CLIENT_ACKNOWLEDGE);
    }

    public void testTransactedSessionCloseWhileFailover() throws Exception
    {
        sessionCloseWhileFailoverImpl(Session.SESSION_TRANSACTED);
    }

    public void testAutoAcknowledgedSessionCloseWhileFailover() throws Exception
    {
        sessionCloseWhileFailoverImpl(Session.AUTO_ACKNOWLEDGE);
    }

    public void testClientAcknowledgedQueueBrowserCloseWhileFailover() throws Exception
    {
        browserCloseWhileFailoverImpl(Session.CLIENT_ACKNOWLEDGE);
    }

    public void testTransactedQueueBrowserCloseWhileFailover() throws Exception
    {
        browserCloseWhileFailoverImpl(Session.SESSION_TRANSACTED);
    }

    public void testAutoAcknowledgedQueueBrowserCloseWhileFailover() throws Exception
    {
        browserCloseWhileFailoverImpl(Session.AUTO_ACKNOWLEDGE);
    }

    public void testKillBrokerFailoverWhilstPublishingInFlight() throws Exception
    {
        doFailoverWhilstPublishingInFlight(true);
    }

    public void testStopBrokerFailoverWhilstPublishingInFlight() throws Exception
    {
        doFailoverWhilstPublishingInFlight(false);
    }

    private void doFailoverWhilstPublishingInFlight(boolean hardKill) throws JMSException, InterruptedException
    {
        init(Session.SESSION_TRANSACTED, false);

        final int numberOfMessages = 200;

        final CountDownLatch halfWay = new CountDownLatch(1);
        final CountDownLatch allDone = new CountDownLatch(1);
        final AtomicReference<Exception> exception = new AtomicReference<>();

        Runnable producerRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                Thread.currentThread().setName("ProducingThread");

                try
                {
                    for(int i=0; i< numberOfMessages; i++)
                    {
                        boolean success = false;
                        while(!success)
                        {
                            try
                            {
                                Message message = _producerSession.createMessage();
                                message.setIntProperty("msgNum", i);
                                _producer.send(message);
                                _producerSession.commit();
                                success = true;
                            }
                            catch (javax.jms.IllegalStateException e)
                            {
                                // fail - failover should not leave a JMS object in an illegal state
                                throw e;
                            }
                            catch (JMSException e)
                            {
                                // OK we will be failing over
                                _logger.debug("Got JMS exception, probably just failing over", e);
                            }
                        }

                        if (i > numberOfMessages / 2 && halfWay.getCount() == 1)
                        {
                            halfWay.countDown();
                        }
                    }

                    allDone.countDown();
                }
                catch (Exception e)
                {
                    exception.set(e);
                }
            }
        };

        Thread producerThread = new Thread(producerRunnable);
        producerThread.start();

        assertTrue("Didn't get to half way within timeout", halfWay.await(30000, TimeUnit.MILLISECONDS));

        if (hardKill)
        {
            _logger.debug("Killing the Broker");
            killBroker(getFailingPort());
        }
        else
        {
            _logger.debug("Stopping the Broker");
            stopBroker(getFailingPort());
        }

        if (exception.get() != null)
        {
            _logger.error("Unexpected exception from producer thread", exception.get());
        }
        assertNull("Producer thread should not have got an exception", exception.get());

        assertTrue("All producing work was not completed", allDone.await(30000, TimeUnit.MILLISECONDS));

        producerThread.join(30000);

        // Extra work to prove the session still okay
        assertNotNull(_producerSession.createTemporaryQueue());
    }


    private Message publishWhileFailingOver(int autoAcknowledge) throws JMSException, InterruptedException
    {
        setDelayedFailoverPolicy(5);
        init(autoAcknowledge, true);

        String text = MessageFormat.format(TEST_MESSAGE_FORMAT, 0);
        Message message = _producerSession.createTextMessage(text);

        failBroker(getFailingPort());

        if(!_failoverStarted.await(5, TimeUnit.SECONDS))
        {
            fail("Did not receieve notification failover had started");
        }

        _producer.send(message);

        if (_producerSession.getTransacted())
        {
            _producerSession.commit();
        }

        Message receivedMessage = _consumer.receive(1000l);
        assertReceivedMessage(receivedMessage, TEST_MESSAGE_FORMAT, 0);
        return receivedMessage;
    }

    private void publishWithFailoverMutex(int autoAcknowledge) throws JMSException, InterruptedException
    {
        setDelayedFailoverPolicy(5);
        init(autoAcknowledge, true);

        String text = MessageFormat.format(TEST_MESSAGE_FORMAT, 0);
        Message message = _producerSession.createTextMessage(text);

        AMQConnection connection = (AMQConnection)_connection;

        // holding failover mutex should prevent the failover from
        // proceeding before we try to send the message
        synchronized(connection.getFailoverMutex())
        {
            failBroker(getFailingPort());

            // wait to make sure that connection is lost
            while(!connection.isFailingOver())
            {
                Thread.sleep(25l);
            }

            try
            {
                _producer.send(message);
                fail("Sending should fail because connection was lost and failover has not yet completed");
            }
            catch(JMSException e)
            {
                // JMSException is expected
            }
        }
        // wait for failover completion, thus ensuring it actually
        //got started, before allowing the test to tear down
        awaitForFailoverCompletion(DEFAULT_FAILOVER_TIME);
     }

    /**
     * This test only tests 0-8/0-9/0-9-1 failover timeout
     */
    public void testFailoverHandlerTimeoutExpires() throws Exception
    {
        _connection.close();
        setTestSystemProperty("qpid.failover_method_timeout", "10000");
        AMQConnection connection = null;
        try
        {
            connection = createConnectionWithFailover();

            // holding failover mutex should prevent the failover from proceeding
            synchronized(connection.getFailoverMutex())
            {
                killBroker();
                startBroker();

                // sleep interval exceeds failover timeout interval
                Thread.sleep(11000l);
            }

            // allows the failover thread to proceed
            Thread.yield();
            assertFalse("Unexpected failover", _failoverComplete.await(2000l, TimeUnit.MILLISECONDS));
            assertTrue("Failover should not succeed due to timeout", connection.isClosed());
        }
        finally
        {
            if (connection != null)
            {
                connection.close();
            }
        }
    }

    public void testFailoverHandlerTimeoutReconnected() throws Exception
    {
        _connection.close();
        setTestSystemProperty("qpid.failover_method_timeout", "10000");
        AMQConnection connection = null;
        try
        {
            connection = createConnectionWithFailover();

            // holding failover mutex should prevent the failover from proceeding
            synchronized(connection.getFailoverMutex())
            {
                killBroker();
                startBroker();
            }

            // allows the failover thread to proceed
            Thread.yield();
            awaitForFailoverCompletion(DEFAULT_FAILOVER_TIME);
            assertFalse("Failover should restore connectivity", connection.isClosed());
        }
        finally
        {
            if (connection != null)
            {
                connection.close();
            }
        }
    }

    /**
     * Tests that the producer flow control flag is reset when failover occurs while
     * the producers are being blocked by the broker.
     *
     * Uses Java broker specific queue configuration to enabled PSFC.
     */
    public void testFlowControlFlagResetOnFailover() throws Exception
    {
        // we do not need the connection failing to second broker
        _connection.close();

        // make sure that failover timeout is bigger than flow control timeout
        setTestSystemProperty("qpid.failover_method_timeout", "60000");
        setTestSystemProperty("qpid.flow_control_wait_failure", "10000");

        AMQConnection connection = null;
        try
        {
            connection = createConnectionWithFailover();

            final Session producerSession = connection.createSession(true, Session.SESSION_TRANSACTED);
            final Queue queue = createAndBindQueueWithFlowControlEnabled(producerSession, getTestQueueName(), DEFAULT_MESSAGE_SIZE * 3, DEFAULT_MESSAGE_SIZE * 2);
            final AtomicInteger counter = new AtomicInteger();
            // try to send 5 messages (should block after 4)
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        MessageProducer producer = producerSession.createProducer(queue);
                        for (int i=0; i < 5; i++)
                        {
                            Message next = createNextMessage(producerSession, i);
                            producer.send(next);
                            producerSession.commit();
                            counter.incrementAndGet();
                        }
                    }
                    catch(Exception e)
                    {
                        // ignore
                    }
                }
            }).start();

            long limit= 30000l;
            long start = System.currentTimeMillis();

            // wait  until session is blocked
            while(!((AMQSession<?,?>)producerSession).isFlowBlocked() && System.currentTimeMillis() - start < limit)
            {
                Thread.sleep(100l);
            }

            assertTrue("Flow is not blocked", ((AMQSession<?, ?>) producerSession).isFlowBlocked());

            final int currentCounter = counter.get();
            assertTrue("Unexpected number of sent messages:" + currentCounter, currentCounter >=3);

            killBroker();
            startBroker();

            // allows the failover thread to proceed
            Thread.yield();
            awaitForFailoverCompletion(60000l);

            assertFalse("Flow is blocked", ((AMQSession<?, ?>) producerSession).isFlowBlocked());
        }
        finally
        {
            if (connection != null)
            {
                connection.close();
            }
        }
    }

    private Queue createAndBindQueueWithFlowControlEnabled(Session session, String queueName, int capacity, int resumeCapacity) throws Exception
    {
        final Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("x-qpid-capacity", capacity);
        arguments.put("x-qpid-flow-resume-capacity", resumeCapacity);
        ((AMQSession<?, ?>) session).createQueue(new AMQShortString(queueName), false, true, false, arguments);
        Queue queue = session.createQueue("direct://amq.direct/" + queueName + "/" + queueName + "?durable='" + true
                + "'&autodelete='" + false + "'");
        ((AMQSession<?, ?>) session).declareAndBind((AMQDestination) queue);
        return queue;
    }

    private AMQConnection createConnectionWithFailover() throws NamingException, JMSException, URLSyntaxException
    {
        BrokerDetails origBrokerDetails = ((AMQConnectionFactory) getConnectionFactory("default")).getConnectionURL().getBrokerDetails(0);

        String retries = "200";
        String connectdelay = "1000";
        String cycleCount = "2";

        String newUrlFormat="amqp://username:password@clientid/test?brokerlist=" +
                            "'tcp://%s:%s?retries='%s'&connectdelay='%s''&failover='singlebroker?cyclecount='%s''";

        String newUrl = String.format(newUrlFormat, origBrokerDetails.getHost(), origBrokerDetails.getPort(),
                                                    retries, connectdelay, cycleCount);

        ConnectionFactory connectionFactory = new AMQConnectionFactory(newUrl);
        AMQConnection connection = (AMQConnection) connectionFactory.createConnection("admin", "admin");
        connection.setConnectionListener(this);
        return connection;
    }

    /**
     * Tests {@link Session#close()} for session with given acknowledge mode
     * to ensure that close works after failover.
     *
     * @param acknowledgeMode session acknowledge mode
     * @throws JMSException
     */
    private void sessionCloseAfterFailoverImpl(int acknowledgeMode) throws JMSException
    {
        init(acknowledgeMode, true);
        produceMessages(TEST_MESSAGE_FORMAT, _messageNumber, false);
        if (acknowledgeMode == Session.SESSION_TRANSACTED)
        {
            _producerSession.commit();
        }

        // intentionally receive message but do not commit or acknowledge it in
        // case of transacted or CLIENT_ACK session
        Message receivedMessage = _consumer.receive(1000l);
        assertReceivedMessage(receivedMessage, TEST_MESSAGE_FORMAT, 0);

        causeFailure();

        assertFailoverException();

        // for transacted/client_ack session
        // no exception should be thrown but transaction should be automatically
        // rolled back
        _consumerSession.close();
    }

    /**
     * A helper method to instantiate produce and consumer sessions, producer
     * and consumer.
     *
     * @param acknowledgeMode
     *            acknowledge mode
     * @param startConnection
     *            indicates whether connection should be started
     * @throws JMSException
     */
    private void init(int acknowledgeMode, boolean startConnection) throws JMSException
    {
        boolean isTransacted = acknowledgeMode == Session.SESSION_TRANSACTED ? true : false;

        _consumerSession = _connection.createSession(isTransacted, acknowledgeMode);
        _destination = createDestination(_consumerSession);
        _consumer = _consumerSession.createConsumer(_destination);

        if (startConnection)
        {
            _connection.start();
        }

        _producerSession = _connection.createSession(isTransacted, acknowledgeMode);
        _producer = _producerSession.createProducer(_destination);

    }

    protected Destination createDestination(Session session) throws JMSException
    {
        return session.createQueue(getTestQueueName() + "_" + System.currentTimeMillis());
    }

    /**
     * Resends messages if reconnected to a non-clustered broker
     *
     * @throws JMSException
     */
    private void resendMessagesIfNecessary() throws JMSException
    {
        if (!CLUSTERED)
        {
            // assert that a new broker does not have messages on a queue
            if (_consumer.getMessageListener() == null)
            {
                Message message = _consumer.receive(100l);
                assertNull("Received a message after failover with non-clustered broker!", message);
            }
            // re-sending messages if reconnected to a non-clustered broker
            produceMessages(true);
        }
    }

    /**
     * Produces a default number of messages with default text content into test
     * queue
     *
     * @throws JMSException
     */
    private void produceMessages() throws JMSException
    {
        produceMessages(false);
    }

    private void produceMessages(boolean seperateProducer) throws JMSException
    {
        produceMessages(TEST_MESSAGE_FORMAT, _messageNumber, seperateProducer);
    }

    /**
     * Consumes a default number of messages and asserts their content.
     *
     * @return last consumed message
     * @throws JMSException
     */
    private Message consumeMessages() throws JMSException
    {
        return consumeMessages(TEST_MESSAGE_FORMAT, _messageNumber);
    }

    /**
     * Produces given number of text messages with content matching given
     * content pattern
     *
     * @param messagePattern message content pattern
     * @param messageNumber  number of messages to send
     * @param standaloneProducer whether to use the existing producer or a new one.
     * @throws JMSException
     */
    private void produceMessages(String messagePattern, int messageNumber, boolean standaloneProducer) throws JMSException
    {
        Session producerSession;
        MessageProducer producer;

        if(standaloneProducer)
        {
            producerSession = _connection.createSession(true, Session.SESSION_TRANSACTED);
            producer = producerSession.createProducer(_destination);
        }
        else
        {
            producerSession = _producerSession;
            producer = _producer;
        }

        for (int i = 0; i < messageNumber; i++)
        {
            String text = MessageFormat.format(messagePattern, i);
            Message message = producerSession.createTextMessage(text);
            producer.send(message);
            _LOGGER.debug("Test message number " + i + " produced with text = " + text + ", and JMSMessageID = " + message.getJMSMessageID());
        }

        if(standaloneProducer)
        {
            producerSession.commit();
        }
    }

    /**
     * Consumes given number of text messages and asserts that their content
     * matches given pattern
     *
     * @param messagePattern
     *            messages content pattern
     * @param messageNumber
     *            message number to received
     * @return last consumed message
     * @throws JMSException
     */
    private Message consumeMessages(String messagePattern, int messageNumber) throws JMSException
    {
        Message receivedMesssage = null;
        for (int i = 0; i < messageNumber; i++)
        {
            receivedMesssage = _consumer.receive(1000l);
            assertReceivedMessage(receivedMesssage, messagePattern, i);
        }
        return receivedMesssage;
    }

    /**
     * Asserts received message
     *
     * @param receivedMessage
     *            received message
     * @param messagePattern
     *            messages content pattern
     * @param messageIndex
     *            message index
     */
    private void assertReceivedMessage(Message receivedMessage, String messagePattern, int messageIndex) throws JMSException
    {
        assertNotNull("Expected message [" + messageIndex + "] is not received!", receivedMessage);
        assertTrue("Failure to receive message [" + messageIndex + "], expected TextMessage but received "
                + receivedMessage, receivedMessage instanceof TextMessage);
        String expectedText = MessageFormat.format(messagePattern, messageIndex);
        String receivedText = null;
        try
        {
            receivedText = ((TextMessage) receivedMessage).getText();
        }
        catch (JMSException e)
        {
            fail("JMSException occured while getting message text:" + e.getMessage());
        }
        _LOGGER.debug("Test message number " + messageIndex + " consumed with text = " + receivedText + ", and JMSMessageID = " + receivedMessage.getJMSMessageID());
        assertEquals("Failover is broken! Expected [" + expectedText + "] but got [" + receivedText + "]",
                expectedText, receivedText);
    }

    /**
     * Causes failover and waits till connection is re-established.
     */
    private void causeFailure()
    {
        causeFailure(getFailingPort(), DEFAULT_FAILOVER_TIME * 2);
    }

    /**
     * Causes failover by stopping broker on given port and waits till
     * connection is re-established during given time interval.
     *
     * @param port
     *            broker port
     * @param delay
     *            time interval to wait for connection re-establishement
     */
    private void causeFailure(int port, long delay)
    {
        failBroker(port);

        awaitForFailoverCompletion(delay);
    }

    private void awaitForFailoverCompletion(long delay)
    {
        _logger.info("Awaiting Failover completion..");
        try
        {
            if (!_failoverComplete.await(delay, TimeUnit.MILLISECONDS))
            {
                fail("Failover did not complete");
            }
        }
        catch (InterruptedException e)
        {
            fail("Test was interrupted:" + e.getMessage());
        }
    }

    private void assertFailoverException()
    {
        // TODO: assert exception is received (once implemented)
        // along with error code and/or expected exception type
    }

    @Override
    public void bytesSent(long count)
    {
    }

    @Override
    public void bytesReceived(long count)
    {
    }

    @Override
    public boolean preFailover(boolean redirect)
    {
        _failoverStarted.countDown();
        return true;
    }

    @Override
    public boolean preResubscribe()
    {
        return true;
    }

    @Override
    public void failoverComplete()
    {
        _failoverComplete.countDown();
    }

    @Override
    public void onException(JMSException e)
    {
        _exceptionListenerException = e;
    }

    /**
     * Causes 1 second delay before reconnect in order to test whether JMS
     * methods block while failover is in progress
     */
    private static class DelayingFailoverPolicy extends FailoverPolicy
    {

        private CountDownLatch _suspendLatch;
        private long _delay;

        public DelayingFailoverPolicy(AMQConnection connection, long delay)
        {
            super(connection.getConnectionURL(), connection);
            _suspendLatch = new CountDownLatch(1);
            _delay = delay;
        }

        public void attainedConnection()
        {
            try
            {
                _suspendLatch.await(_delay, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                // continue
            }
            super.attainedConnection();
        }

    }


    private class FailoverTestMessageListener implements MessageListener
    {
        // message counter
        private AtomicInteger _counter = new AtomicInteger();

        private List<Message> _receivedMessage = new ArrayList<Message>();

        private volatile CountDownLatch _endLatch;

        public FailoverTestMessageListener() throws JMSException
        {
            _endLatch = new CountDownLatch(1);
        }

        @Override
        public void onMessage(Message message)
        {
            _receivedMessage.add(message);
            if (_counter.incrementAndGet() % _messageNumber == 0)
            {
                _endLatch.countDown();
            }
        }

        public void reset()
        {
            _receivedMessage.clear();
            _endLatch = new CountDownLatch(1);
            _counter.set(0);
        }

        public List<Message> getReceivedMessages()
        {
            return _receivedMessage;
        }

        public Object awaitForEnd() throws InterruptedException
        {
            return _endLatch.await((long) _messageNumber, TimeUnit.SECONDS);
        }

        public int getMessageCounter()
        {
            return _counter.get();
        }
    }

    /**
     * Tests {@link Session#close()} for session with given acknowledge mode
     * to ensure that it blocks until failover implementation restores connection.
     *
     * @param acknowledgeMode session acknowledge mode
     * @throws JMSException
     */
    private void sessionCloseWhileFailoverImpl(int acknowledgeMode) throws Exception
    {
        initDelayedFailover(acknowledgeMode);

        // intentionally receive message but not commit or acknowledge it in
        // case of transacted or CLIENT_ACK session
        Message receivedMessage = _consumer.receive(1000l);
        assertReceivedMessage(receivedMessage, TEST_MESSAGE_FORMAT, 0);

        failBroker(getFailingPort());

        // wait until failover is started
        _failoverStarted.await(5, TimeUnit.SECONDS);

        // test whether session#close blocks while failover is in progress
        _consumerSession.close();

        assertTrue("Failover has not completed yet but session was closed", _failoverComplete.await(5, TimeUnit.SECONDS));

        assertFailoverException();
    }

    /**
     * A helper method to instantiate {@link QueueBrowser} and publish test messages on a test queue for further browsing.
     *
     * @param acknowledgeMode session acknowledge mode
     * @return queue browser
     * @throws JMSException
     */
    private QueueBrowser prepareQueueBrowser(int acknowledgeMode) throws JMSException, AMQException
    {
        init(acknowledgeMode, false);
        _consumer.close();
        _connection.start();

        produceMessages(TEST_MESSAGE_FORMAT, _messageNumber, false);
        if (acknowledgeMode == Session.SESSION_TRANSACTED)
        {
            _producerSession.commit();
        }
        else
        {
            ((AMQSession)_producerSession).sync();
        }

        QueueBrowser browser = _consumerSession.createBrowser((Queue) _destination);
        return browser;
    }

    /**
     * Tests {@link QueueBrowser#close()} for session with given acknowledge mode
     * to ensure that it blocks until failover implementation restores connection.
     *
     * @param acknowledgeMode session acknowledge mode
     * @throws JMSException
     */
    private void browserCloseWhileFailoverImpl(int acknowledgeMode) throws Exception
    {
        QueueBrowser browser = prepareQueueBrowser(acknowledgeMode);

        @SuppressWarnings("unchecked")
        Enumeration<Message> messages = browser.getEnumeration();
        Message receivedMessage = (Message) messages.nextElement();
        assertReceivedMessage(receivedMessage, TEST_MESSAGE_FORMAT, 0);

        failBroker(getFailingPort());

        // wait until failover is started
        _failoverStarted.await(5, TimeUnit.SECONDS);

        browser.close();

        assertTrue("Failover has not completed yet but browser was closed", _failoverComplete.await(5, TimeUnit.SECONDS));

        assertFailoverException();
    }

    private DelayingFailoverPolicy initDelayedFailover(int acknowledgeMode) throws JMSException
    {
        DelayingFailoverPolicy failoverPolicy = setDelayedFailoverPolicy();
        init(acknowledgeMode, true);
        produceMessages(TEST_MESSAGE_FORMAT, _messageNumber, false);
        if (acknowledgeMode == Session.SESSION_TRANSACTED)
        {
            _producerSession.commit();
        }
        return failoverPolicy;
    }

    private DelayingFailoverPolicy setDelayedFailoverPolicy()
    {
        return setDelayedFailoverPolicy(2);
    }

    private DelayingFailoverPolicy setDelayedFailoverPolicy(long delay)
    {
        AMQConnection amqConnection = (AMQConnection) _connection;
        DelayingFailoverPolicy failoverPolicy = new DelayingFailoverPolicy(amqConnection, delay);
        ((AMQConnection) _connection).setFailoverPolicy(failoverPolicy);
        return failoverPolicy;
    }

    @Override
    public void failBroker(int port)
    {
        killBroker(port);
    }

}
