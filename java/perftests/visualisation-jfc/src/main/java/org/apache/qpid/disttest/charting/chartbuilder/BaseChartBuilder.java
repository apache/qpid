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
import java.awt.GradientPaint;

import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.DatasetHolder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.ShortTextTitle;
import org.jfree.data.general.Dataset;

public abstract class BaseChartBuilder implements ChartBuilder
{
    static final GradientPaint BLUE_GRADIENT = new GradientPaint(0, 0, Color.white, 0, 1000, Color.blue);

    private SeriesPainter _seriesPainter = new SeriesPainter();

    private final SeriesBuilder _seriesBuilder;

    protected BaseChartBuilder(SeriesBuilder seriesBuilder)
    {
        _seriesBuilder = seriesBuilder;
    }

    @Override
    public JFreeChart buildChart(ChartingDefinition chartingDefinition)
    {
        _seriesBuilder.setDatasetHolder(newDatasetHolder());
        Dataset dataset = _seriesBuilder.build(chartingDefinition.getSeriesDefinitions());

        JFreeChart chart = createChart(chartingDefinition, dataset);
        return chart;
    }


    /**
     * return a holder of an empty dataset suitable for use with the chart type
     * returned by {@link #createChartImpl(String, String, String, Dataset, PlotOrientation, boolean, boolean, boolean)}.
     */
    protected abstract DatasetHolder newDatasetHolder();

    /**
     * Create a chart with the supplied parameters.
     *
     * For ease of implementation, the signature is intentionally similar
     * to {@link ChartFactory}'s factory methods.
     */
    protected abstract JFreeChart createChartImpl(
            String title, String xAxisTitle, String yAxisTitle,
            final Dataset dataset,
            PlotOrientation plotOrientation, boolean showLegend, boolean showToolTips, boolean showUrls);

    /**
     * Create a {@link SeriesStrokeAndPaintApplier} that will be used to format a chart
     */
    protected abstract SeriesStrokeAndPaintApplier newStrokeAndPaintApplier();


    private JFreeChart createChart(ChartingDefinition chartingDefinition, final Dataset dataset)
    {
        String title = chartingDefinition.getChartTitle();
        String xAxisTitle = chartingDefinition.getXAxisTitle();
        String yAxisTitle = chartingDefinition.getYAxisTitle();

        final JFreeChart chart = createChartImpl(
                title, xAxisTitle, yAxisTitle,
                dataset,
                PLOT_ORIENTATION, SHOW_LEGEND, SHOW_TOOL_TIPS, SHOW_URLS);

        addSubtitle(chart, chartingDefinition);
        chart.setBackgroundPaint(BLUE_GRADIENT);
        _seriesPainter.applySeriesAppearance(chart, chartingDefinition.getSeriesDefinitions(), newStrokeAndPaintApplier());

        return chart;
    }

    private void addSubtitle(JFreeChart chart, ChartingDefinition chartingDefinition)
    {
        if (chartingDefinition.getChartSubtitle() != null)
        {
            chart.addSubtitle(new ShortTextTitle(chartingDefinition.getChartSubtitle()));
        }
    }

    void setSeriesPainter(SeriesPainter seriesPainter)
    {
        _seriesPainter = seriesPainter;
    }

}
