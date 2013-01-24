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
package org.apache.qpid.server.store.berkeleydb;


import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.NON_DURABLE_QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.PRIORITY_QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.QUEUE_WITH_DLQ_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SELECTOR_SUB_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SELECTOR_TOPIC_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SUB_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.TOPIC_NAME;

import java.io.File;
import java.io.InputStream;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularDataSupport;

import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests upgrading a BDB store on broker startup.
 * The store will then be used to verify that the upgrade is completed
 * properly and that once upgraded it functions as expected.
 */
public class BDBUpgradeTest extends QpidBrokerTestCase
{
    protected static final Logger _logger = LoggerFactory.getLogger(BDBUpgradeTest.class);

    private static final String STRING_1024 = BDBStoreUpgradeTestPreparer.generateString(1024);
    private static final String STRING_1024_256 = BDBStoreUpgradeTestPreparer.generateString(1024*256);
    private static final String QPID_WORK_ORIG = System.getProperty("QPID_WORK");

    private String _storeLocation;

    @Override
    public void setUp() throws Exception
    {
        assertNotNull("QPID_WORK must be set", QPID_WORK_ORIG);
        _storeLocation = getWorkDirBaseDir() + File.separator + "test-store";

        //Clear the two target directories if they exist.
        File directory = new File(_storeLocation);
        if (directory.exists() && directory.isDirectory())
        {
            FileUtils.delete(directory, true);
        }
        directory.mkdirs();

        // copy store files
        InputStream src = getClass().getClassLoader().getResourceAsStream("upgrade/bdbstore-v4/test-store/00000000.jdb");
        FileUtils.copy(src, new File(_storeLocation, "00000000.jdb"));

        getBrokerConfiguration().addJmxManagementConfiguration();
        super.setUp();
    }

    private String getWorkDirBaseDir()
    {
        return QPID_WORK_ORIG + (isInternalBroker() ? "" : "/" + getPort());
    }

    /**
     * Test that the selector applied to the DurableSubscription was successfully
     * transfered to the new store, and functions as expected with continued use
     * by monitoring message count while sending new messages to the topic and then
     * consuming them.
     */
    public void testSelectorDurability() throws Exception
    {
        JMXTestUtils jmxUtils = null;
        try
        {
            jmxUtils = new JMXTestUtils(this, "guest", "guest");
            jmxUtils.open();
        }
        catch (Exception e)
        {
            fail("Unable to establish JMX connection, test cannot proceed");
        }

        try
        {
            ManagedQueue dursubQueue = jmxUtils.getManagedQueue("clientid" + ":" + SELECTOR_SUB_NAME);
            assertEquals("DurableSubscription backing queue should have 1 message on it initially",
                          new Integer(1), dursubQueue.getMessageCount());

            // Create a connection and start it
            TopicConnection connection = (TopicConnection) getConnection();
            connection.start();

            // Send messages which don't match and do match the selector, checking message count
            TopicSession pubSession = connection.createTopicSession(true, Session.SESSION_TRANSACTED);
            Topic topic = pubSession.createTopic(SELECTOR_TOPIC_NAME);
            TopicPublisher publisher = pubSession.createPublisher(topic);

            BDBStoreUpgradeTestPreparer.publishMessages(pubSession, publisher, topic, DeliveryMode.PERSISTENT, 1*1024, 1, "false");
            pubSession.commit();
            assertEquals("DurableSubscription backing queue should still have 1 message on it",
                         Integer.valueOf(1), dursubQueue.getMessageCount());

            BDBStoreUpgradeTestPreparer.publishMessages(pubSession, publisher, topic, DeliveryMode.PERSISTENT, 1*1024, 1, "true");
            pubSession.commit();
            assertEquals("DurableSubscription backing queue should now have 2 messages on it",
                         Integer.valueOf(2), dursubQueue.getMessageCount());

            TopicSubscriber durSub = pubSession.createDurableSubscriber(topic, SELECTOR_SUB_NAME,"testprop='true'", false);
            Message m = durSub.receive(2000);
            assertNotNull("Failed to receive an expected message", m);
            m = durSub.receive(2000);
            assertNotNull("Failed to receive an expected message", m);
            pubSession.commit();

            pubSession.close();
        }
        finally
        {
            jmxUtils.close();
        }
    }

