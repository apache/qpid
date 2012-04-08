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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueue;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class BindingServlet extends AbstractServlet
{


    private Broker _broker;
    private static final Comparator DEFAULT_COMPARATOR = new KeyComparator("name");


    public BindingServlet(Broker broker)
    {
        _broker = broker;
    }

    protected void onGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

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


        Collection<VirtualHost> vhosts = _broker.getVirtualHosts();
        Collection<AMQQueue> queues = new ArrayList<AMQQueue>();
        List<Map<String,Object>> outputObject = new ArrayList<Map<String,Object>>();

        final PrintWriter writer = response.getWriter();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);

        String vhostName = null;
        String queueName = null;
        String exchangeName = null;
        String bindingKey = null;

        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            String[] parts = path.split("/");
            vhostName = parts.length == 0 ? "" : parts[0];
            if(parts.length > 1)
            {
                exchangeName = parts[1];
                if(parts.length > 2)
                {
                    queueName = parts[2];
                    if(parts.length > 3)
                    {
                        bindingKey = parts[3];
                    }
                }
            }
        }

        for(VirtualHost vhost : vhosts)
        {
            if(vhostName == null || vhostName.equals(vhost.getName()))
            {
                for(Exchange exchange : vhost.getExchanges())
                {
                    final boolean allExchanges = exchangeName == null || exchangeName.equals("*");
                    if(allExchanges || exchangeName.equals(exchange.getName()))
                    {
                        for(Queue queue : vhost.getQueues())
                        {
                            if(queueName == null || queueName.equals(queue.getName()))
                            {
                                for(Binding binding : queue.getBindings())
                                {
                                    if((bindingKey == null || bindingKey.equals(binding.getName()))
                                       && (allExchanges || exchangeName.equals(binding.getParent(Exchange.class).getName())))
                                    {
                                        outputObject.add(convertObjectToMap(binding));
                                        if(bindingKey != null)
                                        {
                                            break;
                                        }
                                    }

                                }

                                if(queueName != null)
                                {
                                    break;
                                }
                            }
                        }
                    }
                }

                if(vhostName != null)
                {
                    break;
                }
            }
        }

        Collections.sort(outputObject, comparator);
        mapper.writeValue(writer, outputObject);

    }

    private Map<String,Object> convertToObject(final Queue queue)
    {
        Map<String, Object> object = convertObjectToMap(queue);


        List<Map<String,Object>> bindings = new ArrayList<Map<String, Object>>();

        for(Binding binding : queue.getBindings())
        {
            bindings.add(convertObjectToMap(binding));
        }

        return object;
    }

    private Map<String, Object> convertObjectToMap(final ConfiguredObject confObject)
    {
        Map<String, Object> object = new LinkedHashMap<String, Object>();

        for(String name : confObject.getAttributeNames())
        {
            Object value = confObject.getAttribute(name);
            if(value != null)
            {
                object.put(name, value);
            }
        }

        Statistics statistics = confObject.getStatistics();
        Map<String, Object> statMap = new HashMap<String, Object>();
        for(String name : statistics.getStatisticNames())
        {
            Object value = statistics.getStatistic(name);
            if(value != null)
            {
                statMap.put(name, value);
            }
        }

        if(!statMap.isEmpty())
        {
            object.put("statistics", statMap);
        }
        return object;
    }


    @Override
    protected void onPut(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException
    {

        response.setContentType("application/json");

        String vhostName = null;
        String queueName = null;
        if(request.getPathInfo() != null && request.getPathInfo().length()>0)
        {
            String path = request.getPathInfo().substring(1);
            String[] parts = path.split("/");
            vhostName = parts.length == 0 ? "" : parts[0];
            if(parts.length > 1)
            {
                queueName = parts[1];
            }
        }

        if(getSubject() == null)
        {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
        else if(vhostName == null)
        {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
        }
        else if (queueName == null)
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
                Map<String,Object> queueObject = mapper.readValue(request.getInputStream(), LinkedHashMap.class);

                final boolean isDurable = queueObject.get("durable") instanceof Boolean
                                          && ((Boolean)queueObject.get("durable"));
                final boolean isAutoDelete = queueObject.get("auto_delete") instanceof Boolean
                                             && ((Boolean)queueObject.get("auto_delete"));

                final Map<String, Object> attributes = new HashMap<String, Object>(queueObject);
                attributes.remove("durable");
                attributes.remove("auto_delete");

                vhost.createQueue(queueName, State.ACTIVE, isDurable,
                                     isAutoDelete ? LifetimePolicy.AUTO_DELETE : LifetimePolicy.PERMANENT,
                                     0l,
                                     attributes);
            }



        }



    }

}
