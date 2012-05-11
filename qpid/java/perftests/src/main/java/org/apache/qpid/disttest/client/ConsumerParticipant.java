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


import java.util.Date;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerParticipant implements Participant
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerParticipant.class);

    private final AtomicInteger _totalNumberOfMessagesReceived = new AtomicInteger(0);
    private final NavigableSet<Integer> _allConsumedPayloadSizes = new ConcurrentSkipListSet<Integer>();
    private final AtomicLong _totalPayloadSizeOfAllMessagesReceived = new AtomicLong(0);
    private final CountDownLatch _asyncRunHasFinished = new CountDownLatch(1);
    private final ClientJmsDelegate _jmsDelegate;
    private final CreateConsumerCommand _command;
    private final ParticipantResultFactory _resultFactory;

    private long _startTime;

    private volatile Exception _asyncMessageListenerException;

    public ConsumerParticipant(final ClientJmsDelegate delegate, final CreateConsumerCommand command)
    {
        _jmsDelegate = delegate;
        _command = command;
        _resultFactory = new ParticipantResultFactory();
    }

    @Override
    public ParticipantResult doIt(String registeredClientName) throws Exception
    {
        final Date start = new Date();
        final int acknowledgeMode = _jmsDelegate.getAcknowledgeMode(_command.getSessionName());

        if (_command.getMaximumDuration() == 0 && _command.getNumberOfMessages() == 0)
        {
            throw new DistributedTestException("number of messages and duration cannot both be zero");
        }

        if (_command.isSynchronous())
        {
            synchronousRun();
        }
        else
        {
            _jmsDelegate.registerListener(_command.getParticipantName(), new MessageListener(){

                @Override
                public void onMessage(Message message)
                {
                    processAsynchMessage(message);
                }

            });

            waitUntilMsgListenerHasFinished();
            rethrowAnyAsyncMessageListenerException();
        }

        Date end = new Date();
        int numberOfMessagesSent = _totalNumberOfMessagesReceived.get();
        long totalPayloadSize = _totalPayloadSizeOfAllMessagesReceived.get();
        int payloadSize = getPayloadSizeForResultIfConstantOrZeroOtherwise(_allConsumedPayloadSizes);

        ConsumerParticipantResult result = _resultFactory.createForConsumer(
                getName(),
                registeredClientName,
                _command,
                acknowledgeMode,
                numberOfMessagesSent,
                payloadSize,
                totalPayloadSize,
                start, end);

        return result;
    }

    private void synchronousRun()
    {
        LOGGER.debug("entered synchronousRun: " + this);

        _startTime = System.currentTimeMillis();

        Message message = null;

        do
        {
            message = _jmsDelegate.consumeMessage(_command.getParticipantName(),
                                                  _command.getReceiveTimeout());
        } while (processMessage(message));
    }

    /**
     * @return whether to continue running (ie returns false if the message quota has been reached)
     */
    private boolean processMessage(Message message)
    {
        int messageCount = _totalNumberOfMessagesReceived.incrementAndGet();
        if (LOGGER.isTraceEnabled())
        {
            LOGGER.trace("message " + messageCount + " received by " + this);
        }
        int messagePayloadSize = _jmsDelegate.calculatePayloadSizeFrom(message);
        _allConsumedPayloadSizes.add(messagePayloadSize);
        _totalPayloadSizeOfAllMessagesReceived.addAndGet(messagePayloadSize);

        boolean batchEnabled = _command.getBatchSize() > 0;
        boolean batchComplete = batchEnabled && messageCount % _command.getBatchSize() == 0;

        if (!batchEnabled || batchComplete)
        {
            if (LOGGER.isTraceEnabled() && batchEnabled)
            {
                LOGGER.trace("Committing: batch size " + _command.getBatchSize() );
            }
            _jmsDelegate.commitOrAcknowledgeMessage(message, _command.getSessionName());
        }

        boolean reachedExpectedNumberOfMessages = _command.getNumberOfMessages() > 0 && messageCount >= _command.getNumberOfMessages();
        boolean reachedMaximumDuration = _command.getMaximumDuration() > 0 && System.currentTimeMillis() - _startTime >= _command.getMaximumDuration();
        boolean finishedConsuming = reachedExpectedNumberOfMessages || reachedMaximumDuration;

        if (finishedConsuming)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("message " + messageCount
                        + " reachedExpectedNumberOfMessages " + reachedExpectedNumberOfMessages
                        + " reachedMaximumDuration " + reachedMaximumDuration);
            }

            if (batchEnabled && !batchComplete)
            {
                if (LOGGER.isTraceEnabled())
                {
                    LOGGER.trace("Committing: batch size " + _command.getBatchSize() );
                }

                // commit/acknowledge remaining messages if necessary
                _jmsDelegate.commitOrAcknowledgeMessage(message, _command.getSessionName());
            }
            return false;
        }

        return true;
    }


    /**
     * Intended to be called from a {@link MessageListener}. Updates {@link #_asyncRunHasFinished} if
     * no more messages should be processed, causing {@link #doIt(String)} to exit.
     */
    public void processAsynchMessage(Message message)
    {
        boolean continueRunning = true;
        try
        {
            if (_startTime == 0)
            {
                // reset counter and start time on receiving of first message
                _startTime = System.currentTimeMillis();
            }

            continueRunning = processMessage(message);
        }
        catch (Exception e)
        {
            LOGGER.error("Error occured consuming message " + _totalNumberOfMessagesReceived, e);
            continueRunning = false;
            _asyncMessageListenerException = e;
        }

        if(!continueRunning)
        {
            _asyncRunHasFinished.countDown();
        }
    }

    @Override
    public void releaseResources()
    {
        _jmsDelegate.closeTestConsumer(_command.getParticipantName());
    }

    private int getPayloadSizeForResultIfConstantOrZeroOtherwise(NavigableSet<Integer> allSizes)
    {
        return allSizes.size() == 1 ? _allConsumedPayloadSizes.first() : 0;
    }

    private void rethrowAnyAsyncMessageListenerException()
    {
        if (_asyncMessageListenerException != null)
        {
            throw new DistributedTestException(_asyncMessageListenerException);
        }
    }

    private void waitUntilMsgListenerHasFinished() throws Exception
    {
        LOGGER.debug("waiting until message listener has finished for " + this);
        _asyncRunHasFinished.await();
        LOGGER.debug("Message listener has finished for " + this);
    }

    @Override
    public String getName()
    {
        return _command.getParticipantName();
    }

    @Override
    public String toString()
    {
        return "ConsumerParticipant [_command=" + _command + ", _startTime=" + _startTime + "]";
    }
}
