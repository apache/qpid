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
package org.apache.qpid.disttest.results.formatting;

import static org.apache.qpid.disttest.message.ParticipantAttribute.BATCH_SIZE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.CONFIGURED_CLIENT_NAME;
import static org.apache.qpid.disttest.message.ParticipantAttribute.DELIVERY_MODE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.ERROR_MESSAGE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.IS_BROWSIING_SUBSCRIPTION;
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
import static org.apache.qpid.disttest.message.ParticipantAttribute.THROUGHPUT;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TAKEN;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TIME_TO_LIVE;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_NUMBER_OF_CONSUMERS;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_NUMBER_OF_PRODUCERS;
import static org.apache.qpid.disttest.message.ParticipantAttribute.TOTAL_PAYLOAD_PROCESSED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.controller.TestResult;
import org.apache.qpid.disttest.message.ParticipantAttribute;
import org.apache.qpid.disttest.message.ParticipantResult;

public class CSVFormaterTest extends TestCase
{
    private static final String TEST1 = "TEST1";
    private static final String PARTICIPANT = "PARTICIPANT";
    private static final String CONFIGURED_CLIENT1 = "CONFIGURED_CLIENT1";

    private CSVFormater _formatter = new CSVFormater();

    public void testResultsFileWithWithOneRow() throws Exception
    {
        ParticipantResult participantResult = mock(ParticipantResult.class);
        Map<ParticipantAttribute, Object> participantAttributes = getParticipantAttributes();

        when(participantResult.getAttributes()).thenReturn(participantAttributes);
        when(participantResult.getParticipantName()).thenReturn(PARTICIPANT);

        TestResult testResult = new TestResult(TEST1);
        testResult.addParticipantResult(participantResult);

        ResultsForAllTests resultsForAllTests = new ResultsForAllTests();
        resultsForAllTests.add(testResult);

        String output = _formatter.format(resultsForAllTests);

        String expectedOutput = readCsvOutputFileAsString("expectedOutput.csv");

        assertEquals(expectedOutput, output);
    }

    private Map<ParticipantAttribute, Object> getParticipantAttributes()
    {
        Map<ParticipantAttribute, Object> participantAttributes = new HashMap<ParticipantAttribute, Object>();

        participantAttributes.put(TEST_NAME, TEST1);
        participantAttributes.put(ITERATION_NUMBER, 0);
        participantAttributes.put(CONFIGURED_CLIENT_NAME, CONFIGURED_CLIENT1);
        participantAttributes.put(PARTICIPANT_NAME, PARTICIPANT);
        participantAttributes.put(NUMBER_OF_MESSAGES_PROCESSED, 1);
        participantAttributes.put(PAYLOAD_SIZE, 2);
        participantAttributes.put(PRIORITY, 3);
        participantAttributes.put(TIME_TO_LIVE, 4);
        participantAttributes.put(DELIVERY_MODE, 5);
        participantAttributes.put(BATCH_SIZE, 6);
        participantAttributes.put(MAXIMUM_DURATION, 7);
        participantAttributes.put(PRODUCER_START_DELAY, 8);
        participantAttributes.put(PRODUCER_INTERVAL, 9);
        participantAttributes.put(IS_TOPIC, true);
        participantAttributes.put(IS_DURABLE_SUBSCRIPTION, false);
        participantAttributes.put(IS_BROWSIING_SUBSCRIPTION, true);
        participantAttributes.put(IS_SELECTOR, false);
        participantAttributes.put(IS_NO_LOCAL, true);
        participantAttributes.put(IS_SYNCHRONOUS_CONSUMER, false);
        participantAttributes.put(TOTAL_NUMBER_OF_CONSUMERS, 1);
        participantAttributes.put(TOTAL_NUMBER_OF_PRODUCERS, 2);
        participantAttributes.put(TOTAL_PAYLOAD_PROCESSED, 1024);
        participantAttributes.put(THROUGHPUT, 2048);
        participantAttributes.put(TIME_TAKEN, 1000);
        participantAttributes.put(ERROR_MESSAGE, "error");

        return participantAttributes;
    }

    private String readCsvOutputFileAsString(String filename) throws Exception
    {
        InputStream is = getClass().getResourceAsStream(filename);
        assertNotNull(is);

        StringBuilder output = new StringBuilder();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line = null;
        while((line = br.readLine()) != null)
        {
            output.append(line);
            output.append("\n");
        }

        return output.toString();
    }

}
