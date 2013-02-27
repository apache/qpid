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
package org.apache.qpid.disttest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;

import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.results.aggregation.TestResultAggregator;
import org.apache.qpid.disttest.results.formatting.CSVFormatter;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

public class ResultsFileWriterTest extends QpidTestCase
{
    private CSVFormatter _csvFormater = mock(CSVFormatter.class);
    private TestResultAggregator _testResultAggregator = mock(TestResultAggregator.class);

    private File _outputDir = TestFileUtils.createTestDirectory();

    private ResultsFileWriter _resultsFileWriter = new ResultsFileWriter(_outputDir);

    @Override
    public void setUp()
    {
        _resultsFileWriter.setCsvFormater(_csvFormater);
        _resultsFileWriter.setTestResultAggregator(_testResultAggregator);
    }

    public void testWriteResultsToFile()
    {
        ResultsForAllTests resultsForAllTests = mock(ResultsForAllTests.class);

        String expectedCsvContents = "expected-csv-contents";
        when(_csvFormater.format(resultsForAllTests)).thenReturn(expectedCsvContents);

        _resultsFileWriter.writeResultsToFile(resultsForAllTests, "config.json");

        File resultsFile = new File(_outputDir, "config.csv");

        assertEquals(expectedCsvContents, FileUtils.readFileAsString(resultsFile));
    }

    public void testWriteResultsSummary()
    {
        ResultsForAllTests results1 = mock(ResultsForAllTests.class);
        ResultsForAllTests results2 = mock(ResultsForAllTests.class);
        ResultsForAllTests summaryResults = mock(ResultsForAllTests.class);

        when(_testResultAggregator.aggregateTestResults(Arrays.asList(results1, results2)))
            .thenReturn(summaryResults);

        String expectedSummaryFileContents = "expected-summary-file";

        when(_csvFormater.format(summaryResults))
            .thenReturn(expectedSummaryFileContents);

        _resultsFileWriter.writeResultsSummary(Arrays.asList(results1, results2));

        File summaryFile = new File(_outputDir, ResultsFileWriter.TEST_SUMMARY_FILE_NAME);

        assertEquals(expectedSummaryFileContents, FileUtils.readFileAsString(summaryFile));
    }

}
