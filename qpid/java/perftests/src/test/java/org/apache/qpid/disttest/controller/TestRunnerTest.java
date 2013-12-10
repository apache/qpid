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
package org.apache.qpid.disttest.controller;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.controller.config.QueueConfig;
import org.apache.qpid.disttest.controller.config.TestInstance;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CreateConnectionCommand;
import org.apache.qpid.disttest.message.NoOpCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StartTestCommand;
import org.apache.qpid.disttest.message.TearDownTestCommand;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestRunnerTest extends QpidTestCase
{
    private static final String TEST_NAME = "TEST_NAME";
    private static final String PARTICIPANT_NAME = "TEST_PARTICIPANT_NAME";
    private static final int ITERATION_NUMBER = 1;

    private static final String CLIENT1_REGISTERED_NAME = "client-uid1";
    private static final String CLIENT1_CONFIGURED_NAME = "client1";

    private static final long COMMAND_RESPONSE_TIMEOUT = 1000;
    private static final long TEST_RESULT_TIMEOUT = 2000;
    private static final long DELAY = 100;

    private TestRunner _testRunner;
    private TestInstance _testInstance;
    private ControllerJmsDelegate _respondingJmsDelegate;
    private ParticipatingClients _participatingClients;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _respondingJmsDelegate = mock(ControllerJmsDelegate.class);

        _participatingClients = mock(ParticipatingClients.class);
        when(_participatingClients.getRegisteredNameFromConfiguredName(CLIENT1_CONFIGURED_NAME)).thenReturn(CLIENT1_REGISTERED_NAME);
        when(_participatingClients.getConfiguredNameFromRegisteredName(CLIENT1_REGISTERED_NAME)).thenReturn(CLIENT1_CONFIGURED_NAME);
        when(_participatingClients.getRegisteredNames()).thenReturn(Collections.singleton(CLIENT1_REGISTERED_NAME));

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                final String clientName = (String)invocation.getArguments()[0];
                final Command command = (Command)invocation.getArguments()[1];
                _testRunner.processCommandResponse(new Response(clientName, command.getType()));
                return null;
            }
        }).when(_respondingJmsDelegate).sendCommandToClient(anyString(), isA(Command.class));
    }

    public void testSendConnectionCommandToClient()
    {
        _testInstance = createTestInstanceWithConnection();

        _testRunner = new TestRunner(_participatingClients, _testInstance , _respondingJmsDelegate, COMMAND_RESPONSE_TIMEOUT, TEST_RESULT_TIMEOUT);
        _testRunner.sendTestSetupCommands();

        verify(_respondingJmsDelegate).sendCommandToClient(eq(CLIENT1_REGISTERED_NAME), isA(CreateConnectionCommand.class));
    }

    public void testSendCommandToAllParticipatingClients()
    {
        _testRunner = new TestRunner(_participatingClients, mock(TestInstance.class), _respondingJmsDelegate, COMMAND_RESPONSE_TIMEOUT, TEST_RESULT_TIMEOUT);

        StartTestCommand startTestCommand = new StartTestCommand();
        _testRunner.sendCommandToParticipatingClients(startTestCommand);

        verify(_respondingJmsDelegate).sendCommandToClient(CLIENT1_REGISTERED_NAME, startTestCommand);
    }

    public void testWaitsForCommandResponses()
    {
        _testInstance = createTestInstanceWithConnection();
        _testRunner = new TestRunner(_participatingClients, _testInstance , _respondingJmsDelegate, COMMAND_RESPONSE_TIMEOUT, TEST_RESULT_TIMEOUT);

        _testRunner.sendTestSetupCommands();

        _testRunner.awaitCommandResponses();
    }

    public void testClientFailsToSendCommandResponseWithinTimeout()
    {
        ControllerJmsDelegate jmsDelegate = mock(ControllerJmsDelegate.class);

        _testInstance = createTestInstanceWithConnection();
        _testRunner = new TestRunner(_participatingClients, _testInstance , jmsDelegate, COMMAND_RESPONSE_TIMEOUT, TEST_RESULT_TIMEOUT);

        _testRunner.sendTestSetupCommands();
        // we don't call sendCommandResponseLater so controller should time out

        try
        {
            _testRunner.awaitCommandResponses();
            fail("Exception not thrown");
        }
        catch (DistributedTestException e)
        {
            // PASS
        }
    }

    public void testCreateAndDeleteQueues()
    {
        _testInstance =  mock(TestInstance.class);
        List<QueueConfig> queues = mock(List.class);
        when(_testInstance.getQueues()).thenReturn(queues);

        _testRunner = new TestRunner(_participatingClients, _testInstance, _respondingJmsDelegate, COMMAND_RESPONSE_TIMEOUT, TEST_RESULT_TIMEOUT);

        _testRunner.createQueues();
        verify(_respondingJmsDelegate).createQueues(queues);

        _testRunner.deleteQueues();
        verify(_respondingJmsDelegate).deleteQueues(queues);
    }

    public void testRun()
    {
        _testInstance = createTestInstanceWithOneParticipant();
        _testRunner = new TestRunner(_participatingClients, _testInstance , _respondingJmsDelegate, COMMAND_RESPONSE_TIMEOUT, TEST_RESULT_TIMEOUT);

        ParticipantResult incomingParticipantResult = new ParticipantResult(PARTICIPANT_NAME);
        incomingParticipantResult.setRegisteredClientName(CLIENT1_REGISTERED_NAME);
        sendTestResultsLater(_testRunner, incomingParticipantResult);

        TestResult results = _testRunner.run();

        verify(_respondingJmsDelegate).addCommandListener(isA(TestRunner.TestCommandResponseListener.class));
        verify(_respondingJmsDelegate).addCommandListener(isA(TestRunner.ParticipantResultListener.class));

        verify(_respondingJmsDelegate).createQueues(isA(List.class));

        verify(_respondingJmsDelegate).sendCommandToClient(eq(CLIENT1_REGISTERED_NAME), isA(StartTestCommand.class));
        verify(_respondingJmsDelegate).sendCommandToClient(eq(CLIENT1_REGISTERED_NAME), isA(NoOpCommand.class));
        verify(_respondingJmsDelegate).sendCommandToClient(eq(CLIENT1_REGISTERED_NAME), isA(TearDownTestCommand.class));

        verify(_respondingJmsDelegate).deleteQueues(isA(List.class));

        verify(_respondingJmsDelegate).removeCommandListener(isA(TestRunner.ParticipantResultListener.class));
        verify(_respondingJmsDelegate).removeCommandListener(isA(TestRunner.TestCommandResponseListener.class));

        List<ParticipantResult> participantResults = results.getParticipantResults();
        assertEquals(1, participantResults.size());
        ParticipantResult resultingParticipantResult = participantResults.get(0);

        assertResultHasCorrectTestDetails(resultingParticipantResult);
    }

    private void assertResultHasCorrectTestDetails(ParticipantResult resultingParticipantResult)
    {
        assertEquals("Test runner should have set configured name when it received participant results",
                CLIENT1_CONFIGURED_NAME, resultingParticipantResult.getConfiguredClientName());
        assertEquals("Test runner should have set test name when it received participant results",
                TEST_NAME, resultingParticipantResult.getTestName());
        assertEquals("Test runner should have set test iteration number when it received participant results",
                ITERATION_NUMBER, resultingParticipantResult.getIterationNumber());
    }


    private TestInstance createTestInstanceWithOneParticipant()
    {
        TestInstance testInstance = mock(TestInstance.class);

        List<CommandForClient> commands = Arrays.asList(
                new CommandForClient(CLIENT1_CONFIGURED_NAME, new NoOpCommand()));

        when(testInstance.createCommands()).thenReturn(commands);

        when(testInstance.getTotalNumberOfParticipants()).thenReturn(1);

        when(testInstance.getName()).thenReturn(TEST_NAME);

        List<QueueConfig> queues = mock(List.class);
        when(testInstance.getQueues()).thenReturn(queues);

        when(testInstance.getIterationNumber()).thenReturn(ITERATION_NUMBER);

        return testInstance;
    }

    private TestInstance createTestInstanceWithConnection()
    {
        TestInstance testInstance = mock(TestInstance.class);

        List<CommandForClient> commands = Arrays.asList(
                new CommandForClient(CLIENT1_CONFIGURED_NAME, new CreateConnectionCommand("conn1", "factory")));

        when(testInstance.createCommands()).thenReturn(commands);

        return testInstance;
    }

    private void sendTestResultsLater(final TestRunner runner, final ParticipantResult result)
    {
        doLater(new TimerTask()
        {
            @Override
            public void run()
            {
                runner.processParticipantResult(result);
            }
        }, DELAY);
    }

    private void doLater(TimerTask task, long delayInMillis)
    {
        Timer timer = new Timer();
        timer.schedule(task, delayInMillis);
    }

}
