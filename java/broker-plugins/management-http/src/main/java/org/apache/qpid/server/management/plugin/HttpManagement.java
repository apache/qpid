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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.AbstractServlet;
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
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.util.MapValueConverter;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class HttpManagement extends AbstractPluginAdapter
{
    private final Logger _logger = Logger.getLogger(HttpManagement.class);

    // 10 minutes by default
    public static final int DEFAULT_TIMEOUT_IN_SECONDS = 60 * 10;
    public static final boolean DEFAULT_HTTP_BASIC_AUTHENTICATION_ENABLED = false;
    public static final boolean DEFAULT_HTTPS_BASIC_AUTHENTICATION_ENABLED = true;
    public static final boolean DEFAULT_HTTP_SASL_AUTHENTICATION_ENABLED = true;
    public static final boolean DEFAULT_HTTPS_SASL_AUTHENTICATION_ENABLED = true;
    public static final String DEFAULT_NAME = "httpManagement";

    public static final String TIME_OUT = "sessionTimeout";
    public static final String HTTP_BASIC_AUTHENTICATION_ENABLED = "httpBasicAuthenticationEnabled";
    public static final String HTTPS_BASIC_AUTHENTICATION_ENABLED = "httpsBasicAuthenticationEnabled";
    public static final String HTTP_SASL_AUTHENTICATION_ENABLED = "httpSaslAuthenticationEnabled";
    public static final String HTTPS_SASL_AUTHENTICATION_ENABLED = "httpsSaslAuthenticationEnabled";

    public static final String PLUGIN_TYPE = "MANAGEMENT-HTTP";

    @SuppressWarnings("serial")
    private static final Collection<String> AVAILABLE_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>(Plugin.AVAILABLE_ATTRIBUTES)
    {{
        add(HTTP_BASIC_AUTHENTICATION_ENABLED);
        add(HTTPS_BASIC_AUTHENTICATION_ENABLED);
        add(HTTP_SASL_AUTHENTICATION_ENABLED);
        add(HTTPS_SASL_AUTHENTICATION_ENABLED);
        add(TIME_OUT);
        add(PluginFactory.PLUGIN_TYPE);
    }});

    public static final String ENTRY_POINT_PATH = "/management";

    private static final String OPERATIONAL_LOGGING_NAME = "Web";


    @SuppressWarnings("serial")
    public static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>()
            {{
                put(HTTP_BASIC_AUTHENTICATION_ENABLED, DEFAULT_HTTP_BASIC_AUTHENTICATION_ENABLED);
                put(HTTPS_BASIC_AUTHENTICATION_ENABLED, DEFAULT_HTTPS_BASIC_AUTHENTICATION_ENABLED);
                put(HTTP_SASL_AUTHENTICATION_ENABLED, DEFAULT_HTTP_SASL_AUTHENTICATION_ENABLED);
                put(HTTPS_SASL_AUTHENTICATION_ENABLED, DEFAULT_HTTPS_SASL_AUTHENTICATION_ENABLED);
                put(TIME_OUT, DEFAULT_TIMEOUT_IN_SECONDS);
                put(NAME, DEFAULT_NAME);
            }});

    @SuppressWarnings("serial")
    private static final Map<String, Class<?>> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Class<?>>(){{
        put(HTTP_BASIC_AUTHENTICATION_ENABLED, Boolean.class);
        put(HTTPS_BASIC_AUTHENTICATION_ENABLED, Boolean.class);
        put(HTTP_SASL_AUTHENTICATION_ENABLED, Boolean.class);
        put(HTTPS_SASL_AUTHENTICATION_ENABLED, Boolean.class);
        put(NAME, String.class);
        put(TIME_OUT, Integer.class);
        put(PluginFactory.PLUGIN_TYPE, String.class);
    }});

    private final Broker _broker;

    private Server _server;

    public HttpManagement(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), broker.getTaskExecutor());
        _broker = broker;
        addParent(Broker.class, broker);
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        if(desiredState == State.ACTIVE)
        {
            start();
            return true;
        }
        else if(desiredState == State.STOPPED)
        {
            stop();
            return true;
        }
        return false;
    }

    private void start()
    {
        CurrentActor.get().message(ManagementConsoleMessages.STARTUP(OPERATIONAL_LOGGING_NAME));

        Collection<Port> httpPorts = getHttpPorts(_broker.getPorts());
        _server = createServer(httpPorts);
        try
        {
            _server.start();
            logOperationalListenMessages(_server);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to start http management on ports " + httpPorts);
        }

        CurrentActor.get().message(ManagementConsoleMessages.READY(OPERATIONAL_LOGGING_NAME));
    }

    private void stop()
    {
        if (_server != null)
        {
            try
            {
                _server.stop();
                logOperationalShutdownMessage(_server);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Failed to stop http management on port " + getHttpPorts(_broker.getPorts()));
            }
        }

        CurrentActor.get().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    /** Added for testing purposes */
    Broker getBroker()
    {
        return _broker;
    }

    /** Added for testing purposes */
    int getSessionTimeout()
    {
        return (Integer)getAttribute(TIME_OUT);
    }

    private boolean isManagementHttp(Port port)
    {
        return port.getProtocols().contains(Protocol.HTTP) || port.getProtocols().contains(Protocol.HTTPS);
    }

    @SuppressWarnings("unchecked")
    private Server createServer(Collection<Port> ports)
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Starting up web server on " + ports);
        }

        Server server = new Server();
        for (Port port : ports)
        {
            final Collection<Protocol> protocols = port.getProtocols();
            Connector connector = null;

            //TODO: what to do if protocol HTTP and transport SSL?
            if (protocols.contains(Protocol.HTTP))
            {
                connector = new SelectChannelConnector();
            }
            else if (protocols.contains(Protocol.HTTPS))
            {
                KeyStore keyStore = _broker.getDefaultKeyStore();
                if (keyStore == null)
                {
                    throw new IllegalConfigurationException("Key store is not configured. Cannot start management on HTTPS port without keystore");
                }
                String keyStorePath = (String)keyStore.getAttribute(KeyStore.PATH);
                String keyStorePassword = keyStore.getPassword();
                validateKeystoreParameters(keyStorePath, keyStorePassword);

                SslContextFactory factory = new SslContextFactory();
                factory.setKeyStorePath(keyStorePath);
                factory.setKeyStorePassword(keyStorePassword);

                connector = new SslSocketConnector(factory);
            }
            else
            {
                throw new IllegalArgumentException("Unexpected protocol " + protocols);
            }
            connector.setPort(port.getPort());
            server.addConnector(connector);
        }

        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
        root.setContextPath("/");
        server.setHandler(root);

        // set servlet context attributes for broker and configuration
        root.getServletContext().setAttribute(AbstractServlet.ATTR_BROKER, _broker);
        root.getServletContext().setAttribute(AbstractServlet.ATTR_MANAGEMENT, this);

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

        root.addServlet(new ServletHolder(new StructureServlet()), "/rest/structure");
        root.addServlet(new ServletHolder(new MessageServlet()), "/rest/message/*");
        root.addServlet(new ServletHolder(new MessageContentServlet()), "/rest/message-content/*");

        root.addServlet(new ServletHolder(new LogRecordsServlet()), "/rest/logrecords");

        root.addServlet(new ServletHolder(new SaslServlet()), "/rest/sasl");

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

        sessionManager.setMaxInactiveInterval((Integer)getAttribute(TIME_OUT));

        return server;
    }

    private void addRestServlet(ServletContextHandler root, String name, Class<? extends ConfiguredObject>... hierarchy)
    {
        root.addServlet(new ServletHolder(new RestServlet(hierarchy)), "/rest/" + name + "/*");
    }

    private void validateKeystoreParameters(String keyStorePath, String password)
    {
        if (keyStorePath == null)
        {
            throw new RuntimeException("Management SSL keystore path not defined, unable to start SSL protected HTTP connector");
        }
        if (password == null)
        {
            throw new RuntimeException("Management SSL keystore password, unable to start SSL protected HTTP connector");
        }
        File ksf = new File(keyStorePath);
        if (!ksf.exists())
        {
            throw new RuntimeException("Cannot find management SSL keystore file: " + ksf);
        }
        if (!ksf.canRead())
        {
            throw new RuntimeException("Cannot read management SSL keystore file: " + ksf + ". Check permissions.");
        }
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

    private Collection<Port> getHttpPorts(Collection<Port> ports)
    {
        Collection<Port> httpPorts = new HashSet<Port>();
        for (Port port : ports)
        {
            if (isManagementHttp(port))
            {
                httpPorts.add(port);
            }
        }
        return httpPorts;
    }


    @Override
    public String getName()
    {
        return (String)getAttribute(NAME);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return Collections.unmodifiableCollection(AVAILABLE_ATTRIBUTES);
    }

    public boolean isHttpsSaslAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTPS_SASL_AUTHENTICATION_ENABLED);
    }

    public boolean isHttpSaslAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTP_SASL_AUTHENTICATION_ENABLED);
    }

    public boolean isHttpsBasicAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTPS_BASIC_AUTHENTICATION_ENABLED);
    }

    public boolean isHttpBasicAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTP_BASIC_AUTHENTICATION_ENABLED);
    }

}
