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

import org.apache.qpid.disttest.client.Participant;

/**
 * Meta-date representing the attributes of {@link Participant} that we write to the test output file.
 *
 * Order of declaration is currently important - it determines they order they appear in the output.
 *
 * @see OutputAttribute
 */
public enum ParticipantAttribute
{
    TEST_NAME("testName"),
    ITERATION_NUMBER("iterationNumber"),
    CONFIGURED_CLIENT_NAME("clientName"),
    PARTICIPANT_NAME("participantName"),
    NUMBER_OF_MESSAGES_PROCESSED("numberOfMessages"),
    PAYLOAD_SIZE("payloadSizeB"),
    PRIORITY("priority"),
    TIME_TO_LIVE("timeToLiveMs"),
    DELIVERY_MODE("deliveryMode"),
    BATCH_SIZE("batchSize"),
    MAXIMUM_DURATION("maximumDurationMs"),
    PRODUCER_START_DELAY("producerStartDelayMs"),
    PRODUCER_INTERVAL("producerIntervalMs"),
    IS_TOPIC("isTopic"),
    IS_DURABLE_SUBSCRIPTION("isDurableSubscription"),
    IS_BROWSIING_SUBSCRIPTION("isBrowsingSubscription"),
    IS_SELECTOR("isSelector"),
    IS_NO_LOCAL("isNoLocal"),
    IS_SYNCHRONOUS_CONSUMER("isSynchronousConsumer"),
    TOTAL_NUMBER_OF_CONSUMERS("totalNumberOfConsumers"),
    TOTAL_NUMBER_OF_PRODUCERS("totalNumberOfProducers"),
    TOTAL_PAYLOAD_PROCESSED("totalPayloadProcessedB"),
    THROUGHPUT("throughputKbPerS"),
    TIME_TAKEN("timeTakenMs"),
    ERROR_MESSAGE("errorMessage");

    private String _displayName;

    ParticipantAttribute(String displayName)
    {
        _displayName = displayName;
    }

    public String getDisplayName()
    {
        return _displayName;
    }
}
