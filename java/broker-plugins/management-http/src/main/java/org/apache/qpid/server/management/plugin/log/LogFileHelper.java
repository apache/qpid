/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.qpid.server.management.plugin.log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.QpidCompositeRollingAppender;

public class LogFileHelper
{
    public static final String GZIP_MIME_TYPE = "application/x-gzip";
    public static final String TEXT_MIME_TYPE = "text/plain";
    public static final String ZIP_MIME_TYPE = "application/zip";
    public static final String GZIP_EXTENSION = ".gz";
    private static final int BUFFER_LENGTH = 1024 * 4;
    private Collection<Appender> _appenders;

    public LogFileHelper(Collection<Appender> appenders)
    {
        super();
        _appenders = appenders;
    }

    public List<LogFileDetails> findLogFileDetails(String[] requestedFiles)
    {
        List<LogFileDetails> logFiles = new ArrayList<LogFileDetails>();
        Map<String, List<LogFileDetails>> cache = new HashMap<String, List<LogFileDetails>>();
        for (int i = 0; i < requestedFiles.length; i++)
        {
            String[] paths = requestedFiles[i].split("/");
            if (paths.length != 2)
            {
                throw new IllegalArgumentException("Log file name '" + requestedFiles[i] + "' does not include an appender name");
            }

            String appenderName = paths[0];
            String fileName = paths[1];

            List<LogFileDetails> appenderFiles = cache.get(appenderName);
            if (appenderFiles == null)
            {
                Appender fileAppender = null;
                for (Appender appender : _appenders)
                {
                    if (appenderName.equals(appender.getName()))
                    {
                        fileAppender = appender;
                        break;
                    }
                }
                if (fileAppender == null)
                {
                    continue;
                }
                appenderFiles = getAppenderFiles(fileAppender, true);
                if (appenderFiles == null)
                {
                    continue;
                }
                cache.put(appenderName, appenderFiles);
            }
            for (LogFileDetails logFileDetails : appenderFiles)
            {
                if (logFileDetails.getName().equals(fileName))
                {
                    logFiles.add(logFileDetails);
                }
            }
        }
        return logFiles;
    }

    public List<LogFileDetails> getLogFileDetails(boolean includeLogFileLocation)
    {
        List<LogFileDetails> results = new ArrayList<LogFileDetails>();
        for (Appender appender : _appenders)
        {
            List<LogFileDetails> appenderFiles = getAppenderFiles(appender, includeLogFileLocation);
            if (appenderFiles != null)
            {
                results.addAll(appenderFiles);
            }
        }
        return results;
    }

    public void writeLogFiles(List<LogFileDetails> logFiles, OutputStream os) throws IOException
    {
        ZipOutputStream out = new ZipOutputStream(os);
        try
        {
            addLogFileEntries(logFiles, out);
        }
        finally
        {
            out.close();
        }
    }

    public void writeLogFile(File file, OutputStream os) throws IOException
    {
        FileInputStream fis = new FileInputStream(file);
        try
        {
            byte[] bytes = new byte[BUFFER_LENGTH];
            int length = 1;
            while ((length = fis.read(bytes)) != -1)
            {
                os.write(bytes, 0, length);
            }
        }
        finally
        {
            fis.close();
        }
    }

    private List<LogFileDetails> getAppenderFiles(Appender appender, boolean includeLogFileLocation)
    {
        if (appender instanceof QpidCompositeRollingAppender)
        {
            return listAppenderFiles((QpidCompositeRollingAppender) appender, includeLogFileLocation);
        }
        else if (appender instanceof FileAppender)
        {
            return listAppenderFiles((FileAppender) appender, includeLogFileLocation);
        }
        return null;
    }

    private List<LogFileDetails> listAppenderFiles(FileAppender appender, boolean includeLogFileLocation)
    {
        String appenderFilePath = appender.getFile();
        File appenderFile = new File(appenderFilePath);
        if (appenderFile.exists())
        {
            return listLogFiles(appenderFile.getParentFile(), appenderFile.getName(), appender.getName(), "", includeLogFileLocation);
        }
        return Collections.emptyList();
    }

    private List<LogFileDetails> listAppenderFiles(QpidCompositeRollingAppender appender, boolean includeLogFileLocation)
    {
        List<LogFileDetails> files = listAppenderFiles((FileAppender) appender, includeLogFileLocation);
        String appenderFilePath = appender.getFile();
        File appenderFile = new File(appenderFilePath);
        File backupFolder = new File(appender.getBackupFilesToPath());
        if (backupFolder.exists())
        {
            String backFolderName = backupFolder.getName() + "/";
            List<LogFileDetails> backedUpFiles = listLogFiles(backupFolder, appenderFile.getName(), appender.getName(),
                    backFolderName, includeLogFileLocation);
            files.addAll(backedUpFiles);
        }
        return files;
    }

    private List<LogFileDetails> listLogFiles(File parent, String baseFileName, String appenderName, String relativePath,
            boolean includeLogFileLocation)
    {
        List<LogFileDetails> files = new ArrayList<LogFileDetails>();
        for (File file : parent.listFiles())
        {
            String name = file.getName();
            if (name.startsWith(baseFileName))
            {
                files.add(new LogFileDetails(name, appenderName, includeLogFileLocation ? file : null, getMimeType(name), file.length(),
                        file.lastModified()));
            }
        }
        return files;
    }

    private String getMimeType(String fileName)
    {
        if (fileName.endsWith(GZIP_EXTENSION))
        {
            return GZIP_MIME_TYPE;
        }
        return TEXT_MIME_TYPE;
    }

    private void addLogFileEntries(List<LogFileDetails> files, ZipOutputStream out) throws IOException
    {
        for (LogFileDetails logFileDetails : files)
        {
            File file = logFileDetails.getLocation();
            if (file.exists())
            {
                ZipEntry entry = new ZipEntry(logFileDetails.getAppenderName() + "/" + logFileDetails.getName());
                entry.setSize(file.length());
                out.putNextEntry(entry);
                writeLogFile(file, out);
                out.closeEntry();
            }
            out.flush();
        }
    }

}
