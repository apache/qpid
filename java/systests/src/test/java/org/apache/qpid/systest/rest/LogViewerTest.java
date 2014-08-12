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
package org.apache.qpid.systest.rest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.qpid.server.BrokerOptions;

public class LogViewerTest extends QpidRestTestCase
{
    public static final String DEFAULT_FILE_APPENDER_NAME = "FileAppender";
    private String _expectedLogFileName;

    public void setUp() throws Exception
    {
        setSystemProperty("logsuffix", "-" + getTestQueueName());
        _expectedLogFileName = System.getProperty("logprefix", "") + "qpid" + System.getProperty("logsuffix", "") + ".log";

        // use real broker log file
        File brokerLogFile = new File(System.getProperty(QPID_HOME), BrokerOptions.DEFAULT_LOG_CONFIG_FILE);
        setBrokerCommandLog4JFile(brokerLogFile);

        super.setUp();
    }

    public void testGetLogFiles() throws Exception
    {
        List<Map<String, Object>> logFiles = getRestTestHelper().getJsonAsList("/service/logfilenames");
        assertNotNull("Log files data cannot be null", logFiles);

        // 1 file appender is configured in QPID default log4j xml:
        assertTrue("Unexpected number of log files", logFiles.size() > 0);

        Map<String, Object> logFileDetails = logFiles.get(0);
        assertEquals("Unexpected log file name", _expectedLogFileName, logFileDetails.get("name"));
        assertEquals("Unexpected log file mime type", "text/plain", logFileDetails.get("mimeType"));
        assertEquals("Unexpected log file appender",DEFAULT_FILE_APPENDER_NAME, logFileDetails.get("appenderName"));
        assertTrue("Unexpected log file size", ((Number)logFileDetails.get("size")).longValue()>0);
        assertTrue("Unexpected log file modification time", ((Number)logFileDetails.get("lastModified")).longValue()>0);
    }

    public void testDownloadExistingLogFiles() throws Exception
    {
        byte[] bytes = getRestTestHelper().getBytes("/service/logfile?l=" + DEFAULT_FILE_APPENDER_NAME + "%2F" + _expectedLogFileName);

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
        try
        {
            ZipEntry entry = zis.getNextEntry();
            assertEquals("Unexpected broker log file name", DEFAULT_FILE_APPENDER_NAME + "/" + _expectedLogFileName, entry.getName());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = zis.read(buffer)) > 0)
            {
                baos.write(buffer, 0, len);
            }
            baos.close();
            assertTrue("Unexpected broker log file content", new String(baos.toByteArray()).contains("BRK-1004"));
            assertNull("Unexpepected log file entry", zis.getNextEntry());
        }
        finally
        {
            zis.close();
        }
    }

    public void testDownloadNonExistingLogFiles() throws Exception
    {
        int responseCode = getRestTestHelper().submitRequest("/service/logfile?l=" + DEFAULT_FILE_APPENDER_NAME + "%2F"
                + _expectedLogFileName + "_" + System.currentTimeMillis(), "GET");

        assertEquals("Unexpected response code", 404, responseCode);
    }

    public void testDownloadNonLogFiles() throws Exception
    {
        int responseCode = getRestTestHelper().submitRequest("/service/logfile?l=config.json", "GET");
        assertEquals("Unexpected response code", 400, responseCode);
    }
}
