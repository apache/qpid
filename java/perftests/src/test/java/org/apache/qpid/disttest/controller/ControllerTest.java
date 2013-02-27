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

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.controller.config.Config;
import org.apache.qpid.disttest.controller.config.TestInstance;
import org.apache.qpid.disttest.jms.ControllerJmsDelegate;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.RegisterClientCommand;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StopClientCommand;
import org.apache.qpid.disttest.results.aggregation.ITestResult;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ControllerTest extends QpidTestCase
{
    private static final String CLIENT1_REGISTERED_NAME = "client-uid1";

    private static final long COMMAND_RESPONSE_TIMEOUT = 1000;
    private static final long REGISTRATION_TIMEOUT = 1000;

    private Controller _controller;
    private ControllerJmsDelegate _respondingJmsDelegate;
    private TestRunner _testRunner;
    private ClientRegistry _clientRegistry;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _respondingJmsDelegate = mock(ControllerJmsDelegate.class);
        _controller = new Controller(_respondingJmsDelegate, REGISTRATION_TIMEOUT, COMMAND_RESPONSE_TIMEOUT);
        _testRunner = mock(TestRunner.class);
        _clientRegistry = mock(ClientRegistry.class);

        Config configWithOneClient = createMockConfig(1);
        _controller.setConfig(configWithOneClient);
        _controller.setClientRegistry(_clientRegistry);
        _controller.setTestRunnerFactory(createTestFactoryReturningMock());

        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                final String clientName = (String)invocation.getArguments()[0];
                final Command command = (Command)invocation.getArguments()[1];
                _controller.processStopClientResponse(new Response(clientName, command.getType()));
                return null;
            }
        }).when(_respondingJmsDelegate).sendCommandToClient(anyString(), isA(Command.class));
    }


    public void testControllerRejectsEmptyConfiguration()
    {
        Config configWithZeroClients = createMockConfig(0);

        try
        {
            _controller.setConfig(configWithZeroClients);
            fail("Exception not thrown");
        }
        catch (DistributedTestException e)
        {
            // PASS
        }
    }

    public void testControllerReceivesTwoExpectedClientRegistrations()
    {
        Config configWithTwoClients = createMockConfig(2);
        _controller.setConfig(configWithTwoClients);
        when(_clientRegistry.awaitClients(2, REGISTRATION_TIMEOUT)).thenReturn(0);

        _controller.awaitClientRegistrations();
    }

    public void testControllerDoesntReceiveAnyRegistrations()
    {
        when(_clientRegistry.awaitClients(1, REGISTRATION_TIMEOUT)).thenReturn(1);

        try
        {
            _controller.awaitClientRegistrations();
            fail("Exception not thrown");
        }
        catch (DistributedTestException e)
        {
            // PASS
        }
    }

    public void testRegisterClient()
    {
        RegisterClientCommand command = new RegisterClientCommand(CLIENT1_REGISTERED_NAME, "dummy");
        _controller.registerClient(command);

        verify(_clientRegistry).registerClient(CLIENT1_REGISTERED_NAME);
        verify(_respondingJmsDelegate).registerClient(command);

    }

    public void testControllerSendsClientStopCommandToClient()
    {
        when(_clientRegistry.getClients()).thenReturn(Collections.singleton(CLIENT1_REGISTERED_NAME));

        _controller.stopAllRegisteredClients();

        verify(_respondingJmsDelegate).sendCommandToClient(eq(CLIENT1_REGISTERED_NAME), isA(StopClientCommand.class));
    }

    public void testRunAllTests()
    {
        Config config = createSimpleConfig();
        _controller.setConfig(config);

        TestResult testResult = new TestResult("test1");

        when(_testRunner.run()).thenReturn(testResult);

        ResultsForAllTests results = _controller.runAllTests();

        List<ITestResult> testResults = results.getTestResults();
        assertEquals(1, testResults.size());
        assertSame(testResult, testResults.get(0));

        verify(_testRunner).run();
    }

    private Config createSimpleConfig()
    {
        Config config = mock(Config.class);
        TestInstance testInstance = mock(TestInstance.class);

        List<TestInstance> testInstances = Arrays.asList(testInstance);

        when(config.getTests()).thenReturn(testInstances);
        when(config.getTotalNumberOfClients()).thenReturn(1); // necessary otherwise controller rejects "invalid" config

        return config;
    }

    private Config createMockConfig(int numberOfClients)
    {
        Config config = mock(Config.class);
        when(config.getTotalNumberOfClients()).thenReturn(numberOfClients);
        return config;
    }

    private TestRunnerFactory createTestFactoryReturningMock()
    {
        TestRunnerFactory testRunnerFactory = mock(TestRunnerFactory.class);

        when(testRunnerFactory.createTestRunner(
                isA(ParticipatingClients.class),
                isA(TestInstance.class),
                isA(ControllerJmsDelegate.class),
                isA(Long.class),
                isA(Long.class)))
                .thenReturn(_testRunner);

        return testRunnerFactory;
    }
}
