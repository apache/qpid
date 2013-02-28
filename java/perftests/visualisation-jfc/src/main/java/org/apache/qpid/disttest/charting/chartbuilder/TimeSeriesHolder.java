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

import java.util.Date;

import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.DatasetHolder;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesRow;
import org.jfree.data.general.Dataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

class TimeSeriesHolder implements DatasetHolder
{
    private final TimeSeriesCollection _timeSeriesCollection = new TimeSeriesCollection();
    private TimeSeries _timeSeries;

    @Override
    public void beginSeries(SeriesDefinition seriesDefinition)
    {
        _timeSeries = new TimeSeries(seriesDefinition.getSeriesLegend());
    }

    @Override
    public void addDataPointToSeries(SeriesDefinition seriesDefinition, SeriesRow row)
    {
        Date x = row.dimensionAsDate(0);
        double y = row.dimensionAsDouble(1);
        RegularTimePeriod jfreeChartDate = new Millisecond(x);
        _timeSeries.add(jfreeChartDate, y);
    }

    @Override
    public void endSeries(SeriesDefinition seriesDefinition)
    {
        _timeSeriesCollection.addSeries(_timeSeries);
    }

    @Override
    public int getNumberOfDimensions()
    {
        return 2;
    }

    @Override
    public Dataset getPopulatedDataset()
    {
        return _timeSeriesCollection;
    }
}