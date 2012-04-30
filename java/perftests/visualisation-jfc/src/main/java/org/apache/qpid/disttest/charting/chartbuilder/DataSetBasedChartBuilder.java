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

import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

public abstract class DataSetBasedChartBuilder implements ChartBuilder
{
    private static final CategoryLabelPositions LABEL_POSITION = CategoryLabelPositions.UP_45;
    private static final PlotOrientation PLOT_ORIENTATION = PlotOrientation.VERTICAL;

    @Override
    public JFreeChart buildChart(ChartingDefinition chartingDefinition)
    {
        String title = chartingDefinition.getChartTitle();
        String xAxisTitle = chartingDefinition.getXAxisTitle();
        String yAxisTitle = chartingDefinition.getYAxisTitle();

        final DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        SeriesBuilder seriesBuilder = new SeriesBuilder(new DataPointCallback()
        {
            @Override
            public void addDataPointToSeries(SeriesDefinition seriesDefinition,
                    Object xValue, Object yValue)
            {
                String x = (String) xValue;
                double y = (Double) yValue;
                dataset.addValue( y, seriesDefinition.getSeriesLegend(), x);
            }
        });

        seriesBuilder.build(chartingDefinition.getSeries());

        JFreeChart chart = createChartImpl(title, xAxisTitle, yAxisTitle,
                dataset, PLOT_ORIENTATION, true, false, false);

        chart.getCategoryPlot().getDomainAxis().setCategoryLabelPositions(LABEL_POSITION);

        return chart;
    }

    public abstract JFreeChart createChartImpl(String title, String xAxisTitle,
            String yAxisTitle, final DefaultCategoryDataset dataset, PlotOrientation plotOrientation,
            boolean showLegend, boolean showToolTips, boolean showUrls);
}
