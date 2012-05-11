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
package org.apache.qpid.disttest.charting.chartbuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collections;

import junit.framework.TestCase;

import org.apache.qpid.disttest.charting.definition.SeriesDefinition;

public class SeriesBuilderTest extends TestCase
{
    private static final String TEST_SERIES_1_SELECT_STATEMENT = "SELECT A, B FROM test";
    private static final String TEST_SERIES_1_LEGEND = "SERIES_1_LEGEND";

    private DataPointCallback _dataPointCallback = mock(DataPointCallback.class);
    private SeriesBuilder _seriesBuilder = new SeriesBuilder(_dataPointCallback);

    private File _testTempDir;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _testTempDir = createTestTemporaryDirectory();
    }

    public void testBuildOneSeries() throws Exception
    {
        createTestCsvIn(_testTempDir);
        SeriesDefinition seriesDefinition = createTestSeriesDefinition();

        _seriesBuilder.build(Collections.singletonList(seriesDefinition));

        verify(_dataPointCallback).addDataPointToSeries(seriesDefinition, (Object)"elephant", (Object)2.0);
        verify(_dataPointCallback).addDataPointToSeries(seriesDefinition, (Object)"lion", (Object)3.0);
        verify(_dataPointCallback).addDataPointToSeries(seriesDefinition, (Object)"tiger", (Object)4.0);
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
        SeriesDefinition definition = new SeriesDefinition(TEST_SERIES_1_SELECT_STATEMENT, TEST_SERIES_1_LEGEND, _testTempDir.getAbsolutePath());
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
