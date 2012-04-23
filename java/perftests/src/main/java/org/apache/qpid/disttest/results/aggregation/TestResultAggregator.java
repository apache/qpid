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
        AggregatedTestResult newTestResult = new AggregatedTestResult(originalTestResult);

        ParticipantResultAggregator consumerParticipantResultAggregator = new ParticipantResultAggregator(ConsumerParticipantResult.class,
                                                                                                          ALL_CONSUMER_PARTICIPANTS_NAME);
        ParticipantResultAggregator producerParticipantResultAggregator = new ParticipantResultAggregator(ProducerParticipantResult.class,
                                                                                                          ALL_PRODUCER_PARTICIPANTS_NAME);

        boolean hasError = aggregate(originalTestResult,
                                     consumerParticipantResultAggregator,
                                     producerParticipantResultAggregator);

        ParticipantResult aggregatedProducerResult = producerParticipantResultAggregator.getAggregatedResult();
        newTestResult.setAllProducerParticipantResult(aggregatedProducerResult);

        ParticipantResult aggregaredConsumerResult = consumerParticipantResultAggregator.getAggregatedResult();
        newTestResult.setAllConsumerParticipantResult(aggregaredConsumerResult);

        ParticipantResult aggregatedAllResult = buildAllResultFromOtherAggregatedResults(
                aggregatedProducerResult, aggregaredConsumerResult);

        if (hasError)
        {
            aggregatedAllResult.setErrorMessage(TestResultAggregator.AGGREGATED_ERROR_MESSAGE);
        }
        newTestResult.setAllParticipantResult(aggregatedAllResult);

        return newTestResult;
    }

    private boolean aggregate(ITestResult originalTestResult,
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

    private ParticipantResult buildAllResultFromOtherAggregatedResults(
            ParticipantResult aggregatedProducerResult, ParticipantResult aggregatedConsumerResult)
    {
        ParticipantResult aggregatedAllResult = new ParticipantResult(ALL_PARTICIPANTS_NAME);
        aggregatedAllResult.setStartDate(aggregatedProducerResult.getStartDate());

        aggregatedAllResult.setEndDate(aggregatedConsumerResult.getEndDate());

        aggregatedAllResult.setIterationNumber(aggregatedConsumerResult.getIterationNumber());
        aggregatedAllResult.setTestName(aggregatedConsumerResult.getTestName());
        aggregatedAllResult.setNumberOfMessagesProcessed(aggregatedConsumerResult.getNumberOfMessagesProcessed());
        aggregatedAllResult.setPayloadSize(aggregatedConsumerResult.getPayloadSize());
        aggregatedAllResult.setTotalPayloadProcessed(aggregatedConsumerResult.getTotalPayloadProcessed());
        aggregatedAllResult.setThroughput(aggregatedConsumerResult.getThroughput());
        aggregatedAllResult.setTotalNumberOfProducers(aggregatedProducerResult.getTotalNumberOfProducers());
        aggregatedAllResult.setTotalNumberOfConsumers(aggregatedConsumerResult.getTotalNumberOfConsumers());

        return aggregatedAllResult;
    }
}
