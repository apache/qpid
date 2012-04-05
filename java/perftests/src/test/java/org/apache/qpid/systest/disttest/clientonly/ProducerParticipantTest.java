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
package org.apache.qpid.systest.disttest.clientonly;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;

import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.client.ParticipantExecutor;
import org.apache.qpid.disttest.client.ProducerParticipant;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;

public class ProducerParticipantTest extends DistributedTestSystemTestBase
{
    private MessageConsumer _consumer;
    private TestClientJmsDelegate _delegate;
    private Client _client;
    private ControllerQueue _controllerQueue;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _controllerQueue = new ControllerQueue(_connection, _context);
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _consumer = session.createConsumer(getTestQueue());

        _delegate = new TestClientJmsDelegate(getContext());
        _client = new Client(_delegate);
    }



    @Override
    protected void tearDown() throws Exception
    {
        _controllerQueue.close();
        super.tearDown();
    }



    public void testProduceNumberOfMessages() throws Exception
    {
        runTest(Session.AUTO_ACKNOWLEDGE, 100, 10, 0, 0);
    }

    protected void runTest(int acknowledgeMode, int messageSize, int numberOfMessages, int batchSize, long publishInterval) throws Exception
    {
        final CreateProducerCommand command = new CreateProducerCommand();
        command.setNumberOfMessages(numberOfMessages);
        command.setDeliveryMode(DeliveryMode.PERSISTENT);
        command.setParticipantName("test");
        command.setMessageSize(messageSize);
        command.setBatchSize(batchSize);
        command.setInterval(publishInterval);
        command.setSessionName("testSession");
        command.setDestinationName(getTestQueueName());

        Session session = _connection.createSession(Session.SESSION_TRANSACTED == acknowledgeMode, acknowledgeMode);

        _delegate.addConnection("name-does-not-matter", _connection);
        _delegate.addSession(command.getSessionName(), session);
        _delegate.createProducer(command);

        final ProducerParticipant producer = new ProducerParticipant(_delegate, command);

        new ParticipantExecutor(producer).start(_client);

        _connection.start();
        for (int i = 0; i < numberOfMessages; i++)
        {
            final Message m = _consumer.receive(1000l);
            assertNotNull("Expected message [" + i + "] is not received", m);
            assertTrue("Unexpected message", m instanceof TextMessage);
        }
        Message m = _consumer.receive(500l);
        assertNull("Unexpected message", m);

        ParticipantResult results = _controllerQueue.getNext();

        assertNotNull("no results", results);
        assertFalse(results.getStartInMillis() == 0);
        assertFalse(results.getEndInMillis() == 0);
    }

    static class TestClientJmsDelegate extends ClientJmsDelegate
    {

        public TestClientJmsDelegate(Context context)
        {
            super(context);
        }

        @Override
        public void addSession(final String sessionName, final Session newSession)
        {
            super.addSession(sessionName, newSession);
        }

        @Override
        public void addConnection(final String connectionName, final Connection newConnection)
        {
            super.addConnection(connectionName, newConnection);
        }
    }
}
