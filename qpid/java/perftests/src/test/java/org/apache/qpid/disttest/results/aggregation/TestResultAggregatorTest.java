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
package org.apache.qpid.disttest.results.aggregation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.controller.TestResult;
import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;
import org.apache.qpid.test.utils.QpidTestCase;

public class TestResultAggregatorTest extends QpidTestCase
{
    private static final String TEST1_NAME = "TEST1_NAME";
    private static final int TEST1_ITERATION_NUMBER = 1;

    private static final String CONSUMER_PARTICIPANT_NAME1 = "CONSUMER_PARTICIPANT_NAME1";
    private static final String CONSUMER_PARTICIPANT_NAME2 = "CONSUMER_PARTICIPANT_NAME2";

    private static final String PRODUCER_PARTICIPANT_NAME = "PRODUCER_PARTICIPANT_NAME";

    private static final long CONSUMER1_STARTDATE = 50;
    private static final long CONSUMER1_ENDDATE = 20000;

    private static final long CONSUMER2_STARTDATE = 100;
    private static final long CONSUMER2_ENDDATE = 21000;

    private static final long PRODUCER_STARTDATE = 0;
    private static final long PRODUCER_ENDDATE = 19000;

    private static final long NUMBER_OF_MESSAGES_PROCESSED_PER_CONSUMER = 50;
    private static final long NUMBER_OF_MESSAGES_CONSUMED_IN_TOTAL = NUMBER_OF_MESSAGES_PROCESSED_PER_CONSUMER * 2;
    private static final long NUMBER_OF_MESSAGES_PRODUCED = NUMBER_OF_MESSAGES_PROCESSED_PER_CONSUMER * 2;

    private static final int PAYLOAD_SIZE = 1024;
    private static final long TOTAL_PAYLOAD_PROCESSED_PER_CONSUMER = NUMBER_OF_MESSAGES_PROCESSED_PER_CONSUMER * PAYLOAD_SIZE;
    private static final long TOTAL_PAYLOAD_PRODUCED_IN_TOTAL =  TOTAL_PAYLOAD_PROCESSED_PER_CONSUMER * 2;

    private static final int EXPECTED_NUMBER_OF_AGGREGATED_RESULTS = 3;

    private static final int BATCH_SIZE = 3;

    private TestResultAggregator _aggregator = new TestResultAggregator();

    public void testAggregateTestResults()
    {
        ResultsForAllTests resultsForAllTests1 = mock(ResultsForAllTests.class);
        ResultsForAllTests resultsForAllTests2 = mock(ResultsForAllTests.class);

        ResultsForAllTests summaryResult1 = mock(ResultsForAllTests.class);
        ResultsForAllTests summaryResult2 = mock(ResultsForAllTests.class);

        when(resultsForAllTests1.getAllParticipantsResult()).thenReturn(summaryResult1);
        when(resultsForAllTests2.getAllParticipantsResult()).thenReturn(summaryResult2);

        ITestResult testResult1 = mock(ITestResult.class);
        ITestResult testResult2 = mock(ITestResult.class);

        when(summaryResult1.getTestResults()).thenReturn(Arrays.asList(testResult1));
        when(summaryResult2.getTestResults()).thenReturn(Arrays.asList(testResult2));

        ResultsForAllTests actualSummaryResults = _aggregator.aggregateTestResults(Arrays.asList(
                resultsForAllTests1,
                resultsForAllTests2));

        assertEquals(
                "Summary results should contain the all the 'all participants' test results",
                Arrays.asList(testResult1, testResult2),
                actualSummaryResults.getTestResults());
    }

