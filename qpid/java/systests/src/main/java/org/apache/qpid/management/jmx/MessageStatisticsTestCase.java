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
package org.apache.qpid.management.jmx;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

/**
 * Test generation of message statistics.
 */
public abstract class MessageStatisticsTestCase extends QpidBrokerTestCase
{
    protected static final String USER = "admin";
    
    protected JMXTestUtils _jmxUtils;
    protected Connection _test, _dev, _local;
    protected String _queueName = "statistics";
    protected Destination _queue;
    protected String _brokerUrl;

    @Override
    public void setUp() throws Exception
    {
        _jmxUtils = new JMXTestUtils(this, USER, USER);
        _jmxUtils.setUp();
        
        configureStatistics();
        
        super.setUp();
        
        _brokerUrl = getBroker().toString();
        _test = new AMQConnection(_brokerUrl, USER, USER, "clientid", "test");
        _dev = new AMQConnection(_brokerUrl, USER, USER, "clientid", "development");
        _local = new AMQConnection(_brokerUrl, USER, USER, "clientid", "localhost");
        
        _test.start();
        _dev.start();
        _local.start();
        
        _jmxUtils.open();
    }
    
    protected void createQueue(Session session) throws AMQException, JMSException
    {
        _queue = new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, _queueName);
        if (!((AMQSession<?,?>) session).isQueueBound((AMQDestination) _queue))
        {
	        ((AMQSession<?,?>) session).createQueue(new AMQShortString(_queueName), false, true, false, null);
	        ((AMQSession<?,?>) session).declareAndBind((AMQDestination) new AMQQueue(ExchangeDefaults.DIRECT_EXCHANGE_NAME, _queueName));
        }
    }
        

    @Override
    public void tearDown() throws Exception
    {
        _jmxUtils.close();
        
        _test.close();
        _dev.close();
        _local.close();
        
        super.tearDown();
    }
    
    /**
     * Configure statistics generation properties on the broker.
     */
    public abstract void configureStatistics() throws Exception;

    protected void sendUsing(Connection con, int number, int size) throws Exception
    {
        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        createQueue(session);
        MessageProducer producer = session.createProducer(_queue);
        String content = new String(new byte[size]);
        TextMessage msg = session.createTextMessage(content);
        for (int i = 0; i < number; i++)
        {
            producer.send(msg);
        }
    }
    
    /**
     * Asserts that the actual value is within the expected value plus or
     * minus the given error.
     */
    public void assertApprox(String message, double error, double expected, double actual)
    {
        double min = expected * (1.0d - error);
        double max = expected * (1.0d + error);
        String assertion = String.format("%s: expected %f +/- %d%%, actual %f",
                message, expected, (int) (error * 100.0d), actual);
        assertTrue(assertion, actual > min && actual < max);
    }
}
