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
package org.apache.qpid.qmf2.test;

import javax.jms.Connection;

// Misc Imports
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.agent.AgentExternal;
import org.apache.qpid.qmf2.agent.MethodCallParams;
import org.apache.qpid.qmf2.agent.MethodCallWorkItem;
import org.apache.qpid.qmf2.agent.ResubscribeParams;
import org.apache.qpid.qmf2.agent.ResubscribeRequestWorkItem;
import org.apache.qpid.qmf2.agent.SubscribeRequestWorkItem;
import org.apache.qpid.qmf2.agent.SubscribableAgent;
import org.apache.qpid.qmf2.agent.Subscription;
import org.apache.qpid.qmf2.agent.SubscriptionParams;
import org.apache.qpid.qmf2.agent.UnsubscribeRequestWorkItem;
import org.apache.qpid.qmf2.agent.QueryWorkItem;
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;
import org.apache.qpid.qmf2.common.QmfType;
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.SchemaProperty;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * Class used to test the AgentExternal.
 * This class provides a demo of all of the features available on the AgentExternal model including Subscriptions.
 * It provides essentially the same behaviour as the AgentTest class though requires a lot more code.
 *
 * The AgentExternal class and this demo are largely provided for completeness (although they do behave correctly)
 * as the author isn't convinced that there's a good reason for using AgentExternal rather than Agent.
 *
 * @author Fraser Adams
 */
public final class AgentExternalTest implements QmfEventListener, SubscribableAgent
{
    /** 
     * This TimerTask causes the Agent to Reap any objects marked as deleted when it gets scheduled
     */
    private final class Reaper extends TimerTask
    {
        public void run()
        {
            // Reap any QmfAgentData Objects that have been marked as Deleted
            // Use the iterator approach rather than foreach as we may want to call iterator.remove() to zap an entry
            Iterator<QmfAgentData> i = _objectIndex.values().iterator();
            while (i.hasNext())
            {
                QmfAgentData object = i.next();
                if (object.isDeleted())
                {
System.out.println("****** Removing deleted Object *******");
                    i.remove();
                }
            }
        }
    }

    private AgentExternal _agent;
    private QmfAgentData _control;
    private SchemaObjectClass _exceptionSchema;
    private SchemaObjectClass _controlSchema;
    private SchemaObjectClass _childSchema;
    private SchemaEventClass _eventSchema;

    /**
     * objectIndex is the global index of QmfAgentData objects registered with this Agent
     */
    private Map<ObjectId, QmfAgentData> _objectIndex = new ConcurrentHashMap<ObjectId, QmfAgentData>();

    /**
     * This Map is used to look up Subscriptions by SubscriptionId
     */
    private Map<String, Subscription> _subscriptions = new ConcurrentHashMap<String, Subscription>();

    private Timer _timer;

