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
package org.apache.qpid.server.management.plugin.servlet.api;

import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.protocol.AMQConnectionModel;


import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class VhostsServlet extends HttpServlet
{


    private Broker _broker;

    public VhostsServlet(Broker broker)
    {
        _broker = broker;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
System.out.println("Get /api/vhosts");
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        Collection<VirtualHost> vhosts = _broker.getVirtualHosts();



        final PrintWriter writer = response.getWriter();

        ObjectMapper mapper = new ObjectMapper();

        if(request.getPathInfo() == null || request.getPathInfo().length()==0)
        {

            LinkedHashMap<String, Object> vhostObject = new LinkedHashMap<String, Object>();
            List<Map> vhostList = new ArrayList<Map>();

            for(VirtualHost vhost : vhosts)
            {
                vhostList.add(Collections.singletonMap("name", vhost.getName()));
            }
            mapper.writeValue(writer, vhostList);
        }
        else
        {
            LinkedHashMap<String, Object> vhostObject = new LinkedHashMap<String, Object>();
            String vhostName = request.getPathInfo().substring(1);

            for(VirtualHost vhost : vhosts)
            {
                if(vhostName.equals(vhost.getName()))
                {
                    vhostObject.put("name", vhost.getName());
                    break;
                }
            }
            mapper.writeValue(writer, vhostObject);
        }
    }


    protected void doPut(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);

        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String vhostName = request.getPathInfo().substring(1);
            _broker.createVirtualHost(vhostName, State.ACTIVE, true, LifetimePolicy.PERMANENT, 0L, Collections.EMPTY_MAP);
        }


    }
}
