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
package org.apache.qpid.server.cluster;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.ConfigurationFileApplicationRegistry;
import org.apache.qpid.server.transport.ConnectorConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * TODO: This is a cut-and-paste from the original broker Main class. Would be preferrable to make that class more
 * reuseable to avoid all this duplication.
 */
public class Main extends org.apache.qpid.server.Main
{
    private static final Logger _logger = Logger.getLogger(Main.class);

    protected Main(String[] args)
    {
        super(args);
    }

    protected void setOptions(Options otions)
    {
        super.setOptions(options);

        //extensions:
        Option join = OptionBuilder.withArgName("join").hasArg().withDescription("Join the specified cluster member. Overrides any value in the config file").
                withLongOpt("join").create("j");
        options.addOption(join);
    }

    protected void bind(int port, ConnectorConfiguration connectorConfig)
    {
        try
        {
            IoAcceptor acceptor = new SocketAcceptor();
            SocketAcceptorConfig sconfig = (SocketAcceptorConfig) acceptor.getDefaultConfig();
            SocketSessionConfig sc = (SocketSessionConfig) sconfig.getSessionConfig();

            sc.setReceiveBufferSize(connectorConfig.socketReceiveBufferSize);
            sc.setSendBufferSize(connectorConfig.socketWriteBuferSize);
            sc.setTcpNoDelay(true);

            // if we do not use the executor pool threading model we get the default leader follower
            // implementation provided by MINA
            if (connectorConfig.enableExecutorPool)
            {
                sconfig.setThreadModel(ReadWriteThreadModel.getInstance());
            }

            String host = InetAddress.getLocalHost().getHostName();
            ClusteredProtocolHandler handler = new ClusteredProtocolHandler(new InetSocketAddress(host, port));
            if (!connectorConfig.enableSSL)
            {
                acceptor.bind(new InetSocketAddress(port), handler, sconfig);
                _logger.info("Qpid.AMQP listening on non-SSL port " + port);
                handler.connect(commandLine.getOptionValue("j"));
            }
            else
            {
                ClusteredProtocolHandler sslHandler = new ClusteredProtocolHandler(handler);
                acceptor.bind(new InetSocketAddress(connectorConfig.sslPort), sslHandler, sconfig);
                _logger.info("Qpid.AMQP listening on SSL port " + connectorConfig.sslPort);
            }
        }
        catch (IOException e)
        {
            _logger.error("Unable to bind service to registry: " + e, e);
        }
        catch (Exception e)
        {
            _logger.error("Unable to connect to cluster: " + e, e);
        }
    }

    public static void main(String[] args)
    {
        new Main(args);
    }
}