    public AgentExternalTest(String url)
    {
        try
        {
            System.out.println("** Starting AgentExternalTest a test of basic AgentExternal class functions **");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _agent = new AgentExternal(this);
            _agent.setVendor("profitron.com");
            _agent.setProduct("gizmo");
            _agent.setValue("attr1", 2000);

            System.out.println("Agent name: " + _agent.getName());

            // Schedule a Reap every 10 seconds sending the first one immediately
            _timer = new Timer(true);
            _timer.schedule(new Reaper(), 0, 10000);

            setupSchema();
            populateData();

            _agent.setConnection(connection);

            for (int i = 0; i < 100; i++)
            {
                _control.setValue("offset", i);
                //control.update(); // Send data indication to the Subscriber on the next Subscription interval
                _control.publish(); // Send data indication to the Subscriber immediately
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException ie)
                {
                }
            }

            _control.destroy();


            // A getObjects call seems necessary to enable automatic reconnection when broker restarts
            // I've got no idea why this is the case though!!!
            //List<QmfConsoleData> connections = console.getObjects("broker");
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: AgentExternalTest failed");
        }
    }

    public void onEvent(WorkItem wi)
    {
        System.out.println("WorkItem type: " + wi.getType());

        if (wi.getType() == METHOD_CALL)
        {
            _control.incValue("methodCount", 1);

            MethodCallWorkItem item = (MethodCallWorkItem)wi;
            MethodCallParams methodCallParams = item.getMethodCallParams();
            String methodName = methodCallParams.getName();
            ObjectId objectId = methodCallParams.getObjectId();
            String userId = methodCallParams.getUserId();
            QmfData inArgs = methodCallParams.getArgs();
            ObjectId controlAddress = _control.getObjectId();

            System.out.println("Method Call User ID = " + userId);

            try
            {
                if (objectId == null)
                {
                    // Method invoked directly on Agent
                    if (methodName.equals("toString"))
                    {
                        QmfData outArgs = new QmfData();
                        outArgs.setValue("string", _agent.toString());
                        _agent.methodResponse(methodName, item.getHandle(), outArgs, null);
                    }
                }
                else if (objectId.equals(controlAddress))
                {
                    if (methodName.equals("stop"))
                    {
                        System.out.println("Invoked stop method");
                        String message = inArgs.getStringValue("message");
                        System.out.println("Stopping: message = " + message);
                        _agent.methodResponse(methodName, item.getHandle(), null, null);
                        _agent.destroy();
                        System.exit(1);
                    }
                    else if (methodName.equals("echo"))
                    {
                        System.out.println("Invoked echo method");
                        _agent.methodResponse(methodName, item.getHandle(), inArgs, null);
                    }
                    else if (methodName.equals("event"))
                    {
                        System.out.println("Invoked event method");
                        QmfEvent event = new QmfEvent(_eventSchema);
                        event.setSeverity((int)inArgs.getLongValue("severity"));
                        event.setValue("text", inArgs.getStringValue("text"));
                        _agent.raiseEvent(event);
                        _agent.methodResponse(methodName, item.getHandle(), null, null);
                    }
                    else if (methodName.equals("fail"))
                    {
                        System.out.println("Invoked fail method");
                        QmfData error = new QmfData();
                        if (inArgs.getBooleanValue("useString"))
                        {
                            error.setValue("error_text", inArgs.getStringValue("stringVal"));
                        }
                        else
                        {
                            error.setValue("whatHappened", "It Failed");
                            error.setValue("howBad", 75);
                            error.setValue("details", inArgs.getValue("details"));
                        }
                        _agent.methodResponse(methodName, item.getHandle(), null, error);
                    }
                    else if (methodName.equals("create_child"))
                    {
                        System.out.println("Invoked create_child method");
                        String childName = inArgs.getStringValue("name");
                        System.out.println("childName = " + childName);
                        QmfAgentData child = new QmfAgentData(_childSchema);
                        child.setValue("name", childName);
                        addObject(child);
                        QmfData outArgs = new QmfData();
                        outArgs.setRefValue("childAddr", child.getObjectId(), "reference"); // Set suptype just to test
                        _agent.methodResponse(methodName, item.getHandle(), outArgs, null);
                    }
                }
            }
            catch (QmfException qmfe)
            {
                System.err.println("QmfException " + qmfe.getMessage() + " caught: AgentExternalTest failed");
                QmfData error = new QmfData();
                error.setValue("error_text", qmfe.getMessage());
                _agent.methodResponse(methodName, item.getHandle(), null, error);
            }
        }

        if (wi.getType() == QUERY)
        {
            QueryWorkItem item = (QueryWorkItem)wi;
            QmfQuery query = item.getQmfQuery();

            System.out.println("Query User ID = " + item.getUserId());

            if (query.getObjectId() != null)
            {
                // Look up a QmfAgentData object by the ObjectId obtained from the query
                ObjectId objectId = query.getObjectId();
                QmfAgentData object = _objectIndex.get(objectId);
                if (object != null && !object.isDeleted())
                {
                    _agent.queryResponse(item.getHandle(), object);
                }
                _agent.queryComplete(item.getHandle(), 0);
            }
            else
            {
                // Look up QmfAgentData objects by the SchemaClassId obtained from the query
                // This is implemented by a linear search and allows searches with only the className specified.
                // Linear searches clearly don't scale brilliantly, but the number of QmfAgentData objects managed
                // by an Agent is generally fairly small, so it should be OK. Note that this is the same approach
                // taken by the C++ broker ManagementAgent, so if it's a problem here........
                for (QmfAgentData object : _objectIndex.values())
                {
                    if (!object.isDeleted() && query.evaluate(object))
                    {
                        _agent.queryResponse(item.getHandle(), object);
                    }
                }
                _agent.queryComplete(item.getHandle(), 0);
            }
        }

        if (wi.getType() == SUBSCRIBE_REQUEST)
        {
            SubscribeRequestWorkItem item = (SubscribeRequestWorkItem)wi;
            SubscriptionParams params = item.getSubscriptionParams();
            Handle handle = item.getHandle();

            System.out.println("Subscribe Request User ID = " + params.getUserId());

            try
            {
                Subscription subscription = new Subscription(this, params);
                _subscriptions.put(subscription.getSubscriptionId(), subscription);
                _timer.schedule(subscription, 0, params.getPublishInterval());

                if (subscription == null)
                {
System.out.println("Requested Subscription has already expired or been cancelled");
                    QmfData error = new QmfData();
                    error.setValue("error_text", "Requested Subscription has already expired or been cancelled");
                    _agent.subscriptionResponse(handle, subscription.getConsoleHandle(), null, 0, 0, error);
                }
                else
                {
                    _agent.subscriptionResponse(handle, subscription.getConsoleHandle(), subscription.getSubscriptionId(), 
                                               subscription.getDuration(), subscription.getInterval(), null);
                }
            }
            catch (QmfException qmfe)
            {
                _agent.raiseException(handle, "Subscribe Request failed, invalid Query: " + qmfe.getMessage());
            }
        }

        if (wi.getType() == RESUBSCRIBE_REQUEST)
        {
            ResubscribeRequestWorkItem item = (ResubscribeRequestWorkItem)wi;
            ResubscribeParams params = item.getResubscribeParams();
            Handle handle = item.getHandle();

            System.out.println("Resubscribe Request User ID = " + params.getUserId());

            String subscriptionId = params.getSubscriptionId();
            Subscription subscription = _subscriptions.get(subscriptionId);
            if (subscription != null)
            {
                subscription.refresh(params);
                _agent.subscriptionResponse(handle, subscription.getConsoleHandle(), subscription.getSubscriptionId(), 
                                           subscription.getDuration(), subscription.getInterval(), null);
            }
            else
            {
System.out.println("Requested Subscription has already expired or been cancelled");
                QmfData error = new QmfData();
                error.setValue("error_text", "Requested Subscription has already expired or been cancelled");
                _agent.subscriptionResponse(handle, subscription.getConsoleHandle(), null, 0, 0, error);
            }
        }

        if (wi.getType() == UNSUBSCRIBE_REQUEST)
        {
            UnsubscribeRequestWorkItem item = (UnsubscribeRequestWorkItem)wi;
            String subscriptionId = item.getSubscriptionId();
System.out.println("Received cancellation request for " + subscriptionId);
            Subscription subscription = _subscriptions.get(subscriptionId);
            if (subscription != null)
            {
                subscription.cancel();
            }
        }

    }

    public void setupSchema() throws QmfException
    {
        System.out.println("*** AgentExternalTest initialising the various Schema classes ***");
                
        // Create and register schema for this agent.
        String packageName = "com.profitron.gizmo";

        // Declare a schema for a structured exception that can be used in failed method invocations.
        _exceptionSchema = new SchemaObjectClass(packageName, "exception");
        _exceptionSchema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));
        _exceptionSchema.addProperty(new SchemaProperty("howBad", QmfType.TYPE_INT));
        _exceptionSchema.addProperty(new SchemaProperty("details", QmfType.TYPE_MAP));

        // Declare a control object to test methods against.
        _controlSchema = new SchemaObjectClass(packageName, "control");
        _controlSchema.addProperty(new SchemaProperty("state", QmfType.TYPE_STRING));
        _controlSchema.addProperty(new SchemaProperty("methodCount", QmfType.TYPE_INT));
        _controlSchema.addProperty(new SchemaProperty("offset", QmfType.TYPE_INT));
        _controlSchema.setIdNames("state");

        SchemaMethod stopMethod = new SchemaMethod("stop", "Stop Agent");
        stopMethod.addArgument(new SchemaProperty("message", QmfType.TYPE_STRING, "{dir:IN}"));
        _controlSchema.addMethod(stopMethod);

        SchemaMethod echoMethod = new SchemaMethod("echo", "Echo Arguments");
        echoMethod.addArgument(new SchemaProperty("message", QmfType.TYPE_STRING, "{dir:INOUT}"));
        _controlSchema.addMethod(echoMethod);

        SchemaMethod eventMethod = new SchemaMethod("event", "Raise an Event");
        eventMethod.addArgument(new SchemaProperty("text", QmfType.TYPE_STRING, "{dir:IN}"));
        eventMethod.addArgument(new SchemaProperty("severity", QmfType.TYPE_INT, "{dir:IN}"));
        _controlSchema.addMethod(eventMethod);

        SchemaMethod failMethod = new SchemaMethod("fail", "Expected to Fail");
        failMethod.addArgument(new SchemaProperty("useString", QmfType.TYPE_BOOL, "{dir:IN}"));
        failMethod.addArgument(new SchemaProperty("stringVal", QmfType.TYPE_STRING, "{dir:IN}"));
        failMethod.addArgument(new SchemaProperty("details", QmfType.TYPE_MAP, "{dir:IN}"));
        _controlSchema.addMethod(failMethod);

        SchemaMethod createMethod = new SchemaMethod("create_child", "Create Child Object");
        createMethod.addArgument(new SchemaProperty("name", QmfType.TYPE_STRING, "{dir:IN}"));
        createMethod.addArgument(new SchemaProperty("childAddr", QmfType.TYPE_MAP, "{dir:OUT}"));
        _controlSchema.addMethod(createMethod);

        // Declare the child class
        _childSchema = new SchemaObjectClass(packageName, "child");
        _childSchema.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
        _childSchema.setIdNames("name");
    
        // Declare the event class
        _eventSchema = new SchemaEventClass(packageName, "event");
        _eventSchema.addProperty(new SchemaProperty("text", QmfType.TYPE_STRING));

        System.out.println("AgentExternalTest Schema classes initialised OK");

        _agent.registerObjectClass(_exceptionSchema);
        _agent.registerObjectClass(_controlSchema);
        _agent.registerObjectClass(_childSchema);
        _agent.registerEventClass(_eventSchema);

        System.out.println("AgentExternalTest Schema classes registered OK");
    }

    public void populateData() throws QmfException
    {
        System.out.println("*** AgentExternalTest creating a control object ***");

        _control = new QmfAgentData(_controlSchema);
        _control.setValue("state", "OPERATIONAL");
        _control.setValue("methodCount", 0);

        addObject(_control);
        System.out.println("AgentExternalTest Schema control object added OK");
    }

    public void addObject(QmfAgentData object) throws QmfException
    {
        ObjectId addr = _agent.allocObjectId(UUID.randomUUID().toString());
        object.setObjectId(addr);
        _objectIndex.put(addr, object);

        // Does the new object match any Subscriptions? If so add a reference to the matching Subscription and publish.
        for (Subscription subscription : _subscriptions.values())
        {
            QmfQuery query = subscription.getQuery();
            if (query.getObjectId() != null)
            {
                if (query.getObjectId().equals(addr))
                {
                    object.addSubscription(subscription.getSubscriptionId(), subscription);
                    object.publish();
                }
            }
            else if (query.evaluate(object))
            {
                object.addSubscription(subscription.getSubscriptionId(), subscription);
                object.publish();
            }
        }
    }



    //                               methods implementing SubscriberProxy interface
    // ********************************************************************************************************

    /**
     * Send a list of updated subscribed data to the Console.
     *
     * @param handle the console reply handle
     * @param results a list of subscribed data in Map encoded form
     */
    public void sendSubscriptionIndicate(Handle handle, List<Map> results)
    {
        _agent.sendSubscriptionIndicate(handle, results);
    }

    /**
     * This method evaluates a QmfQuery over the Agent's data on behalf of a Subscription
     *
     * @param query the QmfQuery that the Subscription wants to be evaluated over the Agent's data
     * @return a List of QmfAgentData objects that match the specified QmfQuery
     */
    public List<QmfAgentData> evaluateQuery(QmfQuery query)
    {
        List<QmfAgentData> results = new ArrayList<QmfAgentData>(_objectIndex.size());
        if (query.getTarget() == QmfQueryTarget.OBJECT)
        {
            if (query.getObjectId() != null)
            {
                // Look up a QmfAgentData object by the ObjectId obtained from the query
                ObjectId objectId = query.getObjectId();
                QmfAgentData object = _objectIndex.get(objectId);
                if (object != null && !object.isDeleted())
                {
                    results.add(object);
                }
            }
            else
            {
                // Look up QmfAgentData objects evaluating the query
                for (QmfAgentData object : _objectIndex.values())
                {
                    if (!object.isDeleted() && query.evaluate(object))
                    {
                        results.add(object);
                    }
                }
            }
        }
        return results;
    }

    /**
     * This method is called by the Subscription to tell the SubscriberProxy that the Subscription has been cancelled.
     *
     * @param subscription the Subscription that has been cancelled and is requesting removal.
     */
    public void removeSubscription(Subscription subscription)
    {
        _subscriptions.remove(subscription.getSubscriptionId());
    }


    public static void main(String[] args)
    {
        //System.out.println("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        AgentExternalTest test1 = new AgentExternalTest(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("ConnectionAudit main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending AgentExternalTest ***");
    }
}
