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
import org.apache.qpid.server.Broker.InitException;
import org.apache.qpid.server.registry.ApplicationRegistry;


/**
 * Main entry point for AMQPD.
 *
 */
public class Main
{
    private static Logger _logger;

    protected final static Options options = new Options();
    protected static CommandLine commandLine;

    public static void main(String[] args)
    {
        //if the -Dlog4j.configuration property has not been set, enable the init override
        //to stop Log4J wondering off and picking up the first log4j.xml/properties file it
        //finds from the classpath when we get the first Loggers
        if(System.getProperty("log4j.configuration") == null)
        {
            System.setProperty("log4j.defaultInitOverride", "true");
        }

        //now that the override status is know, we can instantiate the Loggers
        _logger = Logger.getLogger(Main.class);
        setOptions(options);
        if (parseCommandline(args))
        {
            try
            {
                execute();
            }
            catch(Exception e)
            {
                System.err.println("Exception during startup: " + e);
                e.printStackTrace();
                shutdown(1);
            }
        }
    }

    protected static boolean parseCommandline(String[] args)
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

    protected static void setOptions(Options options)
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

        Option exclude0_10 =
                OptionBuilder.withArgName("exclude-0-10").hasArg()
                        .withDescription("when listening on the specified port do not accept AMQP0-10 connections. The specified port must be one specified on the command line")
                        .withLongOpt("exclude-0-10").create();

        Option exclude0_9_1 =
                OptionBuilder.withArgName("exclude-0-9-1").hasArg()
                        .withDescription("when listening on the specified port do not accept AMQP0-9-1 connections. The specified port must be one specified on the command line")
                        .withLongOpt("exclude-0-9-1").create();


        Option exclude0_9 =
                OptionBuilder.withArgName("exclude-0-9").hasArg()
                        .withDescription("when listening on the specified port do not accept AMQP0-9 connections. The specified port must be one specified on the command line")
                        .withLongOpt("exclude-0-9").create();


        Option exclude0_8 =
                OptionBuilder.withArgName("exclude-0-8").hasArg()
                        .withDescription("when listening on the specified port do not accept AMQP0-8 connections. The specified port must be one specified on the command line")
                        .withLongOpt("exclude-0-8").create();


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
                                         + "default looks for a file named " + BrokerOptions.DEFAULT_LOG_CONFIG_FILE
                                         + " in the same directory as the configuration file").withLongOpt("logconfig").create("l");
        Option logwatchconfig =
                OptionBuilder.withArgName("logwatch").hasArg()
                        .withDescription("monitor the log file configuration file for changes. Units are seconds. "
                                         + "Zero means do not check for changes.").withLongOpt("logwatch").create("w");

        Option sslport =
                OptionBuilder.withArgName("sslport").hasArg()
                        .withDescription("SSL port. Overrides any value in the config file")
                        .withLongOpt("sslport").create("s");

        options.addOption(help);
        options.addOption(version);
        options.addOption(configFile);
        options.addOption(logconfig);
        options.addOption(logwatchconfig);
        options.addOption(port);
        options.addOption(exclude0_10);
        options.addOption(exclude0_9_1);
        options.addOption(exclude0_9);
        options.addOption(exclude0_8);
        options.addOption(mport);
        options.addOption(bind);
        options.addOption(sslport);
    }

    protected static void execute() throws Exception
    {
        BrokerOptions options = new BrokerOptions();
        String configFile = commandLine.getOptionValue(BrokerOptions.CONFIG);
        if(configFile != null)
        {
            options.setConfigFile(configFile);
        }

        String logWatchConfig = commandLine.getOptionValue(BrokerOptions.WATCH);
        if(logWatchConfig != null)
        {
            options.setLogWatchFrequency(Integer.parseInt(logWatchConfig) * 1000);
        }

        String logConfig = commandLine.getOptionValue(BrokerOptions.LOG4J);
        if(logConfig != null)
        {
            options.setLogConfigFile(logConfig);
        }

        String jmxPort = commandLine.getOptionValue(BrokerOptions.MANAGEMENT);
        if(jmxPort != null)
        {
            options.setJmxPort(Integer.parseInt(jmxPort));
        }

        String bindAddr = commandLine.getOptionValue(BrokerOptions.BIND);
        if (bindAddr != null)
        {
            options.setBind(bindAddr);
        }

        String[] portStr = commandLine.getOptionValues(BrokerOptions.PORTS);
        if(portStr != null)
        {
            parsePortArray(options, portStr, false);
            for(ProtocolExclusion pe : ProtocolExclusion.values())
            {
                parsePortArray(options, commandLine.getOptionValues(pe.getExcludeName()), pe);
            }
        }

        String[] sslPortStr = commandLine.getOptionValues(BrokerOptions.SSL_PORTS);
        if(sslPortStr != null)
        {
            parsePortArray(options, sslPortStr, true);
            for(ProtocolExclusion pe : ProtocolExclusion.values())
            {
                parsePortArray(options, commandLine.getOptionValues(pe.getExcludeName()), pe);
            }
        }
        
        Broker broker = new Broker();
        broker.startup(options);
    }

    protected static void shutdown(int status)
    {
        ApplicationRegistry.remove();
        System.exit(status);
    }

    private static void parsePortArray(BrokerOptions options, Object[] ports, boolean ssl) throws InitException
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

    private static void parsePortArray(BrokerOptions options, Object[] ports, ProtocolExclusion excludedProtocol) throws InitException
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
