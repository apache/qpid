/*
 *
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
package org.apache.qpid.disttest.charting.writer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Scanner;

import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

public class ChartWriterTest extends QpidTestCase
{
    private JFreeChart _chart1;
    private JFreeChart _chart2;

    private File _chartDir;
    private ChartWriter _writer;

    @Override
    public void setUp()
    {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("a", 1);
        dataset.setValue("b", 2);

        _chart1 = ChartFactory.createPieChart("chart1", dataset, true, true, false);
        _chart2 = ChartFactory.createPieChart("chart2", dataset, true, true, false);

        _chartDir = TestFileUtils.createTestDirectory();

        _writer = new ChartWriter();
        _writer.setOutputDirectory(_chartDir);
    }

    public void testWriteChartToFileSystem()
    {
        ChartingDefinition chartDef1 = mock(ChartingDefinition.class);
        when(chartDef1.getChartStemName()).thenReturn("chart1");

        File chart1File = new File(_chartDir, "chart1.png");
        assertFalse("chart1 png should not exist yet", chart1File.exists());

        _writer.writeChartToFileSystem(_chart1, chartDef1);

        assertTrue("chart1 png does not exist", chart1File.exists());
    }

    public void testWriteHtmlSummaryToFileSystemOverwritingExistingFile() throws Exception
    {
        ChartingDefinition chartDef1 = mock(ChartingDefinition.class);
        when(chartDef1.getChartStemName()).thenReturn("chart1");
        when(chartDef1.getChartDescription()).thenReturn("chart description1");

        ChartingDefinition chartDef2 = mock(ChartingDefinition.class);
        when(chartDef2.getChartStemName()).thenReturn("chart2");

        File summaryFile = new File(_chartDir, ChartWriter.SUMMARY_FILE_NAME);

        writeDummyContentToSummaryFileToEnsureItGetsOverwritten(summaryFile);

        _writer.writeChartToFileSystem(_chart2, chartDef2);
        _writer.writeChartToFileSystem(_chart1, chartDef1);

        _writer.writeHtmlSummaryToFileSystem("Performance Charts");

        InputStream expectedSummaryFileInputStream = getClass().getResourceAsStream("expected-chart-summary.html");
        String expectedSummaryContent = new Scanner(expectedSummaryFileInputStream).useDelimiter("\\A").next();
        String actualSummaryContent = FileUtils.readFileAsString(summaryFile);

        assertEquals("HTML summary file has unexpected content", expectedSummaryContent, actualSummaryContent);
    }

    public void testWriteHtmlSummaryToFileSystemDoesNothingIfLessThanTwoCharts()
    {
        ChartingDefinition chartDef1 = mock(ChartingDefinition.class);
        when(chartDef1.getChartStemName()).thenReturn("chart1");
        when(chartDef1.getChartDescription()).thenReturn("chart description1");

        File summaryFile = new File(_chartDir, ChartWriter.SUMMARY_FILE_NAME);

        _writer.writeChartToFileSystem(_chart1, chartDef1);

        _writer.writeHtmlSummaryToFileSystem("Performance Charts");

        assertFalse("Only one chart generated so no summary file should have been written",
                summaryFile.exists());
    }

    private void writeDummyContentToSummaryFileToEnsureItGetsOverwritten(File summaryFile) throws Exception
    {
        FileWriter writer = null;
        try
        {
            writer = new FileWriter(summaryFile);
            writer.write("dummy content");
            writer.close();
        }
        finally
        {
            if (writer != null)
            {
                writer.close();
            }
        }
    }
}
