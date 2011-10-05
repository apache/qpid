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
package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.transport.Container;
import org.apache.commons.cli.*;

import java.util.logging.*;

public abstract class Util
{

    private static final Logger FRAME_LOGGER = Logger.getLogger("FRM");
    private static final Logger RAW_LOGGER = Logger.getLogger("RAW");
    private String _host;
    private String _username;
    private String _password;
    private int _port;
    private int _count;
    private boolean _useStdIn;
    private boolean _useTran;
    private String[] _args;
    private AcknowledgeMode _mode;
    private boolean _block;
    private int _frameSize;
    private int _messageSize;
    private String _responseQueue;
    private int _batchSize;
    private double _rollbackRatio;
    private String _linkName;
    private String _containerName;
    private boolean _durableLink;
    private boolean _useMultipleConnections;
    private int _windowSize = 100;
    private String _subject;
    private String _filter;
    private String _remoteHost;
    private boolean _useSSL;

    protected Util(String[] args)
    {
        CommandLineParser cmdLineParse = new PosixParser();

        Options options = new Options();
        options.addOption("h","help",false,"show this help message and exit");
        options.addOption(OptionBuilder.withLongOpt("host")
                .withDescription( "host to connect to (default 0.0.0.0)" )
                .hasArg(true)
                .withArgName("HOST")
                .create('H'));
        options.addOption(OptionBuilder.withLongOpt("username")
                .withDescription( "username to use for authentication" )
                .hasArg(true)
                .withArgName("USERNAME")
                .create('u'));
        options.addOption(OptionBuilder.withLongOpt("password")
                .withDescription( "password to use for authentication" )
                .hasArg(true)
                .withArgName("PASSWORD")
                .create('w'));
        options.addOption(OptionBuilder.withLongOpt("port")
                .withDescription( "port to connect to (default 5672)" )
                .hasArg(true)
                .withArgName("PORT")
                .create('p'));
        options.addOption(OptionBuilder.withLongOpt("frame-size")
                .withDescription( "specify the maximum frame size" )
                .hasArg(true)
                .withArgName("FRAME_SIZE")
                .create('f'));
        options.addOption(OptionBuilder.withLongOpt("container-name")
                .withDescription( "Container name" )
                .hasArg(true)
                .withArgName("CONTAINER_NAME")
                .create('C'));

        options.addOption(OptionBuilder.withLongOpt("ssl")
                                            .withDescription("Use SSL")
                                            .create('S'));

        options.addOption(OptionBuilder.withLongOpt("remote-hostname")
                .withDescription( "hostname to supply in the open frame" )
                .hasArg(true)
                .withArgName("HOST")
                .create('O'));

        if(hasBlockOption())
            options.addOption(OptionBuilder.withLongOpt("block")
                                    .withDescription("block until messages arrive")
                                    .create('b'));

        if(hasCountOption())
            options.addOption(OptionBuilder.withLongOpt("count")
                        .withDescription( "number of messages to send (default 1)" )
                        .hasArg(true)
                        .withArgName("COUNT")
                        .create('c'));
        if(hasModeOption())
            options.addOption(OptionBuilder.withLongOpt("acknowledge-mode")
                .withDescription( "acknowledgement mode: AMO|ALO|EO (At Least Once, At Most Once, Exactly Once" )
                .hasArg(true)
                .withArgName("MODE")
                .create('k'));

        if(hasSubjectOption())
            options.addOption(OptionBuilder.withLongOpt("subject")
                        .withDescription( "subject message property" )
                        .hasArg(true)
                        .withArgName("SUBJECT")
                        .create('s'));


        if(hasSingleLinkPerConnectionMode())
            options.addOption(OptionBuilder.withLongOpt("single-link-per-connection")
                .withDescription("acknowledgement mode: AMO|ALO|EO (At Least Once, At Most Once, Exactly Once")
                .hasArg(false)
                .create('Z'));

        if(hasFilterOption())
            options.addOption(OptionBuilder.withLongOpt("filter")
                .withDescription("filter, e.g. exact-subject=hello; matching-subject=%.a.#")
                .hasArg(true)
                .withArgName("<TYPE>=<VALUE>")
                .create('F'));


        if(hasTxnOption())
        {
            options.addOption("x","txn",false,"use transactions");
            options.addOption(OptionBuilder.withLongOpt("batch-size")
                .withDescription( "transaction batch size (default: 1)" )
                .hasArg(true)
                .withArgName("BATCH-SIZE")
                .create('B'));
            options.addOption(OptionBuilder.withLongOpt("rollback-ratio")
                .withDescription( "rollback ratio - must be between 0 and 1 (default: 0)" )
                .hasArg(true)
                .withArgName("RATIO")
                .create('R'));
        }

        if(hasLinkDurableOption())
        {
            options.addOption("d","durable-link",false,"use a durable link");
        }

        if(hasStdInOption())
            options.addOption("i","stdin",false,"read messages from stdin (one message per line)");

        options.addOption(OptionBuilder.withLongOpt("trace")
                    .withDescription("trace logging specified categories: RAW, FRM")
                    .hasArg(true)
                    .withArgName("TRACE")
                    .create('t'));
        if(hasSizeOption())
            options.addOption(OptionBuilder.withLongOpt("message-size")
                .withDescription( "size to pad outgoing messages to" )
                .hasArg(true)
                .withArgName("SIZE")
                .create('z'));

        if(hasResponseQueueOption())
            options.addOption(OptionBuilder.withLongOpt("response-queue")
                        .withDescription( "response queue to reply to" )
                        .hasArg(true)
                        .withArgName("RESPONSE_QUEUE")
                        .create('r'));

        if(hasLinkNameOption())
        {
            options.addOption(OptionBuilder.withLongOpt("link")
                        .withDescription( "link name" )
                        .hasArg(true)
                        .withArgName("LINK")
                        .create('l'));
        }

        if(hasWindowSizeOption())
        {
            options.addOption(OptionBuilder.withLongOpt("window-size")
                    .withDescription("credit window size")
                    .hasArg(true)
                    .withArgName("WINDOW-SIZE")
                    .create('W'));
        }

        CommandLine cmdLine = null;
        try
        {
             cmdLine = cmdLineParse.parse(options, args);

        }
        catch (ParseException e)
        {
            printUsage(options);
            System.exit(-1);
        }

        if(cmdLine.hasOption('h') || cmdLine.getArgList().isEmpty())
        {
            printUsage(options);
            System.exit(0);
        }
        _host = cmdLine.getOptionValue('H',"0.0.0.0");
        _remoteHost = cmdLine.getOptionValue('O',null);
        String portStr = cmdLine.getOptionValue('p',"5672");
        String countStr = cmdLine.getOptionValue('c',"1");

        _useSSL = cmdLine.hasOption('S');

        if(hasWindowSizeOption())
        {
            String windowSizeStr = cmdLine.getOptionValue('W',"100");
            _windowSize = Integer.parseInt(windowSizeStr);
        }

        if(hasSubjectOption())
        {
            _subject = cmdLine.getOptionValue('s');
        }

        if(cmdLine.hasOption('u'))
        {
            _username = cmdLine.getOptionValue('u');
        }

        if(cmdLine.hasOption('w'))
        {
            _password = cmdLine.getOptionValue('w');
        }

        if(cmdLine.hasOption('F'))
        {
            _filter = cmdLine.getOptionValue('F');
        }

        _port = Integer.parseInt(portStr);

        _containerName = cmdLine.getOptionValue('C');

        if(hasBlockOption())
            _block =  cmdLine.hasOption('b');

        if(hasLinkNameOption())
            _linkName = cmdLine.getOptionValue('l');


        if(hasLinkDurableOption())
            _durableLink = cmdLine.hasOption('d');

        if(hasCountOption())
            _count = Integer.parseInt(countStr);

        if(hasStdInOption())
            _useStdIn = cmdLine.hasOption('i');

        if(hasSingleLinkPerConnectionMode())
            _useMultipleConnections = cmdLine.hasOption('Z');

        if(hasTxnOption())
        {
            _useTran = cmdLine.hasOption('x');
            _batchSize = Integer.parseInt(cmdLine.getOptionValue('B',"1"));
            _rollbackRatio = Double.parseDouble(cmdLine.getOptionValue('R',"0"));
        }

        if(hasModeOption())
        {
            _mode = AcknowledgeMode.ALO;

            if(cmdLine.hasOption('k'))
            {
                _mode = AcknowledgeMode.valueOf(cmdLine.getOptionValue('k'));
            }
        }

        if(hasResponseQueueOption())
        {
            _responseQueue = cmdLine.getOptionValue('r');
        }

        _frameSize = Integer.parseInt(cmdLine.getOptionValue('f',"65536"));

        if(hasSizeOption())
        {
            _messageSize = Integer.parseInt(cmdLine.getOptionValue('z',"-1"));
        }

        String categoriesList = cmdLine.getOptionValue('t');
        String[]categories = categoriesList == null ? new String[0] : categoriesList.split("[, ]");
        for(String cat : categories)
        {
            if(cat.equalsIgnoreCase("FRM"))
            {
                FRAME_LOGGER.setLevel(Level.FINE);
                Formatter formatter = new Formatter()
                {
                    @Override
                    public String format(final LogRecord record)
                    {
                        return "[" + record.getMillis() + " FRM]\t" + record.getMessage() + "\n";
                    }
                };
                for(Handler handler : FRAME_LOGGER.getHandlers())
                {
                    FRAME_LOGGER.removeHandler(handler);
                }
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.FINE);
                handler.setFormatter(formatter);
                FRAME_LOGGER.addHandler(handler);
            }
            else if (cat.equalsIgnoreCase("RAW"))
            {
                RAW_LOGGER.setLevel(Level.FINE);
                Formatter formatter = new Formatter()
                {
                    @Override
                    public String format(final LogRecord record)
                    {
                        return "[" + record.getMillis() + " RAW]\t" + record.getMessage() + "\n";
                    }
                };
                for(Handler handler : RAW_LOGGER.getHandlers())
                {
                    RAW_LOGGER.removeHandler(handler);
                }
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.FINE);
                handler.setFormatter(formatter);
                RAW_LOGGER.addHandler(handler);
            }
        }


        _args = cmdLine.getArgs();

    }

    protected boolean hasFilterOption()
    {
        return false;
    }

    protected boolean hasSubjectOption()
    {
        return false;
    }

    protected boolean hasWindowSizeOption()
    {
        return false;
    }

    protected boolean hasSingleLinkPerConnectionMode()
    {
        return false;
    }

    protected abstract boolean hasLinkDurableOption();

    protected abstract boolean hasLinkNameOption();

    protected abstract boolean hasResponseQueueOption();

    protected abstract boolean hasSizeOption();

    protected abstract boolean hasBlockOption();

    protected abstract boolean hasStdInOption();

    protected abstract boolean hasTxnOption();

    protected abstract boolean hasModeOption();

    protected abstract boolean hasCountOption();

    public String getHost()
    {
        return _host;
    }

    public String getUsername()
    {
        return _username;
    }

    public String getPassword()
    {
        return _password;
    }

    public int getPort()
    {
        return _port;
    }

    public int getCount()
    {
        return _count;
    }

    public boolean useStdIn()
    {
        return _useStdIn;
    }

    public boolean useTran()
    {
        return _useTran;
    }

    public AcknowledgeMode getMode()
    {
        return _mode;
    }

    public boolean isBlock()
    {
        return _block;
    }

    public String[] getArgs()
    {
        return _args;
    }

    public int getMessageSize()
    {
        return _messageSize;
    }

    public String getResponseQueue()
    {
        return _responseQueue;
    }

    public int getBatchSize()
    {
        return _batchSize;
    }

    public double getRollbackRatio()
    {
        return _rollbackRatio;
    }

    public String getLinkName()
    {
        return _linkName;
    }

    public boolean isDurableLink()
    {
        return _durableLink;
    }

    public boolean isUseMultipleConnections()
    {
        return _useMultipleConnections;
    }

    public void setUseMultipleConnections(boolean useMultipleConnections)
    {
        _useMultipleConnections = useMultipleConnections;
    }

    public String getSubject()
    {
        return _subject;
    }

    public void setSubject(String subject)
    {
        _subject = subject;
    }

    protected abstract void printUsage(final Options options);

    protected abstract void run();


    public Connection newConnection() throws Connection.ConnectionException
    {
        Container container = getContainerName() == null ? new Container() : new Container(getContainerName());
        return getUsername() == null ? new Connection(getHost(), getPort(), null, null, _frameSize, container,
                                                      _remoteHost, _useSSL)
                                     : new Connection(getHost(), getPort(), getUsername(), getPassword(), _frameSize,
                                                      container, _remoteHost, _useSSL);
    }

    public String getContainerName()
    {
        return _containerName;
    }

    public int getWindowSize()
    {
        return _windowSize;
    }

    public void setWindowSize(int windowSize)
    {
        _windowSize = windowSize;
    }

    public String getFilter()
    {
        return _filter;
    }
}
