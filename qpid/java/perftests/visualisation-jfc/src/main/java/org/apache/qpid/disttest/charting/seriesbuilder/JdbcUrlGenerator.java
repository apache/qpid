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

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.qpid.disttest.charting.definition.SeriesDefinition;

public class JdbcUrlGenerator
{
    private String _providedJdbdUrl;

    public static final String DEFAULT_JDBC_DRIVER_NAME = "org.relique.jdbc.csv.CsvDriver";

    /**
     * Used to create the JDBC URL if one has not been passed in.
     */
    private static final String CSV_JDBC_URL_BASE = "jdbc:relique:csv:";

    /**
     * @param providedJdbcUrl the JDBC URL. Provide null if the value should be
     * inferred.
     */
    public JdbcUrlGenerator(String providedJdbcUrl)
    {
        _providedJdbdUrl = providedJdbcUrl;
    }

    /**
     * Returns either the provided value ({@link #_providedJdbdUrl})
     * or a CSV JDBC URL pointing at {@link SeriesDefinition#getSeriesDirectory()} value.
     */
    public String getJdbcUrl(SeriesDefinition seriesDefinition)
    {
        String seriesDir = seriesDefinition.getSeriesDirectory();

        if(_providedJdbdUrl == null)
        {
            if(isBlank(seriesDir))
            {
                throw new IllegalArgumentException("Neither a series directory nor a JDBC url have been specified. Series definition: " + seriesDefinition);
            }
            return CSV_JDBC_URL_BASE + seriesDir;
        }
        else
        {
            if(isNotBlank(seriesDir))
            {
                throw new IllegalArgumentException("Both a series directory '" + seriesDir + "' and a JDBC url have been specified. Series definition: " + seriesDefinition);
            }
            return _providedJdbdUrl;
        }
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
            .append("providedJdbdUrl", _providedJdbdUrl)
            .toString();
    }
}