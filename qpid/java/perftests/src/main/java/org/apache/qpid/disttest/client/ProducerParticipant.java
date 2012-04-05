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

import javax.jms.Message;

import org.apache.qpid.disttest.DistributedTestException;
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

    private ParticipantResultFactory _resultFactory;

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

        long expectedDuration = _command.getMaximumDuration() - _command.getStartDelay();

        doSleepForStartDelay();

        final long startTime = System.currentTimeMillis();

        Message lastPublishedMessage = null;
        int numberOfMessagesSent = 0;
        long totalPayloadSizeOfAllMessagesSent = 0;
        NavigableSet<Integer> allProducedPayloadSizes = new TreeSet<Integer>();

        while (true)
        {
            numberOfMessagesSent++;

            lastPublishedMessage = _jmsDelegate.sendNextMessage(_command);

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
                _jmsDelegate.commitOrAcknowledgeMessage(lastPublishedMessage, _command.getSessionName());

                if (_command.getInterval() > 0)
                {
                    // sleep for given time
                    Thread.sleep(_command.getInterval());
                }
            }

            if (_command.getNumberOfMessages() > 0 && numberOfMessagesSent >= _command.getNumberOfMessages()
                            || expectedDuration > 0 && System.currentTimeMillis() - startTime >= expectedDuration)
            {
                break;
            }
        }

        // commit the remaining batch messages
        if (_command.getBatchSize() > 0 && numberOfMessagesSent % _command.getBatchSize() != 0)
        {
            _jmsDelegate.commitOrAcknowledgeMessage(lastPublishedMessage, _command.getSessionName());
        }

        Date start = new Date(startTime);
        Date end = new Date();
        int payloadSize = getPayloadSizeForResultIfConstantOrZeroOtherwise(allProducedPayloadSizes);

        return _resultFactory.createForProducer(
                getName(),
                registeredClientName,
                _command,
                numberOfMessagesSent,
                payloadSize,
                totalPayloadSizeOfAllMessagesSent, start, end);
    }

    private int getPayloadSizeForResultIfConstantOrZeroOtherwise(NavigableSet<Integer> allPayloadSizes)
    {
        return allPayloadSizes.size() == 1 ? allPayloadSizes.first() : 0;
    }

    private void doSleepForStartDelay()
    {
        if (_command.getStartDelay() > 0)
        {
            // start delay is specified. Sleeping...
            try
            {
                Thread.sleep(_command.getStartDelay());
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void releaseResources()
    {
        _jmsDelegate.closeTestProducer(_command.getParticipantName());
    }

    @Override
    public String getName()
    {
        return _command.getParticipantName();
    }
}
