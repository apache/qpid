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

package org.apache.qpid.disttest.client;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;

public class ParticipantExecutorTest extends QpidTestCase
{
    private static final ResultHasError HAS_ERROR = new ResultHasError();
    private static final String CLIENT_NAME = "CLIENT_NAME";
    private static final String PARTICIPANT_NAME = "PARTICIPANT_NAME";
    private ParticipantExecutor _participantExecutor = null;
    private Client _client = null;
    private Participant _participant = null;
    private ParticipantResult _mockResult;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _client = mock(Client.class);
        when(_client.getClientName()).thenReturn(CLIENT_NAME);
        _participant = mock(Participant.class);

        _participantExecutor = new ParticipantExecutor(_participant);
        _participantExecutor.setExecutor(new SynchronousExecutor());

        _mockResult = mock(ParticipantResult.class);
    }

    public void testStart() throws Exception
    {
        when(_participant.doIt(CLIENT_NAME)).thenReturn(_mockResult);

        _participantExecutor.start(_client);

        InOrder inOrder = inOrder(_participant, _client);

        inOrder.verify(_participant).doIt(CLIENT_NAME);
        inOrder.verify(_participant).releaseResources();
        inOrder.verify(_client).sendResults(_mockResult);
    }

    public void testParticipantThrowsException() throws Exception
    {
        when(_participant.doIt(CLIENT_NAME)).thenThrow(DistributedTestException.class);

        _participantExecutor.start(_client);

        InOrder inOrder = inOrder(_participant, _client);

        inOrder.verify(_participant).doIt(CLIENT_NAME);
        inOrder.verify(_participant).releaseResources();
        inOrder.verify(_client).sendResults(argThat(HAS_ERROR));
    }

    public void testReleaseResourcesThrowsException() throws Exception
    {
        when(_participant.doIt(CLIENT_NAME)).thenReturn(_mockResult);
        doThrow(DistributedTestException.class).when(_participant).releaseResources();

        _participantExecutor.start(_client);

        InOrder inOrder = inOrder(_participant, _client);

        inOrder.verify(_participant).doIt(CLIENT_NAME);
        inOrder.verify(_participant).releaseResources();

        // check that sendResults is called even though releaseResources threw an exception
        inOrder.verify(_client).sendResults(_mockResult);
    }

    public void testThreadNameAndDaemonness() throws Exception
    {
        ThreadPropertyReportingParticipant participant = new ThreadPropertyReportingParticipant(PARTICIPANT_NAME);
        _participantExecutor = new ParticipantExecutor(participant);

        _participantExecutor.start(_client);
        participant.awaitExecution();

        assertTrue("Participant should be run in a thread named after it", participant.threadWasCalled().endsWith(PARTICIPANT_NAME));
        assertTrue("Executor should use daemon threads to avoid them preventing JVM termination", participant.wasDaemon());
    }

    private static final class ThreadPropertyReportingParticipant implements Participant
    {
        private final String _participantName;
        private final CountDownLatch _participantExecuted = new CountDownLatch(1);
        private String _threadName;
        private boolean _daemon;

        public ThreadPropertyReportingParticipant(String participantName)
        {
            _participantName = participantName;
        }

        public String threadWasCalled()
        {
            return _threadName;
        }

        public boolean wasDaemon()
        {
            return _daemon;
        }

        @Override
        public void releaseResources()
        {
        }

        @Override
        public String getName()
        {
            return _participantName;
        }

        @Override
        public ParticipantResult doIt(String registeredClientName) throws Exception
        {
            Thread currentThread = Thread.currentThread();
            _threadName = currentThread.getName();
            _daemon = currentThread.isDaemon();

            _participantExecuted.countDown();

            return null; // unused
        }

        public void awaitExecution()
        {
            boolean success = false;
            try
            {
                success = _participantExecuted.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            assertTrue("Participant not executed", success);
        }
    }

    /** avoids our unit test needing to use multiple threads */
    private final class SynchronousExecutor implements Executor
    {
        @Override
        public void execute(Runnable command)
        {
            command.run();
        }
    }

    private static class ResultHasError extends ArgumentMatcher<ParticipantResult>
    {
        @Override
        public boolean matches(Object argument)
        {
            ParticipantResult result = (ParticipantResult) argument;
            return result.hasError();
        }
    }

}
