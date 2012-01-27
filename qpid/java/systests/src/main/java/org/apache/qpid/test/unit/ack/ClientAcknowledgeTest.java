/*
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
 */
package org.apache.qpid.test.unit.ack;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

public class ClientAcknowledgeTest extends QpidBrokerTestCase
{
    private static final long ONE_DAY_MS = 1000l * 60 * 60 * 24;
    private Connection _connection;
    private Queue _queue;
    private Session _consumerSession;
    private MessageConsumer _consumer;
    private MessageProducer _producer;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _queue = getTestQueue();
        _connection = getConnection();
    }

    /**
     * Test that message.acknowledge actually acknowledges, regardless of
     * the flusher thread period, by restarting the broker after calling
     * acknowledge, and then verifying after restart that the message acked
     * is no longer present. This test requires a persistent store.
     */
    public void testClientAckWithLargeFlusherPeriod() throws Exception
    {
        setTestClientSystemProperty("qpid.session.max_ack_delay", Long.toString(ONE_DAY_MS));
        _consumerSession = _connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        _consumer = _consumerSession.createConsumer(_queue);
        _connection.start();

        _producer = _consumerSession.createProducer(_queue);
        _producer.send(createNextMessage(_consumerSession, 1));
        _producer.send(createNextMessage(_consumerSession, 2));

        Message message = _consumer.receive(1000l);
        assertNotNull("Message has not been received", message);
        assertEquals("Unexpected message is received", 1, message.getIntProperty(INDEX));
        message.acknowledge();

        //restart broker to allow verification of the acks
        //without explicitly closing connection (which acks)
        restartBroker();

        // try to receive the message again, which should fail (as it was ackd)
        _connection = getConnection();
        _connection.start();
        _consumerSession = _connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        _consumer = _consumerSession.createConsumer(_queue);
        message = _consumer.receive(1000l);
        assertNotNull("Message has not been received", message);
        assertEquals("Unexpected message is received", 2, message.getIntProperty(INDEX));
    }
}
