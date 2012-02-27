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
package org.apache.qpid.management.jmx;

import org.apache.commons.lang.time.FastDateFormat;

import org.apache.log4j.Logger;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.queue.AMQQueueMBean;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the JMX API for the Managed Queue.
 *
 */
public class ManagedQueueMBeanTest extends QpidBrokerTestCase
{
    protected static final Logger LOGGER = Logger.getLogger(ManagedQueueMBeanTest.class);

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
        _jmxUtils = new JMXTestUtils(this);
        _jmxUtils.setUp();

        super.setUp();
        _sourceQueueName = getTestQueueName() + "_src";
        _destinationQueueName = getTestQueueName() + "_dest";

        _connection = getConnection();
        _connection.start();

        _session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        _sourceQueue = _session.createQueue(_sourceQueueName);
        _destinationQueue = _session.createQueue(_destinationQueueName);
        createQueueOnBroker(_sourceQueue);
        createQueueOnBroker(_destinationQueue);

        _jmxUtils.open();

        _managedSourceQueue = _jmxUtils.getManagedQueue(_sourceQueueName);
        _managedDestinationQueue = _jmxUtils.getManagedQueue(_destinationQueueName);
    }

    public void tearDown() throws Exception
    {
        if (_jmxUtils != null)
        {
            _jmxUtils.close();
        }
        super.tearDown();
    }

    /**
     * Tests {@link ManagedQueue#viewMessages(long, long)} interface.
     */
    public void testViewSingleMessage() throws Exception
    {
        final List<Message> sentMessages = sendMessage(_session, _sourceQueue, 1);
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
        final String expectedFormattedJMSTimestamp = FastDateFormat.getInstance(AMQQueueMBean.JMSTIMESTAMP_DATETIME_FORMAT).format(sentMessage.getJMSTimestamp());
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

    public void testMoveMessagesBetweenQueuesWithActiveConsumerOnSourceQueue() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(1).toString());
        Connection asyncConnection = getConnection();
        asyncConnection.start();

        final int numberOfMessagesToSend = 50;
        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
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

        // The exact number of messages moved will be non deterministic, but the number of messages moved
        // plus the number consumed should be equal to the number we originally sent.

        int numberOfMessagesReadByConsumer = totalConsumed.intValue();
        int numberOfMessagesOnDestinationQueue = _managedDestinationQueue.getMessageCount().intValue();
        LOGGER.debug("Async consumer read : " + numberOfMessagesReadByConsumer
                + " Number of messages moved to destination : " + numberOfMessagesOnDestinationQueue);
        assertEquals(numberOfMessagesToSend, numberOfMessagesReadByConsumer + numberOfMessagesOnDestinationQueue);

        int numberOfMessagesRemainingOnSourceQueue = _managedSourceQueue.getMessageCount().intValue();
        assertEquals(0, numberOfMessagesRemainingOnSourceQueue);
    }

    public void testMoveMessagesBetweenQueuesWithActiveConsumerOnDestinationQueue() throws Exception
    {
        setTestClientSystemProperty(ClientProperties.MAX_PREFETCH_PROP_NAME, new Integer(1).toString());
        Connection asyncConnection = getConnection();
        asyncConnection.start();

        final int numberOfMessagesToSend = 50;
        sendMessage(_session, _sourceQueue, numberOfMessagesToSend);
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

    private void assertMessageIndicesOn(Destination queue, int... expectedIndexes) throws Exception
    {
        MessageConsumer consumer = _session.createConsumer(queue);

        for (int i : expectedIndexes)
        {
            Message message = consumer.receive(1000);
            assertNotNull("Expected message with index " + i, message);
            assertEquals("Expected message with index " + i, i, message.getIntProperty(INDEX));
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
}
