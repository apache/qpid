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
    TEST_NAME("Test Name"),
    ITERATION_NUMBER("Iteration number"),
    CONFIGURED_CLIENT_NAME("Client Name"),
    PARTICIPANT_NAME("Participant name"),
    NUMBER_OF_MESSAGES_PROCESSED("Number of messages"),
    PAYLOAD_SIZE("Payload size (bytes)"),
    PRIORITY("Priority"),
    TIME_TO_LIVE("Time to live (ms)"),
    DELIVERY_MODE("Delivery mode"),
    BATCH_SIZE("Batch size"),
    MAXIMUM_DURATION("Maximum duration (ms)"),
    PRODUCER_START_DELAY("Producer start delay (ms)"),
    PRODUCER_INTERVAL("Producer interval (ms)"),
    IS_TOPIC("Is topic"),
    IS_DURABLE_SUBSCRIPTION("Is durable subscription"),
    IS_BROWSIING_SUBSCRIPTION("Is browsing subscription"),
    IS_SELECTOR("Is selector"),
    IS_NO_LOCAL("Is no local"),
    IS_SYNCHRONOUS_CONSUMER("Is synchronous consumer"),
    TOTAL_PAYLOAD_PROCESSED("Total payload processed (bytes)"),
    THROUGHPUT("Throughput (kbytes/s)"),
    TIME_TAKEN("Time taken (ms)"),
    ERROR_MESSAGE("Error message");

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
