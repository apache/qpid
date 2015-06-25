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
package org.apache.qpid.restapi;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Misc Imports
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.MethodResult;
import org.apache.qpid.qmf2.console.QmfConsoleData;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;


/**
 * This class implements the REST API proper, it is an implementation of the Server interface which allows us to
 * abstract the "business logic" from the Web Server implementation technology (HttpServer/Servlet etc.).
 * <p>
 * The REST API is as follows:
 * <pre>
 * PUT: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;
 *      HTTP body: {"url":&lt;url&gt;,"connectionOptions":&lt;connectionOptions&gt;[,"disableEvents":true;]}
 *      &lt;url&gt;: A string containing an AMQP connection URL as used in the qpid::messaging API.
 *      &lt;connectionOptions&gt;: A JSON string containing connectionOptions in the form specified in the 
 *      qpid::messaging API.
 *
 *      This method creates a Qpid Connection Object with the name &lt;name&gt; using the specified url and options.
 *
 *      The optional disableEvents property is used to start up a QMF Connection which can only
 *      do synchronous calls such as getObjects() and can't receive Agent updates or QMF2 Events.
 *
 * DELETE: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;
 *
 *      This method deletes the Qpid Connection Object with the name &lt;name&gt;.
 *
 * POST: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/object/&lt;ObjectId&gt;
 *      HTTP body: {"_method_name":&lt;method&gt;,"_arguments":&lt;inArgs&gt;}
 *      &lt;method&gt;: A string containing the QMF2 method name e.g. "getLogLevel", "setLogLevel", "create", "delete".
 *      &lt;inArgs&gt;: A JSON string containing the method arguments e.g. {"level":"debug+:Broker"} for setLogLevel.
 *      HTTP response: A JSON string containing the response e.g. {"level":"notice+"} for getLogLevel (may be empty).
 *
 *      This method invokes the QMF2 method &lt;method&gt; with arguments &lt;inArgs&gt; on the object &lt;ObjectId&gt;
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection;
 * 
 *      This method retrieves (as a JSON string) the complete set of connections currently enabled on the Server.
 *      The returned JSON string represents a Map keyed by the connection name/handle. The value part is itself a
 *      Map containing the Connection URL and Connection Options used to create the Qpid Connection. e.g.
 *      {"8c5116d6-46a1-489b-93d8-fde525e0d76d":{"url":"0.0.0.0:5672","connectionOptions":{}}}
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;
 * 
 *      This method retrieves (as a JSON string) a Map containing the Connection URL and Connection Options used to    
 *      create the Qpid Connection with the specified &lt;name&gt;. e.g.
 *      {"url":"0.0.0.0:5672","connectionOptions":{}}
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/objects/&lt;className&gt;
 * 
 *      This method retrieves (as a JSON string) the list of QmfConsoleData objects with the specified &lt;className&gt; 
 *      using the QMF2 Console associated with the Qpid Connection Object with the name &lt;name&gt;.
 *      This is the REST equivalent of Console.getObjects(className) which searches across all packages and all Agents 
 *      for the specified className.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/objects/&lt;packageName&gt;/&lt;className&gt;
 *      !!not yet implemented!!
 *      This method retrieves (as a JSON string) the list of QmfConsoleData objects with the specified 
 *      &lt;packageName&gt; and &lt;className&gt; using the QMF2 Console associated with the Qpid Connection Object
 *      with the name &lt;name&gt;.
 *      This is the REST equivalent of Console.getObjects(packageName, className) which searches across all Agents 
 *      for the specified className in the package packageName.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/object/&lt;ObjectId&gt;
 *      This method retrieves (as a JSON string) the QmfConsoleData object with the specified &lt;ObjectId&gt;
 *      using the QMF2 Console associated with the Qpid Connection Object with the name &lt;name&gt;.
 *      This is the REST implementation of getObjects(oid) it is also the equivalent of the QmfConsoleData refresh() 
 *      method where an object can update its state.
 *      Note that there's a slight variance on the QMF2 API here as that returns an array/list of QmfConsoledData
 *      objects for all queries, however as the call with ObjectId will only return one or zero objects this 
 *      implementation returns the single QmfConsoleData object found or a 404 Not Found response.
 *
 *      N.B. that the ManagementAgent on the broker appears not to set the timestamp properties in the response to this
 *      call, which means that they get set to current time in the QmfConsoleData, this is OK for _update_ts but not
 *      for _create_ts and _delete_ts. Users of this call should be aware of that in their own code.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/objects
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/classes (synonyms)
 * 
 *      This method retrieves (as a JSON string) the list of SchemaClassId for all available Schema for all Agents.
 *      This is the REST equivalent of Console.getClasses() which searches across all Agents.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/classes/&lt;agentName&gt;
 * 
 *      This method retrieves (as a JSON string) the list of SchemaClassId for all available Schema on the
 *      Agent named &lt;agentName&gt;.
 *      This is the REST equivalent of Console.getClasses(agent) which searches across a specified Agent.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/packages
 * 
 *      This method retrieves (as a JSON string) the list of all known Packages for all Agents.
 *      This is the REST equivalent of Console.getPackages() which searches across all Agents.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/packages/&lt;agentName&gt;
 * 
 *      This method retrieves (as a JSON string) the list of all known Packages on the Agent named &lt;agentName&gt;.
 *      This is the REST equivalent of Console.getPackages(agent) which searches across a specified Agent.
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/agents
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/agent (synonyms)
 * 
 *      This method retrieves (as a JSON string) the list of all known Agents.
 *      This is the REST equivalent of Console.getAgents().
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/agent/&lt;agentName&gt;
 * 
 *      This method retrieves (as a JSON string) the Agent named &lt;agentName&gt;.
 *      This is the REST equivalent of Console.getAgent(agentName).
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/address
 * 
 *      This method retrieves (as a JSON string) the AMQP address this Console is listening to.
 *      This is the REST equivalent of Console.getAddress().
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/workItemCount
 * 
 *      This method retrieves (as a plain text string) the count of pending WorkItems that can be retrieved
 *      from this Console.
 *      This is the REST equivalent of Console.getWorkItemCount().
 *
 * GET: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/console/nextWorkItem
 * 
 *      This method retrieves (as a JSON string) the next pending work item from this Console (N.B. this method
 *      blocks until a WorkItem is available so should only be called asynchronously e.g. via AJAX).
 *      This is the REST equivalent of Console.getNextWorkitem().
 * </pre>
 * @author Fraser Adams
 */
