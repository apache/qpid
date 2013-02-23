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


import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.Dataset;

public class BarChart3DBuilder extends CategoryDataSetBasedChartBuilder
{

    public BarChart3DBuilder(SeriesBuilder seriesBuilder)
    {
        super(seriesBuilder);
    }

    @Override
    protected JFreeChart createCategoryChart(String title, String xAxisTitle,
            String yAxisTitle, final Dataset dataset, PlotOrientation plotOrientation,
            boolean showLegend, boolean showToolTips, boolean showUrls)
    {
        JFreeChart chart = ChartFactory.createBarChart3D(title,
                xAxisTitle,
                yAxisTitle,
                (CategoryDataset)dataset,
                plotOrientation,
                showLegend,
                showToolTips,
                showUrls);

        return chart;
    }


}
