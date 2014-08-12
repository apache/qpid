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

import static org.apache.qpid.disttest.client.ClientState.READY;
import static org.apache.qpid.disttest.client.ClientState.RUNNING_TEST;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.client.ClientState;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.jms.JmsMessageAdaptor;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.CreateSessionCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.RegisterClientCommand;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StartTestCommand;
import org.apache.qpid.disttest.message.TearDownTestCommand;
import org.apache.qpid.systest.disttest.DistributedTestSystemTestBase;

public class DistributedClientTest extends DistributedTestSystemTestBase
{
    private static final String TEST_CONSUMER = "newTestConsumer";
    private static final String TEST_DESTINATION = "newDestination";
    private static final String TEST_PRODUCER_NAME = "newTestProducer";
    private static final String TEST_SESSION_NAME = "newTestSession";
    private static final String TEST_CONNECTION_NAME = "newTestConnection";

    private Session _session = null;
    private MessageProducer _clientQueueProducer;
    private Client _client;
    private ControllerQueue _controllerQueue;
    protected ClientJmsDelegate _clientJmsDelegate;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _controllerQueue = new ControllerQueue(_connection, _context);

        _clientJmsDelegate = new ClientJmsDelegate(_context);
        _client = new Client(_clientJmsDelegate);
        _client.start();

        final RegisterClientCommand registrationCommand = _controllerQueue.getNext();
        createClientQueueProducer(registrationCommand);

        createTestConnection(TEST_CONNECTION_NAME);
        createTestSession(TEST_CONNECTION_NAME, TEST_SESSION_NAME);

        assertEquals("Expected no test producers at start of test", 0, _clientJmsDelegate.getNoOfTestProducers());
        assertEquals("Expected no test consumers at start of test", 0, _clientJmsDelegate.getNoOfTestConsumers());
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

    public void testClientCanCreateTestProducer() throws Exception
    {
        assertEquals("Should initially have zero producers", 0, _clientJmsDelegate.getNoOfTestProducers());

        createTestProducer(TEST_SESSION_NAME, TEST_PRODUCER_NAME, TEST_DESTINATION);

        assertEquals("Should now have one test producer", 1, _clientJmsDelegate.getNoOfTestProducers());
    }

    public void testClientCanCreateTestConsumer() throws Exception
    {
        assertEquals("Should initially have no test consumers", 0, _clientJmsDelegate.getNoOfTestConsumers());

        createTestConsumer(TEST_SESSION_NAME, TEST_CONSUMER, TEST_DESTINATION);

        assertEquals("Should now have one test consumer", 1, _clientJmsDelegate.getNoOfTestConsumers());
    }

    public void testClientFailsToCreateSessionUsingInvalidConnection() throws Exception
    {
        int initialNoOfTestSessions = _clientJmsDelegate.getNoOfTestSessions();

        createTestSession("nonExistentConnection", TEST_SESSION_NAME, false /* shouldSucceed */);

        assertEquals("Number of test sessions should not have changed", initialNoOfTestSessions, _clientJmsDelegate.getNoOfTestSessions());
    }

    public void testClientFailsToCreateProducerUsingInvalidSession() throws Exception
    {
        int initialNoOfTestProducers = _clientJmsDelegate.getNoOfTestProducers();

        createTestProducer("invalidSessionName", TEST_PRODUCER_NAME, TEST_DESTINATION, false /* shouldSucceed */);

        assertEquals("Number of test producers should not have changed", initialNoOfTestProducers, _clientJmsDelegate.getNoOfTestProducers());
    }

    public void testClientFailsToCreateConsumerUsingInvalidSession() throws Exception
    {
        int initialNoOfTestConsumers = _clientJmsDelegate.getNoOfTestConsumers();

        createTestConsumer("invalidSessionName", TEST_CONSUMER, TEST_DESTINATION, false /* shouldSucceed */);

        assertEquals("Number of test consumers should not have changed", initialNoOfTestConsumers, _clientJmsDelegate.getNoOfTestConsumers());
    }

    public void testClientCanStartPerformingTests() throws Exception
    {
        createTestProducer(TEST_SESSION_NAME, TEST_PRODUCER_NAME, TEST_DESTINATION);

        sendCommandToClient(new StartTestCommand());

        validateStartTestResponseAndParticipantResults(CommandType.PRODUCER_PARTICIPANT_RESULT);

        assertState(_client, RUNNING_TEST);
    }

    public void testParticipantsSendResults() throws Exception
    {
        createTestProducer(TEST_SESSION_NAME, TEST_PRODUCER_NAME, TEST_DESTINATION);

        sendCommandToClient(new StartTestCommand());

        validateStartTestResponseAndParticipantResults(CommandType.PRODUCER_PARTICIPANT_RESULT);
    }

    /**
     * Need to validate both of these responses together because their order is non-deterministic
     * @param expectedParticipantResultCommandType TODO
     */
    private void validateStartTestResponseAndParticipantResults(CommandType expectedParticipantResultCommandType) throws JMSException
    {
        Map<CommandType, Command> responses = new HashMap<CommandType, Command>();
        _controllerQueue.addNextResponse(responses);
        _controllerQueue.addNextResponse(responses);

        ParticipantResult results = (ParticipantResult) responses.get(expectedParticipantResultCommandType);
        validateResponse(null, results, true);

        Response startTestResponse = (Response) responses.get(CommandType.RESPONSE);
        validateResponse(CommandType.START_TEST, startTestResponse, true);
    }

