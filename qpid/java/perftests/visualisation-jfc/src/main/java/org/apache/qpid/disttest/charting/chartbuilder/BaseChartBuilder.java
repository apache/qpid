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
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.ShortTextTitle;
import org.jfree.data.general.Dataset;

public abstract class BaseChartBuilder implements ChartBuilder
{
    private static final GradientPaint BLUE_GRADIENT = new GradientPaint(0, 0, Color.white, 0, 1000, Color.blue);

    public void addCommonChartAttributes(JFreeChart chart, ChartingDefinition chartingDefinition)
    {
        addSubtitle(chart, chartingDefinition);
        setBackgroundColour(chart);
    }

    private void addSubtitle(JFreeChart chart, ChartingDefinition chartingDefinition)
    {
        if (chartingDefinition.getChartSubtitle() != null)
        {
            chart.addSubtitle(new ShortTextTitle(chartingDefinition.getChartSubtitle()));
        }
    }

    private void setBackgroundColour(JFreeChart chart)
    {
        chart.setBackgroundPaint(BLUE_GRADIENT);
    }

    public abstract JFreeChart createChartImpl(String title, String xAxisTitle,
            String yAxisTitle, final Dataset dataset, PlotOrientation plotOrientation, boolean showLegend, boolean showToolTips,
            boolean showUrls);

}
