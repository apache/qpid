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

package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.LogManager;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.management.plugin.log.LogFileDetails;
import org.apache.qpid.server.management.plugin.log.LogFileHelper;

public class LogFileListingServlet extends AbstractServlet
{
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException,
            ServletException
    {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        if (!getBroker().getSecurityManager().authoriseLogsAccess())
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Log files access is denied");
            return;
        }

        @SuppressWarnings("unchecked")
        LogFileHelper helper = new LogFileHelper(Collections.list(LogManager.getRootLogger().getAllAppenders()));
        List<LogFileDetails> logFiles = helper.getLogFileDetails(false);
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        final Writer writer = getOutputWriter(request, response);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, logFiles);

        response.setStatus(HttpServletResponse.SC_OK);
    }

}
