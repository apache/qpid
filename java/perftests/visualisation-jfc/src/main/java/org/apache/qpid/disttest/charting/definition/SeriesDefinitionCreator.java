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

import static org.apache.commons.lang.text.StrSubstitutor.replaceSystemProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SeriesDefinitionCreator
{
    public static final String SERIES_STATEMENT_KEY_FORMAT = "series.%d.statement";
    public static final String SERIES_LEGEND_KEY_FORMAT = "series.%d.legend";
    public static final String SERIES_DIRECTORY_KEY_FORMAT = "series.%d.dir";
    public static final String SERIES_COLOUR_NAME_FORMAT = "series.%d.colourName";
    public static final String SERIES_STROKE_WIDTH_FORMAT = "series.%d.strokeWidth";

    public List<SeriesDefinition> createFromProperties(Properties properties)
    {
        final List<SeriesDefinition> seriesDefinitions = new ArrayList<SeriesDefinition>();

        int index = 1;
        boolean moreSeriesDefinitions = true;
        while(moreSeriesDefinitions)
        {
            String seriesStatement = replaceSystemProperties(properties.getProperty(String.format(SERIES_STATEMENT_KEY_FORMAT, index)));
            String seriesLegend = replaceSystemProperties(properties.getProperty(String.format(SERIES_LEGEND_KEY_FORMAT, index)));
            String seriesDir = replaceSystemProperties(properties.getProperty(String.format(SERIES_DIRECTORY_KEY_FORMAT, index)));
            String seriesColourName = replaceSystemProperties(properties.getProperty(String.format(SERIES_COLOUR_NAME_FORMAT, index)));
            Integer seriesStrokeWidth = properties.getProperty(String.format(SERIES_STROKE_WIDTH_FORMAT, index)) == null
                    ? null : Integer.parseInt(properties.getProperty(String.format(SERIES_STROKE_WIDTH_FORMAT, index)));

            if (seriesStatement != null)
            {
                final SeriesDefinition seriesDefinition = new SeriesDefinition(seriesStatement, seriesLegend, seriesDir, seriesColourName, seriesStrokeWidth);
                seriesDefinitions.add(seriesDefinition);
            }
            else
            {
                moreSeriesDefinitions = false;
            }
            index++;
        }
        return seriesDefinitions;
    }

}
