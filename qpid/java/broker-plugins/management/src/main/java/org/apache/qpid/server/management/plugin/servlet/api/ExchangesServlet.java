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
import org.codehaus.jackson.map.ObjectReader;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExchangesServlet extends HttpServlet
{


    private Broker _broker;

    public ExchangesServlet(Broker broker)
    {
        _broker = broker;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        Collection<VirtualHost> vhosts = _broker.getVirtualHosts();
        Collection<Exchange> exchanges = new ArrayList<Exchange>();
        Collection<Map<String,Object>> outputObject = new ArrayList<Map<String,Object>>();

        final PrintWriter writer = response.getWriter();

        ObjectMapper mapper = new ObjectMapper();
        String vhostName = null;
        String exchangeName = null;

        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            String[] parts = path.split("/");
            vhostName = parts.length == 0 ? "" : parts[0];
            if(parts.length > 1)
            {
                exchangeName = parts[1];
            }
        }

        for(VirtualHost vhost : vhosts)
        {
            if(vhostName == null || vhostName.equals(vhost.getName()))
            {
                for(Exchange exchange : vhost.getExchanges())
                {
                    if(exchangeName == null || exchangeName.equals(exchange.getName()))
                    {
                        outputObject.add(convertToObject(exchange));
                        if(exchangeName != null)
                        {
                            break;
                        }
                    }
                }
                if(vhostName != null)
                {
                    break;
                }
            }
        }

        mapper.writeValue(writer, outputObject);

    }

    private Map<String,Object> convertToObject(final Exchange exchange)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("name",exchange.getName());
        object.put("type", exchange.getExchangeType());
        object.put("durable", exchange.isDurable());
        object.put("auto-delete", exchange.getLifetimePolicy() == LifetimePolicy.AUTO_DELETE);

        Map<String,Object> arguments = new HashMap<String, Object>();
        for(String key : exchange.getAttributeNames())
        {
            if(!key.equals(Exchange.TYPE))
            {
                arguments.put(key, exchange.getAttribute(key));
            }
        }
        object.put("arguments", arguments);
        return object;
    }

    protected void doPut(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {

        response.setContentType("application/json");


        String vhostName = null;
        String exchangeName = null;
        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            String[] parts = path.split("/");
            vhostName = parts.length == 0 ? "" : parts[0];
            if(parts.length > 1)
            {
                exchangeName = parts[1];
            }
        }
        if(vhostName == null)
        {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
        }
        else if (exchangeName == null)
        {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
        }
        else
        {
            VirtualHost vhost = null;
            for(VirtualHost host : _broker.getVirtualHosts())
            {
                if(host.getName().equals(vhostName))
                {
                    vhost = host;
                }
            }
            if(vhost == null)
            {
                response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            }
            else
            {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                ObjectMapper mapper = new ObjectMapper();
                Map<String,Object> exchangeObject = mapper.readValue(request.getInputStream(), LinkedHashMap.class);

                final boolean isDurable = exchangeObject.get("durable") instanceof Boolean
                                          && ((Boolean)exchangeObject.get("durable"));
                final boolean isAutoDelete = exchangeObject.get("auto_delete") instanceof Boolean
                                          && ((Boolean)exchangeObject.get("auto_delete"));

                final String type = (String) exchangeObject.get("type");
                final Map<String, Object> attributes = new HashMap<String, Object>(exchangeObject);
                attributes.remove("durable");
                attributes.remove("auto_delete");
                attributes.remove("type");

                vhost.createExchange(exchangeName, State.ACTIVE, isDurable,
                                     isAutoDelete ? LifetimePolicy.AUTO_DELETE : LifetimePolicy.PERMANENT,
                                     0l,
                                     type,
                                     attributes);
            }



        }



    }
}
