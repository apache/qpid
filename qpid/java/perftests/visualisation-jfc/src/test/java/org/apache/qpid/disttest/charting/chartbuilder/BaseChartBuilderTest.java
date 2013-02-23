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
import static org.mockito.Mockito.*;

import java.util.List;

import junit.framework.TestCase;

import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.DatasetHolder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.general.Dataset;

public class BaseChartBuilderTest extends TestCase
{
    private static final String CHART_TITLE = "CHART_TITLE";
    private static final String CHART_SUB_TITLE = "CHART_SUB_TITLE";
    private static final String X_TITLE = "X_TITLE";
    private static final String Y_TITLE = "Y_TITLE";

    @SuppressWarnings("unchecked")
    private List<SeriesDefinition> _seriesDefinitions = mock(List.class);

    private ChartingDefinition _chartingDefinition = mock(ChartingDefinition.class);
    private SeriesStrokeAndPaintApplier _strokeAndPaintApplier = mock(SeriesStrokeAndPaintApplier.class);
    private DatasetHolder _datasetHolder = mock(DatasetHolder.class);
    private SeriesPainter _seriesPainter = mock(SeriesPainter.class);

    private SeriesBuilder _seriesBuilder = mock(SeriesBuilder.class);

    private JFreeChart _jFreeChart;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        Plot plot = new CategoryPlot();
        _jFreeChart = new JFreeChart(plot);

        when(_chartingDefinition.getChartTitle()).thenReturn(CHART_TITLE);
        when(_chartingDefinition.getChartSubtitle()).thenReturn(CHART_SUB_TITLE);
        when(_chartingDefinition.getXAxisTitle()).thenReturn(X_TITLE);
        when(_chartingDefinition.getYAxisTitle()).thenReturn(Y_TITLE);
        when(_chartingDefinition.getSeriesDefinitions()).thenReturn(_seriesDefinitions );
    }

    public void testBuildChart()
    {
        BaseChartBuilder chartBuilder = new ChartBuilder(_seriesBuilder, _strokeAndPaintApplier, _datasetHolder)
        {
            @Override
            protected JFreeChart createChartImpl(String title, String xAxisTitle, String yAxisTitle, Dataset dataset, PlotOrientation plotOrientation, boolean showLegend, boolean showToolTips, boolean showUrls)
            {
                assertEquals(CHART_TITLE, title);
                assertEquals(X_TITLE, xAxisTitle);
                assertEquals(Y_TITLE, yAxisTitle);

                return _jFreeChart;
            }
        };

        JFreeChart chart = chartBuilder.buildChart(_chartingDefinition);

        assertEquals(BaseChartBuilder.BLUE_GRADIENT, chart.getBackgroundPaint());
        assertEquals("The *second* subtitle of the generated chart should have the text from the chart definition",
                CHART_SUB_TITLE, ((TextTitle)chart.getSubtitle(1)).getText());
        verify(_seriesPainter).applySeriesAppearance(_jFreeChart, _seriesDefinitions, _strokeAndPaintApplier);
    }

    /**
     * Extends BaseChartBuilder to allow us to plug in in mock dependencies
     */
    private abstract class ChartBuilder extends BaseChartBuilder
    {
        private SeriesStrokeAndPaintApplier _seriesStrokeAndPaintApplier;
        private DatasetHolder _datasetHolder;

        private ChartBuilder(SeriesBuilder seriesBuilder, SeriesStrokeAndPaintApplier seriesStrokeAndPaintApplier, DatasetHolder datasetHolder)
        {
            super(seriesBuilder);
            _seriesStrokeAndPaintApplier = seriesStrokeAndPaintApplier;
            _datasetHolder = datasetHolder;
            setSeriesPainter(_seriesPainter);
        }

        @Override
        protected SeriesStrokeAndPaintApplier newStrokeAndPaintApplier()
        {
            return _seriesStrokeAndPaintApplier;
        }

        @Override
        protected DatasetHolder newDatasetHolder()
        {
            return _datasetHolder;
        }
    }
}
