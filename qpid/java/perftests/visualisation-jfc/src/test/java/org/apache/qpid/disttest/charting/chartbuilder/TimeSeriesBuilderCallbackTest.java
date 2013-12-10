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
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.apache.qpid.disttest.charting.definition.SeriesDefinition;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesRow;
import org.apache.qpid.test.utils.QpidTestCase;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.TimeSeriesDataItem;

public class TimeSeriesBuilderCallbackTest extends QpidTestCase
{
    private static final String SERIES_LEGEND = "mySeriesLegend";

    private static final int NUMBER_OF_DATA_POINTS = 3;

    private Date[] _dates;
    private double[] _values;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+00:00"));

        calendar.set(2013, Calendar.JANUARY, 1);
        Date jan1 = calendar.getTime();

        calendar.set(2013, Calendar.JANUARY, 2);
        Date jan2 = calendar.getTime();

        calendar.set(2013, Calendar.JANUARY, 3);
        Date jan3 = calendar.getTime();

        _dates =  new Date[] {jan1, jan2, jan3};
        _values = new double[] {2.0, 4.0, 8.0};
    }


    public void testAddPointToSeries()
    {
        TimeSeriesHolder timeSeriesHolder = new TimeSeriesHolder();

        SeriesDefinition seriesDefinition = mock(SeriesDefinition.class);
        when(seriesDefinition.getSeriesLegend()).thenReturn(SERIES_LEGEND);

        timeSeriesHolder.beginSeries(seriesDefinition);

        timeSeriesHolder.addDataPointToSeries(seriesDefinition, new SeriesRow(_dates[0], _values[0]));
        timeSeriesHolder.addDataPointToSeries(seriesDefinition, new SeriesRow(_dates[1], _values[1]));
        timeSeriesHolder.addDataPointToSeries(seriesDefinition, new SeriesRow(_dates[2], _values[2]));

        timeSeriesHolder.endSeries(seriesDefinition);

        TimeSeriesCollection timeSeriesCollection = (TimeSeriesCollection) timeSeriesHolder.getPopulatedDataset();

        TimeSeries actualTimeSeries = timeSeriesCollection.getSeries(SERIES_LEGEND);
        for(int i = 0; i < NUMBER_OF_DATA_POINTS; i++)
        {
            TimeSeriesDataItem dataItem0 = actualTimeSeries.getDataItem(i);
            assertEquals(_dates[i].getTime(), dataItem0.getPeriod().getMiddleMillisecond());
            assertEquals(_values[i], dataItem0.getValue());
        }
    }

}
