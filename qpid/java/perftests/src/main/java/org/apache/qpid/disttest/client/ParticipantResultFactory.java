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

import java.util.Date;

import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateParticpantCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;

public class ParticipantResultFactory
{
    public ConsumerParticipantResult createForConsumer(String participantName, String clientRegisteredName, CreateConsumerCommand command, int acknowledgeMode, int numberOfMessagesReceived, int payloadSize, long totalPayloadReceived, Date start, Date end)
    {
        ConsumerParticipantResult consumerParticipantResult = new ConsumerParticipantResult();

        setTestProperties(consumerParticipantResult, command, participantName, clientRegisteredName, acknowledgeMode);
        setTestResultProperties(consumerParticipantResult, numberOfMessagesReceived, payloadSize, totalPayloadReceived, start, end);

        consumerParticipantResult.setTopic(command.isTopic());
        consumerParticipantResult.setDurableSubscription(command.isDurableSubscription());
        consumerParticipantResult.setBrowsingSubscription(command.isBrowsingSubscription());
        consumerParticipantResult.setSelector(command.getSelector() != null);
        consumerParticipantResult.setNoLocal(command.isNoLocal());
        consumerParticipantResult.setSynchronousConsumer(command.isSynchronous());
        consumerParticipantResult.setTotalNumberOfConsumers(1);
        consumerParticipantResult.setTotalNumberOfProducers(0);

        return consumerParticipantResult;
    }

    public ProducerParticipantResult createForProducer(String participantName, String clientRegisteredName, CreateProducerCommand command, int acknowledgeMode, int numberOfMessagesSent, int payloadSize, long totalPayloadSent, Date start, Date end)
    {
        final ProducerParticipantResult producerParticipantResult = new ProducerParticipantResult();

        producerParticipantResult.setStartDelay(command.getStartDelay());
        producerParticipantResult.setDeliveryMode(command.getDeliveryMode());
        producerParticipantResult.setPriority(command.getPriority());
        producerParticipantResult.setInterval(command.getInterval());
        producerParticipantResult.setTimeToLive(command.getTimeToLive());
        producerParticipantResult.setTotalNumberOfConsumers(0);
        producerParticipantResult.setTotalNumberOfProducers(1);


        setTestProperties(producerParticipantResult, command, participantName, clientRegisteredName, acknowledgeMode);

        setTestResultProperties(producerParticipantResult, numberOfMessagesSent, payloadSize, totalPayloadSent, start, end);

        return producerParticipantResult;
    }

    private void setTestResultProperties(final ParticipantResult participantResult, int numberOfMessagesSent, int payloadSize, long totalPayloadReceived, Date start, Date end)
    {
        participantResult.setNumberOfMessagesProcessed(numberOfMessagesSent);
        participantResult.setPayloadSize(payloadSize);
        participantResult.setTotalPayloadProcessed(totalPayloadReceived);
        participantResult.setStartDate(start);
        participantResult.setEndDate(end);
    }

    private void setTestProperties(final ParticipantResult participantResult, CreateParticpantCommand command, String participantName, String clientRegisteredName, int acknowledgeMode)
    {
        participantResult.setParticipantName(participantName);
        participantResult.setRegisteredClientName(clientRegisteredName);
        participantResult.setBatchSize(command.getBatchSize());
        participantResult.setMaximumDuration(command.getMaximumDuration());
        participantResult.setAcknowledgeMode(acknowledgeMode);

    }

    public ParticipantResult createForError(String participantName, String clientRegisteredName, String errorMessage)
    {
        ParticipantResult result = new ParticipantResult();
        result.setParticipantName(participantName);
        result.setRegisteredClientName(clientRegisteredName);
        result.setErrorMessage(errorMessage);

        return result;
    }

}
