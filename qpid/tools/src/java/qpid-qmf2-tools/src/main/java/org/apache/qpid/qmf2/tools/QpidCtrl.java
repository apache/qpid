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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.MethodResult;
import org.apache.qpid.qmf2.console.QmfConsoleData;

import org.apache.qpid.qmf2.util.ConnectionHelper;
import org.apache.qpid.qmf2.util.GetOpt;

// Reuse this class as it provides a handy mechanism to parse an args String into a Map
import org.apache.qpid.messaging.util.AddressParser;

/**
 * A tool to allow QMF2 methods to be invoked from the command line.
 * <pre>
 * Usage: QpidCtrl [options] command [args]
 * The args need to be in a Stringified Map format (similar to an Address String)
 * e.g. to set broker log level: QpidCtrl setLogLevel "{level:\"debug+:Broker\"}"
 * The listValues command lists property names and values of the specified object.
 * The listObjects command lists all objects of the specified package and class.
 * 
 * Options:
 *   -h, --help            show this help message and exit
 *   -v                    enable logging
 *   -a &lt;address&gt;, --broker-address=&lt;address&gt;
 *                         broker-addr is in the form:  [username/password@]
 *                         hostname | ip-address [:&lt;port&gt;]   ex:  localhost,
 *                         10.1.1.7:10000, broker-host:10000,
 *                         guest/guest@localhost
 *   -c &lt;class&gt;, --class=&lt;class&gt;
 *                         class of object on which command is being invoked
 *                         (default broker)
 *   -p &lt;package&gt;, --package=&lt;package&gt;
 *                         package of object on which command is being invoked
 *                         (default org.apache.qpid.broker)
 *   -i &lt;id&gt;, --id=&lt;id&gt;    identifier of object on which command is being invoked
 *                         (default amqp-broker)
 *   --agent=&lt;agent name&gt;
 *                         The name of the Agent to which commands will be sent
 *                         This will try to match &lt;agent name&gt; against the Agent name
 *                         the Agent product name and will also check if the Agent name
 *                         contains the &lt;agent name&gt; String
 *                         (default qpidd)
 *   --sasl-mechanism=&lt;mech&gt;
 *                         SASL mechanism for authentication (e.g. EXTERNAL,
 *                         ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL
 *                         automatically picks the most secure available
 *                         mechanism - use this option to override.
 * </pre>
 * Examples (Note the quotes and escaped quotes are significant!):
 * <p>
 * Get the current broker log level:
 * <pre>QpidCtrl getLogLevel</pre>
 *
 * Set the current broker log level to notice+:
 * <pre>QpidCtrl setLogLevel "{level:\"notice+\"}"</pre>
 * 
 * Set the current broker log level to debug+ for all Management Objects:
 * <pre>QpidCtrl setLogLevel "{level:\"debug+\"}"</pre>
 *
 * Set the current broker log level to debug+ for just the Broker Management Object:
 * <pre>QpidCtrl setLogLevel "{level:\"debug+:Broker\"}"</pre>
 *
 * List the properties of the qmf.default.direct exchange:
 * <pre>QpidCtrl -c exchange -i qmf.default.direct listValues</pre>
 *
 * Create a queue called test with a flow-to-disk limit policy:
 * <pre>QpidCtrl create "{type:queue,name:test,properties:{'qpid.policy_type':ring}}"</pre>
 *
 * Delete a queue called test:
 * <pre>QpidCtrl delete "{type:queue,name:test}"</pre>
 *
 * Create a binding called bind1 between the amq.match exchange and the test queue matching the headers name=fadams
 * and gender=male:
 * <pre>QpidCtrl create "{type:binding,name:'amq.match/test/bind1',properties:{x-match:all,name:fadams,gender:male}}"</pre>
 *
 * Delete the binding called bind1 between the amq.match exchange and the test queue:
 * <pre>QpidCtrl delete "{type:binding,name:'amq.match/test/bind1'}"</pre>
 *
 * Get the broker to echo a message:
 * <pre>QpidCtrl echo "{sequence:1234,body:'Peaches En Regalia'}"</pre>
 *
 * Invoke the event method on the gizmo Agent (launch gizmo Agent via AgentTest):
 * <pre>QpidCtrl -p com.profitron.gizmo -c control -i OPERATIONAL --agent=gizmo event</pre>
 *
 * Invoke the create_child method on the gizmo Agent (launch gizmo Agent via AgentTest):
 * <pre>QpidCtrl -p com.profitron.gizmo -c control -i OPERATIONAL --agent=gizmo create_child "{name:monkeyBoy}"</pre>
 *
 * Invoke the stop method on the gizmo Agent (launch gizmo Agent via AgentTest):
 * <pre>QpidCtrl -p com.profitron.gizmo -c control -i OPERATIONAL --agent=gizmo stop "{message:'Will I dream?'}"</pre>
 *
 * @author Fraser Adams
 */
