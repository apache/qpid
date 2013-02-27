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
package org.apache.qpid.disttest.results.aggregation;

import java.util.Date;

import javax.jms.Session;

import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.test.utils.QpidTestCase;

public class ParticipantResultAggregatorTest extends QpidTestCase
{
    private ParticipantResultAggregator _aggregator = new ParticipantResultAggregator(ParticipantResult.class, AGGREGATED_RESULT_NAME);

    private static final String TEST_NAME = "TEST_NAME";
    private static final String AGGREGATED_RESULT_NAME = "AGGREGATED_RESULT_NAME";
    private static final int TEST_ITERATION_NUMBER = 1;

    private static final long PARTICIPANT1_STARTDATE = 50;
    private static final long PARTICIPANT1_ENDDATE = 20000;
    private static final long PARTICIPANT1_TOTAL_PROCESSED = 1024;
    private static final int PARTICIPANT1_NUMBER_OF_MESSAGES_PROCESSED = 20000;

    private static final long PARTICIPANT2_STARTDATE = 100;
    private static final long PARTICIPANT2_ENDDATE = 21000;
    private static final long PARTICIPANT2_TOTAL_PROCESSED = 2048;
    private static final int PARTICIPANT2_NUMBER_OF_MESSAGES_PROCESSED = 950;

    private static final long OVERALL_PROCESSED = PARTICIPANT1_TOTAL_PROCESSED + PARTICIPANT2_TOTAL_PROCESSED;
    private static final double OVERALL_TIMETAKEN = PARTICIPANT2_ENDDATE - PARTICIPANT1_STARTDATE;
    private static final long OVERALL_NUMBER_OF_MESSAGES_PROCESSED = PARTICIPANT1_NUMBER_OF_MESSAGES_PROCESSED + PARTICIPANT2_NUMBER_OF_MESSAGES_PROCESSED;

    private static final double EXPECTED_AGGREGATED_ALL_THROUGHPUT = ((OVERALL_PROCESSED)/1024)/((OVERALL_TIMETAKEN)/1000);
    private static final int EXPECTED_AGGREGATED_MESSAGE_THROUGHPUT = (int)(OVERALL_NUMBER_OF_MESSAGES_PROCESSED * 1000.0d/OVERALL_TIMETAKEN);

    public void testStartAndEndDateForOneParticipantResult()
    {
        ParticipantResult result = new ParticipantResult();
        result.setStartDate(new Date(PARTICIPANT1_STARTDATE));
        result.setEndDate(new Date(PARTICIPANT1_ENDDATE));

        _aggregator.aggregate(result);
        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(PARTICIPANT1_STARTDATE, aggregratedResult.getStartInMillis());
        assertEquals(PARTICIPANT1_ENDDATE, aggregratedResult.getEndInMillis());
    }

    public void testStartAndEndDateForTwoParticipantResults()
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setStartDate(new Date(PARTICIPANT1_STARTDATE));
        result1.setEndDate(new Date(PARTICIPANT1_ENDDATE));

