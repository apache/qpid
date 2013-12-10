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


import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.DatasetHolder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesRow;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.Dataset;

public abstract class CategoryDataSetBasedChartBuilder extends BaseChartBuilder
{
    public CategoryDataSetBasedChartBuilder(SeriesBuilder seriesBuilder)
    {
        super(seriesBuilder);
    }

    @Override
    protected DatasetHolder newDatasetHolder()
    {
        return new DatasetHolder()
        {
            final private DefaultCategoryDataset _dataset = new DefaultCategoryDataset();

            @Override
            public void addDataPointToSeries(SeriesDefinition seriesDefinition, SeriesRow row)
            {
                String x = row.dimensionAsString(0);
                double y = row.dimensionAsDouble(1);
                _dataset.addValue(y, seriesDefinition.getSeriesLegend(), x);
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

            @Override
            public Dataset getPopulatedDataset()
            {
                return _dataset;
            }
        };
    }

    @Override
    protected SeriesStrokeAndPaintApplier newStrokeAndPaintApplier()
    {
        return new CategoryStrokeAndPaintApplier();
    }

    @Override
    protected final JFreeChart createChartImpl(String title, String xAxisTitle, String yAxisTitle, Dataset dataset, PlotOrientation plotOrientation, boolean showLegend, boolean showToolTips, boolean showUrls)
    {
        JFreeChart chart = createCategoryChart(title, xAxisTitle, yAxisTitle, dataset, plotOrientation, showLegend, showToolTips, showUrls);
        chart.getCategoryPlot().getDomainAxis().setCategoryLabelPositions(CategoryLabelPositions.UP_45);
        return chart;
    }

    protected abstract JFreeChart createCategoryChart(String title, String xAxisTitle, String yAxisTitle, Dataset dataset, PlotOrientation plotOrientation, boolean showLegend, boolean showToolTips, boolean showUrls);
}
