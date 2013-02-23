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
package org.apache.qpid.disttest.charting.definition;

import java.util.Collections;
import java.util.List;

import org.apache.qpid.disttest.charting.ChartType;

public class ChartingDefinition
{
    private final String _chartStemName;
    private final ChartType _chartType;
    private final String _chartTitle;
    private final String _chartSubtitle;
    private final String _chartDescription;
    private final String _xaxisTitle;
    private final String _yaxisTitle;
    private final List<SeriesDefinition> _seriesDefinitions;


    public ChartingDefinition(final String chartStemName,
                              final ChartType chartType,
                              final String chartTitle,
                              final String chartSubtitle,
                              final String chartDescription,
                              final String xaxisTitle, final String yaxisTitle, List<SeriesDefinition> seriesDefinitions)
    {
        _chartStemName = chartStemName;
        _chartType = chartType;
        _chartTitle = chartTitle;
        _chartSubtitle = chartSubtitle;
        _chartDescription = chartDescription;
        _xaxisTitle = xaxisTitle;
        _yaxisTitle = yaxisTitle;
        _seriesDefinitions = seriesDefinitions;
    }

    public String getChartStemName()
    {
        return _chartStemName;
    }

    public String getChartTitle()
    {
        return _chartTitle;
    }

    public String getChartSubtitle()
    {
        return _chartSubtitle;
    }

    public String getChartDescription()
    {
        return _chartDescription;
    }

    public String getXAxisTitle()
    {
        return _xaxisTitle;
    }


    public String getYAxisTitle()
    {
        return _yaxisTitle;
    }

    public ChartType getChartType()
    {
        return _chartType;
    }

    public List<SeriesDefinition> getSeriesDefinitions()
    {
        return Collections.unmodifiableList(_seriesDefinitions);
    }

}
