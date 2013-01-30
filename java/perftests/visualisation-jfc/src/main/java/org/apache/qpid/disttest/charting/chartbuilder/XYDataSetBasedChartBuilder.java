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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilderCallback;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesRow;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.DefaultXYDataset;


public abstract class XYDataSetBasedChartBuilder extends BaseChartBuilder
{
    private final SeriesBuilder _seriesBuilder;

    public XYDataSetBasedChartBuilder(SeriesBuilder seriesBuilder)
    {
        this._seriesBuilder = seriesBuilder;
    }

    @Override
    public JFreeChart buildChart(ChartingDefinition chartingDefinition)
    {
        String title = chartingDefinition.getChartTitle();
        String xAxisTitle = chartingDefinition.getXAxisTitle();
        String yAxisTitle = chartingDefinition.getYAxisTitle();

        final DefaultXYDataset dataset = new DefaultXYDataset();
        _seriesBuilder.setSeriesBuilderCallback(new SeriesBuilderCallback()
        {
            private List<Double[]> _xyPairs = null;

            @Override
            public void beginSeries(SeriesDefinition seriesDefinition)
            {
                _xyPairs = new ArrayList<Double[]>();
            }

            @Override
            public void addDataPointToSeries(SeriesDefinition seriesDefinition, SeriesRow row)
            {
                double x = row.dimensionAsDouble(0);
                double y = row.dimensionAsDouble(1);
                _xyPairs.add(new Double[] {x, y});
            }


            @Override
            public void endSeries(SeriesDefinition seriesDefinition)
            {
                double[][] seriesData = listToSeriesDataArray();
                dataset.addSeries(seriesDefinition.getSeriesLegend(), seriesData);
            }

            @Override
            public int getNumberOfDimensions()
            {
                return 2;
            }

            private double[][] listToSeriesDataArray()
            {
                double[][] seriesData = new double[2][_xyPairs.size()];
                int i = 0;
                for (Iterator<Double[]> iterator = _xyPairs.iterator(); iterator.hasNext();)
                {
                    Double[] xyPair = iterator.next();
                    seriesData[0][i] = xyPair[0];
                    seriesData[1][i] = xyPair[1];
                    i++;
                 }
                return seriesData;
            }
        });

        _seriesBuilder.build(chartingDefinition.getSeries());

        final JFreeChart chart = createChartImpl(title, xAxisTitle, yAxisTitle,
                dataset, PLOT_ORIENTATION, SHOW_LEGEND, SHOW_TOOL_TIPS, SHOW_URLS);

        addCommonChartAttributes(chart, chartingDefinition);
        addSeriesAttributes(chart, chartingDefinition.getSeries(), new SeriesStrokeAndPaintApplier()
        {
            @Override
            public void setSeriesStroke(int seriesIndex, Stroke stroke, JFreeChart targetChart)
            {
                targetChart.getXYPlot().getRenderer().setSeriesStroke(seriesIndex, stroke);
            }

            @Override
            public void setSeriesPaint(int seriesIndex, Color colour, JFreeChart targetChart)
            {
                targetChart.getXYPlot().getRenderer().setSeriesPaint(seriesIndex, colour);
            }
        });

        return chart;
    }
}