public final class QpidCtrl
{
    private static final String _usage =
    "Usage: QpidCtrl [options] command [args]\n" +
    "The args need to be in a Stringified Map format (similar to an Address String)\n" +
    "e.g. to set broker log level: QpidCtrl setLogLevel \"{level:\\\"debug+:Broker\\\"}\"\n" +
    "The listValues command lists property names and values of the specified object.\n" +
    "The listObjects command lists all objects of the specified package and class.\n";

    private static final String _options =
    "Options:\n" +
    "  -h, --help            show this help message and exit\n" +
    "  -v                    enable logging\n" +
    "  -a <address>, --broker-address=<address>\n" +
    "                        broker-addr is in the form:  [username/password@]\n" +
    "                        hostname | ip-address [:<port>]   ex:  localhost,\n" +
    "                        10.1.1.7:10000, broker-host:10000,\n" +
    "                        guest/guest@localhost\n" +
    "  -c <class>, --class=<class>\n" +
    "                        class of object on which command is being invoked\n" +
    "                        (default broker)\n" +
    "  -p <package>, --package=<package>\n" +
    "                        package of object on which command is being invoked\n" +
    "                        (default org.apache.qpid.broker)\n" +
    "  -i <id>, --id=<id>    identifier of object on which command is being invoked\n" +
    "                        (default amqp-broker)\n" +
    "  --agent=<agent name>\n" +
    "                        The name of the Agent to which commands will be sent\n" +
    "                        This will try to match <agent name> against the Agent name,\n" +
    "                        the Agent product name and will also check if the Agent name\n" +
    "                        contains the <agent name> String\n" +
    "                        (default qpidd)\n" +
    "  --sasl-mechanism=<mech>\n" +
    "                        SASL mechanism for authentication (e.g. EXTERNAL,\n" +
    "                        ANONYMOUS, PLAIN, CRAM-MD5, DIGEST-MD5, GSSAPI). SASL\n" +
    "                        automatically picks the most secure available\n" +
    "                        mechanism - use this option to override.\n";

    private Console _console;

