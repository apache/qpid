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
package org.apache.qpid.systest.management.jmx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.naming.NamingException;

import org.apache.commons.lang.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.queue.NotificationCheckTest;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.queue.StandardQueueImpl;
import org.apache.qpid.test.client.destination.AddressBasedDestinationTest;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Tests the JMX API for the Managed Queue.
 *
 */
public class QueueManagementTest extends QpidBrokerTestCase
{

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueManagementTest.class);

    private static final String VIRTUAL_HOST = "test";
    private static final String TEST_QUEUE_DESCRIPTION = "my description";

    private JMXTestUtils _jmxUtils;
    private Connection _connection;
    private Session _session;

    private String _sourceQueueName;
    private String _destinationQueueName;
    private Destination _sourceQueue;
    private Destination _destinationQueue;
    private ManagedQueue _managedSourceQueue;
    private ManagedQueue _managedDestinationQueue;

    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        _jmxUtils = new JMXTestUtils(this);

        super.setUp();
        _sourceQueueName = getTestQueueName() + "_src";
        _destinationQueueName = getTestQueueName() + "_dest";

        createConnectionAndSession();

        _sourceQueue = _session.createQueue(_sourceQueueName);
        _destinationQueue = _session.createQueue(_destinationQueueName);
        createQueueOnBroker(_sourceQueue);
        createQueueOnBroker(_destinationQueue);

        _jmxUtils.open();

        createManagementInterfacesForQueues();
    }

    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    public void testQueueAttributes() throws Exception
    {
        Queue queue = _session.createQueue(getTestQueueName());
        createQueueOnBroker(queue);

        final String queueName = queue.getQueueName();

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals("Unexpected name", queueName, managedQueue.getName());
        assertEquals("Unexpected queue type", "standard", managedQueue.getQueueType());
    }

    public void testExclusiveQueueHasJmsClientIdAsOwner() throws Exception
    {
        final String subName = "testOwner";
        _session.createDurableSubscriber(getTestTopic(), subName);

        final String queueName = _connection.getClientID() + ":" + subName;

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertNotNull(_connection.getClientID());
        assertEquals("Unexpected owner", _connection.getClientID(), managedQueue.getOwner());
    }

    public void testNonExclusiveQueueHasNoOwner() throws Exception
    {
        Queue nonExclusiveQueue = _session.createQueue(getTestQueueName());
        createQueueOnBroker(nonExclusiveQueue);

        final String queueName = nonExclusiveQueue.getQueueName();

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertNull("Unexpected owner", managedQueue.getOwner());
    }

    public void testSetNewQueueDescriptionOnExistingQueue() throws Exception
    {
        Queue queue = _session.createQueue(getTestQueueName());
        createQueueOnBroker(queue);

        final String queueName = queue.getQueueName();

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertNull("Unexpected description", managedQueue.getDescription());

        managedQueue.setDescription(TEST_QUEUE_DESCRIPTION);
        assertEquals(TEST_QUEUE_DESCRIPTION, managedQueue.getDescription());
    }

    public void testNewQueueWithDescription() throws Exception
    {
        String queueName = getTestQueueName();
        Map<String, Object> arguments = Collections.singletonMap(QueueArgumentsConverter.X_QPID_DESCRIPTION, (Object)TEST_QUEUE_DESCRIPTION);
        ((AMQSession<?, ?>)_session).createQueue(AMQShortString.valueOf(queueName), false, true, false, arguments);

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals(TEST_QUEUE_DESCRIPTION, managedQueue.getDescription());
    }

    /**
     * Requires persistent store.
     */
    public void testQueueDescriptionSurvivesRestart() throws Exception
    {
        String queueName = getTestQueueName();
        Map<String, Object> arguments = Collections.singletonMap(QueueArgumentsConverter.X_QPID_DESCRIPTION, (Object)TEST_QUEUE_DESCRIPTION);

        ((AMQSession<?, ?>)_session).createQueue(AMQShortString.valueOf(queueName), false, true, false, arguments);

        ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals(TEST_QUEUE_DESCRIPTION, managedQueue.getDescription());

        restartBroker();

        managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals(TEST_QUEUE_DESCRIPTION, managedQueue.getDescription());
    }

    /**
     * Tests queue creation with {@link QueueArgumentsConverter#X_QPID_MAXIMUM_DELIVERY_COUNT} argument.  Also tests
     * that the attribute is exposed correctly through {@link ManagedQueue#getMaximumDeliveryCount()}.
     */
    public void testCreateQueueWithMaximumDeliveryCountSet() throws Exception
    {
        final String queueName = getName();
        final ManagedBroker managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);

        final Integer deliveryCount = 1;
        final Map<String, Object> arguments = Collections.singletonMap(QueueArgumentsConverter.X_QPID_MAXIMUM_DELIVERY_COUNT, (Object)deliveryCount);
        managedBroker.createNewQueue(queueName, null, true, arguments);

        // Ensure the queue exists
        assertNotNull("Queue object name expected to exist", _jmxUtils.getQueueObjectName(VIRTUAL_HOST, queueName));
        assertNotNull("Manager queue expected to be available", _jmxUtils.getManagedQueue(queueName));

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals("Unexpected maximum delivery count", deliveryCount, managedQueue.getMaximumDeliveryCount());
    }

    public void testCreateQueueWithAlertingThresholdsSet() throws Exception
    {
        final String queueName = getName();
        final ManagedBroker managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);

        final Long maximumMessageCount = 100l;
        final Long maximumMessageSize = 200l;
        final Long maximumQueueDepth = 300l;
        final Long maximumMessageAge = 400l;
        final Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(QueueArgumentsConverter.X_QPID_MAXIMUM_MESSAGE_COUNT, maximumMessageCount);
        arguments.put(QueueArgumentsConverter.X_QPID_MAXIMUM_MESSAGE_SIZE, maximumMessageSize);
        arguments.put(QueueArgumentsConverter.X_QPID_MAXIMUM_QUEUE_DEPTH, maximumQueueDepth);
        arguments.put(QueueArgumentsConverter.X_QPID_MAXIMUM_MESSAGE_AGE, maximumMessageAge);

        managedBroker.createNewQueue(queueName, null, true, arguments);

        // Ensure the queue exists
        assertNotNull("Queue object name expected to exist", _jmxUtils.getQueueObjectName(VIRTUAL_HOST, queueName));
        assertNotNull("Manager queue expected to be available", _jmxUtils.getManagedQueue(queueName));

        ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals("Unexpected maximum message count", maximumMessageCount, managedQueue.getMaximumMessageCount());
        assertEquals("Unexpected maximum message size", maximumMessageSize, managedQueue.getMaximumMessageSize());
        assertEquals("Unexpected maximum queue depth", maximumQueueDepth, managedQueue.getMaximumQueueDepth());
        assertEquals("Unexpected maximum message age", maximumMessageAge, managedQueue.getMaximumMessageAge());
    }

    /**
     * Requires 0-10 as relies on ADDR addresses.
     * @see AddressBasedDestinationTest for the testing of message routing to the alternate exchange
     */
    public void testGetSetAlternateExchange() throws Exception
    {
        String queueName = getTestQueueName();
        String altExchange = "amq.fanout";
        String addrWithAltExch = String.format("ADDR:%s;{create:always,node:{type:queue,x-declare:{alternate-exchange:'%s'}}}", queueName, altExchange);
        Queue queue = _session.createQueue(addrWithAltExch);

        createQueueOnBroker(queue);

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals("Newly created queue does not have expected alternate exchange", altExchange, managedQueue.getAlternateExchange());

        String newAltExch = "amq.topic";
        managedQueue.setAlternateExchange(newAltExch);
        assertEquals("Unexpected alternate exchange after set", newAltExch, managedQueue.getAlternateExchange());
    }

    /**
     * Requires 0-10 as relies on ADDR addresses.
     */
    public void testRemoveAlternateExchange() throws Exception
    {
        String queueName = getTestQueueName();
        String altExchange = "amq.fanout";
        String addrWithAltExch = String.format("ADDR:%s;{create:always,node:{type:queue,x-declare:{alternate-exchange:'%s'}}}", queueName, altExchange);
        Queue queue = _session.createQueue(addrWithAltExch);

        createQueueOnBroker(queue);

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        assertEquals("Newly created queue does not have expected alternate exchange", altExchange, managedQueue.getAlternateExchange());

        managedQueue.setAlternateExchange("");
        assertNull("Unexpected alternate exchange after set", managedQueue.getAlternateExchange());
    }

    /**
     * Requires persistent store
     * Requires 0-10 as relies on ADDR addresses.
     */
    public void testAlternateExchangeSurvivesRestart() throws Exception
    {
        String nonMandatoryExchangeName = "exch" + getName();

        final ManagedBroker managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);
        managedBroker.createNewExchange(nonMandatoryExchangeName, "fanout", true);

        String queueName1 = getTestQueueName() + "1";
        String altExchange1 = "amq.fanout";
        String addr1WithAltExch = String.format("ADDR:%s;{create:always,node:{durable: true,type:queue,x-declare:{alternate-exchange:'%s'}}}", queueName1, altExchange1);
        Queue queue1 = _session.createQueue(addr1WithAltExch);

        String queueName2 = getTestQueueName() + "2";
        String addr2WithoutAltExch = String.format("ADDR:%s;{create:always,node:{durable: true,type:queue}}", queueName2);
        Queue queue2 = _session.createQueue(addr2WithoutAltExch);

        createQueueOnBroker(queue1);
        createQueueOnBroker(queue2);

        ManagedQueue managedQueue1 = _jmxUtils.getManagedQueue(queueName1);
        assertEquals("Newly created queue1 does not have expected alternate exchange", altExchange1, managedQueue1.getAlternateExchange());

        ManagedQueue managedQueue2 = _jmxUtils.getManagedQueue(queueName2);
        assertNull("Newly created queue2 does not have expected alternate exchange", managedQueue2.getAlternateExchange());

        String altExchange2 = nonMandatoryExchangeName;
        managedQueue2.setAlternateExchange(altExchange2);

        restartBroker();

        managedQueue1 = _jmxUtils.getManagedQueue(queueName1);
        assertEquals("Queue1 does not have expected alternate exchange after restart", altExchange1, managedQueue1.getAlternateExchange());

        managedQueue2 = _jmxUtils.getManagedQueue(queueName2);
        assertEquals("Queue2 does not have expected updated alternate exchange after restart", altExchange2, managedQueue2.getAlternateExchange());
    }

    /**
     * Tests the ability to receive queue alerts as JMX notifications.
     *
     * @see NotificationCheckTest
     */
    public void testQueueNotification() throws Exception
    {
        final String queueName = getName();
        final long maximumMessageCount = 3;

        Queue queue = _session.createQueue(queueName);
        createQueueOnBroker(queue);

        ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);
        managedQueue.setMaximumMessageCount(maximumMessageCount);

        RecordingNotificationListener listener = new RecordingNotificationListener(1);

        _jmxUtils.addNotificationListener(_jmxUtils.getQueueObjectName(VIRTUAL_HOST, queueName), listener, null, null);

        // Send two messages - this should *not* trigger the notification
        sendMessage(_session, queue, 2);

        assertEquals("Premature notification received", 0, listener.getNumberOfNotificationsReceived());

        // A further message should trigger the message count alert
        sendMessage(_session, queue, 1);

        listener.awaitExpectedNotifications(5, TimeUnit.SECONDS);

        assertEquals("Unexpected number of JMX notifications received", 1, listener.getNumberOfNotificationsReceived());

        Notification notification = listener.getLastNotification();
        assertEquals("Unexpected notification message", "MESSAGE_COUNT_ALERT 3: Maximum count on queue threshold (3) breached.", notification.getMessage());
    }

    /**
     * Tests {@link ManagedQueue#viewMessages(long, long)} interface.
     */
    public void testViewSingleMessage() throws Exception
    {
        final List<Message> sentMessages = sendMessage(_session, _sourceQueue, 1);
        syncSession(_session);
        final Message sentMessage = sentMessages.get(0);

        assertEquals("Unexpected queue depth", 1, _managedSourceQueue.getMessageCount().intValue());

        // Check the contents of the message
        final TabularData tab = _managedSourceQueue.viewMessages(1l, 1l);
        assertEquals("Unexpected number of rows in table", 1, tab.size());
        final Iterator<CompositeData> rowItr = (Iterator<CompositeData>) tab.values().iterator();

        final CompositeData row1 = rowItr.next();
        assertNotNull("Message should have AMQ message id", row1.get(ManagedQueue.MSG_AMQ_ID));
        assertEquals("Unexpected queue position", 1l, row1.get(ManagedQueue.MSG_QUEUE_POS));
        assertEquals("Unexpected redelivered flag", Boolean.FALSE, row1.get(ManagedQueue.MSG_REDELIVERED));

        // Check the contents of header (encoded in a string array)
        final String[] headerArray = (String[]) row1.get(ManagedQueue.MSG_HEADER);
        assertNotNull("Expected message header array", headerArray);
        final Map<String, String> headers = headerArrayToMap(headerArray);

        final String expectedJMSMessageID = isBroker010() ? sentMessage.getJMSMessageID().replace("ID:", "") : sentMessage.getJMSMessageID();
        final String expectedFormattedJMSTimestamp = FastDateFormat.getInstance(ManagedQueue.JMSTIMESTAMP_DATETIME_FORMAT).format(sentMessage.getJMSTimestamp());
        assertEquals("Unexpected JMSMessageID within header", expectedJMSMessageID, headers.get("JMSMessageID"));
        assertEquals("Unexpected JMSPriority within header", String.valueOf(sentMessage.getJMSPriority()), headers.get("JMSPriority"));
        assertEquals("Unexpected JMSTimestamp within header", expectedFormattedJMSTimestamp, headers.get("JMSTimestamp"));
    }

    /**
     * Tests {@link ManagedQueue#moveMessages(long, long, String)} interface.
     */
    public void testMoveMessagesBetweenQueues() throws Exception
    {
        final int numberOfMessagesToSend = 10;

        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);

        // Move first three messages to destination
        long fromMessageId = amqMessagesIds.get(0);
        long toMessageId = amqMessagesIds.get(2);
        _managedSourceQueue.moveMessages(fromMessageId, toMessageId, _destinationQueueName);

        assertEquals("Unexpected queue depth on destination queue after first move", 3, _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after first move", 7, _managedSourceQueue.getMessageCount().intValue());

        // Now move a further two messages to destination
        fromMessageId = amqMessagesIds.get(7);
        toMessageId = amqMessagesIds.get(8);
        _managedSourceQueue.moveMessages(fromMessageId, toMessageId, _destinationQueueName);
        assertEquals("Unexpected queue depth on destination queue after second move", 5, _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after second move", 5, _managedSourceQueue.getMessageCount().intValue());

        assertMessageIndicesOn(_destinationQueue, 0, 1, 2, 7, 8);
    }

    /**
     * Tests {@link ManagedQueue#copyMessages(long, long, String)} interface.
     */
    public void testCopyMessagesBetweenQueues() throws Exception
    {
        final int numberOfMessagesToSend = 10;
        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);

        // Copy first three messages to destination
        long fromMessageId = amqMessagesIds.get(0);
        long toMessageId = amqMessagesIds.get(2);
        _managedSourceQueue.copyMessages(fromMessageId, toMessageId, _destinationQueueName);

        assertEquals("Unexpected queue depth on destination queue after first copy", 3, _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after first copy", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        // Now copy a further two messages to destination
        fromMessageId = amqMessagesIds.get(7);
        toMessageId = amqMessagesIds.get(8);
        _managedSourceQueue.copyMessages(fromMessageId, toMessageId, _destinationQueueName);
        assertEquals("Unexpected queue depth on destination queue after second copy", 5, _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after second copy", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        assertMessageIndicesOn(_destinationQueue, 0, 1, 2, 7, 8);
    }


    /**
     * Tests {@link ManagedQueue#copyMessages(long, long, String)} interface.
     */
    public void testCopyMessagesBetweenQueuesWithDuplicates() throws Exception
    {
        final int numberOfMessagesToSend = 10;
        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send",
                     numberOfMessagesToSend,
                     _managedSourceQueue.getMessageCount().intValue());

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);

        // Copy first three messages to destination
        long fromMessageId = amqMessagesIds.get(0);
        long toMessageId = amqMessagesIds.get(2);
        _managedSourceQueue.copyMessages(fromMessageId, toMessageId, _destinationQueueName);

        assertEquals("Unexpected queue depth on destination queue after first copy",
                     3,
                     _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after first copy",
                     numberOfMessagesToSend,
                     _managedSourceQueue.getMessageCount().intValue());

        // Now copy a further two messages to destination
        fromMessageId = amqMessagesIds.get(7);
        toMessageId = amqMessagesIds.get(8);
        _managedSourceQueue.copyMessages(fromMessageId, toMessageId, _destinationQueueName);
        assertEquals("Unexpected queue depth on destination queue after second copy",
                     5,
                     _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after second copy",
                     numberOfMessagesToSend,
                     _managedSourceQueue.getMessageCount().intValue());

        // Attempt to copy mixture of messages already on and some not already on the queue

        fromMessageId = amqMessagesIds.get(5);
        toMessageId = amqMessagesIds.get(8);
        _managedSourceQueue.copyMessages(fromMessageId, toMessageId, _destinationQueueName);
        assertEquals("Unexpected queue depth on destination queue after second copy",
                     7,
                     _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after second copy",
                     numberOfMessagesToSend,
                     _managedSourceQueue.getMessageCount().intValue());

        assertMessageIndicesOn(_destinationQueue, 0, 1, 2, 7, 8, 5, 6);


    }

    public void testMoveMessagesBetweenQueuesWithActiveConsumerOnSourceQueue() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(1).toString());
        Connection asyncConnection = getConnection();
        asyncConnection.start();

        final int numberOfMessagesToSend = 50;
        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);

        long fromMessageId = amqMessagesIds.get(0);
        long toMessageId = amqMessagesIds.get(numberOfMessagesToSend - 1);

        CountDownLatch consumerReadToHalfwayLatch = new CountDownLatch(numberOfMessagesToSend / 2);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        startAsyncConsumerOn(_sourceQueue, asyncConnection, consumerReadToHalfwayLatch, totalConsumed);

        boolean halfwayPointReached = consumerReadToHalfwayLatch.await(5000, TimeUnit.MILLISECONDS);
        assertTrue("Did not read half of messages within time allowed", halfwayPointReached);

        _managedSourceQueue.moveMessages(fromMessageId, toMessageId, _destinationQueueName);

        asyncConnection.stop();

        // The exact number of messages moved will be non deterministic, as the number of messages processed
        // by the consumer cannot be predicted.  There is also the possibility that a message can remain
        // on the source queue.  This situation will arise if a message has been acquired by the consumer, but not
        // yet delivered to the client application (i.e. MessageListener#onMessage()) when the Connection#stop() occurs.
        //
        // The number of messages moved + the number consumed + any messages remaining on source should
        // *always* be equal to the number we originally sent.

        int numberOfMessagesReadByConsumer = totalConsumed.intValue();
        int numberOfMessagesOnDestinationQueue = _managedDestinationQueue.getMessageCount().intValue();
        int numberOfMessagesRemainingOnSourceQueue = _managedSourceQueue.getMessageCount().intValue();

        LOGGER.debug("Async consumer read : " + numberOfMessagesReadByConsumer
                + " Number of messages moved to destination : " + numberOfMessagesOnDestinationQueue
                + " Number of messages remaining on source : " + numberOfMessagesRemainingOnSourceQueue);
        assertEquals("Unexpected number of messages after move", numberOfMessagesToSend, numberOfMessagesReadByConsumer + numberOfMessagesOnDestinationQueue + numberOfMessagesRemainingOnSourceQueue);
    }

    public void testMoveMessagesBetweenQueuesWithActiveConsumerOnDestinationQueue() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(1).toString());
        Connection asyncConnection = getConnection();
        asyncConnection.start();

        final int numberOfMessagesToSend = 50;
        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);
        long fromMessageId = amqMessagesIds.get(0);
        long toMessageId = amqMessagesIds.get(numberOfMessagesToSend - 1);

        AtomicInteger totalConsumed = new AtomicInteger(0);
        CountDownLatch allMessagesConsumedLatch = new CountDownLatch(numberOfMessagesToSend);
        startAsyncConsumerOn(_destinationQueue, asyncConnection, allMessagesConsumedLatch, totalConsumed);

        _managedSourceQueue.moveMessages(fromMessageId, toMessageId, _destinationQueueName);

        allMessagesConsumedLatch.await(5000, TimeUnit.MILLISECONDS);
        assertEquals("Did not consume all messages from destination queue", numberOfMessagesToSend, totalConsumed.intValue());
    }

    /**
     * Tests {@link ManagedQueue#moveMessages(long, long, String)} interface.
     */
    public void testMoveMessageBetweenQueuesWithBrokerRestart() throws Exception
    {
        final int numberOfMessagesToSend = 1;

        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        restartBroker();

        createManagementInterfacesForQueues();
        createConnectionAndSession();

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);

        // Move messages to destination
        long messageId = amqMessagesIds.get(0);
        _managedSourceQueue.moveMessages(messageId, messageId, _destinationQueueName);

        assertEquals("Unexpected queue depth on destination queue after move", 1, _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after move", 0, _managedSourceQueue.getMessageCount().intValue());

        assertMessageIndicesOn(_destinationQueue, 0);
    }

    /**
     * Tests {@link ManagedQueue#copyMessages(long, long, String)} interface.
     */
    public void testCopyMessageBetweenQueuesWithBrokerRestart() throws Exception
    {
        final int numberOfMessagesToSend = 1;

        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());

        restartBroker();

        createManagementInterfacesForQueues();
        createConnectionAndSession();

        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);

        // Move messages to destination
        long messageId = amqMessagesIds.get(0);
        _managedSourceQueue.copyMessages(messageId, messageId, _destinationQueueName);

        assertEquals("Unexpected queue depth on destination queue after copy", 1, _managedDestinationQueue.getMessageCount().intValue());
        assertEquals("Unexpected queue depth on source queue after copy", 1, _managedSourceQueue.getMessageCount().intValue());

        assertMessageIndicesOn(_destinationQueue, 0);
    }

    /**
     * Tests {@link ManagedQueue#deleteMessages(long, long)} interface.
     */
    public void testDeleteMessages() throws Exception
    {
        final int numberOfMessagesToSend = 15;

        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
        syncSession(_session);
        assertEquals("Unexpected queue depth after send", numberOfMessagesToSend, _managedSourceQueue.getMessageCount().intValue());
        List<Long> amqMessagesIds = getAMQMessageIdsOn(_managedSourceQueue, 1, numberOfMessagesToSend);
        // Current expected queue state, in terms of message header indices: [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14]

        // Delete the first message (Remember the amqMessagesIds list, and the message indices added as a property when sending, are both 0-based index)
        long fromMessageId = amqMessagesIds.get(0);
        long toMessageId = fromMessageId;
        _managedSourceQueue.deleteMessages(fromMessageId, toMessageId);
        assertEquals("Unexpected message count after first deletion", numberOfMessagesToSend - 1, _managedSourceQueue.getMessageCount().intValue());
        // Current expected queue state, in terms of message header indices: [X,1,2,3,4,5,6,7,8,9,10,11,12,13,14]

        // Delete the 9th-10th messages, in the middle of the queue
        fromMessageId = amqMessagesIds.get(8);
        toMessageId = amqMessagesIds.get(9);
        _managedSourceQueue.deleteMessages(fromMessageId, toMessageId);
        assertEquals("Unexpected message count after third deletion", numberOfMessagesToSend - 3, _managedSourceQueue.getMessageCount().intValue());
        // Current expected queue state, in terms of message header indices: [X,1,2,3,4,5,6,7,X,X,10,11,12,13,14]

        // Delete the 11th and 12th messages, but still include the IDs for the 9th and 10th messages in the
        // range to ensure their IDs are 'skipped' until the matching messages are found
        fromMessageId = amqMessagesIds.get(8);
        toMessageId = amqMessagesIds.get(11);
        _managedSourceQueue.deleteMessages(fromMessageId, toMessageId);
        assertEquals("Unexpected message count after fourth deletion", numberOfMessagesToSend - 5, _managedSourceQueue.getMessageCount().intValue());
        // Current expected queue state, in terms of message header indices: [X,1,2,3,4,5,6,7,X,X,X,X,12,13,14]

        // Delete the 8th message and the 13th message, including the IDs for the 9th-12th messages in the
        // range to ensure their IDs are 'skipped' and the other matching message is found
        fromMessageId = amqMessagesIds.get(7);
        toMessageId = amqMessagesIds.get(12);
        _managedSourceQueue.deleteMessages(fromMessageId, toMessageId);
        assertEquals("Unexpected message count after fourth deletion", numberOfMessagesToSend - 7, _managedSourceQueue.getMessageCount().intValue());
        // Current expected queue state, in terms of message header indices: [X,1,2,3,4,5,6,X,X,X,X,X,X,13,14]

        // Delete the last message message
        fromMessageId = amqMessagesIds.get(numberOfMessagesToSend -1);
        toMessageId = fromMessageId;
        _managedSourceQueue.deleteMessages(fromMessageId, toMessageId);
        assertEquals("Unexpected message count after second deletion", numberOfMessagesToSend - 8, _managedSourceQueue.getMessageCount().intValue());
        // Current expected queue state, in terms of message header indices: [X,1,2,3,4,5,6,X,X,X,X,X,X,13,X]

        // Verify the message indices with a consumer
        assertMessageIndicesOn(_sourceQueue, 1,2,3,4,5,6,13);
    }

    public void testGetMessageGroupKey() throws Exception
    {
        final String queueName = getName();
        final ManagedBroker managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);

        final Object messageGroupKey = "test";
        final Map<String, Object> arguments = Collections.singletonMap(QueueArgumentsConverter.QPID_GROUP_HEADER_KEY, messageGroupKey);
        managedBroker.createNewQueue(queueName, null, true, arguments);

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);

        assertNotNull("Manager queue expected to be available", managedQueue);
        assertEquals("Unexpected message group key", messageGroupKey, managedQueue.getMessageGroupKey());
        assertEquals("Unexpected message group sharing", false, managedQueue.isMessageGroupSharedGroups());
    }

    public void testIsMessageGroupSharedGroups() throws Exception
    {
        final String queueName = getName();
        final ManagedBroker managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);

        final Object messageGroupKey = "test";
        final Map<String, Object> arguments = new HashMap<String, Object>(2);
        arguments.put(QueueArgumentsConverter.QPID_GROUP_HEADER_KEY, messageGroupKey);
        arguments.put(QueueArgumentsConverter.QPID_SHARED_MSG_GROUP, StandardQueueImpl.SHARED_MSG_GROUP_ARG_VALUE);
        managedBroker.createNewQueue(queueName, null, true, arguments);

        final ManagedQueue managedQueue = _jmxUtils.getManagedQueue(queueName);

        assertNotNull("Manager queue expected to be available", managedQueue);
        assertEquals("Unexpected message group key", messageGroupKey, managedQueue.getMessageGroupKey());
        assertEquals("Unexpected message group sharing", true, managedQueue.isMessageGroupSharedGroups());
    }

    @Override
    public Message createNextMessage(Session session, int messageNumber) throws JMSException
    {
        Message message = session.createTextMessage(getContentForMessageNumber(messageNumber));
        message.setIntProperty(INDEX, messageNumber);
        return message;
    }

    private void startAsyncConsumerOn(Destination queue, Connection asyncConnection,
            final CountDownLatch requiredNumberOfMessagesRead, final AtomicInteger totalConsumed) throws Exception
    {
        Session session = asyncConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(queue);
        consumer.setMessageListener(new MessageListener()
        {

            @Override
            public void onMessage(Message arg0)
            {
                totalConsumed.incrementAndGet();
                requiredNumberOfMessagesRead.countDown();
            }
        });
    }

    private void assertMessageIndicesOn(Destination queue, int... expectedIndices) throws Exception
    {
        MessageConsumer consumer = _session.createConsumer(queue);

        for (int i : expectedIndices)
        {
            TextMessage message = (TextMessage)consumer.receive(1000);
            assertNotNull("Expected message with index " + i, message);
            assertEquals("Expected message with index " + i, i, message.getIntProperty(INDEX));
            assertEquals("Expected message content", getContentForMessageNumber(i), message.getText());
        }

        assertNull("Unexpected message encountered", consumer.receive(1000));
    }

    private List<Long> getAMQMessageIdsOn(ManagedQueue managedQueue, long startIndex, long endIndex) throws Exception
    {
        final SortedSet<Long> messageIds = new TreeSet<Long>();

        final TabularData tab = managedQueue.viewMessages(startIndex, endIndex);
        final Iterator<CompositeData> rowItr = (Iterator<CompositeData>) tab.values().iterator();
        while(rowItr.hasNext())
        {
            final CompositeData row = rowItr.next();
            long amqMessageId = (Long)row.get(ManagedQueue.MSG_AMQ_ID);
            messageIds.add(amqMessageId);
        }

        return new ArrayList<Long>(messageIds);
    }

    /**
     *
     * Utility method to convert array of Strings in the form x = y into a
     * map with key/value x =&gt; y.
     *
     */
    private Map<String,String> headerArrayToMap(final String[] headerArray)
    {
        final Map<String, String> headerMap = new HashMap<String, String>();
        final List<String> headerList = Arrays.asList(headerArray);
        for (Iterator<String> iterator = headerList.iterator(); iterator.hasNext();)
        {
            final String nameValuePair = iterator.next();
            final String[] nameValue = nameValuePair.split(" *= *", 2);
            headerMap.put(nameValue[0], nameValue[1]);
        }
        return headerMap;
    }

    private void createQueueOnBroker(Destination destination) throws JMSException
    {
        _session.createConsumer(destination).close(); // Create a consumer only to cause queue creation
    }

    private void syncSession(Session session) throws Exception
    {
        ((AMQSession<?,?>)session).sync();
    }

    private void createConnectionAndSession() throws JMSException,
            NamingException
    {
        _connection = getConnection();
        _connection.start();
        _session = _connection.createSession(true, Session.SESSION_TRANSACTED);
    }

    private void createManagementInterfacesForQueues()
    {
        _managedSourceQueue = _jmxUtils.getManagedQueue(_sourceQueueName);
        _managedDestinationQueue = _jmxUtils.getManagedQueue(_destinationQueueName);
    }

    private String getContentForMessageNumber(int msgCount)
    {
        return "Message count " + msgCount;
    }

    private final class RecordingNotificationListener implements NotificationListener
    {
        private final CountDownLatch _notificationReceivedLatch;
        private final AtomicInteger _numberOfNotifications;
        private final AtomicReference<Notification> _lastNotification;

        private RecordingNotificationListener(int expectedNumberOfNotifications)
        {
            _notificationReceivedLatch = new CountDownLatch(expectedNumberOfNotifications);
            _numberOfNotifications = new AtomicInteger(0);
            _lastNotification = new AtomicReference<Notification>();
        }

        @Override
        public void handleNotification(Notification notification, Object handback)
        {
            _lastNotification.set(notification);
            _numberOfNotifications.incrementAndGet();
            _notificationReceivedLatch.countDown();
        }

        public int getNumberOfNotificationsReceived()
        {
            return _numberOfNotifications.get();
        }

        public Notification getLastNotification()
        {
            return _lastNotification.get();
        }

        public void awaitExpectedNotifications(long timeout, TimeUnit timeunit) throws InterruptedException
        {
            _notificationReceivedLatch.await(timeout, timeunit);
        }
    }

}