    /**
     * Test that the DurableSubscription without selector was successfully
     * transfered to the new store, and functions as expected with continued use.
     */
    public void testDurableSubscriptionWithoutSelector() throws Exception
    {
        JMXTestUtils jmxUtils = null;
        try
        {
            jmxUtils = new JMXTestUtils(this, "guest", "guest");
            jmxUtils.open();
        }
        catch (Exception e)
        {
            fail("Unable to establish JMX connection, test cannot proceed");
        }

        try
        {
            ManagedQueue dursubQueue = jmxUtils.getManagedQueue("clientid" + ":" + SUB_NAME);
            assertEquals("DurableSubscription backing queue should have 1 message on it initially",
                          new Integer(1), dursubQueue.getMessageCount());

            // Create a connection and start it
            TopicConnection connection = (TopicConnection) getConnection();
            connection.start();

            // Send new message matching the topic, checking message count
            TopicSession session = connection.createTopicSession(true, Session.SESSION_TRANSACTED);
            Topic topic = session.createTopic(TOPIC_NAME);
            TopicPublisher publisher = session.createPublisher(topic);

            BDBStoreUpgradeTestPreparer.publishMessages(session, publisher, topic, DeliveryMode.PERSISTENT, 1*1024, 1, "indifferent");
            session.commit();
            assertEquals("DurableSubscription backing queue should now have 2 messages on it",
                        Integer.valueOf(2), dursubQueue.getMessageCount());

            TopicSubscriber durSub = session.createDurableSubscriber(topic, SUB_NAME);
            Message m = durSub.receive(2000);
            assertNotNull("Failed to receive an expected message", m);
            m = durSub.receive(2000);
            assertNotNull("Failed to receive an expected message", m);

            session.commit();
            session.close();
        }
        finally
        {
            jmxUtils.close();
        }
    }

    /**
     * Test that the backing queue for the durable subscription created was successfully
     * detected and set as being exclusive during the upgrade process, and that the
     * regular queue was not.
     */
    public void testQueueExclusivity() throws Exception
    {
        JMXTestUtils jmxUtils = null;
        try
        {
            jmxUtils = new JMXTestUtils(this, "guest", "guest");
            jmxUtils.open();
        }
        catch (Exception e)
        {
            fail("Unable to establish JMX connection, test cannot proceed");
        }

        try
        {
            ManagedQueue queue = jmxUtils.getManagedQueue(QUEUE_NAME);
            assertFalse("Queue should not have been marked as Exclusive during upgrade", queue.isExclusive());

            ManagedQueue dursubQueue = jmxUtils.getManagedQueue("clientid" + ":" + SUB_NAME);
            assertTrue("DurableSubscription backing queue should have been marked as Exclusive during upgrade", dursubQueue.isExclusive());
        }
        finally
        {
            jmxUtils.close();
        }
    }

    /**
     * Test that the upgraded queue continues to function properly when used
     * for persistent messaging and restarting the broker.
     *
     * Sends the new messages to the queue BEFORE consuming those which were
     * sent before the upgrade. In doing so, this also serves to test that
     * the queue bindings were successfully transitioned during the upgrade.
     */
    public void testBindingAndMessageDurabability() throws Exception
    {
        // Create a connection and start it
        TopicConnection connection = (TopicConnection) getConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(QUEUE_NAME);
        MessageProducer messageProducer = session.createProducer(queue);

        // Send a new message
        BDBStoreUpgradeTestPreparer.sendMessages(session, messageProducer, queue, DeliveryMode.PERSISTENT, 256*1024, 1);

        session.close();

        // Restart the broker
        restartBroker();

        // Drain the queue of all messages
        connection = (TopicConnection) getConnection();
        connection.start();
        consumeQueueMessages(connection, true);
    }

