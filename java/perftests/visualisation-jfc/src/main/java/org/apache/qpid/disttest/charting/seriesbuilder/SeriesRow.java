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

import java.util.Arrays;
import java.util.Date;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

/**
 * A data point in a chart. Thinly wraps an array to provide a convenient place to validate the number of dimensions,
 * and to access dimensions in a typesafe manner.
 */
public class SeriesRow
{
    private final Object[] _dimensions;

    public static SeriesRow createValidSeriesRow(int expectedNumberOfDimensions, Object[] dimensions)
    {
        int actualNumberOfDimensions = dimensions.length;
        if(expectedNumberOfDimensions != actualNumberOfDimensions)
        {
            throw new IllegalArgumentException("Expected " + expectedNumberOfDimensions
                    + " dimensions but found " + actualNumberOfDimensions
                    + " in: " + Arrays.asList(dimensions));
        }
        return new SeriesRow(dimensions);
    }

    public SeriesRow(Object... dimensions)
    {
        _dimensions = dimensions;
    }

    public Object dimension(int dimension)
    {
        return _dimensions[dimension];
    }

    public String dimensionAsString(int dimension)
    {
        return String.valueOf(dimension(dimension));
    }

    public double dimensionAsDouble(int dimension)
    {
        return Double.parseDouble(dimensionAsString(dimension));
    }

    public Date dimensionAsDate(int dimension)
    {
        return (Date) dimension(dimension);
    }

    @Override
    public int hashCode()
    {
        return new HashCodeBuilder().append(_dimensions).toHashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
        {
            return false;
        }
        if (obj == this)
        {
            return true;
        }
        if (obj.getClass() != getClass())
        {
            return false;
        }
        SeriesRow rhs = (SeriesRow) obj;
        return new EqualsBuilder().append(_dimensions, rhs._dimensions).isEquals();
    }


}