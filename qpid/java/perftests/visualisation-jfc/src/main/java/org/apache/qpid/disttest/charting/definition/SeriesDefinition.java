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

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

public class SeriesDefinition
{
    private final String _seriesStatement;
    private final String _seriesLegend;
    private final String _seriesDirectory;
    private final String _seriesColourName;
    private final Integer _seriesStrokeWidth;

    public SeriesDefinition(String seriesStatement, String seriesLegend, String seriesDirectory, String seriesColourName, Integer seriesStrokeWidth)
    {
        _seriesStatement = seriesStatement;
        _seriesLegend = seriesLegend;
        _seriesDirectory = seriesDirectory;
        _seriesColourName = seriesColourName;
        _seriesStrokeWidth = seriesStrokeWidth;
    }

    public String getSeriesStatement()
    {
        return _seriesStatement;
    }

    public String getSeriesLegend()
    {
        return _seriesLegend;
    }

    public String getSeriesDirectory()
    {
        return _seriesDirectory;
    }

    public String getSeriesColourName()
    {
        return _seriesColourName;
    }

    public Integer getStrokeWidth()
    {
        return _seriesStrokeWidth;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("seriesLegend", _seriesLegend)
            .append("seriesStatement", _seriesStatement)
            .append("seriesDirectory", _seriesDirectory).toString();
    }
}
