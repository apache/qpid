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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.codehaus.jackson.map.ObjectMapper;

public class VirtualHostServlet extends AbstractServlet
{


    private Broker _broker;

    private static final Comparator DEFAULT_COMPARATOR = new KeyComparator("name");

    public VirtualHostServlet(Broker broker)
    {
        _broker = broker;
    }

    protected void onGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        Collection<VirtualHost> vhosts = _broker.getVirtualHosts();

        String[] sortKeys = request.getParameterValues("sort");
        Comparator comparator;
        if(sortKeys == null || sortKeys.length == 0)
        {
            comparator = DEFAULT_COMPARATOR;
        }
        else
        {
            comparator = new MapComparator(sortKeys);
        }

        final PrintWriter writer = response.getWriter();

        ObjectMapper mapper = new ObjectMapper();
        List<Map> vhostList = new ArrayList<Map>();

        if(request.getPathInfo() == null || request.getPathInfo().length()==0)
        {

            for(VirtualHost vhost : vhosts)
            {
                Map<String, Object> data = convertObject(vhost);


                vhostList.add(data);
            }
        }
        else
        {
            String vhostName = request.getPathInfo().substring(1);

            for(VirtualHost vhost : vhosts)
            {
                if(vhostName.equals(vhost.getName()))
                {
                    Map<String, Object> data = convertObject(vhost);

                    vhostList.add(data);
                    break;
                }
            }

        }


        Collections.sort(vhostList, comparator);

        mapper.writeValue(writer, vhostList);
    }

    private Map<String, Object> convertObject(VirtualHost vhost)
    {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("id", vhost.getId());
        data.put("name", vhost.getName());
        data.put("connection-count", vhost.getConnections().size());
        data.put("exchange-count", vhost.getExchanges().size());
        data.put("queue-count", vhost.getQueues().size());
        Statistics stats = vhost.getStatistics();

        data.put("bytes-out-total", stats.getStatistic("bytes-out-total"));
        data.put("bytes-in-total", stats.getStatistic("bytes-in-total"));
        data.put("msgs-out-total", stats.getStatistic("msgs-out-total"));
        data.put("msgs-in-total", stats.getStatistic("msgs-in-total"));

        return data;
    }


    protected void onPut(final HttpServletRequest request, final HttpServletResponse response)
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