public final class QpidServer implements Server
{
    private static final Logger _log = LoggerFactory.getLogger(QpidServer.class);

    private ConnectionStore _connections = new ConnectionStore();
    private String _defaultBroker = null;

    public QpidServer(final String broker)
    {
        _defaultBroker = broker;
    }

    /**
     * Handle a "/qpid/connection/<connectionName>/console/objects" or 
     * "/qpid/connection/<connectionName>/console/objects/" request,
     * in other words a request for information about an object resource specified by the remaining path.
     * Only the GET method is valid for this resource and it is in effect the REST mapping for Console.getObjects().
     */
    private void sendGetObjectsResponse(final HttpTransaction tx, final Console console, final String path) throws IOException
    {
        String[] params = path.split("/");
        if (params.length == 1)
        { // With one parameter we call getObjects(className)
            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getObjects(params[0])));
        }
        else if (params.length == 2)
        { // With two parameters we call getObjects(packageName, className)
            //System.out.println("params = " + params[0] + ", " + params[1]);
            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getObjects(params[0], params[1])));
        }
        else if (params.length == 3)
        { // TODO With three parameters we call getObjects(packageName, className, agent)
            //System.out.println("params = " + params[0] + ", " + params[1] + ", " + params[2]);
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Too many parameters for objects GET request.");
        } else {
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Too many parameters for objects GET request.");
        }
    }

    /**
     * Called by the Web Server to allow a Server to handle a GET request.
     * The HTTP GET URL structure for the REST API is specified above in the overall class documentation.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doGet(final HttpTransaction tx) throws IOException
    {
        String path = tx.getRequestURI();

        //System.out.println();
        //System.out.println("QpidServer doGet " + path);
        //tx.logRequest();

        if (path.startsWith("/qpid/connection/"))
        {
            path = path.substring(17);

            String user = tx.getPrincipal(); // Using the principal lets different users use the default connection.
            if (path.length() == 0)
            { // handle "/qpid/connection/" request with unspecified connection (returns list of available connections).
                tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(_connections.getAll(user)));   
            }
            else
            { // if path.length() > 0 we're dealing with a specified Connection so extract the name and look it up.
                String connectionName = path;
                int i = path.indexOf("/");
                if (i > 0) // Can use > rather than >= as we've already tested for "/qpid/connection/" above.
                {
                    connectionName = path.substring(0, i);
                    path = path.substring(i + 1);
                }
                else
                {
                    path = "";
                }

                connectionName = user + "." + connectionName;

                // TODO what if we don't want a default connection.
                // If necessary we create a new "default" Connection associated with the user. The default connection
                // attempts to connect to a broker specified in the QpidRestAPI config (or default 0.0.0.0:5672).
                if (connectionName.equals(user + ".default"))
                {
                    ConnectionProxy defaultConnection = _connections.get(connectionName);
                    if (defaultConnection == null)
                    {
                        defaultConnection = _connections.create(connectionName, _defaultBroker, "", false);

                        // Wait a maximum of 1000ms for the underlying Qpid Connection to become available. If we
                        // don't do this the first call using the default will return 404 Not Found.
                        defaultConnection.waitForConnection(1000);
                    }
                }

                // Find the Connection with the name extracted from the URI.
                ConnectionProxy connection = _connections.get(connectionName);

                if (connection == null)
                {
                    tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                }
                else if (!connection.isConnected())
                {
                    tx.sendResponse(HTTP_INTERNAL_ERROR, "text/plain", "500 Broker Disconnected.");
                }
                else
                {
                    if (path.length() == 0)
                    { // handle request for information about a specified Console
                        tx.sendResponse(HTTP_OK, "application/json", connection.toString());
                    }
                    else
                    { // In this block we are dealing with resources associated with a specified connectionName.
                      // path describes the resources specifically related to "/qpid/connection/<connectionName>"
                        Console console = connection.getConsole();

                        if (path.startsWith("console/objects/"))
                        { // Get information about specified objects.
                            path = path.substring(16);
                            sendGetObjectsResponse(tx, console, path);
                        }
                        else if (path.startsWith("console/objects") && path.length() == 15)
                        {  // If objects is unspecified treat as a synonym for classes.
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getClasses()));
                        }
                        else if (path.startsWith("console/address/"))
                        { // Get the Console AMQP Address
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getAddress()));
                        }
                        else if (path.startsWith("console/address") && path.length() == 15)
                        { // Get the Console AMQP Address
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getAddress()));
                        }
                        else if (path.startsWith("console/workItemCount/"))
                        { // Returns the count of pending WorkItems that can be retrieved.
                            tx.sendResponse(HTTP_OK, "text/plain", "" + console.getWorkitemCount());
                        }
                        else if (path.startsWith("console/workItemCount") && path.length() == 21)
                        { // Returns the count of pending WorkItems that can be retrieved.
                            tx.sendResponse(HTTP_OK, "text/plain", "" + console.getWorkitemCount());
                        }
                        else if (path.startsWith("console/nextWorkItem/"))
                        { // Obtains the next pending work item, or null if none available.
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getNextWorkitem()));
                        }
                        else if (path.startsWith("console/nextWorkItem") && path.length() == 20)
                        { // Obtains the next pending work item, or null if none available.
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getNextWorkitem()));
                        }
                        else if (path.startsWith("console/agents") && path.length() == 14)
                        { // Get information about all available Agents.
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getAgents()));
                        }
                        else if (path.startsWith("console/agent/"))
                        { // Get information about a specified Agent.
                            Agent agent = console.getAgent(path.substring(14));
                            if (agent == null)
                            {
                                tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                            }
                            else
                            {
                                tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(agent));
                            }
                        }
                        else if (path.startsWith("console/agent") && path.length() == 13)
                        { // If agent is unspecified treat as a synonym for agents.
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getAgents()));
                        }
                        else if (path.startsWith("console/classes/"))
                        { // Get information about the classes for a specified Agent
                            path = path.substring(16); // Get Agent name

                            // TODO handle getClasses() for specified Agent
                            tx.sendResponse(HTTP_NOT_IMPLEMENTED, "text/plain", "501 getClasses() for specified Agent not yet implemented.");
                        }
                        else if (path.startsWith("console/classes") && path.length() == 15)
                        { // Get information about all the classes for all Agents
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getClasses()));
                        }
                        else if (path.startsWith("console/packages/"))
                        { // Get information about the packages for a specified Agent
                            path = path.substring(17); // Get Agent name

                            // TODO handle getPackages() for specified Agent.
                            tx.sendResponse(HTTP_NOT_IMPLEMENTED, "text/plain", "501 getPackages() for specified Agent not yet implemented.");
                        }
                        else if (path.startsWith("object/"))
                        {
                            /**
                             * This is the REST implementation of getObjects(oid) it is also the equivalent of
                             * the QmfConsoleData refresh() method where an object can update its state.
                             * N.B. that the ManagementAgent on the broker appears not to set the timestamp properties
                             * in the response to this call, which means that they get set to current time in the
                             * QmfConsoleData, this is OK for _update_ts but not for _create_ts and _delete_ts
                             * users of this call should be aware of that in their own code.
                             */
                            path = path.substring(7);

                            // The ObjectId has been passed in the URI, create a real ObjectId
                            ObjectId oid = new ObjectId(path);

                            List<QmfConsoleData> objects = console.getObjects(oid);
                            if (objects.size() == 0)
                            {
                                tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                            }
                            else
                            {
                                // Not that in a departure from the QMF2 API this returns the QmfConsoleData object
                                // rather than a list of size one. Perhaps the APIs should be completely consistent
                                // but this response seems more convenient.
                                tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(objects.get(0)));
                            }
                        }
                        else if (path.startsWith("console/packages") && path.length() == 16)
                        { // Get information about all the packages for all Agents
                            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(console.getPackages()));
                        }
                        else
                        {
                            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                        }
                    }
                }
            }
        }
        else if (path.startsWith("/qpid/connection"))
        { // handle "/qpid/connection" request with unspecified connection (returns list of available connections).
            String user = tx.getPrincipal(); // Using the principal lets different users use the default connection.
            tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(_connections.getAll(user)));   
        }
        else
        {
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
        }
    }

    /**
     * Called by the Web Server to allow a Server to handle a POST request.
     * <pre>
     * POST: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;/object/&lt;ObjectId&gt;
     *      HTTP body: {"_method_name":&lt;method&gt;,"_arguments":&lt;inArgs&gt;}
     *      &lt;method&gt;: A string containing the QMF2 method name e.g. "getLogLevel", "setLogLevel", "create", "delete".
     *      &lt;inArgs&gt;: A JSON string containing the method arguments e.g. {"level":"debug+:Broker"} for setLogLevel.
     *      HTTP response: A JSON string containing the response e.g. {"level":"notice+"} for getLogLevel (may be empty).
     *
     *      This method invokes the QMF2 method &lt;method&gt; with arguments &lt;inArgs&gt; on the object &lt;ObjectId&gt;
     * </pre>
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    @SuppressWarnings("unchecked")
    public void doPost(final HttpTransaction tx) throws IOException
    {
        String path = tx.getRequestURI();

        //System.out.println();
        //System.out.println("QpidServer doPost " + path);
        //System.out.println("thread = " + Thread.currentThread().getId());

        if (path.startsWith("/qpid/connection/"))
        {
            path = path.substring(17);
            String user = tx.getPrincipal();

            String connectionName = path;
            int i = path.indexOf("/");
            if (i > 0) // Can use > rather than >= as we've already tested for "/qpid/connection/" above.
            {
                connectionName = user + "." + path.substring(0, i);
                path = path.substring(i + 1);

                // Find the Connection with the name extracted from the URI.
                ConnectionProxy connection = _connections.get(connectionName);

                if (connection == null)
                {
                    _log.info("QpidServer.doPost path: {} Connection not found.", tx.getRequestURI());
                    tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                }
                else if (!connection.isConnected())
                {
                    tx.sendResponse(HTTP_INTERNAL_ERROR, "text/plain", "500 Broker Disconnected.");
                }
                else
                { // If we get this far we should have found a Qpid Connection so retrieve the QMF2 Console Object.
                    Console console = connection.getConsole();

                    if (path.startsWith("object/"))
                    {
                        path = path.substring(7);

                        // The ObjectId has been passed in the URI create an ObjectId and retrieve the Agent Name.
                        ObjectId oid = new ObjectId(path);
                        String agentName = oid.getAgentName();

                        // The qpidd ManagementAgent doesn't populate AgentName, if it's empty assume it's the broker.
                        agentName = agentName.equals("") ? "broker" : agentName;

                        Agent agent = console.getAgent(agentName); // Find the Agent we got the QmfData from.
                        if (agent == null)
                        {
                            _log.info("QpidServer.doPost path: {} Agent: {} not found.", tx.getRequestURI(), agentName);
                            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                        }
                        else
                        {   // If we get this far we can create the Object and invoke the method.
                            // We can create a QmfConsoleData with nothing but an ObjectId and the agent.
                            QmfConsoleData object = new QmfConsoleData(Collections.EMPTY_MAP, agent);
                            object.setObjectId(oid);

                            String request = tx.getRequestString();
                            _log.info("QpidServer.doPost path: {} body: {}", tx.getRequestURI(), request);

                            //System.out.println(request);
                            String method = "";
                            try
                            {
                                Map<String, Object> reqMap = JSON.toMap(request);

                                method = (String)reqMap.get("_method_name");
                                Object arguments = reqMap.get("_arguments");

                                Map args = (arguments instanceof Map) ? (Map)arguments : null;
                                //System.out.println("method: " + method + ", args: " + args);

                                // Parse the args if present into a QmfData (needed by invokeMethod).
                                QmfData inArgs = (args == null) ? new QmfData() : new QmfData(args);

                                // Invoke the specified method on the QmfConsoleData we've created.
                                MethodResult results = null;

                                _log.info("invokeMethod: {}", request);
                                results = object.invokeMethod(method, inArgs);
                                tx.sendResponse(HTTP_OK, "application/json", JSON.fromObject(results));
                            }
                            catch (QmfException qmfe)
                            {
                                _log.info("QpidServer.doPost() caught Exception {}", qmfe.getMessage());
                                tx.sendResponse(HTTP_INTERNAL_ERROR, "text/plain", "invokeMethod(" + 
                                                method + ") -> " + qmfe.getMessage());
                            }
                            catch (Exception e)
                            {
                                _log.info("QpidServer.doPost() caught Exception {}", e.getMessage());
                                tx.sendResponse(HTTP_INTERNAL_ERROR, "text/plain", "500 " + e.getMessage());
                            }
                        }
                    }
                    else
                    {
                        tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
                    }
                }
            }
            else
            {
                tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
            }
        }
        else
        {
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
        }
    }

    /**
     * <pre>
     * PUT: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;
     *      HTTP body: {"url":&lt;url&gt;,"connectionOptions":&lt;connectionOptions&gt;[,"disableEvents":true;]}
     *      &lt;url&gt;: A string containing an AMQP connection URL as used in the qpid::messaging API.
     *      &lt;connectionOptions&gt;: A JSON string containing connectionOptions in the form specified in the 
     *      qpid::messaging API.
     * </pre>
     * Called by the Web Server to allow a Server to handle a PUT request.
     * This method creates a Qpid Connection Object with the name &lt;name&gt; using the specified url and options.
     * <p>
     * The optional disableEvents property is used to start up a QMF Connection which can only
     * do synchronous calls such as getObjects() and can't receive Agent updates or QMF2 Events.
     * <p>
     * N.B. It is possible for the Qpid broker to be unavailable when this method is called so it is actually a
     * ConnectionProxy that is created. This method waits for up to 1000ms for the underlying Qpid Connection to
     * become available and then returns. Clients should be aware that this method successfully returning only
     * implies that the ConnectionProxy is in place and the underlying Qpid Connection may not be available.
     * If the broker is down the ConnectionProxy will periodically attempt to reconnect, but whilst it is down
     * the REST API will return a 500 Broker Disconnected response to any PUT or POST call.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    @SuppressWarnings("unchecked")
    public void doPut(final HttpTransaction tx) throws IOException
    {
        String path = tx.getRequestURI();
        //tx.logRequest();

        if (path.startsWith("/qpid/connection/"))
        {
            path = path.substring(17);
            String user = tx.getPrincipal();
            String request = tx.getRequestString();
            _log.info("QpidServer.doPut path: {} body: {}", tx.getRequestURI(), request);

            String name = user + "." + path;

            try
            {
                // The PUT request is a JSON string containing a url String property and a connectionOptions
                // property which is itself a JSON String.
                Map<String, String> reqMap = JSON.toMap(request);

                String url = reqMap.get("url");
                url = url.equals("") ? _defaultBroker : url;

                // Extract the connectionOptions property and check its type is a MAP
                Object options = reqMap.get("connectionOptions");
                Map optionsMap = (options instanceof Map) ? (Map)options : null;

                // Turn the connectionOptions Map back into a JSON String. Note that we can't just get
                // connectionOptions as a String from reqMap as it is sent as JSON and the JSON.toMap()
                // call on the POST request will fully parse it.
                String connectionOptions = JSON.fromObject(optionsMap);

                boolean disableEvents = false;
                String disableEventsString = reqMap.get("disableEvents");
                if (disableEventsString != null)
                {
                    disableEvents = disableEventsString.equalsIgnoreCase("true");
                }

                ConnectionProxy proxy = _connections.create(name, url, connectionOptions, disableEvents);

                // Wait a maximum of 1000ms for the underlying Qpid Connection to become available then return.
                proxy.waitForConnection(1000);
                tx.sendResponse(HTTP_CREATED, "text/plain", "201 Created.");
            } 
            catch (Exception e)
            {
                _log.info("QpidServer.doPut() caught Exception {}", e.getMessage());
                tx.sendResponse(HTTP_INTERNAL_ERROR, "text/plain", "500 " + e.getMessage());
            }
        }
        else
        {
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
        }
    }

    /**
     * Called by the Web Server to allow a Server to handle a DELETE request.
     *
     * DELETE: &lt;host&gt;:&lt;port&gt;/qpid/connection/&lt;name&gt;
     *
     *      This method deletes the Qpid Connection Object with the name &lt;name&gt;.
     *
     * @param tx the HttpTransaction containing the request from the client and used to send the response.
     */
    public void doDelete(final HttpTransaction tx) throws IOException
    {
        String path = tx.getRequestURI();
        //tx.logRequest();

        if (path.startsWith("/qpid/connection/"))
        {
            path = path.substring(17);
            String user = tx.getPrincipal();
            String name = user + "." + path;

            //System.out.println("Deleting " + name);
            _log.info("QpidServer.doDelete path: {}", tx.getRequestURI());

            _connections.delete(name);
            tx.sendResponse(HTTP_OK, "text/plain", "200 Deleted.");
        }
        else
        {
            tx.sendResponse(HTTP_NOT_FOUND, "text/plain", "404 Not Found.");
        }
    }
}


