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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.controller.config.QueueConfig;
import org.apache.qpid.disttest.controller.config.TestInstance;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StartTestCommand;
import org.apache.qpid.disttest.message.TearDownTestCommand;

public class TestRunner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRunner.class);

    private static final long PARTICIPANT_RESULTS_LOG_INTERVAL = 60000;
    public static final long WAIT_FOREVER = -1;

    private final long _commandResponseTimeout;

    private final Set<CommandType> _setOfResponsesToExpect = Collections.synchronizedSet(new HashSet<CommandType>());

    private final ParticipatingClients _participatingClients;

    private final TestInstance _testInstance;
    private ControllerJmsDelegate _jmsDelegate;

    private volatile CountDownLatch _commandResponseLatch = null;
    private final CountDownLatch _testResultsLatch;
    private final TestResult _testResult;

    /** Length of time to await test results or {@value #WAIT_FOREVER} */
    private final long _testResultTimeout;

    private Thread _removeQueuesShutdownHook = new Thread()
    {
        @Override
        public void run()
        {
            LOGGER.info("Shutdown intercepted: deleting test queues");
            try
            {
                deleteQueues();
            }
            catch (Exception t)
            {
                LOGGER.error("Failed to delete test queues during shutdown", t);
            }
        }
    };

    public TestRunner(ParticipatingClients participatingClients, TestInstance testInstance, ControllerJmsDelegate jmsDelegate, long commandResponseTimeout, long testResultTimeout)
    {
        _participatingClients = participatingClients;
        _testInstance = testInstance;
        _jmsDelegate = jmsDelegate;
        _commandResponseTimeout = commandResponseTimeout;
        _testResultsLatch = new CountDownLatch(testInstance.getTotalNumberOfParticipants());
        _testResultTimeout = testResultTimeout;
        _testResult = new TestResult(testInstance.getName());
    }

    public TestResult run()
    {
        final ParticipantResultListener participantResultListener = new ParticipantResultListener();
        TestCommandResponseListener testCommandResponseListener = new TestCommandResponseListener();

        try
        {
            _jmsDelegate.addCommandListener(testCommandResponseListener);
            _jmsDelegate.addCommandListener(participantResultListener);

            runParts();

            return _testResult;
        }
        catch(RuntimeException e)
        {
            LOGGER.error("Couldn't run test", e);
            throw e;
        }
        finally
        {
            _jmsDelegate.removeCommandListener(participantResultListener);
            _jmsDelegate.removeCommandListener(testCommandResponseListener);
        }
    }

    private void runParts()
    {
        boolean queuesCreated = false;

        try
        {
            createQueues();
            queuesCreated = true;
            Runtime.getRuntime().addShutdownHook(_removeQueuesShutdownHook);

            sendTestSetupCommands();
            awaitCommandResponses();
            sendCommandToParticipatingClients(new StartTestCommand());
            awaitCommandResponses();

            awaitTestResults();

            sendCommandToParticipatingClients(new TearDownTestCommand());
            awaitCommandResponses();
        }
        finally
        {

            if (queuesCreated)
            {
                deleteQueues();
            }

            Runtime.getRuntime().removeShutdownHook(_removeQueuesShutdownHook);
        }
    }

    void createQueues()
    {
        List<QueueConfig> queues = _testInstance.getQueues();
        if (!queues.isEmpty())
        {
            _jmsDelegate.createQueues(queues);
        }
    }

    void sendTestSetupCommands()
    {
        List<CommandForClient> commandsForAllClients = _testInstance.createCommands();
        final int numberOfCommandsToSend = commandsForAllClients.size();
        _commandResponseLatch = new CountDownLatch(numberOfCommandsToSend);

        LOGGER.debug("About to send {} command(s)", numberOfCommandsToSend);

        for (CommandForClient commandForClient : commandsForAllClients)
        {
            String configuredClientName = commandForClient.getClientName();
            String registeredClientName = _participatingClients.getRegisteredNameFromConfiguredName(configuredClientName);

            Command command = commandForClient.getCommand();

            LOGGER.debug("Sending command : {} ", command);

            sendCommandInternal(registeredClientName, command);
        }
    }

    void awaitCommandResponses()
    {
        awaitLatch(_commandResponseLatch, _commandResponseTimeout, "Timed out waiting for command responses");
    }


    void processCommandResponse(final Response response)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Received response for command " + response);
        }

        _commandResponseLatch.countDown();
        checkForResponseError(response);
    }


    void awaitTestResults()
    {
        long timeout = _testResultTimeout;
        DistributedTestException lastException = null;

        boolean waitForever = _testResultTimeout == WAIT_FOREVER;
        final long interval = waitForever ? PARTICIPANT_RESULTS_LOG_INTERVAL : Math.min(PARTICIPANT_RESULTS_LOG_INTERVAL, _testResultTimeout);

        while(_testResultsLatch.getCount() > 0 && (waitForever || timeout > 0))
        {
            try
            {
                awaitLatch(_testResultsLatch, interval, "still waiting for participant results");
            }
            catch (DistributedTestException e)
            {
                lastException = e;
                LOGGER.info(e.getMessage());
            }

            if (!waitForever)
            {
                timeout =- interval;
            }
        }

        if (_testResultsLatch.getCount() > 0)
        {
            throw lastException;
        }
    }

    void deleteQueues()
    {
        List<QueueConfig> queues = _testInstance.getQueues();
        if (!queues.isEmpty())
        {
            _jmsDelegate.deleteQueues(queues);
        }
    }

    void sendCommandToParticipatingClients(final Command command)
    {
        Collection<String> participatingRegisteredClients = _participatingClients.getRegisteredNames();
        final int numberOfClients = participatingRegisteredClients.size();
        _commandResponseLatch = new CountDownLatch(numberOfClients);

        LOGGER.debug("About to send command {} to {} clients", command, numberOfClients);

        for (final String clientName : participatingRegisteredClients)
        {
            LOGGER.debug("Sending command : {} ", command);
            sendCommandInternal(clientName, command);
        }
    }

    public void processParticipantResult(ParticipantResult result)
    {
        setOriginalTestDetailsOn(result);

        _testResult.addParticipantResult(result);
        LOGGER.debug("Received result " + result);

        _testResultsLatch.countDown();
        checkForResponseError(result);
    }

    private void setOriginalTestDetailsOn(ParticipantResult result)
    {
        // Client knows neither the configured client name nor test name
        String registeredClientName = result.getRegisteredClientName();
        String configuredClient = _participatingClients.getConfiguredNameFromRegisteredName(registeredClientName);

        result.setConfiguredClientName(configuredClient);
        result.setTestName(_testInstance.getName());
        result.setIterationNumber(_testInstance.getIterationNumber());
    }

    private void sendCommandInternal(String registeredClientName, Command command)
    {
        _setOfResponsesToExpect.add(command.getType());
        _jmsDelegate.sendCommandToClient(registeredClientName, command);
    }

    private void awaitLatch(CountDownLatch latch, long timeout, String message)
    {
        try
        {
            final boolean countedDownOK = latch.await(timeout, TimeUnit.MILLISECONDS);
            if (!countedDownOK)
            {
                final long latchCount = latch.getCount();
                String formattedMessage = "After " + timeout + "ms ... " + message + " ... Expecting " + latchCount + " more responses.";
                LOGGER.info(formattedMessage); // info rather than error because we time out periodically so we can log progress
                throw new DistributedTestException(formattedMessage);
            }
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private void checkForResponseError(final Response response)
    {
        if (response.hasError())
        {
            LOGGER.error("Client " + response.getRegisteredClientName() + " reported error " + response);
        }
    }

    final class ParticipantResultListener implements CommandListener
    {
        @Override
        public boolean supports(Command command)
        {
            return command instanceof ParticipantResult;
        }

        @Override
        public void processCommand(Command command)
        {
            processParticipantResult((ParticipantResult) command);

        }
    }

    final class TestCommandResponseListener implements CommandListener
    {
        @Override
        public void processCommand(Command command)
        {
            processCommandResponse((Response)command);
        }

        @Override
        public boolean supports(Command command)
        {
            CommandType type = command.getType();
            if (type == CommandType.RESPONSE)
            {
                Response response = (Response)command;
                return _setOfResponsesToExpect.contains(response.getInReplyToCommandType());
            }
            return false;
        }
    }

}
