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
import org.apache.log4j.Logger;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.framing.ProtocolVersion;

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
            .withDescription("use given store type").withLongOpt("store-type").create("st");

    private static final Option OPTION_INITIAL_CONFIGURATION_STORE_PATH = OptionBuilder.withArgName("path").hasArg()
            .withDescription("pass the location of initial store to use to create a user store").withLongOpt("initial-store-path").create("isp");

    private static final Option OPTION_INITIAL_CONFIGURATION_STORE_TYPE = OptionBuilder.withArgName("type").hasArg()
            .withDescription("the type of initial store").withLongOpt("initial-store-type").create("ist");

    private static final Option OPTION_LOG_CONFIG_FILE =
            OptionBuilder.withArgName("file").hasArg()
                    .withDescription("use the specified log4j xml configuration file. By "
                                     + "default looks for a file named " + BrokerOptions.DEFAULT_LOG_CONFIG_FILE
                                     + " in the same directory as the configuration file").withLongOpt("logconfig").create("l");

    private static final Option OPTION_LOG_WATCH =
            OptionBuilder.withArgName("period").hasArg()
                    .withDescription("monitor the log file configuration file for changes. Units are seconds. "
                                     + "Zero means do not check for changes.").withLongOpt("logwatch").create("w");

    private static final Option OPTION_MANAGEMENT_MODE = OptionBuilder.withDescription("start broker in a management mode")
            .withLongOpt("management-mode").create("mm");
    private static final Option OPTION_RMI_PORT = OptionBuilder.withArgName("port").hasArg()
            .withDescription("override jmx rmi port in management mode").withLongOpt("jmxregistryport").create("rmi");
    private static final Option OPTION_CONNECTOR_PORT = OptionBuilder.withArgName("port").hasArg()
            .withDescription("override jmx connector port in management mode").withLongOpt("jmxconnectorport").create("jmxrmi");
    private static final Option OPTION_HTTP_PORT = OptionBuilder.withArgName("port").hasArg()
            .withDescription("override web management port in management mode").withLongOpt("httpport").create("http");

    private static final Options OPTIONS = new Options();

    static
    {
        OPTIONS.addOption(OPTION_HELP);
        OPTIONS.addOption(OPTION_VERSION);
        OPTIONS.addOption(OPTION_CONFIGURATION_STORE_PATH);
        OPTIONS.addOption(OPTION_CONFIGURATION_STORE_TYPE);
        OPTIONS.addOption(OPTION_LOG_CONFIG_FILE);
        OPTIONS.addOption(OPTION_LOG_WATCH);
        OPTIONS.addOption(OPTION_INITIAL_CONFIGURATION_STORE_PATH);
        OPTIONS.addOption(OPTION_INITIAL_CONFIGURATION_STORE_TYPE);
        OPTIONS.addOption(OPTION_MANAGEMENT_MODE);
        OPTIONS.addOption(OPTION_RMI_PORT);
        OPTIONS.addOption(OPTION_CONNECTOR_PORT);
        OPTIONS.addOption(OPTION_HTTP_PORT);
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
        if (_commandLine.hasOption(OPTION_HELP.getOpt()))
        {
            final HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Qpid", OPTIONS, true);
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
            BrokerOptions options = new BrokerOptions();
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
                options.setLogConfigFile(logConfig);
            }

            String initialConfigurationStore = _commandLine.getOptionValue(OPTION_INITIAL_CONFIGURATION_STORE_PATH.getOpt());
            if (initialConfigurationStore != null)
            {
                options.setInitialConfigurationStoreLocation(initialConfigurationStore);
            }
            String initailConfigurationStoreType = _commandLine.getOptionValue(OPTION_INITIAL_CONFIGURATION_STORE_TYPE.getOpt());
            if (initailConfigurationStoreType != null)
            {
                options.setInitialConfigurationStoreType(initailConfigurationStoreType);
            }

            boolean managmentMode = _commandLine.hasOption(OPTION_MANAGEMENT_MODE.getOpt());
            if (managmentMode)
            {
                options.setManagementMode(true);
                String rmiPort = _commandLine.getOptionValue(OPTION_RMI_PORT.getOpt());
                if (rmiPort != null)
                {
                    options.setManagementModeRmiPort(Integer.parseInt(rmiPort));
                }
                String connectorPort = _commandLine.getOptionValue(OPTION_CONNECTOR_PORT.getOpt());
                if (connectorPort != null)
                {
                    options.setManagementModeConnectorPort(Integer.parseInt(connectorPort));
                }
                String httpPort = _commandLine.getOptionValue(OPTION_HTTP_PORT.getOpt());
                if (httpPort != null)
                {
                    options.setManagementModeHttpPort(Integer.parseInt(httpPort));
                }
            }
            setExceptionHandler();

            startBroker(options);
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
