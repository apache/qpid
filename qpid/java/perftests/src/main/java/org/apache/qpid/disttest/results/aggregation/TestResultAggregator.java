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

import org.apache.qpid.disttest.message.ConsumerParticipantResult;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.message.ProducerParticipantResult;

public class TestResultAggregator
{
    static final String AGGREGATED_ERROR_MESSAGE = "One or more participants reported errors.";
    public static final String ALL_PARTICIPANTS_NAME = "All";
    public static final String ALL_PRODUCER_PARTICIPANTS_NAME = "All Producers";
    public static final String ALL_CONSUMER_PARTICIPANTS_NAME = "All Consumers";

    public AggregatedTestResult aggregateTestResult(ITestResult originalTestResult)
    {
        ParticipantResultAggregator consumerResultAggregator = new ParticipantResultAggregator(ConsumerParticipantResult.class,
                                                                                               ALL_CONSUMER_PARTICIPANTS_NAME);
        ParticipantResultAggregator producerResultAggregator = new ParticipantResultAggregator(ProducerParticipantResult.class,
                                                                                               ALL_PRODUCER_PARTICIPANTS_NAME);
        ParticipantResultAggregator aggregatedResultsAggregator = new ParticipantResultAggregator(ParticipantResult.class,
                                                                                               ALL_PARTICIPANTS_NAME);

        boolean hasError = aggregateOriginalResults(originalTestResult,
                                     consumerResultAggregator,
                                     producerResultAggregator);

        ParticipantResult aggregatedProducerResult = producerResultAggregator.getAggregatedResult();
        ParticipantResult aggregaredConsumerResult = consumerResultAggregator.getAggregatedResult();

        ParticipantResult aggregatedAllResult = aggregateAggregatedResults(
                aggregatedResultsAggregator, aggregatedProducerResult,
                aggregaredConsumerResult);

        applyNonAggregateablesToAll(aggregatedAllResult,
                aggregatedProducerResult, aggregaredConsumerResult);

        AggregatedTestResult newTestResult = new AggregatedTestResult(originalTestResult);
        newTestResult.setAllProducerParticipantResult(aggregatedProducerResult);
        newTestResult.setAllConsumerParticipantResult(aggregaredConsumerResult);
        newTestResult.setAllParticipantResult(aggregatedAllResult);

        if (hasError)
        {
            aggregatedAllResult.setErrorMessage(TestResultAggregator.AGGREGATED_ERROR_MESSAGE);
        }

        return newTestResult;
    }

    private ParticipantResult aggregateAggregatedResults(
            ParticipantResultAggregator aggregatedResultsAggregator,
            ParticipantResult aggregatedProducerResult,
            ParticipantResult aggregaredConsumerResult)
    {
        aggregatedResultsAggregator.aggregate(aggregatedProducerResult);
        aggregatedResultsAggregator.aggregate(aggregaredConsumerResult);
        ParticipantResult aggregatedAllResult = aggregatedResultsAggregator.getAggregatedResult();
        return aggregatedAllResult;
    }

    private boolean aggregateOriginalResults(ITestResult originalTestResult,
            ParticipantResultAggregator consumerParticipantResultAggregator,
            ParticipantResultAggregator producerParticipantResultAggregator)
    {
        boolean hasError = false;
        for (ParticipantResult result : originalTestResult.getParticipantResults())
        {
            consumerParticipantResultAggregator.aggregate(result);
            producerParticipantResultAggregator.aggregate(result);

            if (result.hasError())
            {
                hasError = true;
            }
        }
        return hasError;
    }

    private void applyNonAggregateablesToAll(ParticipantResult aggregatedAllResult, ParticipantResult aggregatedProducerResult, ParticipantResult aggregatedConsumerResult)
    {
        aggregatedAllResult.setStartDate(aggregatedProducerResult.getStartDate());
        aggregatedAllResult.setEndDate(aggregatedConsumerResult.getEndDate());

        aggregatedAllResult.setNumberOfMessagesProcessed(aggregatedConsumerResult.getNumberOfMessagesProcessed());
        aggregatedAllResult.setTotalPayloadProcessed(aggregatedConsumerResult.getTotalPayloadProcessed());
        aggregatedAllResult.setThroughput(aggregatedConsumerResult.getThroughput());
    }
}
