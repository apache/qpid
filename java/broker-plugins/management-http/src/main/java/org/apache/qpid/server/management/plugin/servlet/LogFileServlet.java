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

package org.apache.qpid.server.management.plugin.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.LogManager;
import org.apache.qpid.server.management.plugin.log.LogFileDetails;
import org.apache.qpid.server.management.plugin.log.LogFileHelper;
import org.apache.qpid.server.management.plugin.servlet.rest.AbstractServlet;

public class LogFileServlet extends AbstractServlet
{
    private static final long serialVersionUID = 1L;

    public static final String LOGS_FILE_NAME = "qpid-logs-%s.zip";
    public static final String DATE_FORMAT = "yyyy-MM-dd-mmHHss";

    @SuppressWarnings("unchecked")
    private LogFileHelper _helper = new LogFileHelper(Collections.list(LogManager.getRootLogger().getAllAppenders()));

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException
    {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        if (!getBroker().getSecurityManager().authoriseLogsAccess())
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Log files download is denied");
            return;
        }

        String[] requestedFiles = request.getParameterValues("l");

        if (requestedFiles == null || requestedFiles.length == 0)
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        List<LogFileDetails> logFiles = null;

        try
        {
            logFiles = _helper.findLogFileDetails(requestedFiles);
        }
        catch(IllegalArgumentException e)
        {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (logFiles.size() == 0)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String fileName = String.format(LOGS_FILE_NAME, new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setContentType(LogFileHelper.ZIP_MIME_TYPE);

        OutputStream os = response.getOutputStream();
        try
        {
            _helper.writeLogFiles(logFiles, os);
        }
        finally
        {
            if (os != null)
            {
                os.close();
            }
        }
    }

}
