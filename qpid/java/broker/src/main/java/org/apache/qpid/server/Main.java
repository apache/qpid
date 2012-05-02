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
import org.apache.qpid.server.Broker.InitException;
import org.apache.qpid.server.registry.ApplicationRegistry;


/**
 * Main entry point for AMQPD.
 *
 */
public class Main
{

    private static final Option OPTION_HELP = new Option("h", "help", false, "print this message");

    private static final Option OPTION_VERSION = new Option("v", "version", false, "print the version information and exit");

    private static final Option OPTION_CONFIG_FILE =
            OptionBuilder.withArgName("file").hasArg().withDescription("use given configuration file").withLongOpt("config")
                    .create("c");

    private static final Option OPTION_PORT =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("listen on the specified port. Overrides any value in the config file")
                    .withLongOpt("port").create("p");

    private static final Option OPTION_SSLPORT =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("SSL port. Overrides any value in the config file")
                    .withLongOpt("sslport").create("s");


    private static final Option OPTION_EXCLUDE_1_0 =
            OptionBuilder.withArgName("port").hasArg()
                         .withDescription("when listening on the specified port do not accept AMQP1-0 connections. The specified port must be one specified on the command line")
                         .withLongOpt("exclude-1-0").create();

    private static final Option OPTION_EXCLUDE_0_10 =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("when listening on the specified port do not accept AMQP0-10 connections. The specified port must be one specified on the command line")
                    .withLongOpt("exclude-0-10").create();

    private static final Option OPTION_EXCLUDE_0_9_1 =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("when listening on the specified port do not accept AMQP0-9-1 connections. The specified port must be one specified on the command line")
                    .withLongOpt("exclude-0-9-1").create();

    private static final Option OPTION_EXCLUDE_0_9 =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("when listening on the specified port do not accept AMQP0-9 connections. The specified port must be one specified on the command line")
                    .withLongOpt("exclude-0-9").create();

    private static final Option OPTION_EXCLUDE_0_8 =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("when listening on the specified port do not accept AMQP0-8 connections. The specified port must be one specified on the command line")
                    .withLongOpt("exclude-0-8").create();

    private static final Option OPTION_JMX_PORT_REGISTRY_SERVER =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("listen on the specified management (registry server) port. Overrides any value in the config file")
                    .withLongOpt("jmxregistryport").create("m");

    private static final Option OPTION_JMX_PORT_CONNECTOR_SERVER =
            OptionBuilder.withArgName("port").hasArg()
                    .withDescription("listen on the specified management (connector server) port. Overrides any value in the config file")
                    .withLongOpt("jmxconnectorport").create();

    private static final Option OPTION_BIND =
            OptionBuilder.withArgName("address").hasArg()
                    .withDescription("bind to the specified address. Overrides any value in the config file")
                    .withLongOpt("bind").create("b");

    private static final Option OPTION_LOG_CONFIG_FILE =
            OptionBuilder.withArgName("file").hasArg()
                    .withDescription("use the specified log4j xml configuration file. By "
                                     + "default looks for a file named " + BrokerOptions.DEFAULT_LOG_CONFIG_FILE
                                     + " in the same directory as the configuration file").withLongOpt("logconfig").create("l");

    private static final Option OPTION_LOG_WATCH =
            OptionBuilder.withArgName("period").hasArg()
                    .withDescription("monitor the log file configuration file for changes. Units are seconds. "
                                     + "Zero means do not check for changes.").withLongOpt("logwatch").create("w");

    private static final Options OPTIONS = new Options();

    static
    {
        OPTIONS.addOption(OPTION_HELP);
        OPTIONS.addOption(OPTION_VERSION);
        OPTIONS.addOption(OPTION_CONFIG_FILE);
        OPTIONS.addOption(OPTION_LOG_CONFIG_FILE);
        OPTIONS.addOption(OPTION_LOG_WATCH);
        OPTIONS.addOption(OPTION_PORT);
        OPTIONS.addOption(OPTION_SSLPORT);
        OPTIONS.addOption(OPTION_EXCLUDE_1_0);
        OPTIONS.addOption(OPTION_EXCLUDE_0_10);
        OPTIONS.addOption(OPTION_EXCLUDE_0_9_1);
        OPTIONS.addOption(OPTION_EXCLUDE_0_9);
        OPTIONS.addOption(OPTION_EXCLUDE_0_8);
        OPTIONS.addOption(OPTION_BIND);

        OPTIONS.addOption(OPTION_JMX_PORT_REGISTRY_SERVER);
        OPTIONS.addOption(OPTION_JMX_PORT_CONNECTOR_SERVER);
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
            String configFile = _commandLine.getOptionValue(OPTION_CONFIG_FILE.getOpt());
            if(configFile != null)
            {
                options.setConfigFile(configFile);
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

            String jmxPortRegistryServer = _commandLine.getOptionValue(OPTION_JMX_PORT_REGISTRY_SERVER.getOpt());
            if(jmxPortRegistryServer != null)
            {
                options.setJmxPortRegistryServer(Integer.parseInt(jmxPortRegistryServer));
            }

            String jmxPortConnectorServer = _commandLine.getOptionValue(OPTION_JMX_PORT_CONNECTOR_SERVER.getLongOpt());
            if(jmxPortConnectorServer != null)
            {
                options.setJmxPortConnectorServer(Integer.parseInt(jmxPortConnectorServer));
            }

            String bindAddr = _commandLine.getOptionValue(OPTION_BIND.getOpt());
            if (bindAddr != null)
            {
                options.setBind(bindAddr);
            }

            String[] portStr = _commandLine.getOptionValues(OPTION_PORT.getOpt());
            if(portStr != null)
            {
                parsePortArray(options, portStr, false);
                for(ProtocolExclusion pe : ProtocolExclusion.values())
                {
                    parsePortArray(options, _commandLine.getOptionValues(pe.getExcludeName()), pe);
                }
            }

            String[] sslPortStr = _commandLine.getOptionValues(OPTION_SSLPORT.getOpt());
            if(sslPortStr != null)
            {
                parsePortArray(options, sslPortStr, true);
                for(ProtocolExclusion pe : ProtocolExclusion.values())
                {
                    parsePortArray(options, _commandLine.getOptionValues(pe.getExcludeName()), pe);
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
        ApplicationRegistry.remove();
        System.exit(status);
    }

    private static void parsePortArray(final BrokerOptions options,final Object[] ports,
                                       final boolean ssl) throws InitException
    {
        if(ports != null)
        {
            for(int i = 0; i < ports.length; i++)
            {
                try
                {
                    if(ssl)
                    {
                        options.addSSLPort(Integer.parseInt(String.valueOf(ports[i])));
                    }
                    else
                    {
                        options.addPort(Integer.parseInt(String.valueOf(ports[i])));
                    }
                }
                catch (NumberFormatException e)
                {
                    throw new InitException("Invalid port: " + ports[i], e);
                }
            }
        }
    }

    private static void parsePortArray(final BrokerOptions options, final Object[] ports,
                                       final ProtocolExclusion excludedProtocol) throws InitException
    {
        if(ports != null)
        {
            for(int i = 0; i < ports.length; i++)
            {
                try
                {
                    options.addExcludedPort(excludedProtocol, 
                            Integer.parseInt(String.valueOf(ports[i])));
                }
                catch (NumberFormatException e)
                {
                    throw new InitException("Invalid port for exclusion: " + ports[i], e);
                }
            }
        }
    }
}
