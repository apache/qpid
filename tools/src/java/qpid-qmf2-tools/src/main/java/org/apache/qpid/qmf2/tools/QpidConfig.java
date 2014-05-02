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

import javax.jms.Connection;

// Misc Imports
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Date;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;

/**
 * QpidConfig is a fairly "literal" Java port of the python qpid-config tool.
 * <p>
 * It's vaguely pointless, as the python qpid-config is the "canonical" qpid-config :-)
 * Nonetheless, it's a useful intellectual exercise to illustrate using QMF2 from Java.
 * <p>
 * QpidConfig (unlike the python qpid-config) uses pure QMF2 for adding/deleting queues, exchanges & bindings
 * this provides useful illustration of how to do these things using the ManagementAgent method calls.
 * <p>
 * N.B. "create" and "delete" broker ManagementAgent methods were added in Qpid version 0.10, unfortunately these
 * calls won't work for earlier versions of Qpid.
 * <pre>
 * Usage:  qpid-config [OPTIONS]
 *         qpid-config [OPTIONS] exchanges [filter-string]
 *         qpid-config [OPTIONS] queues    [filter-string]
 *         qpid-config [OPTIONS] add exchange &lt;type&gt; &lt;name&gt; [AddExchangeOptions]
 *         qpid-config [OPTIONS] del exchange &ltname&gt;
 *         qpid-config [OPTIONS] add queue &lt;name&gt; [AddQueueOptions]
 *         qpid-config [OPTIONS] del queue &lt;name&gt; [DelQueueOptions]
 *         qpid-config [OPTIONS] bind   &lt;exchange-name&gt; &lt;queue-name&gt; [binding-key]
 *                   &lt;for type xml&gt;     [-f -|filename]
 *                   &lt;for type header&gt;  [all|any] k1=v1 [, k2=v2...]
 *         qpid-config [OPTIONS] unbind &lt;exchange-name&gt; &lt;queue-name&gt; [binding-key]
 * 
 * ADDRESS syntax:
 * 
 *       [username/password@] hostname
 *       ip-address [:&lt;port&gt;]
 * 
 * Examples:
 * 
 * $ qpid-config add queue q
 * $ qpid-config add exchange direct d localhost:5672
 * $ qpid-config exchanges 10.1.1.7:10000
 * $ qpid-config queues guest/guest@broker-host:10000
 * 
 * Add Exchange &lt;type&gt; values:
 * 
 *     direct     Direct exchange for point-to-point communication
 *     fanout     Fanout exchange for broadcast communication
 *     topic      Topic exchange that routes messages using binding keys with wildcards
 *     headers    Headers exchange that matches header fields against the binding keys
 *     xml        XML Exchange - allows content filtering using an XQuery
 * 
 * 
 * Queue Limit Actions
 * 
 *     none (default) - Use broker's default policy
 *     reject         - Reject enqueued messages
 *     flow-to-disk   - Page messages to disk
 *     ring           - Replace oldest unacquired message with new
 *     ring-strict    - Replace oldest message, reject if oldest is acquired
 * 
 * Queue Ordering Policies
 * 
 *     fifo (default) - First in, first out
 *     lvq            - Last Value Queue ordering, allows queue browsing
 *     lvq-no-browse  - Last Value Queue ordering, browsing clients may lose data
 * 
 * Options:
 *   -h, --help            show this help message and exit
 * 
 *   General Options:
 *     -t &lt;secs&gt;, --timeout=&lt;secs&gt;
 *                         Maximum time to wait for broker connection (in
 *                         seconds)
 *     -b, --bindings      Show bindings in queue or exchange list
 *     -a &lt;address&gt;, --broker-addr=&lt;address&gt;
 *                         Maximum time to wait for broker connection (in
 *                         seconds)
 *     --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 * 
 *   Options for Adding Exchanges and Queues:
 *     --alternate-exchange=&lt;aexname&gt;
 *                         Name of the alternate-exchange for the new queue or
 *                         exchange. Exchanges route messages to the alternate
 *                         exchange if they are unable to route them elsewhere.
 *                         Queues route messages to the alternate exchange if
 *                         they are rejected by a subscriber or orphaned by queue
 *                         deletion.
 *     --passive, --dry-run
 *                         Do not actually add the exchange or queue, ensure that
 *                         all parameters and permissions are correct and would
 *                         allow it to be created.
 *     --durable           The new queue or exchange is durable.
 * 
 *   Options for Adding Queues:
 *     --file-count=&lt;n&gt;    Number of files in queue's persistence journal
 *     --file-size=&lt;n&gt;     File size in pages (64Kib/page)
 *     --max-queue-size=&lt;n&gt;
 *                         Maximum in-memory queue size as bytes
 *     --max-queue-count=&lt;n&gt;
 *                         Maximum in-memory queue size as a number of messages
 *     --limit-policy=&lt;policy&gt;
 *                         Action to take when queue limit is reached
 *     --order=&lt;ordering&gt;  Queue ordering policy
 *     --generate-queue-events=&lt;n&gt;
 *                         If set to 1, every enqueue will generate an event that
 *                         can be processed by registered listeners (e.g. for
 *                         replication). If set to 2, events will be generated
 *                         for enqueues and dequeues.
 *     --flow-stop-size=&lt;n&gt;
 *                         Turn on sender flow control when the number of queued
 *                         bytes exceeds this value.
 *     --flow-resume-size=&lt;n&gt;
 *                         Turn off sender flow control when the number of queued
 *                         bytes drops below this value.
 *     --flow-stop-count=&lt;n&gt;
 *                         Turn on sender flow control when the number of queued
 *                         messages exceeds this value.
 *     --flow-resume-count=&lt;n&gt;
 *                         Turn off sender flow control when the number of queued
 *                         messages drops below this value.
 *     --argument=&lt;NAME=VALUE&gt;
 *                         Specify a key-value pair to add to queue arguments
 * 
 *   Options for Adding Exchanges:
 *     --sequence          Exchange will insert a 'qpid.msg_sequence' field in
 *                         the message header
 *     --ive               Exchange will behave as an 'initial-value-exchange',
 *                         keeping a reference  to the last message forwarded and
 *                         enqueuing that message to newly bound queues.
 * 
 *   Options for Deleting Queues:
 *     --force             Force delete of queue even if it's currently used or
 *                         it's not empty
 *     --force-if-not-empty
 *                         Force delete of queue even if it's not empty
 *     --force-if-not-used
 *                         Force delete of queue even if it's currently used
 * 
 *   Options for Declaring Bindings:
 *     -f &lt;file.xq&gt;, --file=&lt;file.xq&gt;
 *                         For XML Exchange bindings - specifies the name of a
 *                         file containing an XQuery.
 * 
 * </pre>
 * @author Fraser Adams
 */