    /**
     * Test that all of the committed persistent messages previously sent to
     * the broker are properly received following update of the MetaData and
     * Content entries during the store upgrade process.
     */
    public void testConsumptionOfUpgradedMessages() throws Exception
    {
        // Create a connection and start it
        Connection connection = getConnection();
        connection.start();

        consumeDurableSubscriptionMessages(connection, true);
        consumeDurableSubscriptionMessages(connection, false);
        consumeQueueMessages(connection, false);
    }

    /**
     * Tests store migration containing messages for non-existing queue.
     *
     * @throws Exception
     */
    public void testMigrationOfMessagesForNonDurableQueues() throws Exception
    {
        // Create a connection and start it
        Connection connection = getConnection();
        connection.start();

        // consume a message for non-existing store
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(NON_DURABLE_QUEUE_NAME);
        MessageConsumer messageConsumer = session.createConsumer(queue);

        for (int i = 1; i <= 3; i++)
        {
            Message message = messageConsumer.receive(1000);
            assertNotNull("Message was not migrated!", message);
            assertTrue("Unexpected message received!", message instanceof TextMessage);
            assertEquals("ID property did not match", i, message.getIntProperty("ID"));
        }
    }

    /**
     * Tests store upgrade has maintained the priority queue configuration,
     * such that sending messages with priorities out-of-order and then consuming
     * them gets the messages back in priority order.
     */
    public void testPriorityQueue() throws Exception
    {
        // Create a connection and start it
        Connection connection = getConnection();
        connection.start();

        // send some messages to the priority queue
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(PRIORITY_QUEUE_NAME);
        MessageProducer producer = session.createProducer(queue);

        producer.setPriority(4);
        producer.send(createMessage(1, false, session, producer));
        producer.setPriority(1);
        producer.send(createMessage(2, false, session, producer));
        producer.setPriority(9);
        producer.send(createMessage(3, false, session, producer));
        session.close();

        //consume the messages, expected order: msg 3, msg 1, msg 2.
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(queue);

        Message msg = consumer.receive(1500);
        assertNotNull("expected message was not received", msg);
        assertEquals(3, msg.getIntProperty("msg"));
        msg = consumer.receive(1500);
        assertNotNull("expected message was not received", msg);
        assertEquals(1, msg.getIntProperty("msg"));
        msg = consumer.receive(1500);
        assertNotNull("expected message was not received", msg);
        assertEquals(2, msg.getIntProperty("msg"));
    }

    /**
     * Test that the queue configured to have a DLQ was recovered and has the alternate exchange
     * and max delivery count, the DLE exists, the DLQ exists with no max delivery count, the
     * DLQ is bound to the DLE, and that the DLQ does not itself have a DLQ.
     *
     * DLQs are NOT enabled at the virtualhost level, we are testing recovery of the arguments
     * that turned it on for this specific queue.
     */
    public void testRecoveryOfQueueWithDLQ() throws Exception
    {
        JMXTestUtils jmxUtils = null;
        try
        {
            jmxUtils = new JMXTestUtils(this, "guest", "guest");
            jmxUtils.open();
        }
        catch (Exception e)
        {
            fail("Unable to establish JMX connection, test cannot proceed");
        }

        try
        {
            //verify the DLE exchange exists, has the expected type, and a single binding for the DLQ
            ManagedExchange exchange = jmxUtils.getManagedExchange(QUEUE_WITH_DLQ_NAME + "_DLE");
            assertEquals("Wrong exchange type", "fanout", exchange.getExchangeType());
            TabularDataSupport bindings = (TabularDataSupport) exchange.bindings();
            assertEquals(1, bindings.size());
            for(Object o : bindings.values())
            {
                CompositeData binding = (CompositeData) o;

                String bindingKey = (String) binding.get(ManagedExchange.BINDING_KEY);
                String[] queueNames = (String[]) binding.get(ManagedExchange.QUEUE_NAMES);

                //Because its a fanout exchange, we just return a single '*' key with all bound queues
                assertEquals("unexpected binding key", "*", bindingKey);
                assertEquals("unexpected number of queues bound", 1, queueNames.length);
                assertEquals("unexpected queue name", QUEUE_WITH_DLQ_NAME + "_DLQ", queueNames[0]);
            }

            //verify the queue exists, has the expected alternate exchange and max delivery count
            ManagedQueue queue = jmxUtils.getManagedQueue(QUEUE_WITH_DLQ_NAME);
            assertEquals("Queue does not have the expected AlternateExchange", QUEUE_WITH_DLQ_NAME + "_DLE", queue.getAlternateExchange());
            assertEquals("Unexpected maximum delivery count", Integer.valueOf(2), queue.getMaximumDeliveryCount());

            ManagedQueue dlQqueue = jmxUtils.getManagedQueue(QUEUE_WITH_DLQ_NAME + "_DLQ");
            assertNull("Queue should not have an AlternateExchange", dlQqueue.getAlternateExchange());
            assertEquals("Unexpected maximum delivery count", Integer.valueOf(0), dlQqueue.getMaximumDeliveryCount());

            String dlqDlqObjectNameString = jmxUtils.getQueueObjectNameString("test", QUEUE_WITH_DLQ_NAME + "_DLQ" + "_DLQ");
            assertFalse("a DLQ should not exist for the DLQ itself", jmxUtils.doesManagedObjectExist(dlqDlqObjectNameString));
        }
        finally
        {
            jmxUtils.close();
        }
    }

