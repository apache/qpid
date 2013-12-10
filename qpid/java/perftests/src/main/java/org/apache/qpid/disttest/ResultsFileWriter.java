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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.qpid.disttest.controller.ResultsForAllTests;
import org.apache.qpid.disttest.results.aggregation.TestResultAggregator;
import org.apache.qpid.disttest.results.formatting.CSVFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultsFileWriter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ResultsFileWriter.class);

    static final String TEST_SUMMARY_FILE_NAME = "test-summary.csv";

    private final File _outputDir;

    private CSVFormatter _csvFormater = new CSVFormatter();

    private TestResultAggregator _testResultAggregator = new TestResultAggregator();

    public ResultsFileWriter(File outputDir)
    {
        _outputDir = outputDir;
    }

    public void writeResultsToFile(ResultsForAllTests resultsForAllTests, String testConfigFile)
    {
        final String outputFile = generateOutputCsvNameFrom(testConfigFile);
        writeResultsToOutputFile(resultsForAllTests, outputFile);
    }

    public void writeResultsSummary(List<ResultsForAllTests> allResultsList)
    {
        ResultsForAllTests combinedResults = _testResultAggregator.aggregateTestResults(allResultsList);
        writeResultsToOutputFile(combinedResults, new File(_outputDir, TEST_SUMMARY_FILE_NAME).getAbsolutePath());
    }

    /**
     * generateOutputCsvNameFrom("/config/testConfigFile.js", "/output") returns /output/testConfigFile.csv
     */
    private String generateOutputCsvNameFrom(String testConfigFile)
    {
        final String filenameOnlyWithExtension = new File(testConfigFile).getName();
        final String cvsFile = filenameOnlyWithExtension.replaceFirst(".?\\w*$", ".csv");

        return new File(_outputDir, cvsFile).getAbsolutePath();
    }

    private void writeResultsToOutputFile(ResultsForAllTests resultsForAllTests, String outputFile)
    {
        FileWriter writer = null;
        try
        {
            final String outputCsv = _csvFormater.format(resultsForAllTests);
            writer = new FileWriter(outputFile);
            writer.write(outputCsv);
            LOGGER.info("Wrote " + resultsForAllTests.getTestResults().size() + " test result(s) to output file " + outputFile);
        }
        catch (IOException e)
        {
            throw new DistributedTestException("Unable to write output file " + outputFile, e);
        }
        finally
        {
            if (writer != null)
            {
                try
                {
                    writer.close();
                }
                catch (IOException e)
                {
                    LOGGER.error("Failed to close stream for file " + outputFile, e);
                }
            }
        }
    }

    void setCsvFormater(CSVFormatter csvFormater)
    {
        _csvFormater = csvFormater;
    }

    void setTestResultAggregator(TestResultAggregator testResultAggregator)
    {
        _testResultAggregator = testResultAggregator;
    }

}