public final class QpidConfig
{
    private static final String _usage =
    "Usage:  qpid-config [OPTIONS]\n" +
    "        qpid-config [OPTIONS] exchanges [filter-string]\n" +
    "        qpid-config [OPTIONS] queues    [filter-string]\n" +
    "        qpid-config [OPTIONS] add exchange <type> <name> [AddExchangeOptions]\n" +
    "        qpid-config [OPTIONS] del exchange <name>\n" +
    "        qpid-config [OPTIONS] add queue <name> [AddQueueOptions]\n" +
    "        qpid-config [OPTIONS] del queue <name> [DelQueueOptions]\n" +
    "        qpid-config [OPTIONS] bind   <exchange-name> <queue-name> [binding-key]\n" +
    "                  <for type xml>     [-f -|filename]\n" +
    "                  <for type header>  [all|any] k1=v1 [, k2=v2...]\n" +
    "        qpid-config [OPTIONS] unbind <exchange-name> <queue-name> [binding-key]\n";

    private static final String _description =
    "ADDRESS syntax:\n" +
    "\n" +
    "      [username/password@] hostname\n" +
    "      ip-address [:<port>]\n" +
    "\n" +
    "Examples:\n" +
    "\n" +
    "$ qpid-config add queue q\n" +
    "$ qpid-config add exchange direct d localhost:5672\n" +
    "$ qpid-config exchanges 10.1.1.7:10000\n" +
    "$ qpid-config queues guest/guest@broker-host:10000\n" +
    "\n" +
    "Add Exchange <type> values:\n" +
    "\n" +
    "    direct     Direct exchange for point-to-point communication\n" +
    "    fanout     Fanout exchange for broadcast communication\n" +
    "    topic      Topic exchange that routes messages using binding keys with wildcards\n" +
    "    headers    Headers exchange that matches header fields against the binding keys\n" +
    "    xml        XML Exchange - allows content filtering using an XQuery\n" +
    "\n" +
    "\n" +
    "Queue Limit Actions\n" +
    "\n" +
    "    none (default) - Use broker's default policy\n" +
    "    reject         - Reject enqueued messages\n" +
    "    flow-to-disk   - Page messages to disk\n" +
    "    ring           - Replace oldest unacquired message with new\n" +
    "    ring-strict    - Replace oldest message, reject if oldest is acquired\n" +
    "\n" +
    "Queue Ordering Policies\n" +
    "\n" +
    "    fifo (default) - First in, first out\n" +
    "    lvq            - Last Value Queue ordering, allows queue browsing\n" +
    "    lvq-no-browse  - Last Value Queue ordering, browsing clients may lose data\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "\n" +
    "  General Options:\n" +
    "    -t <secs>, --timeout=<secs>\n" +
    "                        Maximum time to wait for broker connection (in\n" +
    "                        seconds)\n" +
    "    -b, --bindings      Show bindings in queue or exchange list\n" +
    "    -a <address>, --broker-addr=<address>\n" +
    "                        Maximum time to wait for broker connection (in\n" +
    "                        seconds)\n" +
    "    --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n" +
    "\n" +
    "  Options for Adding Exchanges and Queues:\n" +
    "    --alternate-exchange=<aexname>\n" +
    "                        Name of the alternate-exchange for the new queue or\n" +
    "                        exchange. Exchanges route messages to the alternate\n" +
    "                        exchange if they are unable to route them elsewhere.\n" +
    "                        Queues route messages to the alternate exchange if\n" +
    "                        they are rejected by a subscriber or orphaned by queue\n" +
    "                        deletion.\n" +
    "    --passive, --dry-run\n" +
    "                        Do not actually add the exchange or queue, ensure that\n" +
    "                        all parameters and permissions are correct and would\n" +
    "                        allow it to be created.\n" +
    "    --durable           The new queue or exchange is durable.\n" +
    "\n" +
    "  Options for Adding Queues:\n" +
    "    --file-count=<n>    Number of files in queue's persistence journal\n" +
    "    --file-size=<n>     File size in pages (64Kib/page)\n" +
    "    --max-queue-size=<n>\n" +
    "                        Maximum in-memory queue size as bytes\n" +
    "    --max-queue-count=<n>\n" +
    "                        Maximum in-memory queue size as a number of messages\n" +
    "    --limit-policy=<policy>\n" +
    "                        Action to take when queue limit is reached\n" +
    "    --order=<ordering>  Queue ordering policy\n" +
    "    --generate-queue-events=<n>\n" +
    "                        If set to 1, every enqueue will generate an event that\n" +
    "                        can be processed by registered listeners (e.g. for\n" +
    "                        replication). If set to 2, events will be generated\n" +
    "                        for enqueues and dequeues.\n" +
    "    --flow-stop-size=<n>\n" +
    "                        Turn on sender flow control when the number of queued\n" +
    "                        bytes exceeds this value.\n" +
    "    --flow-resume-size=<n>\n" +
    "                        Turn off sender flow control when the number of queued\n" +
    "                        bytes drops below this value.\n" +
    "    --flow-stop-count=<n>\n" +
    "                        Turn on sender flow control when the number of queued\n" +
    "                        messages exceeds this value.\n" +
    "    --flow-resume-count=<n>\n" +
    "                        Turn off sender flow control when the number of queued\n" +
    "                        messages drops below this value.\n" +
    "    --argument=<NAME=VALUE>\n" +
    "                        Specify a key-value pair to add to queue arguments\n" +
    "\n" +
    "  Options for Adding Exchanges:\n" +
    "    --sequence          Exchange will insert a 'qpid.msg_sequence' field in\n" +
    "                        the message header\n" +
    "    --ive               Exchange will behave as an 'initial-value-exchange',\n" +
    "                        keeping a reference  to the last message forwarded and\n" +
    "                        enqueuing that message to newly bound queues.\n" +
    "\n" +
    "  Options for Deleting Queues:\n" +
    "    --force             Force delete of queue even if it's currently used or\n" +
    "                        it's not empty\n" +
    "    --force-if-not-empty\n" +
    "                        Force delete of queue even if it's not empty\n" +
    "    --force-if-not-used\n" +
    "                        Force delete of queue even if it's currently used\n" +
    "\n" +
    "  Options for Declaring Bindings:\n" +
    "    -f <file.xq>, --file=<file.xq>\n" +
    "                        For XML Exchange bindings - specifies the name of a\n" +
    "                        file containing an XQuery.\n";

