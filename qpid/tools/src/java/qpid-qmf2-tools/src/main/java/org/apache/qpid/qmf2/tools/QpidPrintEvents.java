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
package org.apache.qpid.qmf2.tools;

// JMS Imports
import javax.jms.Connection;

// Misc Imports
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.EventReceivedWorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * Collect and print events from one or more Qpid message brokers.
 * <pre>
 * If no broker-addr is supplied, QpidPrintEvents connects to 'localhost:5672'.
 * 
 * [broker-addr] syntax:
 * 
 * [username/password@] hostname
 * ip-address [:&lt;port&gt;]
 * 
 * Examples:
 * 
 * $ QpidPrintEvents localhost:5672
 * $ QpidPrintEvents 10.1.1.7:10000
 * $ QpidPrintEvents guest/guest@broker-host:10000
 * 
 * Options:
 *   -h, --help            show this help message and exit
 *   --heartbeats          Use heartbeats.
 *   --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 * </pre>
 * @author Fraser Adams
 */
public final class QpidPrintEvents implements QmfEventListener
{
    private static final String _usage =
    "Usage: QpidPrintEvents [options] [broker-addr]...\n";

    private static final String _description =
    "Collect and print events from one or more Qpid message brokers.\n" +
    "\n" +
    "If no broker-addr is supplied, QpidPrintEvents connects to 'localhost:5672'.\n" +
    "\n" +
    "[broker-addr] syntax:\n" +
    "\n" +
    "[username/password@] hostname\n" +
    "ip-address [:<port>]\n" +
    "\n" +
    "Examples:\n" +
    "\n" +
    "$ QpidPrintEvents localhost:5672\n" +
    "$ QpidPrintEvents 10.1.1.7:10000\n" +
    "$ QpidPrintEvents guest/guest@broker-host:10000\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "  --heartbeats          Use heartbeats.\n" +
    "  --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n";

    private final String _url;
    private Console _console;

    /**
     * Basic constructor. Creates JMS Session, Initialises Destinations, Producers &amp; Consumers and starts connection.
     * @param url the connection URL.
     * @param connectionOptions the options String to pass to ConnectionHelper.
     */
    public QpidPrintEvents(final String url, final String connectionOptions)
    {
        System.out.println("Connecting to " + url);
        _url = url;
        try
        {
            Connection connection = ConnectionHelper.createConnection(url, connectionOptions);        
            _console = new Console(this);
            _console.addConnection(connection);
        }
        catch (QmfException qmfe)
        {
            System.err.println ("QmfException " + qmfe.getMessage() + " caught in QpidPrintEvents constructor");
        }
    }

    /**
     * Checks if the WorkItem is an EventReceivedWorkItem and if it is extracts and renders the QmfEvent.
     * @param wi a QMF2 WorkItem object
     */
    public void onEvent(final WorkItem wi)
    {
        if (wi instanceof EventReceivedWorkItem)
        {
            EventReceivedWorkItem item = (EventReceivedWorkItem)wi;
            QmfEvent event = item.getEvent();
            System.out.println(event + " broker=" + _url);
        }
    }

    /**
     * Runs QpidPrintEvents.
     * @param args the command line arguments.
     */
    public static void main(final String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        String[] longOpts = {"help", "heartbeats", "sasl-mechanism="};
        try
        {
            String connectionOptions = "{reconnect: true}";
            GetOpt getopt = new GetOpt(args, "h", longOpts);
            List<String[]> optList = getopt.getOptList();
            String[] cargs = {};
            cargs = getopt.getEncArgs().toArray(cargs);
            for (String[] opt : optList)
            {
                if (opt[0].equals("-h") || opt[0].equals("--help"))
                {
                    System.out.println(_usage);
                    System.out.println(_description);
                    System.out.println(_options);
                    System.exit(1);
                }
                else if (opt[0].equals("--heartbeats"))
                {
                    // Ignore Java uses heartbeats by default
                }
                else if (opt[0].equals("--sasl-mechanism"))
                {
                    connectionOptions = "{reconnect: true, sasl_mechs: " + opt[1] + "}";
                }
            }

            int nargs = cargs.length;
            if (nargs == 0)
            {
                cargs = new String[] {"localhost"};
            }

            for (String url : cargs)
            {
                QpidPrintEvents eventPrinter = new QpidPrintEvents(url, connectionOptions);
            }
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(_usage);
            System.exit(1);
        }

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("QpidPrintEvents main(): IOException: " + e.getMessage());
        }
    }
}
