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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.log4j.Logger;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.api.ExchangesServlet;
import org.apache.qpid.server.management.plugin.servlet.api.VhostsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.*;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class Management
{

    private final Logger _logger = Logger.getLogger(Management.class);

    private Broker _broker;

    private Collection<Server> _servers = new ArrayList<Server>();


    public Management()
    {
        _broker = ApplicationRegistry.getInstance().getBroker();

        Collection<Port> ports = _broker.getPorts();
        for(Port port : ports)
        {
            // TODO - cover cases where more than just HTTP supported, and SSL as a transport
            if(port.getProtocols().contains(Protocol.HTTP))
            {
                if(port.getTransports().contains(Transport.TCP))
                {
                    int portNumber = port.getPort();
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Creating web server on port " + portNumber);
                    }
                    _servers.add(createServer(portNumber));
                }
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.info(_servers.size() + " server(s) defined");
        }

    }

    private Server createServer(int port)
    {
        _logger.info("Starting up web server on port " + port);

        Server server = new Server(port);
        SocketAddress socketAddress = new InetSocketAddress(port);

        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
                root.setContextPath("/");
                server.setHandler(root);

        root.addServlet(new ServletHolder(new VhostsServlet(_broker)), "/api/vhosts/*");
        root.addServlet(new ServletHolder(new ExchangesServlet(_broker)), "/api/exchanges/*");

        addRestServlet(root, "broker", socketAddress);
        addRestServlet(root, "virtualhost", socketAddress, VirtualHost.class);
        addRestServlet(root, "authenticationprovider", socketAddress, AuthenticationProvider.class);
        addRestServlet(root, "user", socketAddress, AuthenticationProvider.class, User.class);
        addRestServlet(root, "exchange", socketAddress, VirtualHost.class, Exchange.class);
        addRestServlet(root, "queue", socketAddress, VirtualHost.class, Queue.class);
        addRestServlet(root, "connection", socketAddress, VirtualHost.class, Connection.class);
        addRestServlet(root, "binding", socketAddress, VirtualHost.class, Exchange.class, Queue.class, Binding.class);
        addRestServlet(root, "port", socketAddress, Port.class);
        addRestServlet(root, "session", socketAddress, VirtualHost.class, Connection.class, Session.class);

        root.addServlet(new ServletHolder(new StructureServlet(_broker, socketAddress)), "/rest/structure");
        root.addServlet(new ServletHolder(new MessageServlet(_broker, socketAddress)), "/rest/message/*");
        root.addServlet(new ServletHolder(new MessageContentServlet(_broker, socketAddress)), "/rest/message-content/*");

        root.addServlet(new ServletHolder(new LogRecordsServlet(_broker, socketAddress)), "/rest/logrecords");


        root.addServlet(new ServletHolder(new SaslServlet(_broker, socketAddress)), "/rest/sasl");

        root.addServlet(new ServletHolder(new DefinedFileServlet("management.html")),"/management");


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

        sessionManager.setMaxInactiveInterval(60 * 15);

        return server;
    }

    private void addRestServlet(ServletContextHandler root, String name, SocketAddress socketAddress, Class<? extends ConfiguredObject>... hierarchy)
    {
        root.addServlet(new ServletHolder(new RestServlet(_broker, socketAddress, hierarchy)), "/rest/"+name+"/*");
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
