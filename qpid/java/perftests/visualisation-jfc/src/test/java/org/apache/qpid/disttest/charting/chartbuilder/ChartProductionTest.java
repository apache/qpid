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

import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.qpid.disttest.charting.ChartType;
import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilderCallback;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.apache.qpid.disttest.charting.writer.ChartWriter;
import org.apache.qpid.test.utils.TestFileUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.ShortTextTitle;

import junit.framework.TestCase;

/**
 * Tests the production of the different chart types.  To manually
 * verify the generated output, set the system property {@link #RETAIN_TEST_CHARTS}
 * to prevent the automatic deletion of the test chart directory.
 *
 */
public class ChartProductionTest extends TestCase
{
    private static final String TEST_CHARTTITLE = "TEST_CHARTTITLE";
    private static final String TEST_CHARTSUBTITLE = "TEST_CHARTSUBTITLE";
    private static final String TEST_XAXIS = "TEST_XAXIS";
    private static final String TEST_YAXIS = "TEST_YAXIS";

    private static final String TEST_SERIESLEGEND = "TEST_SERIESLEGEND";

    private static final String RETAIN_TEST_CHARTS = "retainTestCharts";

    private SeriesDefinition _seriesDefinition = mock(SeriesDefinition.class);
    private ChartingDefinition _chartingDefinition = mock(ChartingDefinition.class);
    private ChartWriter _writer = new ChartWriter();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        when(_seriesDefinition.getSeriesLegend()).thenReturn(TEST_SERIESLEGEND);

        when(_chartingDefinition.getChartTitle()).thenReturn(TEST_CHARTTITLE);
        when(_chartingDefinition.getChartSubtitle()).thenReturn(TEST_CHARTSUBTITLE);
        when(_chartingDefinition.getXAxisTitle()).thenReturn(TEST_XAXIS);
        when(_chartingDefinition.getYAxisTitle()).thenReturn(TEST_YAXIS);
        when(_chartingDefinition.getSeries()).thenReturn(Collections.singletonList(_seriesDefinition));

        File chartDir = TestFileUtils.createTestDirectory("charts", false);
        if (!System.getProperties().containsKey(RETAIN_TEST_CHARTS))
        {
            chartDir.deleteOnExit();
        }
        else
        {
            System.out.println("Charting directory for manual observation " + chartDir);
        }

        _writer.setOutputDirectory(chartDir);
    }

    public void testBarChart() throws Exception
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.BAR, new SampleSeriesBuilder());
        assertChartTitlesAndWriteToFile(builder);
    }

    public void testBar3DChart() throws Exception
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.BAR3D, new SampleSeriesBuilder());
        assertChartTitlesAndWriteToFile(builder);
    }

    public void testLineChart() throws Exception
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.LINE, new SampleSeriesBuilder());
        assertChartTitlesAndWriteToFile(builder);
    }

    public void testLine3DChart() throws Exception
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.LINE3D, new SampleSeriesBuilder());
        assertChartTitlesAndWriteToFile(builder);
    }
    public void testXYLineChart() throws Exception
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.XYLINE, new SampleSeriesBuilder());
        assertChartTitlesAndWriteToFile(builder);
    }

    private void assertChartTitlesAndWriteToFile(ChartBuilder builder)
    {
        JFreeChart chart = builder.buildChart(_chartingDefinition);
        assertEquals(TEST_CHARTTITLE, chart.getTitle().getText());
        assertEquals(TEST_CHARTSUBTITLE, ((ShortTextTitle)chart.getSubtitle(1)).getText());
        assertEquals(TEST_SERIESLEGEND, chart.getPlot().getLegendItems().get(0).getLabel());

        if (chart.getPlot() instanceof XYPlot)
        {
            assertEquals(1, chart.getXYPlot().getDatasetCount());
        }
        else
        {
            assertEquals(1, chart.getCategoryPlot().getDatasetCount());
        }

        _writer.writeChartToFileSystem(chart, getName());
    }

    private class SampleSeriesBuilder implements SeriesBuilder
    {
        private SeriesBuilderCallback _dataPointCallback;

        @Override
        public void build(List<SeriesDefinition> seriesDefinitions)
        {
            for (Iterator<SeriesDefinition> iterator = seriesDefinitions.iterator(); iterator
                    .hasNext();)
            {
                SeriesDefinition seriesDefinition = iterator.next();
                _dataPointCallback.beginSeries(seriesDefinition);
                _dataPointCallback.addDataPointToSeries(seriesDefinition, new Object[]{1d, 1d});
                _dataPointCallback.addDataPointToSeries(seriesDefinition, new Object[]{2d, 2d});
                _dataPointCallback.addDataPointToSeries(seriesDefinition, new Object[]{4d, 4d});
                _dataPointCallback.addDataPointToSeries(seriesDefinition, new Object[]{5d, 5d});
                _dataPointCallback.addDataPointToSeries(seriesDefinition, new Object[]{6d, 3d});
                _dataPointCallback.endSeries(seriesDefinition);
            }
        }

        @Override
        public void setSeriesBuilderCallback(SeriesBuilderCallback dataPointCallback)
        {
            _dataPointCallback = dataPointCallback;
        }
    }
}
