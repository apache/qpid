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
package org.apache.qpid.server.management.plugin.servlet;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.codehaus.jackson.map.ObjectMapper;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FileServlet extends HttpServlet
{
    public static final FileServlet INSTANCE = new FileServlet();


    public FileServlet()
    {
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String filename = request.getServletPath();

        if(filename.endsWith(".js"))
        {
            response.setContentType("application/javascript");
        }
        else if(filename.endsWith(".html"))
        {
            response.setContentType("text/html");
        }
        else if(filename.endsWith(".css"))
        {
            response.setContentType("text/css");
        }
        else if(filename.endsWith(".json"))
        {
            response.setContentType("application/json");
        }

        final ServletOutputStream output = response.getOutputStream();
        InputStream fileInput = getClass().getResourceAsStream("/resources" + filename);
System.out.println("Got: " + filename);
        if(fileInput != null)
        {
            byte[] buffer = new byte[1024];
            response.setStatus(HttpServletResponse.SC_OK);
            int read = 0;

            while((read = fileInput.read(buffer)) > 0)
            {
                output.write(buffer, 0, read);
            }
        }
        else
        {
System.out.println("Not found: " + filename);
            response.sendError(404, "unknown file: "+ filename);
        }

    }

}