    public void testAggregateResultsForTwoConsumerAndOneProducer() throws Exception
    {
        TestResult originalTestResult = createResultsFromTest();

        int numberOfOriginalParticipantResults = originalTestResult.getParticipantResults().size();
        int expectedNumberOfResults = numberOfOriginalParticipantResults + EXPECTED_NUMBER_OF_AGGREGATED_RESULTS;

        AggregatedTestResult aggregatedTestResult = _aggregator.aggregateTestResult(originalTestResult);

        aggregatedTestResult.getAllConsumerParticipantResult().getTotalPayloadProcessed();
        assertEquals(expectedNumberOfResults, aggregatedTestResult.getParticipantResults().size());

        assertMinimalAggregatedResults(
                aggregatedTestResult.getAllConsumerParticipantResult(),
                TEST1_NAME, TEST1_ITERATION_NUMBER,
                BATCH_SIZE, NUMBER_OF_MESSAGES_CONSUMED_IN_TOTAL, 2, 0);

        assertMinimalAggregatedResults(
                aggregatedTestResult.getAllProducerParticipantResult(),
                TEST1_NAME, TEST1_ITERATION_NUMBER,
                BATCH_SIZE, NUMBER_OF_MESSAGES_PRODUCED, 0, 1);

        assertMinimalAggregatedResults(
                aggregatedTestResult.getAllParticipantResult(),
                TEST1_NAME, TEST1_ITERATION_NUMBER,
                BATCH_SIZE, NUMBER_OF_MESSAGES_CONSUMED_IN_TOTAL, 2, 1);
    }

    public void testAggregateResultsWhenParticipantErrored()
    {
        ParticipantResult failedParticipantResult = new ParticipantResult();
        failedParticipantResult.setParticipantName(PRODUCER_PARTICIPANT_NAME);
        failedParticipantResult.setErrorMessage("error");
        TestResult result = new TestResult(TEST1_NAME);
        result.addParticipantResult(failedParticipantResult);

        AggregatedTestResult aggregatedTestResult = _aggregator.aggregateTestResult(result);
        assertEquals(TestResultAggregator.AGGREGATED_ERROR_MESSAGE, aggregatedTestResult.getAllParticipantResult().getErrorMessage());
    }

    public void testAggregateResultsForConsumerWithLatencyResults() throws Exception
    {
        TestResult originalTestResult = createResultsFromTest();
        List<ParticipantResult> results = originalTestResult.getParticipantResults();
        for (ParticipantResult participantResult : results)
        {
            if (participantResult instanceof ConsumerParticipantResult)
            {
                ((ConsumerParticipantResult)participantResult).setMessageLatencies(SeriesStatisticsTest.SERIES);
                break;
            }
        }

        int numberOfOriginalParticipantResults = originalTestResult.getParticipantResults().size();
        int expectedNumberOfResults = numberOfOriginalParticipantResults + EXPECTED_NUMBER_OF_AGGREGATED_RESULTS;

        AggregatedTestResult aggregatedTestResult = _aggregator.aggregateTestResult(originalTestResult);

        aggregatedTestResult.getAllConsumerParticipantResult().getTotalPayloadProcessed();
        assertEquals(expectedNumberOfResults, aggregatedTestResult.getParticipantResults().size());

        assertMinimalAggregatedResults(
                aggregatedTestResult.getAllConsumerParticipantResult(),
                TEST1_NAME, TEST1_ITERATION_NUMBER,
                BATCH_SIZE, NUMBER_OF_MESSAGES_CONSUMED_IN_TOTAL, 2, 0);

        assertLatencyAggregatedResults(aggregatedTestResult.getAllConsumerParticipantResult());

        assertMinimalAggregatedResults(
                aggregatedTestResult.getAllProducerParticipantResult(),
                TEST1_NAME, TEST1_ITERATION_NUMBER,
                BATCH_SIZE, NUMBER_OF_MESSAGES_PRODUCED, 0, 1);

        assertMinimalAggregatedResults(
                aggregatedTestResult.getAllParticipantResult(),
                TEST1_NAME, TEST1_ITERATION_NUMBER,
                BATCH_SIZE, NUMBER_OF_MESSAGES_CONSUMED_IN_TOTAL, 2, 1);

        int expectedThroughtput = (int)Math.round(NUMBER_OF_MESSAGES_PRODUCED * 1000.0d /(CONSUMER2_ENDDATE - PRODUCER_STARTDATE));
        ParticipantResult result = aggregatedTestResult.getAllParticipantResult();
        assertEquals("Unexpected message throughtput", expectedThroughtput, result.getMessageThroughput());
    }

    private void assertLatencyAggregatedResults(ParticipantResult allConsumerParticipantResult)
    {
        assertTrue("Unexpected result", allConsumerParticipantResult instanceof ConsumerParticipantResult);
        ConsumerParticipantResult results = (ConsumerParticipantResult)allConsumerParticipantResult;
        assertEquals("Unexpected average", 5.0, results.getAverageLatency(), 0.01);
        assertEquals("Unexpected min", 2, results.getMinLatency());
        assertEquals("Unexpected max", 9, results.getMaxLatency());
        assertEquals("Unexpected standard deviation", 2.0, results.getLatencyStandardDeviation(), 0.01);
    }

