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

import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.SUB_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.TOPIC_NAME;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

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

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.message.EnqueableMessage;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.store.berkeleydb.keys.MessageContentKey_4;
import org.apache.qpid.server.store.berkeleydb.tuples.MessageContentKeyTupleBindingFactory;
import org.apache.qpid.server.store.berkeleydb.tuples.MessageMetaDataTupleBindingFactory;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * Tests upgrading a BDB store and using it with the new broker 
 * after the required contents are entered into the store using 
 * an old broker with the BDBStoreUpgradeTestPreparer. The store
 * will then be used to verify that the upgraded is completed 
 * properly and that once upgraded it functions as expected with 
 * the new broker.
 */
public class BDBUpgradeTest extends QpidBrokerTestCase
{
    protected static final Logger _logger = LoggerFactory.getLogger(BDBUpgradeTest.class);

    private static final String STRING_1024 = BDBStoreUpgradeTestPreparer.generateString(1024);
    private static final String STRING_1024_256 = BDBStoreUpgradeTestPreparer.generateString(1024*256);
    private static final String QPID_WORK_ORIG = System.getProperty("QPID_WORK");
    private static final String QPID_HOME = System.getProperty("QPID_HOME");
    private static final int VERSION_4 = 4;

    private String _fromDir;
    private String _toDir;
    private String _toDirTwice;

