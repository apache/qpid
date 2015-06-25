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
import org.apache.qpid.qmf2.console.AgentHeartbeatWorkItem;
import org.apache.qpid.qmf2.console.AgentRestartedWorkItem;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.EventReceivedWorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;

/**
 * ConnectionLogger is a QMF2 class used to provide information about connections made to a broker.
 * <p>
 * In default mode ConnectionLogger lists the connections made to a broker along with information about sessions
 * such as whether any subscriptions are associated with the session (if a session has no subscriptions then it's
 * quite likely to be a "producer only" session, so this knowledge is quite useful).
 * <p>
 * In "log queue and binding" mode the information provided is very similar to qpid-config -b queues but with
 * additional connection related information provided as with default mode.
 *
 * <pre>
 * Usage: ConnectionLogger [options]
 * 
 * Options:
 *   -h, --help            show this help message and exit
 *   -q                    log queue and binding information for consumer connection
 *   -a &lt;address&gt;, --broker-address=&lt;address&gt;
 *                         broker-addr is in the form:  [username/password@]
 *                         hostname | ip-address [:&lt;port&gt;]   ex:  localhost,
 *                         10.1.1.7:10000, broker-host:10000,
 *                         guest/guest@localhost
 *   --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 * </pre>
 * @author Fraser Adams
 */
public final class ConnectionLogger implements QmfEventListener
{
    private static final String _usage =
    "Usage: ConnectionLogger [options]\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "  -q                    log queue and binding information for consumer connections\n" +
    "  -a <address>, --broker-address=<address>\n" +
    "                        broker-addr is in the form:  [username/password@]\n" +
    "                        hostname | ip-address [:<port>]   ex:  localhost,\n" +
    "                        10.1.1.7:10000, broker-host:10000,\n" +
    "                        guest/guest@localhost\n" +
    "  --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n";

    private Console _console;
    private boolean _logQueues;

    // If any queues get added or deleted we set this to flag that we need to re-log connections on next heartbeat.
    private boolean _stateChanged = false; 

    /**
     * Basic constructor. Creates JMS Session, Initialises Destinations, Producers &amp; Consumers and starts connection.
     * @param url the connection URL.
     * @param connectionOptions the options String to pass to ConnectionHelper.
     * @param logQueues flags whether queue &amp; binding information is logged as well as connection info.
     */
    public ConnectionLogger(final String url, final String connectionOptions, final boolean logQueues)
    {
        try
        {
            Connection connection = ConnectionHelper.createConnection(url, connectionOptions);        
            _console = new Console(this);
            _console.addConnection(connection);
            _logQueues = logQueues;
            System.out.println("Hit Return to exit");
            logConnectionInformation();
        }
        catch (QmfException qmfe)
        {
            System.err.println ("QmfException " + qmfe.getMessage() + " caught in ConnectionLogger constructor");
        }
    }

    /**
     * Finds a QmfConsoleData in a List of QMF Objects that matches a given ObjectID
     *
     * More or less a direct Java port of findById from qpid-config
     *
     * @param items the List of QMF Objects to search
     * @param id the ObjectId we're searching the List for
     * @return return the found object as a QmfConsoleData else return null
     */
    private QmfConsoleData findById(final List<QmfConsoleData> items, final ObjectId id)
    {
        for (QmfConsoleData item : items)
        {
            if (item.getObjectId().equals(id))
            {
                return item;
            }
        }

        return null;
    }

    /**
     * For every queue list the bindings (equivalent of qpid-config -b queues)
     *
     * More or less a direct Java port of QueueListRecurse in qpid-config, which handles qpid-config -b queues
     *
     * @param ref If ref is null list info about all queues else list info about queue referenced by ObjectID
     */
    private void logQueueInformation(final ObjectId ref)
    {
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");
        List<QmfConsoleData> bindings = _console.getObjects("org.apache.qpid.broker", "binding");
        List<QmfConsoleData> exchanges = _console.getObjects("org.apache.qpid.broker", "exchange");

        for (QmfConsoleData queue : queues)
        {
            ObjectId queueId = queue.getObjectId();

            if (ref == null || ref.equals(queueId))
            {
                System.out.printf("    Queue '%s'\n", queue.getStringValue("name"));
                System.out.println("        arguments " + (Map)queue.getValue("arguments"));

                for (QmfConsoleData binding : bindings)
                {
                    ObjectId queueRef = binding.getRefValue("queueRef");

                    if (queueRef.equals(queueId))
                    {
                        ObjectId exchangeRef = binding.getRefValue("exchangeRef");
                        QmfConsoleData exchange = findById(exchanges, exchangeRef);

                        String exchangeName = "<unknown>";
                        if (exchange != null)
                        {
                            exchangeName = exchange.getStringValue("name");
                            if (exchangeName.equals(""))
                            {
                                exchangeName = "''";
                            }
                        }

                        String bindingKey = binding.getStringValue("bindingKey");
                        Map arguments = (Map)binding.getValue("arguments");
                        if (arguments.isEmpty())
                        {
                            System.out.printf("        bind [%s] => %s\n", bindingKey, exchangeName);
                        }
                        else
                        {
                            // If there are binding arguments then it's a headers exchange
                            System.out.printf("        bind [%s] => %s %s\n", bindingKey, exchangeName, arguments);
                        }
                    }
                }
            }
        }
    }