    private Console _console;
    private QmfConsoleData _broker;

    private boolean _recursive = false;
    private String _host = "localhost";
    private int _connTimeout = 10;
    private String _altExchange = null;
    private boolean _passive = false;
    private boolean _durable = false;
    private boolean _ifEmpty = true;
    private boolean _ifUnused = true;
    private long _fileCount = 8;
    private long _fileSize = 24;
    private long _maxQueueSize = 0;
    private long _maxQueueCount = 0;
    private String _limitPolicy = "none";
    private String _order = "fifo";
    private boolean _msgSequence = false;
    private boolean _ive = false;
    private long _eventGeneration = 0;
    private String _file = null;

    // New to Qpid 0.10 qpid-config
    private String _saslMechanism = null;
    private long _flowStopCount = 0;
    private long _flowResumeCount = 0;
    private long _flowStopSize = 0;
    private long _flowResumeSize = 0;
    private List<String> extraArguments = new ArrayList<String>();

    private static final String FILECOUNT = "qpid.file_count";
    private static final String FILESIZE  = "qpid.file_size";
    private static final String MAX_QUEUE_SIZE  = "qpid.max_size";
    private static final String MAX_QUEUE_COUNT  = "qpid.max_count";
    private static final String POLICY_TYPE  = "qpid.policy_type";
    private static final String LVQ = "qpid.last_value_queue";
    private static final String LVQNB = "qpid.last_value_queue_no_browse";
    private static final String MSG_SEQUENCE = "qpid.msg_sequence";
    private static final String IVE = "qpid.ive";
    private static final String QUEUE_EVENT_GENERATION = "qpid.queue_event_generation";
    private static final String FLOW_STOP_COUNT   = "qpid.flow_stop_count";
    private static final String FLOW_RESUME_COUNT = "qpid.flow_resume_count";
    private static final String FLOW_STOP_SIZE    = "qpid.flow_stop_size";
    private static final String FLOW_RESUME_SIZE  = "qpid.flow_resume_size";

    // There are various arguments to declare that have specific program options in this utility.
    // However there is now a generic mechanism for passing arguments as well. The SPECIAL_ARGS
    // set contains the arguments for which there are specific program options defined i.e. the
    // arguments for which there is special processing on add and list.
    private static HashSet<String> SPECIAL_ARGS = new HashSet<String>();

    static
    {
        SPECIAL_ARGS.add(FILECOUNT);
        SPECIAL_ARGS.add(FILESIZE);
        SPECIAL_ARGS.add(MAX_QUEUE_SIZE);
        SPECIAL_ARGS.add(MAX_QUEUE_COUNT);
        SPECIAL_ARGS.add(POLICY_TYPE);
        SPECIAL_ARGS.add(LVQ);
        SPECIAL_ARGS.add(LVQNB);
        SPECIAL_ARGS.add(MSG_SEQUENCE);
        SPECIAL_ARGS.add(IVE);
        SPECIAL_ARGS.add(QUEUE_EVENT_GENERATION);
        SPECIAL_ARGS.add(FLOW_STOP_COUNT);
        SPECIAL_ARGS.add(FLOW_RESUME_COUNT);
        SPECIAL_ARGS.add(FLOW_STOP_SIZE);
        SPECIAL_ARGS.add(FLOW_RESUME_SIZE);
    }

    /**
     * Display long-form QpidConfig usage.   
     */
    private void usage()
    {
        System.out.println(_usage);
        System.exit(1);
    }

    /**
     * Display QpidConfig options.
     */
    private void options()
    {
        System.out.println(_usage);
        System.out.println(_description);
        System.out.println(_options);
        System.exit(1);
    }

