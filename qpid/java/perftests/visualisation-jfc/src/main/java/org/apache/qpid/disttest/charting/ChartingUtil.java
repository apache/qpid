/*
 *
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
package org.apache.qpid.disttest.charting;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.disttest.ArgumentParser;
import org.apache.qpid.disttest.charting.chartbuilder.ChartBuilder;
import org.apache.qpid.disttest.charting.chartbuilder.ChartBuilderFactory;
import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.ChartingDefinitionCreator;
import org.apache.qpid.disttest.charting.seriesbuilder.JdbcSeriesBuilder;
import org.apache.qpid.disttest.charting.seriesbuilder.JdbcUrlGenerator;
import org.apache.qpid.disttest.charting.seriesbuilder.SeriesBuilder;
import org.apache.qpid.disttest.charting.writer.ChartWriter;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws charts for data drawn from CSV datasources using rules described in
 * charting definitions (.chartdef) files.
 * <p>
 * The following arguments are understood:
 * </p>
 * <ol>
 *  <li>{@link #OUTPUT_DIR_PROP}</li>
 *  <li>{@link #CHART_DEFINITIONS_PROP}</li>
 *  <li>{@link #SUMMARY_TITLE_PROP}</li>
 *  <li>{@link #JDBC_DRIVER_NAME_PROP}</li>
 *  <li>{@link #JDBC_URL_PROP}</li>
 * </ol>
 * Default values are indicated by the similarly named constants in this class.
 */
public class ChartingUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChartingUtil.class);

    /** directory in which to produce the PNGs */
    public static final String OUTPUT_DIR_PROP = "outputdir";
    public static final String OUTPUT_DIR_DEFAULT = ".";

    /** the path to the directory containing the chart definition files */
    public static final String CHART_DEFINITIONS_PROP = "chart-defs";
    public static final String CHART_DEFINITIONS_DEFAULT = ".";

    public static final String SUMMARY_TITLE_PROP = "summary-title";
    public static final String SUMMARY_TITLE_DEFAULT = "Performance Charts";

    /** the class name of the JDBC driver to use for reading the chart data */
    public static final String JDBC_DRIVER_NAME_PROP = "jdbcDriverClass";
    public static final String JDBC_DRIVER_NAME_DEFAULT = JdbcUrlGenerator.DEFAULT_JDBC_DRIVER_NAME;

    /** the JDBC URL of the data to be charted */
    public static final String JDBC_URL_PROP = "jdbcUrl";
    public static final String JDBC_URL_DEFAULT = null;


    private Map<String,String> _cliOptions = new HashMap<String, String>();

    {
        _cliOptions.put(OUTPUT_DIR_PROP, OUTPUT_DIR_DEFAULT);
        _cliOptions.put(CHART_DEFINITIONS_PROP, CHART_DEFINITIONS_DEFAULT);
        _cliOptions.put(SUMMARY_TITLE_PROP, SUMMARY_TITLE_DEFAULT);
        _cliOptions.put(JDBC_DRIVER_NAME_PROP, JDBC_DRIVER_NAME_DEFAULT);
        _cliOptions.put(JDBC_URL_PROP, JDBC_URL_DEFAULT);
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            LOGGER.info("Starting charting");

            ChartingUtil chartingUtil = new ChartingUtil();
            chartingUtil.parseArgumentsIntoConfig(args);
            chartingUtil.produceAllCharts();
        }
        finally
        {
            LOGGER.info("Charting complete");
        }
    }

    private void produceAllCharts()
    {

        final ChartWriter writer = new ChartWriter();
        writer.setOutputDirectory(new File(_cliOptions.get(OUTPUT_DIR_PROP)));

        SeriesBuilder seriesBuilder = new JdbcSeriesBuilder(
                _cliOptions.get(JDBC_DRIVER_NAME_PROP),
                _cliOptions.get(JDBC_URL_PROP));

        for (ChartingDefinition chartingDefinition : loadChartDefinitions())
        {
            try
            {
                ChartBuilder chartBuilder = ChartBuilderFactory.createChartBuilder(
                        chartingDefinition.getChartType(),
                        seriesBuilder);

                JFreeChart chart = chartBuilder.buildChart(chartingDefinition);
                writer.writeChartToFileSystem(chart, chartingDefinition);
            }
            catch (Exception e)
            {
                LOGGER.error("Couldn't produce chart " + chartingDefinition, e);
            }
        }

        final String summaryChartTitle = _cliOptions.get(SUMMARY_TITLE_PROP);
        writer.writeHtmlSummaryToFileSystem(summaryChartTitle);
    }

    private List<ChartingDefinition> loadChartDefinitions()
    {
        final String chartingDefsDir = _cliOptions.get(CHART_DEFINITIONS_PROP);

        LOGGER.info("Chart chartdef directory/file: {}", chartingDefsDir);

        List<ChartingDefinition> definitions  = loadChartDefinitions(chartingDefsDir);

        LOGGER.info("There are {} chart(s) to produce", definitions.size());
        return definitions;
    }

    private List<ChartingDefinition> loadChartDefinitions(String chartingDefsDir)
    {
        ChartingDefinitionCreator chartingDefinitionLoader = new ChartingDefinitionCreator();
        return chartingDefinitionLoader.createFromFileOrDirectory(chartingDefsDir);
    }

    private void parseArgumentsIntoConfig(String[] args)
    {
        ArgumentParser argumentParser = new ArgumentParser();
        argumentParser.parseArgumentsIntoConfig(_cliOptions, args);
    }
}