    /**
     * Logs audit information about each connection made to the broker
     *
     * Obtains connection, session and subscription objects and iterates in turn through these comparing
     * references to find the subscriptions association with sessions and sessions associated with
     * connections. Ultimately it then uses logQueueInformation to display the queues associated with
     * each subscription.
     */
    private void logConnectionInformation()
    {
        System.out.println("\n\n**** ConnectionLogger: Logging current connection information ****");

        List<QmfConsoleData> connections = _console.getObjects("org.apache.qpid.broker", "connection");
        List<QmfConsoleData> sessions = _console.getObjects("org.apache.qpid.broker", "session");
        List<QmfConsoleData> subscriptions = _console.getObjects("org.apache.qpid.broker", "subscription");

        for (QmfConsoleData connection : connections)
        {
            System.out.printf("\nConnection '%s'\n", connection.getStringValue("address"));

            String[] properties = {"authIdentity","remoteProcessName", "federationLink"};
            for (String p : properties)
            {
                System.out.println(p + ": " + connection.getStringValue(p));
            }

            System.out.println("createTimestamp: " + new Date(connection.getCreateTime()/1000000l));

            ObjectId connectionId = connection.getObjectId();
            for (QmfConsoleData session : sessions)
            { // Iterate through all session objects
                ObjectId connectionRef = session.getRefValue("connectionRef");
                if (connectionRef.equals(connectionId))
                { // But only select sessions that are associated with the connection under consideration.
                    System.out.printf("Session '%s'\n", session.getStringValue("name"));
                    int subscriptionCount = 0;
                    ObjectId sessionId = session.getObjectId();
                    for (QmfConsoleData subscription : subscriptions)
                    { // Iterate through all subscription objects
                        ObjectId sessionRef = subscription.getRefValue("sessionRef");
                        if (sessionRef.equals(sessionId))
                        { // But only select subscriptions that are associated with the session under consideration.
                            subscriptionCount++;
                            ObjectId queueRef = subscription.getRefValue("queueRef");
                            if (_logQueues)
                            {
                                logQueueInformation(queueRef);
                            }
                        }
                    }
                    if (subscriptionCount == 0)
                    {
                        System.out.println("    ** No Subscriptions for this Session - probably a producer only Session **");
                    }
                }
            }
        }
    }

    /**
     * Listener for QMF2 WorkItems
     * <p>
     * This method looks for clientConnect or clientDisconnect Events and uses these as a trigger to log the new
     * connection state when the next Heartbeat occurs.
     * <p>
     * There are a couple of reasons for using this approach rather than just calling logConnectionInformation()
     * as soon as we see the clientConnect or clientDisconnect Event.
     * <p>
     * 1. We could potentially have lots of connection Events and redisplaying all of the connections for each
     *    Event is likely to be confusing.
     * <p>
     * 2. When a clientConnect Event occurs we don't have all of the informatin that we might need, for example this
     *    application checks the Session and Subscription information and also optionally Queue and Binding information.
     *    Creating Sessions/Subscriptions won't generally occur until some (possibly small, but possibly not) time
     *    after the Connection has been made. The approach taken here reduces spurious assertions that a Session is
     *    probably a "producer only" Session. As one of the use-cases for this tool is to attempt to flag up "producer
     *    only" Sessions we want to try and make it as reliable as possible.
     *
     * @param wi a QMF2 WorkItem object
     */
    public void onEvent(final WorkItem wi)
    {
        if (wi instanceof EventReceivedWorkItem)
        {
            EventReceivedWorkItem item = (EventReceivedWorkItem)wi;
            Agent agent = item.getAgent();
            QmfEvent event = item.getEvent();

            String className = event.getSchemaClassId().getClassName();
            if (className.equals("clientConnect") ||
                className.equals("clientDisconnect"))
            {
                _stateChanged = true;
            }
        }
        else if (wi instanceof AgentRestartedWorkItem)
        {
            _stateChanged = true;
        }
        else if (wi instanceof AgentHeartbeatWorkItem)
        {
            AgentHeartbeatWorkItem item = (AgentHeartbeatWorkItem)wi;
            Agent agent = item.getAgent();

            if (_stateChanged && agent.getName().contains("qpidd"))
            {
                logConnectionInformation();
                _stateChanged = false;
            }
        }
    }

    /**
     * Runs ConnectionLogger.
     * @param args the command line arguments.
     */
    public static void main(final String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        String[] longOpts = {"help", "broker-address=", "sasl-mechanism="};
        try
        {
            String host = "localhost";
            String connectionOptions = "{reconnect: true}";
            boolean logQueues = false;

            GetOpt getopt = new GetOpt(args, "ha:q", longOpts);
            List<String[]> optList = getopt.getOptList();
            String[] cargs = {};
            cargs = getopt.getEncArgs().toArray(cargs);

            for (String[] opt : optList)
            {
                if (opt[0].equals("-h") || opt[0].equals("--help"))
                {
                    System.out.println(_usage);
                    System.out.println(_options);
                    System.exit(1);
                }
                else if (opt[0].equals("-a") || opt[0].equals("--broker-address"))
                {
                    host = opt[1];
                }
                else if (opt[0].equals("-q"))
                {
                    logQueues = true;
                }
                else if (opt[0].equals("--sasl-mechanism"))
                {
                    connectionOptions = "{reconnect: true, sasl_mechs: " + opt[1] + "}";
                }
            }

            ConnectionLogger logger = new ConnectionLogger(host, connectionOptions, logQueues);
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(_usage);
            System.out.println(e.getMessage());
            System.exit(1);
        }
    
        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("ConnectionLogger main(): IOException: " + e.getMessage());
        }
    }
}