        ParticipantResult result2 = new ParticipantResult();
        result2.setStartDate(new Date(PARTICIPANT2_STARTDATE));
        result2.setEndDate(new Date(PARTICIPANT2_ENDDATE));

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(PARTICIPANT1_STARTDATE, aggregratedResult.getStartInMillis());
        assertEquals(PARTICIPANT2_ENDDATE, aggregratedResult.getEndInMillis());
    }

    public void testComputeNumberOfMessagesProcessed()
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setNumberOfMessagesProcessed(10);

        ParticipantResult result2 = new ParticipantResult();
        result2.setNumberOfMessagesProcessed(15);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(25, aggregratedResult.getNumberOfMessagesProcessed());
    }

    public void testComputeTotalPayloadProcessed()
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setTotalPayloadProcessed(PARTICIPANT1_TOTAL_PROCESSED);

        ParticipantResult result2 = new ParticipantResult();
        result2.setTotalPayloadProcessed(PARTICIPANT2_TOTAL_PROCESSED);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(OVERALL_PROCESSED, aggregratedResult.getTotalPayloadProcessed());
    }

    public void testComputeThroughput()
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setStartDate(new Date(PARTICIPANT1_STARTDATE));
        result1.setEndDate(new Date(PARTICIPANT1_ENDDATE));
        result1.setTotalPayloadProcessed(PARTICIPANT1_TOTAL_PROCESSED);

        ParticipantResult result2 = new ParticipantResult();
        result2.setStartDate(new Date(PARTICIPANT2_STARTDATE));
        result2.setEndDate(new Date(PARTICIPANT2_ENDDATE));
        result2.setTotalPayloadProcessed(PARTICIPANT2_TOTAL_PROCESSED);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(EXPECTED_AGGREGATED_ALL_THROUGHPUT, aggregratedResult.getThroughput(), 0.1);
    }

    public void testComputeMessageThroughput()
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setStartDate(new Date(PARTICIPANT1_STARTDATE));
        result1.setEndDate(new Date(PARTICIPANT1_ENDDATE));
        result1.setNumberOfMessagesProcessed(PARTICIPANT1_NUMBER_OF_MESSAGES_PROCESSED);

        ParticipantResult result2 = new ParticipantResult();
        result2.setStartDate(new Date(PARTICIPANT2_STARTDATE));
        result2.setEndDate(new Date(PARTICIPANT2_ENDDATE));
        result2.setNumberOfMessagesProcessed(PARTICIPANT2_NUMBER_OF_MESSAGES_PROCESSED);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(EXPECTED_AGGREGATED_MESSAGE_THROUGHPUT, aggregratedResult.getMessageThroughput());

    }

    public void testConstantTestNameAndIterationNumberRolledUp() throws Exception
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setTestName(TEST_NAME);
        result1.setIterationNumber(TEST_ITERATION_NUMBER);

        ParticipantResult result2 = new ParticipantResult();
        result2.setTestName(TEST_NAME);
        result2.setIterationNumber(TEST_ITERATION_NUMBER);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(TEST_ITERATION_NUMBER, aggregratedResult.getIterationNumber());
        assertEquals(TEST_NAME, aggregratedResult.getTestName());
    }

    public void testConstantPayloadSizesRolledUp() throws Exception
    {
        final int payloadSize = 1024;

        ParticipantResult result1 = new ParticipantResult();
        result1.setPayloadSize(payloadSize);

        ParticipantResult result2 = new ParticipantResult();
        result2.setPayloadSize(payloadSize);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(payloadSize, aggregratedResult.getPayloadSize());
    }

    public void testDifferingPayloadSizesNotRolledUp() throws Exception
    {
        final int payload1Size = 1024;
        final int payload2Size = 2048;

        ParticipantResult result1 = new ParticipantResult();
        result1.setPayloadSize(payload1Size);

        ParticipantResult result2 = new ParticipantResult();
        result2.setPayloadSize(payload2Size);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(0, aggregratedResult.getPayloadSize());
    }

    public void testConstantBatchSizesRolledUp() throws Exception
    {
        final int batchSize = 10;

        ParticipantResult result1 = new ParticipantResult();
        result1.setBatchSize(batchSize);

        ParticipantResult result2 = new ParticipantResult();
        result2.setBatchSize(batchSize);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(batchSize, aggregratedResult.getBatchSize());
    }

    public void testDifferingBatchSizesNotRolledUp() throws Exception
    {
        final int batch1Size = 10;
        final int batch2Size = 20;

        ParticipantResult result1 = new ParticipantResult();
        result1.setBatchSize(batch1Size);

        ParticipantResult result2 = new ParticipantResult();
        result2.setBatchSize(batch2Size);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(0, aggregratedResult.getBatchSize());
    }

    public void testConstantAcknowledgeModesRolledUp() throws Exception
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setAcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE);

        ParticipantResult result2 = new ParticipantResult();
        result2.setAcknowledgeMode(Session.DUPS_OK_ACKNOWLEDGE);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(Session.DUPS_OK_ACKNOWLEDGE, aggregratedResult.getAcknowledgeMode());
    }

    public void testDifferingAcknowledgeModesNotRolledUp() throws Exception
    {
        ParticipantResult result1 = new ParticipantResult();
        result1.setBatchSize(Session.AUTO_ACKNOWLEDGE);

        ParticipantResult result2 = new ParticipantResult();
        result2.setBatchSize(Session.SESSION_TRANSACTED);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(-1, aggregratedResult.getAcknowledgeMode());
    }

    public void testSumNumberOfConsumerAndProducers() throws Exception
    {
        final int expectedNumberOfProducers = 1;
        final int expectedNumberOfConsumers = 2;

        ParticipantResult result1 = new ParticipantResult();
        result1.setTotalNumberOfConsumers(1);

        ParticipantResult result2 = new ParticipantResult();
        result2.setTotalNumberOfConsumers(1);

        ParticipantResult result3 = new ParticipantResult();
        result2.setTotalNumberOfProducers(1);

        _aggregator.aggregate(result1);
        _aggregator.aggregate(result2);
        _aggregator.aggregate(result3);

        ParticipantResult aggregratedResult = _aggregator.getAggregatedResult();
        assertEquals(expectedNumberOfConsumers, aggregratedResult.getTotalNumberOfConsumers());
        assertEquals(expectedNumberOfProducers, aggregratedResult.getTotalNumberOfProducers());
    }

}
