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
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;

public interface ChartBuilder
{
    public static final boolean SHOW_URLS = false;
    public static final boolean SHOW_TOOL_TIPS = false;
    public static final boolean SHOW_LEGEND = true;
    public static final PlotOrientation PLOT_ORIENTATION = PlotOrientation.VERTICAL;

    public JFreeChart buildChart(ChartingDefinition chartingDefinition);
}