    /**
     * Finds a QmfConsoleData instance in a List of QMF Objects that matches a given ObjectID.
     *
     * More or less a direct Java port of findById from qpid-config.
     *
     * @param items the List of QMF Objects to search.
     * @param id the ObjectId we're searching the List for.
     * @return return the found object as a QmfConsoleData else return null.
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
     * Provide a basic overview of the number and type of queues and exchanges.
     */
    private void overview()
    {
        List<QmfConsoleData> exchanges = _console.getObjects("org.apache.qpid.broker", "exchange");
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");

        System.out.printf("Total Exchanges: %d\n", exchanges.size());

        Map<String, AtomicInteger> etype = new HashMap<String, AtomicInteger>();
        for (QmfConsoleData exchange : exchanges)
        {
            String exchangeType = exchange.getStringValue("type");
            AtomicInteger n = etype.get(exchangeType);
            if (n == null)
            {
                etype.put(exchangeType, new AtomicInteger(1));
            }
            else
            {
                n.getAndIncrement();
            }
        }

        for (Map.Entry<String, AtomicInteger> entry : etype.entrySet())
        {
            System.out.printf("%15s: %s\n", entry.getKey(), entry.getValue());
        }

        System.out.println();
        System.out.printf("   Total Queues: %d\n", queues.size());

        int durable = 0;
        for (QmfConsoleData queue : queues)
        {
            boolean isDurable = queue.getBooleanValue("durable");
            if (isDurable)
            {
                durable++;
            }
        }

        System.out.printf("        durable: %d\n", durable);
        System.out.printf("    non-durable: %d\n", queues.size() - durable);
    }

    /**
     * For every exchange list detailed info (equivalent of qpid-config exchanges).
     *
     * More or less a direct Java port of ExchangeList in qpid-config, which handles qpid-config exchanges.
     *
     * @param filter specifies the exchange name to display info for, if set to "" displays info for every exchange.
     */
    private void exchangeList(final String filter)
    {
        List<QmfConsoleData> exchanges = _console.getObjects("org.apache.qpid.broker", "exchange");

        String caption1 = "Type      ";
        String caption2 = "Exchange Name";
        int maxNameLen = caption2.length();

        for (QmfConsoleData exchange : exchanges)
        {
            String name = exchange.getStringValue("name");
            if (filter.equals("") || filter.equals(name))
            {
                if (name.length() > maxNameLen)
                {
                    maxNameLen = name.length();
                }
            }
        }

        System.out.printf("%s%-" + maxNameLen + "s Attributes\n", caption1, caption2);

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < (((maxNameLen + caption1.length()) / 5) + 5); i++)
        {
            buf.append("=====");
        }
        String line = buf.toString();
        System.out.println(line);

