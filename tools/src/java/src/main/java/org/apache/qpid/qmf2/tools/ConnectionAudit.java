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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// For DOM parsing the whitelist
import org.w3c.dom.*;
import javax.xml.parsers.*;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.AgentRestartedWorkItem;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.EventReceivedWorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;

/**
 * Audits connections to one or more Qpid message brokers.
 * <pre>
 * Exchange and Queue names are checked against a whitelist and if no match is found an alert is generated.
 * 
 * If no broker-addr is supplied, ConnectionAudit connects to 'localhost:5672'.
 * 
 * [broker-addr] syntax:
 * 
 * [username/password@] hostname
 * ip-address [:&lt;port&gt;]
 * 
 * Examples:
 * 
 * $ ConnectionAudit localhost:5672
 * $ ConnectionAudit 10.1.1.7:10000
 * $ ConnectionAudit guest/guest@broker-host:10000
 * 
 * Options:
 *   -h, --help            show this help message and exit
 *   --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 *   --whitelist=&lt;whitelist XML document&gt;
 *                         The fully qualified name of the whitelist XML file,
 *                         default is ./whitelist.xml
 * 
 * </pre>
 * An example whitelist is illustrated below, note that in this example the exchanges associated with management
 * have been whitelisted to remove spurious alerts caused by the temporary management queues.
 * <pre>
 *&lt;?xml version="1.0" encoding="UTF-8"?&gt;
 *&lt;whitelist&gt;
 *    &lt;exchangeWhitelist&gt;
 *        &lt;exchange&gt;qmf.default.topic&lt;/exchange&gt;
 *        &lt;exchange&gt;qmf.default.direct&lt;/exchange&gt;
 *        &lt;exchange&gt;qpid.management&lt;/exchange&gt;
 *        &lt;exchange&gt;amq.direct&lt;/exchange&gt;
 *        &lt;exchange&gt;&lt;/exchange&gt;
 *    &lt;/exchangeWhitelist&gt;
 *    &lt;queueWhitelist&gt;
 *        &lt;queue&gt;testqueue&lt;/queue&gt;
 *    &lt;/queueWhitelist&gt;
 *&lt;/whitelist&gt;
 * </pre>

 * @author Fraser Adams
 */
public final class ConnectionAudit implements QmfEventListener
{
    private static final String _usage =
    "Usage: ConnectionAudit [options] [broker-addr]...\n";

    private static final String _description =
    "Audits connections to one or more Qpid message brokers.\n" +
    "Exchange and Queue names are checked against a whitelist and if no match is found an alert is generated.\n" +
    "\n" +
    "If no broker-addr is supplied, ConnectionAudit connects to 'localhost:5672'.\n" +
    "\n" +
    "[broker-addr] syntax:\n" +
    "\n" +
    "[username/password@] hostname\n" +
    "ip-address [:<port>]\n" +
    "\n" +
    "Examples:\n" +
    "\n" +
    "$ ConnectionAudit localhost:5672\n" +
    "$ ConnectionAudit 10.1.1.7:10000\n" +
    "$ ConnectionAudit guest/guest@broker-host:10000\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "  --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n" +
    "  --whitelist=<whitelist XML document>\n" +
    "                        The fully qualified name of the whitelist XML file,\n" +
    "                        default is ./whitelist.xml\n";


    private final String _url;
    private final String _whitelist;
    private long _whitelistLastModified = 0;
    private Console _console;

    // The sets to be used as the whitelists.
    private Set<String> _exchangeWhitelist = new HashSet<String>();
    private Set<String> _queueWhitelist = new HashSet<String>();

    /**
     * Basic constructor. Creates JMS Session, Initialises Destinations, Producers & Consumers and starts connection.
     * @param url the connection URL.
     * @param connectionOptions the options String to pass to ConnectionHelper.
     * @param whitelist the path name of the whitelist XML file.
     */
    public ConnectionAudit(final String url, final String connectionOptions, final String whitelist)
    {
        System.out.println("Connecting to " + url);
        _url = url;
        _whitelist = whitelist;
        try
        {
            Connection connection = ConnectionHelper.createConnection(url, connectionOptions);        
            _console = new Console(this);
            _console.addConnection(connection);
            checkExistingSubscriptions();
        }
        catch (QmfException qmfe)
        {
            System.err.println ("QmfException " + qmfe.getMessage() + " caught in ConnectionAudit constructor");
        }
    }

