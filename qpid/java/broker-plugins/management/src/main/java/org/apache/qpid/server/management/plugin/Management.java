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
package org.apache.qpid.server.management.plugin;

import java.util.ArrayList;
import java.util.Collection;
import org.apache.log4j.Logger;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.api.ExchangesServlet;
import org.apache.qpid.server.management.plugin.servlet.api.VhostsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.*;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.SessionManager;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

public class Management
{

    private final Logger _logger = Logger.getLogger(Management.class);

    private Broker _broker;

    private Collection<Server> _servers = new ArrayList<Server>();


    public Management()
    {
        _logger.info("Starting up web server on port 8080");

        _broker = ApplicationRegistry.getInstance().getBroker();

        Collection<Port> ports = _broker.getPorts();
        for(Port port : ports)
        {
            // TODO - cover cases where more than just HTTP supported, and SSL as a transport
            if(port.getProtocols().contains(Protocol.HTTP))
            {
                if(port.getTransports().contains(Transport.TCP))
                {
                    _servers.add(createServer(port.getPort()));
                }
            }
        }


    }

    private Server createServer(int port)
    {
        Server server = new Server(port);
        Context root;
        root = new Context(server,"/", Context.SESSIONS);
        root.addServlet(new ServletHolder(new VhostsServlet(_broker)), "/api/vhosts/*");
        root.addServlet(new ServletHolder(new ExchangesServlet(_broker)), "/api/exchanges/*");

        addRestServlet(root, "broker");
        addRestServlet(root, "virtualhost", VirtualHost.class);
        addRestServlet(root, "exchange", VirtualHost.class, Exchange.class);
        addRestServlet(root, "queue", VirtualHost.class, Queue.class);
        addRestServlet(root, "connection", VirtualHost.class, Connection.class);
        addRestServlet(root, "binding", VirtualHost.class, Exchange.class, Queue.class, Binding.class);
        addRestServlet(root, "port", Port.class);
        addRestServlet(root, "session", VirtualHost.class, Connection.class, Session.class);

        root.addServlet(new ServletHolder(new StructureServlet(_broker)), "/rest/structure");
        root.addServlet(new ServletHolder(new MessageServlet(_broker)), "/rest/message/*");

        root.addServlet(new ServletHolder(new LogRecordsServlet(_broker)), "/rest/logrecords");


        root.addServlet(new ServletHolder(new SaslServlet(_broker)), "/rest/sasl");

        root.addServlet(new ServletHolder(new DefinedFileServlet("queue.html")),"/queue");
        root.addServlet(new ServletHolder(new DefinedFileServlet("exchange.html")),"/exchange");
        root.addServlet(new ServletHolder(new DefinedFileServlet("vhost.html")),"/vhost");
        root.addServlet(new ServletHolder(new DefinedFileServlet("broker.html")),"/broker");
        root.addServlet(new ServletHolder(new DefinedFileServlet("connection.html")),"/connection");


        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.js");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.css");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.html");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.png");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.gif");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.jpg");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.jpeg");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.json");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.txt");
        root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.xsl");

        final SessionManager sessionManager = root.getSessionHandler().getSessionManager();

        sessionManager.setMaxCookieAge(60 * 30);
        sessionManager.setMaxInactiveInterval(60 * 15);

        return server;
    }

    private void addRestServlet(Context root, String name, Class<? extends ConfiguredObject>... hierarchy)
    {
        root.addServlet(new ServletHolder(new RestServlet(_broker, hierarchy)), "/rest/"+name+"/*");
    }

    public void start() throws Exception
    {
        for(Server server : _servers)
        {
            server.start();
        }
    }

    public void stop() throws Exception
    {
        for(Server server : _servers)
        {
            server.stop();
        }
    }

}