        for (QmfConsoleData exchange : exchanges)
        {
            String name = exchange.getStringValue("name");
            if (filter.equals("") || filter.equals(name))
            {
                System.out.printf("%-10s%-" + maxNameLen + "s ", exchange.getStringValue("type"), name);
                Map args = (Map)exchange.getValue("arguments");

                if (exchange.getBooleanValue("durable"))
                {
                    System.out.printf("--durable ");
                }

                if (args.containsKey(MSG_SEQUENCE) && QmfData.getLong(args.get(MSG_SEQUENCE)) == 1)
                {
                    System.out.printf("--sequence ");
                }

                if (args.containsKey(IVE) && QmfData.getLong(args.get(IVE)) == 1)
                {
                    System.out.printf("--ive ");
                }

                if (exchange.hasValue("altExchange"))
                {
                    ObjectId altExchangeRef = exchange.getRefValue("altExchange");
                    QmfConsoleData altExchange = findById(exchanges, altExchangeRef);
                    if (altExchange != null)
                    {
                        System.out.printf("--alternate-exchange=%s", altExchange.getStringValue("name"));
                    }
                }

                System.out.println();
            }
        }
    }

    /**
     * For every exchange list the bindings (equivalent of qpid-config -b exchanges).
     *
     * More or less a direct Java port of ExchangeListRecurse in qpid-config, which handles qpid-config -b exchanges.
     *
     * @param filter specifies the exchange name to display info for, if set to "" displays info for every exchange.
     */
    private void exchangeListRecurse(final String filter)
    {
        List<QmfConsoleData> exchanges = _console.getObjects("org.apache.qpid.broker", "exchange");
        List<QmfConsoleData> bindings = _console.getObjects("org.apache.qpid.broker", "binding");
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");

        for (QmfConsoleData exchange : exchanges)
        {
            ObjectId exchangeId = exchange.getObjectId();
            String name = exchange.getStringValue("name");

            if (filter.equals("") || filter.equals(name))
            {
                System.out.printf("Exchange '%s' (%s)\n", name, exchange.getStringValue("type"));
                for (QmfConsoleData binding : bindings)
                {
                    ObjectId exchangeRef = binding.getRefValue("exchangeRef");

                    if (exchangeRef.equals(exchangeId))
                    {
                        ObjectId queueRef = binding.getRefValue("queueRef");
                        QmfConsoleData queue = findById(queues, queueRef);

                        String queueName = "<unknown>";
                        if (queue != null)
                        {
                            queueName = queue.getStringValue("name");
                            if (queueName.equals(""))
                            {
                                queueName = "''";
                            }
                        }

                        String bindingKey = binding.getStringValue("bindingKey");
                        Map arguments = (Map)binding.getValue("arguments");
                        if (arguments.isEmpty())
                        {
                            System.out.printf("    bind [%s] => %s\n", bindingKey, queueName);
                        }
                        else
                        {
                            // If there are binding arguments then it's a headers exchange
                            System.out.printf("    bind [%s] => %s %s\n", bindingKey, queueName, arguments);
                        }
                    }
                }
            }
        }
    }

    /**
     * For every queue list detailed info (equivalent of qpid-config queues).
     *
     * More or less a direct Java port of QueueList in qpid-config, which handles qpid-config queues.
     *
     * @param filter specifies the queue name to display info for, if set to "" displays info for every queue.
     */
    private void queueList(final String filter)
    {
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");

        String caption = "Queue Name";
        int maxNameLen = caption.length();

        for (QmfConsoleData queue : queues)
        {
            String name = queue.getStringValue("name");
            if (filter.equals("") || filter.equals(name))
            {
                if (name.length() > maxNameLen)
                {
                    maxNameLen = name.length();
                }
            }
        }

        System.out.printf("%-" + maxNameLen + "s Attributes\n", caption);

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < ((maxNameLen / 5) + 5); i++)
        {
            buf.append("=====");
        }
        String line = buf.toString();
        System.out.println(line);

        for (QmfConsoleData queue : queues)
        {
            String name = queue.getStringValue("name");
            if (filter.equals("") || filter.equals(name))
            {
                System.out.printf("%-" + maxNameLen + "s ", name);
                Map<String, Object> args = queue.<Map<String, Object>>getValue("arguments");
/*System.out.println(args);
for (Map.Entry<String, Object> entry  : args.entrySet()) {
    System.out.println(entry.getKey() + " " + entry.getValue().getClass().getCanonicalName());
}*/
                if (queue.getBooleanValue("durable"))
                {
                    System.out.printf("--durable ");
                }

                if (queue.getBooleanValue("autoDelete"))
                {
                    System.out.printf("auto-del ");
                }

                if (queue.getBooleanValue("exclusive"))
                {
                    System.out.printf("excl ");
                }

                if (args.containsKey(FILESIZE))
                {
                    System.out.printf("--file-size=%d ", QmfData.getLong(args.get(FILESIZE)));
                }

                if (args.containsKey(FILECOUNT))
                {
                    System.out.printf("--file-count=%d ", QmfData.getLong(args.get(FILECOUNT)));
                }

                if (args.containsKey(MAX_QUEUE_SIZE))
                {
                    System.out.printf("--max-queue-size=%d ", QmfData.getLong(args.get(MAX_QUEUE_SIZE)));
                }

                if (args.containsKey(MAX_QUEUE_COUNT))
                {
                    System.out.printf("--max-queue-count=%d ", QmfData.getLong(args.get(MAX_QUEUE_COUNT)));
                }

                if (args.containsKey(POLICY_TYPE))
                {
                    System.out.printf("--limit-policy=%s ", (QmfData.getString(args.get(POLICY_TYPE))).replace("_", "-"));
                }

                if (args.containsKey(LVQ) && QmfData.getLong(args.get(LVQ)) == 1)
                {
                    System.out.printf("--order lvq ");
                }

                if (args.containsKey(LVQNB) && QmfData.getLong(args.get(LVQNB)) == 1)
                {
                    System.out.printf("--order lvq-no-browse ");
                }

                if (args.containsKey(QUEUE_EVENT_GENERATION))
                {
                    System.out.printf("--generate-queue-events=%d ", QmfData.getLong(args.get(QUEUE_EVENT_GENERATION)));
                }
 
                if (queue.hasValue("altExchange"))
                {
                    ObjectId altExchangeRef = queue.getRefValue("altExchange");
                    List<QmfConsoleData> altExchanges = _console.getObjects(altExchangeRef);
                    if (altExchanges.size() == 1)
                    {
                        QmfConsoleData altExchange = altExchanges.get(0);
                        System.out.printf("--alternate-exchange=%s", altExchange.getStringValue("name"));
                    }
                }

                if (args.containsKey(FLOW_STOP_SIZE))
                {
                    System.out.printf("--flow-stop-size=%d ", QmfData.getLong(args.get(FLOW_STOP_SIZE)));
                }

                if (args.containsKey(FLOW_RESUME_SIZE))
                {
                    System.out.printf("--flow-resume-size=%d ", QmfData.getLong(args.get(FLOW_RESUME_SIZE)));
                }

                if (args.containsKey(FLOW_STOP_COUNT))
                {
                    System.out.printf("--flow-stop-count=%d ", QmfData.getLong(args.get(FLOW_STOP_COUNT)));
                }

                if (args.containsKey(FLOW_RESUME_COUNT))
                {
                    System.out.printf("--flow-resume-count=%d ", QmfData.getLong(args.get(FLOW_RESUME_COUNT)));
                }

                for (Map.Entry<String, Object> entry : args.entrySet())
                { // Display generic queue arguments
                    if (!SPECIAL_ARGS.contains(entry.getKey()))
                    {
                        System.out.printf("--argument %s=%s ", entry.getKey(), entry.getValue());
                    }
                }

                System.out.println();
            }
        }
    }

    /**
     * For every queue list the bindings (equivalent of qpid-config -b queues).
     *
     * More or less a direct Java port of QueueListRecurse in qpid-config, which handles qpid-config -b queues.
     *
     * @param filter specifies the queue name to display info for, if set to "" displays info for every queue.
     */
    private void queueListRecurse(final String filter)
    {
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");
        List<QmfConsoleData> bindings = _console.getObjects("org.apache.qpid.broker", "binding");
        List<QmfConsoleData> exchanges = _console.getObjects("org.apache.qpid.broker", "exchange");

        for (QmfConsoleData queue : queues)
        {
            ObjectId queueId = queue.getObjectId();
            String name = queue.getStringValue("name");

            if (filter.equals("") || filter.equals(name))
            {
                System.out.printf("Queue '%s'\n", name);

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
                            System.out.printf("    bind [%s] => %s\n", bindingKey, exchangeName);
                        }
                        else
                        {
                            // If there are binding arguments then it's a headers exchange
                            System.out.printf("    bind [%s] => %s %s\n", bindingKey, exchangeName, arguments);
                        }
                    }
                }
            }
        }
    }

    /**
     * Add an exchange using the QMF "create" method.
     * @param args the exchange type is the first argument and the exchange name is the second argument.
     * The remaining QMF method properties are populated form config parsed from the command line.
     */
    private void addExchange(final String[] args)
    {
        if (args.length < 2)
        {
            usage();
        }

        Map<String, Object> properties = new HashMap<String, Object>();
        if (_durable)
        {
            properties.put("durable", true);
        }

        properties.put("exchange-type", args[0]);

        if (_msgSequence)
        {
            properties.put(MSG_SEQUENCE, 1l);
        }

        if (_ive) 
        {
            properties.put(IVE, 1l);
        }

        if (_altExchange != null)
        {
            properties.put("alternate-exchange", _altExchange);
        }

        QmfData arguments = new QmfData();
        arguments.setValue("type", "exchange");
        arguments.setValue("name", args[1]);
        arguments.setValue("properties", properties);

        try
        {
            _broker.invokeMethod("create", arguments);
        }
        catch (QmfException e)
        {
            System.out.println(e.getMessage());
        }
        // passive exchange creation not implemented yet (not sure how to do it using QMF2)
    }

    /**
     * Add a queue using the QMF "create" method.
     * @param args the queue name is the first argument.
     * The remaining QMF method properties are populated form config parsed from the command line.
     */
    private void addQueue(final String[] args)
    {
        if (args.length < 1)
        {
            usage();
        }

        Map<String, Object> properties = new HashMap<String, Object>();

        for (String a : extraArguments)
        {
            String[] r = a.split("=");
            String value = r.length == 2 ? r[1] : null;
            properties.put(r[0], value);
        }

        if (_durable)
        {
            properties.put("durable", true);
            properties.put(FILECOUNT, _fileCount);
            properties.put(FILESIZE, _fileSize);
        }

        if (_maxQueueSize > 0)
        {
            properties.put(MAX_QUEUE_SIZE, _maxQueueSize);
        }

        if (_maxQueueCount > 0)
        {
            properties.put(MAX_QUEUE_COUNT, _maxQueueCount);
        }

        if (_limitPolicy.equals("reject"))
        {
            properties.put(POLICY_TYPE, "reject");
        }
        else if (_limitPolicy.equals("flow-to-disk"))
        {
            properties.put(POLICY_TYPE, "flow_to_disk");
        }
        else if (_limitPolicy.equals("ring"))
        {
            properties.put(POLICY_TYPE, "ring");
        }
        else if (_limitPolicy.equals("ring-strict"))
        {
            properties.put(POLICY_TYPE, "ring_strict");
        }

        if (_order.equals("lvq"))
        {
            properties.put(LVQ, 1l);
        }
        else if (_order.equals("lvq-no-browse"))
        {
            properties.put(LVQNB, 1l);
        }

        if (_eventGeneration > 0)
        {
            properties.put(QUEUE_EVENT_GENERATION, _eventGeneration);
        }

        if (_altExchange != null)
        {
            properties.put("alternate-exchange", _altExchange);
        }

        if (_flowStopSize > 0)
        {
            properties.put(FLOW_STOP_SIZE, _flowStopSize);
        }

        if (_flowResumeSize > 0)
        {
            properties.put(FLOW_RESUME_SIZE, _flowResumeSize);
        }

        if (_flowStopCount > 0)
        {
            properties.put(FLOW_STOP_COUNT, _flowStopCount);
        }

        if (_flowResumeCount > 0)
        {
            properties.put(FLOW_RESUME_COUNT, _flowResumeCount);
        }

        QmfData arguments = new QmfData();
        arguments.setValue("type", "queue");
        arguments.setValue("name", args[0]);
        arguments.setValue("properties", properties);

        try
        {
            _broker.invokeMethod("create", arguments);
        }
        catch (QmfException e)
        {
            System.out.println(e.getMessage());
        }
        // passive queue creation not implemented yet (not sure how to do it using QMF2)
    }

    /**
     * Remove an exchange using the QMF "delete" method.
     * @param args the exchange name is the first argument.
     * The remaining QMF method properties are populated form config parsed from the command line.
     */
    private void delExchange(final String[] args)
    {
        if (args.length < 1)
        {
            usage();
        }

        QmfData arguments = new QmfData();
        arguments.setValue("type", "exchange");
        arguments.setValue("name", args[0]);

        try
        {
            _broker.invokeMethod("delete", arguments);
        }
        catch (QmfException e)
        {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Remove a queue using the QMF "delete" method.
     * @param args the queue name is the first argument.
     * The remaining QMF method properties are populated form config parsed from the command line.
     */
    private void delQueue(final String[] args)
    {
        if (args.length < 1)
        {
            usage();
        }

        if (_ifEmpty || _ifUnused)
        { // Check the selected queue object to see if it is not empty or is in use
            List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");
            for (QmfConsoleData queue : queues)
            {
                ObjectId queueId = queue.getObjectId();
                String name = queue.getStringValue("name");
                if (name.equals(args[0]))
                {
                    long msgDepth = queue.getLongValue("msgDepth");
                    if (_ifEmpty == true && msgDepth > 0)
                    {
                        System.out.println("Cannot delete queue " + name + "; queue not empty");
                        return;
                    }

                    long consumerCount = queue.getLongValue("consumerCount");                    
                    if (_ifUnused == true && consumerCount > 0)
                    {
                        System.out.println("Cannot delete queue " + name + "; queue in use");
                        return;
                    }
                }
            }
        }

        QmfData arguments = new QmfData();
        arguments.setValue("type", "queue");
        arguments.setValue("name", args[0]);

        try
        {
            _broker.invokeMethod("delete", arguments);
        }
        catch (QmfException e)
        {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Add a binding using the QMF "create" method.
     * @param args the exchange name is the first argument, the queue name is the second argument and the binding key
     * is the third argument.
     * The remaining QMF method properties are populated form config parsed from the command line.
     */
    private void bind(final String[] args)
    {
        if (args.length < 2)
        {
            usage();
        }

        // Look up exchange objects to find the type of the selected exchange
        String exchangeType = null;
        List<QmfConsoleData> exchanges = _console.getObjects("org.apache.qpid.broker", "exchange");
        for (QmfConsoleData exchange : exchanges)
        {
            String name = exchange.getStringValue("name");
            if (args[0].equals(name))
            {
                exchangeType = exchange.getStringValue("type");
                break;
            }
        }

        if (exchangeType == null)
        {
            System.out.println("Exchange " + args[0] + " is invalid");
            return;
        }

        Map<String, Object> properties = new HashMap<String, Object>();
        if (exchangeType.equals("xml"))
        {
            if (_file == null)
            {
                System.out.println("Invalid args to bind xml:  need an input file or stdin");
                return;
            }

            String xquery = null;
            if (_file.equals("-"))
            { // Read xquery off stdin
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                try
                {
                    StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) // read until eof
                    {
                        buf.append(line + "\n");
                    }
                    xquery = buf.toString();
                }
                catch (IOException ioe)
                {
                    System.out.println("Exception " + ioe + " while reading stdin");
                    return;
                }      
            }
            else
            { // Read xquery from input file
                File file = new File(_file);
                try
                {
                    FileInputStream fin = new FileInputStream(file);
                    try
                    {
                        byte content[] = new byte[(int)file.length()];
                        fin.read(content);
                        xquery = new String(content);
                    }
                    finally
                    {
                        fin.close();
                    }
                }
                catch (FileNotFoundException e)
                {
                    System.out.println("File " + _file + " not found");
                    return;
                }
                catch (IOException ioe)
                {
                    System.out.println("Exception " + ioe + " while reading " + _file);
                    return;
                }
            }
            properties.put("xquery", xquery);
        }
        else if (exchangeType.equals("headers"))
        {
            if (args.length < 5)
            {
                System.out.println("Invalid args to bind headers: need 'any'/'all' plus conditions");
                return;
            }
            String op = args[3];
            if (op.equals("all") || op.equals("any"))
            {
                properties.put("x-match", op);
                String[] bindings = Arrays.copyOfRange(args, 4, args.length);
                for (String binding : bindings)
                {
                    if (binding.contains("="))
                    {
                        binding = binding.split(",")[0];
                        String[] kv = binding.split("=");
                        properties.put(kv[0], kv[1]);
                    }
                }
            }
            else
            {
                System.out.println("Invalid condition arg to bind headers, need 'any' or 'all', not '" + op + "'");
                return;
            }
        }

        String bindingIdentifier = args[0] + "/" + args[1];
        if (args.length > 2)
        {
            bindingIdentifier = bindingIdentifier + "/" + args[2];
        }

        QmfData arguments = new QmfData();
        arguments.setValue("type", "binding");
        arguments.setValue("name", bindingIdentifier);
        arguments.setValue("properties", properties);

        try
        {
            _broker.invokeMethod("create", arguments);
        }
        catch (QmfException e)
        {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Remove a binding using the QMF "delete" method.
     * @param args the exchange name is the first argument, the queue name is the second argument and the binding key
     * is the third argument.
     * The remaining QMF method properties are populated form config parsed from the command line.
     */
    private void unbind(final String[] args)
    {
        if (args.length < 2)
        {
            usage();
        }

        String bindingIdentifier = args[0] + "/" + args[1];
        if (args.length > 2)
        {
            bindingIdentifier = bindingIdentifier + "/" + args[2];
        }

        QmfData arguments = new QmfData();
        arguments.setValue("type", "binding");
        arguments.setValue("name", bindingIdentifier);

        try
        {
            _broker.invokeMethod("delete", arguments);
        }
        catch (QmfException e)
        {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Create an instance of QpidConfig.
     *
     * @param args the command line arguments.
     */
    public QpidConfig(final String[] args)
    {
        String[] longOpts = {"help", "durable", "bindings", "broker-addr=", "file-count=",
                             "file-size=", "max-queue-size=", "max-queue-count=", "limit-policy=",
                             "order=", "sequence", "ive", "generate-queue-events=", "force", "force-if-not-empty",
                             "force-if-used", "alternate-exchange=", "passive", "timeout=", "file=", "flow-stop-size=",
                             "flow-resume-size=", "flow-stop-count=", "flow-resume-count=", "argument="};

        try
        {
            GetOpt getopt = new GetOpt(args, "ha:bf:", longOpts);
            List<String[]> optList = getopt.getOptList();
            String[] cargs = {};
            cargs = getopt.getEncArgs().toArray(cargs);

            //System.out.println("optList");
            for (String[] opt : optList)
            {
                //System.out.println(opt[0] + ":" + opt[1]);

                if (opt[0].equals("-h") || opt[0].equals("--help"))
                {
                    options();
                }

                if (opt[0].equals("-b") || opt[0].equals("--bindings"))
                {
                    _recursive = true;
                }

                if (opt[0].equals("-a") || opt[0].equals("--broker-addr"))
                {
                    _host = opt[1];
                }

                if (opt[0].equals("-f") || opt[0].equals("--file"))
                {
                    _file = opt[1];
                }

                if (opt[0].equals("--timeout"))
                {
                    _connTimeout = Integer.parseInt(opt[1]);
                }

                if (opt[0].equals("--alternate-exchange"))
                {
                    _altExchange = opt[1];
                }

                if (opt[0].equals("--passive"))
                {
                    _passive = true;
                }

                if (opt[0].equals("--durable"))
                {
                    _durable = true;
                }

                if (opt[0].equals("--file-count"))
                {
                    _fileCount = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--file-size"))
                {
                    _fileSize = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--max-queue-size"))
                {
                    _maxQueueSize = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--max-queue-count"))
                {
                    _maxQueueCount = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--limit-policy"))
                {
                    _limitPolicy = opt[1];
                }

                if (opt[0].equals("--flow-stop-size"))
                {
                    _flowStopSize = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--flow-resume-size"))
                {
                    _flowResumeSize = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--flow-stop-count"))
                {
                    _flowStopCount = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--flow-resume-count"))
                {
                    _flowResumeCount = Long.parseLong(opt[1]);
                }

                boolean validPolicy = false;
                String[] validPolicies = {"none", "reject", "flow-to-disk", "ring", "ring-strict"};
                for (String i : validPolicies)
                {
                    if (_limitPolicy.equals(i))
                    {
                        validPolicy = true;
                        break;
                    }
                }

                if (!validPolicy)
                {
                    System.err.println("Error: Invalid --limit-policy argument");
                    System.exit(1);
                }

                if (opt[0].equals("--order"))
                {
                    _order = opt[1];
                }

                boolean validOrder = false;
                String[] validOrders = {"fifo", "lvq", "lvq-no-browse"};
                for (String i : validOrders)
                {
                    if (_order.equals(i))
                    {
                        validOrder = true;
                        break;
                    }
                }

                if (!validOrder)
                {
                    System.err.println("Error: Invalid --order argument");
                    System.exit(1);
                }

                if (opt[0].equals("--sequence"))
                {
                    _msgSequence = true;
                }
    
                if (opt[0].equals("--ive"))
                {
                    _ive = true;
                }

                if (opt[0].equals("--generate-queue-events"))
                {
                    _eventGeneration = Long.parseLong(opt[1]);
                }

                if (opt[0].equals("--force"))
                {
                    _ifEmpty  = false;
                    _ifUnused = false;
                }

                if (opt[0].equals("--force-if-not-empty"))
                {
                    _ifEmpty = false;
                }

                if (opt[0].equals("--force-if-used"))
                {
                    _ifUnused = false;
                }

                if (opt[0].equals("--argument"))
                {
                    extraArguments.add(opt[1]);
                }
            }

            Connection connection = ConnectionHelper.createConnection(_host, "{reconnect: true}");        
            _console = new Console();
            _console.disableEvents(); // Optimisation, as we're only doing getObjects() calls.
            _console.addConnection(connection);
            List<QmfConsoleData> brokers = _console.getObjects("org.apache.qpid.broker", "broker");
            if (brokers.isEmpty())
            {
                System.out.println("No broker QmfConsoleData returned");
                System.exit(1);
            }

            _broker = brokers.get(0);

            int nargs = cargs.length;
            if (nargs == 0)
            {
                overview();
            }
            else
            {
                String cmd = cargs[0];
                String modifier = "";

                if (nargs > 1)
                {
                    modifier = cargs[1];
                }

                if (cmd.equals("exchanges"))
                {
                    if (_recursive)
                    {
                        exchangeListRecurse(modifier);
                    }
                    else
                    {
                        exchangeList(modifier);
                    }
                }
                else if (cmd.equals("queues"))
                {
                    if (_recursive)
                    {
                        queueListRecurse(modifier);
                    }
                    else
                    {
                        queueList(modifier);
                    }
                }
                else if (cmd.equals("add"))
                {
                    if (modifier.equals("exchange"))
                    {
                        addExchange(Arrays.copyOfRange(cargs, 2, cargs.length));
                    }
                    else if (modifier.equals("queue"))
                    {
                        addQueue(Arrays.copyOfRange(cargs, 2, cargs.length));
                    }
                    else
                    {
                        usage();
                    }
                }
                else if (cmd.equals("del"))
                {
                    if (modifier.equals("exchange"))
                    {
                        delExchange(Arrays.copyOfRange(cargs, 2, cargs.length));
                    }
                    else if (modifier.equals("queue"))
                    {
                        delQueue(Arrays.copyOfRange(cargs, 2, cargs.length));
                    }
                    else
                    {
                        usage();
                    }
                }
                else if (cmd.equals("bind"))
                {
                    bind(Arrays.copyOfRange(cargs, 1, cargs.length));
                }
                else if (cmd.equals("unbind"))
                {
                    unbind(Arrays.copyOfRange(cargs, 1, cargs.length));
                }
                else
                {
                    usage();
                }
            }
        }
        catch (QmfException e)
        {
            System.err.println(e.toString());
            usage();
        }
        catch (IllegalArgumentException e)
        {
            System.err.println(e.toString());
            usage();
        }
    }

    /**
     * Runs QpidConfig.
     * @param args the command line arguments.
     */
    public static void main(String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        // As of Qpid 0.16 the Session Dispatcher Thread is non-Daemon so the JVM gets prevented from exiting.
        // Setting the following property to true makes it a Daemon Thread.
        System.setProperty("qpid.jms.daemon.dispatcher", "true");

        QpidConfig qpidConfig = new QpidConfig(args);
    }
}
