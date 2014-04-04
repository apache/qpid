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
package org.apache.qpid.server.store;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.util.FileUtils;

public class SplitStoreTest extends QpidBrokerTestCase
{
    private String _messageStorePath;
    private String _configStorePath;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        String virtualHostWorkDir = System.getProperty("QPID_WORK") + File.separator + TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST + File.separator;
        _messageStorePath =  virtualHostWorkDir  + "messageStore";
        _configStorePath =  virtualHostWorkDir  + "configStore";
    }

    @Override
    public void startBroker() throws Exception
    {
        // Overridden to prevent QBTC starting the Broker.
    }

    public void testJsonConfigurationStoreWithPersistentMessageStore() throws Exception
    {
        Map<String, Object> configurationStoreSettings = new HashMap<String, Object>();
        configurationStoreSettings.put(DurableConfigurationStore.STORE_TYPE, JsonFileConfigStore.TYPE);
        configurationStoreSettings.put(DurableConfigurationStore.STORE_PATH, _configStorePath);

        doTest(configurationStoreSettings);
    }

    public void testSeparateConfigurationAndMessageStoresOfTheSameType() throws Exception
    {
        Map<String, Object> configurationStoreSettings = new HashMap<String, Object>();
        configurationStoreSettings.put(DurableConfigurationStore.STORE_TYPE, getTestProfileMessageStoreType());
        configurationStoreSettings.put(DurableConfigurationStore.STORE_PATH, _configStorePath);

        doTest(configurationStoreSettings);
    }

    private void configureAndStartBroker(Map<String, Object> configurationStoreSettings) throws Exception
    {
        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(MessageStore.STORE_TYPE, getTestProfileMessageStoreType());
        messageStoreSettings.put(MessageStore.STORE_PATH, _messageStorePath);

        TestBrokerConfiguration config = getBrokerConfiguration();
        config.setObjectAttribute(VirtualHost.class, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST, VirtualHost.MESSAGE_STORE_SETTINGS, messageStoreSettings);
        config.setObjectAttribute(VirtualHost.class, TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST, VirtualHost.CONFIGURATION_STORE_SETTINGS, configurationStoreSettings);

        super.startBroker();
    }

    private void doTest(Map<String, Object> configurationStoreSettings) throws Exception
    {
        configureAndStartBroker(configurationStoreSettings);

        Connection connection = getConnection();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Queue queue = session.createQueue(getTestQueueName());
        session.createConsumer(queue).close(); // Create durable queue by side effect
        sendMessage(session, queue, 2);
        connection.close();

        restartBroker();

        setTestSystemProperty(ClientProperties.QPID_DECLARE_QUEUES_PROP_NAME, "false");
        connection = getConnection();
        connection.start();
        session = connection.createSession(true, Session.SESSION_TRANSACTED);
        MessageConsumer consumer = session.createConsumer(queue);
        Message message = consumer.receive(1000);
        session.commit();

        assertNotNull("Message was not received after first restart", message);
        assertEquals("Unexpected message received after first restart", 0, message.getIntProperty(INDEX));

        stopBroker();
        File messageStoreFile = new File(_messageStorePath);
        FileUtils.delete(messageStoreFile, true);
        assertFalse("Store folder was not deleted", messageStoreFile.exists());
        super.startBroker();

        connection = getConnection();
        connection.start();
        session = connection.createSession(true, Session.SESSION_TRANSACTED);
        consumer = session.createConsumer(queue);
        message = consumer.receive(500);

        assertNull("Message was received after store removal", message);
    }

}
