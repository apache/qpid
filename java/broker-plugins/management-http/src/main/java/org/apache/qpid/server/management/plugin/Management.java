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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.api.ExchangesServlet;
import org.apache.qpid.server.management.plugin.servlet.api.VhostsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.LogRecordsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.MessageContentServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.MessageServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.RestServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.SaslServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.StructureServlet;
import org.apache.qpid.server.model.AuthenticationProvider;
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
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class Management
{

    private final Logger _logger = Logger.getLogger(Management.class);

    private Broker _broker;

    private Collection<Server> _servers = new ArrayList<Server>();

    public Management() throws ConfigurationException, IOException
    {
        _broker = ApplicationRegistry.getInstance().getBroker();

        Collection<Port> ports = _broker.getPorts();
        int httpPort = -1, httpsPort = -1;
        for (Port port : ports)
        {
            if (port.getProtocols().contains(Protocol.HTTP))
            {
                if (port.getTransports().contains(Transport.TCP))
                {
                    httpPort = port.getPort();
                }
            }
            if (port.getProtocols().contains(Protocol.HTTPS))
            {
                if (port.getTransports().contains(Transport.SSL))
                {
                    httpsPort = port.getPort();
                }
            }
        }

        if (httpPort != -1 || httpsPort != -1)
        {
            _servers.add(createServer(httpPort, httpsPort));
            if (_logger.isDebugEnabled())
            {
                _logger.debug(_servers.size() + " server(s) defined");
            }
        }
        else
        {
            if (_logger.isInfoEnabled())
            {
                _logger.info("Cannot create web server as neither HTTP nor HTTPS port specified");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Server createServer(int port, int sslPort) throws IOException, ConfigurationException
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Starting up web server on" + (port == -1 ? "" : " HTTP port " + port)
                    + (sslPort == -1 ? "" : " HTTPS port " + sslPort));
        }

        Server server = new Server();

        if (port != -1)
        {
            SelectChannelConnector connector = new SelectChannelConnector();
            connector.setPort(port);
            if (sslPort != -1)
            {
                connector.setConfidentialPort(sslPort);
            }
            server.addConnector(connector);
        }

        if (sslPort != -1)
        {
            IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
            String keyStorePath = getKeyStorePath(appRegistry);

            SslContextFactory factory = new SslContextFactory();
            factory.setKeyStorePath(keyStorePath);
            factory.setKeyStorePassword(appRegistry.getConfiguration().getManagementKeyStorePassword());

            SslSocketConnector connector = new SslSocketConnector(factory);
            connector.setPort(sslPort);
            server.addConnector(connector);
        }

        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
        root.setContextPath("/");
        server.setHandler(root);

        root.addServlet(new ServletHolder(new VhostsServlet(_broker)), "/api/vhosts/*");
        root.addServlet(new ServletHolder(new ExchangesServlet(_broker)), "/api/exchanges/*");

        addRestServlet(root, "broker");
        addRestServlet(root, "virtualhost", VirtualHost.class);
        addRestServlet(root, "authenticationprovider", AuthenticationProvider.class);
        addRestServlet(root, "user", AuthenticationProvider.class, User.class);
        addRestServlet(root, "exchange", VirtualHost.class, Exchange.class);
        addRestServlet(root, "queue", VirtualHost.class, Queue.class);
        addRestServlet(root, "connection", VirtualHost.class, Connection.class);
        addRestServlet(root, "binding", VirtualHost.class, Exchange.class, Queue.class, Binding.class);
        addRestServlet(root, "port", Port.class);
        addRestServlet(root, "session", VirtualHost.class, Connection.class, Session.class);

        root.addServlet(new ServletHolder(new StructureServlet(_broker)), "/rest/structure");
        root.addServlet(new ServletHolder(new MessageServlet(_broker)), "/rest/message/*");
        root.addServlet(new ServletHolder(new MessageContentServlet(_broker)), "/rest/message-content/*");

        root.addServlet(new ServletHolder(new LogRecordsServlet(_broker)), "/rest/logrecords");

        root.addServlet(new ServletHolder(new SaslServlet(_broker)), "/rest/sasl");

        root.addServlet(new ServletHolder(new DefinedFileServlet("management.html")), "/management");

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

    private void addRestServlet(ServletContextHandler root, String name, Class<? extends ConfiguredObject>... hierarchy)
    {
        root.addServlet(new ServletHolder(new RestServlet(_broker, hierarchy)), "/rest/" + name + "/*");
    }

    public void start() throws Exception
    {
        for (Server server : _servers)
        {
            server.start();
        }
    }

    public void stop() throws Exception
    {
        for (Server server : _servers)
        {
            server.stop();
        }
    }

    private String getKeyStorePath(IApplicationRegistry appRegistry) throws ConfigurationException, FileNotFoundException
    {
        String keyStorePath = null;
        if (System.getProperty("javax.net.ssl.keyStore") != null)
        {
            keyStorePath = System.getProperty("javax.net.ssl.keyStore");
        }
        else
        {
            keyStorePath = appRegistry.getConfiguration().getManagementKeyStorePath();
        }

        if (keyStorePath == null)
        {
            throw new ConfigurationException("Management SSL keystore path not defined, unable to start SSL protected HTTP connector");
        }
        else
        {
            File ksf = new File(keyStorePath);
            if (!ksf.exists())
            {
                throw new FileNotFoundException("Cannot find management SSL keystore file: " + ksf);
            }
            if (!ksf.canRead())
            {
                throw new FileNotFoundException("Cannot read management SSL keystore file: " + ksf + ". Check permissions.");
            }
        }
        return keyStorePath;
    }

}
