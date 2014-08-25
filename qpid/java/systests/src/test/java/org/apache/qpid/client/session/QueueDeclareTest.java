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
package org.apache.qpid.client.session;

import java.util.Collections;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.url.AMQBindingURL;

public class QueueDeclareTest extends QpidBrokerTestCase
{
    private Connection _connection;
    private AMQSession<?, ?> _session;

    protected void setUp() throws Exception
    {
        super.setUp();

        _connection = getConnection();
        _session = (AMQSession<?, ?>) _connection.createSession(true, Session.SESSION_TRANSACTED);
    }

    public void testDeclareAndBindWhenQueueIsNotSpecifiedInDestinationUrl() throws Exception
    {
        AMQQueue destination = new AMQQueue(new AMQBindingURL("topic://amq.topic//?routingkey='testTopic'"));

        assertEquals("Queue name is generated in parser", AMQShortString.EMPTY_STRING, destination.getAMQQueueName());

        _session.declareAndBind(destination, FieldTable.convertToFieldTable(Collections.<String, Object> emptyMap()));

        assertFalse("Unexpected queue name: [" + destination.getAMQQueueName() + "]", AMQShortString.EMPTY_STRING.equals(destination.getAMQQueueName()));

        sendMessage(_session, destination, 1);

        MessageConsumer consumer = _session.createConsumer(destination);
        _connection.start();
        Message message = consumer.receive(1000l);
        assertNotNull("Message not received", message);
        _session.commit();
    }
}
