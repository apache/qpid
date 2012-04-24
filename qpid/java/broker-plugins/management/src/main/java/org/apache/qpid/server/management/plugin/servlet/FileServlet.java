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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FileServlet extends HttpServlet
{
    public static final FileServlet INSTANCE = new FileServlet();
    
    private static final Map<String, String> CONTENT_TYPES;
    
    static
    {

        Map<String, String> contentTypes = new HashMap<String, String>();
        contentTypes.put("js",   "application/javascript");
        contentTypes.put("html", "text/html");
        contentTypes.put("css",  "text/css");
        contentTypes.put("json", "application/json");
        contentTypes.put("jpg",  "image/jpg");
        contentTypes.put("png",  "image/png");
        contentTypes.put("gif",  "image/gif");
        CONTENT_TYPES = Collections.unmodifiableMap(contentTypes);
    }


    public FileServlet()
    {
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        String filename = request.getServletPath();

        if(filename.contains("."))
        {
            String suffix = filename.substring(filename.lastIndexOf('.')+1);
            String contentType = CONTENT_TYPES.get(suffix);
            if(contentType != null)
            {
                response.setContentType(contentType);
            }
        }

        final ServletOutputStream output = response.getOutputStream();
        InputStream fileInput = getClass().getResourceAsStream("/resources" + filename);

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
            response.sendError(404, "unknown file: "+ filename);
        }

    }

}