    @Override
    public void setUp() throws Exception
    {
        assertNotNull("QPID_WORK must be set", QPID_WORK_ORIG);
        assertNotNull("QPID_HOME must be set", QPID_HOME);

        _fromDir = QPID_HOME + "/bdbstore-to-upgrade/test-store";
        _toDir = getWorkDirBaseDir() + "/bdbstore/test-store";
        _toDirTwice = getWorkDirBaseDir() + "/bdbstore-upgraded-twice";

        //Clear the two target directories if they exist.
        File directory = new File(_toDir);
        if (directory.exists() && directory.isDirectory())
        {
            FileUtils.delete(directory, true);
        }
        directory = new File(_toDirTwice);
        if (directory.exists() && directory.isDirectory())
        {
            FileUtils.delete(directory, true);
        }

        //Upgrade the test store.
        upgradeBrokerStore(_fromDir, _toDir);

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
     * Tests that the core upgrade method of the store upgrade tool passes through the exception
     * from the BDBMessageStore indicating that the data on disk can't be loaded as the previous
     * version because it has already been upgraded.
     * @throws Exception
     */
    public void testMultipleUpgrades() throws Exception
    {
        //stop the broker started by setUp() in order to allow the second upgrade attempt to proceed
        stopBroker();

        try
        {
            new BDBStoreUpgrade(_toDir, _toDirTwice, null, false, true).upgradeFromVersion(VERSION_4);
            fail("Second Upgrade Succeeded");
        }
        catch (Exception e)
        {
            System.err.println("Showing stack trace, we are expecting an 'Unable to load BDBStore' error");
            e.printStackTrace();
            assertTrue("Incorrect Exception Thrown:" + e.getMessage(),
                    e.getMessage().contains("Unable to load BDBStore as version 4. Store on disk contains version 5 data"));
        }
    }

    /**
     * Test that the selector applied to the DurableSubscription was successfully
     * transfered to the new store, and functions as expected with continued use
     * by monitoring message count while sending new messages to the topic.
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
            ManagedQueue dursubQueue = jmxUtils.getManagedQueue("clientid" + ":" + SUB_NAME);
            assertEquals("DurableSubscription backing queue should have 1 message on it initially", 
                          new Integer(1), dursubQueue.getMessageCount());
            
            // Create a connection and start it
            TopicConnection connection = (TopicConnection) getConnection();
            connection.start();
            
            // Send messages which don't match and do match the selector, checking message count
            TopicSession pubSession = connection.createTopicSession(true, org.apache.qpid.jms.Session.SESSION_TRANSACTED);
            Topic topic = pubSession.createTopic(TOPIC_NAME);
            TopicPublisher publisher = pubSession.createPublisher(topic);
            
            BDBStoreUpgradeTestPreparer.publishMessages(pubSession, publisher, topic, DeliveryMode.PERSISTENT, 1*1024, 1, "false");
            pubSession.commit();
            assertEquals("DurableSubscription backing queue should still have 1 message on it", 
                        new Integer(1), dursubQueue.getMessageCount());
            
            BDBStoreUpgradeTestPreparer.publishMessages(pubSession, publisher, topic, DeliveryMode.PERSISTENT, 1*1024, 1, "true");
            pubSession.commit();
            assertEquals("DurableSubscription backing queue should now have 2 messages on it", 
                        new Integer(2), dursubQueue.getMessageCount());

            dursubQueue.clearQueue();
            pubSession.close();
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

        consumeDurableSubscriptionMessages(connection);
        consumeQueueMessages(connection, false);
    }

    /**
     * Tests store migration containing messages for non-existing queue.
     *
     * @throws Exception
     */
    public void testMigrationOfMessagesForNonExistingQueues() throws Exception
    {
        stopBroker();

        // copy store data into a new location for adding of phantom message
        File storeLocation = new File(_fromDir);
        File target = new File(_toDirTwice);
        if (!target.exists())
        {
            target.mkdirs();
        }
        FileUtils.copyRecursive(storeLocation, target);

        // delete migrated data
        File directory = new File(_toDir);
        if (directory.exists() && directory.isDirectory())
        {
            FileUtils.delete(directory, true);
        }

        // test data
        String nonExistingQueueName = getTestQueueName();
        String messageText = "Test Phantom Message";

        // add message
        addMessageForNonExistingQueue(target, VERSION_4, nonExistingQueueName, messageText);

        String[] inputs = { "Yes", "Yes", "Yes" };
        upgradeBrokerStoreInInterractiveMode(_toDirTwice, _toDir, inputs);

        // start broker
        startBroker();

        // Create a connection and start it
        Connection connection = getConnection();
        connection.start();

        // consume a message for non-existing store
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(nonExistingQueueName);
        MessageConsumer messageConsumer = session.createConsumer(queue);
        Message message = messageConsumer.receive(1000);

        // assert consumed message
        assertNotNull("Message was not migrated!", message);
        assertTrue("Unexpected message received!", message instanceof TextMessage);
        String text = ((TextMessage) message).getText();
        assertEquals("Message migration failed!", messageText, text);
    }

    /**
     * An utility method to upgrade broker with simulation user interactions
     *
     * @param fromDir
     *            location of the store to migrate
     * @param toDir
     *            location of where migrated data will be stored
     * @param inputs
     *            user answers on upgrade tool questions
     * @throws Exception
     */
    private void upgradeBrokerStoreInInterractiveMode(String fromDir, String toDir, final String[] inputs)
            throws Exception
    {
        // save to restore system.in after data migration
        InputStream stdin = System.in;

        // set fake system in to simulate user interactions
        // FIXME: it is a quite dirty simulator of system input but it does the job
        System.setIn(new InputStream()
        {

            int counter = 0;

            public synchronized int read(byte b[], int off, int len)
            {
                byte[] src = (inputs[counter] + "\n").getBytes();
                System.arraycopy(src, 0, b, off, src.length);
                counter++;
                return src.length;
            }

            @Override
            public int read() throws IOException
            {
                return -1;
            }
        });

        try
        {
            // Upgrade the test store.
            new BDBStoreUpgrade(fromDir, toDir, null, true, true).upgradeFromVersion(VERSION_4);
        }
        finally
        {
            // restore system in
            System.setIn(stdin);
        }
    }

    @SuppressWarnings("unchecked")
    private void addMessageForNonExistingQueue(File storeLocation, int storeVersion, String nonExistingQueueName,
            String messageText) throws Exception
    {
        final AMQShortString queueName = new AMQShortString(nonExistingQueueName);
        BDBMessageStore store = new BDBMessageStore(storeVersion);
        store.configure(storeLocation, false);
        try
        {
            store.start();

            // store message objects
            ByteBuffer completeContentBody = ByteBuffer.wrap(messageText.getBytes("UTF-8"));
            long bodySize = completeContentBody.limit();
            MessagePublishInfo pubInfoBody = new MessagePublishInfoImpl(new AMQShortString("amq.direct"), false,
                    false, queueName);
            BasicContentHeaderProperties props = new BasicContentHeaderProperties();
            props.setDeliveryMode(Integer.valueOf(BasicContentHeaderProperties.PERSISTENT).byteValue());
            props.setContentType("text/plain");
            props.setType("text/plain");
            props.setMessageId("whatever");
            props.setEncoding("UTF-8");
            props.getHeaders().setString("Test", "MST");
            MethodRegistry methodRegistry = MethodRegistry.getMethodRegistry(ProtocolVersion.v0_9);
            int classForBasic = methodRegistry.createBasicQosOkBody().getClazz();
            ContentHeaderBody contentHeaderBody = new ContentHeaderBody(classForBasic, 1, props, bodySize);

            // add content entry to database
            final long messageId = store.getNewMessageId();
            TupleBinding<MessageContentKey> contentKeyTB = new MessageContentKeyTupleBindingFactory(storeVersion).getInstance();
            MessageContentKey contentKey = null;
            if (storeVersion == VERSION_4)
            {
                contentKey = new MessageContentKey_4(messageId, 0);
            }
            else
            {
                throw new Exception(storeVersion + " is not supported");
            }
            DatabaseEntry key = new DatabaseEntry();
            contentKeyTB.objectToEntry(contentKey, key);
            DatabaseEntry data = new DatabaseEntry();
            ContentTB contentTB = new ContentTB();
            contentTB.objectToEntry(completeContentBody, data);
            store.getContentDb().put(null, key, data);

            // add meta data entry to database
            TupleBinding<Long> longTB = TupleBinding.getPrimitiveBinding(Long.class);
            TupleBinding<Object> metaDataTB = new MessageMetaDataTupleBindingFactory(storeVersion).getInstance();
            key = new DatabaseEntry();
            data = new DatabaseEntry();
            longTB.objectToEntry(new Long(messageId), key);
            MessageMetaData metaData = new MessageMetaData(pubInfoBody, contentHeaderBody, 1);
            metaDataTB.objectToEntry(metaData, data);
            store.getMetaDataDb().put(null, key, data);

            // add delivery entry to database
            TransactionLogResource mockQueue = new TransactionLogResource()
            {
                public String getResourceName()
                {
                    return queueName.asString();
                }
            };

            EnqueableMessage mockMessage = new EnqueableMessage()
            {
    
                public long getMessageNumber()
                {
                    return messageId;
                }

                public boolean isPersistent()
                {
                    return true;
                }

                public StoredMessage getStoredMessage()
                {
                    return null;
                }
            };

            MessageStore log = (MessageStore) store;
            MessageStore.Transaction txn = log.newTransaction();
            txn.enqueueMessage(mockQueue, mockMessage);
            txn.commitTran();
        }
        finally
        {
            // close store
            store.close();
        }
    }

    private void consumeDurableSubscriptionMessages(Connection connection) throws Exception
    {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(TOPIC_NAME);

        TopicSubscriber durSub = session.createDurableSubscriber(topic, SUB_NAME,"testprop='true'", false);

        // Retrieve the matching message 
        Message m = durSub.receive(2000);
        assertNotNull("Failed to receive an expected message", m);
        assertEquals("Selector property did not match", "true", m.getStringProperty("testprop"));
        assertEquals("ID property did not match", 1, m.getIntProperty("ID"));
        assertEquals("Message content was not as expected",BDBStoreUpgradeTestPreparer.generateString(1024) , ((TextMessage)m).getText());

        // Verify that neither the non-matching or uncommitted message are received
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

    private void upgradeBrokerStore(String fromDir, String toDir) throws Exception
    {
        new BDBStoreUpgrade(_fromDir, _toDir, null, false, true).upgradeFromVersion(VERSION_4);
    }
}
