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
package org.apache.qpid.test.client.message;

import org.apache.qpid.test.VMTestCase;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.UUID;

public class MessageToStringTest extends VMTestCase
{
    private Connection _connection;
    private Session _session;
    private Queue _queue;
    MessageConsumer _consumer;

    public void setUp() throws Exception
    {
        super.setUp();

        //Create Producer put some messages on the queue
        _connection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        //Create Queue
        _queue = (Queue) _context.lookup("queue");

        //Create Consumer
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _consumer = _session.createConsumer(_queue);

        _connection.start();
    }

    public void tearDown() throws Exception
    {
        //clean up
        _connection.close();

        super.tearDown();
    }

    public void testObjectMessage() throws JMSException
    {
        MessageProducer producer = _session.createProducer(_queue);

        //Create Sample Message using UUIDs
        UUID test = UUID.randomUUID();

        Message testMessage = _session.createObjectMessage(test);

        producer.send(testMessage);

        Message receivedMessage = _consumer.receive(1000);

        assertNotNull("Message was not received.", receivedMessage);

        assertNotNull("Message returned null from toString", receivedMessage.toString());

        UUID result = null;

        try
        {
            result = (UUID) ((ObjectMessage) receivedMessage).getObject();
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }

        assertEquals("UUIDs were not equal", test, result);
    }

}
