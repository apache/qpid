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
 *
 */
package org.apache.qpid.systest.disttest.clientonly;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.client.ClientState;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.jms.JmsMessageAdaptor;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.CreateSessionCommand;
import org.apache.qpid.disttest.message.NoOpCommand;
import org.apache.qpid.disttest.message.RegisterClientCommand;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StopClientCommand;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;

public class BasicDistributedClientTest extends DistributedTestSystemTestBase
{
    private Session _session = null;
    private MessageProducer _clientQueueProducer;
    private Client _client;
    private ControllerQueue _controllerQueue;
    private ClientJmsDelegate _clientJmsDelegate = null;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _controllerQueue = new ControllerQueue(_connection, _context);
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        _clientJmsDelegate = new ClientJmsDelegate(_context);
        _client = new Client(_clientJmsDelegate);
        _client.start();
    }

    @Override
    protected void tearDown() throws Exception
    {
        try
        {
            _controllerQueue.close();
            if (_session != null)
            {
                _session.close();
            }
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testClientSendsRegistrationMessage() throws Exception
    {
        final RegisterClientCommand regClientCommand = _controllerQueue.getNext();

        assertNotNull("Client must have a non-null name", regClientCommand.getClientName());
        assertEquals("Unexpected client name", _clientJmsDelegate.getClientName(), regClientCommand.getClientName());
        assertNotNull("Client queue name should not be null", regClientCommand.getClientQueueName());
    }

    public void testClientSendsCommandResponses() throws Exception
    {
        final RegisterClientCommand registrationCommand = _controllerQueue.getNext();
        createClientQueueProducer(registrationCommand);

        sendCommandToClient(new NoOpCommand());

        final Response responseCommand = _controllerQueue.getNext();
        assertEquals("Incorrect client message type", CommandType.RESPONSE, responseCommand.getType());
    }

    public void testClientCanBeStoppedViaCommand() throws Exception
    {
        assertEquals("Expected client to be in STARTED state", ClientState.READY, _client.getState());

        final RegisterClientCommand registrationCommand = _controllerQueue.getNext();
        createClientQueueProducer(registrationCommand);

        final Command stopClientCommand = new StopClientCommand();
        sendCommandToClient(stopClientCommand);

        _client.waitUntilStopped(1000);

        Response response = _controllerQueue.getNext();
        assertNotNull(response);
        assertFalse("response shouldn't contain error", response.hasError());

        assertEquals("Expected client to be in STOPPED state", ClientState.STOPPED, _client.getState());
    }

    public void testClientCanCreateTestConnection() throws Exception
    {
        assertEquals("Unexpected number of test connections", 0, _clientJmsDelegate.getNoOfTestConnections());

        final RegisterClientCommand registration = _controllerQueue.getNext();
        createClientQueueProducer(registration);

        final CreateConnectionCommand createConnectionCommand = new CreateConnectionCommand();
        createConnectionCommand.setConnectionName("newTestConnection");
        createConnectionCommand.setConnectionFactoryName("connectionfactory");

        sendCommandToClient(createConnectionCommand);
        Response response = _controllerQueue.getNext();

        assertFalse("Response message should not have indicated an error", response.hasError());
        assertEquals("Unexpected number of test connections", 1, _clientJmsDelegate.getNoOfTestConnections());
    }

    public void testClientCanCreateTestSession() throws Exception
    {
        assertEquals("Unexpected number of test sessions", 0, _clientJmsDelegate.getNoOfTestSessions());

        final RegisterClientCommand registration = _controllerQueue.getNext();
        createClientQueueProducer(registration);

        final CreateConnectionCommand createConnectionCommand = new CreateConnectionCommand();
        createConnectionCommand.setConnectionName("newTestConnection");
        createConnectionCommand.setConnectionFactoryName("connectionfactory");

        sendCommandToClient(createConnectionCommand);
        Response response = _controllerQueue.getNext();
        assertFalse("Response message should not have indicated an error", response.hasError());

        final CreateSessionCommand createSessionCommand = new CreateSessionCommand();
        createSessionCommand.setConnectionName("newTestConnection");
        createSessionCommand.setSessionName("newTestSession");
        createSessionCommand.setAcknowledgeMode(Session.AUTO_ACKNOWLEDGE);

        sendCommandToClient(createSessionCommand);
        response = _controllerQueue.getNext();

        assertFalse("Response message should not have indicated an error", response.hasError());
        assertEquals("Unexpected number of test sessions", 1, _clientJmsDelegate.getNoOfTestSessions());
    }

    private void sendCommandToClient(final Command command) throws JMSException
    {
        final Message message = JmsMessageAdaptor.commandToMessage(_session, command);
        _clientQueueProducer.send(message);
    }

    private void createClientQueueProducer(
            final RegisterClientCommand registration) throws JMSException
    {
        final Destination clientCommandQueue = createDestinationFromRegistration(registration);
        _clientQueueProducer  = _session.createProducer(clientCommandQueue);
    }

    private Queue createDestinationFromRegistration(
            final RegisterClientCommand registrationCommand)
            throws JMSException
    {
        String clientQueueName = registrationCommand.getClientQueueName();
        assertNotNull("Null client queue in register message", clientQueueName);
        return _session.createQueue(clientQueueName);
    }
}
