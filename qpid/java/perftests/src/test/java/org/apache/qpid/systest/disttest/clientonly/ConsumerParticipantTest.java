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

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.client.ConsumerParticipant;
import org.apache.qpid.disttest.client.ParticipantExecutor;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;
import org.apache.qpid.systest.disttest.clientonly.ProducerParticipantTest.TestClientJmsDelegate;

public class ConsumerParticipantTest  extends DistributedTestSystemTestBase
{
    private MessageProducer _producer;
    private Session _session;
    private TestClientJmsDelegate _delegate;
    private Client _client;
    private ControllerQueue _controllerQueue;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _controllerQueue = new ControllerQueue(_connection, _context);
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _producer = _session.createProducer(getTestQueue());

        _delegate = new TestClientJmsDelegate(getContext());
        _client = new Client(_delegate);
    }


    @Override
    protected void tearDown() throws Exception
    {
        _controllerQueue.close();
        super.tearDown();
    }

    public void testConsumeNumberOfMessagesSynchronously() throws Exception
    {
        runTest(Session.AUTO_ACKNOWLEDGE, 10, 0, true);
    }

    public void testConsumeNumberOfMessagesAsynchronously() throws Exception
    {
        runTest(Session.AUTO_ACKNOWLEDGE, 10, 0, false);
    }

    public void testSelectors() throws Exception
    {
        final CreateConsumerCommand command = new CreateConsumerCommand();
        command.setNumberOfMessages(10);
        command.setSessionName("testSession");
        command.setDestinationName(getTestQueueName());
        command.setSelector("id=1");
        Session session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _delegate.addConnection("name-does-not-matter", _connection);
        _delegate.addSession(command.getSessionName(), session);

        ConsumerParticipant consumerParticipant =  new ConsumerParticipant(_delegate, command);
        _delegate.createConsumer(command);

        for (int i = 0; i < 20; i++)
        {
            Message message = _session.createMessage();
            if (i % 2 == 0)
            {
                message.setIntProperty("id", 0);
            }
            else
            {
                message.setIntProperty("id", 1);
            }
            _producer.send(message);
        }

        new ParticipantExecutor(consumerParticipant).start(_client);

        ParticipantResult results = _controllerQueue.getNext();
        assertNotNull("No results message recieved", results);
        assertEquals("Unexpected number of messages received", 10, results.getNumberOfMessagesProcessed());

        Session testQueueConsumerSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageConsumer testQueueConsumer = testQueueConsumerSession.createConsumer(getTestQueue());
        for (int i = 0; i < 10; i++)
        {
            Message message = testQueueConsumer.receive(2000);
            assertNotNull("Message is not received: " + message, message);
            assertEquals("Unexpected id value", 0, message.getIntProperty("id"));
        }
        Message message = testQueueConsumer.receive(2000);
        assertNull("Unexpected message remaining on test queue: " + message, message);

        _connection.stop();
    }

    protected void runTest(int acknowledgeMode, int numberOfMessages, int batchSize, boolean synchronous) throws Exception
    {
        final CreateConsumerCommand command = new CreateConsumerCommand();
        command.setNumberOfMessages(numberOfMessages);
        command.setBatchSize(batchSize);
        command.setSessionName("testSession");
        command.setDestinationName(getTestQueueName());
        command.setSynchronous(synchronous);

        Session session = _connection.createSession(Session.SESSION_TRANSACTED == acknowledgeMode, acknowledgeMode);

        _delegate.addConnection("name-does-not-matter", _connection);
        _delegate.addSession(command.getSessionName(), session);

        ConsumerParticipant consumerParticipant =  new ConsumerParticipant(_delegate, command);
        _delegate.createConsumer(command);

        for (int i = 0; i < numberOfMessages; i++)
        {
            _producer.send(_session.createMessage());
        }

        new ParticipantExecutor(consumerParticipant).start(_client);

        ParticipantResult results = _controllerQueue.getNext();
        assertNotNull("No results message recieved", results);

        Session testQueueConsumerSession = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        final MessageConsumer testQueueConsumer = testQueueConsumerSession.createConsumer(getTestQueue());
        Message message = testQueueConsumer.receive(2000);
        assertNull("Unexpected message remaining on test queue: " + message, message);

        _connection.stop();
    }
}
