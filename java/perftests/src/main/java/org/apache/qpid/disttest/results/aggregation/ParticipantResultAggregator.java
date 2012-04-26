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
import java.util.NavigableSet;
import java.util.TreeSet;

import org.apache.qpid.disttest.message.ParticipantResult;

public class ParticipantResultAggregator
{
    private final String _aggregatedResultName;
    private final Class<? extends ParticipantResult> _targetClass;

    private long _minStartDate = Long.MAX_VALUE;
    private long _maxEndDate = 0;
    private long _numberOfMessagesProcessed = 0;
    private long _totalPayloadProcessed = 0;

    private int _totalNumberOfConsumers = 0;
    private int _totalNumberOfProducers = 0;

    private NavigableSet<Integer> _encounteredPayloadSizes = new TreeSet<Integer>();
    private NavigableSet<Integer> _encounteredIterationNumbers = new TreeSet<Integer>();
    private NavigableSet<Integer> _encounteredBatchSizes = new TreeSet<Integer>();
    private NavigableSet<Integer> _encounteredAcknowledgeMode = new TreeSet<Integer>();
    private NavigableSet<String> _encountedTestNames = new TreeSet<String>();

    public ParticipantResultAggregator(Class<? extends ParticipantResult> targetClass, String aggregateResultName)
    {
        _aggregatedResultName = aggregateResultName;
        _targetClass = targetClass;
    }

    public void aggregate(ParticipantResult result)
    {
        if (isAggregatable(result))
        {
            rollupConstantAttributes(result);
            computeVariableAttributes(result);
        }
    }

    public ParticipantResult getAggregatedResult()
    {
        ParticipantResult aggregatedResult = new ParticipantResult(_aggregatedResultName);

        setRolledUpConstantAttributes(aggregatedResult);
        setComputedVariableAttributes(aggregatedResult);

        return aggregatedResult;
    }

    private boolean isAggregatable(ParticipantResult result)
    {
        return _targetClass.isAssignableFrom(result.getClass());
    }

    private void computeVariableAttributes(ParticipantResult result)
    {
        _numberOfMessagesProcessed += result.getNumberOfMessagesProcessed();
        _totalPayloadProcessed += result.getTotalPayloadProcessed();
        _totalNumberOfConsumers += result.getTotalNumberOfConsumers();
        _totalNumberOfProducers += result.getTotalNumberOfProducers();
        _minStartDate = Math.min(_minStartDate, result.getStartInMillis());
        _maxEndDate = Math.max(_maxEndDate, result.getEndInMillis());
    }

    private void rollupConstantAttributes(ParticipantResult result)
    {
        if (result.getTestName() != null)
        {
            _encountedTestNames.add(result.getTestName());
        }
        _encounteredPayloadSizes.add(result.getPayloadSize());
        _encounteredIterationNumbers.add(result.getIterationNumber());
        _encounteredBatchSizes.add(result.getBatchSize());
        _encounteredAcknowledgeMode.add(result.getAcknowledgeMode());
    }

    private void setComputedVariableAttributes(ParticipantResult aggregatedResult)
    {
        aggregatedResult.setNumberOfMessagesProcessed(_numberOfMessagesProcessed);
        aggregatedResult.setTotalPayloadProcessed(_totalPayloadProcessed);
        aggregatedResult.setTotalNumberOfConsumers(_totalNumberOfConsumers);
        aggregatedResult.setTotalNumberOfProducers(_totalNumberOfProducers);
        aggregatedResult.setStartDate(new Date(_minStartDate));
        aggregatedResult.setEndDate(new Date(_maxEndDate));
        aggregatedResult.setThroughput(calculateThroughputInKiloBytesPerSecond());
    }

    private void setRolledUpConstantAttributes(ParticipantResult aggregatedResult)
    {
        if (_encounteredIterationNumbers.size() == 1)
        {
            aggregatedResult.setIterationNumber( _encounteredIterationNumbers.first());
        }
        if (_encounteredPayloadSizes.size() == 1)
        {
            aggregatedResult.setPayloadSize(_encounteredPayloadSizes.first());
        }
        if (_encountedTestNames.size() == 1)
        {
            aggregatedResult.setTestName(_encountedTestNames.first());
        }
        if (_encounteredBatchSizes.size() == 1)
        {
            aggregatedResult.setBatchSize(_encounteredBatchSizes.first());
        }
        if (_encounteredAcknowledgeMode.size() == 1)
        {
            aggregatedResult.setAcknowledgeMode(_encounteredAcknowledgeMode.first());
        }
    }

    private double calculateThroughputInKiloBytesPerSecond()
    {
        double durationInMillis = _maxEndDate - _minStartDate;
        double durationInSeconds = durationInMillis / 1000;
        double totalPayloadProcessedInKiloBytes = ((double)_totalPayloadProcessed) / 1024;

        return totalPayloadProcessedInKiloBytes/durationInSeconds;
    }

}
