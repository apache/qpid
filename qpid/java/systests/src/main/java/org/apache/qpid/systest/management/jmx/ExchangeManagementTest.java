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
package org.apache.qpid.systest.management.jmx;

import java.util.Collections;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.management.common.mbeans.ManagedBroker;
import org.apache.qpid.management.common.mbeans.ManagedExchange;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.test.utils.JMXTestUtils;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class ExchangeManagementTest extends QpidBrokerTestCase
{
    private static final String MESSAGE_PROPERTY_INDEX = "index";
    private static final String MESSAGE_PROPERTY_TEST = "test";
    private static final String MESSAGE_PROPERTY_DUMMY = "dummy";
    private static final String SELECTOR_ARGUMENT = AMQPFilterTypes.JMS_SELECTOR.toString();
    private static final String SELECTOR = MESSAGE_PROPERTY_TEST + "='test'";
    private static final String VIRTUAL_HOST = "test";

    private JMXTestUtils _jmxUtils;
    private ManagedBroker _managedBroker;
    private String _testQueueName;
    private ManagedExchange _directExchange;
    private ManagedExchange _topicExchange;
    private ManagedExchange _fanoutExchange;
    private ManagedExchange _headersExchange;
    private Connection _connection;
    private Session _session;

    public void setUp() throws Exception
    {
        getBrokerConfiguration().addJmxManagementConfiguration();

        // to test exchange selectors the publishing of unroutable messages should be allowed
        getBrokerConfiguration().setBrokerAttribute(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE, false);

        _jmxUtils = new JMXTestUtils(this);

        super.setUp();

        _jmxUtils.open();

        _managedBroker = _jmxUtils.getManagedBroker(VIRTUAL_HOST);
        _testQueueName = getTestName();
        _managedBroker.createNewQueue(_testQueueName, null, true);
        _directExchange = _jmxUtils.getManagedExchange(ExchangeDefaults.DIRECT_EXCHANGE_NAME);
        _topicExchange = _jmxUtils.getManagedExchange(ExchangeDefaults.TOPIC_EXCHANGE_NAME);
        _fanoutExchange = _jmxUtils.getManagedExchange(ExchangeDefaults.FANOUT_EXCHANGE_NAME);
        _headersExchange = _jmxUtils.getManagedExchange(ExchangeDefaults.HEADERS_EXCHANGE_NAME);

        _connection = getConnection();
        _connection.start();
        _session = _connection.createSession(true, Session.SESSION_TRANSACTED);
    }

    public void testCreateNewBindingWithArgumentsOnDirectExchange() throws Exception
    {
        String bindingKey = "test-direct-binding";

        _directExchange.createNewBinding(_testQueueName, bindingKey,
                Collections.<String, Object> singletonMap(SELECTOR_ARGUMENT, SELECTOR));

        bindingTest(_session.createQueue(bindingKey));
    }

    public void testCreateNewBindingWithArgumentsOnTopicExchange() throws Exception
    {
        String bindingKey = "test-topic-binding";

        _topicExchange.createNewBinding(_testQueueName, bindingKey,
                Collections.<String, Object> singletonMap(SELECTOR_ARGUMENT, SELECTOR));

        bindingTest(_session.createTopic(bindingKey));
    }

    public void testCreateNewBindingWithArgumentsOnFanoutExchange() throws Exception
    {
        _fanoutExchange.createNewBinding(_testQueueName, null,
                Collections.<String, Object> singletonMap(SELECTOR_ARGUMENT, SELECTOR));

        bindingTest(_session.createQueue("fanout://amq.fanout//?routingkey='routing-key-must-not-be-null'"));
    }

    public void testCreateNewBindingWithArgumentsOnHeadersExchange() throws Exception
    {
        // headers exchange uses 'dummy' property to match messages
        // i.e. all test messages have matching header value
        _headersExchange.createNewBinding(_testQueueName, "x-match=any,dummy=test",
                Collections.<String, Object> singletonMap(SELECTOR_ARGUMENT, SELECTOR));

        bindingTest(_session.createQueue("headers://amq.match//?routingkey='routing-key-must-not-be-null'"));
    }

    private void bindingTest(Destination destination) throws JMSException
    {
        publishMessages(destination, 4);
        receiveAndAssertMessages(2);
    }

    private void publishMessages(Destination destination, int messageNumber) throws JMSException
    {
        MessageProducer producer = _session.createProducer(destination);

        for (int i = 0; i < messageNumber; i++)
        {
            Message m = _session.createMessage();
            m.setStringProperty(MESSAGE_PROPERTY_TEST, i % 2 == 0 ? MESSAGE_PROPERTY_TEST : "");
            m.setIntProperty(MESSAGE_PROPERTY_INDEX, i);
            m.setStringProperty(MESSAGE_PROPERTY_DUMMY, "test");
            producer.send(m);
        }
        _session.commit();
    }

    private void receiveAndAssertMessages(int messageNumber) throws JMSException
    {
        MessageConsumer consumer = _session.createConsumer(_session.createQueue(_testQueueName));

        for (int i = 0; i < messageNumber; i++)
        {
            int index = i * 2;
            Message message = consumer.receive(1000l);
            assertNotNull("Expected message is not received at " + i, message);
            assertEquals("Unexpected test property at " + i, MESSAGE_PROPERTY_TEST,
                    message.getStringProperty(MESSAGE_PROPERTY_TEST));
            assertEquals("Unexpected index property at " + i, index, message.getIntProperty(MESSAGE_PROPERTY_INDEX));
        }

        Message message = consumer.receive(1000l);
        assertNull("Unexpected message received", message);
        _session.commit();
    }

}
