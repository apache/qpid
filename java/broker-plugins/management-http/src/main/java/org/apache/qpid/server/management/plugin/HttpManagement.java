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

import java.lang.reflect.Type;
import java.net.SocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ManagementConsoleMessages;
import org.apache.qpid.server.management.plugin.filter.ForbiddingAuthorisationFilter;
import org.apache.qpid.server.management.plugin.filter.RedirectingAuthorisationFilter;
import org.apache.qpid.server.management.plugin.servlet.DefinedFileServlet;
import org.apache.qpid.server.management.plugin.servlet.FileServlet;
import org.apache.qpid.server.management.plugin.servlet.LogFileServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.HelperServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.LogFileListingServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.LogRecordsServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.LogoutServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.MessageContentServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.MessageServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.PreferencesServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.UserPreferencesServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.RestServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.SaslServlet;
import org.apache.qpid.server.management.plugin.servlet.rest.StructureServlet;
import org.apache.qpid.server.model.AccessControlProvider;
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
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.adapter.AbstractPluginAdapter;
import org.apache.qpid.server.plugin.PluginFactory;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.util.MapValueConverter;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class HttpManagement extends AbstractPluginAdapter implements HttpManagementConfiguration
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
    private static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(HTTP_BASIC_AUTHENTICATION_ENABLED, Boolean.class);
        put(HTTPS_BASIC_AUTHENTICATION_ENABLED, Boolean.class);
        put(HTTP_SASL_AUTHENTICATION_ENABLED, Boolean.class);
        put(HTTPS_SASL_AUTHENTICATION_ENABLED, Boolean.class);
        put(NAME, String.class);
        put(TIME_OUT, Integer.class);
        put(PluginFactory.PLUGIN_TYPE, String.class);
    }});

    private static final String JSESSIONID_COOKIE_PREFIX = "JSESSIONID_";

    private Server _server;

    public HttpManagement(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), broker);
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

        Collection<Port> httpPorts = getHttpPorts(getBroker().getPorts());
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
                throw new RuntimeException("Failed to stop http management on port " + getHttpPorts(getBroker().getPorts()));
            }
        }

        CurrentActor.get().message(ManagementConsoleMessages.STOPPED(OPERATIONAL_LOGGING_NAME));
    }

    /** Added for testing purposes */
    int getSessionTimeout()
    {
        return (Integer)getAttribute(TIME_OUT);
    }

    @SuppressWarnings("unchecked")
    private Server createServer(Collection<Port> ports)
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Starting up web server on " + ports);
        }

        Server server = new Server();
        int lastPort = -1;
        for (Port port : ports)
        {
            if (State.QUIESCED.equals(port.getActualState()))
            {
                continue;
            }

            Connector connector = null;

            Collection<Transport> transports = port.getTransports();
            if (!transports.contains(Transport.SSL))
            {
                connector = new SelectChannelConnector();
            }
            else if (transports.contains(Transport.SSL))
            {
                KeyStore keyStore = port.getKeyStore();
                if (keyStore == null)
                {
                    throw new IllegalConfigurationException("Key store is not configured. Cannot start management on HTTPS port without keystore");
                }
                SslContextFactory factory = new SslContextFactory();
                try
                {
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(keyStore.getKeyManagers(), null, null);
                    factory.setSslContext(sslContext);
                }
                catch (GeneralSecurityException e)
                {
                    throw new RuntimeException("Cannot configure port " + port.getName() + " for transport " + Transport.SSL, e);
                }
                connector = new SslSocketConnector(factory);
            }
            else
            {
                throw new IllegalArgumentException("Unexpected transport on port " + port.getName() + ":" + transports);
            }
            lastPort = port.getPort();
            connector.setPort(port.getPort());
            server.addConnector(connector);
        }

        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
        root.setContextPath("/");
        server.setHandler(root);

        // set servlet context attributes for broker and configuration
        root.getServletContext().setAttribute(HttpManagementUtil.ATTR_BROKER, getBroker());
        root.getServletContext().setAttribute(HttpManagementUtil.ATTR_MANAGEMENT_CONFIGURATION, this);

        FilterHolder restAuthorizationFilter = new FilterHolder(new ForbiddingAuthorisationFilter());
        restAuthorizationFilter.setInitParameter(ForbiddingAuthorisationFilter.INIT_PARAM_ALLOWED, "/rest/sasl");
        root.addFilter(restAuthorizationFilter, "/rest/*", EnumSet.of(DispatcherType.REQUEST));
        root.addFilter(new FilterHolder(new RedirectingAuthorisationFilter()), HttpManagementUtil.ENTRY_POINT_PATH, EnumSet.of(DispatcherType.REQUEST));
        root.addFilter(new FilterHolder(new RedirectingAuthorisationFilter()), "/index.html", EnumSet.of(DispatcherType.REQUEST));
        root.addFilter(new FilterHolder(new RedirectingAuthorisationFilter()), "/", EnumSet.of(DispatcherType.REQUEST));

        addRestServlet(root, "broker");
        addRestServlet(root, "virtualhost", VirtualHost.class);
        addRestServlet(root, "authenticationprovider", AuthenticationProvider.class);
        addRestServlet(root, "accesscontrolprovider", AccessControlProvider.class);
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
        addRestServlet(root, "keystore", KeyStore.class);
        addRestServlet(root, "truststore", TrustStore.class);
        addRestServlet(root, "plugin", Plugin.class);
        addRestServlet(root, "preferencesprovider", AuthenticationProvider.class, PreferencesProvider.class);

        root.addServlet(new ServletHolder(new UserPreferencesServlet()), "/rest/userpreferences/*");
        root.addServlet(new ServletHolder(new PreferencesServlet()), "/rest/preferences");
        root.addServlet(new ServletHolder(new StructureServlet()), "/rest/structure");
        root.addServlet(new ServletHolder(new MessageServlet()), "/rest/message/*");
        root.addServlet(new ServletHolder(new MessageContentServlet()), "/rest/message-content/*");

        root.addServlet(new ServletHolder(new LogRecordsServlet()), "/rest/logrecords");

        root.addServlet(new ServletHolder(new SaslServlet()), "/rest/sasl");

        root.addServlet(new ServletHolder(new DefinedFileServlet("index.html")), HttpManagementUtil.ENTRY_POINT_PATH);
        root.addServlet(new ServletHolder(new DefinedFileServlet("index.html")), "/");
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
        root.addServlet(new ServletHolder(new HelperServlet()), "/rest/helper");
        root.addServlet(new ServletHolder(new LogFileListingServlet()), "/rest/logfiles");
        root.addServlet(new ServletHolder(new LogFileServlet()), "/rest/logfile");

        String[] timeZoneFiles = {"africa", "antarctica", "asia", "australasia", "backward",
                "etcetera", "europe", "northamerica", "pacificnew",  "southamerica"};
        for (String timeZoneFile : timeZoneFiles)
        {
            root.addServlet(new ServletHolder(FileServlet.INSTANCE), "/dojo/dojox/date/zoneinfo/" + timeZoneFile);
        }

        final SessionManager sessionManager = root.getSessionHandler().getSessionManager();
        sessionManager.setSessionCookie(JSESSIONID_COOKIE_PREFIX + lastPort);
        sessionManager.setMaxInactiveInterval((Integer)getAttribute(TIME_OUT));

        return server;
    }

    private void addRestServlet(ServletContextHandler root, String name, Class<? extends ConfiguredObject>... hierarchy)
    {
        root.addServlet(new ServletHolder(new RestServlet(hierarchy)), "/rest/" + name + "/*");
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
            if (port.getProtocols().contains(Protocol.HTTP))
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

    @Override
    public boolean isHttpsSaslAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTPS_SASL_AUTHENTICATION_ENABLED);
    }

    @Override
    public boolean isHttpSaslAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTP_SASL_AUTHENTICATION_ENABLED);
    }

    @Override
    public boolean isHttpsBasicAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTPS_BASIC_AUTHENTICATION_ENABLED);
    }

    @Override
    public boolean isHttpBasicAuthenticationEnabled()
    {
        return (Boolean)getAttribute(HTTP_BASIC_AUTHENTICATION_ENABLED);
    }

    @Override
    public SubjectCreator getSubjectCreator(SocketAddress localAddress)
    {
        return getBroker().getSubjectCreator(localAddress);
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> convertedAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        validateAttributes(convertedAttributes);

        super.changeAttributes(convertedAttributes);
    }

    private void validateAttributes(Map<String, Object> convertedAttributes)
    {
        if(convertedAttributes.containsKey(HttpManagement.NAME))
        {
            String newName = (String) convertedAttributes.get(HttpManagement.NAME);
            if(!getName().equals(newName))
            {
                throw new IllegalConfigurationException("Changing the name of http management plugin is not allowed");
            }
        }
        if (convertedAttributes.containsKey(TIME_OUT))
        {
            Number value = (Number) convertedAttributes.get(TIME_OUT);
            if (value == null || value.longValue() < 0)
            {
                throw new IllegalConfigurationException("Only positive integer value can be specified for the session time out attribute");
            }
        }
    }

}
