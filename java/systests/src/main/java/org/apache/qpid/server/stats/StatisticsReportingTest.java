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
package org.apache.qpid.server.stats;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.test.utils.TestBrokerConfiguration;
import org.apache.qpid.util.LogMonitor;

import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Test generation of message/data statistics reporting and the ability
 * to control from the configuration file.
 */
public class StatisticsReportingTest extends QpidBrokerTestCase
{
    private static final String VHOST_NAME1 = "vhost1";
    private static final String VHOST_NAME2 = "vhost2";
    private static final String VHOST_NAME3 = "vhost3";
    private static long STATISTICS_REPORTING_PERIOD_IN_SECONDS = 10l;

    protected LogMonitor _monitor;
    protected static final String USER = "admin";

    protected Connection _conToVhost1, _conToVhost2, _conToVhost3;
    protected String _queueName = "statistics";
    protected Destination _queue;
    protected String _brokerUrl;
    private long _startTestTime;

    @Override
    public void setUp() throws Exception
    {
        createTestVirtualHost(0, VHOST_NAME1);
        createTestVirtualHost(0, VHOST_NAME2);
        createTestVirtualHost(0, VHOST_NAME3);

        if (getName().equals("testEnabledStatisticsReporting"))
        {
            TestBrokerConfiguration config = getBrokerConfiguration();
            config.removeObjectConfiguration(TestBrokerConfiguration.ENTRY_NAME_VIRTUAL_HOST);
            config.setBrokerAttribute(Broker.STATISTICS_REPORTING_PERIOD, STATISTICS_REPORTING_PERIOD_IN_SECONDS);
        }

        _monitor = new LogMonitor(_outputFile);
        _startTestTime = System.currentTimeMillis();

        super.setUp();

        _brokerUrl = getBroker().toString();
        _conToVhost1 = new AMQConnection(_brokerUrl, USER, USER, "clientid", VHOST_NAME1);
        _conToVhost2 = new AMQConnection(_brokerUrl, USER, USER, "clientid", VHOST_NAME2);
        _conToVhost3 = new AMQConnection(_brokerUrl, USER, USER, "clientid", VHOST_NAME3);

        _conToVhost1.start();
        _conToVhost2.start();
        _conToVhost3.start();
    }

    @Override
    public void tearDown() throws Exception
    {
        _conToVhost1.close();
        _conToVhost2.close();
        _conToVhost3.close();

        super.tearDown();
    }

    /**
     * Test enabling reporting.
     */
    public void testEnabledStatisticsReporting() throws Exception
    {
        sendUsing(_conToVhost1, 10, 100);
        sendUsing(_conToVhost2, 20, 100);
        sendUsing(_conToVhost3, 15, 100);

        Thread.sleep(STATISTICS_REPORTING_PERIOD_IN_SECONDS * 1000);

        Map<String, List<String>> brokerStatsData = _monitor.findMatches("BRK-1008", "BRK-1009", "VHT-1003", "VHT-1004");
        long endTestTime = System.currentTimeMillis();

        int maxNumberOfReports = (int)((endTestTime - _startTestTime)/STATISTICS_REPORTING_PERIOD_IN_SECONDS);

        int brk1008LinesNumber = brokerStatsData.get("BRK-1008").size();
        int brk1009LinesNumber = brokerStatsData.get("BRK-1009").size();
        int vht1003LinesNumber = brokerStatsData.get("VHT-1003").size();
        int vht1004LinesNumber = brokerStatsData.get("VHT-1004").size();

        assertTrue("Incorrect number of broker data stats log messages:" + brk1008LinesNumber, 2 <= brk1008LinesNumber
                && brk1008LinesNumber <= maxNumberOfReports * 2);
        assertTrue("Incorrect number of broker message stats log messages:" + brk1009LinesNumber, 2 <= brk1009LinesNumber
                && brk1009LinesNumber <= maxNumberOfReports * 2);
        assertTrue("Incorrect number of virtualhost data stats log messages:" + vht1003LinesNumber, 6 <= vht1003LinesNumber
                && vht1003LinesNumber <= maxNumberOfReports * 6);
        assertTrue("Incorrect number of virtualhost message stats log messages: " + vht1004LinesNumber, 6 <= vht1004LinesNumber
                && vht1004LinesNumber <= maxNumberOfReports * 6);
    }

    /**
     * Test not enabling reporting.
     */
    public void testNotEnabledStatisticsReporting() throws Exception
    {
        sendUsing(_conToVhost1, 10, 100);
        sendUsing(_conToVhost2, 20, 100);
        sendUsing(_conToVhost3, 15, 100);

        Thread.sleep(10 * 1000); // 15s

        List<String> brokerStatsData = _monitor.findMatches("BRK-1008");
        List<String> brokerStatsMessages = _monitor.findMatches("BRK-1009");
        List<String> vhostStatsData = _monitor.findMatches("VHT-1003");
        List<String> vhostStatsMessages = _monitor.findMatches("VHT-1004");

        assertEquals("Incorrect number of broker data stats log messages", 0, brokerStatsData.size());
        assertEquals("Incorrect number of broker message stats log messages", 0, brokerStatsMessages.size());
        assertEquals("Incorrect number of virtualhost data stats log messages", 0, vhostStatsData.size());
        assertEquals("Incorrect number of virtualhost message stats log messages", 0, vhostStatsMessages.size());
    }

    private void sendUsing(Connection con, int number, int size) throws Exception
    {
        Session session = con.createSession(true, Session.SESSION_TRANSACTED);
        createQueue(session);
        MessageProducer producer = session.createProducer(_queue);
        String content = new String(new byte[size]);
        TextMessage msg = session.createTextMessage(content);
        for (int i = 0; i < number; i++)
        {
            producer.send(msg);
        }
        session.commit();
        session.close();
    }

    private void createQueue(Session session) throws AMQException, JMSException
    {
        _queue = new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, _queueName);
        if (!((AMQSession<?,?>) session).isQueueBound((AMQDestination) _queue))
        {
            ((AMQSession<?,?>) session).createQueue(new AMQShortString(_queueName), false, true, false, null);
            ((AMQSession<?,?>) session).declareAndBind((AMQDestination) new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, _queueName));
        }
    }
}
