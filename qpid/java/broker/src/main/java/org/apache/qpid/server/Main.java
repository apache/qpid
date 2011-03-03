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
import org.apache.qpid.BrokerOptions;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.framing.ProtocolVersion;

/**
 * Main entry point for Qpid broker.
 */
public class Main
{
    @SuppressWarnings("static-access")
    private static Options getOptions()
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
                                         + "default looks for a file named " + BrokerOptions.DEFAULT_LOG_CONFIG_FILENAME
                                         + " in the same directory as the configuration file").withLongOpt("logconfig").create("l");
        Option logwatchconfig =
                OptionBuilder.withArgName("logwatch").hasArg()
                        .withDescription("monitor the log file configuration file for changes. Units are seconds. "
                                         + "Zero means do not check for changes.").withLongOpt("logwatch").create("w");

        Options options = new Options();
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
        return options;
    }

    public static void main(String[] args)
    {
        Options options = getOptions();
        try
        {
            CommandLine commandLine = new PosixParser().parse(options, args);
        
            // note this understands either --help or -h. If an option only has a
            // long name you can use that but if an option has a short name and a
            // long name you must use the short name here.
            if (commandLine.hasOption("h"))
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("Qpid", options, true);
            }
            else if (commandLine.hasOption("v"))
            {
                String ver = QpidProperties.getVersionString();
    
                StringBuilder protocol = new StringBuilder();
                for (ProtocolVersion pv : ProtocolVersion.getSupportedProtocolVersions())
                {
                    if (protocol.length() > 0)
                    {
                        protocol.append(", ");
                    }
                    protocol.append(pv.getMajorVersion()).append('-').append(pv.getMinorVersion());
                }
    
                System.out.println(ver + " AMQP version(s) [major.minor]: (" + protocol + ")");
            }
            else
            {
                BrokerOptions brokerOptions = new BrokerOptions();
                for (String option : BrokerOptions.COMMAND_LINE_OPTIONS)
                {
                    brokerOptions.put(option, commandLine.getOptionValues(option));
                }
                BrokerInstance broker = new BrokerInstance();
                try
                {
                    broker.startup(brokerOptions);
                }
                catch (BrokerInstance.InitException e)
                {
                    System.out.println("Error initialising message broker: " + e);
                    e.printStackTrace();
                    broker.shutdown();
                }
                catch (Throwable e)
                {
                    System.out.println("Error running message broker: " + e);
                    e.printStackTrace();
                    broker.shutdown();
                }
            }
        }
        catch (ParseException pe)
        {
            System.err.println("Error: " + pe.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Qpid", options, true);
        }
    }
}