    public void testClientCannotStartPerformingTestsInNonReadyState() throws Exception
    {
        assertState(_client, READY);
        sendCommandAndValidateResponse(new StartTestCommand(), true);
        assertState(_client, RUNNING_TEST);

        // Send another start test command
        sendCommandAndValidateResponse(new StartTestCommand(), false /*should reject duplicate start command*/);
        assertState(_client, RUNNING_TEST);
    }

    public void testNonRunningClientIsUnaffectedByStopTestCommand() throws Exception
    {
        assertState(_client, READY);

        sendCommandAndValidateResponse(new TearDownTestCommand(), false);

        assertState(_client, READY);
    }

    private void sendCommandToClient(final Command command) throws Exception
    {
        final Message message = JmsMessageAdaptor.commandToMessage(_session, command);
        _clientQueueProducer.send(message);
        ((AMQSession<?, ?>)_session).sync();
    }

    private void sendCommandAndValidateResponse(final Command command, boolean shouldSucceed) throws Exception
    {
        sendCommandToClient(command);
        Response response = _controllerQueue.getNext();
        validateResponse(command.getType(), response, shouldSucceed);
    }

    private void sendCommandAndValidateResponse(final Command command) throws Exception
    {
        sendCommandAndValidateResponse(command, true);
    }

    private void createTestConnection(String connectionName) throws Exception
    {
        int initialNumberOfConnections = _clientJmsDelegate.getNoOfTestConnections();

        final CreateConnectionCommand createConnectionCommand = new CreateConnectionCommand();
        createConnectionCommand.setConnectionName(connectionName);
        createConnectionCommand.setConnectionFactoryName("connectionfactory");

        sendCommandAndValidateResponse(createConnectionCommand);

        int expectedNumberOfConnections = initialNumberOfConnections + 1;

        assertEquals("unexpected number of test connections", expectedNumberOfConnections, _clientJmsDelegate.getNoOfTestConnections());
    }

    private void createTestSession(String connectionName, String sessionName, boolean shouldSucceed) throws Exception
    {
        int initialNumberOfSessions = _clientJmsDelegate.getNoOfTestSessions();

        final CreateSessionCommand createSessionCommand = new CreateSessionCommand();
        createSessionCommand.setConnectionName(connectionName);
        createSessionCommand.setSessionName(sessionName);
        createSessionCommand.setAcknowledgeMode(Session.AUTO_ACKNOWLEDGE);

        sendCommandAndValidateResponse(createSessionCommand, shouldSucceed);

        int expectedNumberOfSessions = initialNumberOfSessions + (shouldSucceed ? 1 : 0);

        assertEquals("unexpected number of test sessions", expectedNumberOfSessions, _clientJmsDelegate.getNoOfTestSessions());
    }

    private void createTestSession(String connectionName, String sessionName) throws Exception
    {
        createTestSession(connectionName, sessionName, true);
    }

    private void createTestProducer(String sessionName, String producerName, String destinationName, boolean shouldSucceed) throws Exception
    {
        final CreateProducerCommand createProducerCommand = new CreateProducerCommand();
        createProducerCommand.setParticipantName(producerName);
        createProducerCommand.setSessionName(sessionName);
        createProducerCommand.setDestinationName(destinationName);
        createProducerCommand.setNumberOfMessages(100);

        sendCommandAndValidateResponse(createProducerCommand, shouldSucceed);
    }

    private void createTestProducer(String sessionName, String producerName, String destinationName) throws Exception
    {
        createTestProducer(sessionName, producerName, destinationName, true);
    }

    private void createTestConsumer(String sessionName, String consumerName, String destinationName, boolean shouldSucceed) throws Exception
    {
        final CreateConsumerCommand createConsumerCommand = new CreateConsumerCommand();
        createConsumerCommand.setSessionName(sessionName);
        createConsumerCommand.setDestinationName(destinationName);
        createConsumerCommand.setParticipantName(consumerName);
        createConsumerCommand.setNumberOfMessages(1);

        sendCommandAndValidateResponse(createConsumerCommand, shouldSucceed);
    }

    private void createTestConsumer(String sessionName, String consumerName, String destinationName) throws Exception
    {
        createTestConsumer(sessionName, consumerName, destinationName, true);
    }

    private void validateResponse(CommandType originatingCommandType, Response response, boolean shouldSucceed) throws JMSException
    {
        assertEquals("Response is a reply to the wrong command: " + response,
                originatingCommandType,
                response.getInReplyToCommandType());

        boolean shouldHaveError = !shouldSucceed;
        assertEquals("Response message " + response + " should have indicated hasError=" + shouldHaveError,
                shouldHaveError,
                response.hasError());
    }

    private void createClientQueueProducer(final RegisterClientCommand registration) throws JMSException
    {
        final Destination clientCommandQueue = createDestinationFromRegistration(registration);
        _clientQueueProducer = _session.createProducer(clientCommandQueue);
    }

    private Queue createDestinationFromRegistration(final RegisterClientCommand registrationCommand) throws JMSException
    {
        String clientQueueName = registrationCommand.getClientQueueName();
        assertNotNull("Null client queue in register message", clientQueueName);
        return _session.createQueue(clientQueueName);
    }

    private static void assertState(Client client, ClientState expectedState)
    {
        ClientState clientState = client.getState();
        assertEquals("Client should be in state: " + expectedState + " but is in state " + clientState, expectedState, clientState);
    }
}
