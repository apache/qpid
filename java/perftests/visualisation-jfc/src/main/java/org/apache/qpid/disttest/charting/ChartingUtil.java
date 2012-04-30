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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.disttest.ArgumentParser;
import org.apache.qpid.disttest.charting.chartbuilder.ChartBuilder;
import org.apache.qpid.disttest.charting.chartbuilder.ChartBuilderFactory;
import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.apache.qpid.disttest.charting.definition.ChartingDefinitionCreator;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChartingUtil
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChartingUtil.class);

    public static final String OUTPUT_DIR_PROP = "outputdir";
    public static final String OUTPUT_DIR_DEFAULT = ".";

    public static final String CHART_DEFINITIONS_PROP = "chart-defs";
    public static final String CHART_DEFINITIONS_DEFAULT = ".";

    private Map<String,String> _cliOptions = new HashMap<String, String>();
    {
        _cliOptions.put(OUTPUT_DIR_PROP, OUTPUT_DIR_DEFAULT);
        _cliOptions.put(CHART_DEFINITIONS_PROP, CHART_DEFINITIONS_DEFAULT);
    }

    public static void main(String[] args) throws Exception
    {
        try
        {
            LOGGER.debug("Starting charting");

            ChartingUtil chartingUtil = new ChartingUtil();
            chartingUtil.parseArgumentsIntoConfig(args);
            chartingUtil.produceAllCharts();
        }
        finally
        {
            LOGGER.debug("Charting complete");
        }
    }

    private void produceAllCharts()
    {
        final String chartingDefsDir = _cliOptions.get(CHART_DEFINITIONS_PROP);
        List<ChartingDefinition> definitions  = loadChartDefinitions(chartingDefsDir);

        LOGGER.debug("There are {} chart(s) to produce", definitions.size());

        for (ChartingDefinition chartingDefinition : definitions)
        {
            ChartBuilder chartBuilder = ChartBuilderFactory.createChartBuilder(chartingDefinition.getChartType());
            JFreeChart chart = chartBuilder.buildChart(chartingDefinition);
            writeChartToFileSystem(chart, chartingDefinition.getChartStemName());
        }
    }

    private void writeChartToFileSystem(JFreeChart chart, String chartStemName)
    {
        OutputStream pngOutputStream = null;
        try
        {

            File pngFile = new File(chartStemName + ".png");
            pngOutputStream = new BufferedOutputStream(new FileOutputStream(pngFile));
            ChartUtilities.writeChartAsPNG(pngOutputStream, chart, 600, 400, true, 0);
            pngOutputStream.close();

            LOGGER.debug("Written {} chart", pngFile);
        }
        catch (IOException e)
        {
            throw new ChartingException("Failed to create chart", e);
        }
        finally
        {
            if (pngOutputStream != null)
            {
                try
                {
                    pngOutputStream.close();
                }
                catch (IOException e)
                {
                    throw new ChartingException("Failed to create chart", e);
                }
            }
        }
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
