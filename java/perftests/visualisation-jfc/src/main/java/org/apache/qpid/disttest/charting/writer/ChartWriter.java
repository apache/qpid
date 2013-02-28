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
package org.apache.qpid.disttest.charting.writer;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.qpid.disttest.charting.ChartingException;
import org.apache.qpid.disttest.charting.definition.ChartingDefinition;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChartWriter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChartWriter.class);

    static final String SUMMARY_FILE_NAME = "chart-summary.html";

    private File _chartDirectory = new File(".");
    private SortedMap<File,ChartingDefinition> _chartFilesToChartDef = new TreeMap<File, ChartingDefinition>();

    public void writeChartToFileSystem(JFreeChart chart, ChartingDefinition chartDef)
    {
        OutputStream pngOutputStream = null;
        try
        {
            File pngFile = new File(_chartDirectory, chartDef.getChartStemName() + ".png");
            pngOutputStream = new BufferedOutputStream(new FileOutputStream(pngFile));
            ChartUtilities.writeChartAsPNG(pngOutputStream, chart, 600, 400, true, 0);
            pngOutputStream.close();

            _chartFilesToChartDef.put(pngFile, chartDef);

            LOGGER.info("Written {} chart", pngFile);
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

    public void writeHtmlSummaryToFileSystem(String summaryPageTitle)
    {
        if(_chartFilesToChartDef.size() < 2)
        {
            LOGGER.info("Only {} chart image(s) have been written so no HTML summary file will be produced", _chartFilesToChartDef.size());
            return;
        }

        String htmlHeader = String.format(
            "<html>\n" +
            "    <head>\n" +
            "        <title>%s</title>\n" +
            "        <style type='text/css'>figure { float: left; display: table; width: 87px;}</style>\n" +
            "    </head>\n" +
            "    <body>\n", summaryPageTitle);

        String htmlFooter =
            "    </body>\n" +
            "</html>";

        BufferedWriter writer = null;
        try
        {
            File summaryFile = new File(_chartDirectory, SUMMARY_FILE_NAME);
            LOGGER.debug("About to produce HTML summary file " + summaryFile.getAbsolutePath() + " from charts " + _chartFilesToChartDef);

            writer = new BufferedWriter(new FileWriter(summaryFile));
            writer.write(htmlHeader);

            writer.write("        <ul>\n");
            for (File chartFile : _chartFilesToChartDef.keySet())
            {
                writer.write("            <li><a href='#"+ chartFile.getName() +"'>" + chartFile.getName() + "</a></li>\n");
            }
            writer.write("        </ul>\n");

            for (File chartFile : _chartFilesToChartDef.keySet())
            {
                ChartingDefinition def = _chartFilesToChartDef.get(chartFile);
                writer.write("        <figure>\n");
                writer.write("          <a name='" + chartFile.getName() + "'/>\n");
                writer.write("          <img src='" + chartFile.getName() + "'/>\n");
                if (def.getChartDescription() != null)
                {
                    writer.write("          <figcaption>" + def.getChartDescription() + "</figcaption>\n");
                }
                writer.write("        </figure>\n");
            }
            writer.write(htmlFooter);
            writer.close();
        }
        catch (Exception e)
        {
            throw new ChartingException("Failed to create HTML summary file", e);
        }
        finally
        {
            if(writer != null)
            {
                try
                {
                    writer.close();
                }
                catch(IOException e)
                {
                    throw new ChartingException("Failed to create HTML summary file", e);
                }
            }
        }
    }

    public void setOutputDirectory(final File chartDirectory)
    {
        _chartDirectory = chartDirectory;
        LOGGER.info("Set chart directory: {}", chartDirectory);
    }
}
