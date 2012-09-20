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

import java.text.DecimalFormat;

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
    THROUGHPUT("throughputKbPerS", "#"),
    AVERAGE_LATENCY("averageLatency", "#"),
    CONFIGURED_CLIENT_NAME("clientName"),
    PARTICIPANT_NAME("participantName"),
    NUMBER_OF_MESSAGES_PROCESSED("numberOfMessages"),
    PAYLOAD_SIZE("payloadSizeB"),
    PRIORITY("priority"),
    TIME_TO_LIVE("timeToLiveMs"),
    ACKNOWLEDGE_MODE("acknowledgeMode"),
    DELIVERY_MODE("deliveryMode"),
    BATCH_SIZE("batchSize"),
    MAXIMUM_DURATION("maximumDurationMs"),
    PRODUCER_START_DELAY("producerStartDelayMs"),
    PRODUCER_INTERVAL("producerIntervalMs"),
    IS_TOPIC("isTopic"),
    IS_DURABLE_SUBSCRIPTION("isDurableSubscription"),
    IS_BROWSING_SUBSCRIPTION("isBrowsingSubscription"),
    IS_SELECTOR("isSelector"),
    IS_NO_LOCAL("isNoLocal"),
    IS_SYNCHRONOUS_CONSUMER("isSynchronousConsumer"),
    TOTAL_NUMBER_OF_CONSUMERS("totalNumberOfConsumers"),
    TOTAL_NUMBER_OF_PRODUCERS("totalNumberOfProducers"),
    TOTAL_PAYLOAD_PROCESSED("totalPayloadProcessedB"),
    TIME_TAKEN("timeTakenMs"),
    ERROR_MESSAGE("errorMessage"),
    MIN_LATENCY("minLatency"),
    MAX_LATENCY("maxLatency"),
    LATENCY_STANDARD_DEVIATION("latencyStandardDeviation"),
    MESSAGE_THROUGHPUT("throughputMessagesPerS")
    ;

    private String _displayName;
    private String _decimalFormat;

    ParticipantAttribute(String displayName)
    {
        _displayName = displayName;
    }

    ParticipantAttribute(String displayName, String decimalFormat)
    {
        _displayName = displayName;
        _decimalFormat = decimalFormat;
    }

    public String getDecimalFormat()
    {
        return _decimalFormat;
    }

    public String getDisplayName()
    {
        return _displayName;
    }

    public String format(Object attributeValue)
    {
        if(attributeValue == null)
        {
            return null;
        }

        String attributeAsString = String.valueOf(attributeValue);

        if(_decimalFormat != null)
        {
            DecimalFormat decimalFormat = new DecimalFormat(_decimalFormat);
            double attributeAsDoule = Double.valueOf(attributeAsString);
            return decimalFormat.format(attributeAsDoule);
        }
        else
        {
            return attributeAsString;
        }
    }
}