    /**
     * When we start up we need to check any subscriptions that already exist against the whitelist.
     * Subsequent checks are made only when we receive new subscribe events.
     */
    private void checkExistingSubscriptions()
    {
        readWhitelist();
        List<QmfConsoleData> subscriptions = _console.getObjects("org.apache.qpid.broker", "subscription");
        for (QmfConsoleData subscription : subscriptions)
        {
            QmfConsoleData queue = dereference(subscription.getRefValue("queueRef"));
            QmfConsoleData session = dereference(subscription.getRefValue("sessionRef"));
            QmfConsoleData connection = dereference(session.getRefValue("connectionRef"));
            
            String queueName = queue.getStringValue("name");
            String address = connection.getStringValue("address");
            String timestamp = new Date(subscription.getCreateTime()/1000000l).toString();
            validateQueue(queueName, address, timestamp);
        }
    }

    /**
     * Dereferences an ObjectId returning a QmfConsoleData.
     * @param ref the ObjectId to be dereferenced.
     * @return the dereferenced QmfConsoleData object or null if the object can't be found.
     */
    private QmfConsoleData dereference(final ObjectId ref)
    {
        List<QmfConsoleData> data = _console.getObjects(ref);
        if (data.size() == 1)
        {
            return data.get(0);
        }
        return null;
    }

    /**
     * Looks up the exchange and binding information from the supplied queuename then calls the main validateQueue()
     * @param queueName the name of the queue that we want to check against the whitelists.
     * @param exchangeName the name of the exchange that the queue we want to check against the whitelists is bound to.
     * @param binding the binding associating queue "queueName" with exchange "exchangeName".
     * @param address the connection address information for the subscription.
     * @param timestamp the timestamp of the subscription.
     */
    private void validateQueue(final String queueName, String exchangeName, final QmfConsoleData binding,
                               final String address, final String timestamp)
    {
        if (_exchangeWhitelist.contains(exchangeName))
        { // Check exchangeName against the exchangeWhitelist and if it's in there we simply return.
            return;
        }

        if (_queueWhitelist.contains(queueName))
        { // Check queueName against the queueWhitelist and if it's in there we simply return.
            return;
        }

        if (exchangeName.equals(""))
        { // Make exchangeName render more prettily if necessary.
            exchangeName = "''";
        }

        String bindingKey = binding.getStringValue("bindingKey");
        Map arguments = (Map)binding.getValue("arguments");
        if (arguments.isEmpty())
        {
            System.out.printf("%s ALERT ConnectionAudit.validateQueue() validation failed for queue: %s with binding[%s] => %s from address: %s with connection timestamp %s\n\n", new Date().toString(), queueName, bindingKey, exchangeName, address, timestamp);
        }
        else
        { // If there are binding arguments then it's a headers exchange so display accordimgly.
            System.out.printf("%s ALERT ConnectionAudit.validateQueue() validation failed for queue: %s with binding[%s] => %s %s from address: %s with connection timestamp %s\n\n", new Date().toString(), queueName, bindingKey, exchangeName, arguments, address, timestamp);
        }
    }

    /**
     * Looks up the exchange and binding information from the supplied queuename then calls the main validateQueue()
     * @param queueName the name of the queue that we want to check against the whitelists.
     * @param address the connection address information for the subscription.
     * @param timestamp the timestamp of the subscription.
     */
    private void validateQueue(final String queueName, final String address, final String timestamp)
    {
        ObjectId queueId = null;
        List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");
        for (QmfConsoleData queue : queues)
        { // We first have to find the ObjectId of the queue called queueName.
            if (queue.getStringValue("name").equals(queueName))
            {
                queueId = queue.getObjectId();
                break;
            }
        }

        if (queueId == null)
        {
            System.out.printf("%s ERROR ConnectionAudit.validateQueue() %s reference couldn't be found\n",
                              new Date().toString(), queueName);
        }
        else
        { // If we've got the queue's ObjectId we then find the binding that references it.
            List<QmfConsoleData> bindings = _console.getObjects("org.apache.qpid.broker", "binding");
            for (QmfConsoleData binding : bindings)
            {
                ObjectId queueRef = binding.getRefValue("queueRef");
                if (queueRef.equals(queueId))
                { // We've found a binding that matches queue queueName so look up the associated exchange and validate.
                    QmfConsoleData exchange = dereference(binding.getRefValue("exchangeRef"));
                    String exchangeName = exchange.getStringValue("name");
                    validateQueue(queueName, exchangeName, binding, address, timestamp);
                }
            }
        }
    }

