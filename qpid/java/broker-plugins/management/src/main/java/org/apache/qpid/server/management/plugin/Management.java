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

import org.apache.log4j.Logger;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.api.ExchangesServlet;
import org.apache.qpid.server.management.plugin.servlet.api.VhostsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.RestServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.SaslServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.StructureServlet;
import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
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

    private Server _server;
    private Context _root;

    public Management()
    {
        _logger.info("Starting up web server on port 8080");

        _broker = ApplicationRegistry.getInstance().getBroker();

        _server = new Server(8080);

        _root = new Context(_server,"/", Context.SESSIONS);
        _root.addServlet(new ServletHolder(new VhostsServlet(_broker)), "/api/vhosts/*");
        _root.addServlet(new ServletHolder(new ExchangesServlet(_broker)), "/api/exchanges/*");

        addRestServlet("virtualhost", VirtualHost.class);
        addRestServlet("exchange", VirtualHost.class, Exchange.class);
        addRestServlet("queue", VirtualHost.class, Queue.class);
        addRestServlet("connection", VirtualHost.class, Connection.class);
        addRestServlet("binding", VirtualHost.class, Exchange.class, Queue.class, Binding.class);
        addRestServlet("port", Port.class);
        addRestServlet("session", VirtualHost.class, Connection.class, Session.class);

        _root.addServlet(new ServletHolder(new StructureServlet(_broker)), "/rest/structure");

        _root.addServlet(new ServletHolder(new SaslServlet(_broker)), "/rest/sasl");

        _root.addServlet(new ServletHolder(new DefinedFileServlet("queue.html")),"/queue");
        _root.addServlet(new ServletHolder(new DefinedFileServlet("exchange.html")),"/exchange");
        _root.addServlet(new ServletHolder(new DefinedFileServlet("vhost.html")),"/vhost");
        _root.addServlet(new ServletHolder(new DefinedFileServlet("broker.html")),"/broker");
        _root.addServlet(new ServletHolder(new DefinedFileServlet("connection.html")),"/connection");


        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.js");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.css");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.html");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.png");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.gif");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.jpg");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.jpeg");
        _root.addServlet(new ServletHolder(FileServlet.INSTANCE), "*.json");

        final SessionManager sessionManager = _root.getSessionHandler().getSessionManager();

        sessionManager.setMaxCookieAge(60 * 30);
        sessionManager.setMaxInactiveInterval(60 * 15);
    }

    private void addRestServlet(String name, Class<? extends ConfiguredObject>... hierarchy)
    {
        _root.addServlet(new ServletHolder(new RestServlet(_broker, hierarchy)), "/rest/"+name+"/*");
    }

    public void start() throws Exception
    {
        _server.start();
    }

    public void stop() throws Exception
    {
        _server.stop();
    }

}
