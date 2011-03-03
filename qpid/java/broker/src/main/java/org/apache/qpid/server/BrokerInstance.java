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

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.xml.QpidLog4JConfigurator;
import org.apache.qpid.BrokerOptions;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.protocol.ReceiverFactory;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.management.ConfigurationManagementMBean;
import org.apache.qpid.server.information.management.ServerInformationMBean;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.management.LoggingManagementMBean;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.protocol.BrokerReceiverFactory;
import org.apache.qpid.server.protocol.BrokerReceiverFactory.VERSION;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.ConnectionSettings;
import org.apache.qpid.transport.network.IncomingNetworkTransport;
import org.apache.qpid.transport.network.NetworkTransport;
import org.apache.qpid.transport.network.Transport;

public class BrokerInstance
{
    private static Logger _logger;

    public class InitException extends Exception
    {
        InitException(String msg, Throwable cause)
        {
            super(msg, cause);
        }
        
        InitException(String msg)
        {
            super(msg);
        }
    }

    public void shutdown()
    {
        ApplicationRegistry.remove();
    }
    
    public void startup(BrokerOptions options) throws Exception
	{
        //if the -Dlog4j.configuration property has not been set, enable the init override
        //to stop Log4J wondering off and picking up the first log4j.xml/properties file it
        //finds from the classpath when we get the first Loggers
        if (System.getProperty("log4j.configuration") == null)
        {
            System.setProperty("log4j.defaultInitOverride", "true");
        }
        _logger = Logger.getLogger(BrokerInstance.class);
        CurrentActor.set(new BrokerActor(new SystemOutMessageLogger()));

        String home = System.getProperty(BrokerOptions.QPID_HOME);
        File defaultConfigFile = new File(home, BrokerOptions.DEFAULT_CONFIG_FILE);
        File configFile = new File(options.getValue(BrokerOptions.CONFIG, defaultConfigFile.getPath()));
        if (!configFile.exists())
        {
            String error = "File " + configFile + " could not be found. Check the file exists and is readable.";
            if (home == null)
            {
                error = error + "\nNote: " + BrokerOptions.QPID_HOME + " is not set.";
            }

            throw new InitException(error, null);
        }
        else
        {
            CurrentActor.get().message(BrokerMessages.CONFIG(configFile.getAbsolutePath()));
        }


        String watch = options.getValue(BrokerOptions.WATCH);
        int logWatchTime = 0;
        try
        {
            logWatchTime = Integer.parseInt(watch);
        }
        catch (NumberFormatException e)
        {
            System.err.println("Log watch configuration value of " + watch + " is invalid. Must be "
                               + "a non-negative integer. Using default of zero (no watching configured");
        }

        String log4j = options.getValue(BrokerOptions.LOG4J, System.getProperty("log4j.configuration"));
        File logConfigFile;
        if (log4j != null)
        {
            logConfigFile = new File(log4j);
            configureLogging(logConfigFile, logWatchTime);
        }
        else
        {
            File configFileDirectory = configFile.getParentFile();
            logConfigFile = new File(configFileDirectory, BrokerOptions.DEFAULT_LOG_CONFIG_FILENAME);
            configureLogging(logConfigFile, logWatchTime);
        }

        ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(configFile);
        ServerConfiguration serverConfig = config.getConfiguration();

        String management = options.getValue(BrokerOptions.MANAGEMENT);
        updateManagementPort(serverConfig, management);

        // Application registry initialise
        ApplicationRegistry.initialise(config);

        // We have already loaded the BrokerMessages class by this point so we
        // need to refresh the locale setting in case we had a different value in
        // the configuration.
        BrokerMessages.reload();

        // AR.initialise() sets and removes its own actor so we now need to set the actor
        // for the remainder of the startup, and the default actor if the stack is empty
        CurrentActor.set(new BrokerActor(config.getCompositeStartupMessageLogger()));
        CurrentActor.setDefault(new BrokerActor(config.getRootMessageLogger()));
        GenericActor.setDefaultMessageLogger(config.getRootMessageLogger());

        try
        {
            configureLoggingManagementMBean(logConfigFile, logWatchTime);

            ConfigurationManagementMBean configMBean = new ConfigurationManagementMBean();
            configMBean.register();

            ServerInformationMBean sysInfoMBean = new ServerInformationMBean(QpidProperties.BUILD_VERSION_PROPERTY, QpidProperties.RELEASE_VERSION_PROPERTY);
            sysInfoMBean.register();

            Set<Integer> ports = new HashSet<Integer>();
            Set<Integer> exclude_0_10 = new HashSet<Integer>();
            Set<Integer> exclude_0_9_1 = new HashSet<Integer>();
            Set<Integer> exclude_0_9 = new HashSet<Integer>();
            Set<Integer> exclude_0_8 = new HashSet<Integer>();
            parsePortList(ports, options.get(BrokerOptions.PORTS, serverConfig.getPorts()));
            parsePortList(exclude_0_10, options.get(BrokerOptions.EXCLUDE_0_10, serverConfig.getPortExclude010()));
            parsePortList(exclude_0_9_1, options.get(BrokerOptions.EXCLUDE_0_9_1, serverConfig.getPortExclude091()));
            parsePortList(exclude_0_9, options.get(BrokerOptions.EXCLUDE_0_9, serverConfig.getPortExclude09()));
            parsePortList(exclude_0_8, options.get(BrokerOptions.EXCLUDE_0_8, serverConfig.getPortExclude08()));

            String protocol = options.getValue(BrokerOptions.PROTOCOL, "tcp");
            String bind = options.getValue(BrokerOptions.BIND);
            if (bind == null)
            {
                bind = serverConfig.getBind();
            }
            InetAddress address = null;

            if (bind.equals("*"))
            {
                address = new InetSocketAddress(0).getAddress();
            }
            else
            {
                address = InetAddress.getByName(bind);
            }
            String host = address.getCanonicalHostName();
            
            ConnectionSettings settings = new ConnectionSettings();
            settings.setProtocol(protocol);
            settings.setHost(bind);

            String keystorePath = serverConfig.getKeystorePath();
            String keystorePassword = serverConfig.getKeystorePassword();
            String certType = serverConfig.getCertType();
            SSLContextFactory sslFactory = null;

            if (!serverConfig.getSSLOnly())
            {
                for (int port : ports)
                {
                    IncomingNetworkTransport transport = Transport.getIncomingTransport();

                    Set<VERSION> supported = EnumSet.allOf(VERSION.class);
                    if (exclude_0_10.contains(port))
                    {
                        supported.remove(VERSION.v0_10);
                    }
                    if (exclude_0_9_1.contains(port))
                    {
                        supported.remove(VERSION.v0_9_1);
                    }
                    if (exclude_0_9.contains(port))
                    {
                        supported.remove(VERSION.v0_9);
                    }
                    if (exclude_0_8.contains(port))
                    {
                        supported.remove(VERSION.v0_8);
                    }
                    
                    settings.setPort(port);
                    
                    ReceiverFactory factory = new BrokerReceiverFactory(host, supported);
                    transport.accept(settings, factory, sslFactory);

                    config.registerTransport(port, transport);
                    CurrentActor.get().message(BrokerMessages.LISTENING(protocol.toUpperCase(), port));
                }
            }

            if (serverConfig.getEnableSSL())
            {
                IncomingNetworkTransport transport = Transport.getIncomingTransport();
                sslFactory = new SSLContextFactory(keystorePath, keystorePassword, certType);
                settings.setPort(serverConfig.getSSLPort());
                
                ReceiverFactory factory = new BrokerReceiverFactory(host, EnumSet.allOf(VERSION.class));
                transport.accept(settings, factory, sslFactory);
                
                config.registerTransport(serverConfig.getSSLPort(), transport);
                CurrentActor.get().message(BrokerMessages.LISTENING(protocol.toUpperCase() + "/SSL", serverConfig.getSSLPort()));
            }

            CurrentActor.get().message(BrokerMessages.READY());
        }
        finally
        {
            // Startup is complete so remove the AR initialised Startup actor
            CurrentActor.remove();
        }
    }
    