    /**
     * Basic constructor. Creates JMS Session, Initialises Destinations, Producers &amp; Consumers and starts connection.
     * @param url the Connection URL.
     * @param connectionOptions the connection options String to pass to ConnectionHelper.
     * @param pkg the package name of the object we're invoking the method on.
     * @param cls the class name of the object we're invoking the method on.
     * @param id the ObjectId name of the object we're invoking the method on.
     * @param agentName the name of the Agent to invoke the QMF method on.
     * @param command the QMF method we're invoking.
     * @param args the Stringified Map form of the method arguments.
     */
    public QpidCtrl(final String url, final String connectionOptions, final String pkg, final String cls,
                    final String id, final String agentName, final String command, final String args)
    {
        try
        {
            Connection connection = ConnectionHelper.createConnection(url, connectionOptions);        
            _console = new Console();
            _console.addConnection(connection);

            // Find the specified Agent
            Agent agent = _console.findAgent(agentName);
            if (agent == null)
            {
                System.out.println("Agent " + agentName + " not found");
                System.exit(1);
            }

            List<Agent> agentList = Arrays.asList(new Agent[] {agent});
            List<QmfConsoleData> objects = _console.getObjects(pkg, cls, agentList);

            // Parse the args String
            QmfData inArgs = (args == null) ? new QmfData() : new QmfData(new AddressParser(args).map());

            // Find the required QmfConsoleData object and invoke the specified command
            MethodResult results = null;
            for (QmfConsoleData object : objects)
            {
                String objectName = object.getObjectId().getObjectName();
                if (command.equals("listObjects"))
                {
                    System.out.println(objectName);
                }
                else
                {
                    if (objectName.contains(id))
                    { // Use contains as ObjectNames may comprise other identifiers tha make using equals impractical
                        if (command.equals("listValues"))
                        {
                            object.listValues();
                            System.exit(1);
                        }
                        else
                        {
                            results = object.invokeMethod(command, inArgs);
                        }
                        break;
                    }
                }
            }

            if (results == null)
            {
                if (objects.size() == 0)
                {
                    System.out.println("getObjects(" + pkg + ", " + cls + ", " + agentName + ") returned no objects.");
                }
                else
                {
                    System.out.println("Id " + id + " not found in " + pkg + ":" + cls);
                }
            }
            else
            {
                if (results.succeeded())
                {
                    results.listValues();
                }
                else
                {
                    System.err.println ("QmfException " + results.getQmfException().getMessage() +
                                        " returned from " + command + " method");
                }
            }
        }
        catch (QmfException qmfe)
        {
            System.err.println ("QmfException " + qmfe.getMessage() + " caught in QpidCtrl constructor");
        }
    }

    /**
     * Runs QpidCtrl.
     * @param args the command line arguments.
     */
    public static void main(final String[] args)
    {
        String logLevel = System.getProperty("amqj.logging.level");
        logLevel = (logLevel == null) ? "FATAL" : logLevel; // Set default log level to FATAL rather than DEBUG.
        System.setProperty("amqj.logging.level", logLevel);

        // As of Qpid 0.16 the Session Dispatcher Thread is non-Daemon so the JVM gets prevented from exiting.
        // Setting the following property to true makes it a Daemon Thread.
        System.setProperty("qpid.jms.daemon.dispatcher", "true");

        String[] longOpts = {"help", "broker-address=", "class=", "package=", "id=", "agent=", "sasl-mechanism="};
        try
        {
            String host = "localhost";
            String connectionOptions = "{reconnect: true}";
            String cls = "broker";
            String pkg = "org.apache.qpid.broker";
            String id = "amqp-broker";
            String agentName = "qpidd";
            String command = null;
            String arg = null;

            GetOpt getopt = new GetOpt(args, "ha:c:p:i:v", longOpts);
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
                else if (opt[0].equals("-c") || opt[0].equals("--class"))
                {
                    cls = opt[1];
                }
                else if (opt[0].equals("-p") || opt[0].equals("--package"))
                {
                    pkg = opt[1];
                }
                else if (opt[0].equals("-i") || opt[0].equals("--id"))
                {
                    id = opt[1];
                }
                else if (opt[0].equals("--agent"))
                {
                    agentName = opt[1];
                }
                else if (opt[0].equals("-v"))
                {
                    System.setProperty("amqj.logging.level", "DEBUG");
                }
                else if (opt[0].equals("--sasl-mechanism"))
                {
                    connectionOptions = "{reconnect: true, sasl_mechs: " + opt[1] + "}";
                }
            }

            if (cargs.length < 1 || cargs.length > 2)
            {
                System.out.println(Arrays.asList(cargs));
                System.out.println(_usage);
                System.exit(1);
            }
    
            command = cargs[0];

            if (cargs.length == 2)
            {
                arg = cargs[1];
                if (!arg.startsWith("{") || !arg.endsWith("}"))
                {
                    System.out.println("Incorrect format for args.");
                    System.out.println("This needs to be in a Stringified Map format similar to an Address String");
                    System.exit(1);
                }
            }

            QpidCtrl qpidCtrl = new QpidCtrl(host, connectionOptions, pkg, cls, id, agentName, command, arg);
        }
        catch (IllegalArgumentException e)
        {
            System.out.println(_usage);
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }
}
