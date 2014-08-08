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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;
import org.apache.qpid.qmf2.common.SchemaClassId;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.AgentHeartbeatWorkItem;
import org.apache.qpid.qmf2.console.AgentRestartedWorkItem;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.QmfConsoleData;

import org.apache.qpid.qmf2.console.SubscribeIndication;
import org.apache.qpid.qmf2.console.SubscribeParams;
import org.apache.qpid.qmf2.console.SubscriptionIndicationWorkItem;

import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;

/**
 * Collect and print queue statistics.
 * <pre>
 * Usage: QpidQueueStats [options]
 * 
 * Options:
 *   -h, --help            show this help message and exit
 *   -a &lt;address&gt;, --broker-address=&lt;address&gt;
 *                         broker-addr is in the form:  [username/password@]
 *                         hostname | ip-address [:&lt;port&gt;]   ex:  localhost,
 *                         10.1.1.7:10000, broker-host:10000,
 *                         guest/guest@localhost
 *   -f &lt;filter&gt;, --filter=&lt;filter&gt;
 *                         a list of comma separated queue names (regex are
 *                         accepted) to show
 *   --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 * </pre>
 * @author Fraser Adams
 */
public final class QpidQueueStats implements QmfEventListener
{
    private final class Stats
    {
        private final String _name;
        private QmfConsoleData _data;

        public Stats(final String name, final QmfConsoleData data)
        {
            _name = name;
            _data = data;
        }

        public String getName()
        {
            return _name;
        }

        public QmfConsoleData getData()
        {
            return _data;
        }

        public void setData(final QmfConsoleData data)
        {
            _data = data;
        }
    }

    private static final String _usage =
    "Usage: QpidQueueStats [options]\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "  -a <address>, --broker-address=<address>\n" +
    "                        broker-addr is in the form:  [username/password@]\n" +
    "                        hostname | ip-address [:<port>]   ex:  localhost,\n" +
    "                        10.1.1.7:10000, broker-host:10000,\n" +
    "                        guest/guest@localhost\n" +
    "  -f <filter>, --filter=<filter>\n" +
    "                        a list of comma separated queue names (regex are\n" +
    "                        accepted) to show\n" +
    "  --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n";

    private final String _url;
    private final List<Pattern> _filter;
    private Agent _broker;
    private Console _console;
    private Map<ObjectId, Stats> _objects = new HashMap<ObjectId, Stats>();
    private String _subscriptionId = null;
    private long _subscriptionDuration;
    private long _startTime;

    /**
     * Basic constructor. Creates JMS Session, Initialises Destinations, Producers &amp; Consumers and starts connection.
     * @param url the connection URL.
     * @param connectionOptions the options String to pass to ConnectionHelper.
     * @param filter a list of regex Patterns used to choose the queues we wish to display.
     */
    public QpidQueueStats(final String url, final String connectionOptions, final List<Pattern> filter)
    {
        System.out.println("Connecting to " + url);
        if (filter.size() > 0)
        {
            System.out.println("Filter = " + filter);
        }
        _url = url;
        _filter = filter;
        try
        {
            Connection connection = ConnectionHelper.createConnection(url, connectionOptions);        
            _console = new Console(this);
            _console.addConnection(connection);

            // Wait until the broker Agent has been discovered
            _broker = _console.findAgent("broker");
            if (_broker != null)
            {
                createQueueSubscription();
            }

            System.out.println("Hit Return to exit");
            System.out.println(
                "Queue Name                                          Sec       Depth     Enq Rate     Deq Rate");
            System.out.println(
                "=============================================================================================");
        }
        catch (QmfException qmfe)
        {
            System.err.println ("QmfException " + qmfe.getMessage() + " caught in QpidQueueStats constructor");
        }
    }

    /**
     * Create a Subscription to query for all queue objects
     */
    private void createQueueSubscription()
    {
        try
        {   // This QmfQuery simply does an ID query for objects with the className "queue"
            QmfQuery query = new QmfQuery(QmfQueryTarget.OBJECT, new SchemaClassId("queue"));
            SubscribeParams params = _console.createSubscription(_broker, query, "queueStatsHandle");
            _subscriptionId = params.getSubscriptionId();
            _subscriptionDuration = params.getLifetime() - 10; // Subtract 10 as we want to refresh before it times out
            _startTime = System.currentTimeMillis();
        }
        catch (QmfException qmfe)
        {
        }
    }

