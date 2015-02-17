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

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class DefaultFiltersTest extends QpidBrokerTestCase
{

    private String _queueName;
    private Connection _connection;
    private Session _session;
    private Queue _queue;

    protected void setUp() throws Exception
    {
        super.setUp();

        _queueName = getTestQueueName();
        _connection = getConnection();
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _connection.start();
    }

    private void createQueueWithDefaultFilter(String selector) throws AMQException
    {
        final Map<String,Object> arguments = new HashMap<>();
        selector = selector.replace("\\", "\\\\");
        selector = selector.replace("\"", "\\\"");

        arguments.put("qpid.default_filters","{ \"x-filter-jms-selector\" : { \"x-filter-jms-selector\" : [ \""+selector+"\" ] } }");
        ((AMQSession<?,?>) _session).createQueue(new AMQShortString(_queueName), false, true, false, arguments);
        _queue = new org.apache.qpid.client.AMQQueue("amq.direct", _queueName);
        ((AMQSession<?,?>) _session).declareAndBind((AMQDestination)_queue);
    }

    public void testDefaultFilterIsApplied() throws AMQException, JMSException
    {
        createQueueWithDefaultFilter("foo = 1");
        final MessageProducer prod = _session.createProducer(_queue);
        TextMessage textMessage = _session.createTextMessage("hello");
        textMessage.setIntProperty("foo", 0);
        prod.send(textMessage);

        MessageConsumer cons = _session.createConsumer(_queue);

        assertNull("Message with foo=0 should not be received", cons.receive(500));

        textMessage = _session.createTextMessage("hello");
        textMessage.setIntProperty("foo", 1);
        prod.send( textMessage);

        Message receivedMsg = cons.receive(500);
        assertNotNull("Message with foo=1 should be received", receivedMsg);
        assertEquals("Property foo not as expected", 1, receivedMsg.getIntProperty("foo"));
    }


    public void testDefaultFilterIsOverridden() throws AMQException, JMSException
    {
        createQueueWithDefaultFilter("foo = 1");
        final MessageProducer prod = _session.createProducer(_queue);
        TextMessage textMessage = _session.createTextMessage("hello");
        textMessage.setIntProperty("foo", 0);
        prod.send(textMessage);

        MessageConsumer cons = _session.createConsumer(_queue, "foo = 0");

        Message receivedMsg = cons.receive(500);
        assertNotNull("Message with foo=0 should be received", receivedMsg);
        assertEquals("Property foo not as expected", 0, receivedMsg.getIntProperty("foo"));


        textMessage = _session.createTextMessage("hello");
        textMessage.setIntProperty("foo", 1);
        prod.send(textMessage);

        assertNull("Message with foo=1 should not be received", cons.receive(500));

    }

}