    /**
     * Handles WorkItems delivered by the Console.
     * <p>
     * If we receive an EventReceivedWorkItem check if it is a subscribe event. If it is we check if the whitelist has 
     * changed, and if it has we re-read it. We then extract the queue name, exchange name, binding, connection address
     * and timestamp and validate with the whitelsist.
     * <p>
     * If we receive an AgentRestartedWorkItem we revalidate all subscriptions as it's possible that a client connection
     * could have been made to the broker before ConnectionAudit has successfully re-established its own connections.
     * @param wi a QMF2 WorkItem object
     */
    public void onEvent(final WorkItem wi)
    {
        if (wi instanceof EventReceivedWorkItem)
        {
            EventReceivedWorkItem item = (EventReceivedWorkItem)wi;
            QmfEvent event = item.getEvent();
            String className = event.getSchemaClassId().getClassName();
            if (className.equals("subscribe"))
            {
                readWhitelist();
                String queueName = event.getStringValue("qName");
                String address = event.getStringValue("rhost");
                String timestamp = new Date(event.getTimestamp()/1000000l).toString();
                validateQueue(queueName, address, timestamp);
            }
        }
        else if (wi instanceof AgentRestartedWorkItem)
        {
            checkExistingSubscriptions();
        }
    }

    /**
     * This method first checks if the whitelist file exists, if not it clears the sets used as whitelists
     * so that no whitelisting is applied. If the whitelist file does exist it is parsed by a DOM parser.
     * <p>
     * We look for all exchange and queue elements and populate the respective whitelist sets with their
     * contents. Note that we check the whitelist file update time to avoid reading it if it hasn't been changed
     */
    private void readWhitelist()
    {
        File file = new File(_whitelist);
        if (file.exists())
        {
            long mtime = file.lastModified();
            if (mtime != _whitelistLastModified)
            {
                _whitelistLastModified = mtime;
                _exchangeWhitelist.clear();
                _queueWhitelist.clear();

                try
                {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder docBuilder = factory.newDocumentBuilder();
                    Document doc = docBuilder.parse(file);
    
                    Element whitelist = doc.getDocumentElement();
                    if (whitelist.getNodeName().equals("whitelist"))
                    {
                        NodeList children = whitelist.getChildNodes();
                        for (int i = 0; i < children.getLength(); i++)
                        {
                            Node child = children.item(i);
                            if (child.getNodeName().equals("exchangeWhitelist"))
                            {
                                NodeList exchanges = child.getChildNodes();
                                for (int j = 0; j < exchanges.getLength(); j++)
                                {
                                    Node node = exchanges.item(j);
                                    if (node.getNodeName().equals("exchange"))
                                    {
                                        if (node.hasChildNodes())
                                        {
                                            String exchange = node.getFirstChild().getNodeValue();
                                            _exchangeWhitelist.add(exchange);
                                        }
                                        else
                                        {
                                            _exchangeWhitelist.add("");
                                        }
                                    }
                                }
                            }
                            else if (child.getNodeName().equals("queueWhitelist"))
                            {
                                NodeList queues = child.getChildNodes();
                                for (int j = 0; j < queues.getLength(); j++)
                                {
                                    Node node = queues.item(j);
                                    if (node.getNodeName().equals("queue"))
                                    {
                                        if (node.hasChildNodes())
                                        {
                                            String queue = node.getFirstChild().getNodeValue();
                                            _queueWhitelist.add(queue);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (Exception e)
                { // Failed to parse correctly.
                    System.out.println("Exception " + e + " while reading " + _whitelist);
                    System.out.println(new Date().toString() + " WARN ConnectionAudit.readWhitelist() " + 
                                       _whitelist + " failed: " + e.getMessage());
                    return;
                }
            }
        }
        else 
        { // If whitelist file doesn't exist log a warning and clear the whitelists.
            System.out.println(new Date().toString() + " WARN ConnectionAudit.readWhitelist() " + 
                               _whitelist + " doesn't exist");
            _exchangeWhitelist.clear();
            _queueWhitelist.clear();
        }
    } // End of readWhitelist()

    /**
     * Runs ConnectionAudit.
     * @param args the command line arguments.
     */
    public static void main(final String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        String[] longOpts = {"help", "whitelist=", "sasl-mechanism="};
        try
        {
            String connectionOptions = "{reconnect: true}";
            String whitelist = "./whitelist.xml";
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
                else if (opt[0].equals("--whitelist"))
                {
                    whitelist = opt[1];
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
                ConnectionAudit eventPrinter = new ConnectionAudit(url, connectionOptions, whitelist);
            }
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(_usage);
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
