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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.qpid.disttest.charting.ChartingException;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChartWriter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChartWriter.class);
    private File _chartDirectory = new File(".");

    public void writeChartToFileSystem(JFreeChart chart, String chartStemName)
    {
        OutputStream pngOutputStream = null;
        try
        {

            File pngFile = new File(_chartDirectory, chartStemName + ".png");
            pngOutputStream = new BufferedOutputStream(new FileOutputStream(pngFile));
            ChartUtilities.writeChartAsPNG(pngOutputStream, chart, 600, 400, true, 0);
            pngOutputStream.close();

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

    public void setOutputDirectory(final File chartDirectory)
    {
        _chartDirectory = chartDirectory;
    }
}
