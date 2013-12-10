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
package org.apache.qpid.disttest.client;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.Response;
import org.apache.qpid.disttest.message.StopClientCommand;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class ClientTest extends QpidTestCase
{
    private Client _client;
    private ClientJmsDelegate _delegate;
    private ClientCommandVisitor _visitor;
    private ParticipantExecutor _participant;
    private ParticipantExecutorRegistry _participantRegistry;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _delegate = mock(ClientJmsDelegate.class);
        _visitor = mock(ClientCommandVisitor.class);
        _client = new Client(_delegate);
        _client.setClientCommandVisitor(_visitor);
        _participant = mock(ParticipantExecutor.class);
        when(_participant.getParticipantName()).thenReturn("testParticipantMock");

        _participantRegistry = mock(ParticipantExecutorRegistry.class);
        when(_participantRegistry.executors()).thenReturn(Collections.singletonList(_participant));
        _client.setParticipantRegistry(_participantRegistry);
    }

    public void testInitialState() throws Exception
    {
        assertEquals("Expected client to be in CREATED state", ClientState.CREATED, _client.getState());
    }

    public void testStart() throws Exception
    {
        _client.start();
        final InOrder inOrder = inOrder(_delegate);
        inOrder.verify(_delegate).setInstructionListener(_client);
        inOrder.verify(_delegate).sendRegistrationMessage();
        assertEquals("Expected client to be in STARTED state", ClientState.READY, _client.getState());
    }

    public void testStopClient() throws Exception
    {
        _client.stop();

        assertEquals("Expected client to be in STOPPED state", ClientState.STOPPED, _client.getState());
    }

    public void testProcessInstructionVisitsCommandAndResponds() throws Exception
    {
        // has to be declared to be of supertype Command otherwise Mockito verify()
        // refers to wrong method
        final Command command = new StopClientCommand();
        _client.processInstruction(command);

        verify(_visitor).visit(command);
        verify(_delegate).sendResponseMessage(isA(Response.class));
    }

    public void testWaitUntilStopped() throws Exception
    {
        stopClientLater(500);
        _client.waitUntilStopped(1000);
        verify(_delegate).destroy();
    }

    public void testStartTest() throws Exception
    {
        _client.start();
        _client.addParticipantExecutor(_participant);

        verify(_participantRegistry).add(_participant);

        _client.startTest();

        InOrder inOrder = Mockito.inOrder(_delegate, _participant);
        inOrder.verify(_delegate).startConnections();
        inOrder.verify(_participant).start(_client);
    }

    public void testTearDownTest() throws Exception
    {
        // before we can tear down the test the client needs to be in the "running test" state, which requires a participant
        _client.start();
        _client.addParticipantExecutor(_participant);
        _client.startTest();

        _client.tearDownTest();

        verify(_delegate).tearDownTest();

        verify(_participantRegistry).clear();
    }

    public void testResults() throws Exception
    {
        ParticipantResult testResult = mock(ParticipantResult.class);
        _client.sendResults(testResult);
        verify(_delegate).sendResponseMessage(testResult);
    }

    private void stopClientLater(long delay)
    {
        doLater(new TimerTask()
        {
            @Override
            public void run()
            {
                _client.stop();
            }

        }, delay);
    }

    private void doLater(TimerTask task, long delayInMillis)
    {
        Timer timer = new Timer();
        timer.schedule(task, delayInMillis);
    }

}
