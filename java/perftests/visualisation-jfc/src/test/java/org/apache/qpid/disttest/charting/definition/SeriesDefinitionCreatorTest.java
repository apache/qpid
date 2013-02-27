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

import static org.apache.qpid.disttest.charting.definition.SeriesDefinitionCreator.SERIES_COLOUR_NAME_FORMAT;
import static org.apache.qpid.disttest.charting.definition.SeriesDefinitionCreator.SERIES_DIRECTORY_KEY_FORMAT;
import static org.apache.qpid.disttest.charting.definition.SeriesDefinitionCreator.SERIES_LEGEND_KEY_FORMAT;
import static org.apache.qpid.disttest.charting.definition.SeriesDefinitionCreator.SERIES_STATEMENT_KEY_FORMAT;
import static org.apache.qpid.disttest.charting.definition.SeriesDefinitionCreator.SERIES_STROKE_WIDTH_FORMAT;

import java.util.List;
import java.util.Properties;

import org.apache.qpid.test.utils.QpidTestCase;

public class SeriesDefinitionCreatorTest extends QpidTestCase
{
    private static final String TEST_SERIES_1_SELECT_STATEMENT = "SERIES_1_SELECT_STATEMENT";
    private static final String TEST_SERIES_1_LEGEND = "SERIES_1_LEGEND";
    private static final String TEST_SERIES_1_DIR = "SERIES_1_DIR";
    private static final String TEST_SERIES_1_COLOUR_NAME = "seriesColourName";
    private static final Integer TEST_SERIES_1_STROKE_WIDTH = 1;;

    private static final String TEST_SERIES_1_DIR_WITH_SYSPROP = "${java.io.tmpdir}/mydir";

    private static final String TEST_SERIES_2_SELECT_STATEMENT = "SERIES_2_SELECT_STATEMENT";
    private static final String TEST_SERIES_2_LEGEND = "SERIES_2_LEGEND";
    private static final String TEST_SERIES_2_DIR = "SERIES_2_DIR";

    private Properties _properties = new Properties();

    private SeriesDefinitionCreator _seriesDefinitionLoader = new SeriesDefinitionCreator();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
    }

    public void testOneSeriesDefinition() throws Exception
    {
        createTestProperties(1, TEST_SERIES_1_SELECT_STATEMENT, TEST_SERIES_1_LEGEND, TEST_SERIES_1_DIR, TEST_SERIES_1_COLOUR_NAME, TEST_SERIES_1_STROKE_WIDTH);

        List<SeriesDefinition> definitions = _seriesDefinitionLoader.createFromProperties(_properties);
        assertEquals(1, definitions.size());

        SeriesDefinition definition = definitions.get(0);
        assertEquals(TEST_SERIES_1_SELECT_STATEMENT, definition.getSeriesStatement());
        assertEquals(TEST_SERIES_1_LEGEND, definition.getSeriesLegend());
        assertEquals(TEST_SERIES_1_DIR, definition.getSeriesDirectory());
        assertEquals(TEST_SERIES_1_COLOUR_NAME, definition.getSeriesColourName());
        assertEquals(TEST_SERIES_1_STROKE_WIDTH, definition.getStrokeWidth());
    }

    public void testTwoSeriesDefinitions() throws Exception
    {
        createTestProperties(1, TEST_SERIES_1_SELECT_STATEMENT, TEST_SERIES_1_LEGEND, TEST_SERIES_1_DIR, TEST_SERIES_1_COLOUR_NAME, TEST_SERIES_1_STROKE_WIDTH);
        createTestProperties(2, TEST_SERIES_2_SELECT_STATEMENT, TEST_SERIES_2_LEGEND, TEST_SERIES_2_DIR, null, null);

        List<SeriesDefinition> definitions = _seriesDefinitionLoader.createFromProperties(_properties);
        assertEquals(2, definitions.size());

        SeriesDefinition seriesDefinition1 = definitions.get(0);
        assertEquals(TEST_SERIES_1_SELECT_STATEMENT, seriesDefinition1.getSeriesStatement());
        assertEquals(TEST_SERIES_1_LEGEND, seriesDefinition1.getSeriesLegend());
        assertEquals(TEST_SERIES_1_DIR, seriesDefinition1.getSeriesDirectory());

        SeriesDefinition seriesDefinition2 = definitions.get(1);
        assertEquals(TEST_SERIES_2_SELECT_STATEMENT, seriesDefinition2.getSeriesStatement());
        assertEquals(TEST_SERIES_2_LEGEND, seriesDefinition2.getSeriesLegend());
        assertEquals(TEST_SERIES_2_DIR, seriesDefinition2.getSeriesDirectory());
    }

    public void testNonSequentialSeriesDefinitionsIgnored() throws Exception
    {
        createTestProperties(1, TEST_SERIES_1_SELECT_STATEMENT, TEST_SERIES_1_LEGEND, TEST_SERIES_1_DIR, TEST_SERIES_1_COLOUR_NAME, TEST_SERIES_1_STROKE_WIDTH);
        createTestProperties(3, TEST_SERIES_2_SELECT_STATEMENT, TEST_SERIES_2_LEGEND, TEST_SERIES_2_DIR, null, null);

        List<SeriesDefinition> definitions = _seriesDefinitionLoader.createFromProperties(_properties);
        assertEquals(1, definitions.size());
    }

    public void testSeriesDirectorySubstitution() throws Exception
    {
        final String tmpDir = System.getProperty("java.io.tmpdir");
        createTestProperties(1, TEST_SERIES_1_SELECT_STATEMENT, TEST_SERIES_1_LEGEND, TEST_SERIES_1_DIR_WITH_SYSPROP, null, null);

        List<SeriesDefinition> definitions = _seriesDefinitionLoader.createFromProperties(_properties);
        assertEquals(1, definitions.size());

        SeriesDefinition seriesDefinition1 = definitions.get(0);
        assertTrue(seriesDefinition1.getSeriesDirectory().startsWith(tmpDir));
    }

    private void createTestProperties(int index, String selectStatement, String seriesLegend, String seriesDir, String seriesColourName, Integer seriesStrokeWidth) throws Exception
    {
        _properties.setProperty(String.format(SERIES_STATEMENT_KEY_FORMAT, index), selectStatement);
        _properties.setProperty(String.format(SERIES_LEGEND_KEY_FORMAT, index), seriesLegend);
        _properties.setProperty(String.format(SERIES_DIRECTORY_KEY_FORMAT, index), seriesDir);
        if (seriesColourName != null)
        {
            _properties.setProperty(String.format(SERIES_COLOUR_NAME_FORMAT, index), seriesColourName);
        }
        if (seriesStrokeWidth != null)
        {
            _properties.setProperty(String.format(SERIES_STROKE_WIDTH_FORMAT, index), seriesStrokeWidth.toString());
        }
    }

}