    /**
     * Main Event handler. Checks if the WorkItem is a SubscriptionIndicationWorkItem, if it is it stores the object
     * in a Map and uses this to maintain state so we can record deltas such as enqueue and dequeue rates.
     * <p>
     * The AgentHeartbeatWorkItem is used to periodically compare the elapsed time against the Subscription duration
     * so that we can refresh the Subscription (or create a new one if necessary) in order to continue receiving
     * queue Management Object data from the broker.
     * <p>
     * When the AgentRestartedWorkItem is received we clear the state to remove any stale queue Management Objects.
     * @param wi a QMF2 WorkItem object
     */
    public void onEvent(final WorkItem wi)
    {
        if (wi instanceof AgentHeartbeatWorkItem && _subscriptionId != null)
        {
            long elapsed = (long)Math.round((System.currentTimeMillis() - _startTime)/1000.0f);    
            if (elapsed > _subscriptionDuration)
            {
                try
                {
                    _console.refreshSubscription(_subscriptionId);
                    _startTime = System.currentTimeMillis();
                }
                catch (QmfException qmfe)
                {
                    System.err.println ("QmfException " + qmfe.getMessage() + " caught in QpidQueueStats onEvent");
                   createQueueSubscription();
                }
            }
        }
        else if (wi instanceof AgentRestartedWorkItem)
        {
            _objects.clear();
        }
        else if (wi instanceof SubscriptionIndicationWorkItem)
        {
            SubscriptionIndicationWorkItem item = (SubscriptionIndicationWorkItem)wi;
            SubscribeIndication indication = item.getSubscribeIndication();
            String correlationId = indication.getConsoleHandle();
            if (correlationId.equals("queueStatsHandle"))
            { // If it is (and it should be!!) then it's our queue object Subscription
                List<QmfConsoleData> data = indication.getData();
                for (QmfConsoleData record : data)
                {
                    ObjectId id = record.getObjectId();
                    if (record.isDeleted())
                    { // If the object was deleted by the Agent we remove it from out Map
                        _objects.remove(id);
                    }
                    else
                    {
                        if (_objects.containsKey(id))
                        { // If the object is already in the Map it's likely to be a statistics push from the broker.
                            Stats stats = _objects.get(id);
                            String name = stats.getName();

                            boolean matches = false;
                            for (Pattern x : _filter)
                            { // Check the queue name against the regexes in the filter List (if any)
                                Matcher m = x.matcher(name);
                                if (m.find())
                                {
                                    matches = true;
                                    break;
                                }
                            }

                            if (_filter.isEmpty() || matches)
                            { // If there's no filter enabled or the filter matches the queue name we display statistics.
                                QmfConsoleData lastSample = stats.getData();
                                stats.setData(record);
    
                                float deltaTime = record.getUpdateTime() - lastSample.getUpdateTime();
                                if (deltaTime > 1000000000.0f)
                                {
                                    float deltaEnqueues = record.getLongValue("msgTotalEnqueues") -
                                                          lastSample.getLongValue("msgTotalEnqueues");
                                    float deltaDequeues = record.getLongValue("msgTotalDequeues") -
                                                          lastSample.getLongValue("msgTotalDequeues");
                                    long msgDepth = record.getLongValue("msgDepth");
                                    float enqueueRate = deltaEnqueues/(deltaTime/1000000000.0f);
                                    float dequeueRate = deltaDequeues/(deltaTime/1000000000.0f);

                                    System.out.printf("%-46s%10.2f%11d%13.2f%13.2f\n", 
                                                      name, deltaTime/1000000000, msgDepth, enqueueRate, dequeueRate);
                                }
                            }
                        }
                        else
                        { // If the object isn't in the Map it's likely to be a properties push from the broker.
                            if (!record.hasValue("name"))
                            { // This probably won't happen, but if it does we refresh the object to get its full state.
                                try
                                {
                                    record.refresh();
                                }
                                catch (QmfException qmfe)
                                {  
                                } 
                            }
                            String queueName = record.getStringValue("name");
                            _objects.put(id, new Stats(queueName, record));
                        }
                    }
                }
            }
        }
    }

    /**
     * Runs QpidQueueStats.
     * @param args the command line arguments.
     */
    public static void main(final String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        String[] longOpts = {"help", "broker-address=", "filter=", "sasl-mechanism="};
        try
        {
            String host = "localhost";
            String connectionOptions = "{reconnect: true}";
            List<Pattern> filter = new ArrayList<Pattern>();
            GetOpt getopt = new GetOpt(args, "ha:f:", longOpts);
            List<String[]> optList = getopt.getOptList();

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
                else if (opt[0].equals("-f") || opt[0].equals("--filter"))
                {
                    String[] split = opt[1].split(",");
                    for (String s : split)
                    {
                        Pattern p = Pattern.compile(s);
                        filter.add(p);
                    }
                }
                else if (opt[0].equals("--sasl-mechanism"))
                {
                    connectionOptions = "{reconnect: true, sasl_mechs: " + opt[1] + "}";
                }
            }

            QpidQueueStats queueStats = new QpidQueueStats(host, connectionOptions, filter);
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
            System.out.println ("QpidQueueStats main(): IOException: " + e.getMessage());
        }
    }
}
