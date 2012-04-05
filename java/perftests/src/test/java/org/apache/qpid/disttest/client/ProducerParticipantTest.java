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

import static org.apache.qpid.disttest.client.ParticipantTestHelper.assertExpectedResults;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.jms.DeliveryMode;
import javax.jms.Message;

import junit.framework.TestCase;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.mockito.InOrder;

public class ProducerParticipantTest extends TestCase
{
    private ProducerParticipant _producer;

    private static final String SESSION_NAME1 = "SESSION1";
    private static final String PARTICIPANT_NAME1 = "PARTICIPANT_NAME1";

    private static final String CLIENT_NAME = "CLIENT_NAME";
    private static final int PAYLOAD_SIZE_PER_MESSAGE = 1024;


    private final Message _mockMessage = mock(Message.class);
    private final CreateProducerCommand _command = new CreateProducerCommand();
    private ClientJmsDelegate _delegate;
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

        when(_delegate.sendNextMessage(isA(CreateProducerCommand.class))).thenReturn(_mockMessage);
        when(_delegate.calculatePayloadSizeFrom(_mockMessage)).thenReturn(PAYLOAD_SIZE_PER_MESSAGE);

        _producer = new ProducerParticipant(_delegate, _command);

        _testStartTime = System.currentTimeMillis();
    }

    public void testStartDelay() throws Exception
    {
        final long delay = 100;
        int numberOfMessages = 1;
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;

        _command.setStartDelay(delay);
        _command.setNumberOfMessages(numberOfMessages);

        ParticipantResult result = _producer.doIt(CLIENT_NAME);

        long expectedPublishedStartTime = _testStartTime + delay;
        assertExpectedResults(result, PARTICIPANT_NAME1, CLIENT_NAME, expectedPublishedStartTime, numberOfMessages, PAYLOAD_SIZE_PER_MESSAGE, totalPayloadSize, null);
    }


    public void testNoMessagesToSend() throws Exception
    {
        _command.setNumberOfMessages(0);
        _command.setMaximumDuration(0);

        try
        {
            _producer.doIt(CLIENT_NAME);
            fail("Exception not thrown");
        }
        catch (DistributedTestException e)
        {
            // PASS
            assertEquals("number of messages and duration cannot both be zero", e.getMessage());
        }
    }

    public void testOneMessageToSend()  throws Exception
    {
        int batchSize = 1;
        int numberOfMessages = 1;
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;
        int deliveryMode = DeliveryMode.PERSISTENT;

        _command.setNumberOfMessages(numberOfMessages);
        _command.setBatchSize(batchSize);
        _command.setDeliveryMode(deliveryMode);

        ParticipantResult result = (ParticipantResult) _producer.doIt(CLIENT_NAME);
        assertExpectedResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime, numberOfMessages, PAYLOAD_SIZE_PER_MESSAGE, totalPayloadSize, null);

        _inOrder.verify(_delegate).sendNextMessage(isA(CreateProducerCommand.class));
        _inOrder.verify(_delegate).calculatePayloadSizeFrom(_mockMessage);
        _inOrder.verify(_delegate).commitOrAcknowledgeMessage(_mockMessage, SESSION_NAME1);

    }

    public void testSendMessagesForDuration() throws Exception
    {
        final long duration = 100;
        _command.setMaximumDuration(duration);

        ParticipantResult result = _producer.doIt(CLIENT_NAME);
        assertExpectedResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime, null, PAYLOAD_SIZE_PER_MESSAGE, null, duration);

        verify(_delegate, atLeastOnce()).sendNextMessage(isA(CreateProducerCommand.class));
        verify(_delegate, atLeastOnce()).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, atLeastOnce()).commitOrAcknowledgeMessage(_mockMessage, SESSION_NAME1);
    }

    public void testSendMessageBatches() throws Exception
    {
        final int numberOfMessages = 10;
        final int expectedNumberOfCommits = 4; // one for each batch of 3 messages, plus one more at the end of the test for the tenth msg.
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;

        _command.setNumberOfMessages(numberOfMessages);
        _command.setBatchSize(3);

        ParticipantResult result = _producer.doIt(CLIENT_NAME);
        assertExpectedResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime, numberOfMessages, PAYLOAD_SIZE_PER_MESSAGE, totalPayloadSize, null);

        verify(_delegate, times(numberOfMessages)).sendNextMessage(isA(CreateProducerCommand.class));
        verify(_delegate, times(numberOfMessages)).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, times(expectedNumberOfCommits)).commitOrAcknowledgeMessage(_mockMessage, SESSION_NAME1);
    }

    public void testSendMessageWithPublishInterval() throws Exception
    {
        final int batchSize = 3;
        final long publishInterval = 100;
        int numberOfMessages = 10;
        long totalPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * numberOfMessages;

        final long expectedTimeToRunTest = batchSize * publishInterval;

        _command.setNumberOfMessages(numberOfMessages);
        _command.setBatchSize(batchSize);
        _command.setInterval(publishInterval);

        ParticipantResult result = _producer.doIt(CLIENT_NAME);
        assertExpectedResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime, numberOfMessages, null, totalPayloadSize, expectedTimeToRunTest);

        verify(_delegate, times(numberOfMessages)).sendNextMessage(isA(CreateProducerCommand.class));
        verify(_delegate, times(numberOfMessages)).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, times(4)).commitOrAcknowledgeMessage(_mockMessage, SESSION_NAME1);
    }

    public void testSendMessageWithVaryingPayloadSize() throws Exception
    {
        int numberOfMessages = 3;

        int firstPayloadSize = PAYLOAD_SIZE_PER_MESSAGE;
        int secondPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * 2;
        int thirdPayloadSize = PAYLOAD_SIZE_PER_MESSAGE * 4;

        final long totalPayloadSize = firstPayloadSize + secondPayloadSize + thirdPayloadSize;

        when(_delegate.calculatePayloadSizeFrom(_mockMessage)).thenReturn(firstPayloadSize, secondPayloadSize, thirdPayloadSize);

        _command.setNumberOfMessages(numberOfMessages);

        ParticipantResult result = _producer.doIt(CLIENT_NAME);

        final int expectedPayloadResultPayloadSize = 0;
        assertExpectedResults(result, PARTICIPANT_NAME1, CLIENT_NAME, _testStartTime, numberOfMessages, expectedPayloadResultPayloadSize, totalPayloadSize, null);

        verify(_delegate, times(numberOfMessages)).sendNextMessage(isA(CreateProducerCommand.class));
        verify(_delegate, times(numberOfMessages)).calculatePayloadSizeFrom(_mockMessage);
        verify(_delegate, times(numberOfMessages)).commitOrAcknowledgeMessage(_mockMessage, SESSION_NAME1);
    }

    public void testReleaseResources()
    {
        _producer.releaseResources();
        verify(_delegate).closeTestProducer(PARTICIPANT_NAME1);
    }
}
