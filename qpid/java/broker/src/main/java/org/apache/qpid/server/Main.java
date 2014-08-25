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
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.util.FileUtils;

/**
 * Main entry point for AMQPD.
 *
 */
public class Main
{
    private static final Option OPTION_HELP = new Option("h", "help", false, "print this message");

    private static final Option OPTION_VERSION = new Option("v", "version", false, "print the version information and exit");

    private static final Option OPTION_CONFIGURATION_STORE_PATH = OptionBuilder.withArgName("path").hasArg()
            .withDescription("use given configuration store location").withLongOpt("store-path").create("sp");

    private static final Option OPTION_CONFIGURATION_STORE_TYPE = OptionBuilder.withArgName("type").hasArg()
            .withDescription("use given broker configuration store type").withLongOpt("store-type").create("st");

    private static final Option OPTION_INITIAL_CONFIGURATION_PATH = OptionBuilder.withArgName("path").hasArg()
            .withDescription("set the location of initial JSON config to use when creating/overwriting a broker configuration store").withLongOpt("initial-config-path").create("icp");

    private static final Option OPTION_OVERWRITE_CONFIGURATION_STORE = OptionBuilder.withDescription("overwrite the broker configuration store with the current initial configuration")
            .withLongOpt("overwrite-store").create("os");

    private static final Option OPTION_CREATE_INITIAL_CONFIG = OptionBuilder.withArgName("path").hasOptionalArg().withDescription("create a copy of the initial config file, either to an" +
            " optionally specified file path, or as " + BrokerOptions.DEFAULT_INITIAL_CONFIG_NAME + " in the current directory")
            .withLongOpt("create-initial-config").create("cic");

    private static final Option OPTION_CONFIGURATION_PROPERTY = OptionBuilder.withArgName("name=value").hasArg()
            .withDescription("set a configuration property to use when resolving variables in the broker configuration store, with format \"name=value\"")
            .withLongOpt("config-property").create("prop");

    private static final Option OPTION_LOG_CONFIG_FILE =
            OptionBuilder.withArgName("file").hasArg()
                    .withDescription("use the specified log4j xml configuration file. By "
                                     + "default looks for a file named " + BrokerOptions.DEFAULT_LOG_CONFIG_FILE
                                     + " in the same directory as the configuration file").withLongOpt("logconfig").create("l");

    private static final Option OPTION_LOG_WATCH =
            OptionBuilder.withArgName("period").hasArg()
                    .withDescription("monitor the log file configuration file for changes. Units are seconds. "
                                     + "Zero means do not check for changes.").withLongOpt("logwatch").create("w");

    private static final Option OPTION_MANAGEMENT_MODE = OptionBuilder.withDescription("start broker in management mode, disabling the AMQP ports")
            .withLongOpt("management-mode").create("mm");
    private static final Option OPTION_MM_QUIESCE_VHOST = OptionBuilder.withDescription("make virtualhosts stay in the quiesced state during management mode.")
            .withLongOpt("management-mode-quiesce-virtualhosts").create("mmqv");
    private static final Option OPTION_MM_RMI_PORT = OptionBuilder.withArgName("port").hasArg()
            .withDescription("override jmx rmi registry port in management mode").withLongOpt("management-mode-rmi-registry-port").create("mmrmi");
    private static final Option OPTION_MM_CONNECTOR_PORT = OptionBuilder.withArgName("port").hasArg()
            .withDescription("override jmx connector port in management mode").withLongOpt("management-mode-jmx-connector-port").create("mmjmx");
    private static final Option OPTION_MM_HTTP_PORT = OptionBuilder.withArgName("port").hasArg()
            .withDescription("override http management port in management mode").withLongOpt("management-mode-http-port").create("mmhttp");
    private static final Option OPTION_MM_PASSWORD = OptionBuilder.withArgName("password").hasArg()
            .withDescription("Set the password for the management mode user " + BrokerOptions.MANAGEMENT_MODE_USER_NAME).withLongOpt("management-mode-password").create("mmpass");

    private static final Option OPTION_INITIAL_SYSTEM_PROPERTIES = OptionBuilder.withArgName("path").hasArg()
            .withDescription("set the location of initial properties file to set otherwise unset system properties").withLongOpt("system-properties-file").create("props");

    private static final Options OPTIONS = new Options();

    static
    {
        OPTIONS.addOption(OPTION_HELP);
        OPTIONS.addOption(OPTION_VERSION);
        OPTIONS.addOption(OPTION_CONFIGURATION_STORE_PATH);
        OPTIONS.addOption(OPTION_CONFIGURATION_STORE_TYPE);
        OPTIONS.addOption(OPTION_OVERWRITE_CONFIGURATION_STORE);
        OPTIONS.addOption(OPTION_CREATE_INITIAL_CONFIG);
        OPTIONS.addOption(OPTION_LOG_CONFIG_FILE);
        OPTIONS.addOption(OPTION_LOG_WATCH);
        OPTIONS.addOption(OPTION_INITIAL_CONFIGURATION_PATH);
        OPTIONS.addOption(OPTION_MANAGEMENT_MODE);
        OPTIONS.addOption(OPTION_MM_QUIESCE_VHOST);
        OPTIONS.addOption(OPTION_MM_RMI_PORT);
        OPTIONS.addOption(OPTION_MM_CONNECTOR_PORT);
        OPTIONS.addOption(OPTION_MM_HTTP_PORT);
        OPTIONS.addOption(OPTION_MM_PASSWORD);
        OPTIONS.addOption(OPTION_CONFIGURATION_PROPERTY);
        OPTIONS.addOption(OPTION_INITIAL_SYSTEM_PROPERTIES);
    }

