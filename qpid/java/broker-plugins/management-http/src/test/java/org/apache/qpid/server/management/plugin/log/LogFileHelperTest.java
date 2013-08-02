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
package org.apache.qpid.server.management.plugin.log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Appender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.QpidCompositeRollingAppender;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.varia.ExternallyRolledFileAppender;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;
import org.apache.qpid.util.FileUtils;

public class LogFileHelperTest extends QpidTestCase
{
    private Map<String, List<File>> _appendersFiles;
    private File _compositeRollingAppenderBackupFolder;
    private List<Appender> _appenders;
    private LogFileHelper _helper;

    public void setUp() throws Exception
    {
        super.setUp();
        _appendersFiles = new HashMap<String, List<File>>();
        _compositeRollingAppenderBackupFolder = new File(TMP_FOLDER, "_compositeRollingAppenderBackupFolder");
        _compositeRollingAppenderBackupFolder.mkdirs();

        _appendersFiles.put(FileAppender.class.getSimpleName(),
                Collections.singletonList(TestFileUtils.createTempFile(this, ".log", "FileAppender")));
        _appendersFiles.put(DailyRollingFileAppender.class.getSimpleName(),
                Collections.singletonList(TestFileUtils.createTempFile(this, ".log", "DailyRollingFileAppender")));
        _appendersFiles.put(RollingFileAppender.class.getSimpleName(),
                Collections.singletonList(TestFileUtils.createTempFile(this, ".log", "RollingFileAppender")));
        _appendersFiles.put(ExternallyRolledFileAppender.class.getSimpleName(),
                Collections.singletonList(TestFileUtils.createTempFile(this, ".log", "ExternallyRolledFileAppender")));

        File file = TestFileUtils.createTempFile(this, ".log", "QpidCompositeRollingAppender");
        File backUpFile = File.createTempFile(file.getName() + ".", ".1." + LogFileHelper.GZIP_EXTENSION);
        _appendersFiles.put(QpidCompositeRollingAppender.class.getSimpleName(), Arrays.asList(file, backUpFile));

        FileAppender fileAppender = new FileAppender();
        DailyRollingFileAppender dailyRollingFileAppender = new DailyRollingFileAppender();
        RollingFileAppender rollingFileAppender = new RollingFileAppender();
        ExternallyRolledFileAppender externallyRolledFileAppender = new ExternallyRolledFileAppender();
        QpidCompositeRollingAppender qpidCompositeRollingAppender = new QpidCompositeRollingAppender();
        qpidCompositeRollingAppender.setbackupFilesToPath(_compositeRollingAppenderBackupFolder.getPath());

        _appenders = new ArrayList<Appender>();
        _appenders.add(fileAppender);
        _appenders.add(dailyRollingFileAppender);
        _appenders.add(rollingFileAppender);
        _appenders.add(externallyRolledFileAppender);
        _appenders.add(qpidCompositeRollingAppender);

        for (Appender appender : _appenders)
        {
            FileAppender fa = (FileAppender) appender;
            fa.setName(fa.getClass().getSimpleName());
            fa.setFile(_appendersFiles.get(fa.getClass().getSimpleName()).get(0).getPath());
        }

        _helper = new LogFileHelper(_appenders);
    }