    private void consumeDurableSubscriptionMessages(Connection connection, boolean selector) throws Exception
    {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = null;
        TopicSubscriber durSub = null;

        if(selector)
        {
            topic = session.createTopic(SELECTOR_TOPIC_NAME);
            durSub = session.createDurableSubscriber(topic, SELECTOR_SUB_NAME,"testprop='true'", false);
        }
        else
        {
            topic = session.createTopic(TOPIC_NAME);
            durSub = session.createDurableSubscriber(topic, SUB_NAME);
        }


        // Retrieve the matching message
        Message m = durSub.receive(2000);
        assertNotNull("Failed to receive an expected message", m);
        if(selector)
        {
            assertEquals("Selector property did not match", "true", m.getStringProperty("testprop"));
        }
        assertEquals("ID property did not match", 1, m.getIntProperty("ID"));
        assertEquals("Message content was not as expected",BDBStoreUpgradeTestPreparer.generateString(1024) , ((TextMessage)m).getText());

        // Verify that no more messages are received
        m = durSub.receive(1000);
        assertNull("No more messages should have been recieved", m);

        durSub.close();
        session.close();
    }

    private void consumeQueueMessages(Connection connection, boolean extraMessage) throws Exception
    {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(QUEUE_NAME);

        MessageConsumer consumer = session.createConsumer(queue);
        Message m;

        // Retrieve the initial pre-upgrade messages
        for (int i=1; i <= 5 ; i++)
        {
            m = consumer.receive(2000);
            assertNotNull("Failed to receive an expected message", m);
            assertEquals("ID property did not match", i, m.getIntProperty("ID"));
            assertEquals("Message content was not as expected", STRING_1024_256, ((TextMessage)m).getText());
        }
        for (int i=1; i <= 5 ; i++)
        {
            m = consumer.receive(2000);
            assertNotNull("Failed to receive an expected message", m);
            assertEquals("ID property did not match", i, m.getIntProperty("ID"));
            assertEquals("Message content was not as expected", STRING_1024, ((TextMessage)m).getText());
        }

        if(extraMessage)
        {
            //verify that the extra message is received
            m = consumer.receive(2000);
            assertNotNull("Failed to receive an expected message", m);
            assertEquals("ID property did not match", 1, m.getIntProperty("ID"));
            assertEquals("Message content was not as expected", STRING_1024_256, ((TextMessage)m).getText());
        }

        // Verify that no more messages are received
        m = consumer.receive(1000);
        assertNull("No more messages should have been recieved", m);

        consumer.close();
        session.close();
    }

    private Message createMessage(int msgId, boolean first, Session producerSession, MessageProducer producer) throws JMSException
    {
        Message send = producerSession.createTextMessage("Message: " + msgId);
        send.setIntProperty("msg", msgId);

        return send;
    }
}
