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
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.SchemaClassId;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.EventReceivedWorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;

import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;

/**
 * QueueFuse provides protection to message producers from consumers who can't consume messages fast enough.
 * <p>
 * With the default "reject" limit policy when a queue exceeds its capacity an exception is thrown to the
 * producer. This behaviour is unfortunate, because if there happen to be multiple consumers consuming
 * messages from a given producer it is possible for a single slow consumer to cause message flow to be
 * stopped to <u>all</u> consumers, in other words a de-facto denial of service may take place.
 * <p>
 * In an Enterprise environment it is likely that this sort of behaviour is unwelcome, so QueueFuse makes it
 * possible for queueThresholdExceeded Events to be detected and for the offending queues to have messages
 * purged, thus protecting the other consumers by preventing an exception being thrown to the message producer.
 * <p>
 * The original intention with this class was to unbind bindings to queues that exceed the threshold. This method
 * works, but it has a number of disadvantages. In particular there is no way to unbind from (and thus protect)
 * queues bound to the default direct exchange, in addition in order to unbind it is necessary to retrieve
 * binding and exchange information, both of which require further exchanges with the broker (which is not
 * desirable as when the queueThresholdExceeded occurs we need to act pretty quickly). Finally as it happens
 * it is also necessary to purge some messages after unbinding anyway as if this is not done the queue remains
 * in the flowStopped state and producers will eventually time out and throw an exception if this is not cleared.
 * So all in all simply purging each time we cross the threshold is simpler and has the additional advantage that
 * if and when the consumer speeds up message delivery will eventually return to normal. 
 * 
 * <pre>
 * Usage: QueueFuse [options] [broker-addr]...
 * 
 * Monitors one or more Qpid message brokers for queueThresholdExceeded Events.
 *
 * If a queueThresholdExceeded Event occurs messages are purged from the queue,
 * in other words this class behaves rather like a fuse 'blowing' if the
 * threshold gets exceeded.
 *
 * If no broker-addr is supplied, QueueFuse connects to 'localhost:5672'.
 *
 * [broker-addr] syntax:
 *
 * [username/password@] hostname
 * ip-address [:&lt;port&gt;]
 * 
 * Examples:
 * 
 * $ QueueFuse localhost:5672
 * $ QueueFuse 10.1.1.7:10000
 * $ QueueFuse guest/guest@broker-host:10000
 *
 * Options:
 *   -h, --help            show this help message and exit
 *   -f &lt;filter&gt;, --filter=&lt;filter&gt;
 *                         a list of comma separated queue names (regex are
 *                         accepted) to protect (default is to protect all).
 *   -p &lt;PERCENT&gt;, --purge=&lt;PERCENT&gt;\n" +
 *                         The percentage of messages to purge when the queue\n" +
 *                         threshold gets exceeded (default = 20%).\n" +
 *                         N.B. if this gets set too low the fuse may not blow.\n" +
 *   --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 * </pre>
 * @author Fraser Adams
 */
public final class QueueFuse implements QmfEventListener
{
    private static final String _usage =
    "Usage: QueueFuse [options] [broker-addr]...\n";

    private static final String _description =
    "Monitors one or more Qpid message brokers for queueThresholdExceeded Events.\n" +
    "\n" +
    "If a queueThresholdExceeded Event occurs messages are purged from the queue,\n" +
    "in other words this class behaves rather like a fuse 'blowing' if the\n" +
    "threshold gets exceeded.\n" +
    "\n" +
    "If no broker-addr is supplied, QueueFuse connects to 'localhost:5672'.\n" +
    "\n" +
    "[broker-addr] syntax:\n" +
    "\n" +
    "[username/password@] hostname\n" +
    "ip-address [:<port>]\n" +
    "\n" +
    "Examples:\n" +
    "\n" +
    "$ QueueFuse localhost:5672\n" +
    "$ QueueFuse 10.1.1.7:10000\n" +
    "$ QueueFuse guest/guest@broker-host:10000\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "  -f <filter>, --filter=<filter>\n" +
    "                        a list of comma separated queue names (regex are\n" +
    "                        accepted) to protect (default is to protect all).\n" +
    "  -p <PERCENT>, --purge=<PERCENT>\n" +
    "                        The percentage of messages to purge when the queue\n" +
    "                        threshold gets exceeded (default = 20%).\n" +
    "                        N.B. if this gets set too low the fuse may not blow.\n" +
    "  --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n";

    private final String _url;
    private final List<Pattern> _filter;
    private final float _purge;
    private Map<String, QmfConsoleData> _queueCache = new HashMap<String, QmfConsoleData>(50);
    private Console _console;

