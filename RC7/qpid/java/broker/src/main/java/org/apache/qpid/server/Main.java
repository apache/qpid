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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.FixedSizeByteBufferAllocator;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.management.JMXManagedObjectRegistry;
import org.apache.qpid.server.protocol.AMQPFastProtocolHandler;
import org.apache.qpid.server.protocol.AMQPProtocolProvider;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.server.transport.ConnectorConfiguration;
import org.apache.qpid.url.URLSyntaxException;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;

/**
 * Main entry point for AMQPD.
 *
 */
@SuppressWarnings({"AccessStaticViaInstance"})
public class Main
{
    private static final Logger _logger = Logger.getLogger(Main.class);
    public static final Logger _brokerLogger = Logger.getLogger("Qpid.Broker");

    private static final String DEFAULT_CONFIG_FILE = "etc/config.xml";

    private static final String DEFAULT_LOG_CONFIG_FILENAME = "log4j.xml";
    public static final String QPID_HOME = "QPID_HOME";
    private static final int IPV4_ADDRESS_LENGTH = 4;

    private static final char IPV4_LITERAL_SEPARATOR = '.';

    protected static class InitException extends Exception
    {
        InitException(String msg, Throwable cause)
        {
            super(msg, cause);
        }
    }

    protected final Options options = new Options();
    protected CommandLine commandLine;

    protected Main(String[] args)
    {
        setOptions(options);
        if (parseCommandline(args))
        {
            execute();
        }
    }

    protected boolean parseCommandline(String[] args)
    {
        try
        {
            commandLine = new PosixParser().parse(options, args);

            return true;
        }
        catch (ParseException e)
        {
            System.err.println("Error: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Qpid", options, true);

            return false;
        }
    }

    protected void setOptions(Options options)
    {
        Option help = new Option("h", "help", false, "print this message");
        Option version = new Option("v", "version", false, "print the version information and exit");
        Option configFile =
                OptionBuilder.withArgName("file").hasArg().withDescription("use given configuration file").withLongOpt("config")
                        .create("c");
        Option port =
                OptionBuilder.withArgName("port").hasArg()
                        .withDescription("listen on the specified port. Overrides any value in the config file")
                        .withLongOpt("port").create("p");
        Option mport =
                OptionBuilder.withArgName("mport").hasArg()
                        .withDescription("listen on the specified management port. Overrides any value in the config file")
                        .withLongOpt("mport").create("m");


        Option bind =
                OptionBuilder.withArgName("bind").hasArg()
                        .withDescription("bind to the specified address. Overrides any value in the config file")
                        .withLongOpt("bind").create("b");
        Option logconfig =
                OptionBuilder.withArgName("logconfig").hasArg()
                        .withDescription("use the specified log4j xml configuration file. By "
                                         + "default looks for a file named " + DEFAULT_LOG_CONFIG_FILENAME
                                         + " in the same directory as the configuration file").withLongOpt("logconfig").create("l");
        Option logwatchconfig =
                OptionBuilder.withArgName("logwatch").hasArg()
                        .withDescription("monitor the log file configuration file for changes. Units are seconds. "
                                         + "Zero means do not check for changes.").withLongOpt("logwatch").create("w");

        options.addOption(help);
        options.addOption(version);
        options.addOption(configFile);
        options.addOption(logconfig);
        options.addOption(logwatchconfig);
        options.addOption(port);
        options.addOption(mport);
        options.addOption(bind);
    }