    private void configureLogging(File logConfigFile, int logWatchTime) throws Exception
    {
        if (logConfigFile.exists() && logConfigFile.canRead())
        {
            CurrentActor.get().message(BrokerMessages.LOG_CONFIG(logConfigFile.getAbsolutePath()));

            try
            {
                if (logWatchTime > 0)
                {
                    System.out.println("log file " + logConfigFile.getAbsolutePath() + " will be checked for changes every "
                                       + logWatchTime + " seconds");
                    // log4j expects the watch interval in milliseconds
                    QpidLog4JConfigurator.configureAndWatch(logConfigFile.getPath(), logWatchTime * 1000);
                }
                else
                {
                    QpidLog4JConfigurator.configure(logConfigFile.getPath());
                }
            }
            catch (Exception e)
            {
                throw new InitException(e.getMessage(), e);
            }
        }
        else
        {
            System.err.println("Logging configuration error: unable to read file " + logConfigFile.getAbsolutePath());
            System.err.println("Using the fallback internal log4j.properties configuration");

            InputStream propsFile = this.getClass().getResourceAsStream("/log4j.properties");
            if (propsFile == null)
            {
                throw new InitException("Unable to load the fallback internal log4j.properties configuration file");
            }
            else
            {
                Properties fallbackProps = new Properties();
                fallbackProps.load(propsFile);
                PropertyConfigurator.configure(fallbackProps);
            }
        }
    }
    
    private void parsePortList(Set<Integer> output, List<String> input) throws InitException
    {
        if (input != null)
        {
            for (String port : input)
            {
                try
                {
                    output.add(Integer.parseInt(String.valueOf(port)));
                }
                catch (NumberFormatException e)
                {
                    throw new InitException("Invalid port: " + port, e);
                }
            }
        }
    }

    /**
     * Update the configuration data with the management port.
     * @param configuration
     * @param managementPort The string from the command line
     */
    private void updateManagementPort(ServerConfiguration configuration, String managementPort)
    {
        if (managementPort != null)
        {
            try
            {
                configuration.setJMXManagementPort(Integer.parseInt(managementPort));
            }
            catch (NumberFormatException e)
            {
                _logger.warn("Invalid management port: " + managementPort + " will use:" + configuration.getJMXManagementPort(), e);
            }
        }
    }

    private void configureLoggingManagementMBean(File logConfigFile, int logWatchTime) throws Exception
    {
        LoggingManagementMBean blm = new LoggingManagementMBean(logConfigFile.getPath(),logWatchTime);

        blm.register();
    }
}
