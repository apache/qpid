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

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.qpid.server.configuration.BrokerProperties;

public class LogViewerTest extends QpidRestTestCase
{
    private String _expectedLogFileName;
    private FileAppender _fileAppender;
    private String _appenderName;

    @Override
    public void setUp() throws Exception
    {
        _appenderName = getTestQueueName();
        _expectedLogFileName =  "qpid-" + _appenderName + ".log";

        _fileAppender = new FileAppender(new SimpleLayout(),
                System.getProperty(BrokerProperties.PROPERTY_QPID_WORK)  + File.separator +  _expectedLogFileName, false);
        _fileAppender.setName(_appenderName);
        Logger.getRootLogger().addAppender(_fileAppender);
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception
    {
        if (_fileAppender != null)
        {
            Logger.getRootLogger().removeAppender(_fileAppender);
        }
        super.tearDown();
    }

    public void testGetLogFiles() throws Exception
    {
        List<Map<String, Object>> logFiles = getRestTestHelper().getJsonAsList("/service/logfilenames");
        assertNotNull("Log files data cannot be null", logFiles);

        // 1 file appender is configured in QPID default log4j xml:
        assertTrue("Unexpected number of log files", logFiles.size() > 0);
        Map<String, Object> logFileDetails = null;
        for (Map<String, Object> appenderDetails: logFiles)
        {
            if (_appenderName.equals(appenderDetails.get("appenderName")))
            {
                logFileDetails = appenderDetails;
                break;
            }
        }

        assertEquals("Unexpected log file name", _expectedLogFileName, logFileDetails.get("name"));
        assertEquals("Unexpected log file mime type", "text/plain", logFileDetails.get("mimeType"));
        assertEquals("Unexpected log file appender",_appenderName, logFileDetails.get("appenderName"));
        assertTrue("Unexpected log file size", ((Number)logFileDetails.get("size")).longValue()>0);
        assertTrue("Unexpected log file modification time", ((Number)logFileDetails.get("lastModified")).longValue()>0);
    }

    public void testDownloadExistingLogFiles() throws Exception
    {
        byte[] bytes = getRestTestHelper().getBytes("/service/logfile?l=" + _appenderName + "%2F" + _expectedLogFileName);

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes));
        try
        {
            ZipEntry entry = zis.getNextEntry();
            assertEquals("Unexpected broker log file name", _appenderName + "/" + _expectedLogFileName, entry.getName());
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
        int responseCode = getRestTestHelper().submitRequest("/service/logfile?l=" + _appenderName + "%2F"
                + _expectedLogFileName + "_" + System.currentTimeMillis(), "GET");

        assertEquals("Unexpected response code", 404, responseCode);
    }

    public void testDownloadNonLogFiles() throws Exception
    {
        int responseCode = getRestTestHelper().submitRequest("/service/logfile?l=config.json", "GET");
        assertEquals("Unexpected response code", 400, responseCode);
    }
}
