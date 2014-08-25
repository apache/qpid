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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

import org.apache.qpid.server.logging.LogRecorder;

public class LogRecordsServlet extends AbstractServlet
{
    private static final long serialVersionUID = 2L;

    public static final String PARAM_LAST_LOG_ID = "lastLogId";

    public LogRecordsServlet()
    {
        super();
    }

    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Pragma","no-cache");
        response.setDateHeader ("Expires", 0);

        if (!getBroker().getSecurityManager().authoriseLogsAccess())
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Broker logs access is denied");
            return;
        }

        long lastLogId = 0;
        try
        {
            lastLogId = Long.parseLong(request.getParameter(PARAM_LAST_LOG_ID));
        }
        catch(Exception e)
        {
            // ignore null and incorrect parameter values
        }

        List<Map<String,Object>> logRecords = new ArrayList<Map<String, Object>>();

        LogRecorder logRecorder = getBroker().getLogRecorder();
        for(LogRecorder.Record record : logRecorder)
        {
            if (record.getId() > lastLogId)
            {
                logRecords.add(logRecordToObject(record));
            }
        }

        final Writer writer = getOutputWriter(request,response);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        mapper.writeValue(writer, logRecords);

        response.setStatus(HttpServletResponse.SC_OK);

    }

    private Map<String, Object> logRecordToObject(LogRecorder.Record record)
    {
        Map<String, Object> recordMap = new LinkedHashMap<String, Object>();
        recordMap.put("id",record.getId());
        recordMap.put("timestamp", record.getTimestamp());
        recordMap.put("level", record.getLevel());
        recordMap.put("thread", record.getThreadName());
        recordMap.put("logger", record.getLogger());
        recordMap.put("message", record.getMessage());
        return recordMap;
    }

}
