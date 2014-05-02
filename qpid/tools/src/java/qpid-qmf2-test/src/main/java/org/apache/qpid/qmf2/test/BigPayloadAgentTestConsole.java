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
public final class BigPayloadAgentTestConsole implements QmfEventListener
{
    private Console _console;
    private Agent _agent;

    public BigPayloadAgentTestConsole(String url)
    {
        try
        {
            System.out.println("*** Starting BigPayloadAgentTestConsole used to test basic Console and Agent behaviour ***");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console(this);
            _console.addConnection(connection, " ; {link: {name:'big-payload-console', x-declare: {arguments: {'qpid.policy_type': ring, 'qpid.max_size': 500000000}}}}");

            // Wait until the broker Agent has been discovered
            _agent = _console.findAgent("big-payload-agent");
            if (_agent == null)
            {
                System.out.println("Big Payload Agent not found");
                System.exit(1);
            }

            List<QmfConsoleData> controls = _console.getObjects("com.test.bigagent", "control");
            if (controls.size() > 0)
            {
                QmfConsoleData control = controls.get(0);

                // ********** Invoke processPayload nethod **********
                System.out.println("Testing invokeMethod(processPayload, args)");
                QmfData inArgs = new QmfData();
                inArgs.setValue("parameter", new byte[150000000]);

                MethodResult results = control.invokeMethod("processPayload", inArgs);
                if (!results.succeeded())
                {
                    System.out.println("processPayload returned an exception object");
                    System.exit(1);
                }

                if (!results.hasValue("return"))
                {
                    System.out.println("processPayload returned an unexpected value");
                    System.exit(1);
                }

                byte[] returnVal = results.getValue("return");        
                System.out.println("returnVal size = " + returnVal.length);
            }
            else
            {
                System.out.println("No control objects returned: BigPayloadAgentTestConsole failed");
                System.exit(1);
            }
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + ": BigPayloadAgentTestConsole failed");
            System.exit(1);
        }
    }

    public void onEvent(WorkItem wi)
    {
        //System.out.println("WorkItem type: " + wi.getType());

        if (wi.getType() == AGENT_HEARTBEAT)
        {
            AgentHeartbeatWorkItem item = (AgentHeartbeatWorkItem)wi;
            Agent agent = item.getAgent();
            System.out.println(agent.getName());
        }
    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        BigPayloadAgentTestConsole test = new BigPayloadAgentTestConsole(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("BigPayloadAgentTestConsole main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending BigPayloadAgentTestConsole ***");
    }
}
