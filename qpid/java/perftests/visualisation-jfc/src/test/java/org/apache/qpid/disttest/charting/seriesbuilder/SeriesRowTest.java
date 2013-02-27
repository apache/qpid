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
 */
package org.apache.qpid.disttest.charting.seriesbuilder;

import org.apache.qpid.test.utils.QpidTestCase;

public class SeriesRowTest extends QpidTestCase
{
    private static final Integer[] PAIR = new Integer[] {10, 11};

    public void testValidSeriesRow()
    {
        assertEquals(11, SeriesRow.createValidSeriesRow(2, PAIR).dimension(1));
    }

    public void testValidSeriesRowThrowsExceptionIfArrayTooSmall()
    {
        try
        {
            SeriesRow.createValidSeriesRow(1, PAIR);
            fail("Expected exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }
    }

    public void testDimension()
    {
        SeriesRow seriesRow = new SeriesRow(10, 11);
        assertEquals(10, seriesRow.dimension(0));
        assertEquals(11, seriesRow.dimension(1));
    }

    public void testDimensionAsString()
    {
        SeriesRow seriesRow = new SeriesRow(10);
        assertEquals("10", seriesRow.dimensionAsString(0));
    }

    public void testDimensionAsDouble()
    {
        SeriesRow seriesRow = new SeriesRow(10.1);
        assertEquals(10.1, seriesRow.dimensionAsDouble(0), 0.0);
    }

}
