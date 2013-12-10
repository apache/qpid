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

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.disttest.message.ParticipantResult;

public class AggregatedTestResult implements ITestResult
{
    private ParticipantResult _allParticipantResult;
    private ParticipantResult _allConsumerParticipantResult;
    private ParticipantResult _allProducerParticipantResult;
    private final ITestResult _originalTestResult;

    public AggregatedTestResult(ITestResult originalTestResult)
    {
        _originalTestResult = originalTestResult;
    }

    /**
     * Returns the result where {@link ParticipantResult#getNumberOfMessagesProcessed()}
     * is the total number of messages consumed during the test, and {@link ParticipantResult#getTimeTaken()}
     * is the time between the start of the first producer and the end of the last consumer to finish.
     */
    public ParticipantResult getAllParticipantResult()
    {
        return _allParticipantResult;
    }

    public void setAllParticipantResult(ParticipantResult allParticipantResult)
    {
        _allParticipantResult = allParticipantResult;
    }

    public ParticipantResult getAllConsumerParticipantResult()
    {
        return _allConsumerParticipantResult;
    }
    public void setAllConsumerParticipantResult(ParticipantResult allConsumerParticipantResult)
    {
        _allConsumerParticipantResult = allConsumerParticipantResult;
    }
    public ParticipantResult getAllProducerParticipantResult()
    {
        return _allProducerParticipantResult;
    }
    public void setAllProducerParticipantResult(ParticipantResult allProducerParticipantResult)
    {
        _allProducerParticipantResult = allProducerParticipantResult;
    }

    // TODO  should weaken to Collection
    @Override
    public List<ParticipantResult> getParticipantResults()
    {
        List<ParticipantResult> allParticipantResults = new ArrayList<ParticipantResult>(_originalTestResult.getParticipantResults());

        allParticipantResults.add(_allConsumerParticipantResult);
        allParticipantResults.add(_allProducerParticipantResult);
        allParticipantResults.add(_allParticipantResult);

        return allParticipantResults;
    }

    @Override
    public boolean hasErrors()
    {
        return _originalTestResult.hasErrors();
    }

    @Override
    public String getName()
    {
        return _originalTestResult.getName();
    }



}
