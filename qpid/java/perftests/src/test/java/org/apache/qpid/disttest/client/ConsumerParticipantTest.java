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

import static org.apache.qpid.disttest.client.ParticipantTestHelper.assertExpectedConsumerResults;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;

import javax.jms.Message;
import javax.jms.Session;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.InOrder;

public class ConsumerParticipantTest extends QpidTestCase
{
    private static final String SESSION_NAME1 = "SESSION1";
    private static final String PARTICIPANT_NAME1 = "PARTICIPANT_NAME1";
    private static final long RECEIVE_TIMEOUT = 100;
    private static final String CLIENT_NAME = "CLIENT_NAME";
    private static final int PAYLOAD_SIZE_PER_MESSAGE = 1024;

    private final Message _mockMessage = mock(Message.class);
    private final CreateConsumerCommand _command = new CreateConsumerCommand();
    private ClientJmsDelegate _delegate;
    private ConsumerParticipant _consumerParticipant;
    private InOrder _inOrder;

    /** used to check start/end time of results */
    private long _testStartTime;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _delegate = mock(ClientJmsDelegate.class);
        _inOrder = inOrder(_delegate);

        _command.setSessionName(SESSION_NAME1);
        _command.setParticipantName(PARTICIPANT_NAME1);
        _command.setSynchronous(true);
        _command.setReceiveTimeout(RECEIVE_TIMEOUT);

        _consumerParticipant = new ConsumerParticipant(_delegate, _command);

        when(_delegate.consumeMessage(PARTICIPANT_NAME1, RECEIVE_TIMEOUT)).thenReturn(_mockMessage);
        when(_delegate.calculatePayloadSizeFrom(_mockMessage)).thenReturn(PAYLOAD_SIZE_PER_MESSAGE);
        when(_delegate.getAcknowledgeMode(SESSION_NAME1)).thenReturn(Session.CLIENT_ACKNOWLEDGE);

        _testStartTime = System.currentTimeMillis();
    }

    public void testNoMessagesToReceive() throws Exception
    {
        _command.setNumberOfMessages(0);
        _command.setMaximumDuration(0);

        try
        {
            _consumerParticipant.doIt(CLIENT_NAME);
            fail("Exception not thrown");
        }
        catch(DistributedTestException e)
        {
            // PASS
            assertEquals("number of messages and duration cannot both be zero", e.getMessage());

        }

        verify(_delegate, never()).consumeMessage(anyString(), anyLong());
    }

    public void testReceiveOneMessageSynch() throws Exception
    {
        int numberOfMessages = 1;
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;
        _command.setNumberOfMessages(numberOfMessages);

        ParticipantResult result = _consumerParticipant.doIt(CLIENT_NAME);

        assertExpectedConsumerResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime,
                                      Session.CLIENT_ACKNOWLEDGE, null, numberOfMessages, PAYLOAD_SIZE_PER_MESSAGE, totalPayloadSize, null);

        _inOrder.verify(_delegate).consumeMessage(PARTICIPANT_NAME1, RECEIVE_TIMEOUT);
        _inOrder.verify(_delegate).calculatePayloadSizeFrom(_mockMessage);
        _inOrder.verify(_delegate).commitOrAcknowledgeMessageIfNecessary(SESSION_NAME1, _mockMessage);
    }

    public void testReceiveMessagesForDurationSynch() throws Exception
    {
        long duration = 100;
        _command.setMaximumDuration(duration);

        ParticipantResult result = _consumerParticipant.doIt(CLIENT_NAME);

        assertExpectedConsumerResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime,
                                      Session.CLIENT_ACKNOWLEDGE, null, null, PAYLOAD_SIZE_PER_MESSAGE, null, duration);

        verify(_delegate, atLeastOnce()).consumeMessage(PARTICIPANT_NAME1, RECEIVE_TIMEOUT);
        verify(_delegate, atLeastOnce()).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, atLeastOnce()).commitOrAcknowledgeMessageIfNecessary(SESSION_NAME1, _mockMessage);
    }

    public void testReceiveMessagesBatchedSynch() throws Exception
    {
        int numberOfMessages = 10;
        final int batchSize = 3;
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;
        _command.setNumberOfMessages(numberOfMessages);
        _command.setBatchSize(batchSize);

        ParticipantResult result = _consumerParticipant.doIt(CLIENT_NAME);

        assertExpectedConsumerResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime,
                                      Session.CLIENT_ACKNOWLEDGE, batchSize, numberOfMessages, PAYLOAD_SIZE_PER_MESSAGE, totalPayloadSize, null);

        verify(_delegate, times(numberOfMessages)).consumeMessage(PARTICIPANT_NAME1, RECEIVE_TIMEOUT);
        verify(_delegate, times(numberOfMessages)).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, times(4)).commitOrAcknowledgeMessageIfNecessary(SESSION_NAME1, _mockMessage);
    }

    public void testReceiveMessagesWithVaryingPayloadSize() throws Exception
    {
        int numberOfMessages = 3;

        int firstPayloadSize = PAYLOAD_SIZE_PER_MESSAGE;
        int secondPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * 2;
        int thirdPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * 4;

        _command.setNumberOfMessages(numberOfMessages);

        when(_delegate.calculatePayloadSizeFrom(_mockMessage)).thenReturn(firstPayloadSize, secondPayloadSize, thirdPayloadSize);

        ParticipantResult result = _consumerParticipant.doIt(CLIENT_NAME);

        final int expectedPayloadResultPayloadSize = 0;
        final long totalPayloadSize = firstPayloadSize + secondPayloadSize + thirdPayloadSize;
        assertExpectedConsumerResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime,
                                      Session.CLIENT_ACKNOWLEDGE, null, numberOfMessages, expectedPayloadResultPayloadSize, totalPayloadSize, null);

        verify(_delegate, times(numberOfMessages)).consumeMessage(PARTICIPANT_NAME1, RECEIVE_TIMEOUT);
        verify(_delegate, times(numberOfMessages)).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, times(numberOfMessages)).commitOrAcknowledgeMessageIfNecessary(SESSION_NAME1, _mockMessage);
    }

    public void testReleaseResources()
    {
        _consumerParticipant.releaseResources();
        verify(_delegate).closeTestConsumer(PARTICIPANT_NAME1);
    }

    public void testLatency() throws Exception
    {
        int numberOfMessages = 1;
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;
        _command.setNumberOfMessages(numberOfMessages);
        _command.setEvaluateLatency(true);
        _consumerParticipant = new ConsumerParticipant(_delegate, _command);
        ParticipantResult result = _consumerParticipant.doIt(CLIENT_NAME);

        assertExpectedConsumerResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime,
                                      Session.CLIENT_ACKNOWLEDGE, null, numberOfMessages, PAYLOAD_SIZE_PER_MESSAGE, totalPayloadSize, null);

        _inOrder.verify(_delegate).consumeMessage(PARTICIPANT_NAME1, RECEIVE_TIMEOUT);
        _inOrder.verify(_delegate).calculatePayloadSizeFrom(_mockMessage);
        _inOrder.verify(_delegate).commitOrAcknowledgeMessageIfNecessary(SESSION_NAME1, _mockMessage);
        assertTrue("Unexpected consuemr results", result instanceof ConsumerParticipantResult);
        Collection<Long> latencies = ((ConsumerParticipantResult)result).getMessageLatencies();
        assertNotNull("Message latency is not cllected", latencies);
        assertEquals("Unexpected message latency results", 1,  latencies.size());
    }
}
