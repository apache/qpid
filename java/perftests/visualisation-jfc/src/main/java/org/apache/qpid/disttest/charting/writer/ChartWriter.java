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
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.qpid.disttest.charting.ChartingException;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChartWriter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChartWriter.class);

    static final String SUMMARY_FILE_NAME = "chart-summary.html";

    private File _chartDirectory = new File(".");
    private SortedSet<File> _chartFiles = new TreeSet<File>();

    public void writeChartToFileSystem(JFreeChart chart, String chartStemName)
    {
        OutputStream pngOutputStream = null;
        try
        {
            File pngFile = new File(_chartDirectory, chartStemName + ".png");
            pngOutputStream = new BufferedOutputStream(new FileOutputStream(pngFile));
            ChartUtilities.writeChartAsPNG(pngOutputStream, chart, 600, 400, true, 0);
            pngOutputStream.close();

            _chartFiles.add(pngFile);

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

    public void writeHtmlSummaryToFileSystem()
    {
        if(_chartFiles.size() < 2)
        {
            LOGGER.info("Only " + _chartFiles.size() + " chart image(s) have been written so no HTML summary file will be produced");
            return;
        }

        String htmlHeader =
            "<html>\n" +
            "    <head>\n" +
            "        <title>Performance Charts</title>\n" +
            "    </head>\n" +
            "    <body>\n";

        String htmlFooter =
            "    </body>\n" +
            "</html>";

        BufferedWriter writer = null;
        try
        {
            File summaryFile = new File(_chartDirectory, SUMMARY_FILE_NAME);
            LOGGER.debug("About to produce HTML summary file " + summaryFile.getAbsolutePath() + " from charts " + _chartFiles);

            writer = new BufferedWriter(new FileWriter(summaryFile));
            writer.write(htmlHeader);

            writer.write("        <ul>\n");
            for (File chartFile : _chartFiles)
            {
                writer.write("            <li><a href='#"+ chartFile.getName() +"'>" + chartFile.getName() + "</a></li>\n");
            }
            writer.write("        </ul>\n");

            for (File chartFile : _chartFiles)
            {
                writer.write("        <a name='" + chartFile.getName() + "'/>\n");
                writer.write("        <img src='" + chartFile.getName() + "'/>\n");
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
    }
}
