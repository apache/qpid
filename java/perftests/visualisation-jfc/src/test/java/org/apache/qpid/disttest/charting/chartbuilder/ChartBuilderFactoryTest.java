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

import org.apache.qpid.disttest.charting.ChartType;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.apache.qpid.test.utils.QpidTestCase;

public class ChartBuilderFactoryTest extends QpidTestCase
{
    private SeriesBuilder _seriesBuilder = mock(SeriesBuilder.class);

    public void testLineChart()
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.LINE, _seriesBuilder);
        assertTrue(builder instanceof LineChartBuilder);
    }

    public void testLineChart3D()
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.LINE3D, _seriesBuilder);
        assertTrue(builder instanceof LineChart3DBuilder);
    }

    public void testBarChart()
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.BAR, _seriesBuilder);
        assertTrue(builder instanceof BarChartBuilder);
    }

    public void testBarChart3D()
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.BAR3D, _seriesBuilder);
        assertTrue(builder instanceof BarChart3DBuilder);
    }

    public void testXYLineChart()
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.XYLINE, _seriesBuilder);
        assertTrue(builder instanceof XYLineChartBuilder);
    }

    public void testTimeSeriesLineChart()
    {
        ChartBuilder builder = ChartBuilderFactory.createChartBuilder(ChartType.TIMELINE, _seriesBuilder);
        assertTrue(builder instanceof TimeSeriesLineChartBuilder);
    }
}
