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
package org.apache.qpid.test.client.timeouts;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.test.VMTestCase;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.io.File;

/**
 * This tests that when the commit takes a long time(due to POST_COMMIT_DELAY) that the commit does not timeout
 * This test must be run in conjunction with SyncWaiteTimeoutDelay or be run with POST_COMMIT_DELAY > 30s to ensure
 * that the default value is being replaced.
 */
public class SyncWaitDelayTest extends VMTestCase
{
    protected static final Logger _logger = Logger.getLogger(SyncWaitDelayTest.class);

    final String QpidHome = System.getProperty("QPID_HOME");
    final File _configFile = new File(QpidHome, "etc/config.xml");

    private String VIRTUALHOST = "test";
    protected long POST_COMMIT_DELAY = 1000L;
    protected long SYNC_WRITE_TIMEOUT = POST_COMMIT_DELAY + 1000;

    protected Connection _connection;
    protected Session _session;
    protected Queue _queue;
    protected MessageConsumer _consumer;

    public void setUp() throws Exception
    {
        if (!_configFile.exists())
        {
            fail("Unable to test without config file:" + _configFile);
        }

        ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(_configFile);

        //For now disable management on all configured inVM broker.
        config.getConfiguration().setProperty("management.enabled", "false");

        Configuration testVirtualhost = config.getConfiguration().subset("virtualhosts.virtualhost." + VIRTUALHOST);
        testVirtualhost.setProperty("store.class", "org.apache.qpid.server.store.SlowMessageStore");
        testVirtualhost.setProperty("store.delays.commitTran.post", POST_COMMIT_DELAY);

        startVMBroker(2, config);

        //Set the syncWrite timeout to be just larger than the delay on the commitTran.
        setSystemProperty("amqj.default_syncwrite_timeout", String.valueOf(SYNC_WRITE_TIMEOUT));

        _brokerlist = "vm://:2";

        super.setUp();

        _connection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        //Create Queue
        _queue = (Queue) _context.lookup("queue");

        //Create Consumer
        _session = _connection.createSession(true, Session.SESSION_TRANSACTED);

        //Ensure Queue exists
        _session.createConsumer(_queue).close();
    }

    public void tearDown() throws Exception
    {
        //clean up
        _connection.close();

        stopVMBroker(2);

        super.tearDown();
    }

    public void test() throws JMSException
    {
        MessageProducer producer = _session.createProducer(_queue);

        Message message = _session.createTextMessage("Message");

        producer.send(message);

        long start = System.nanoTime();

        _logger.info("Calling Commit");

        try
        {
            _session.commit();
            long end = System.nanoTime();
            long time = (end - start);
            // As we are using Nano time ensure to multiply up the millis.
            assertTrue("Commit was quickier than the delay:" + time, time > 1000000L * POST_COMMIT_DELAY);
            assertFalse("Commit was to slower than the build in default", time > 1000000L * 1000 * 30);
        }
        catch (JMSException e)
        {
            fail(e.getMessage());
        }

    }

}
