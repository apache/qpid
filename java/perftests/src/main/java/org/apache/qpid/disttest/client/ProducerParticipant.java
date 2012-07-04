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
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

import javax.jms.Message;

import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.client.utils.ExecutorWithLimits;
import org.apache.qpid.disttest.client.utils.ExecutorWithLimitsFactory;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerParticipant implements Participant
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerParticipant.class);

    private final ClientJmsDelegate _jmsDelegate;

    private final CreateProducerCommand _command;

    private final ParticipantResultFactory _resultFactory;

    private ExecutorWithLimits _limiter;

    public ProducerParticipant(final ClientJmsDelegate jmsDelegate, final CreateProducerCommand command)
    {
        _jmsDelegate = jmsDelegate;
        _command = command;
        _resultFactory = new ParticipantResultFactory();
    }

    @Override
    public ParticipantResult doIt(String registeredClientName) throws Exception
    {
        if (_command.getMaximumDuration() == 0 && _command.getNumberOfMessages() == 0)
        {
            throw new DistributedTestException("number of messages and duration cannot both be zero");
        }

        int acknowledgeMode = _jmsDelegate.getAcknowledgeMode(_command.getSessionName());

        doSleepForStartDelay();

        final long requiredDuration = _command.getMaximumDuration() - _command.getStartDelay();

        final long startTime = System.currentTimeMillis();

        Message lastPublishedMessage = null;
        int numberOfMessagesSent = 0;
        long totalPayloadSizeOfAllMessagesSent = 0;
        NavigableSet<Integer> allProducedPayloadSizes = new TreeSet<Integer>();

        _limiter = ExecutorWithLimitsFactory.createExecutorWithLimit(startTime, requiredDuration);

        while (true)
        {
            try
            {
                lastPublishedMessage = _limiter.execute(new Callable<Message>()
                {
                    @Override
                    public Message call() throws Exception
                    {
                        return _jmsDelegate.sendNextMessage(_command);
                    }
                });
            }
            catch (CancellationException ce)
            {
                LOGGER.debug("Producer send was cancelled due to maximum duration {} ms", requiredDuration);
                break;
            }

            numberOfMessagesSent++;

            int lastPayloadSize = _jmsDelegate.calculatePayloadSizeFrom(lastPublishedMessage);
            totalPayloadSizeOfAllMessagesSent += lastPayloadSize;
            allProducedPayloadSizes.add(lastPayloadSize);

            if (LOGGER.isTraceEnabled())
            {
                LOGGER.trace("message " + numberOfMessagesSent + " sent by " + this);
            }

            final boolean batchLimitReached = _command.getBatchSize() <= 0
                            || numberOfMessagesSent % _command.getBatchSize() == 0;

            if (batchLimitReached)
            {
                if (LOGGER.isTraceEnabled() && _command.getBatchSize() > 0)
                {
                    LOGGER.trace("Committing: batch size " + _command.getBatchSize() );
                }
                _jmsDelegate.commitOrAcknowledgeMessage(lastPublishedMessage, _command.getSessionName());

                doSleepForInterval();
            }

            if (_command.getNumberOfMessages() > 0 && numberOfMessagesSent >= _command.getNumberOfMessages()
                            || requiredDuration > 0 && System.currentTimeMillis() - startTime >= requiredDuration)
            {
                break;
            }
        }

        // commit the remaining batch messages
        if (_command.getBatchSize() > 0 && numberOfMessagesSent % _command.getBatchSize() != 0)
        {
            if (LOGGER.isTraceEnabled())
            {
                LOGGER.trace("Committing: batch size " + _command.getBatchSize() );
            }
            _jmsDelegate.commitOrAcknowledgeMessage(lastPublishedMessage, _command.getSessionName());
        }

        Date start = new Date(startTime);
        Date end = new Date();
        int payloadSize = getPayloadSizeForResultIfConstantOrZeroOtherwise(allProducedPayloadSizes);

        return _resultFactory.createForProducer(
                getName(),
                registeredClientName,
                _command,
                acknowledgeMode,
                numberOfMessagesSent,
                payloadSize, totalPayloadSizeOfAllMessagesSent, start, end);
    }

    private int getPayloadSizeForResultIfConstantOrZeroOtherwise(NavigableSet<Integer> allPayloadSizes)
    {
        return allPayloadSizes.size() == 1 ? allPayloadSizes.first() : 0;
    }

    private void doSleepForStartDelay()
    {
        long sleepTime = _command.getStartDelay();
        if (sleepTime > 0)
        {
            // start delay is specified. Sleeping...
            doSleep(sleepTime);
        }
    }

    private void doSleepForInterval() throws InterruptedException
    {
        long sleepTime = _command.getInterval();
        if (sleepTime > 0)
        {
            doSleep(sleepTime);
        }
    }

    private void doSleep(long sleepTime)
    {
        try
        {
            Thread.sleep(sleepTime);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void releaseResources()
    {
        if (_limiter != null)
        {
            _limiter.shutdown();
        }
        _jmsDelegate.closeTestProducer(_command.getParticipantName());
    }

    @Override
    public String getName()
    {
        return _command.getParticipantName();
    }

    @Override
    public String toString()
    {
        return "ProducerParticipant [command=" + _command + "]";
    }

}
