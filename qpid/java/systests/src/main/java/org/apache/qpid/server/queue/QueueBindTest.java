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
package org.apache.qpid.server.queue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.AMQBindingURL;

public class QueueBindTest extends QpidBrokerTestCase
{
    private Connection _connection;
    private AMQSession<?, ?> _session;

    protected void setUp() throws Exception
    {
        super.setUp();

        _connection = getConnection();
        _session = (AMQSession<?, ?>) _connection.createSession(true, Session.SESSION_TRANSACTED);
    }

    public void testQueueCannotBeReboundOnNonTopicExchange() throws Exception
    {
        runTestForNonTopicExhange(new AMQQueue(new AMQBindingURL("direct://amq.direct//" + getTestQueueName())));
        runTestForNonTopicExhange(new AMQQueue(new AMQBindingURL("fanout://amq.fanout//" + getTestQueueName()) + "?routingkey='"
                + getTestQueueName() + "'"));
    }

    public void testQueueCanBeReboundOnTopicExchange() throws Exception
    {
        AMQQueue destination = new AMQQueue(new AMQBindingURL("topic://amq.topic//" + getTestQueueName() + "?routingkey='"
                + getTestQueueName() + "'"));
        setTestClientSystemProperty("qpid.default_mandatory", "false");
        runTestForTopicExchange(destination);

    }

    private void runTestForTopicExchange(AMQDestination destination) throws AMQException, JMSException, Exception
    {
        // binding queue with empty arguments
        _session.declareAndBind(destination, FieldTable.convertToFieldTable(Collections.<String, Object> emptyMap()));

        // try to re-bind queue with a selector
        Map<String, Object> bindArguments = new HashMap<String, Object>();
        bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue().toString(), INDEX + "=0");
        _session.bindQueue(destination.getAMQQueueName(), destination.getRoutingKey(),
                FieldTable.convertToFieldTable(bindArguments), destination.getExchangeName(), destination);

        _connection.start();

        // repeat send/receive twice to make sure that selector is working
        for (int i = 0; i < 2; i++)
        {
            int numberOfMesssages = 2;
            sendMessage(_session, destination, numberOfMesssages);

            MessageConsumer consumer = _session.createConsumer(destination);
            Message m = consumer.receive(1000);
            assertNotNull("Message not received", m);
            assertEquals("Unexpected index", 0, m.getIntProperty(INDEX));
            _session.commit();

            m = consumer.receive(1000);
            assertNull("Message received", m);

            consumer.close();
        }
    }

    private void runTestForNonTopicExhange(AMQQueue destination) throws AMQException, Exception, JMSException
    {
        // binding queue with empty arguments
        _session.declareAndBind(destination, FieldTable.convertToFieldTable(Collections.<String, Object> emptyMap()));

        // try to re-bind queue with a selector
        Map<String, Object> bindArguments = new HashMap<String, Object>();
        bindArguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue().toString(), INDEX + "=0");
        _session.bindQueue(destination.getAMQQueueName(), destination.getRoutingKey(),
                FieldTable.convertToFieldTable(bindArguments), destination.getExchangeName(), destination);

        // send and receive to prove that selector is not used
        int numberOfMesssages = 2;
        sendMessage(_session, destination, numberOfMesssages);

        MessageConsumer consumer = _session.createConsumer(destination);
        _connection.start();

        for (int i = 0; i < numberOfMesssages; i++)
        {
            Message m = consumer.receive(1000l);
            assertNotNull("Message [" + i + "] not received with exchange " + destination.getExchangeName(), m);
            assertEquals("Unexpected index", i, m.getIntProperty(INDEX));
            _session.commit();
        }
        consumer.close();
    }
}
