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
import org.apache.qpid.disttest.charting.seriesbuilder.JdbcCsvSeriesBuilder;
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
 *  <li>chart-defs=<i>directory contain chartdef file(s)</i></li>
 *  <li>output-dir=<i>directory in which to produce the PNGs</i></li>
 * </ol>
 */
public class ChartingUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChartingUtil.class);

    public static final String OUTPUT_DIR_PROP = "outputdir";
    public static final String OUTPUT_DIR_DEFAULT = ".";

    public static final String CHART_DEFINITIONS_PROP = "chart-defs";
    public static final String CHART_DEFINITIONS_DEFAULT = ".";

    public static final String SUMMARY_TITLE_PROP = "summary-title";
    public static final String SUMMARY_TITLE_DEFAULT = "Performance Charts";

    private Map<String,String> _cliOptions = new HashMap<String, String>();
    {
        _cliOptions.put(OUTPUT_DIR_PROP, OUTPUT_DIR_DEFAULT);
        _cliOptions.put(CHART_DEFINITIONS_PROP, CHART_DEFINITIONS_DEFAULT);
        _cliOptions.put(SUMMARY_TITLE_PROP, SUMMARY_TITLE_DEFAULT);
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
        final String chartingDefsDir = _cliOptions.get(CHART_DEFINITIONS_PROP);
        final File chartDirectory = new File(_cliOptions.get(OUTPUT_DIR_PROP));
        LOGGER.info("Chart chartdef directory/file: {} output directory : {}", chartingDefsDir, chartDirectory);

        List<ChartingDefinition> definitions  = loadChartDefinitions(chartingDefsDir);

        LOGGER.info("There are {} chart(s) to produce", definitions.size());

        final ChartWriter writer = new ChartWriter();
        writer.setOutputDirectory(chartDirectory);

        final SeriesBuilder seriesBuilder = new JdbcCsvSeriesBuilder();
        for (ChartingDefinition chartingDefinition : definitions)
        {
            ChartBuilder chartBuilder = ChartBuilderFactory.createChartBuilder(chartingDefinition.getChartType(), seriesBuilder);
            JFreeChart chart = chartBuilder.buildChart(chartingDefinition);
            writer.writeChartToFileSystem(chart, chartingDefinition);
        }

        final String summaryChartTitle = _cliOptions.get(SUMMARY_TITLE_PROP);
        writer.writeHtmlSummaryToFileSystem(summaryChartTitle);
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