    protected void execute()
    {
        // note this understands either --help or -h. If an option only has a long name you can use that but if
        // an option has a short name and a long name you must use the short name here.
        if (commandLine.hasOption("h"))
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Qpid", options, true);
        }
        else if (commandLine.hasOption("v"))
        {
            String ver = QpidProperties.getVersionString();

            StringBuilder protocol = new StringBuilder("AMQP version(s) [major.minor]: ");

            boolean first = true;
            for (ProtocolVersion pv : ProtocolVersion.getSupportedProtocolVersions())
            {
                if (first)
                {
                    first = false;
                }
                else
                {
                    protocol.append(", ");
                }

                protocol.append(pv.getMajorVersion()).append('-').append(pv.getMinorVersion());

            }

            System.out.println(ver + " (" + protocol + ")");
        }
        else
        {
            try
            {
                startup();
            }
            catch (InitException e)
            {
                System.out.println(e.getMessage());
                _brokerLogger.error("Initialisation Error : " + e.getMessage());
                shutdown(1);
            }
            catch (ConfigurationException e)
            {
                System.out.println("Error configuring message broker: " + e);
                _brokerLogger.error("Error configuring message broker: " + e);
                e.printStackTrace();
                shutdown(1);
            }
            catch (Throwable e)
            {
                System.out.println("Error initialising message broker: " + e);
                _brokerLogger.error("Error initialising message broker: " + e);
                e.printStackTrace();
                shutdown(1);
            }
        }
    }

    protected void shutdown(int status)
    {
        ApplicationRegistry.removeAll();
        System.exit(status);
    }

    protected void startup() throws InitException, ConfigurationException, Exception
    {
        final String QpidHome = System.getProperty(QPID_HOME);
        final File defaultConfigFile = new File(QpidHome, DEFAULT_CONFIG_FILE);
        final File configFile = new File(commandLine.getOptionValue("c", defaultConfigFile.getPath()));
        if (!configFile.exists())
        {
            String error = "File " + configFile + " could not be found. Check the file exists and is readable.";

            if (QpidHome == null)
            {
                error = error + "\nNote: " + QPID_HOME + " is not set.";
            }

            throw new InitException(error, null);
        }
        else
        {
            System.out.println("Using configuration file " + configFile.getAbsolutePath());
        }

        String logConfig = commandLine.getOptionValue("l");
        String logWatchConfig = commandLine.getOptionValue("w", "0");
        if (logConfig != null)
        {
            File logConfigFile = new File(logConfig);
            configureLogging(logConfigFile, logWatchConfig);
        }
        else
        {
            File configFileDirectory = configFile.getParentFile();
            File logConfigFile = new File(configFileDirectory, DEFAULT_LOG_CONFIG_FILENAME);
            configureLogging(logConfigFile, logWatchConfig);
        }

        ConfigurationFileApplicationRegistry config = new ConfigurationFileApplicationRegistry(configFile);


        updateManagementPort(config.getConfiguration(), commandLine.getOptionValue("m"));



        ApplicationRegistry.initialise(config);


        //fixme .. use QpidProperties.getVersionString when we have fixed the classpath issues
        // that are causing the broker build to pick up the wrong properties file and hence say
        // Starting Qpid Client 
        _brokerLogger.info("Starting Qpid Broker " + QpidProperties.getReleaseVersion()
                           + " build: " + QpidProperties.getBuildVersion());

        ConnectorConfiguration connectorConfig =
                ApplicationRegistry.getInstance().getConfiguredObject(ConnectorConfiguration.class);

        ByteBuffer.setUseDirectBuffers(connectorConfig.enableDirectBuffers);

        // the MINA default is currently to use the pooled allocator although this may change in future
        // once more testing of the performance of the simple allocator has been done
        if (!connectorConfig.enablePooledAllocator)
        {
            ByteBuffer.setAllocator(new FixedSizeByteBufferAllocator());
        }


        if(connectorConfig.useBiasedWrites)
        {
            System.setProperty("org.apache.qpid.use_write_biased_pool","true");
        }

        int port = connectorConfig.port;

        String portStr = commandLine.getOptionValue("p");
        if (portStr != null)
        {
            try
            {
                port = Integer.parseInt(portStr);
            }
            catch (NumberFormatException e)
            {
                throw new InitException("Invalid port: " + portStr, e);
            }
        }

        String VIRTUAL_HOSTS = "virtualhosts";

        Object virtualHosts = ApplicationRegistry.getInstance().getConfiguration().getProperty(VIRTUAL_HOSTS);

        if (virtualHosts != null)
        {
            if (virtualHosts instanceof Collection)
            {
                int totalVHosts = ((Collection) virtualHosts).size();
                for (int vhost = 0; vhost < totalVHosts; vhost++)
                {
                    setupVirtualHosts(configFile.getParent(), (String) ((List) virtualHosts).get(vhost));
                }
            }
            else
            {
                setupVirtualHosts(configFile.getParent(), (String) virtualHosts);
            }
        }

        bind(port, connectorConfig);

    }

    /**
     * Update the configuration data with the management port.
     * @param configuration
     * @param managementPort The string from the command line
     */
    private void updateManagementPort(Configuration configuration, String managementPort)
    {
        if (managementPort != null)
        {
            int mport;
            int defaultMPort = configuration.getInt(JMXManagedObjectRegistry.MANAGEMENT_PORT_CONFIG_PATH);
            try
            {
                mport = Integer.parseInt(managementPort);
                configuration.setProperty(JMXManagedObjectRegistry.MANAGEMENT_PORT_CONFIG_PATH, mport);
            }
            catch (NumberFormatException e)
            {
                _logger.warn("Invalid management port: " + managementPort + " will use default:" + defaultMPort, e);
            }
        }
    }

    protected void setupVirtualHosts(String configFileParent, String configFilePath)
            throws ConfigurationException, AMQException, URLSyntaxException
    {
        String configVar = "${conf}";

        if (configFilePath.startsWith(configVar))
        {
            configFilePath = configFileParent + configFilePath.substring(configVar.length());
        }

        if (configFilePath.indexOf(".xml") != -1)
        {
            VirtualHostConfiguration vHostConfig = new VirtualHostConfiguration(configFilePath);
            vHostConfig.performBindings();
        }
        else
        {
            // the virtualhosts value is a path. Search it for XML files.

            File virtualHostDir = new File(configFilePath);

            String[] fileNames = virtualHostDir.list();

            for (int each = 0; each < fileNames.length; each++)
            {
                if (fileNames[each].endsWith(".xml"))
                {
                    VirtualHostConfiguration vHostConfig =
                            new VirtualHostConfiguration(configFilePath + "/" + fileNames[each]);
                    vHostConfig.performBindings();
                }
            }
        }
    }

    protected void bind(int port, ConnectorConfiguration connectorConfig) throws BindException
    {
        String bindAddr = commandLine.getOptionValue("b");
        if (bindAddr == null)
        {
            bindAddr = connectorConfig.bindAddress;
        }

        try
        {
            // IoAcceptor acceptor = new SocketAcceptor(connectorConfig.processors);
            IoAcceptor acceptor = connectorConfig.createAcceptor();
            SocketAcceptorConfig sconfig = (SocketAcceptorConfig) acceptor.getDefaultConfig();
            SocketSessionConfig sc = (SocketSessionConfig) sconfig.getSessionConfig();

            sc.setReceiveBufferSize(connectorConfig.socketReceiveBufferSize);
            sc.setSendBufferSize(connectorConfig.socketWriteBuferSize);
            sc.setTcpNoDelay(connectorConfig.tcpNoDelay);

            // if we do not use the executor pool threading model we get the default leader follower
            // implementation provided by MINA
            if (connectorConfig.enableExecutorPool)
            {
                sconfig.setThreadModel(ReadWriteThreadModel.getInstance());
            }

            if (!connectorConfig.enableSSL || !connectorConfig.sslOnly)
            {
                AMQPFastProtocolHandler handler = new AMQPProtocolProvider().getHandler();
                InetSocketAddress bindAddress;
                if (bindAddr.equals("wildcard"))
                {
                    bindAddress = new InetSocketAddress(port);
                }
                else
                {
                    bindAddress = new InetSocketAddress(InetAddress.getByAddress(parseIP(bindAddr)), port);
                }

                bind(acceptor, bindAddress, handler, sconfig);

                //fixme  qpid.AMQP should be using qpidproperties to get value
                _brokerLogger.info("Qpid.AMQP listening on non-SSL address " + bindAddress);
            }

            if (connectorConfig.enableSSL)
            {
                AMQPFastProtocolHandler handler = new AMQPProtocolProvider().getHandler();
                try
                {

                    bind(acceptor, new InetSocketAddress(connectorConfig.sslPort), handler, sconfig);

                    //fixme  qpid.AMQP should be using qpidproperties to get value
                    _brokerLogger.info("Qpid.AMQP listening on SSL port " + connectorConfig.sslPort);

                }
                catch (IOException e)
                {
                    _brokerLogger.error("Unable to listen on SSL port: " + e, e);
                }
            }

            //fixme  qpid.AMQP should be using qpidproperties to get value
            _brokerLogger.info("Qpid Broker Ready :" + QpidProperties.getReleaseVersion()
                               + " build: " + QpidProperties.getBuildVersion());
        }
        catch (Exception e)
        {
            _logger.error("Unable to bind service to registry: " + e, e);
            //fixme this need tidying up
            throw new BindException(e.getMessage());
        }
    }

    /**
     * Ensure that any bound Acceptors are recorded in the registry so they can be closed later.
     *
     * @param acceptor
     * @param bindAddress
     * @param handler
     * @param sconfig
     *
     * @throws IOException from the acceptor.bind command
     */
    private void bind(IoAcceptor acceptor, InetSocketAddress bindAddress, AMQPFastProtocolHandler handler, SocketAcceptorConfig sconfig) throws IOException
    {
        acceptor.bind(bindAddress, handler, sconfig);

        ApplicationRegistry.getInstance().addAcceptor(bindAddress, acceptor);
    }

    public static void main(String[] args)
    {

        new Main(args);
    }

    private byte[] parseIP(String address) throws Exception
    {
        char[] literalBuffer = address.toCharArray();
        int byteCount = 0;
        int currByte = 0;
        byte[] ip = new byte[IPV4_ADDRESS_LENGTH];
        for (int i = 0; i < literalBuffer.length; i++)
        {
            char currChar = literalBuffer[i];
            if ((currChar >= '0') && (currChar <= '9'))
            {
                currByte = (currByte * 10) + (Character.digit(currChar, 10) & 0xFF);
            }

            if (currChar == IPV4_LITERAL_SEPARATOR || (i + 1 == literalBuffer.length))
            {
                ip[byteCount++] = (byte) currByte;
                currByte = 0;
            }
        }

        if (byteCount != 4)
        {
            throw new Exception("Invalid IP address: " + address);
        }
        return ip;
    }

    private void configureLogging(File logConfigFile, String logWatchConfig)
    {
        int logWatchTime = 0;
        try
        {
            logWatchTime = Integer.parseInt(logWatchConfig);
        }
        catch (NumberFormatException e)
        {
            System.err.println("Log watch configuration value of " + logWatchConfig + " is invalid. Must be "
                               + "a non-negative integer. Using default of zero (no watching configured");
        }

        if (logConfigFile.exists() && logConfigFile.canRead())
        {
            System.out.println("Configuring logger using configuration file " + logConfigFile.getAbsolutePath());
            if (logWatchTime > 0)
            {
                System.out.println("log file " + logConfigFile.getAbsolutePath() + " will be checked for changes every "
                                   + logWatchTime + " seconds");
                // log4j expects the watch interval in milliseconds
                DOMConfigurator.configureAndWatch(logConfigFile.getAbsolutePath(), logWatchTime * 1000);
            }
            else
            {
                DOMConfigurator.configure(logConfigFile.getAbsolutePath());
            }
        }
        else
        {
            System.err.println("Logging configuration error: unable to read file " + logConfigFile.getAbsolutePath());
            System.err.println("Using basic log4j configuration");
            BasicConfigurator.configure();
        }
    }

}