    /**
     * Basic constructor. Creates JMS Session, Initialises Destinations, Producers & Consumers and starts connection.
     * @param url the connection URL.
     * @param connectionOptions the options String to pass to ConnectionHelper.
     * @param filter a list of regex Patterns used to choose the queues we wish to protect.
     * @param purge the ratio of messages that we wish to purge if the threshold gets exceeded.
     */
    public QueueFuse(final String url, final String connectionOptions, final List<Pattern> filter, final float purge)
    {
        System.out.println("QueueFuse Connecting to " + url);
        if (filter.size() > 0)
        {
            System.out.println("Filter = " + filter);
        }
        _url = url;
        _filter = filter;
        _purge = purge;
        try
        {
            Connection connection = ConnectionHelper.createConnection(url, connectionOptions);        
            _console = new Console(this);
            _console.addConnection(connection);
            updateQueueCache();
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught in QueueFuse constructor");
        }
    }

    /**
     * Looks up queue objects and stores them in _queueCache keyed by the queue name
     */
    private void updateQueueCache()
    {
        _queueCache.clear();
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");
        for (QmfConsoleData queue : queues)
        {
            String queueName = queue.getStringValue("name");
            _queueCache.put(queueName, queue);
        }
    }

    /**
     * Look up a queue object with the given name and if it's not a ring queue invoke the queue's purge method.
     * @param queueName the name of the queue to purge
     * @param msgDepth the number of messages on the queue, used to determine how many messages to purge.
     */
    private void purgeQueue(final String queueName, long msgDepth)
    {
        QmfConsoleData queue = _queueCache.get(queueName);

        if (queue == null)
        {
            System.out.printf("%s ERROR QueueFuse.disconnectQueue() %s reference couldn't be found\n",
                              new Date().toString(), queueName);
        }
        else
        { // If we've found a queue called queueName we then find the bindings that reference it.

            Map args = (Map)queue.getValue("arguments");
            String policyType = (String)args.get("qpid.policy_type");
            if (policyType != null && policyType.equals("ring"))
            {  // If qpid.policy_type=ring we return.
                return;
            }

            try
            {
                QmfData arguments = new QmfData();
                arguments.setValue("request", (long)(_purge*msgDepth));
                queue.invokeMethod("purge", arguments);
            }
            catch (QmfException e)
            {
                System.out.println(e.getMessage());
            }    
        }
    }

    /**
     * Main Event handler.
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

            if (className.equals("queueDeclare"))
            {
                updateQueueCache();
            }
            else if (className.equals("queueThresholdExceeded"))
            {
                String queueName = event.getStringValue("qName");
                boolean matches = false;
                for (Pattern x : _filter)
                { // Check the queue name against the regexes in the filter List (if any)
                    Matcher m = x.matcher(queueName);
                    if (m.find())
                    {
                        matches = true;
                        break;
                    }
                }

                if (_filter.isEmpty() || matches)
                { // If there's no filter enabled or the filter matches the queue name we call purgeQueue().
                    long msgDepth = event.getLongValue("msgDepth");
                    purgeQueue(queueName, msgDepth);
                }
            }
        }
    }

    /**
     * Runs QueueFuse.
     * @param args the command line arguments.
     */
    public static void main(final String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        String[] longOpts = {"help", "filter=", "purge=", "sasl-mechanism="};
        try
        {
            boolean includeRingQueues = false;
            String connectionOptions = "{reconnect: true}";
            List<Pattern> filter = new ArrayList<Pattern>();
            float purge = 0.2f;
            GetOpt getopt = new GetOpt(args, "hf:p:", longOpts);
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
                else if (opt[0].equals("-f") || opt[0].equals("--filter"))
                {
                    String[] split = opt[1].split(",");
                    for (String s : split)
                    {
                        Pattern p = Pattern.compile(s);
                        filter.add(p);
                    }
                }
                else if (opt[0].equals("-p") || opt[0].equals("--purge"))
                {
                    int percent = Integer.parseInt(opt[1]);
                    if (percent < 0 || percent > 100)
                    {
                        System.out.println(_usage);
                        System.exit(1);
                    }
                    purge = percent/100.0f;
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
                QueueFuse queueFuse = new QueueFuse(url, connectionOptions, filter, purge);
            }
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(_usage);
            System.out.println(e.getMessage());
            System.exit(1);
        }

        try
        {   // Block here
            Thread.currentThread().join();
        }
        catch (InterruptedException ie)
        {
        }
    }
}
