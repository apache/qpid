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
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SELECTOR_SUB_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SELECTOR_TOPIC_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SUB_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.TOPIC_NAME;

import java.io.File;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
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
        _storeLocation = getWorkDirBaseDir() + "/bdbstore/test-store";

        //Clear the two target directories if they exist.
        File directory = new File(_storeLocation);
        if (directory.exists() && directory.isDirectory())
        {
            FileUtils.delete(directory, true);
        }

        // copy store files
        String src = getClass().getClassLoader().getResource("upgrade/bdbstore-v4/test-store").toURI().getPath();
        FileUtils.copyRecursive(new File(src), new File(_storeLocation));

        //override the broker config used and then start the broker with the updated store
        _configFile = new File("build/etc/config-systests-bdb.xml");
        setConfigurationProperty("management.enabled", "true");

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

        for (int i = 0; i < 3; i++)
        {
            Message message = messageConsumer.receive(1000);
            assertNotNull("Message was not migrated!", message);
            assertTrue("Unexpected message received!", message instanceof TextMessage);
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

}
