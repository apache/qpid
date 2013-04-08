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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.agent.Agent;
import org.apache.qpid.qmf2.agent.MethodCallParams;
import org.apache.qpid.qmf2.agent.MethodCallWorkItem;
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfType;
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.SchemaProperty;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * A class used to test the Agent API functionality.
 *
 * @author Fraser Adams
 */
public final class AgentTest implements QmfEventListener
{
    private Agent _agent;
    private QmfAgentData _control;
    private SchemaObjectClass _exceptionSchema;
    private SchemaObjectClass _controlSchema;
    private SchemaObjectClass _childSchema;
    private SchemaEventClass _eventSchema;

    public AgentTest(String url)
    {
        try
        {
            System.out.println("** Starting AgentTest a test of basic Agent class functions **");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _agent = new Agent(this);
            _agent.setVendor("profitron.com");
            _agent.setProduct("gizmo");
            _agent.setValue("attr1", 2000);

            System.out.println("Agent name: " + _agent.getName());

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

        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: AgentTest failed");
        }
    }

    public void onEvent(WorkItem wi)
    {
        System.out.println("WorkItem type: " + wi.getType());
        _control.incValue("methodCount", 1);

        if (wi.getType() == METHOD_CALL)
        {
            MethodCallWorkItem item = (MethodCallWorkItem)wi;
            MethodCallParams methodCallParams = item.getMethodCallParams();
            String methodName = methodCallParams.getName();
            ObjectId objectId = methodCallParams.getObjectId();
            String userId = methodCallParams.getUserId();
            userId = userId.equals("") ? "anonymous" : userId;
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
                        _agent.addObject(child);
                        QmfData outArgs = new QmfData();
                        outArgs.setRefValue("childAddr", child.getObjectId(), "reference"); // Set subtype just to test
                        _agent.methodResponse(methodName, item.getHandle(), outArgs, null);
                    }
                }
            }
            catch (QmfException qmfe)
            {
                System.err.println("QmfException " + qmfe.getMessage() + " caught: AgentTest failed");
                QmfData error = new QmfData();
                error.setValue("error_text", qmfe.getMessage());
                _agent.methodResponse(methodName, item.getHandle(), null, error);
            }
        }
    }

    public void setupSchema() throws QmfException
    {
        System.out.println("*** AgentTest initialising the various Schema classes ***");
                
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

        System.out.println("AgentTest Schema classes initialised OK");

        _agent.registerObjectClass(_exceptionSchema);
        _agent.registerObjectClass(_controlSchema);
        _agent.registerObjectClass(_childSchema);
        _agent.registerEventClass(_eventSchema);

        System.out.println("AgentTest Schema classes registered OK");
    }

    public void populateData() throws QmfException
    {
        System.out.println("*** AgentTest creating a control object ***");

        _control = new QmfAgentData(_controlSchema);
        _control.setValue("state", "OPERATIONAL");
        _control.setValue("methodCount", 0);
        _agent.addObject(_control);
        System.out.println("AgentTest Schema control object added OK");
    }


    public static void main(String[] args)
    {
        //System.out.println("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        AgentTest test1 = new AgentTest(url);

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

        System.out.println("*** Ending AgentTest ***");
    }
}
