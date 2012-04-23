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


    public BindingServlet(Broker broker)
    {
        super(broker, VirtualHost.class, Exchange.class, Queue.class, Binding.class);
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
            for(VirtualHost host : getBroker().getVirtualHosts())
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

                vhost.createQueue(queueName, State.ACTIVE, isDurable, false,
                                     isAutoDelete ? LifetimePolicy.AUTO_DELETE : LifetimePolicy.PERMANENT,
                                     0l,
                                     attributes);
            }



        }



    }

}