    private void assertMinimalAggregatedResults(ParticipantResult result, String expectedTestName, int expectedIterationNumber, int expectedBatchSize, long expectedNumberOfMessagesProcessed, int expectedTotalNumberOfConsumers, int expectedTotalNumberOfProducers)
    {
        assertEquals("Unexpected test name in " + result.getParticipantName(), expectedTestName, result.getTestName());
        assertEquals("Unexpected iteration number in " + result.getParticipantName(), expectedIterationNumber, result.getIterationNumber());
        assertEquals("Unexpected batch size " + result.getParticipantName(), expectedBatchSize, result.getBatchSize());
        assertEquals("Unexpected number of messages processed in " + result.getParticipantName(), expectedNumberOfMessagesProcessed, result.getNumberOfMessagesProcessed());
        assertEquals("Unexpected total number of consumers " + result.getParticipantName(), expectedTotalNumberOfConsumers, result.getTotalNumberOfConsumers());
        assertEquals("Unexpected total number of producers " + result.getParticipantName(), expectedTotalNumberOfProducers, result.getTotalNumberOfProducers());
    }

    private TestResult createResultsFromTest()
    {
        TestResult testResult = new TestResult(TEST1_NAME);

        ConsumerParticipantResult consumerResult1 = new ConsumerParticipantResult();
        setPropertiesOn(consumerResult1, TEST1_NAME, TEST1_ITERATION_NUMBER, CONSUMER_PARTICIPANT_NAME1, NUMBER_OF_MESSAGES_PROCESSED_PER_CONSUMER, BATCH_SIZE, PAYLOAD_SIZE, TOTAL_PAYLOAD_PROCESSED_PER_CONSUMER, CONSUMER1_STARTDATE, CONSUMER1_ENDDATE, 1, 0);
        testResult.addParticipantResult(consumerResult1);

        ConsumerParticipantResult consumerResult2 = new ConsumerParticipantResult();
        setPropertiesOn(consumerResult2, TEST1_NAME, TEST1_ITERATION_NUMBER, CONSUMER_PARTICIPANT_NAME2, NUMBER_OF_MESSAGES_PROCESSED_PER_CONSUMER, BATCH_SIZE, PAYLOAD_SIZE, TOTAL_PAYLOAD_PROCESSED_PER_CONSUMER, CONSUMER2_STARTDATE, CONSUMER2_ENDDATE, 1, 0);
        testResult.addParticipantResult(consumerResult2);

        ParticipantResult producerResult = new ProducerParticipantResult();
        setPropertiesOn(producerResult, TEST1_NAME, TEST1_ITERATION_NUMBER, PRODUCER_PARTICIPANT_NAME, NUMBER_OF_MESSAGES_PRODUCED, BATCH_SIZE, PAYLOAD_SIZE, TOTAL_PAYLOAD_PRODUCED_IN_TOTAL, PRODUCER_STARTDATE, PRODUCER_ENDDATE, 0, 1);
        testResult.addParticipantResult(producerResult);

        return testResult;
    }

    private void setPropertiesOn(ParticipantResult participantResult, String testName, int iterationNumber, String participantName, long numberOfMessagesProcessed, int batchSize, int payloadSize, long totalPayloadProcessed, long start, long end, int totalNumberOfConsumers, int totalNumberOfProducers)
    {
        participantResult.setParticipantName(participantName);
        participantResult.setTestName(testName);
        participantResult.setIterationNumber(iterationNumber);
        participantResult.setTotalNumberOfConsumers(totalNumberOfConsumers);
        participantResult.setTotalNumberOfProducers(totalNumberOfProducers);

        participantResult.setNumberOfMessagesProcessed(numberOfMessagesProcessed);
        participantResult.setPayloadSize(payloadSize);
        participantResult.setTotalPayloadProcessed(totalPayloadProcessed);
        participantResult.setStartDate(new Date(start));
        participantResult.setEndDate(new Date(end));
        participantResult.setBatchSize(batchSize);
    }

}
