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

import javax.jms.DeliveryMode;

import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.CreateConsumerCommand;
import org.apache.qpid.disttest.message.CreateParticpantCommand;
import org.apache.qpid.disttest.message.CreateProducerCommand;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;
import org.apache.qpid.test.utils.QpidTestCase;

public class ParticipantResultFactoryTest extends QpidTestCase
{
    private static final String PARTICIPANT_NAME = "participantName";
    private static final String REGISTERED_CLIENT_NAME = "registeredClientName";

    private static final int BATCH_SIZE = 10;
    private static final long MAXIMUM_DURATION = 500;
    private static final int NUMBER_OF_MESSAGES_PROCESSED = 100;
    private static final long TIME_TAKEN = 100;
    private static final long TOTAL_PAYLOAD_PROCESSED = 200;
    private static final int PAYLOAD_SIZE = 300;

    private static final Date START = new Date(0);
    private static final Date END = new Date(START.getTime() + TIME_TAKEN);

    private ParticipantResultFactory _participantResultFactory;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        _participantResultFactory = new ParticipantResultFactory();
    }

    public void testCreateForProducer()
    {
        CreateProducerCommand command = new CreateProducerCommand();
        setCommonCommandFields(command);

        long producerStartDelay = 30;
        command.setStartDelay(producerStartDelay);

        int deliveryMode = DeliveryMode.PERSISTENT;
        command.setDeliveryMode(deliveryMode);

        int priority = 5;
        command.setPriority(priority);

        long producerInterval = 50;
        command.setInterval(producerInterval);

        long timeToLive = 60;
        command.setTimeToLive(timeToLive);

        int totalNumberOfConsumers = 0;
        int totalNumberOfProducers = 1;

        int acknowledgeMode = 1;

        ProducerParticipantResult result = _participantResultFactory.createForProducer(PARTICIPANT_NAME,
                                                                                       REGISTERED_CLIENT_NAME,
                                                                                       command,
                                                                                       acknowledgeMode,
                                                                                       NUMBER_OF_MESSAGES_PROCESSED,
                                                                                       PAYLOAD_SIZE,
                                                                                       TOTAL_PAYLOAD_PROCESSED,
                                                                                       START, END);

        assertCommonResultProperties(result);

        assertEquals(deliveryMode, result.getDeliveryMode());
        assertEquals(acknowledgeMode, result.getAcknowledgeMode());
        assertEquals(priority, result.getPriority());
        assertEquals(producerInterval, result.getInterval());
        assertEquals(producerStartDelay, result.getStartDelay());
        assertEquals(timeToLive, result.getTimeToLive());
        assertEquals(totalNumberOfConsumers, result.getTotalNumberOfConsumers());
        assertEquals(totalNumberOfProducers, result.getTotalNumberOfProducers());
    }

    public void testCreateForConsumer()
    {
        CreateConsumerCommand command = new CreateConsumerCommand();
        setCommonCommandFields(command);

        boolean topic = true;
        command.setTopic(topic);

        boolean durable = true;
        command.setDurableSubscription(durable);

        boolean browsingSubscription = false;
        command.setBrowsingSubscription(browsingSubscription);

        String selector = "selector";
        boolean isSelector = true;
        command.setSelector(selector);

        boolean noLocal = false;
        command.setNoLocal(noLocal);

        boolean synchronousConsumer = true;
        command.setSynchronous(synchronousConsumer);

        int totalNumberOfConsumers = 1;
        int totalNumberOfProducers = 0;

        int acknowledgeMode = 2;

        ConsumerParticipantResult result = _participantResultFactory.createForConsumer(PARTICIPANT_NAME,
                                                                                       REGISTERED_CLIENT_NAME,
                                                                                       command,
                                                                                       acknowledgeMode,
                                                                                       NUMBER_OF_MESSAGES_PROCESSED,
                                                                                       PAYLOAD_SIZE,
                                                                                       TOTAL_PAYLOAD_PROCESSED,
                                                                                       START, END);

        assertCommonResultProperties(result);

        assertEquals(topic,                  result.isTopic());
        assertEquals(durable,                result.isDurableSubscription());
        assertEquals(browsingSubscription,   result.isBrowsingSubscription());
        assertEquals(isSelector,             result.isSelector());
        assertEquals(noLocal,                result.isNoLocal());
        assertEquals(synchronousConsumer,    result.isSynchronousConsumer());
        assertEquals(totalNumberOfConsumers, result.getTotalNumberOfConsumers());
        assertEquals(totalNumberOfProducers, result.getTotalNumberOfProducers());
    }

    public void testCreateForError()
    {
        String errorMessage = "error";
        ParticipantResult result = _participantResultFactory.createForError(PARTICIPANT_NAME, REGISTERED_CLIENT_NAME, errorMessage);
        assertEquals(PARTICIPANT_NAME, result.getParticipantName());
        assertEquals(REGISTERED_CLIENT_NAME, result.getRegisteredClientName());
    }


    private void setCommonCommandFields(CreateParticpantCommand command)
    {
        command.setBatchSize(BATCH_SIZE);
        command.setMaximumDuration(MAXIMUM_DURATION);
    }


    private void assertCommonResultProperties(ParticipantResult result)
    {
        assertEquals(PARTICIPANT_NAME, result.getParticipantName());
        assertEquals(REGISTERED_CLIENT_NAME, result.getRegisteredClientName());
        assertEquals(BATCH_SIZE, result.getBatchSize());
        assertEquals(MAXIMUM_DURATION, result.getMaximumDuration());
        assertEquals(TIME_TAKEN, result.getTimeTaken());
        assertEquals(NUMBER_OF_MESSAGES_PROCESSED, result.getNumberOfMessagesProcessed());
        assertEquals(TOTAL_PAYLOAD_PROCESSED, result.getTotalPayloadProcessed());
        assertEquals(PAYLOAD_SIZE, result.getPayloadSize());
    }

}