    public void tearDown() throws Exception
    {
        try
        {
            for (List<File> files : _appendersFiles.values())
            {
                for (File file : files)
                {
                    try
                    {
                        FileUtils.delete(file, false);
                    }
                    catch (Exception e)
                    {
                        // ignore
                    }
                }
            }
            FileUtils.delete(_compositeRollingAppenderBackupFolder, true);
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testGetLogFileDetailsWithLocations() throws Exception
    {
        List<LogFileDetails> details = _helper.getLogFileDetails(true);

        assertLogFiles(details, true);
    }

    public void testGetLogFileDetailsWithoutLocations() throws Exception
    {
        List<LogFileDetails> details = _helper.getLogFileDetails(false);

        assertLogFiles(details, false);
    }

    public void testWriteLogFilesForAllLogs() throws Exception
    {
        List<LogFileDetails> details = _helper.getLogFileDetails(true);
        File f = TestFileUtils.createTempFile(this, ".zip");

        FileOutputStream os = new FileOutputStream(f);
        try
        {
            _helper.writeLogFiles(details, os);
        }
        finally
        {
            if (os != null)
            {
                os.close();
            }
        }

        assertWrittenFile(f, details);
    }

    public void testWriteLogFile() throws Exception
    {
        File file = _appendersFiles.get(FileAppender.class.getSimpleName()).get(0);

        File f = TestFileUtils.createTempFile(this, ".log");
        FileOutputStream os = new FileOutputStream(f);
        try
        {
            _helper.writeLogFile(file, os);
        }
        finally
        {
            if (os != null)
            {
                os.close();
            }
        }

        assertEquals("Unexpected log content", FileAppender.class.getSimpleName(), FileUtils.readFileAsString(f));
    }

    public void testFindLogFileDetails()
    {
        String[] logFileDisplayedPaths = new String[6];
        File[] files = new File[logFileDisplayedPaths.length];
        int i = 0;
        for (Map.Entry<String, List<File>> entry : _appendersFiles.entrySet())
        {
            String appenderName = entry.getKey();
            List<File> appenderFiles = entry.getValue();
            for (File logFile : appenderFiles)
            {
                logFileDisplayedPaths[i] = appenderName + "/" + logFile.getName();
                files[i++] = logFile;
            }
        }

        List<LogFileDetails> logFileDetails = _helper.findLogFileDetails(logFileDisplayedPaths);
        assertEquals("Unexpected details size", logFileDisplayedPaths.length, logFileDetails.size());

        boolean gzipFileFound = false;
        for (int j = 0; j < logFileDisplayedPaths.length; j++)
        {
            String displayedPath = logFileDisplayedPaths[j];
            String[] parts = displayedPath.split("/");
            LogFileDetails d = logFileDetails.get(j);
            assertEquals("Unexpected name", parts[1], d.getName());
            assertEquals("Unexpected appender", parts[0], d.getAppenderName());
            if (files[j].getName().endsWith(LogFileHelper.GZIP_EXTENSION))
            {
                assertEquals("Unexpected mime type for gz file", LogFileHelper.GZIP_MIME_TYPE, d.getMimeType());
                gzipFileFound = true;
            }
            else
            {
                assertEquals("Unexpected mime type", LogFileHelper.TEXT_MIME_TYPE, d.getMimeType());
            }
            assertEquals("Unexpecte file location", files[j], d.getLocation());
            assertEquals("Unexpecte file size", files[j].length(), d.getSize());
            assertEquals("Unexpecte file last modified date", files[j].lastModified(), d.getLastModified());
        }
        assertTrue("Gzip log file is not found", gzipFileFound);
    }

    public void testFindLogFileDetailsForNotExistingAppender()
    {
        String[] logFileDisplayedPaths = { "NotExistingAppender/qpid.log" };
        List<LogFileDetails> details = _helper.findLogFileDetails(logFileDisplayedPaths);
        assertTrue("No details should be created for non-existing appender", details.isEmpty());
    }

    public void testFindLogFileDetailsForNotExistingFile()
    {
        String[] logFileDisplayedPaths = { "FileAppender/qpid-non-existing.log" };
        List<LogFileDetails> details = _helper.findLogFileDetails(logFileDisplayedPaths);
        assertTrue("No details should be created for non-existing file", details.isEmpty());
    }

    public void testFindLogFileDetailsForIncorectlySpecifiedLogFilePath()
    {
        String[] logFileDisplayedPaths = { "FileAppender\\" + _appendersFiles.get("FileAppender").get(0).getName() };
        try
        {
            _helper.findLogFileDetails(logFileDisplayedPaths);
            fail("Exception is expected for incorectly set path to log file");
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
    }

    private void assertLogFiles(List<LogFileDetails> details, boolean includeLocation)
    {
        for (Map.Entry<String, List<File>> appenderData : _appendersFiles.entrySet())
        {
            String appenderName = (String) appenderData.getKey();
            List<File> files = appenderData.getValue();

            for (File logFile : files)
            {
                String logFileName = logFile.getName();
                LogFileDetails d = findLogFileDetails(logFileName, appenderName, details);
                assertNotNull("Log file " + logFileName + " is not found for appender " + appenderName, d);
                if (includeLocation)
                {
                    assertEquals("Log file " + logFileName + " is different in appender " + appenderName, d.getLocation(),
                            logFile);
                }
            }
        }
    }

    private LogFileDetails findLogFileDetails(String logFileName, String appenderName, List<LogFileDetails> logFileDetails)
    {
        LogFileDetails d = null;
        for (LogFileDetails lfd : logFileDetails)
        {
            if (lfd.getName().equals(logFileName) && lfd.getAppenderName().equals(appenderName))
            {
                d = lfd;
                break;
            }
        }
        return d;
    }

    private void assertWrittenFile(File f, List<LogFileDetails> details) throws FileNotFoundException, IOException
    {
        FileInputStream fis = new FileInputStream(f);
        try
        {
            ZipInputStream zis = new ZipInputStream(fis);
            ZipEntry ze = zis.getNextEntry();

            while (ze != null)
            {
                String entryName = ze.getName();
                String[] parts = entryName.split("/");

                String appenderName = parts[0];
                String logFileName = parts[1];

                LogFileDetails d = findLogFileDetails(logFileName, appenderName, details);

                assertNotNull("Unexpected entry " + entryName, d);
                details.remove(d);

                File logFile = d.getLocation();
                String logContent = FileUtils.readFileAsString(logFile);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0)
                {
                    baos.write(buffer, 0, len);
                }
                baos.close();

                assertEquals("Unexpected log file content", logContent, baos.toString());

                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

        }
        finally
        {
            if (fis != null)
            {
                fis.close();
            }
        }
        assertEquals("Not all log files have been output", 0, details.size());
    }

}
