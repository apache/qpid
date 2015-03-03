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
package org.apache.qpid.server.management.plugin.servlet.rest;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.server.management.plugin.report.ReportRunner;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;

public class QueueReportServlet extends AbstractServlet
{
    @Override
    protected void doGetWithSubjectAndActor(HttpServletRequest request, HttpServletResponse response) throws
                                                                                                      IOException,
                                                                                                      ServletException
    {
        String[] pathInfoElements = getPathInfoElements(request);
        if(pathInfoElements != null && pathInfoElements.length == 3)
        {
            Queue<?> queue = getQueueFromRequest(request);
            ReportRunner<?> reportRunner = ReportRunner.createRunner(pathInfoElements[2],request.getParameterMap());
            Object output = reportRunner.runReport(queue);
            response.setContentType(reportRunner.getContentType());
            if(reportRunner.isBinaryReport())
            {
                response.getOutputStream().write((byte[])output);
            }
            else
            {
                response.getWriter().write((String)output);
            }
        }
        else
        {
            throw new IllegalArgumentException("Invalid path is specified");
        }

    }

    private Queue<?> getQueueFromRequest(HttpServletRequest request)
    {
        String[] pathInfoElements = getPathInfoElements(request);
        if(pathInfoElements == null || pathInfoElements.length < 2)
        {
            throw new IllegalArgumentException("Invalid path is specified");
        }
        String vhostName = pathInfoElements[0];
        String queueName = pathInfoElements[1];

        VirtualHost<?,?,?> vhost = getBroker().findVirtualHostByName(vhostName);
        if (vhost == null)
        {
            throw new IllegalArgumentException("Could not find virtual host with name '" + vhostName + "'");
        }

        Queue queueFromVirtualHost = getQueueFromVirtualHost(queueName, vhost);
        if (queueFromVirtualHost == null)
        {
            throw new IllegalArgumentException("Could not find queue with name '" + queueName  + "' on virtual host '" + vhost.getName() + "'");
        }
        return queueFromVirtualHost;
    }

    private Queue getQueueFromVirtualHost(String queueName, VirtualHost<?,?,?> vhost)
    {
        Queue queue = null;

        for(Queue<?> q : vhost.getQueues())
        {

            if(q.getName().equals(queueName))
            {
                queue = q;
                break;
            }
        }
        return queue;
    }

}
