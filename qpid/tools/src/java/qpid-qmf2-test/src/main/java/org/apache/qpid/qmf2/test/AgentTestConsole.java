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
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.AgentAddedWorkItem;
import org.apache.qpid.qmf2.console.AgentHeartbeatWorkItem;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.EventReceivedWorkItem;
import org.apache.qpid.qmf2.console.MethodResult;
import org.apache.qpid.qmf2.console.MethodResponseWorkItem;
import org.apache.qpid.qmf2.console.ObjectUpdateWorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * This class is the Console part of AgentTest which together provide a test of a number of core Console and
 * Agent behaviours such as Schema creation, registration and lookup, Object lookup, method invocation on Objects
 * Object refreshing (updating state of local proxy objects from the real Agent).
 *
 * N.B. AgentTest needs to be running for this test to behave as expected.
 *
 * @author Fraser Adams
 */
public final class AgentTestConsole implements QmfEventListener
{
    private Console _console;
    private Agent _gizmo;

    public AgentTestConsole(String url)
    {
        try
        {
            System.out.println("*** Starting AgentTestConsole used to test basic Console and Agent behaviour ***");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console(this);
            _console.addConnection(connection);

            // Wait until the gizmo Agent has been discovered
            synchronized(this)
            {
                while (_gizmo == null)
                {
                    long startTime = System.currentTimeMillis();
                    try
                    {
                        wait(10*1000);
                    }
                    catch (InterruptedException ie)
                    {
                        continue;
                    }
                    // Measure elapsed time to test against spurious wakeups and ensure we really have timed out
                    long elapsedTime = (System.currentTimeMillis() - startTime)/1000;
                    if (_gizmo == null && elapsedTime >= 10)
                    {
                        System.out.println("gizmo Agent not found, you probably need to run AgentTest");
                        System.exit(1);
                    }
                }
            }

            System.out.println("Testing lookup of control objects by name");
            List<QmfConsoleData> controls = _console.getObjects("com.profitron.gizmo", "control");
            if (controls.size() > 0)
            {
                System.out.println("control object found");
                QmfConsoleData control = controls.get(0);
                //control.listValues();

                ObjectId oid = control.getObjectId();
                //System.out.println("Agent Name = " + oid.getAgentName());
                //System.out.println("Agent Epoch = " + oid.getAgentEpoch());
                //System.out.println("Object Name = " + oid.getObjectName());

                System.out.println("Testing lookup of object by ObjectId");
                controls = _console.getObjects(oid);

                if (controls.size() == 0)
                {
                    System.out.println("No objects returned from ObjectId lookup: AgentTestConsole failed");
                    System.exit(1);
                }

                System.out.println("MethodCount = " + control.getLongValue("methodCount"));
                QmfData inArgs;
                QmfData outArgs;
                MethodResult results;

/*
                System.out.println("Testing invokeMethod(toString, args) - method called directly on Agent");
                results = _gizmo.invokeMethod("toString", null);
                System.out.println("gizmo.toString() = " + results.getArguments().getStringValue("string"));
*/

                // ********** Invoke create_child nethod **********
                System.out.println("Testing invokeMethod(create_child, args)");
                inArgs = new QmfData();
                inArgs.setValue("name", "child 1");

                results = control.invokeMethod("create_child", inArgs);
                if (!results.succeeded())
                {
                    System.out.println("create_child returned an exception object");
                    System.exit(1);
                }

                if (!results.hasValue("childAddr"))
                {
                    System.out.println("create_child returned an unexpected value");
                    System.exit(1);
                }

                ObjectId childId = results.getRefValue("childAddr");        
                System.out.println("childId = " + childId);
                System.out.println("childAddr subtype = " + results.getSubtype("childAddr"));
                QmfConsoleData child1 = _console.getObjects(childId).get(0);
                System.out.println("child1 name = " + child1.getStringValue("name"));


                // Update and display state of control object
                control.refresh();
                System.out.println("MethodCount = " + control.getLongValue("methodCount"));


                // ********** Invoke event nethod **********
                System.out.println("Testing invokeMethod(event, args) ");
                inArgs = new QmfData();
                inArgs.setValue("text", "Attention Will Robinson!! Aliens have just invaded");
                inArgs.setValue("severity", 0);
                control.invokeMethod("event", inArgs);


                // Update and display state of control object
                control.refresh();
                System.out.println("MethodCount = " + control.getLongValue("methodCount"));


                // ********** Invoke fail nethod **********
                System.out.println("Testing invokeMethod(fail, args) ");
                QmfData details = new QmfData();
                details.setValue("detail1", "something bad");
                details.setValue("detail2", "something even badder");
                inArgs = new QmfData();
                inArgs.setValue("details", details.mapEncode());
                results = control.invokeMethod("fail", inArgs);
                System.out.println("whatHappened: " + results.getStringValue("whatHappened"));
                System.out.println("howBad: " + results.getLongValue("howBad"));

                // Update and display state of control object
                control.refresh();
                System.out.println("MethodCount = " + control.getLongValue("methodCount"));


                // ********** Invoke echo nethod asynchronously **********
                System.out.println("Testing asynchronous call of invokeMethod(echo, args) ");
                inArgs = new QmfData();
                inArgs.setValue("message", "This message should be echoed by the Agent");
                control.invokeMethod("echo", inArgs, "echoMethodCorrelationId");


                // Asynchronous update and display state of control object. The state here should be the same as
                // the last time it was called as this is an asynchronous refresh. The ObjectUpdateWorkItem in
                // the event handler contains the new state
                control.refresh("echoMethodCorrelationId");
                System.out.println("MethodCount = " + control.getLongValue("methodCount") + " (should be same as last value)");



                // ********** Invoke stop nethod, this will stop the Agent **********
                System.out.println("Testing invokeMethod(stop, args) ");
                inArgs = new QmfData();
                inArgs.setValue("message", "Ladies and gentlemen Elvis has just left the building");
                control.invokeMethod("stop", inArgs);


            }
            else
            {
                System.out.println("No control objects returned: AgentTestConsole failed");
                System.exit(1);
            }
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + ": AgentTestConsole failed");
            System.exit(1);
        }
    }

    public void onEvent(WorkItem wi)
    {
        System.out.println("WorkItem type: " + wi.getType());

        if (wi.getType() == AGENT_ADDED)
        {
            AgentAddedWorkItem item = (AgentAddedWorkItem)wi;
            Agent agent = item.getAgent();

            // If this is the gizmo Agent we notify the main thread so processing can continue.
            if (agent.getProduct().equals("gizmo"))
            {
                synchronized(this)
                {
                    _gizmo = agent;
                    notify();
                }
            }
        }

        if (wi.getType() == AGENT_HEARTBEAT)
        {
            AgentHeartbeatWorkItem item = (AgentHeartbeatWorkItem)wi;
            Agent agent = item.getAgent();
            System.out.println(agent.getName());
        }

        if (wi.getType() == EVENT_RECEIVED)
        {
            EventReceivedWorkItem item = (EventReceivedWorkItem)wi;
            Agent agent = item.getAgent();
            QmfEvent event = item.getEvent();

            String className = event.getSchemaClassId().getClassName();
            System.out.println("Event: " + className);
//event.listValues();    
        }

        if (wi.getType() == METHOD_RESPONSE)
        {
            MethodResponseWorkItem item = (MethodResponseWorkItem)wi;
            MethodResult result = item.getMethodResult();
            String correlationId = item.getHandle().getCorrelationId();
            System.out.println("correlationId = " + correlationId);
            System.out.println(result.getStringValue("message"));
        }

        if (wi.getType() == OBJECT_UPDATE)
        {
            ObjectUpdateWorkItem item = (ObjectUpdateWorkItem)wi;
            QmfConsoleData object = item.getQmfConsoleData();
            ObjectId objectId = object.getObjectId();
            String correlationId = item.getHandle().getCorrelationId();
            System.out.println("correlationId = " + correlationId);
            System.out.println("objectId = " + objectId);
            System.out.println("MethodCount = " + object.getLongValue("methodCount"));
        }

    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        AgentTestConsole test = new AgentTestConsole(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("AgentTestConsole main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending AgentTestConsole ***");
    }
}
