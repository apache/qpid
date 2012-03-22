package org.apache.qpid.server.management.plugin.servlet;

import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class DefinedFileServlet extends HttpServlet
{

    private final String _filename;

    public DefinedFileServlet(String filename)
    {
        _filename = filename;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        final ServletOutputStream output = response.getOutputStream();
        InputStream fileInput = getClass().getResourceAsStream("/resources/"+_filename);

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
            response.sendError(404, "unknown file: "+ _filename);
        }
    }
}
