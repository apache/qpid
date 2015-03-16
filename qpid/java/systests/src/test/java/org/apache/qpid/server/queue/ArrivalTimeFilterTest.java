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

public class ArrivalTimeFilterTest extends QpidBrokerTestCase
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


    public void testArrivalTime0() throws AMQException, JMSException, InterruptedException
    {
        createDestinationWithFilter(0);
        final MessageProducer prod = _session.createProducer(_queue);
        TextMessage textMessage = _session.createTextMessage("hello");
        prod.send(textMessage);

        Thread.sleep(100);

        MessageConsumer cons = _session.createConsumer(_queue);

        assertNull("Message should not be received", cons.receive(500));

        textMessage = _session.createTextMessage("hello");
        prod.send( textMessage);

        Message receivedMsg = cons.receive(500);
        assertNotNull("Message should be received", receivedMsg);
    }


    public void testArrivalTime1000() throws AMQException, JMSException, InterruptedException
    {
        createDestinationWithFilter(1000);
        final MessageProducer prod = _session.createProducer(_queue);
        TextMessage textMessage = _session.createTextMessage("hello");
        prod.send(textMessage);

        Thread.sleep(100);

        MessageConsumer cons = _session.createConsumer(_queue);

        assertNotNull("Message should be received", cons.receive(500));

        textMessage = _session.createTextMessage("hello");
        prod.send( textMessage);

        Message receivedMsg = cons.receive(500);
        assertNotNull("Message should be received", receivedMsg);
    }

    private void createDestinationWithFilter(final int period) throws AMQException, JMSException
    {
        ((AMQSession<?,?>) _session).createQueue(new AMQShortString(_queueName), false, true, false, null);
        Queue queue = new org.apache.qpid.client.AMQQueue("amq.direct", _queueName);
        ((AMQSession<?,?>) _session).declareAndBind((AMQDestination)queue);
        _queue = _session.createQueue("direct://amq.direct/"+_queueName+"/"+_queueName+"?x-qpid-replay-period='"+period+"'");
    }


}
