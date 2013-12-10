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
package org.apache.qpid.disttest.message;

import static org.apache.qpid.disttest.message.ParticipantAttribute.ACKNOWLEDGE_MODE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.BATCH_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.CONFIGURED_CLIENT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.DELIVERY_MODE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.ERROR_MESSAGE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_BROWSING_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_DURABLE_SUBSCRIPTION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_NO_LOCAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SELECTOR;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_SYNCHRONOUS_CONSUMER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_TOPIC;
import static org.apache.qpid.disttest.message.ParticipantAttribute.ITERATION_NUMBER;
import static org.apache.qpid.disttest.message.ParticipantAttribute.MAXIMUM_DURATION;
import static org.apache.qpid.disttest.message.ParticipantAttribute.NUMBER_OF_MESSAGES_PROCESSED;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PARTICIPANT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PAYLOAD_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRIORITY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRODUCER_INTERVAL;
import static org.apache.qpid.disttest.message.ParticipantAttribute.PRODUCER_START_DELAY;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TEST_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TAKEN;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TO_LIVE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_NUMBER_OF_CONSUMERS;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_NUMBER_OF_PRODUCERS;

import java.util.Date;

import javax.jms.DeliveryMode;

import org.apache.qpid.test.utils.QpidTestCase;

public class ParticipantResultTest extends QpidTestCase
{

    public void testSharedParticipantResultAttributes() throws Exception
    {
        final String participantName = "PARTICIPANT_NAME1";
        final String testName = "TEST_NAME1";
        String clientConfiguredName = "CLIENT_CONFIGURED_NAME";
        String errorMessage = "errorMessage";
        int iterationNumber = 1;

        ParticipantResult result = new ParticipantResult();

        long numberOfMessages = 500;
        long timeTaken = 30;
        int batchSize = 10;

        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeTaken;
        long maximumDuration = 1000;

        int totalNumberOfConsumers = 1;
        int totalNumberOfProducers = 1;

        int acknowledgeMode = 1;

        result.setParticipantName(participantName);
        result.setTestName(testName);
        result.setIterationNumber(iterationNumber);
        result.setConfiguredClientName(clientConfiguredName);

        result.setAcknowledgeMode(acknowledgeMode);
        result.setNumberOfMessagesProcessed(numberOfMessages);
        result.setConfiguredClientName(clientConfiguredName);
        result.setBatchSize(batchSize);

        result.setStartDate(new Date(startTime));
        result.setEndDate(new Date(endTime));
        result.setMaximumDuration(maximumDuration);

        result.setTotalNumberOfConsumers(totalNumberOfConsumers);
        result.setTotalNumberOfProducers(totalNumberOfProducers);

        result.setErrorMessage(errorMessage);

        assertEquals(participantName,      result.getAttributes().get(PARTICIPANT_NAME));
        assertEquals(testName,             result.getAttributes().get(TEST_NAME));
        assertEquals(iterationNumber,         result.getAttributes().get(ITERATION_NUMBER));
        assertEquals(clientConfiguredName,   result.getAttributes().get(CONFIGURED_CLIENT_NAME));
        assertEquals(numberOfMessages,       result.getAttributes().get(NUMBER_OF_MESSAGES_PROCESSED));
        assertEquals(timeTaken,              result.getAttributes().get(TIME_TAKEN));
        assertEquals(timeTaken,              result.getAttributes().get(TIME_TAKEN));
        assertEquals(timeTaken,              result.getAttributes().get(TIME_TAKEN));
        assertEquals(batchSize,              result.getAttributes().get(BATCH_SIZE));
        assertEquals(maximumDuration,        result.getAttributes().get(MAXIMUM_DURATION));
        assertEquals(totalNumberOfConsumers, result.getAttributes().get(TOTAL_NUMBER_OF_CONSUMERS));
        assertEquals(totalNumberOfProducers, result.getAttributes().get(TOTAL_NUMBER_OF_PRODUCERS));
        assertEquals(acknowledgeMode,        result.getAttributes().get(ACKNOWLEDGE_MODE));
        assertEquals(errorMessage,           result.getAttributes().get(ERROR_MESSAGE));
    }

    public void testConsumerParticipantResultAttributes() throws Exception
    {
        ConsumerParticipantResult result = new ConsumerParticipantResult();

        boolean topic = true;
        boolean durable = true;
        boolean browsingSubscription = false;
        boolean selector = true;
        boolean noLocal = false;
        boolean synchronousConsumer = true;

        result.setTopic(topic);
        result.setDurableSubscription(durable);
        result.setBrowsingSubscription(browsingSubscription);
        result.setSelector(selector);
        result.setNoLocal(noLocal);
        result.setSynchronousConsumer(synchronousConsumer);

        assertEquals(topic,                  result.getAttributes().get(IS_TOPIC));
        assertEquals(durable,                result.getAttributes().get(IS_DURABLE_SUBSCRIPTION));
        assertEquals(browsingSubscription,   result.getAttributes().get(IS_BROWSING_SUBSCRIPTION));
        assertEquals(selector,               result.getAttributes().get(IS_SELECTOR));
        assertEquals(noLocal,                result.getAttributes().get(IS_NO_LOCAL));
        assertEquals(synchronousConsumer,    result.getAttributes().get(IS_SYNCHRONOUS_CONSUMER));
    }

    public void testProducerParticipantResultAttributes() throws Exception
    {
        ProducerParticipantResult result = new ProducerParticipantResult();

        int priority = 2;
        long timeToLive = 30;
        long producerStartDelay = 40;
        long producerInterval = 50;
        int messageSize = 60;
        int deliveryMode = DeliveryMode.PERSISTENT;

        result.setPriority(priority);
        result.setTimeToLive(timeToLive);
        result.setStartDelay(producerStartDelay);
        result.setInterval(producerInterval);
        result.setPayloadSize(messageSize);
        result.setDeliveryMode(deliveryMode);


        assertEquals(priority,           result.getAttributes().get(PRIORITY));
        assertEquals(timeToLive,         result.getAttributes().get(TIME_TO_LIVE));
        assertEquals(producerStartDelay, result.getAttributes().get(PRODUCER_START_DELAY));
        assertEquals(producerInterval,   result.getAttributes().get(PRODUCER_INTERVAL));
        assertEquals(messageSize,        result.getAttributes().get(PAYLOAD_SIZE));
        assertEquals(deliveryMode,       result.getAttributes().get(DELIVERY_MODE));
    }
}
