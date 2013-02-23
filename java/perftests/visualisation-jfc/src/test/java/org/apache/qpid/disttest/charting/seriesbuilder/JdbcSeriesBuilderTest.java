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
package org.apache.qpid.disttest.charting.seriesbuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collections;

import junit.framework.TestCase;

import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.JdbcSeriesBuilder;

public class JdbcSeriesBuilderTest extends TestCase
{
    private static final String TEST_SERIES_1_SELECT_STATEMENT = "SELECT A, B FROM test";
    private static final String TEST_SERIES_1_LEGEND = "SERIES_1_LEGEND";
    private static final String TEST_SERIES1_COLOUR_NAME = "blue";
    private static final Integer TEST_SERIES1_STROKE_WIDTH = 3;

    private DatasetHolder _seriesWalkerCallback = mock(DatasetHolder.class);

    private File _testTempDir;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        when(_seriesWalkerCallback.getNumberOfDimensions()).thenReturn(2);
        _testTempDir = createTestTemporaryDirectory();
        createTestCsvIn(_testTempDir);
    }

    public void testBuildOneSeries() throws Exception
    {
        SeriesDefinition seriesDefinition = createTestSeriesDefinition();

        JdbcSeriesBuilder seriesBuilder = new JdbcSeriesBuilder("org.relique.jdbc.csv.CsvDriver", null);

        seriesBuilder.setDatasetHolder(_seriesWalkerCallback);

        seriesBuilder.build(Collections.singletonList(seriesDefinition));

        verify(_seriesWalkerCallback).beginSeries(seriesDefinition);
        verify(_seriesWalkerCallback).addDataPointToSeries(seriesDefinition, new SeriesRow("elephant", "2"));
        verify(_seriesWalkerCallback).addDataPointToSeries(seriesDefinition, new SeriesRow("lion", "3"));
        verify(_seriesWalkerCallback).addDataPointToSeries(seriesDefinition, new SeriesRow("tiger", "4"));
        verify(_seriesWalkerCallback).endSeries(seriesDefinition);
    }

    private void createTestCsvIn(File testDir) throws Exception
    {
        File csv = new File(_testTempDir, "test.csv");

        PrintWriter csvWriter = new PrintWriter(new BufferedWriter(new FileWriter(csv)));
        csvWriter.println("A,B");
        csvWriter.println("elephant,2");
        csvWriter.println("lion,3");
        csvWriter.println("tiger,4");
        csvWriter.close();
    }

    private SeriesDefinition createTestSeriesDefinition()
    {
        SeriesDefinition definition = new SeriesDefinition(
                TEST_SERIES_1_SELECT_STATEMENT,
                TEST_SERIES_1_LEGEND,
                _testTempDir.getAbsolutePath(),
                TEST_SERIES1_COLOUR_NAME,
                TEST_SERIES1_STROKE_WIDTH);
        return definition;
    }

    private File createTestTemporaryDirectory() throws Exception
    {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "testdef" + System.nanoTime());
        tmpDir.mkdirs();
        tmpDir.deleteOnExit();
        return tmpDir;
    }

}
