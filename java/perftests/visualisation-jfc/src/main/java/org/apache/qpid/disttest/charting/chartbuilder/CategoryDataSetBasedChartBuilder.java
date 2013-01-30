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


import java.awt.Color;
import java.awt.Stroke;

import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilderCallback;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesRow;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.data.category.DefaultCategoryDataset;

public abstract class CategoryDataSetBasedChartBuilder extends BaseChartBuilder
{
    private final SeriesBuilder _seriesBuilder;

    public CategoryDataSetBasedChartBuilder(SeriesBuilder seriesBuilder)
    {
        _seriesBuilder = seriesBuilder;
    }

    @Override
    public JFreeChart buildChart(ChartingDefinition chartingDefinition)
    {
        String title = chartingDefinition.getChartTitle();
        String xAxisTitle = chartingDefinition.getXAxisTitle();
        String yAxisTitle = chartingDefinition.getYAxisTitle();

        final DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        _seriesBuilder.setSeriesBuilderCallback(new SeriesBuilderCallback()
        {
            @Override
            public void addDataPointToSeries(SeriesDefinition seriesDefinition, SeriesRow row)
            {
                String x = row.dimensionAsString(0);
                double y = row.dimensionAsDouble(1);
                dataset.addValue(y, seriesDefinition.getSeriesLegend(), x);
            }

            @Override
            public void beginSeries(SeriesDefinition seriesDefinition)
            {
                // unused
            }

            @Override
            public void endSeries(SeriesDefinition seriesDefinition)
            {
                // unused
            }

            @Override
            public int getNumberOfDimensions()
            {
                return 2;
            }

        });

        _seriesBuilder.build(chartingDefinition.getSeries());

        final JFreeChart chart = createChartImpl(title, xAxisTitle, yAxisTitle,
                dataset, PLOT_ORIENTATION, SHOW_LEGEND, SHOW_TOOL_TIPS, SHOW_URLS);

        chart.getCategoryPlot().getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);

        addCommonChartAttributes(chart, chartingDefinition);
        addSeriesAttributes(chart, chartingDefinition.getSeries(), new SeriesStrokeAndPaintApplier()
        {
            @Override
            public void setSeriesStroke(int seriesIndex, Stroke stroke, JFreeChart targetChart)
            {
                targetChart.getCategoryPlot().getRenderer().setSeriesStroke(seriesIndex, stroke);
            }

            @Override
            public void setSeriesPaint(int seriesIndex, Color colour, JFreeChart targetChart)
            {
                targetChart.getCategoryPlot().getRenderer().setSeriesPaint(seriesIndex, colour);
            }
        });

        return chart;
    }
}
