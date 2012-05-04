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
package org.apache.qpid.server;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.QpidLog4JConfigurator;

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.ServerNetworkTransportConfiguration;
import org.apache.qpid.server.configuration.management.ConfigurationManagementMBean;
import org.apache.qpid.server.information.management.ServerInformationMBean;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.management.LoggingManagementMBean;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.server.protocol.MultiVersionProtocolEngineFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.Transport;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class Broker
{
    private static final Logger LOGGER = Logger.getLogger(Broker.class);

    private static final int IPV4_ADDRESS_LENGTH = 4;
    private static final char IPV4_LITERAL_SEPARATOR = '.';
    private volatile Thread _shutdownHookThread;

    protected static class InitException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        InitException(String msg, Throwable cause)
        {
            super(msg, cause);
        }
    }

    public void shutdown()
    {
        try
        {
            removeShutdownHook();
        }
        finally
        {
            ApplicationRegistry.remove();
        }
    }

    public void startup() throws Exception
    {
        startup(new BrokerOptions());
    }

    public void startup(final BrokerOptions options) throws Exception
    {
        try
        {
            CurrentActor.set(new BrokerActor(new SystemOutMessageLogger()));
            startupImpl(options);
            addShutdownHook();
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    private void startupImpl(final BrokerOptions options) throws Exception
    {
        final String qpidHome = options.getQpidHome();
        final File configFile = getConfigFile(options.getConfigFile(),
                                    BrokerOptions.DEFAULT_CONFIG_FILE, qpidHome, true);

        CurrentActor.get().message(BrokerMessages.CONFIG(configFile.getAbsolutePath()));

        File logConfigFile = getConfigFile(options.getLogConfigFile(),
                                    BrokerOptions.DEFAULT_LOG_CONFIG_FILE, qpidHome, false);

        configureLogging(logConfigFile, options.getLogWatchFrequency());

        ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(configFile, options.getBundleContext());
        ServerConfiguration serverConfig = config.getConfiguration();
        updateManagementPorts(serverConfig, options.getJmxPortRegistryServer(), options.getJmxPortConnectorServer());

        ApplicationRegistry.initialise(config);

        // We have already loaded the BrokerMessages class by this point so we
        // need to refresh the locale setting incase we had a different value in
        // the configuration.
        BrokerMessages.reload();

        // AR.initialise() sets and removes its own actor so we now need to set the actor
        // for the remainder of the startup, and the default actor if the stack is empty
        CurrentActor.set(new BrokerActor(config.getCompositeStartupMessageLogger()));
        CurrentActor.setDefault(new BrokerActor(config.getRootMessageLogger()));
        GenericActor.setDefaultMessageLogger(config.getRootMessageLogger());

        try
        {
            configureLoggingManagementMBean(logConfigFile, options.getLogWatchFrequency());

            ConfigurationManagementMBean configMBean = new ConfigurationManagementMBean();
            configMBean.register();

            ServerInformationMBean sysInfoMBean = new ServerInformationMBean(config);
            sysInfoMBean.register();

            Set<Integer> ports = new HashSet<Integer>(options.getPorts());
            if(ports.isEmpty())
            {
                parsePortList(ports, serverConfig.getPorts());
            }

            Set<Integer> sslPorts = new HashSet<Integer>(options.getSSLPorts());
            if(sslPorts.isEmpty())
            {
                parsePortList(sslPorts, serverConfig.getSSLPorts());
            }

            Set<Integer> exclude_1_0 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v1_0));
            if(exclude_1_0.isEmpty())
            {
                parsePortList(exclude_1_0, serverConfig.getPortExclude10());
            }

            Set<Integer> exclude_0_10 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_10));
            if(exclude_0_10.isEmpty())
            {
                parsePortList(exclude_0_10, serverConfig.getPortExclude010());
            }

            Set<Integer> exclude_0_9_1 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_9_1));
            if(exclude_0_9_1.isEmpty())
            {
                parsePortList(exclude_0_9_1, serverConfig.getPortExclude091());
            }

            Set<Integer> exclude_0_9 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_9));
            if(exclude_0_9.isEmpty())
            {
                parsePortList(exclude_0_9, serverConfig.getPortExclude09());
            }

            Set<Integer> exclude_0_8 = new HashSet<Integer>(options.getExcludedPorts(ProtocolExclusion.v0_8));
            if(exclude_0_8.isEmpty())
            {
                parsePortList(exclude_0_8, serverConfig.getPortExclude08());
            }

            String bindAddr = options.getBind();
            if (bindAddr == null)
            {
                bindAddr = serverConfig.getBind();
            }

            InetAddress bindAddress;
            if (bindAddr.equals(WILDCARD_ADDRESS))
            {
                bindAddress = null;
            }
            else
            {
                bindAddress = InetAddress.getByName(bindAddr);
            }

            final AmqpProtocolVersion defaultSupportedProtocolReply = serverConfig.getDefaultSupportedProtocolReply();

            if (!serverConfig.getSSLOnly())
            {
                for(int port : ports)
                {
                    final InetSocketAddress inetSocketAddress = new InetSocketAddress(bindAddress, port);

                    final Set<AmqpProtocolVersion> supported =
                                    getSupportedVersions(port, exclude_1_0, exclude_0_10, exclude_0_9_1, exclude_0_9,
                                                         exclude_0_8, serverConfig);

                    final NetworkTransportConfiguration settings =
                                    new ServerNetworkTransportConfiguration(serverConfig, inetSocketAddress, Transport.TCP);

                    final IncomingNetworkTransport transport = Transport.getIncomingTransportInstance();
                    final MultiVersionProtocolEngineFactory protocolEngineFactory =
                                    new MultiVersionProtocolEngineFactory(supported, defaultSupportedProtocolReply);

                    transport.accept(settings, protocolEngineFactory, null);

                    ApplicationRegistry.getInstance().addAcceptor(inetSocketAddress,
                                    new QpidAcceptor(transport,"TCP"));
                    CurrentActor.get().message(BrokerMessages.LISTENING("TCP", port));
                }
            }

            if (serverConfig.getEnableSSL())
            {
                final String keystorePath = serverConfig.getConnectorKeyStorePath();
                final String keystorePassword = serverConfig.getConnectorKeyStorePassword();
                final String keyManagerFactoryAlgorithm = serverConfig.getConnectorKeyManagerFactoryAlgorithm();
                final SSLContext sslContext = SSLContextFactory.buildServerContext(keystorePath, keystorePassword, keyManagerFactoryAlgorithm);

                for(int sslPort : sslPorts)
                {
                    final InetSocketAddress inetSocketAddress = new InetSocketAddress(bindAddress, sslPort);

                    final Set<AmqpProtocolVersion> supported =
                                    getSupportedVersions(sslPort, exclude_1_0, exclude_0_10, exclude_0_9_1,
                                                         exclude_0_9, exclude_0_8, serverConfig);
                    final NetworkTransportConfiguration settings =
                        new ServerNetworkTransportConfiguration(serverConfig, inetSocketAddress, Transport.TCP);

                    final IncomingNetworkTransport transport = Transport.getIncomingTransportInstance();
                    final MultiVersionProtocolEngineFactory protocolEngineFactory =
                                    new MultiVersionProtocolEngineFactory(supported, defaultSupportedProtocolReply);

                    transport.accept(settings, protocolEngineFactory, sslContext);

                    ApplicationRegistry.getInstance().addAcceptor(inetSocketAddress,
                            new QpidAcceptor(transport,"TCP"));
                    CurrentActor.get().message(BrokerMessages.LISTENING("TCP/SSL", sslPort));
                }
            }

            CurrentActor.get().message(BrokerMessages.READY());
        }
        finally
        {
            // Startup is complete so remove the AR initialised Startup actor
            CurrentActor.remove();
        }
    }

    private static Set<AmqpProtocolVersion> getSupportedVersions(final int port,
                                                                 final Set<Integer> exclude_1_0,
                                                                 final Set<Integer> exclude_0_10,
                                                                 final Set<Integer> exclude_0_9_1,
                                                                 final Set<Integer> exclude_0_9,
                                                                 final Set<Integer> exclude_0_8,
                                                                 final ServerConfiguration serverConfig)
    {
        final EnumSet<AmqpProtocolVersion> supported = EnumSet.allOf(AmqpProtocolVersion.class);

        if(exclude_1_0.contains(port) || !serverConfig.isAmqp10enabled())
        {
            supported.remove(AmqpProtocolVersion.v1_0_0);
        }
        if(exclude_0_10.contains(port) || !serverConfig.isAmqp010enabled())
        {
            supported.remove(AmqpProtocolVersion.v0_10);
        }
        if(exclude_0_9_1.contains(port) || !serverConfig.isAmqp091enabled())
        {
            supported.remove(AmqpProtocolVersion.v0_9_1);
        }
        if(exclude_0_9.contains(port) || !serverConfig.isAmqp09enabled())
        {
            supported.remove(AmqpProtocolVersion.v0_9);
        }
        if(exclude_0_8.contains(port) || !serverConfig.isAmqp08enabled())
        {
            supported.remove(AmqpProtocolVersion.v0_8);
        }

        return supported;
    }

    private File getConfigFile(final String fileName,
                               final String defaultFileName,
                               final String qpidHome, boolean throwOnFileNotFound) throws InitException
    {
        File configFile = null;
        if (fileName != null)
        {
            configFile = new File(fileName);
        }
        else
        {
            configFile = new File(qpidHome, defaultFileName);
        }

        if (!configFile.exists() && throwOnFileNotFound)
        {
            String error = "File " + fileName + " could not be found. Check the file exists and is readable.";

            if (qpidHome == null)
            {
                error = error + "\nNote: " + BrokerOptions.QPID_HOME + " is not set.";
            }

            throw new InitException(error, null);
        }

        return configFile;
    }

    public static void parsePortList(Set<Integer> output, List<?> ports) throws InitException
    {
        if(ports != null)
        {
            for(Object o : ports)
            {
                try
                {
                    output.add(Integer.parseInt(String.valueOf(o)));
                }
                catch (NumberFormatException e)
                {
                    throw new InitException("Invalid port: " + o, e);
                }
            }
        }
    }

    /**
     * Update the configuration data with the management port.
     * @param configuration
     * @param registryServerPort The string from the command line
     */
    private void updateManagementPorts(ServerConfiguration configuration, Integer registryServerPort, Integer connectorServerPort)
    {
        if (registryServerPort != null)
        {
            try
            {
                configuration.setJMXPortRegistryServer(registryServerPort);
            }
            catch (NumberFormatException e)
            {
                throw new InitException("Invalid management (registry server) port: " + registryServerPort, null);
            }
        }
        if (connectorServerPort != null)
        {
            try
            {
                configuration.setJMXPortConnectorServer(connectorServerPort);
            }
            catch (NumberFormatException e)
            {
                throw new InitException("Invalid management (connector server) port: " + connectorServerPort, null);
            }
        }
    }

    private void configureLogging(File logConfigFile, long logWatchTime) throws InitException, IOException
    {
        if (logConfigFile.exists() && logConfigFile.canRead())
        {
            CurrentActor.get().message(BrokerMessages.LOG_CONFIG(logConfigFile.getAbsolutePath()));

            if (logWatchTime > 0)
            {
                System.out.println("log file " + logConfigFile.getAbsolutePath() + " will be checked for changes every "
                        + logWatchTime + " seconds");
                // log4j expects the watch interval in milliseconds
                try
                {
                    QpidLog4JConfigurator.configureAndWatch(logConfigFile.getPath(), logWatchTime * 1000);
                }
                catch (Exception e)
                {
                    throw new InitException(e.getMessage(),e);
                }
            }
            else
            {
                try
                {
                    QpidLog4JConfigurator.configure(logConfigFile.getPath());
                }
                catch (Exception e)
                {
                    throw new InitException(e.getMessage(),e);
                }
            }
        }
        else
        {
            System.err.println("Logging configuration error: unable to read file " + logConfigFile.getAbsolutePath());
            System.err.println("Using the fallback internal fallback-log4j.properties configuration");

            InputStream propsFile = this.getClass().getResourceAsStream("/fallback-log4j.properties");
            if(propsFile == null)
            {
                throw new IOException("Unable to load the fallback internal fallback-log4j.properties configuration file");
            }
            else
            {
                try
                {
                    Properties fallbackProps = new Properties();
                    fallbackProps.load(propsFile);
                    PropertyConfigurator.configure(fallbackProps);
                }
                finally
                {
                    propsFile.close();
                }
            }
        }
    }

    private void configureLoggingManagementMBean(File logConfigFile, int logWatchTime) throws Exception
    {
        LoggingManagementMBean blm = new LoggingManagementMBean(logConfigFile.getPath(),logWatchTime);

        blm.register();
    }

    private void addShutdownHook()
    {
        Thread shutdownHookThread = new Thread(new ShutdownService());
        shutdownHookThread.setName("QpidBrokerShutdownHook");

        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        _shutdownHookThread = shutdownHookThread;

        LOGGER.debug("Added shutdown hook");
    }

    private void removeShutdownHook()
    {
        Thread shutdownThread = _shutdownHookThread;

        //if there is a shutdown thread and we aren't it, we should remove it
        if(shutdownThread != null && !(Thread.currentThread() == shutdownThread))
        {
            LOGGER.debug("Removing shutdown hook");

            _shutdownHookThread = null;

            boolean removed = false;
            try
            {
                removed = Runtime.getRuntime().removeShutdownHook(shutdownThread);
            }
            catch(IllegalStateException ise)
            {
                //ignore, means the JVM is already shutting down
            }

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Removed shutdown hook: " + removed);
            }
        }
        else
        {
            LOGGER.debug("Skipping shutdown hook removal as there either isnt one, or we are it.");
        }
    }

    private class ShutdownService implements Runnable
    {
        public void run()
        {
            LOGGER.debug("Shutdown hook running");
            Broker.this.shutdown();
        }
    }
}
