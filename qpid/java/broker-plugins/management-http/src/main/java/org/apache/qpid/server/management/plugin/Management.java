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
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.LogRecordsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.LogoutServlet;
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
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.eclipse.jetty.server.Connector;
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

    public static final String ENTRY_POINT_PATH = "/management";

    private static final String OPERATIONAL_LOGGING_NAME = "Web";

    private final Broker _broker;

    private final Collection<Server> _servers = new ArrayList<Server>();

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

        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
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

        addRestServlet(root, "broker");
        addRestServlet(root, "virtualhost", VirtualHost.class);
        addRestServlet(root, "authenticationprovider", AuthenticationProvider.class);
        addRestServlet(root, "user", AuthenticationProvider.class, User.class);
        addRestServlet(root, "groupprovider", GroupProvider.class);
        addRestServlet(root, "group", GroupProvider.class, Group.class);
        addRestServlet(root, "groupmember", GroupProvider.class, Group.class, GroupMember.class);
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

        root.addServlet(new ServletHolder(new DefinedFileServlet("index.html")), ENTRY_POINT_PATH);
        root.addServlet(new ServletHolder(new LogoutServlet()), "/logout");

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

        sessionManager.setMaxInactiveInterval(appRegistry.getConfiguration().getHTTPManagementSessionTimeout());

        return server;
    }

    private void addRestServlet(ServletContextHandler root, String name, Class<? extends ConfiguredObject>... hierarchy)
    {
        root.addServlet(new ServletHolder(new RestServlet(_broker, hierarchy)), "/rest/" + name + "/*");
    }

    public void start() throws Exception
    {
        CurrentActor.get().message(ManagementConsoleMessages.STARTUP(OPERATIONAL_LOGGING_NAME));

        for (Server server : _servers)
        {
            server.start();

            logOperationalListenMessages(server);
        }

        CurrentActor.get().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
    }

    public void stop() throws Exception
    {
        for (Server server : _servers)
        {
            logOperationalShutdownMessage(server);

            server.stop();
        }

        CurrentActor.get().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    private String getKeyStorePath(IApplicationRegistry appRegistry) throws ConfigurationException, FileNotFoundException
    {
        String keyStorePath = System.getProperty("javax.net.ssl.keyStore");
        if (keyStorePath == null)
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

    private void logOperationalListenMessages(Server server)
    {
        Connector[] connectors = server.getConnectors();
        for (Connector connector : connectors)
        {
            CurrentActor.get().message(ManagementConsoleMessages.LISTENING(stringifyConnectorScheme(connector), connector.getPort()));
            if (connector instanceof SslSocketConnector)
            {
                SslContextFactory sslContextFactory = ((SslSocketConnector)connector).getSslContextFactory();
                if (sslContextFactory != null && sslContextFactory.getKeyStorePath() != null)
                {
                    CurrentActor.get().message(ManagementConsoleMessages.SSL_KEYSTORE(sslContextFactory.getKeyStorePath()));
                }
            }
        }
    }

    private void logOperationalShutdownMessage(Server server)
    {
        Connector[] connectors = server.getConnectors();
        for (Connector connector : connectors)
        {
            CurrentActor.get().message(ManagementConsoleMessages.SHUTTING_DOWN(stringifyConnectorScheme(connector), connector.getPort()));
        }
    }

    private String stringifyConnectorScheme(Connector connector)
    {
        return connector instanceof SslSocketConnector ? "HTTPS" : "HTTP";
    }


}