    protected CommandLine _commandLine;

    public static void main(String[] args)
    {
        //if the -Dlog4j.configuration property has not been set, enable the init override
        //to stop Log4J wondering off and picking up the first log4j.xml/properties file it
        //finds from the classpath when we get the first Loggers
        if(System.getProperty("log4j.configuration") == null)
        {
            System.setProperty("log4j.defaultInitOverride", "true");
        }

        new Main(args);
    }

    public Main(final String[] args)
    {
        if (parseCommandline(args))
        {
            try
            {
                execute();
            }
            catch(Throwable e)
            {
                System.err.println("Exception during startup: " + e);
                e.printStackTrace();
                shutdown(1);
            }
        }
    }

    protected boolean parseCommandline(final String[] args)
    {
        try
        {
            _commandLine = new PosixParser().parse(OPTIONS, args);

            return true;
        }
        catch (ParseException e)
        {
            System.err.println("Error: " + e.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Qpid", OPTIONS, true);

            return false;
        }
    }

    protected void execute() throws Exception
    {
        String initialProperties = _commandLine.getOptionValue(OPTION_INITIAL_SYSTEM_PROPERTIES.getOpt());
        populateSystemPropertiesFromDefaults(initialProperties);

        BrokerOptions options = new BrokerOptions();

        String initialConfigLocation = _commandLine.getOptionValue(OPTION_INITIAL_CONFIGURATION_PATH.getOpt());
        if (initialConfigLocation != null)
        {
            options.setInitialConfigurationLocation(initialConfigLocation);
        }

        //process the remaining options
        if (_commandLine.hasOption(OPTION_HELP.getOpt()))
        {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Qpid", OPTIONS, true);
        }
        else if (_commandLine.hasOption(OPTION_CREATE_INITIAL_CONFIG.getOpt()))
        {
            File destinationFile = null;

            String destinationOption = _commandLine.getOptionValue(OPTION_CREATE_INITIAL_CONFIG.getOpt());
            if (destinationOption != null)
            {
                destinationFile = new File(destinationOption);
            }
            else
            {
                destinationFile = new File(System.getProperty("user.dir"), BrokerOptions.DEFAULT_INITIAL_CONFIG_NAME);
            }

            copyInitialConfigFile(options, destinationFile);

            System.out.println("Initial config written to: " + destinationFile.getAbsolutePath());
        }
        else if (_commandLine.hasOption(OPTION_VERSION.getOpt()))
        {
            final StringBuilder protocol = new StringBuilder("AMQP version(s) [major.minor]: ");
            boolean first = true;
            for (final ProtocolVersion pv : ProtocolVersion.getSupportedProtocolVersions())
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
            System.out.println(QpidProperties.getVersionString() + " (" + protocol + ")");
        }
        else
        {
            String[] configPropPairs = _commandLine.getOptionValues(OPTION_CONFIGURATION_PROPERTY.getOpt());
            if(configPropPairs != null && configPropPairs.length > 0)
            {
                for(String s : configPropPairs)
                {
                    int firstEquals = s.indexOf("=");
                    if(firstEquals == -1)
                    {
                        throw new IllegalArgumentException("Configuration property argument is not of the format name=value: " + s);
                    }
                    String name = s.substring(0, firstEquals);
                    String value = s.substring(firstEquals + 1);

                    if(name.equals(""))
                    {
                        throw new IllegalArgumentException("Configuration property argument is not of the format name=value: " + s);
                    }

                    options.setConfigProperty(name, value);
                }
            }

            String configurationStore = _commandLine.getOptionValue(OPTION_CONFIGURATION_STORE_PATH.getOpt());
            if (configurationStore != null)
            {
                options.setConfigurationStoreLocation(configurationStore);
            }

            String configurationStoreType = _commandLine.getOptionValue(OPTION_CONFIGURATION_STORE_TYPE.getOpt());
            if (configurationStoreType != null)
            {
                options.setConfigurationStoreType(configurationStoreType);
            }

            String logWatchConfig = _commandLine.getOptionValue(OPTION_LOG_WATCH.getOpt());
            if(logWatchConfig != null)
            {
                options.setLogWatchFrequency(Integer.parseInt(logWatchConfig));
            }

            String logConfig = _commandLine.getOptionValue(OPTION_LOG_CONFIG_FILE.getOpt());
            if(logConfig != null)
            {
                options.setLogConfigFileLocation(logConfig);
            }

            boolean overwriteConfigurationStore = _commandLine.hasOption(OPTION_OVERWRITE_CONFIGURATION_STORE.getOpt());
            options.setOverwriteConfigurationStore(overwriteConfigurationStore);

            boolean managementMode = _commandLine.hasOption(OPTION_MANAGEMENT_MODE.getOpt());
            if (managementMode)
            {
                options.setManagementMode(true);
                String rmiPort = _commandLine.getOptionValue(OPTION_MM_RMI_PORT.getOpt());
                if (rmiPort != null)
                {
                    options.setManagementModeRmiPortOverride(Integer.parseInt(rmiPort));
                }
                String connectorPort = _commandLine.getOptionValue(OPTION_MM_CONNECTOR_PORT.getOpt());
                if (connectorPort != null)
                {
                    options.setManagementModeJmxPortOverride(Integer.parseInt(connectorPort));
                }
                String httpPort = _commandLine.getOptionValue(OPTION_MM_HTTP_PORT.getOpt());
                if (httpPort != null)
                {
                    options.setManagementModeHttpPortOverride(Integer.parseInt(httpPort));
                }

                boolean quiesceVhosts = _commandLine.hasOption(OPTION_MM_QUIESCE_VHOST.getOpt());
                options.setManagementModeQuiesceVirtualHosts(quiesceVhosts);

                String password = _commandLine.getOptionValue(OPTION_MM_PASSWORD.getOpt());
                if (password != null)
                {
                    options.setManagementModePassword(password);
                }
            }
            setExceptionHandler();

            startBroker(options);
        }
    }

    private void populateSystemPropertiesFromDefaults(final String initialProperties) throws IOException
    {
        URL initialPropertiesLocation;
        if(initialProperties == null)
        {
            initialPropertiesLocation = getClass().getClassLoader().getResource("system.properties");
        }
        else
        {
            initialPropertiesLocation = (new File(initialProperties)).toURI().toURL();
        }

        Properties props = new Properties();
        props.load(initialPropertiesLocation.openStream());
        Set<String> propertyNames = new HashSet<>(props.stringPropertyNames());
        propertyNames.removeAll(System.getProperties().stringPropertyNames());
        for(String propName : propertyNames)
        {
            System.setProperty(propName, props.getProperty(propName));
        }
    }

    private void copyInitialConfigFile(final BrokerOptions options, final File destinationFile)
    {
        String initialConfigLocation = options.getInitialConfigurationLocation();
        URL url = null;
        try
        {
            url = new URL(initialConfigLocation);
        }
        catch (MalformedURLException e)
        {
            File locationFile = new File(initialConfigLocation);
            try
            {
                url = locationFile.toURI().toURL();
            }
            catch (MalformedURLException e1)
            {
                throw new IllegalConfigurationException("Cannot create URL for file " + locationFile, e1);
            }
        }
        InputStream in =  null;
        try
        {
            in = url.openStream();
            FileUtils.copy(in, destinationFile);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot create file " + destinationFile
                                                    + " by copying initial config from " + initialConfigLocation, e);
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot close initial config input stream: " + initialConfigLocation, e);
                }
            }
        }
    }

    protected void setExceptionHandler()
    {
        Thread.UncaughtExceptionHandler handler = null;
        String handlerClass = System.getProperty("qpid.broker.exceptionHandler");
        if(handlerClass != null)
        {
            try
            {
                handler = (Thread.UncaughtExceptionHandler) Class.forName(handlerClass).newInstance();
            }
            catch (ClassNotFoundException e)
            {
                
            }
            catch (InstantiationException e)
            {
                
            }
            catch (IllegalAccessException e)
            {
                
            }
            catch (ClassCastException e)
            {

            }
        }
        
        if(handler == null)
        {
            handler =
                new Thread.UncaughtExceptionHandler()
                {
                    public void uncaughtException(final Thread t, final Throwable e)
                    {
                        boolean continueOnError = Boolean.getBoolean("qpid.broker.exceptionHandler.continue");
                        try
                        {
                            System.err.println("########################################################################");
                            System.err.println("#");
                            System.err.print("# Unhandled Exception ");
                            System.err.print(e.toString());
                            System.err.print(" in Thread ");
                            System.err.println(t.getName());
                            System.err.println("#");
                            System.err.println(continueOnError ? "# Forced to continue by JVM setting 'qpid.broker.exceptionHandler.continue'" : "# Exiting");
                            System.err.println("#");
                            System.err.println("########################################################################");
                            e.printStackTrace(System.err);

                            Logger logger = Logger.getLogger("org.apache.qpid.server.Main");
                            logger.error("Uncaught exception, " + (continueOnError ? "continuing." : "shutting down."), e);
                        }
                        finally
                        {
                            if (!continueOnError)
                            {
                                Runtime.getRuntime().halt(1);
                            }
                        }

                    }
                };

            Thread.setDefaultUncaughtExceptionHandler(handler);
        } 
    }

    protected void startBroker(final BrokerOptions options) throws Exception
    {
        Broker broker = new Broker();
        broker.startup(options);
    }

    protected void shutdown(final int status)
    {
        System.exit(status);
    }

}
