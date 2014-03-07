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
package org.apache.qpid.disttest.results;

import java.util.List;

import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.message.ParticipantResult;
import org.apache.qpid.disttest.results.aggregation.ITestResult;

public class BenchmarkResultWriter implements ResultsWriter
{
    private  final boolean _reportMessageTotals;

    public BenchmarkResultWriter(boolean reportMessageTotals)
    {
        _reportMessageTotals = reportMessageTotals;
    }

    @Override
    public void writeResults(ResultsForAllTests resultsForAllTests, String testConfigFile)
    {
        for (ITestResult allResult : resultsForAllTests.getAllParticipantsResult().getTestResults())
        {
            ParticipantResult allRowData = allResult.getParticipantResults().iterator().next();

            if (allRowData.getErrorMessage() == null)
            {
                final String output;
                if (_reportMessageTotals)
                {
                    output = String.format("%s : %,d (total payload/bytes) : %,d (time taken/ms) : %,d (total messages) : %,d (messages/s) %,.2f (Kbytes/s)",
                            allResult.getName(), allRowData.getTotalPayloadProcessed(), allRowData.getTimeTaken(), allRowData.getNumberOfMessagesProcessed(), allRowData.getMessageThroughput(), allRowData.getThroughput());
                }
                else
                {
                    output = String.format("%s : %,d (messages/s) %,.2f (Kbytes/s)", allResult.getName(), allRowData.getMessageThroughput(), allRowData.getThroughput());
                }
                System.out.println(output);
            }
            else
            {
                System.err.println(allRowData.getErrorMessage());
            }
        }
    }

    @Override
    public void writeResultsSummary(List<ResultsForAllTests> allResultsList)
    {
    }
}
