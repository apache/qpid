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
import org.apache.qpid.server.management.plugin.servlet.rest.BindingServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.ConnectionServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.ExchangeServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.QueueServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.SaslServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.VirtualHostServlet;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.SessionManager;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

public class Management
{

    private final Logger _logger = Logger.getLogger(Management.class);

    private Broker _broker;

    public Management()
    {
        _logger.info("Starting up web server on port 8080");

        _broker = BrokerAdapter.getInstance();

        start();



    }

    private void start()
    {
        Server server = new Server(8080);
        try
        {
            Context root = new Context(server,"/", Context.SESSIONS);
            root.addServlet(new ServletHolder(new VhostsServlet(_broker)), "/api/vhosts/*");
            root.addServlet(new ServletHolder(new ExchangesServlet(_broker)), "/api/exchanges/*");

            root.addServlet(new ServletHolder(new VirtualHostServlet(_broker)), "/rest/virtualhost/*");
            root.addServlet(new ServletHolder(new ExchangeServlet(_broker)), "/rest/exchange/*");
            root.addServlet(new ServletHolder(new QueueServlet(_broker)), "/rest/queue/*");
            root.addServlet(new ServletHolder(new ConnectionServlet(_broker)), "/rest/connection/*");
            root.addServlet(new ServletHolder(new BindingServlet(_broker)), "/rest/binding/*");
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

            final SessionManager sessionManager = root.getSessionHandler().getSessionManager();

            sessionManager.setMaxCookieAge(60 * 30);
            sessionManager.setMaxInactiveInterval(60 * 15);

            server.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();  //TODO
        }
    }

}
